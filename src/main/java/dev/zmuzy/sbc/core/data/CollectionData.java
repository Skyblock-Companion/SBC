// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// which collections exist, their category, what each tier takes: hypixels public collections list copied
// daily into our data repo + a copy in the jar. from skyblocker profileviewer
public final class CollectionData {
    public static final String FILE = "collections.json";

    // tiers[i] = what tier i + 1 takes
    public record Item(String id, String name, long[] tiers) {
        public int maxTier() {
            return tiers.length;
        }

        // from skyblocker getMaxUnlockedTier: highest tier this amount hits
        public int tier(long collected) {
            for (int i = tiers.length - 1; i >= 0; i--) if (tiers[i] <= collected) return i + 1;
            return 0;
        }

        public boolean maxed(long collected) {
            return tiers.length > 0 && tier(collected) == tiers.length;
        }

        // what the next tier takes in total, -1 once theres none
        public long next(long collected) {
            int t = tier(collected);
            return t >= tiers.length ? -1 : tiers[t];
        }
    }

    public record Category(String id, String name, List<Item> items) {}

    private static volatile List<Category> categories;

    private CollectionData() {}

    public static List<Category> categories() {
        List<Category> c = categories;
        if (c == null) {
            JsonObject file = DataFiles.object(DataFiles.Source.HYPIXEL, FILE);
            c = file == null ? List.of() : parse(file);
            categories = c;
        }
        return c;
    }

    // a weird file gives what could be read, never throws. the page falls back to its plain list
    static List<Category> parse(JsonObject file) {
        List<Category> out = new ArrayList<>();
        try {
            for (Map.Entry<String, JsonElement> cat : file.getAsJsonObject("collections").entrySet()) {
                JsonObject o = cat.getValue().getAsJsonObject();
                List<Item> items = new ArrayList<>();
                for (Map.Entry<String, JsonElement> it : o.getAsJsonObject("items").entrySet()) {
                    JsonObject item = it.getValue().getAsJsonObject();
                    JsonArray arr = item.getAsJsonArray("tiers");
                    long[] tiers = new long[arr.size()];
                    for (int i = 0; i < tiers.length; i++) tiers[i] = arr.get(i).getAsJsonObject().get("amountRequired").getAsLong();
                    items.add(new Item(it.getKey(), item.get("name").getAsString(), tiers));
                }
                out.add(new Category(cat.getKey(), o.get("name").getAsString(), List.copyOf(items)));
            }
        } catch (RuntimeException e) {
            dev.zmuzy.sbc.SbcMod.LOG.warn("The collections list could not be read: {}", e.toString());
        }
        return List.copyOf(out);
    }
}
