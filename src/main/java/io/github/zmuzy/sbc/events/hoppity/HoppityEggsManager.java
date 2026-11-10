// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events.hoppity;

import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.SbTime;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

// from skyhanni HoppityEggsManager: which meal eggs are claimed, when they respawn, hypixel's egg lines
// (found, spawned, already collected, none left, not the event), the "next egg in" reply, the all-eggs-ready
// warning, and the unclaimed eggs list (HoppityEggDisplayManager)
public final class HoppityEggsManager {
    // misc.chocolatefactory.* in the catalog
    static final Patterns.P EGG_FOUND = Patterns.p("SH.misc.chocolatefactory.egg.found");
    static final Patterns.P HITMAN_EGG_FOUND = Patterns.p("SH.misc.chocolatefactory.egg.found.hitman");
    static final Patterns.P EGG_BOUGHT = Patterns.p("SH.misc.chocolatefactory.egg.bought");
    static final Patterns.P RABBIT_FOUND = Patterns.p("SH.misc.chocolatefactory.rabbit.found");
    static final Patterns.P NEW_RABBIT = Patterns.p("SH.misc.chocolatefactory.rabbit.found.new");
    static final Patterns.P DUPLICATE_RABBIT = Patterns.p("SH.misc.chocolatefactory.rabbit.duplicate");
    static final Patterns.P VISITOR_ACCEPTED = Patterns.p("SH.misc.chocolatefactory.hoppity.visitor.accepted");
    static final Patterns.P NO_EGGS_LEFT = Patterns.p("SH.misc.chocolatefactory.egg.noneleft");
    static final Patterns.P EGG_SPAWNED = Patterns.p("SH.misc.chocolatefactory.egg.spawned");
    static final Patterns.P EGG_ALREADY_COLLECTED = Patterns.p("SH.misc.chocolatefactory.egg.alreadycollected");
    static final Patterns.P EVENT_NOT_ON = Patterns.p("SH.misc.chocolatefactory.egg.notevent");

    private static HoppityEggType lastMeal;
    private static String lastNote;
    // so no warnings fire on the unknown startup state before any claim or rollover we saw
    private static boolean warningActive;
    private static long lastWarnTime = HoppityEnv.FAR_PAST;
    private static final Set<HoppityEggType> observedSpawnedEggs = new HashSet<>();
    private static boolean syncedFromConfig;
    private static int spawnedThisEvent;

    private HoppityEggsManager() {}

    // ProfileJoinEvent: restore claimed/spawned from the profile's saved marks
    public static void onProfileJoin() {
        warningActive = false;
        lastWarnTime = HoppityEnv.FAR_PAST;
        observedSpawnedEggs.clear();
        HoppityEggType.resetAll();
        if (!HoppityTime.isHoppityEvent()) return;
        for (HoppityEggType it : HoppityEggType.RESETTING) {
            Long lastFound = HoppityEnv.profile().mealLastFound.get(it.name());
            if (lastFound != null && HoppityEnv.isInPast(lastFound)) it.markClaimed(lastFound);
            Long nextSpawn = HoppityEnv.profile().mealNextSpawn.get(it.name());
            if (nextSpawn != null && HoppityEnv.isInPast(nextSpawn) && it.hasRemainingSpawns() && !it.hasNotFirstSpawnedYet()) markObservedSpawned(it, false);
        }
    }

    // EggSpawnedEvent
    public static void onEggSpawned(HoppityEggType type) {
        markObservedSpawned(type, true);
        spawnedThisEvent++;
        HoppityEggLocator.onEggSpawned(type);
    }

    public static void onWorldChange() {
        lastMeal = null;
        lastNote = null;
        syncFromConfig();
    }

    private static void syncFromConfig() {
        if (syncedFromConfig) return;
        for (var e : HoppityEnv.profile().mealNextSpawn.entrySet()) {
            if (!HoppityEnv.isInPast(e.getValue())) continue;
            try {
                if (HoppityTime.isHoppityEvent()) markObservedSpawned(HoppityEggType.valueOf(e.getKey()), false);
            } catch (IllegalArgumentException ignored) {
            }
        }
        syncedFromConfig = true;
    }

