// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.events.diana;

import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

// from skyhanni GriffinBurrowHelper: the burrow guesses (spade particle curve, chain arrow, burrow
// particles), nearest target, chat lines that type or remove them (dug, mob spawn, treasure, death, "Poof"),
// block clicks, world render (boxes, labels, beacons, line to next)
public final class GriffinBurrowHelper {
    // matches: §eYou finished the Griffin burrow chain! §r§7(8/8)
    // and: §eYou dug out a Griffin Burrow! §r§7(4/8)
    static final Patterns.P BURROW_DUG = Patterns.p("SH.event.diana.mythological.burrows.burrow-dug-capture");
    // matches: §c§lUh oh! §r§eYou dug out a §r§2Gaia Construct§r§e!
    static final Patterns.P GENERIC_SPAWN = Patterns.p("SH.event.diana.mythological.burrows.generic-spawn");
    // matches: §6§lRARE DROP! §r§eYou dug out a §r§9Mythos Fragment§r§e!
    static final Patterns.P TREASURE_DUG = Patterns.p("SH.event.diana.mythological.burrows.treasure-dug");

    public static Vec3 targetLocation;

    // hypixel itself removes burrows after 30m
    private static final Map<GuessEntry, Long> allGuessesTimers = new IdentityHashMap<>();
    private static final List<GuessEntry> guessSoup = new ArrayList<>();
    private static final TimeLimitedSet<Vec3> recentGuessesRemoved = new TimeLimitedSet<>(5000);
    private static final Deque<String> recentActionsDebug = new ArrayDeque<>();

    private static boolean shouldFocusOnRareMob;
    public static boolean mobAlive;
    private static Vec3 lastMoveCheck;
    private GriffinBurrowHelper() {}

    public static Long getTimer(GuessEntry e) {
        return allGuessesTimers.get(e);
    }

    public static List<GuessEntry> guesses() {
        return new ArrayList<>(guessSoup);
    }

    public static List<String> debugLines() {
        return new ArrayList<>(recentActionsDebug);
    }

    public static void addDebug(String action) {
        recentActionsDebug.addFirst(action);
        if (recentActionsDebug.size() > 80) recentActionsDebug.pollLast();
    }

    public static void removeGuess(Vec3 location, String reason) {
        List<GuessEntry> toRemove = new ArrayList<>();
        for (GuessEntry g : guessSoup) if (g.contains(location)) toRemove.add(g);
        for (GuessEntry g : toRemove) removeGuess(g, reason, true);
    }

    public static void removeGuess(GuessEntry guess, String reason, boolean logAsPossibleBurrow) {
        if (guessSoup.contains(guess)) addDebug("removed guess: " + guess + " because " + reason);
        guessSoup.remove(guess);
        allGuessesTimers.remove(guess);
        if (logAsPossibleBurrow) recentGuessesRemoved.addAll(guess.guesses);
    }

    public static void removeGuess(Set<GuessEntry> set, String reason) {
        boolean any = false;
        for (GuessEntry g : guessSoup) if (set.contains(g)) any = true;
        if (any) addDebug("removed guesses: " + set + " because " + reason);
        guessSoup.removeAll(set);
        for (GuessEntry g : set) allGuessesTimers.remove(g);
        for (GuessEntry g : set) recentGuessesRemoved.addAll(g.guesses);
    }

    public static void addGuess(GuessEntry guess, String reason) {
        GuessEntry existing = getGuess(guess.getCurrent());
        if (existing != null) {
            if (existing.burrowType != BurrowType.UNKNOWN) {
                addDebug("didnt add guess because already exists with type: " + guess);
                return;
            } else removeGuess(existing, "added guess replacing unknown type", true);
        }
        guessSoup.add(guess);
        allGuessesTimers.put(guess, DianaEnv.now());
        addDebug("added guess: " + guess + " because " + reason);
    }

    public static GuessEntry getGuess(Vec3 location) {
        if (location == null) return null;
        for (GuessEntry g : new ArrayList<>(guessSoup)) if (g.contains(location)) return g;
        return null;
    }

