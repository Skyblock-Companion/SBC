// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

// pet score helper. points per pet by rarity, best copy counts, max level +1
// score gives magic find at fixed steps + 3 sb xp a point, so we price every step in coins per point
public final class PetHelper {
    public static final String[] RARITY = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
    // pet score -> magic find
    public static final int[][] MAGIC_FIND = {{10, 1}, {25, 2}, {50, 3}, {75, 4}, {100, 5}, {130, 6}, {175, 7}, {225, 8}, {275, 9}, {325, 10}, {375, 11}, {450, 12}, {500, 13}};
    // wiki says these give no score
    private static final Set<String> NO_SCORE = Set.of("PHOENIX");

    public record Owned(String type, String name, String tier, int level, int maxLevel, double exp, double xpForMax, boolean active, String held, double value) {
        public boolean atMax() {
            return level >= maxLevel;
        }

        public double xpLeft() {
            return Math.max(0, xpForMax - exp);
        }
    }

    public enum Kind { BUY, UPGRADE, LEVEL }

    // cost -1 = no price
    public record Step(Kind kind, String type, String from, String to, int points, double cost, double xpLeft) {
        public double coinsPerPoint() {
            return cost < 0 ? Double.MAX_VALUE : cost / Math.max(1, points);
        }
    }

    public record Plan(int score, int magicFind, int xp, int nextAt, int nextMagicFind, List<Step> buys, List<Step> levels, int stepsToNext, double costToNext, boolean canReachNext) {}

    private PetHelper() {}

    public static int rarity(String tier) {
        for (int i = 0; i < RARITY.length; i++) if (RARITY[i].equalsIgnoreCase(tier)) return i;
        return -1;
    }

    public static int points(String tier) {
        return rarity(tier) + 1;
    }

    // rarest copy, highest level if tied
    public static Map<String, Owned> best(List<Owned> mine) {
        Map<String, Owned> out = new HashMap<>();
        for (Owned p : mine) {
            Owned b = out.get(p.type());
            if (b == null || rarity(p.tier()) > rarity(b.tier()) || rarity(p.tier()) == rarity(b.tier()) && p.level() > b.level()) out.put(p.type(), p);
        }
        return out;
    }

    public static int score(List<Owned> mine) {
        int s = 0;
        for (Owned p : best(mine).values()) {
            if (NO_SCORE.contains(p.type()) || rarity(p.tier()) < 0) continue;
            s += points(p.tier()) + (p.atMax() ? 1 : 0);
        }
        return s;
    }

    public static int magicFind(int score) {
        int mf = 0;
        for (int[] t : MAGIC_FIND) if (score >= t[0]) mf = t[1];
        return mf;
    }

    // price = cheapest one on the ah, <= 0 means unknown
    public static Plan plan(List<Owned> mine, Map<String, Set<String>> tiers, BiFunction<String, String, Double> price) {
        Map<String, Owned> best = best(mine);
        int score = score(mine);
        List<Step> buys = new ArrayList<>(), levels = new ArrayList<>();
        for (var e : tiers.entrySet()) {
            String type = e.getKey();
            if (NO_SCORE.contains(type)) continue;
            Owned have = best.get(type);
            int from = have == null ? -1 : rarity(have.tier());
            int already = have == null ? 0 : points(have.tier()) + (have.atMax() ? 1 : 0);
            Step pick = null;
            for (String tier : e.getValue()) {
                int r = rarity(tier);
                if (r <= from || r < 0) continue;
                int gain = r + 1 - already;
                if (gain <= 0) continue;
                double p = price.apply(type, tier);
                Step s = new Step(have == null ? Kind.BUY : Kind.UPGRADE, type, have == null ? "" : have.tier(), tier, gain, p > 0 ? p : -1, 0);
                if (pick == null || s.coinsPerPoint() < pick.coinsPerPoint() || s.cost() < 0 && pick.cost() < 0 && s.points() < pick.points()) pick = s;
            }
            if (pick != null) buys.add(pick);
            if (have != null && !have.atMax()) levels.add(new Step(Kind.LEVEL, type, have.tier(), have.tier(), 1, -1, have.xpLeft()));
        }
        buys.sort(Comparator.comparingDouble(Step::coinsPerPoint).thenComparing(Step::type));
        levels.sort(Comparator.comparingDouble(Step::xpLeft));
        int nextAt = -1, nextMf = -1;
        for (int[] t : MAGIC_FIND) {
            if (score < t[0]) {
                nextAt = t[0];
                nextMf = t[1];
                break;
            }
        }
        // cheapest points first until the next mf step
        int gained = 0, steps = 0;
        double cost = 0;
        Set<String> used = new HashSet<>();
        boolean reach = nextAt < 0;
        if (nextAt >= 0) for (Step s : buys) {
            if (s.cost() < 0 || !used.add(s.type())) continue;
            gained += s.points();
            cost += s.cost();
            steps++;
            if (score + gained >= nextAt) {
                reach = true;
                break;
            }
        }
        return new Plan(score, magicFind(score), score * 3, nextAt, nextMf, buys, levels, steps, cost, reach);
    }
}
