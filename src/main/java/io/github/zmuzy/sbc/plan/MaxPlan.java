// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.plan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zmuzy.sbc.core.Config;
import io.github.zmuzy.sbc.core.Net;
import io.github.zmuzy.sbc.core.Profile;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.tasks.Routine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// long term "max everything" plan. every goal gets xp left, your rate (measured or a rough guess from gear),
// hours and sb xp
// sorted by money/level goal then spread over your daily hours
public final class MaxPlan {
    public record Goal(String id, String name, String icon, String activity, String unit, double have, double need, double ratePerHour,
                       boolean measured, double sbXp, double coinsPerHour, double hours, String how) {
        public double progress() {
            return need <= 0 ? 1 : Math.min(1, have / need);
        }

        public boolean done() {
            return have >= need;
        }
    }

    public record Phase(Goal goal, double startDay, double endDay) {}

    public record Block(String name, String icon, int minutes, String island, String node, String why, String taskId) {}

    public static final class Result {
        public final List<Goal> goals = new ArrayList<>();
        public final List<Phase> phases = new ArrayList<>();
        public final List<Block> today = new ArrayList<>();
        public double dailyMinutes, focusHoursPerDay, sbXpPerDay;
        public final Map<Integer, Double> levelEtaDays = new LinkedHashMap<>();
        public final List<String[]> collectionTargets = new ArrayList<>();
        public int collectionTiersLeft;
        public double totalDays;
    }

    private static final String[][] SKILL_INFO = {
            // skill, activity, icon, default xp/h, how
            {"Farming", "farming", "golden_hoe", "1200000", "Farm your best crop on the Garden; Jacob's contests on top."},
            {"Mining", "gemstones", "iron_pickaxe", "400000", "Gemstone mining or Mineshafts; powder comes with it."},
            {"Combat", "slayer", "iron_sword", "600000", "Slayer bosses (or Dungeons) give the most Combat XP."},
            {"Foraging", "foraging", "diamond_axe", "500000", "Chop Galatea trees with your best axe and a Monkey pet."},
            {"Fishing", "fishing", "fishing_rod", "400000", "Sea creatures - lava fishing once you can."},
            {"Enchanting", "enchanting", "enchanting_table", "3000000", "Enchant in bulk and do Experiments daily."},
            {"Alchemy", "alchemy", "brewing_stand", "2000000", "Brew potions in bulk (cheap ingredients, a Brewing stand row)."},
            {"Carpentry", "", "crafting_table", "2000000", "Craft cheap items in bulk (e.g. furniture) at a crafting table."},
            {"Taming", "", "bone", "300000", "Comes from pet XP - level pets with the Kat or while grinding."},
            {"Hunting", "hunting", "lead", "150000", "Hunt shards on Galatea - sell the valuable ones."},
            {"Runecrafting", "", "magma_cream", "15000", "Fuse runes; slow, do it last."}};

    private static volatile Map<String, Long> collectionMax = Map.of();
    private static volatile Map<String, long[]> collectionTiers = Map.of();
    private static boolean collectionsLoading;
    private static Result cached;
    private static long cachedAt;

    private MaxPlan() {}

    public static Result get() {
        long now = System.currentTimeMillis();
        if (cached == null || now - cachedAt > 5000) {
            cached = build();
            cachedAt = now;
        }
        return cached;
    }

    public static void invalidate() {
        cachedAt = 0;
    }

