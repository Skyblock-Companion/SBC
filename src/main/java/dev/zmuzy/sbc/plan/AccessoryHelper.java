// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.plan;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

// accessory bag planner. only the best acc of a family counts, so a step = MP of the new tier minus what you have
// each family gets priced at its best MP per coin tier, then we walk the purse down the list
public final class AccessoryHelper {
    public record Tier(String id, String name, int rarity) {}

    public record Family(String root, List<Tier> tiers, int owned) {
        public int ownedMp() {
            return owned < 0 ? 0 : MP[tiers.get(owned).rarity()];
        }
    }

    public record Step(Family family, Tier tier, int gain, double cost) {
        public double perMp() {
            return cost / Math.max(1, gain);
        }
    }

    public record Plan(List<Step> steps, int affordable, int affordableMp, double affordableCost, int totalMp, double totalCost) {}

    public static final int[] MP = {3, 5, 8, 12, 16, 22, 3, 5};

    public static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "SPECIAL", "VERY_SPECIAL"};
    private static final String[] TIER_WORDS = {"TALISMAN", "RING", "ARTIFACT", "RELIC"};

    private AccessoryHelper() {}

    private static int rarityIndex(String tier) {
        for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equalsIgnoreCase(tier)) return i;
        return 0;
    }

    // "Ring of Love" + "Love Talisman" = same family, just strip the tier words
    private static String stemOf(String name) {
        StringBuilder sb = new StringBuilder();
        for (String w : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9' ]", " ").split(" ")) {
            if (w.isEmpty() || w.equals("talisman") || w.equals("ring") || w.equals("artifact") || w.equals("relic") || w.equals("of") || w.equals("the")) continue;
            sb.append(w).append(' ');
        }
        return sb.toString().trim();
    }

    // talisman_upgrades from the repo. names alone miss stuff like Shady Ring -> Crooked Artifact
    private static Map<String, List<String>> upgrades;
    // talismans that never count for the bag
    private static Set<String> ignored;

    private static synchronized void loadChains() {
        if (upgrades != null) return;
        upgrades = new HashMap<>();
        ignored = new HashSet<>();
        try (InputStream in = dev.zmuzy.sbc.core.data.DataFiles.neu("misc.json")) {
            if (in == null) return;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : o.getAsJsonObject("talisman_upgrades").entrySet()) {
                List<String> to = new ArrayList<>();
                for (var id : e.getValue().getAsJsonArray()) to.add(id.getAsString());
                upgrades.put(e.getKey(), to);
            }
            for (var id : o.getAsJsonArray("ignored_talisman")) ignored.add(id.getAsString());
        } catch (Exception e) {
            dev.zmuzy.sbc.SbcMod.LOG.warn("Could not read the talisman upgrades", e);
        }
    }

    public static List<Family> families(Map<String, String[]> accessories, Set<String> owned) {
        loadChains();
        // start of each chain + where every id sits in it
        Set<String> upgraded = new HashSet<>();
        for (List<String> to : upgrades.values()) upgraded.addAll(to);
        Map<String, String> chainRoot = new HashMap<>();
        Map<String, Integer> place = new HashMap<>();
        for (Map.Entry<String, List<String>> e : upgrades.entrySet()) {
            if (upgraded.contains(e.getKey())) continue;
            chainRoot.put(e.getKey(), e.getKey());
            place.put(e.getKey(), 0);
            for (int i = 0; i < e.getValue().size(); i++) {
                chainRoot.put(e.getValue().get(i), e.getKey());
                place.put(e.getValue().get(i), i + 1);
            }
        }
        Map<String, List<Tier>> byRoot = new LinkedHashMap<>();
        for (Map.Entry<String, String[]> e : accessories.entrySet()) {
            String id = e.getKey(), root = id;
            if (ignored.contains(id)) continue;
            if (chainRoot.containsKey(id)) {
                byRoot.computeIfAbsent(chainRoot.get(id), k -> new ArrayList<>()).add(new Tier(id, e.getValue()[0], rarityIndex(e.getValue()[1])));
                continue;
            }
            for (String t : TIER_WORDS)
                if (id.endsWith("_" + t)) {
                    root = id.substring(0, id.length() - t.length() - 1);
                    break;
                }
            String stem = stemOf(e.getValue()[0]);
            if (!stem.isEmpty()) root = stem;
            byRoot.computeIfAbsent(root, k -> new ArrayList<>()).add(new Tier(id, e.getValue()[0], rarityIndex(e.getValue()[1])));
        }
        List<Family> out = new ArrayList<>();
        for (Map.Entry<String, List<Tier>> e : byRoot.entrySet()) {
            List<Tier> list = e.getValue();
            list.sort(Comparator.<Tier>comparingInt(t -> place.getOrDefault(t.id(), Integer.MAX_VALUE)).thenComparingInt(Tier::rarity).thenComparing(Tier::id));
            int ownedIdx = -1;
            for (int i = 0; i < list.size(); i++) if (owned.contains(list.get(i).id())) ownedIdx = i;
            out.add(new Family(e.getKey(), list, ownedIdx));
        }
        return out;
    }

    // null = nothing higher you can buy
    static Step best(Family f, ToDoubleFunction<String> price) {
        Step pick = null;
        for (int i = f.owned() + 1; i < f.tiers().size(); i++) {
            Tier t = f.tiers().get(i);
            int gain = MP[t.rarity()] - f.ownedMp();
            double cost = price.applyAsDouble(t.id());
            if (gain <= 0 || cost <= 0) continue;
            Step s = new Step(f, t, gain, cost);
            if (pick == null || s.perMp() < pick.perMp()) pick = s;
        }
        return pick;
    }

    public static Plan plan(List<Family> families, ToDoubleFunction<String> price, double purse) {
        List<Step> steps = new ArrayList<>();
        for (Family f : families) {
            Step s = best(f, price);
            if (s != null) steps.add(s);
        }
        steps.sort(Comparator.comparingDouble(Step::perMp).thenComparing(s -> s.tier().name()));
        int count = 0, mp = 0, total = 0;
        double spent = 0, all = 0;
        boolean out = false;
        for (Step s : steps) {
            total += s.gain();
            all += s.cost();
            if (!out && spent + s.cost() <= purse) {
                spent += s.cost();
                mp += s.gain();
                count++;
            } else out = true;
        }
        return new Plan(steps, count, mp, spent, total, all);
    }
}