    // drop inaccurate spade guesses you're looking at (within 2 degrees and 80 blocks)
    public static void removeInaccurateIfLooking() {
        Set<GuessEntry> toDelete = new HashSet<>();
        Vec3 eyePos = DianaEnv.world.eyePos();
        Vec3 lookAngle = DianaEnv.world.look();
        for (GuessEntry item : new ArrayList<>(guessSoup)) {
            if (!item.spadeGuess) continue;
            Vec3 toTarget = item.getCurrent().subtract(eyePos);
            double len = Math.sqrt(toTarget.lengthSqr());
            if (len == 0) continue;
            double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, lookAngle.dot(toTarget.scale(1 / len))))));
            if (angle < 2.0 && len < 80) toDelete.add(item);
        }
        removeGuess(toDelete, "clicked inaccurate guess");
    }

    // once a sec (onSecondPassed) and after every change
    public static void update() {
        Vec3 newLocation = calculateNewTarget();
        if (!java.util.Objects.equals(targetLocation, newLocation)) targetLocation = newLocation;

        if (Diana.warpEnabled() && targetLocation != null) BurrowWarpHelper.shouldUseWarps(targetLocation);

        // try to move all guesses
        StringBuilder sb = new StringBuilder();
        Set<GuessEntry> toDelete = new LinkedHashSet<>();
        for (GuessEntry g : new ArrayList<>(guessSoup)) if (g.checkRemove(sb)) toDelete.add(g);
        removeGuess(toDelete, sb.toString());

        if (!toDelete.isEmpty()) update();
    }

    private static Vec3 calculateNewTarget() {
        List<Vec3> locations = new ArrayList<>();
        if (Diana.shareEnabled()) for (RareMobWaypointShare.SharedRareMob w : RareMobWaypointShare.waypoints().values()) locations.add(w.location);
        shouldFocusOnRareMob = Diana.shareFocus() && !locations.isEmpty();
        if (!shouldFocusOnRareMob) {
            for (GuessEntry g : guessSoup) locations.add(g.getCurrent());
            for (RareMobWaypointShare.SharedRareMob w : RareMobWaypointShare.waypoints().values()) locations.add(w.location);
        }
        Vec3 player = DianaEnv.world.playerPos();
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        for (Vec3 l : locations) {
            double d = l.distanceTo(player);
            if (d < bestD) {
                bestD = d;
                best = l;
            }
        }
        return best;
    }

    public static void showUseSpadeTitle() {
        addDebug("showing use spade title");
        DianaEnv.world.title("§eUse Spade");
    }

    // BurrowGuessEvent: new guess from the spade, the arrow or a moved entry
    public static void onBurrowGuess(GuessEntry guess, String source) {
        DianaExtras.onBurrowGuess();
        Vec3 newLocation = guess.getCurrent();
        Vec3 playerLocation = DianaEnv.world.playerPos();
        if (newLocation.distanceTo(playerLocation) < 6) return;
        if (!DianaData.inBounds("HUB", newLocation)) return;
        addGuess(guess, "burrow guess from " + source);
        update();
    }

    // BurrowDetectEvent: particle finder knows this block's type
    public static void onBurrowDetect(Vec3 burrowLocation, BurrowType type) {
        GuessEntry currentEntry = getGuess(burrowLocation);
        if (currentEntry != null) removeGuess(currentEntry, "type detected", true);
        addGuess(new GuessEntry(List.of(burrowLocation), type, true), "type detected");

        Set<GuessEntry> toDelete = new HashSet<>();
        for (GuessEntry g : guessSoup) if (g.spadeGuess && g.getCurrent().distanceToSqr(burrowLocation) < 2000) toDelete.add(g);
        removeGuess(toDelete, "inaccurate burrow near detected burrow");
        update();
    }

    // BurrowDugEvent: "You dug out a Griffin Burrow! (current/max)"
    public static void onBurrowDug(Vec3 location, int current, int max) {
        mobAlive = false;
        addDebug("Burrow dug event [" + location.x + ", " + location.y + ", " + location.z + "] recently removed burrows size: " + recentGuessesRemoved.size());
        removeGuess(location, "burrow dug event");
        GriffinBurrowParticleFinder.onBurrowDug(location);
        ArrowGuessBurrow.onBurrowDug(current, max);
        DianaExtras.onBurrowDug();

        // chain done
        if (current == max && Diana.b("warnOnChainComp", true)) {
            Set<GuessEntry> nearby = new HashSet<>();
            for (GuessEntry g : guessSoup) if (g.getCurrent().distanceToSqr(location) < 10) nearby.add(g);
            removeGuess(nearby, "chain finished with leftover burrow within 3 blocks");
            Vec3 player = DianaEnv.world.playerPos();
            boolean anyClose = false;
            for (GuessEntry g : guessSoup) if (g.getCurrent().distanceToSqr(player) < 8100) anyClose = true;
            if (!anyClose) showUseSpadeTitle();
        }

        if (Diana.b("guessFromArrow", true) && Diana.b("warnOnFail", true) && current != max) {
            Delayed.run(1000, () -> {
                if (DianaEnv.passedSince(ArrowGuessBurrow.lastArrowTime) > 2000) showUseSpadeTitle();
            });
        }
        update();
    }

    // EntityMoveEvent for you: moved 10+ blocks since the last check
    public static void onPlayerMove() {
        if (!isEnabled()) return;
        Vec3 p = DianaEnv.world.playerPos();
        if (lastMoveCheck == null || p.distanceTo(lastMoveCheck) > 10) {
            lastMoveCheck = p;
            update();
        }
    }

    // GriffinBurrowHelper.onChat (hub only), formatted line with § codes
    public static void onChat(String message) {
        if (!isEnabled()) return;
        Vec3 it = BurrowApi.lastBurrowInteracted;
        if (it != null) {
            if (message.startsWith("§c ☠ §r§7You were killed by §r")) {
                mobAlive = false;
                removeGuess(it, "you died L bozo");
            }
            if (it.distanceTo(DianaEnv.world.playerPos()) > 9) {
                addDebug("burrow dug event received at to far away location [" + it.x + ", " + it.y + ", " + it.z + "]");
                return;
            }
            Matcher m = BURROW_DUG.matcher(message);
            if (m.find()) {
                int current = Integer.parseInt(m.group("current"));
                int max = Integer.parseInt(m.group("max"));
                onBurrowDug(it, current, max);
            } else if (GENERIC_SPAWN.matcher(message).matches()) {
                mobAlive = true;
                removeGuess(it, "chat mob spawn replacing old");
                addGuess(new GuessEntry(List.of(it), BurrowType.MOB, true), "chat mob spawn");
            } else if (TREASURE_DUG.matcher(message).matches()) {
                removeGuess(it, "chat treasure dug replacing old");
                addGuess(new GuessEntry(List.of(it), BurrowType.START, false, DianaEnv.now() + 3000, true), "chat treasure dug");
            }
        }
        // talked to diana npc
        if (message.equals("§6Poof! §r§eYou have cleared your griffin burrows!")) resetAllData();
    }

    public static void resetAllData() {
        addDebug("reset all data");
        guessSoup.clear();
        allGuessesTimers.clear();
        targetLocation = null;
        GriffinBurrowParticleFinder.reset();
        mobAlive = false;
        BurrowWarpHelper.currentWarp = null;
        if (isEnabled()) update();
    }

    public static void onWorldChange() {
        if (mobAlive) {
            if (BurrowApi.lastBurrowInteracted != null) removeGuess(BurrowApi.lastBurrowInteracted, "changed worlds while mob was alive");
            mobAlive = false;
        }
        if (Diana.b("clearOnWorldChange", false)) resetAllData();
    }

    public static void onProfileJoin() {
        resetAllData();
    }

    // grass block with air, flowers, grass, leaves or spruce fence above (unloaded chunks count as valid)
    public static boolean isBlockValid(Vec3 pos) {
        DianaEnv.World w = DianaEnv.world;
        if (!w.inLoadedChunk(pos)) return true;
        boolean isGround = w.isGrass(pos);
        boolean isValidBlockAbove = w.isAllowedAbove(pos.add(0, 1, 0));
        return isGround && isValidBlockAbove;
    }

    // holding the spade longer than timeInPastMs, hypixel shows burrow particles then
    public static boolean shouldBurrowParticlesBeVisible(long timeInPastMs) {
        long time = DianaEnv.passedSince(DianaEnv.world.lastItemChange());
        return DianaApi.hasSpadeInHand() && time > timeInPastMs;
    }

    public static void removeSpadeWarnTitle() {
        if (Diana.b("warnOnFail", true) || Diana.b("warnOnChainComp", true)) DianaEnv.world.stopTitle("§eUse Spade");
    }

    private static Vec3 blockCenter(Vec3 v) {
        return new Vec3(Math.floor(v.x) + 0.5, Math.floor(v.y) + 0.5, Math.floor(v.z) + 0.5);
    }

    private static AABB blockBox(Vec3 v) {
        double x = Math.floor(v.x), y = Math.floor(v.y), z = Math.floor(v.z);
        return new AABB(x, y, z, x + 1, y + 1, z + 1);
    }

    private static String sep(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    // GriffinBurrowHelper.onRenderWorld
    public static void render(WorldDraw w) {
        if (!isEnabled()) return;
        Vec3 playerLocation = DianaEnv.world.playerPos();
        if (Diana.shareEnabled()) renderRareMobs(w, playerLocation);

        BurrowWarpHelper.WarpPoint currentWarp = BurrowWarpHelper.currentWarp;
        if (Diana.b("lineToNext", true)) {
            int color;
            Vec3 renderLocation;
            if (currentWarp != null) {
                color = 0xFF55FFFF; // AQUA
                renderLocation = currentWarp.location();
            } else {
                color = shouldFocusOnRareMob ? 0xFFFF55FF : 0xFFFFFFFF;
                if (targetLocation == null) return;
                renderLocation = blockCenter(targetLocation);
            }
            GuessEntry target = getGuess(targetLocation);
            BurrowType targetType = target == null ? null : target.burrowType;
            float lineWidth = 2;
            if (targetType != null && targetType != BurrowType.UNKNOWN) {
                color = targetType.color();
                lineWidth = 3;
            }
            if (currentWarp == null) {
                // drawLineToCrosshair: from just in front of the eyes to the target
                Vec3 start = w.eye.add(DianaEnv.world.look().scale(2));
                w.line(start, renderLocation, color, lineWidth, false);
            }
        }

        if (!RareMobWaypointShare.waypoints().isEmpty() && Diana.shareFocus()) return;

        double textScale = Diana.n("textScale", 1.0);
        double beaconDistance = Diana.n("beaconDistance", 10);
        if (Diana.b("multiGuesses", true)) {
            renderAllGuesses(w, playerLocation, textScale, beaconDistance);
        } else {
            GuessEntry target = getGuess(targetLocation);
            if (target == null) return;
            Vec3 location = target.getCurrent();
            double distance = location.distanceTo(playerLocation);
            String text = target.burrowType == BurrowType.UNKNOWN ? (currentWarp != null ? "§b" : "§f") + "Guess" : target.burrowType.code() + target.burrowType.text;
            w.waypointBox(location, target.burrowType.color(), beaconDistance != -1 && distance > beaconDistance, true, 0);
            w.label(text, blockCenter(location).add(0, 1, 0), true);
        }
    }

    private static void renderRareMobs(WorldDraw w, Vec3 playerLocation) {
        for (RareMobWaypointShare.SharedRareMob rareMob : new ArrayList<>(RareMobWaypointShare.waypoints().values())) {
            Vec3 location = rareMob.location;
            w.waypointBox(location, 0xFFFF55FF, true, true, 0);
            double distance = location.distanceTo(playerLocation);
            Vec3 up = blockCenter(location).add(0, 1, 0);
            if (distance > 10) w.label("§d§l" + rareMob.mobName + " §e" + sep((int) distance) + "m", up, true);
            else w.label("§d§l" + rareMob.mobName, up, true);
            if (distance < 5) RareMobWaypointShare.maybeRemove(rareMob);
            w.label("§eFrom §b" + rareMob.playerDisplayName, up.add(0, -0.35, 0), true);
            if (Diana.b(Diana.SHARE, "showDespawnTime", true)) {
                long left = 75_000 - DianaEnv.passedSince(rareMob.spawnTime);
                w.label("§eDespawns in §b" + Math.max(0, left / 1000) + "s", up.add(0, -0.7, 0), true);
            }
        }
    }

    private static void renderAllGuesses(WorldDraw w, Vec3 playerLocation, double textScale, double beaconDistance) {
        boolean nearbyDetection = Diana.b("burrowsNearbyDetection", true);
        boolean guessOn = Diana.guess();
        boolean subGuesses = Diana.b("renderSubGuesses", false);
        boolean filled = Diana.b("filled", true);
        for (GuessEntry guess : new ArrayList<>(guessSoup)) {
            Vec3 location = guess.getCurrent();
            double distance = location.distanceTo(playerLocation);
            BurrowType burrowType = guess.burrowType;
            String text = burrowType.code() + burrowType.text;

            // skyhanni returns from the whole method here, skipping only this entry keeps the rest drawn
            if (!nearbyDetection && burrowType != BurrowType.UNKNOWN) continue;

            if (burrowType == BurrowType.UNKNOWN) {
                if (!guessOn) continue;
                String textColor = BurrowWarpHelper.currentWarp != null && location.equals(targetLocation) ? "§b" : "§f";
                text = textColor + "Guess";
                if (distance > 5) w.label("§e" + sep((int) distance) + "m", blockCenter(location).add(0, 0.65, 0), true);
            }

            if (subGuesses) {
                Vec3 lineStart = location;
                for (int i = guess.currentIndex + 1; i < guess.guesses.size(); i++) {
                    Vec3 sub = guess.guesses.get(i);
                    w.box(blockBox(sub), 0xFFC0C0C0, false, false);
                    w.line(blockCenter(lineStart), blockCenter(sub), 0xFFC0C0C0, 1, false);
                    lineStart = sub;
                }
            }
            if (filled) w.waypointBox(location, burrowType.color(), beaconDistance != -1 && distance > beaconDistance, true, 0);
            else {
                w.box(blockBox(location), burrowType.color(), false, true);
                if (beaconDistance != -1 && distance > beaconDistance) w.beacon(blockCenter(location), burrowType.color(), true);
            }
            w.label(text, blockCenter(location).add(0, 1, 0), true);
        }
    }

    // BlockClickEvent (hub only): a spade click on a guess may move it, a click on a known burrow marks it interacted
    public static void onBlockClick(Vec3 location) {
        if (!isEnabled()) return;
        if (!DianaApi.hasSpadeInHand()) return;
        GuessEntry g = getGuess(location);
        if (g != null && g.burrowType == BurrowType.UNKNOWN && g.getCurrent().equals(location)) {
            Delayed.run(200, () -> {
                if (DianaEnv.passedSince(BurrowApi.lastBurrowRelatedChatMessage) > 400) g.attemptMove();
            });
        }
        Set<Vec3> burrows = new HashSet<>();
        for (GuessEntry e : guessSoup) burrows.addAll(e.guesses);
        burrows.addAll(recentGuessesRemoved.toList());
        if (burrows.contains(location)) BurrowApi.setBurrowInteracted(location);
    }

    static boolean isEnabled() {
        return DianaApi.isDoingDiana();
    }
}
