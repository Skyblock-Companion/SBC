// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item.value;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.SbcMod;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// from skyhanni Items.json (ItemsJson), just what EstimatedItemValue reads
// value_calculation_data (always active enchants, only tier 1 / tier 5 prices, end cap enchants),
// book_bundle_amount, crimson_prestige_costs, crimson_tiers, has_legacy_gemstone_slots
public final class ItemValueData {
    public record AlwaysActive(int level, Set<String> items) {}

    public record Endcap(int requiredLevel, String endcapItem) {}

    public static Map<String, AlwaysActive> alwaysActiveEnchants = Map.of();
    public static Set<String> onlyTierOnePrices = Set.of(), onlyTierFivePrices = Set.of();
    public static Map<String, List<Endcap>> endcapEnchants = Map.of();
    public static Map<String, Integer> bookBundleAmount = Map.of();
    // tier name (HOT..INFERNAL) -> item -> amount
    public static Map<String, Map<String, Integer>> crimsonPrestigeCosts = Map.of();
    public static Set<String> hasLegacyGemstoneSlots = Set.of();
    private static boolean loaded;

    private ItemValueData() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (var in = io.github.zmuzy.sbc.core.data.DataFiles.sh("Items.json")) {
            if (in == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject v = root.getAsJsonObject("value_calculation_data");
            if (v != null) {
                Map<String, AlwaysActive> aa = new HashMap<>();
                for (Map.Entry<String, JsonElement> e : v.getAsJsonObject("always_active_enchants").entrySet()) {
                    JsonObject o = e.getValue().getAsJsonObject();
                    Set<String> items = new HashSet<>();
                    for (JsonElement i : o.getAsJsonArray("items")) items.add(i.getAsString());
                    aa.put(e.getKey(), new AlwaysActive(o.get("level").getAsInt(), items));
                }
                alwaysActiveEnchants = aa;
                onlyTierOnePrices = strings(v, "only_tier_one_prices");
                onlyTierFivePrices = strings(v, "only_tier_five_prices");
                Map<String, List<Endcap>> ec = new HashMap<>();
                JsonObject en = v.getAsJsonObject("endcap_enchants_new");
                if (en != null) for (Map.Entry<String, JsonElement> e : en.entrySet()) {
                    if (!e.getValue().isJsonArray()) continue;
                    List<Endcap> l = new ArrayList<>();
                    for (JsonElement x : e.getValue().getAsJsonArray()) {
                        JsonObject o = x.getAsJsonObject();
                        l.add(new Endcap(o.get("required_level").getAsInt(), o.get("endcap_item").getAsString()));
                    }
                    ec.put(e.getKey(), l);
                }
                endcapEnchants = ec;
            }
            Map<String, Integer> bb = new HashMap<>();
            if (root.has("book_bundle_amount")) for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("book_bundle_amount").entrySet()) bb.put(e.getKey(), e.getValue().getAsInt());
            bookBundleAmount = bb;
            Map<String, Map<String, Integer>> cp = new HashMap<>();
            if (root.has("crimson_prestige_costs")) for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("crimson_prestige_costs").entrySet()) {
                Map<String, Integer> m = new HashMap<>();
                for (Map.Entry<String, JsonElement> i : e.getValue().getAsJsonObject().entrySet()) m.put(i.getKey(), i.getValue().getAsInt());
                cp.put(e.getKey(), m);
            }
            crimsonPrestigeCosts = cp;
            hasLegacyGemstoneSlots = strings(root, "has_legacy_gemstone_slots");
        } catch (Exception e) {
            SbcMod.LOG.warn("Bad Items.json", e);
        }
    }

    private static Set<String> strings(JsonObject o, String key) {
        Set<String> out = new HashSet<>();
        if (o.has(key)) for (JsonElement e : o.getAsJsonArray(key)) out.add(e.getAsString());
        return out;
    }
}
