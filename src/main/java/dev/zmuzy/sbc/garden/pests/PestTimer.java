// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.pests;

import dev.zmuzy.sbc.core.CookieBuff;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.SbTime;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.garden.GardenPack;
import dev.zmuzy.sbc.garden.milestones.GardenCropSpeed;
import dev.zmuzy.sbc.garden.milestones.GardenMilestones;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

// from skyhanni PestSpawnTimer: when the last pest came, spawn cooldown, avg spawn time, + its warnings
// the tab's Pests widget time rounds down so the end is kept like skyhanni's getTablistEndTime does
public final class PestTimer {
    public static final String ID = "garden_pest_timer";
    private static final List<Long> spawnTimes = new ArrayList<>();
    private static long lastSpawn, cooldownEnd, longestBreak, lastCropBroken, lastWarnSound, nextSecond;
    private static boolean pestSpawned, hasWarned, hasReminded, maxPests, ready, unknown = true, repeating;
    private static String lastIsland = "";

    private PestTimer() {}

    // TimeUtils.getTablistEndTime with shouldSetCooldown
    static long endFrom(String time, long current, long now) {
        long left = CookieBuff.parse(time);
        if (left < 0) return current;
        boolean seconds = time.contains("s");
        long tabEnd = now + left;
        boolean set = current == 0 || tabEnd > current + 6000 // up to 6s of delay, it never says more than there is otherwise
                || !seconds && tabEnd + 60_000 < current // it sometimes rounds down to the nearest minute
                || seconds && tabEnd + 1000 < current; // it shouldnt say less while it shows seconds
        if (!set) return current;
        return seconds ? tabEnd : tabEnd + 60_000; // always rounded down so assume it just changed
    }

    static long average(List<Long> times) {
        if (times.isEmpty()) return 0;
        long sum = 0;
        for (long t : times) sum += t;
        return sum / times.size();
    }

    private static void onSpawn(Integer amount) {
        long now = System.currentTimeMillis();
        repeating = false;
        if (lastSpawn != 0) {
            long took = now - lastSpawn;
            if (longestBreak <= GardenPack.number(ID, "afk", 30) * 1000L) spawnTimes.add(took);
            if (GardenPack.on(ID, "spawn_chat")) GardenPack.chat("§7Pests spawned in §b" + SbTime.dur(took));
        }
        pestSpawned = true;
        longestBreak = 0;
        lastSpawn = now;
    }

    private static void onCrop() {
        long now = System.currentTimeMillis();
        if (lastCropBroken != 0) longestBreak = Math.max(longestBreak, now - lastCropBroken);
        lastCropBroken = now;
    }

    // onWidgetUpdate
    private static void readTab(long now) {
        for (String l : GameState.tab) if (Patterns.get("SH.garden.pests.max-pests-alive").matcher(l).matches()) {
            maxPests = true;
            cooldownEnd = 0;
            repeating = false;
            unknown = false;
            return;
        }
        for (String l : GameState.tab) {
            Matcher m = Patterns.get("SH.garden.pests.cooldowntime-no-color").matcher(l);
            if (!m.matches()) continue;
            unknown = false;
            ready = m.group("ready") != null;
            maxPests = m.group("maxPests") != null;
            if (ready || maxPests) {
                cooldownEnd = 0;
                repeating = false;
                return;
            }
            if (m.group("time") == null) return;
            cooldownEnd = GardenPack.on(ID, "custom") && lastSpawn != 0 ? lastSpawn + customCooldown() * 1000L : endFrom(m.group("time"), cooldownEnd, now);
            if (pestSpawned) {
                hasWarned = false;
                hasReminded = false;
                pestSpawned = false;
            }
            return;
        }
        unknown = true;
    }

    // getCustomCooldownTime with Perk.PEST_ERADICATOR, finnegan's
    private static int customCooldown() {
        boolean eradicator = false;
        for (var perk : dev.zmuzy.sbc.core.Mayor.perks) if ("Pest Eradicator".equals(perk.name())) eradicator = true;
        return eradicator ? GardenPack.number(ID, "custom_time_finnegan", 75) : GardenPack.number(ID, "custom_time", 135);
    }

