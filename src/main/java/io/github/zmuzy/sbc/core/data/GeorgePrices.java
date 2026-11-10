// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker, NotEnoughUpdates; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zmuzy.sbc.core.data.DataFiles.Source;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// what george pays for a pet. like skyblocker GeorgePriceTooltip (pet with 0 xp, looked up by "TYPE;tier", no
// line without a price) and skyhanni HypixelItemApi
public final class GeorgePrices {
    private static final String FILE = "george.json";
    private static Map<String, Double> prices;

    private GeorgePrices() {}

    // the files prices, empty if the shape is unknown
    static Map<String, Double> parse(JsonObject file) {
        Map<String, Double> out = new HashMap<>();
        if (file == null || !file.has("prices") || !file.get("prices").isJsonObject()) return out;
        for (Map.Entry<String, JsonElement> e : file.getAsJsonObject("prices").entrySet()) {
            JsonElement v = e.getValue();
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isNumber()) out.put(e.getKey(), v.getAsDouble());
        }
        return out;
    }

    private static synchronized Map<String, Double> prices() {
        if (prices == null) prices = parse(DataFiles.object(Source.NEU, FILE));
        return prices;
    }

    // from skyhanni LorenzRarity.neuPetIndex + PetInfo.tierIndex: COMMON = 0, VERY_SPECIAL = 8, -1 otherwise
    static int tierIndex(String tier) {
        return switch (tier == null ? "" : tier.toUpperCase(Locale.ROOT)) {
            case "COMMON" -> 0;
            case "UNCOMMON" -> 1;
            case "RARE" -> 2;
            case "EPIC" -> 3;
            case "LEGENDARY" -> 4;
            case "MYTHIC" -> 5;
            case "DIVINE" -> 6;
            case "SPECIAL" -> 7;
            case "VERY_SPECIAL" -> 8;
            default -> -1;
        };
    }

    // from skyhanni getNpcPrice, the george half, by "TYPE;tier". -1 = he doesnt buy it
    public static double price(String petId) {
        Double p = prices().get(petId);
        return p == null ? -1 : p;
    }

    // from skyblocker GeorgePriceTooltip.addToTooltip: only a 0 xp pet, -1 otherwise
    public static double forPet(String type, String tier, double exp) {
        if (type == null || type.isEmpty() || exp > 0) return -1;
        int index = tierIndex(tier);
        return index < 0 ? -1 : price(type + ";" + index);
    }

    // tests: read again
    static synchronized void forget() {
        prices = null;
    }
}
