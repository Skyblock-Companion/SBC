// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.plan;

import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Mayor;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Profile;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.tasks.Routine;
import dev.zmuzy.sbc.tasks.TaskDef;
import dev.zmuzy.sbc.track.Tracker;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// what to do now, in the order it pays:
// 1. stuff that stopped till you come back (forge done, minion full) 2. dailies 3. best measured grind 4.
// upgrades that pay back fastest
// coins/h only ever measured. nothing measured = tell them how to measure, dont guess
public final class Planner {
    public record Activity(String id, String name, String category, String icon, String island, String node, String skill,
                           double coinsPerHour, double skillXpPerHour, double sbXpPerHour, String how) {}

    public record Step(String name, String icon, int minutes, double coins, double sbXp, String island, String node,
                       boolean measured, String why, String taskId) {}

    public static final List<Activity> ACTIVITIES = List.of(
            new Activity("farming", "Farming the best crop", "Farming", "golden_hoe", "GARDEN", "", "Farming", 3_000_000, 600_000, 0,
                    "Farm your highest-fortune crop; sell to Bazaar or keep for contests."),
            new Activity("visitors_loop", "Garden visitors + pests loop", "Farming", "emerald", "GARDEN", "", "Farming", 1_500_000, 100_000, 0,
                    "Serve visitors as they arrive and clear pests between them."),
            new Activity("commissions_loop", "Commission loop", "Mining", "iron_pickaxe", "DWARVEN_MINES", "King", "Mining", 1_000_000, 250_000, 0,
                    "Chain commissions for powder, HOTM XP and Mining XP."),
            new Activity("gemstones", "Gemstone mining", "Mining", "amethyst_shard", "CRYSTAL_HOLLOWS", "", "Mining", 3_000_000, 300_000, 0,
                    "Mine the most valuable gemstone your gear handles; sell to Bazaar."),
            new Activity("glacite", "Glacite Mineshafts", "Mining", "packed_ice", "DWARVEN_MINES", "", "Mining", 2_500_000, 250_000, 0,
                    "Run Mineshafts for corpses and Glacite Powder."),
            new Activity("foraging", "Foraging", "Foraging", "diamond_axe", "GALATEA", "", "Foraging", 1_500_000, 400_000, 0,
                    "Chop the best tree for your tools on Galatea or in the Park."),
            new Activity("hunting", "Hunting shards", "Hunting", "lead", "GALATEA", "", "Hunting", 2_000_000, 150_000, 0,
                    "Hunt the shards with the best Bazaar price right now."),
            new Activity("fishing", "Sea creature fishing", "Fishing", "fishing_rod", "CRIMSON_ISLE", "", "Fishing", 1_800_000, 350_000, 0,
                    "Fish lava sea creatures (Thunder / Jawbus) or Bayou for drops."),
            new Activity("trophy", "Trophy fishing", "Fishing", "tropical_fish", "CRIMSON_ISLE", "Odger", "Fishing", 800_000, 150_000, 5,
                    "Catch missing trophy fish tiers; each new tier gives SkyBlock XP."),
            new Activity("slayer", "Slayer bosses", "Slayer", "rotten_flesh", "HUB", "Maddox the Slayer", "Combat", 1_500_000, 500_000, 0,
                    "Your fastest slayer tier; drops plus Slayer and Combat XP."),
            new Activity("dungeons", "Dungeon floors", "Dungeons", "skeleton_skull", "DUNGEON_HUB", "Mort", "Combat", 3_000_000, 200_000, 0,
                    "Highest floor you clear quickly; chests plus Catacombs XP."),
            new Activity("kuudra", "Kuudra", "Crimson Isle", "magma_cream", "CRIMSON_ISLE", "Elle", "Combat", 4_000_000, 150_000, 0,
                    "Kuudra at your tier; paid chests with keys."),
            new Activity("diana", "Mythological Ritual", "Diana", "golden_shovel", "HUB", "", "Combat", 6_000_000, 250_000, 0,
                    "Only when Diana is mayor: burrows, Inquisitors, Chimera books."),
            new Activity("rift", "Rift", "Rift", "ender_eye", "THE_RIFT", "", "", 400_000, 0, 10,
                    "Motes and Rift progress (Enigma Souls and Timecharms give SkyBlock XP)."),
            new Activity("flipping", "Bazaar flipping", "Economy", "gold_ingot", "HUB", "Bazaar", "", 0, 0, 0,
                    "Place buy orders and sell offers on the best spreads (see the Money page)."),
            new Activity("enchanting", "Experiments + enchanting XP", "Enchanting", "enchanting_table", "", "", "Enchanting", 500_000, 2_000_000, 0,
                    "Experiments for books; enchant for Enchanting XP."),
            new Activity("alchemy", "Brewing", "Alchemy", "brewing_stand", "", "", "Alchemy", 300_000, 1_500_000, 0,
                    "Brew potions in bulk for Alchemy XP."));