    private static void warningSound() {
        GardenPack.sound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.5f);
    }

    private static void second(long now) {
        readTab(now);
        if (repeating) {
            if (cooldownEnd <= now) repeating = false;
            else GardenPack.title("§cPest spawn cooldown expires in " + SbTime.dur(cooldownEnd - now), 1);
        }
        if (hasWarned || !GardenPack.on(ID, "warn") || cooldownEnd == 0) return;
        if (cooldownEnd <= now) {
            repeating = false;
            GardenPack.title("§cPest Cooldown Has Expired!", 3);
            GardenPack.chat("§cPest spawn cooldown has expired!");
            warningSound();
            hasWarned = true;
            return;
        }
        if (hasReminded) return;
        if (cooldownEnd - (GardenPack.number(ID, "warn_seconds", 5) + 1) * 1000L <= now) {
            GardenPack.chat("§cPest spawn cooldown expires in " + SbTime.dur(cooldownEnd - now));
            hasWarned = true;
            hasReminded = true;
            if (GardenPack.on(ID, "repeat")) repeating = true;
            else {
                GardenPack.title("§cPest Cooldown Expires Soon!", 3);
                warningSound();
            }
        }
    }

    static boolean comingBack() {
        return lastIsland.isEmpty();
    }

    static void beenHereForTest() {
        lastIsland = "GARDEN";
    }

    // onIslandChange, leaving half: warning stops, next garden visit counts as coming back
    private static void left() {
        repeating = false;
        if (!GardenPack.garden()) lastIsland = "";
    }

    // onIslandChange, arriving half
    private static void arrive(long now) {
        repeating = false;
        longestBreak = lastCropBroken == 0 ? 0 : now - lastCropBroken;
        lastSpawn = 0;
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        if (!GameState.island.equals(lastIsland)) {
            lastIsland = GameState.island;
            if ("GARDEN".equals(lastIsland)) arrive(System.currentTimeMillis());
        }
        long now = System.currentTimeMillis();
        if (repeating) {
            // onTick: warning stops when the loadouts or wardrobe menu opens, thats what its trying to get you to do
            var screen = mc.gui.screen();
            String title = screen == null ? "" : GameState.strip(screen.getTitle().getString());
            if (title.startsWith("Wardrobe") || title.startsWith("Equipment") || title.startsWith("Loadout")) repeating = false;
            else if (now - lastWarnSound >= 1000) {
                lastWarnSound = now;
                warningSound();
            }
        }
        if (now >= nextSecond) {
            nextSecond = now + 1000;
            second(now);
        }
    }

    // Cooldown line value
    static String cooldownText(boolean unknown, boolean max, boolean ready, long end, long now) {
        if (unknown) return "§cUnknown";
        if (max) return "§cMax Pests!";
        if (ready) return "§aReady!";
        if (end == 0) return "§cUnknown";
        return end <= now ? "§aReady!" : "§b" + SbTime.dur(end - now);
    }

    // shouldRender: the items it shows with
    private static boolean holding() {
        return switch (GardenPack.choice(ID, "holding", "Farming tool")) {
            case "Anything" -> true;
            case "Vacuum" -> GardenPack.vacuumInHand();
            case "Lasso" -> GardenPack.lassoInHand();
            default -> GardenMilestones.hasToolInHand();
        };
    }

    private static List<RichCard.Line> lines(String last, String cooldown, String average) {
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.row("Last pest", last).brief("Last"));
        out.add(RichCard.Line.row("Cooldown", cooldown).brief("CD"));
        if (!average.isEmpty()) out.add(RichCard.Line.row("Average", average).brief("Avg"));
        return out;
    }

    private static final RichCard CARD = new RichCard(ID, "Pest timer", "Garden", Zone.TOP_LEFT, 31, ID) {
        @Override
        public boolean visible() {
            return GardenPack.garden() && holding();
        }

        @Override
        protected Content live() {
            long now = System.currentTimeMillis();
            String last = lastSpawn == 0 ? "§7None yet" : "§b" + SbTime.dur(now - lastSpawn) + " §7ago";
            long avg = average(spawnTimes);
            return new Content("", lines(last, cooldownText(unknown, maxPests, ready, cooldownEnd, now), avg == 0 ? "" : "§b" + SbTime.dur(avg)));
        }

        @Override
        protected Content sample() {
            return new Content("", lines("§b1m 8s §7ago", "§b58s", "§b4m 32s"));
        }

        @Override
        protected List<Row> classicRows() {
            long now = System.currentTimeMillis();
            return List.of(new Row("Last pest", lastSpawn == 0 ? "None yet" : SbTime.dur(now - lastSpawn), 0), new Row("Cooldown", GameState.strip(cooldownText(unknown, maxPests, ready, cooldownEnd, now)), 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Last pest", "1m 8s", 0), new Row("Cooldown", "58s", 0), new Row("Average", "4m 32s", 0));
        }
    };

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Pest timer", "Since when the last pest came, the spawn cooldown and the average time a spawn takes, with a warning before the cooldown ends if you want one.", "SkyHanni: Pest Timer")
                .when(GardenPack::garden).tick(PestTimer::tick).left(PestTimer::left)
                .option(Option.choice("garden_pest_timer.holding", "Show while holding", "", "Farming tool", "Farming tool", "Vacuum", "Lasso", "Anything"))
                .option(Option.bool("garden_pest_timer.warn", "Warn before the cooldown ends", "A title, a chat line and a sound, so you can change your equipment in time.", false))
                .option(Option.integer("garden_pest_timer.warn_seconds", "Warn this many seconds before", "", 5, 1, 60))
                .option(Option.bool("garden_pest_timer.repeat", "Repeat the warning", "Until the wardrobe or loadouts menu is opened or the cooldown is over.", false))
                .option(Option.bool("garden_pest_timer.custom", "Own cooldown length", "Count from the spawn with your own time instead of what the tab list says. For equipment swapping.", false))
                .option(Option.integer("garden_pest_timer.custom_time", "Own cooldown", "In seconds after a spawn.", 135, 1, 600))
                .option(Option.integer("garden_pest_timer.custom_time_finnegan", "Own cooldown with Finnegan", "In seconds, while his Pest Eradicator perk is active.", 75, 1, 600))
                .option(Option.bool("garden_pest_timer.spawn_chat", "Say the spawn time in chat", "When a pest spawns, how long it took.", false))
                .option(Option.integer("garden_pest_timer.afk", "Away after (s)", "A spawn does not count in the average when you stopped farming this long before it.", 30, 1, 600)));
        PestAlerts.onSpawn(PestTimer::onSpawn);
        GardenCropSpeed.onCrop(c -> onCrop());
        HudLayout.add(CARD);
    }
}