    private static Result build() {
        loadCollections();
        Result r = new Result();
        Profile.Data d = Profile.current;
        MoneyMethods.Loadout lo = MoneyMethods.loadout();
        Map<String, Double> measured = new HashMap<>();
        for (Store.Rate rate : Store.I.rates.values())
            for (String sk : rate.skillXp.keySet()) if (rate.activeMs > 10 * 60_000) measured.merge(sk, rate.skillPerHour(sk), Math::max);

        for (String[] s : SKILL_INFO) {
            String sk = s[0];
            // not read yet, or a skill with its own table -> skip, dont start from a fake 0
            if (d != null && d.fromGame && (!d.skills.containsKey(sk) || io.github.zmuzy.sbc.core.game.GameRead.ownTable(sk))) continue;
            int cap = switch (sk) { case "Farming", "Mining", "Combat", "Enchanting", "Taming" -> 60; case "Runecrafting" -> 25; default -> 50; };
            double have = d != null && d.skills.containsKey(sk) ? d.skills.get(sk).xp() : xpOfObserved(sk);
            double need = Profile.SKILL_XP[Math.min(cap, Profile.SKILL_XP.length - 1)];
            double rate = measured.getOrDefault(sk, 0.0);
            boolean meas = rate > 0;
            if (!meas) rate = Double.parseDouble(s[3]) * gearFactor(sk, lo);
            int lvl = Profile.level(Profile.SKILL_XP, have, cap).level();
            double sb = 0;
            for (int l = lvl + 1; l <= cap; l++) sb += l <= 10 ? 5 : l <= 25 ? 10 : l <= 50 ? 20 : 30;
            double coins = s[1].isEmpty() ? 0 : MoneyMethods.rateFor(s[1], 0);
            r.goals.add(goal("skill_" + sk, sk + " " + lvl + " → " + cap, s[2], s[1], "XP", have, need, rate, meas, sb, coins, s[4]));
        }
        String[][] slayers = {{"Revenant", "rotten_flesh"}, {"Tarantula", "string"}, {"Sven", "bone"}, {"Voidgloom", "ender_pearl"}, {"Inferno", "blaze_powder"}};
        int[] slayerSb = {15, 25, 35, 50, 75, 100, 125, 150, 175};
        for (String[] sl : slayers) {
            Profile.Level l = d == null ? null : d.slayers.get(sl[0]);
            if (l == null && d != null && d.fromGame) continue; // not read yet, dont start from a fake 0
            double have = l == null ? 0 : l.xp();
            double need = 1_000_000;
            int lvl = l == null ? 0 : l.level();
            double rate = lvl >= 3 ? 5000 : 1500;
            double sb = 0;
            for (int i = lvl; i < 9; i++) sb += slayerSb[i];
            r.goals.add(goal("slayer_" + sl[0], sl[0] + " " + lvl + " → 9", sl[1], "slayer", "Slayer XP", have, need, rate, false, sb,
                    MoneyMethods.rateFor("slayer", 0), "Grind the highest tier you kill quickly; Aatrox (Slayer mayor) doubles XP."));
        }
        if (d == null || d.knows(io.github.zmuzy.sbc.core.game.GameProfile.CATACOMBS)) {
            double have = d == null || d.catacombs == null ? 0 : d.catacombs.xp();
            int lvl = d == null || d.catacombs == null ? 0 : d.catacombs.level();
            double need = Profile.DUNGEON_XP[50];
            double rate = lvl < 15 ? 30_000 : lvl < 28 ? 90_000 : lvl < 34 ? 200_000 : 400_000;
            double sb = 0;
            for (int i = lvl + 1; i <= 50; i++) sb += i < 40 ? 20 : 40;
            r.goals.add(goal("cata", "Catacombs " + lvl + " → 50", "skeleton_skull", "dungeons", "XP", have, need, rate, false, sb,
                    MoneyMethods.rateFor("dungeons", 0), "Run the highest floor you clear; Catacombs XP scales with floor."));
        }
        if (d == null || d.knows(io.github.zmuzy.sbc.core.game.GameProfile.HOTM)) {
            double have = d == null || d.hotm == null ? 0 : d.hotm.xp();
            r.goals.add(goal("hotm", "Heart of the Mountain " + (d == null || d.hotm == null ? 0 : d.hotm.level()) + " → 10", "emerald", "commissions_loop", "HOTM XP",
                    have, Profile.HOTM_XP[9], 30_000, false, (10 - (d == null || d.hotm == null ? 0 : d.hotm.level())) * 35.0,
                    MoneyMethods.rateFor("commissions_loop", 0), "Commissions give most HOTM XP - do the daily bonus ones every day."));
        }
        if (d == null || d.knows(io.github.zmuzy.sbc.core.game.GameProfile.HOTF)) {
            double hf = d == null ? 0 : d.hotfXp;
            r.goals.add(goal("hotf", "Heart of the Forest " + (d == null || d.hotf == null ? 0 : d.hotf.level()) + " → 10", "oak_sapling", "foraging", "HOTF XP",
                    hf, Profile.HOTM_XP[9], 25_000, false, (10 - (d == null || d.hotf == null ? 0 : d.hotf.level())) * 35.0,
                    MoneyMethods.rateFor("foraging", 0), "Foraging on Galatea levels the Heart of the Forest; whispers come from trees."));
        }
        // every collection tier left = 4 sb xp. ~6 tiers/h early, 1/h late (rough)
        if (d != null && !collectionTiers.isEmpty()) {
            int left = 0, total = 0;
            List<Object[]> near = new ArrayList<>();
            for (Map.Entry<String, long[]> e : collectionTiers.entrySet()) {
                if (d.fromGame && !d.collections.containsKey(e.getKey())) continue;
                long have = d.collections.getOrDefault(e.getKey(), 0L);
                long[] tiers = e.getValue();
                for (long t : tiers) {
                    total++;
                    if (have < t) left++;
                }
                for (long t : tiers)
                    if (have < t) {
                        near.add(new Object[]{e.getKey(), have, t, (double) have / t});
                        break;
                    }
            }
            near.sort((a, b) -> Double.compare((double) b[3], (double) a[3]));
            for (Object[] n : near.stream().limit(10).toList())
                r.collectionTargets.add(new String[]{io.github.zmuzy.sbc.core.ItemRepo.name((String) n[0]), io.github.zmuzy.sbc.core.Prices.coins((long) n[1])
                        + " / " + io.github.zmuzy.sbc.core.Prices.coins((long) n[2]), String.format(java.util.Locale.ROOT, "%.0f%%", (double) n[3] * 100)});
            r.collectionTiersLeft = left;
            r.goals.add(goal("collections", "Collections (" + (total - left) + "/" + total + " tiers)", "chest", "", "tiers", total - left, total,
                    2.5, false, left * 4.0, 0, "Finish the collection tiers closest to done - most come for free while you grind other goals."));
        }

        double w = Config.I.moneyWeight / 100.0;
        double maxSb = 0.0001, maxC = 1;
        for (Goal g : r.goals) {
            if (g.done() || g.hours() <= 0) continue;
            maxSb = Math.max(maxSb, g.sbXp() / g.hours());
            maxC = Math.max(maxC, g.coinsPerHour());
        }
        final double fSb = maxSb, fC = maxC;
        r.goals.sort(Comparator.comparing(Goal::done).thenComparing(Comparator.comparingDouble(
                (Goal g) -> (1 - w) * (g.hours() <= 0 ? 0 : g.sbXp() / g.hours()) / fSb + w * g.coinsPerHour() / fC).reversed()));

        // dailies first, rest goes to focus goals
        double hours = Math.max(0.5, Config.I.planHours);
        double dailyMin = 0, dailySb = 0;
        for (Routine.Entry e : Routine.today()) {
            if (!e.def().period().equals(io.github.zmuzy.sbc.core.SbTime.Period.DAILY)) continue;
            dailyMin += e.def().minutes();
        }
        dailyMin = Math.min(dailyMin, hours * 60 * 0.5);
        r.dailyMinutes = dailyMin;
        r.focusHoursPerDay = Math.max(0.25, hours - dailyMin / 60);
        double day = 0;
        double sbTotal = 0;
        double cur = d == null ? 0 : Math.max(0, d.sbXp);
        for (Goal g : r.goals) {
            if (g.done() || g.hours() <= 0 || Double.isInfinite(g.hours())) continue;
            double len = g.hours() / r.focusHoursPerDay;
            r.phases.add(new Phase(g, day, day + len));
            for (int target : new int[]{300, 400, 500}) {
                double needXp = target * 100 - cur;
                if (!r.levelEtaDays.containsKey(target) && sbTotal + g.sbXp() >= needXp && needXp > 0)
                    r.levelEtaDays.put(target, day + len * Math.max(0, (needXp - sbTotal) / Math.max(1, g.sbXp())));
            }
            sbTotal += g.sbXp();
            day += len;
        }
        r.totalDays = day;
        r.sbXpPerDay = day <= 0 ? 0 : sbTotal / day;
        for (int target : new int[]{300, 400, 500}) if (cur >= target * 100) r.levelEtaDays.put(target, 0.0);

        // today: open dailies then the first 2 focus goals
        int dm = (int) Math.round(dailyMin);
        if (dm > 0) r.today.add(new Block("Daily tasks (guided routine)", "clock", dm, "", "", "Press Start routine - it walks you through them by importance", ""));
        int focusMin = (int) Math.round(r.focusHoursPerDay * 60);
        List<Goal> open = r.goals.stream().filter(g -> !g.done() && g.hours() > 0).toList();
        if (!open.isEmpty()) {
            Goal a = open.get(0);
            Goal b = open.size() > 1 && focusMin >= 90 ? open.get(1) : null;
            int am = b == null ? focusMin : (int) Math.round(focusMin * 0.65);
            r.today.add(block(a, am));
            if (b != null) r.today.add(block(b, focusMin - am));
        }
        return r;
    }