    private static void markObservedSpawned(HoppityEggType type, boolean setLastReset) {
        if (HoppityTime.isHoppityEvent() && type.isResetting()) observedSpawnedEggs.add(type);
        type.markSpawned(setLastReset);
    }

    // EggFoundEvent for a meal egg: claimed, nearest known spot collected
    public static void onEggFound(HoppityEggType type, String note) {
        if (!HoppityTime.isHoppityEvent() || !type.isResetting()) return;
        HoppityEggLocations.saveNearestEgg();
        type.markClaimed();
        lastMeal = type;
        lastNote = note;
    }

    private static void sendNextEggAvailable() {
        HoppityEggType nextEgg = null;
        for (HoppityEggType t : HoppityEggType.RESETTING) if (nextEgg == null || t.timeUntil() < nextEgg.timeUntil()) nextEgg = t;
        if (nextEgg == null) return;
        if (spawnedThisEvent == 279) sendNextHuntIn("No more eggs will spawn this event");
        else HoppityEnv.world.chat("§eNext egg available in §b" + HoppityEnv.format(nextEgg.timeUntil()) + "§e.");
    }

    private static void sendNextHuntIn(String reason) {
        int currentYear = HoppityTime.now().year();
        long timeUntil = HoppityTime.eventStart(currentYear + 1) - HoppityEnv.now();
        HoppityEnv.world.chat("§e" + reason + ". The next Hoppity's Hunt is in §b" + HoppityEnv.format(timeUntil) + "§e.");
    }

    // HoppityEggsManager.onChat (skyblock), formatted line. true = hide it (ours replaces it)
    public static boolean onChat(String message) {
        boolean timeInChat = Hoppity.b(Hoppity.PANEL, "eggLocatorTimeInChat", true);
        if (EVENT_NOT_ON.matcher(message).matches()) {
            if (!timeInChat) return false;
            sendNextHuntIn("Hoppity's Hunt is not active");
            return true;
        }
        if (!HoppityTime.isHoppityEvent()) return false;
        if (NO_EGGS_LEFT.matcher(message).matches()) {
            HoppityEggType.markAllFound();
            if (timeInChat) {
                sendNextEggAvailable();
                return true;
            }
            return false;
        }
        Matcher m = EGG_ALREADY_COLLECTED.matcher(message);
        if (m.matches()) {
            HoppityEggType t = HoppityEggType.byMeal(m.group("meal"));
            if (t != null) t.markClaimed();
            if (timeInChat) {
                sendNextEggAvailable();
                return true;
            }
            return false;
        }
        m = EGG_SPAWNED.matcher(message);
        if (m.matches()) {
            HoppityEggType t = HoppityEggType.byMeal(m.group("meal"));
            if (t != null) onEggSpawned(t);
        }
        return false;
    }

    // HoppityEggsManager.shareWaypointPrompt: after a new/dupe rabbit, offer to share the egg you just found
    public static void shareWaypointPrompt() {
        if (!Hoppity.b(Hoppity.EGGS, "shared", true)) return;
        HoppityEggType meal = lastMeal;
        String note = lastNote;
        if (meal == null || note == null) return;
        lastMeal = null;
        lastNote = null;
        HoppityEggsShared.remember(HoppityEnv.world.playerPos(), meal, note);
        if (HoppityCompactChat.on()) return; // the short line itself is the link
        HoppityEnv.world.clickableChat("Click here to share the location of this chocolate egg with the server!", "/sbc shareegg", "§eClick to share!");
    }

    // SecondPassedEvent
    public static void onSecondPassed() {
        checkSpawned();
        if (!isActive()) return;
        checkWarn();
    }

    private static void checkSpawned() {
        for (HoppityEggType t : HoppityEggType.RESETTING) if (t.spawnedToday() && !t.alreadyResetToday()) onEggSpawned(t);
    }

    private static void checkWarn() {
        boolean allEggsRemaining = HoppityEggType.allEggsUnclaimed();
        if (!warningActive) warningActive = !allEggsRemaining || observedSpawnedEggs.containsAll(HoppityEggType.RESETTING);
        if (warningActive && allEggsRemaining) warn();
    }

