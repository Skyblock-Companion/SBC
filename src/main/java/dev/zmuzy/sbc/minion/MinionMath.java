// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.minion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemPrice;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.Prices;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// what a minion makes a day + whats worth adding, from the games own words + central prices. every fuel and
// upgrade says what it does on its item (item repo): "Increases..."
public final class MinionMath {
    // fuel like its item says: speed %, drop multiplier (1 = none), hours it lasts (0 = forever)
    public record Fuel(String id, double speed, double multiplier, double hours) {}

    // minion money/day: drops sold minus fuel, can every price be ranked on, where its from in words
    public record Estimate(double perDay, double fuelPerDay, boolean certain, String basis) {
        public static final Estimate NONE = new Estimate(0, 0, false, "no data for this minion");
    }

    // a change to a minion: what it adds a day, one time cost, days till it pays itself back
    public record Advice(String text, double extraPerDay, double cost, boolean certain) {
        public double daysToPayBack() {
            return extraPerDay <= 0 ? Double.MAX_VALUE : cost / extraPerDay;
        }
    }

    private static final Pattern SPEED = Pattern.compile("speed of your minion by (\\d+(?:\\.\\d+)?)%");
    private static final Pattern PERMANENT = Pattern.compile("permanently increases minion speed by (\\d+(?:\\.\\d+)?)%");
    private static final Pattern MULTIPLIER = Pattern.compile("resources created by (\\d+(?:\\.\\d+)?)x");
    private static final Pattern HOURS = Pattern.compile("for (\\d+(?:\\.\\d+)?) hours?");

    private static final Map<String, Fuel> FUELS = new LinkedHashMap<>();
    private static final Map<String, Double> UPGRADE_SPEED = new LinkedHashMap<>();
    private static double infusionSpeed;
    // crafting recipes by result: input id -> how many
    private static final Map<String, Map<String, Integer>> CRAFT = new HashMap<>();
    // enchanted form of a material + how many make one, from recipes
    private static final Map<String, Object[]> COMPACTED = new HashMap<>();
    private static boolean loaded;

    private MinionMath() {}