    public static List<Step> lastPlan = List.of();

    private Planner() {}

    // only offer upgrades that pay back in this many days
    static final double PAYBACK_DAYS = 30;

    public static List<Step> plan() {
        double minutesLeft = Config.I.planHours * 60;
        double w = Config.I.moneyWeight / 100.0;
        List<Step> out = new ArrayList<>();
        long now = System.currentTimeMillis();

        // 1. stuff that stopped making money till you come back
        Long forge = Store.I.timers.get("forge");
        if (forge != null && forge <= now) {
            out.add(new Step("Claim the Forge and start the next", "anvil", 2, 0, 0, "DWARVEN_MINES", "Forge", true,
                    "Finished: it makes nothing more until you claim it. The Forge page shows what pays most to put in.", ""));
            minutesLeft -= 2;
        }
        for (Store.MinionRecord r : Store.minions().values()) {
            if (dev.zmuzy.sbc.minion.Minions.hoursUntilFull(r) != 0) continue;
            out.add(new Step("Empty your " + dev.zmuzy.sbc.minion.Minions.pretty(r.type) + " minion " + r.tier, "chest", 1, 0, 0,
                    r.island == null || r.island.isEmpty() ? "PRIVATE_ISLAND" : r.island, "", true, "Full by its measured rate: it stopped working.", ""));
            minutesLeft -= 1;
        }

        // 2. dailies, best per minute first (theyre capped per day so they beat any grind)
        List<Routine.Entry> open = new ArrayList<>(Routine.today().stream().filter(e -> !e.complete()).toList());
        for (Routine.Entry e : open) {
            TaskDef t = e.def();
            int left = t.target() - e.done();
            int mins = (int) Math.ceil(t.minutes() * (double) left / t.target());
            if (mins > minutesLeft) continue;
            double coins = t.coins() * left / t.target();
            double xp = t.sbXp() * left / t.target();
            if (!t.core() && coins * w + xp * 50_000 * (1 - w) <= 0) continue;
            // value only sorts the dailies, we dont show coins for it bc nothing was measured
            out.add(new Step(t.displayName() + (left > 1 ? " ×" + left : ""), t.icon(), mins, 0, xp, t.island(), t.guideNode(), false,
                    t.hint(), t.id()));
            minutesLeft -= mins;
            if (minutesLeft < 10) break;
        }

        // 3. rest of the time: best measured grind, or how to measure one
        if (minutesLeft >= 15) {
            List<Scored> ranked = rank(w).stream().filter(Scored::measured).toList();
            if (ranked.isEmpty()) {
                out.add(new Step("Measure a money method", "clock", 10, 0, 0, "", "", false,
                        "Play one for 10 minutes with its tracker on; then the best of yours shows here with your real rate.", ""));
            } else {
                int blocks = minutesLeft >= 90 && ranked.size() > 1 ? 2 : 1;
                for (int k = 0; k < blocks; k++) {
                    Scored sc = ranked.get(k);
                    int mins = (int) Math.round(blocks == 2 ? (k == 0 ? minutesLeft * 0.6 : minutesLeft * 0.4) : minutesLeft);
                    out.add(new Step(sc.a().name(), sc.a().icon(), mins, sc.coinsPerHour() * mins / 60, sc.sbXpPerHour() * mins / 60, sc.a().island(),
                            sc.a().node(), true, Prices.coins(sc.coinsPerHour()) + " an hour from your own play there. " + sc.a().how(), ""));
                }
            }
        }

        // 4. upgrades that pay back fastest (only prices that actually trade)
        List<Object[]> upgrades = new ArrayList<>();
        for (Store.MinionRecord r : Store.minions().values())
            for (dev.zmuzy.sbc.minion.Minions.Advice a : dev.zmuzy.sbc.minion.Minions.advise(r))
                if (a.certain() && a.cost() > 0 && a.daysToPayBack() <= PAYBACK_DAYS) upgrades.add(new Object[]{r, a});
        upgrades.sort(java.util.Comparator.comparingDouble(o -> ((dev.zmuzy.sbc.minion.Minions.Advice) o[1]).daysToPayBack()));
        for (Object[] o : upgrades.stream().limit(2).toList()) {
            Store.MinionRecord r = (Store.MinionRecord) o[0];
            dev.zmuzy.sbc.minion.Minions.Advice a = (dev.zmuzy.sbc.minion.Minions.Advice) o[1];
            out.add(new Step(a.text() + " · " + dev.zmuzy.sbc.minion.Minions.pretty(r.type) + " minion " + r.tier, "hopper", 1, 0, 0,
                    r.island == null || r.island.isEmpty() ? "PRIVATE_ISLAND" : r.island, "", true,
                    String.format(java.util.Locale.ROOT, "Pays back in %.1f days: +%s a day for %s.", a.daysToPayBack(), Prices.coins(a.extraPerDay()), Prices.coins(a.cost())), ""));
        }
        lastPlan = out;
        return out;
    }