    private static void warn() {
        if (!Hoppity.b(Hoppity.PANEL, "warningsEnabled", true)) return;
        if (HoppityEnv.world.isStrandedProfile()) return;
        if (HoppityEnv.world.isBusy() && !Hoppity.b(Hoppity.PANEL, "warnWhileBusy", false)) return;
        if (HoppityEnv.passedSince(lastWarnTime) < 60_000) return;
        lastWarnTime = HoppityEnv.now();
        int amount = HoppityEggType.RESETTING.size();
        String message = "All " + amount + " Hoppity Eggs are ready to be found!";
        String dest = Hoppity.text(Hoppity.PANEL, "warpClickDestination", "").trim();
        if (Hoppity.b(Hoppity.PANEL, "warpClickEnabled", true)) {
            String cmd = HoppityEnv.world.inSkyBlock() ? "/warp " + dest : "/skyblock";
            HoppityEnv.world.clickableChat(message, cmd.trim(), "§eClick to " + (HoppityEnv.world.inSkyBlock() ? "warp" : "join /skyblock") + "!");
        } else HoppityEnv.world.chat(message);
        HoppityEnv.world.title("§e" + amount + " Hoppity Eggs!");
        HoppityEnv.world.playSound("block.note_block.pling", 1f);
    }

    // HoppityEggDisplayManager.updateDisplay: "Unclaimed Eggs" lines (legacy colours), empty if nothing
    public static List<String> displayLines() {
        List<String> out = new ArrayList<>();
        if (!isActive()) return out;
        if (HoppityEnv.world.isBusy() && !Hoppity.b(Hoppity.PANEL, "showWhileBusy", false)) return out;
        out.add("§bUnclaimed Eggs:");
        for (HoppityEggType it : entries()) {
            String color = it.hasRemainingSpawns() ? it.mealColor : "§c";
            out.add("§7 - " + it.formattedName() + color + " " + eggTime(it));
        }
        if (Hoppity.b(Hoppity.PANEL, "showCollectedLocationCount", true) && HoppityEnv.world.inSkyBlock()) {
            int totalEggs = HoppityEggLocations.islandLocations().size();
            if (totalEggs > 0) {
                Set<net.minecraft.world.phys.Vec3> island = HoppityEggLocations.islandLocations();
                int collected = 0;
                for (var v : HoppityEggLocations.islandCollectedLocations()) if (island.contains(v)) collected++;
                double percentage = collected / (double) totalEggs;
                out.add("§7Locations: " + formatEggsCollected(percentage) + collected + "§7/§a" + totalEggs);
                if (percentage >= 1) HoppityEggLocations.setFoundAll();
            }
        }
        if (out.size() == 1) return List.of();
        return out;
    }

    // meals the list shows, in the chosen order
    static List<HoppityEggType> entries() {
        List<HoppityEggType> entries = new ArrayList<>();
        for (HoppityEggType t : HoppityEggType.RESETTING) if (t.hasRemainingSpawns() || !t.isClaimed()) entries.add(t);
        if (Hoppity.text(Hoppity.PANEL, "displayOrder", "Soonest first").startsWith("Soonest")) entries.sort(java.util.Comparator.comparingLong(HoppityEggType::timeUntil));
        else entries.sort(java.util.Comparator.<HoppityEggType>comparingInt(t -> t.altDay ? 1 : 0).thenComparingInt(t -> t.resetsAt));
        return entries;
    }

    // meal timer: until next spawn, or until the event ends if none left
    static String eggTime(HoppityEggType it) {
        if (it.hasRemainingSpawns()) return HoppityEnv.format(it.timeUntil());
        Long end = HoppityTime.eventEndMark();
        return end == null ? "???" : HoppityEnv.format(end - HoppityEnv.now());
    }

    private static String formatEggsCollected(double p) {
        return p < 0.3 ? "§c" : p < 0.6 ? "§6" : p < 0.9 ? "§e" : "§a";
    }

    public static boolean isActive() {
        return HoppityEnv.world.inSkyBlock() && HoppityTime.isHoppityEvent();
    }

    public static void reset() {
        lastMeal = null;
        lastNote = null;
        warningActive = false;
        lastWarnTime = HoppityEnv.FAR_PAST;
        observedSpawnedEggs.clear();
        syncedFromConfig = false;
        spawnedThisEvent = 0;
        HoppityEggType.resetAll();
    }

    static SbTime.SbDate nowDate() {
        return HoppityTime.now();
    }
}