    // reads fuels, upgrades + recipes from the item repo once
    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        String items = dev.zmuzy.sbc.core.RepoData.read("items.min.json");
        if (items != null) readItems(items);
        String recipes = dev.zmuzy.sbc.core.RepoData.read("recipes.min.json");
        if (recipes != null) readRecipes(recipes);
        SbcMod.LOG.info("Minion math: {} fuels, {} speed upgrades, {} compactions", FUELS.size(), UPGRADE_SPEED.size(), COMPACTED.size());
    }

    static void readItems(String json) {
        try {
            JsonElement root = JsonParser.parseString(json);
            Iterable<JsonElement> all = root.isJsonArray() ? root.getAsJsonArray() : root.getAsJsonObject().asMap().values();
            for (JsonElement e : all) {
                if (!e.isJsonObject()) continue;
                JsonObject comp = e.getAsJsonObject().getAsJsonObject("components");
                if (comp == null || !comp.has("minecraft:custom_data") || !comp.has("minecraft:lore")) continue;
                JsonObject cd = comp.getAsJsonObject("minecraft:custom_data");
                if (!cd.has("id")) continue;
                StringBuilder b = new StringBuilder();
                for (JsonElement l : comp.getAsJsonArray("minecraft:lore")) b.append(' ').append(text(l));
                noteItem(cd.get("id").getAsString(), GameState.strip(b.toString()).replaceAll("\\s+", " ").trim());
            }
        } catch (RuntimeException ex) {
            SbcMod.LOG.warn("Could not read minion fuels from the item repo", ex);
        }
    }

    private static String text(JsonElement l) {
        if (!l.isJsonObject()) return l.isJsonPrimitive() ? l.getAsString() : "";
        JsonObject o = l.getAsJsonObject();
        StringBuilder b = new StringBuilder(o.has("text") ? o.get("text").getAsString() : "");
        if (o.has("extra")) for (JsonElement x : o.getAsJsonArray("extra")) b.append(text(x));
        return b.toString();
    }

    // one items lore on one line: keeps a fuel, speed upgrade or the infusion
    static void noteItem(String id, String lore) {
        Matcher perm = PERMANENT.matcher(lore);
        if (perm.find()) {
            if (id.equals("MITHRIL_INFUSION")) infusionSpeed = Double.parseDouble(perm.group(1));
            return;
        }
        if (lore.startsWith("Minion Fuel")) {
            Matcher sp = SPEED.matcher(lore), mu = MULTIPLIER.matcher(lore), hr = HOURS.matcher(lore);
            double speed = sp.find() ? Double.parseDouble(sp.group(1)) : 0;
            double mult = mu.find() ? Double.parseDouble(mu.group(1)) : 1;
            double hours = lore.contains("Unlimited Duration") ? 0 : hr.find() ? Double.parseDouble(hr.group(1)) : -1;
            // "during the day" (solar panel) or one minion only (inferno fuels) isnt a plain bonus, left out
            if ((speed > 0 || mult > 1) && hours >= 0 && !lore.contains("during the day") && !lore.contains("exclusively"))
                FUELS.put(id, new Fuel(id, speed, mult, hours));
        } else if (lore.startsWith("Minion Upgrade")) {
            Matcher sp = SPEED.matcher(lore);
            if (sp.find()) UPGRADE_SPEED.put(id, Double.parseDouble(sp.group(1)));
        }
    }

    static void readRecipes(String json) {
        try {
            for (JsonElement e : JsonParser.parseString(json).getAsJsonArray()) {
                JsonObject r = e.getAsJsonObject();
                if (!r.has("type") || !"crafting".equals(r.get("type").getAsString()) || !r.has("result") || !r.has("keys")) continue;
                JsonObject res = r.getAsJsonObject("result");
                if (!res.has("id")) continue;
                int made = res.has("count") ? res.get("count").getAsInt() : 1;
                var keys = r.getAsJsonArray("keys");
                Map<String, Integer> in = new HashMap<>();
                for (JsonElement p : r.getAsJsonArray("pattern")) {
                    int k = p.getAsInt();
                    if (k < 0 || k >= keys.size()) continue;
                    JsonObject key = keys.get(k).getAsJsonObject();
                    in.merge(key.get("id").getAsString(), key.has("count") ? key.get("count").getAsInt() : 1, Integer::sum);
                }
                String out = res.get("id").getAsString();
                CRAFT.putIfAbsent(out, in);
                // the super compactors job: one material in, its enchanted form out
                if (out.startsWith("ENCHANTED_") && in.size() == 1 && made == 1) {
                    var only = in.entrySet().iterator().next();
                    if (!only.getKey().startsWith("ENCHANTED_")) COMPACTED.putIfAbsent(only.getKey(), new Object[]{out, only.getValue()});
                }
            }
        } catch (RuntimeException ex) {
            SbcMod.LOG.warn("Could not read crafting recipes for minions", ex);
        }
    }

    public static Fuel fuel(String id) {
        return id == null ? null : FUELS.get(id);
    }

    public static Map<String, Fuel> fuels() {
        return FUELS;
    }

    // repo id of a fuel/upgrade like the menu names it ("Enchanted Lava Bucket")
    public static String idOf(String name) {
        if (name == null || name.isBlank()) return null;
        String plain = GameState.strip(name).trim();
        String id = ItemRepo.idForName(plain);
        return id != null ? id : Prices.idFromName(plain);
    }

    // what one drop brings: as is, or with a super compactor its enchanted form divided out
    static ItemPrice.Quote dropValue(String id, boolean compactor) {
        ItemPrice.Quote raw = ItemPrice.sellBulk(id);
        Object[] c = compactor ? COMPACTED.get(id) : null;
        if (c == null) return raw;
        ItemPrice.Quote ench = ItemPrice.sellBulk((String) c[0]);
        if (!ench.known()) return raw;
        return new ItemPrice.Quote(ench.coins() / (int) c[1], ench.source(), ench.certain());
    }

    // minion money/day with this fuel + these upgrades
    public static Estimate estimate(String type, int tier, String fuelId, List<String> upgradeIds, boolean infused) {
        load();
        MinionCatalog.Spec s = MinionCatalog.spec(type, tier);
        List<Object[]> drops = MinionCatalog.drops(type);
        if (s == null || drops == null) return Estimate.NONE;
        double speed = infused ? infusionSpeed : 0, mult = 1;
        Fuel f = fuel(fuelId);
        if (f != null) {
            speed += f.speed();
            mult = f.multiplier();
        }
        boolean compactor = false;
        for (String u : upgradeIds) {
            if (u == null) continue;
            speed += UPGRADE_SPEED.getOrDefault(u, 0.0);
            if (u.equals("SUPER_COMPACTOR_3000")) compactor = true;
        }
        double harvests = 86400 / (2 * s.delaySeconds() / (1 + speed / 100));
        double value = 0;
        boolean certain = true;
        for (Object[] d : drops) {
            ItemPrice.Quote q = dropValue((String) d[0], compactor);
            if (!q.known()) continue;
            if (!q.certain()) certain = false;
            value += harvests * (double) d[1] * mult * q.coins();
        }
        double fuelPerDay = 0;
        if (f != null && f.hours() > 0) {
            ItemPrice.Quote fq = ItemPrice.buy(f.id());
            if (!fq.certain()) certain = false;
            fuelPerDay = fq.coins() * 24 / f.hours();
        }
        String basis = String.format(Locale.ROOT, "%.0f harvests a day (%.1fs between actions, +%.0f%% speed)%s", harvests, s.delaySeconds(), speed,
                mult > 1 ? ", " + (int) mult + "x drops" : "") + (compactor ? ", sold compacted" : "") + (fuelPerDay > 0 ? ", less fuel" : "");
        return new Estimate(value - fuelPerDay, fuelPerDay, certain, basis);
    }

    // what the next tier costs besides the minion itself, by its recipe. NONE = no recipe or price
    public static ItemPrice.Quote nextTierCost(String type, int tier) {
        load();
        Map<String, Integer> in = CRAFT.get(type + "_GENERATOR_" + (tier + 1));
        if (in == null) return ItemPrice.Quote.NONE;
        double cost = 0;
        boolean certain = true;
        for (var e : in.entrySet()) {
            if (e.getKey().startsWith(type + "_GENERATOR_")) continue;
            ItemPrice.Quote q = ItemPrice.buy(e.getKey());
            if (!q.known()) return ItemPrice.Quote.NONE;
            if (!q.certain()) certain = false;
            cost += q.coins() * e.getValue();
        }
        return new ItemPrice.Quote(cost, ItemPrice.Source.BAZAAR_ORDER, certain);
    }

    // whats worth changing on a minion, best payback first: a lasting fuel better than the current one, each
    // speed upgrade or the super compactor for a free slot, the next tier. timed fuels count as cost
    public static List<Advice> advise(String type, int tier, String fuelId, List<String> upgradeIds, boolean infused) {
        load();
        Estimate now = estimate(type, tier, fuelId, upgradeIds, infused);
        List<Advice> out = new ArrayList<>();
        if (now == Estimate.NONE) return out;
        Fuel current = fuel(fuelId);
        for (Fuel f : FUELS.values()) {
            if (current != null && f.id().equals(current.id())) continue;
            Estimate with = estimate(type, tier, f.id(), upgradeIds, infused);
            double extra = with.perDay() - now.perDay();
            if (extra <= 0) continue;
            ItemPrice.Quote cost = ItemPrice.buy(f.id());
            if (!cost.known()) continue;
            String name = ItemRepo.name(f.id());
            if (f.hours() == 0) out.add(new Advice("Fuel: " + name + " (" + speedWords(f) + ", lasts forever)", extra, cost.coins(), with.certain() && cost.certain()));
            else out.add(new Advice("Fuel: " + name + " (" + speedWords(f) + " for " + (int) f.hours() + "h, cost counted)", extra, 0, with.certain()));
        }
        int free = Math.max(0, 2 - (int) upgradeIds.stream().filter(u -> u != null).count());
        if (free > 0) {
            List<String> candidates = new ArrayList<>(UPGRADE_SPEED.keySet());
            candidates.add("SUPER_COMPACTOR_3000");
            for (String u : candidates) {
                if (upgradeIds.contains(u)) continue;
                List<String> with = new ArrayList<>(upgradeIds);
                with.add(u);
                Estimate e = estimate(type, tier, fuelId, with, infused);
                double extra = e.perDay() - now.perDay();
                ItemPrice.Quote cost = ItemPrice.buy(u);
                if (extra <= 0 || !cost.known()) continue;
                out.add(new Advice("Upgrade: " + ItemRepo.name(u), extra, cost.coins(), e.certain() && cost.certain()));
            }
        }
        if (!infused && infusionSpeed > 0) {
            Estimate e = estimate(type, tier, fuelId, upgradeIds, true);
            ItemPrice.Quote cost = ItemPrice.buy("MITHRIL_INFUSION");
            double extra = e.perDay() - now.perDay();
            if (extra > 0 && cost.known()) out.add(new Advice("Mithril Infusion (+" + (int) infusionSpeed + "% speed, if it has none yet)", extra, cost.coins(), e.certain() && cost.certain()));
        }
        if (MinionCatalog.spec(type, tier + 1) != null) {
            Estimate next = estimate(type, tier + 1, fuelId, upgradeIds, infused);
            ItemPrice.Quote cost = nextTierCost(type, tier);
            double extra = next.perDay() - now.perDay();
            if (extra > 0 && cost.known()) out.add(new Advice("Tier " + (tier + 1) + " (by its recipe)", extra, cost.coins(), next.certain() && cost.certain()));
        }
        out.sort(Comparator.comparing((Advice a) -> !a.certain()).thenComparingDouble(Advice::daysToPayBack));
        return out;
    }

    private static String speedWords(Fuel f) {
        if (f.multiplier() > 1) return (int) f.multiplier() + "x drops";
        return "+" + (f.speed() == Math.rint(f.speed()) ? String.valueOf((int) f.speed()) : String.valueOf(f.speed())) + "% speed";
    }

    // tests: forget what was read
    static void resetForTests() {
        FUELS.clear();
        UPGRADE_SPEED.clear();
        CRAFT.clear();
        COMPACTED.clear();
        infusionSpeed = 0;
        loaded = true;
    }
}