    private static Block block(Goal g, int minutes) {
        Planner.Activity a = activity(g.activity());
        return new Block(g.name(), g.icon(), minutes, a == null ? "" : a.island(), a == null ? "" : a.node(), g.how(), "");
    }

    public static Planner.Activity activity(String id) {
        for (Planner.Activity a : Planner.ACTIVITIES) if (a.id().equals(id)) return a;
        return null;
    }

    private static Goal goal(String id, String name, String icon, String activity, String unit, double have, double need, double rate, boolean measured,
                             double sb, double coins, String how) {
        double hours = have >= need ? 0 : rate <= 0 ? Double.POSITIVE_INFINITY : (need - have) / rate;
        return new Goal(id, name, icon, activity, unit, have, need, rate, measured, sb, coins, hours, how);
    }

    // better gear = faster leveling. very rough multiplier from the money tier you hit
    private static double gearFactor(String skill, MoneyMethods.Loadout lo) {
        return switch (skill) {
            case "Farming" -> lo.has("FERMENTO") ? 2.5 : lo.has("MELON_DICER", "PUMPKIN_DICER", "THEORETICAL_HOE") ? 1.6 : 1;
            case "Mining" -> lo.has("DIVAN_DRILL") ? 2 : lo.has("DRILL") ? 1.3 : 1;
            case "Combat" -> lo.has("TERMINATOR", "HYPERION") ? 2 : 1;
            case "Fishing" -> lo.has("HELLFIRE_ROD", "INFERNO_ROD") ? 2 : 1;
            case "Foraging" -> lo.pet("MONKEY", "LEGENDARY") ? 1.6 : 1;
            default -> 1;
        };
    }

    private static double xpOfObserved(String skill) {
        double[] o = Store.I.observedSkills.get(skill);
        if (o == null) return 0;
        int lvl = (int) Math.max(0, Math.min(60, o[0]));
        double base = Profile.SKILL_XP[lvl];
        double span = lvl + 1 < Profile.SKILL_XP.length ? Profile.SKILL_XP[lvl + 1] - base : 0;
        return base + span * o[1] / 100.0;
    }

    private static void loadCollections() {
        if (collectionsLoading || !collectionTiers.isEmpty()) return;
        collectionsLoading = true;
        Map<String, long[]> tiers = new HashMap<>();
        Map<String, Long> max = new HashMap<>();
        for (var category : io.github.zmuzy.sbc.core.data.CollectionData.categories()) {
            for (var item : category.items()) {
                tiers.put(item.id(), item.tiers());
                max.put(item.id(), item.tiers().length == 0 ? 0 : item.tiers()[item.tiers().length - 1]);
            }
        }
        if (tiers.isEmpty()) {
            collectionsLoading = false;
            return;
        }
        collectionTiers = tiers;
        collectionMax = max;
        invalidate();
    }
}
