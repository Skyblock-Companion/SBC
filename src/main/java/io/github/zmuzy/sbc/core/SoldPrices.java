// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zmuzy.sbc.SbcMod;
import net.minecraft.nbt.CompoundTag;

import java.util.HashMap;
import java.util.Map;

// what items really sold for: a collector reads every auction that ended for 3 days and the data repo shares,
// per item, the median price a piece and how many sold a day. a missing or old file, or under a day of sales,
// shows nothing
public final class SoldPrices {
    public record Sold(double median, double perDay, int sales) {}

    private static final String SHARED = "https://raw.githubusercontent.com/Skyblock-Companion/sbc-data/sold/sold.json";
    private static final long EVERY_MS = 10 * 60_000L, MAX_AGE_MS = 3 * 3600 * 1000L;
    // fewer sales than this in 3 days and the median says little
    static final int MIN_SALES = 3;
    // less than a day of sales and a few lucky hours would read as a busy day
    static final long MIN_SPAN_MS = 24 * 3600 * 1000L;
    private static volatile Map<String, Sold> items = Map.of();
    private static volatile long fetched;
    private static volatile boolean loading;

    private SoldPrices() {}

    public static void tick() {
        if (loading || System.currentTimeMillis() - fetched < EVERY_MS) return;
        if (!GameState.inSkyBlock && !SbcMod.DEV) return;
        loading = true;
        Net.getJson(SHARED, null).whenComplete((json, e) -> {
            fetched = System.currentTimeMillis();
            loading = false;
            Map<String, Sold> got = e == null ? parse(json, fetched) : null;
            items = got == null ? Map.of() : got;
        });
    }

    // the shared file, null when it is too old or cannot be read. pure, for tests
    static Map<String, Sold> parse(JsonElement json, long now) {
        try {
            JsonObject o = json.getAsJsonObject();
            if (now - o.get("updated").getAsLong() > MAX_AGE_MS) return null;
            if (o.get("updated").getAsLong() - o.get("since").getAsLong() < MIN_SPAN_MS) return null;
            Map<String, Sold> out = new HashMap<>();
            for (Map.Entry<String, JsonElement> en : o.getAsJsonObject("items").entrySet()) {
                JsonObject it = en.getValue().getAsJsonObject();
                int sales = it.get("sales").getAsInt();
                if (sales < MIN_SALES) continue;
                out.put(en.getKey(), new Sold(it.get("median").getAsDouble(), it.get("perDay").getAsDouble(), sales));
            }
            return out;
        } catch (RuntimeException e) {
            return null;
        }
    }

    // sales of this item's kind, null when there is no trustworthy number
    public static Sold of(CompoundTag ea) {
        try {
            String k = AuctionPrices.key(ea);
            return k == null ? null : items.get(k);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // "12/day", "0.5/day"
    public static String perDay(double v) {
        if (v >= 10) return String.valueOf(Math.round(v));
        return String.valueOf(Math.round(v * 10) / 10.0).replaceAll("\\.0$", "");
    }

    // tests
    static void forTests(Map<String, Sold> m) {
        items = m;
    }
}
