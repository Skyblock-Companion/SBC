// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// sack of sacks planner, the accessory helpers twin: every kind of sack is a family of sizes, only your biggest
// one of a kind counts, the rest of the family is what you are still missing
public final class SackHelper {
    public record Tier(String id, String name) {}

    public record Family(String root, String name, List<Tier> tiers, int owned) {
        public boolean done() {
            return owned == tiers.size() - 1;
        }

        public Tier top() {
            return tiers.get(tiers.size() - 1);
        }
    }

    // smallest first. trophy sacks go bronze, silver, gold
    private static final String[] SIZES = {"BEGINNER_", "SMALL_", "MEDIUM_", "LARGE_", "BRONZE_", "SILVER_", "GOLD_"};
    private static final int[] RANK = {0, 1, 2, 3, 0, 1, 2};

    private SackHelper() {}

    // a real sack holds things: its lore says how many of each
    public static boolean isSack(String id, List<String> lore) {
        if (!id.endsWith("_SACK")) return false;
        for (String l : lore) if (l.contains("Capacity")) return true;
        return false;
    }

    static int rank(String id) {
        for (int i = 0; i < SIZES.length; i++) if (id.startsWith(SIZES[i])) return RANK[i];
        return 0;
    }

    static String root(String id) {
        for (String s : SIZES) if (id.startsWith(s)) return id.substring(s.length());
        return id;
    }

    // "Large Agronomy Sack" -> "Agronomy Sack" when there are sizes, a sack with one size keeps its name
    static String familyName(List<Tier> tiers) {
        String top = tiers.get(tiers.size() - 1).name();
        if (tiers.size() == 1) return top;
        int space = top.indexOf(' ');
        return space < 0 ? top : top.substring(space + 1);
    }

    // sacks = id -> name of every sack the repo knows, owned = the ids you have
    public static List<Family> families(Map<String, String> sacks, Set<String> owned) {
        Map<String, List<Tier>> byRoot = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : sacks.entrySet()) byRoot.computeIfAbsent(root(e.getKey()), k -> new ArrayList<>()).add(new Tier(e.getKey(), e.getValue()));
        List<Family> out = new ArrayList<>();
        for (Map.Entry<String, List<Tier>> e : byRoot.entrySet()) {
            List<Tier> tiers = e.getValue();
            tiers.sort(Comparator.comparingInt(t -> rank(t.id())));
            int have = -1;
            for (int i = 0; i < tiers.size(); i++) if (owned.contains(tiers.get(i).id())) have = i;
            out.add(new Family(e.getKey(), familyName(tiers), List.copyOf(tiers), have));
        }
        out.sort(Comparator.comparing(f -> f.name().toLowerCase(Locale.ROOT)));
        return out;
    }
}
