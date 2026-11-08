// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mining;

import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni HotmApi PowderType (available/total per profile, gains + spends from the POWDER widget and
// sidebar line)
// and PowderTracker (chests lockpicked, hard stone compacted, every chest reward from chat with per hour
// rates, in the hollows)
public final class Powder {
    public enum Type {
        MITHRIL("Mithril", "§2"), GEMSTONE("Gemstone", "§d"), GLACITE("Glacite", "§b");

        public final String displayName, color;

        Type(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        private long[] storage() {
            return MiningEnv.profile().powder.computeIfAbsent(name(), k -> new long[]{0, 0});
        }

        public long current() {
            return storage()[0];
        }

        public long total() {
            return storage()[1];
        }

        // skyhanni PowderType.setAmount: the diff is a gain or a spend
        public void setAmount(long value) {
            long[] s = storage();
            long diff = value - s[0];
            if (diff == 0) return;
            boolean firstRead = s[0] == 0;
            s[1] += diff;
            s[0] = value;
            if (firstRead) return; // stored value was unknown, nothing gained now
            if (diff > 0) onGain(this, diff);
            else sessionSpent.merge(this, -diff, Long::sum);
        }

        public static Type byName(String n) {
            for (Type t : values()) if (t.displayName.equals(n)) return t;
            return null;
        }
    }

    static final Patterns.P WIDGET_POWDER = Patterns.p("SH.mining.hotm.widget.powder-nocolor");
    static final Patterns.P SCOREBOARD_POWDER = Patterns.p("SH.features.gui.customscoreboard.scoreboard.mining.powder");
    static final Patterns.P PICKED = Patterns.p("SH.mining.powder.tracker.picked.colorless");
    static final Patterns.P COMPACTED = Patterns.p("SH.mining.powder.tracker.compacted.colorless");

    // skyhanni PowderChestReward: reward lines of a lockpicked chest (colorless catalog patterns)
    public enum Reward {
        GEMSTONE_POWDER("§dGemstone Powder", "gemstonepowder"),
        ROUGH_RUBY_GEMSTONE("§fRough Ruby Gemstone", "roughrubygemstone"), FLAWED_RUBY_GEMSTONE("§aFlawed Ruby Gemstone", "flawedrubygemstone"), FINE_RUBY_GEMSTONE("§9Fine Ruby Gemstone", "finerubygemstone"), FLAWLESS_RUBY_GEMSTONE("§5Flawless Ruby Gemstone", "flawlessrubygemstone"),
        ROUGH_SAPPHIRE_GEMSTONE("§fRough Sapphire Gemstone", "roughsapphiregemstone"), FLAWED_SAPPHIRE_GEMSTONE("§aFlawed Sapphire Gemstone", "flawedsapphiregemstone"), FINE_SAPPHIRE_GEMSTONE("§9Fine Sapphire Gemstone", "finesapphiregemstone"), FLAWLESS_SAPPHIRE_GEMSTONE("§5Flawless Sapphire Gemstone", "flawlesssapphiregemstone"),
        ROUGH_AMBER_GEMSTONE("§fRough Amber Gemstone", "roughambergemstone"), FLAWED_AMBER_GEMSTONE("§aFlawed Amber Gemstone", "flawedambergemstone"), FINE_AMBER_GEMSTONE("§9Fine Amber Gemstone", "fineambergemstone"), FLAWLESS_AMBER_GEMSTONE("§5Flawless Amber Gemstone", "flawlessambergemstone"),
        ROUGH_AMETHYST_GEMSTONE("§fRough Amethyst Gemstone", "roughamethystgemstone"), FLAWED_AMETHYST_GEMSTONE("§aFlawed Amethyst Gemstone", "flawedamethystgemstone"), FINE_AMETHYST_GEMSTONE("§9Fine Amethyst Gemstone", "fineamethystgemstone"), FLAWLESS_AMETHYST_GEMSTONE("§5Flawless Amethyst Gemstone", "flawlessamethystgemstone"),
        ROUGH_JADE_GEMSTONE("§fRough Jade Gemstone", "roughjadegemstone"), FLAWED_JADE_GEMSTONE("§aFlawed Jade Gemstone", "flawedjadegemstone"), FINE_JADE_GEMSTONE("§9Fine Jade Gemstone", "finejadegemstone"), FLAWLESS_JADE_GEMSTONE("§5Flawless Jade Gemstone", "flawlessjadegemstone"),
        ROUGH_TOPAZ_GEMSTONE("§fRough Topaz Gemstone", "roughtopazgemstone"), FLAWED_TOPAZ_GEMSTONE("§aFlawed Topaz Gemstone", "flawedtopazgemstone"), FINE_TOPAZ_GEMSTONE("§9Fine Topaz Gemstone", "finetopazgemstone"), FLAWLESS_TOPAZ_GEMSTONE("§5Flawless Topaz Gemstone", "flawlesstopazgemstone"),
        FTX_3070("§9FTX 3070", "ftx3070"), ELECTRON_TRANSIMTTER("§9Electron Transmitter", "electrontransimtter"), ROBOTRON_REFLECTOR("§9Robotron Reflector", "robotronreflector"),
        SUPERLITE_MOTOR("§9Superlite Motor", "superlitemotor"), CONTROL_SWITCH("§9Control Switch", "controlswitch"), SYNTHETIC_HEART("§9Synthetic Heart", "syntheticheart"),
        GOBLIN_EGG("§9Goblin Egg", "goblinegg"), GREEN_GOBLIN_EGG("§aGreen Goblin Egg", "greengoblinegg"), RED_GOBLIN_EGG("§cRed Goblin Egg", "redgoblinegg"), YELLOW_GOBLIN_EGG("§eYellow Goblin Egg", "yellowgoblinegg"), BLUE_GOBLIN_EGG("§3Blue Goblin Egg", "bluegoblinegg"),
        WISHING_COMPASS("§aWishing Compass", "wishingcompass"), SLUDGE_JUICE("§aSludge Juice", "sludgejuice"), ASCENSION_ROPE("§9Ascension Rope", "ascensionrope"), TREASURITE("§5Treasurite", "treasurite"),
        JUNGLE_HEART("§6Jungle Heart", "jungleheart"), PICKONIMBUS_2000("§5Pickonimbus 2000", "pickonimbus2000"), YOGGIE("§aYoggie", "yoggie"), PREHISTORIC_EGG("§fPrehistoric Egg", "prehistoricegg"), OIL_BARREL("§aOil Barrel", "oilbarrel"),
        DIAMOND_ESSENCE("§dDiamond Essence", "diamondessence"), GOLD_ESSENCE("§dGold Essence", "goldessence");

        public final String displayName;
        final Patterns.P pattern;

        Reward(String displayName, String id) {
            this.displayName = displayName;
            this.pattern = Patterns.p("SH.mining.powder.tracker.reward." + id + ".colorless");
        }
    }

    // from skyhanni PowderTracker.ResourceInfo
    static final class ResourceInfo {
        long estimated, lastEstimated;
        int stoppedChecks;
        double perHour;
        final List<Long> perMin = new ArrayList<>();
    }

    // session tracker data (skyhanni keeps it per profile in tracker storage)
    public static long totalChestPicked, totalHardStoneCompacted;
    public static final Map<Reward, Long> rewards = new LinkedHashMap<>();
    public static final Map<Type, Long> sessionGained = new LinkedHashMap<>(), sessionSpent = new LinkedHashMap<>();
    private static long lastChestPicked = MiningEnv.FAR_PAST;
    private static boolean isGrinding;
    static final ResourceInfo gemstoneInfo = new ResourceInfo(), diamondEssenceInfo = new ResourceInfo(), goldEssenceInfo = new ResourceInfo(), chestInfo = new ResourceInfo(), hardStoneInfo = new ResourceInfo();

    private Powder() {}

    private static void onGain(Type type, long amount) {
        sessionGained.merge(type, amount, Long::sum);
        // skyhanni onPowderGain (hollows): gemstone powder right after a lockpick counts as a chest reward
        if (MiningApi.inCrystalHollows() && type == Type.GEMSTONE && MiningEnv.passedSince(lastChestPicked) <= 5000) rewards.merge(Reward.GEMSTONE_POWDER, amount, Long::sum);
    }

    // skyhanni WidgetUpdateEvent(POWDER): " Mithril: 99,918" lines
    public static void onWidget(List<String> lines) {
        for (String line : lines) {
            Matcher m = WIDGET_POWDER.matcher(line);
            if (!m.matches()) continue;
            Type t = Type.byName(m.group("type"));
            if (t == null) continue;
            t.setAmount(MiningEnv.parseLong(m.group("amount")));
        }
    }

    // skyhanni HotmData.onScoreboardChange, the sidebar powder line
    public static void onScoreboard(List<String> formatted) {
        for (String l : formatted) {
            Matcher m = SCOREBOARD_POWDER.matcher(l);
            if (!m.matches()) continue;
            Type t = Type.byName(m.group("type"));
            if (t == null || m.group("amount").isEmpty()) continue;
            t.setAmount(MiningEnv.parseLong(m.group("amount")));
        }
    }

    public static boolean trackerEnabled() {
        return MiningApi.inCrystalHollows();
    }

    // skyhanni PowderTracker.onChat, colorless line
    public static void onChat(String plain) {
        if (!trackerEnabled()) return;
        if (PICKED.matcher(plain).matches()) {
            totalChestPicked++;
            isGrinding = true;
            lastChestPicked = MiningEnv.now();
        }
        if (COMPACTED.matcher(plain).matches()) totalHardStoneCompacted++;
        for (Reward r : Reward.values()) {
            if (r == Reward.GEMSTONE_POWDER) continue;
            Matcher m = r.pattern.matcher(plain);
            if (!m.matches()) continue;
            String a = m.group("amount");
            rewards.merge(r, a == null ? 1 : MiningEnv.parseLong(a), Long::sum);
        }
    }

    // from skyhanni PowderTracker.onSecondPassed
    public static void onSecondPassed() {
        if (!trackerEnabled()) return;
        gemstoneInfo.estimated = rewards.getOrDefault(Reward.GEMSTONE_POWDER, 0L);
        diamondEssenceInfo.estimated = rewards.getOrDefault(Reward.DIAMOND_ESSENCE, 0L);
        goldEssenceInfo.estimated = rewards.getOrDefault(Reward.GOLD_ESSENCE, 0L);
        chestInfo.estimated = totalChestPicked;
        hardStoneInfo.estimated = totalHardStoneCompacted;
        for (ResourceInfo i : new ResourceInfo[]{gemstoneInfo, diamondEssenceInfo, goldEssenceInfo, chestInfo, hardStoneInfo}) calculateResourceHour(i);
        if (MiningEnv.passedSince(lastChestPicked) > 60_000) isGrinding = false;
    }

    static void calculateResourceHour(ResourceInfo info) {
        long difference = info.estimated - info.lastEstimated;
        info.lastEstimated = info.estimated;
        if (difference == info.estimated) return;
        info.perHour = info.perMin.isEmpty() ? 0 : info.perMin.stream().mapToLong(Long::longValue).average().orElse(0) * 3600;
        info.perMin.add(difference);
        if (difference == 0L) {
            info.stoppedChecks += 1;
            if (info.stoppedChecks == 60) {
                info.stoppedChecks = 0;
                info.perMin.clear();
                info.perHour = 0.0;
            }
            return;
        }
        info.stoppedChecks = 0;
    }

    public static boolean isGrinding() {
        return isGrinding;
    }

    private static String rate(double e) {
        return e < 0 ? "0" : MiningEnv.sep((long) e);
    }

    // one gems rewards compacted up: {flawless, fine, flawed, rough}, null if none
    static long[] gemstone(String gem) {
        long total = 0;
        for (String q : new String[]{"ROUGH", "FLAWED", "FINE", "FLAWLESS"}) {
            long count = rewards.getOrDefault(Reward.valueOf(q + "_" + gem.toUpperCase(java.util.Locale.ROOT) + "_GEMSTONE"), 0L);
            long mul = switch (q) {
                case "FLAWED" -> 80;
                case "FINE" -> 6400;
                case "FLAWLESS" -> 512000;
                default -> 1;
            };
            total += count * mul;
        }
        if (total == 0) return null;
        long flawless = total / 512000, rem = total % 512000;
        long fine = rem / 6400;
        rem %= 6400;
        return new long[]{flawless, fine, rem / 80, rem % 80};
    }

    // since the last lockpicked chest, -1 before the first
    static long sinceLastChest() {
        return MiningEnv.isFarPast(lastChestPicked) ? -1 : MiningEnv.passedSince(lastChestPicked);
    }

    // skyhanni PowderTracker.drawDisplay, the lines skyhanni shows by default
    public static List<String> trackerLines() {
        List<String> out = new ArrayList<>();
        out.add("§d" + MiningEnv.sep(totalChestPicked) + " Total Chests Picked §7(" + rate(chestInfo.perHour) + "/h)");
        MiningEvents.Active ev = MiningEvents.getActiveEvent(MiningEvents.Type.DOUBLE_POWDER);
        out.add(ev != null ? "§bDouble Powder: §aActive! §7(" + MiningEnv.format(ev.timeLeft()) + ")" : "§bDouble Powder: §cInactive!");
        out.add("§b" + MiningEnv.sep(rewards.getOrDefault(Reward.GEMSTONE_POWDER, 0L)) + " " + Reward.GEMSTONE_POWDER.displayName + " §7(" + rate(gemstoneInfo.perHour) + "/h)");
        out.add("§b" + MiningEnv.sep(rewards.getOrDefault(Reward.DIAMOND_ESSENCE, 0L)) + " " + Reward.DIAMOND_ESSENCE.displayName + " §7(" + rate(diamondEssenceInfo.perHour) + "/h)");
        out.add("§b" + MiningEnv.sep(rewards.getOrDefault(Reward.GOLD_ESSENCE, 0L)) + " " + Reward.GOLD_ESSENCE.displayName + " §7(" + rate(goldEssenceInfo.perHour) + "/h)");
        out.add("§b" + MiningEnv.sep(totalHardStoneCompacted) + " §fHard Stone §bCompacted §7(" + rate(hardStoneInfo.perHour) + "/h)");
        String[][] gems = {{"Ruby", "§c"}, {"Sapphire", "§b"}, {"Amber", "§6"}, {"Amethyst", "§5"}, {"Jade", "§a"}, {"Topaz", "§e"}};
        for (String[] g : gems) {
            long[] c = gemstone(g[0]);
            if (c != null) out.add("§5" + c[0] + "§7-§9" + c[1] + "§7-§a" + c[2] + "§f-" + c[3] + " " + g[1] + g[0] + " Gemstone");
        }
        return out;
    }

    public static void reset() {
        totalChestPicked = totalHardStoneCompacted = 0;
        rewards.clear();
        sessionGained.clear();
        sessionSpent.clear();
        lastChestPicked = MiningEnv.FAR_PAST;
        isGrinding = false;
        for (ResourceInfo i : new ResourceInfo[]{gemstoneInfo, diamondEssenceInfo, goldEssenceInfo, chestInfo, hardStoneInfo}) {
            i.estimated = i.lastEstimated = 0;
            i.stoppedChecks = 0;
            i.perHour = 0;
            i.perMin.clear();
        }
    }

    static Store.MiningProfile profile() {
        return MiningEnv.profile();
    }
}