    public record Scored(Activity a, double coinsPerHour, double sbXpPerHour, boolean measured, double score) {}

    public static List<Scored> rank(double moneyWeight) {
        List<Scored> list = new ArrayList<>();
        double maxC = 1, maxX = 0.01;
        List<Scored> raw = new ArrayList<>();
        for (Activity a : ACTIVITIES) {
            if (Config.I.excludedActivities.contains(a.id())) continue;
            if (a.id().equals("diana") && !Mayor.dianaActive()) continue;
            Store.Rate r = Store.I.rates.get(a.island());
            boolean islandMeasured = r != null && r.activeMs > 10 * 60_000 && a.island() != null && !a.island().isEmpty();
            // tracker first, else the island. nothing measured = 0
            double tracked = MoneyMethods.rateFor(a.id(), 0);
            double coins = a.id().equals("flipping") ? flipRate() : tracked > 0 ? tracked : islandMeasured && r.coinsPerHour() > 0 ? r.coinsPerHour() : 0;
            // flipping comes from the order book not your play, so never "measured"
            boolean measured = !a.id().equals("flipping") && (tracked > 0 || islandMeasured);
            double skillXp = islandMeasured && !a.skill().isEmpty() && r.skillPerHour(a.skill()) > 0 ? r.skillPerHour(a.skill()) : a.skillXpPerHour();
            double sbXp = a.sbXpPerHour() + sbXpFromSkill(a.skill(), skillXp);
            if (islandMeasured && r.sbXpPerHour() > sbXp) sbXp = r.sbXpPerHour();
            raw.add(new Scored(a, coins, sbXp, measured, 0));
            maxC = Math.max(maxC, coins);
            maxX = Math.max(maxX, sbXp);
        }
        for (Scored s : raw)
            list.add(new Scored(s.a(), s.coinsPerHour(), s.sbXpPerHour(), s.measured(),
                    moneyWeight * s.coinsPerHour() / maxC + (1 - moneyWeight) * s.sbXpPerHour() / maxX));
        list.sort(Comparator.comparingDouble(Scored::score).reversed());
        return list;
    }

    // (skill xp/h / xp to next lvl) * sb xp that lvl gives
    public static double sbXpFromSkill(String skill, double skillXpPerHour) {
        if (skill == null || skill.isEmpty() || skillXpPerHour <= 0) return 0;
        int level = currentLevel(skill);
        if (level < 0) level = 20; // unknown: assume mid game, ranking only
        if (level >= 60) return 0;
        long need = Profile.SKILL_XP[Math.min(level + 1, Profile.SKILL_XP.length - 1)] - Profile.SKILL_XP[level];
        if (need <= 0) return 0;
        int next = level + 1;
        int sbPerLevel = next <= 10 ? 5 : next <= 25 ? 10 : next <= 50 ? 20 : 30;
        return skillXpPerHour / need * sbPerLevel;
    }

    public static int currentLevel(String skill) {
        Profile.Data d = Profile.current;
        if (d != null && d.skills.containsKey(skill)) return d.skills.get(skill).level();
        if (d != null && d.fromGame) return -1; // that list has every profile, this one didnt show the skill yet
        double[] o = Store.I.observedSkills.get(skill);
        return o == null ? -1 : (int) o[0];
    }

    private static double flipRate() {
        double capital = Config.I.flipCapital > 0 ? Config.I.flipCapital : Math.max(0, GameState.purse) + Math.max(0, GameState.bank);
        double sum = 0;
        int n = 0;
        for (Prices.Flip f : Prices.flips(capital / 4, 4)) {
            sum += f.coinsPerHour();
            n++;
        }
        return n == 0 ? 0 : sum;
    }

    // measured coins/h on the island youre on (hud + dashboard)
    public static double currentRate() {
        Store.Rate r = Store.I.rates.get(Tracker.activity());
        return r == null ? 0 : r.coinsPerHour();
    }
}
