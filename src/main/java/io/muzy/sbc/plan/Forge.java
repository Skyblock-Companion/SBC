// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, NotEnoughUpdates; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.plan;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.AuctionPrices;
import io.muzy.sbc.core.ItemPrice;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Profile;
import io.muzy.sbc.core.Store;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// forge profit. ingredients at buy orders, result at sell offer minus tax (or npc / lbin)
// quick forge from skyhanni HotmData. rows with a shaky price get shown but not ranked
public final class Forge {
    public record Input(String id, int count, ItemPrice.Quote price) {
        public double unitCost() {
            return price.coins();
        }
    }

    // seconds already has quick forge in it. perHour counts at least 1h bc nobody comes back to the forge every minute lol
    public record Row(String id, String name, int count, long seconds, long baseSeconds, double coins, List<Input> inputs, double cost,
                      ItemPrice.Quote result, double sell, double profit, double perHour, int hotm, boolean priced, boolean certain) {
        public double perDay() {
            return perHour * 24;
        }

        public String basis() {
            StringBuilder b = new StringBuilder("Sells at " + result.basis() + "; ingredients at buy orders");
            if (!certain) b.append("; some prices uncertain, not ranked");
            return b.toString();
        }
    }

    private static final List<JsonObject> recipes = new ArrayList<>();

    private Forge() {}

    public static void load() {
        String recipesJson = io.muzy.sbc.core.RepoData.read("recipes.min.json");
        if (recipesJson == null) return;
        try {
            for (JsonElement e : JsonParser.parseString(recipesJson).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                if (o.has("type") && "forge".equals(o.get("type").getAsString()) && o.has("result")) recipes.add(o);
            }
            SbcMod.LOG.info("Forge: {} recipes", recipes.size());
        } catch (Exception ex) {
            SbcMod.LOG.warn("Could not read forge recipes", ex);
        }
    }

    public static boolean available() {
        return !recipes.isEmpty();
    }

    // -1 = dont know yet
    public static int hotm() {
        Profile.Data d = Profile.current;
        return d == null || d.hotm == null ? -1 : d.hotm.level();
    }

    // api calls it forge_time, the layout calls it quick_forge, so check both
    public static int quickForgeLevel() {
        Profile.Data d = Profile.current;
        if (d == null) return 0;
        Map<String, Integer> nodes = d.hotmNodes;
        if (d.hotmOff.contains("quick_forge") || d.hotmOff.contains("forge_time")) return 0;
        return Math.max(nodes.getOrDefault("quick_forge", 0), nodes.getOrDefault("forge_time", 0));
    }

    // from skyhanni HotmData.QUICK_FORGE: 10% + 0.5%/lvl, 30% at 20
    public static double quickForgePercent(int level) {
        if (level <= 0) return 0;
        return level >= 20 ? 30.0 : 10.0 + level * 0.5;
    }

    // learned from the forge menu first, else hypixel item data, 0 = no idea
    public static int requiredTier(String id) {
        Integer learned = Store.I.forgeTiers.get(ItemRepo.name(id));
        if (learned != null) return learned;
        return ItemRepo.hotmRequirement(id);
    }

    public static List<Row> rows() {
        double quick = quickForgePercent(quickForgeLevel());
        List<Row> out = new ArrayList<>();
        for (JsonObject r : recipes) {
            Row row = row(r, quick);
            if (row != null) out.add(row);
        }
        // certain + profitable first, the rest after
        out.sort(Comparator.comparing((Row x) -> !x.certain()).thenComparing(Comparator.comparingDouble(Row::perHour).reversed()));
        return out;
    }

    static Row row(JsonObject r, double quickPercent) {
        JsonObject res = r.getAsJsonObject("result");
        // fossil pets come as {type: pet, pet: AMMONITE, tier: LEGENDARY}
        boolean pet = res.has("pet");
        if (!pet && !res.has("id")) return null;
        String id = pet ? "PET:" + res.get("pet").getAsString() + ":" + (res.has("tier") ? res.get("tier").getAsString() : "LEGENDARY")
                : res.get("id").getAsString();
        int count = res.has("count") ? res.get("count").getAsInt() : 1;
        long base = r.has("time") ? r.get("time").getAsLong() : 0;
        long seconds = Math.round(base * (1 - quickPercent / 100));
        double coins = r.has("coins") ? r.get("coins").getAsDouble() : 0;
        List<Input> inputs = new ArrayList<>();
        double cost = coins;
        boolean priced = true, certain = true;
        if (r.has("inputs")) for (JsonElement ie : r.getAsJsonArray("inputs")) {
            JsonObject in = ie.getAsJsonObject();
            if (!in.has("id")) continue;
            String iid = in.get("id").getAsString();
            int n = in.has("count") ? in.get("count").getAsInt() : 1;
            ItemPrice.Quote q = ItemPrice.buy(iid);
            if (!q.known()) priced = false;
            if (!q.certain()) certain = false;
            inputs.add(new Input(iid, n, q));
            cost += q.coins() * n;
        }
        String[] pp = pet ? id.split(":") : null;
        ItemPrice.Quote result;
        if (pet) {
            double bin = AuctionPrices.pet(pp[1], pp[2], 0, null);
            result = bin > 0 ? new ItemPrice.Quote(bin, ItemPrice.Source.AUCTION, ItemPrice.auctionCertain(AuctionPrices.petKey(pp[1], pp[2], false)))
                    : ItemPrice.Quote.NONE;
        } else result = ItemPrice.sell(id);
        if (!result.known()) priced = false;
        if (!result.certain()) certain = false;
        double sell = result.coins() * count;
        double profit = sell - cost;
        double hours = Math.max(1.0, seconds / 3600.0);
        String name = pet ? pp[2].charAt(0) + pp[2].substring(1).toLowerCase() + " " + Profile.cap(pp[1].replace('_', ' ')) + " Pet" : ItemRepo.name(id);
        return new Row(id, name, count, seconds, base, coins, inputs, cost, result, sell, profit, profit / hours, pet ? 0 : requiredTier(id),
                priced, priced && certain);
    }
}
