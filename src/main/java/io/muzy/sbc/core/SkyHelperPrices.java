// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHelper; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.core;

import com.google.gson.JsonElement;
import io.muzy.sbc.SbcMod;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// skyhelpers public price list, same prices skycrypt networth uses: every item, pets by level + rarity
// ("lvl_100_legendary_golden_dragon"), skinned pets, shiny items, kuudra armor by attribute rolls
public final class SkyHelperPrices {
    private static final String URL = "https://raw.githubusercontent.com/SkyHelperBot/Prices/main/prices.json";
    private static volatile Map<String, Double> prices = Map.of();
    private static volatile long fetched;
    private static volatile boolean loading;

    private SkyHelperPrices() {}

    public static void tick() {
        if (loading || System.currentTimeMillis() - fetched < 30 * 60_000) return;
        loading = true;
        Net.getJson(URL, null).whenComplete((json, e) -> {
            loading = false;
            fetched = System.currentTimeMillis();
            if (e != null || json == null || !json.isJsonObject()) return;
            Map<String, Double> m = new HashMap<>();
            for (Map.Entry<String, JsonElement> en : json.getAsJsonObject().entrySet())
                if (en.getValue().isJsonPrimitive()) m.put(en.getKey(), en.getValue().getAsDouble());
            boolean first = prices.isEmpty();
            prices = m;
            SbcMod.LOG.info("SkyHelper prices: {}", m.size());
            // a profile loaded before prices came was valued without them, value it again once
            Profile.Data d = Profile.current;
            if (first && d != null && !d.uuid.isEmpty()) Profile.load(d.uuid, d.player);
        });
    }

    public static boolean ready() {
        return !prices.isEmpty();
    }

    // price for a skyhelper key (lowercase), 0 = not listed
    public static double get(String key) {
        return prices.getOrDefault(key.toLowerCase(Locale.ROOT), 0.0);
    }

    // pet at a level: skyhelper lists lvl 1 + max, anything between gets scaled by xp like skyhelper does
    public static double pet(String type, String tier, int level, double xpFraction, String skin) {
        String base = tier.toLowerCase(Locale.ROOT) + "_" + type.toLowerCase(Locale.ROOT);
        String suffix = skin == null || skin.isEmpty() ? "" : "_skinned_" + skin.toLowerCase(Locale.ROOT);
        int max = Pets.maxLevel(type);
        double low = get("lvl_1_" + base + suffix), top = get("lvl_" + max + "_" + base + suffix);
        if (max == 200 && top <= 0) top = get("lvl_100_" + base + suffix);
        if (low <= 0 && top <= 0) return 0;
        if (level >= max || low <= 0) return top > 0 ? (level >= max ? top : top * xpFraction) : low;
        if (top <= 0) return low;
        return low + (top - low) * xpFraction;
    }
}
