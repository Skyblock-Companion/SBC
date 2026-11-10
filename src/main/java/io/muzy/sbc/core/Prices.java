// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// live bazaar prices from hypixels public api (no key), every 90 sec while in skyblock
public final class Prices {
    public record Product(String id, double buy, double sell, double buyWeek, double sellWeek, int buyOrders, int sellOrders) {}

    public record Flip(Product product, double marginEach, double marginPct, double itemsPerHour, double coinsPerHour, long maxAffordable) {}

    // order book like the api lists it: amount, pricePerUnit, best first
    public record Book(double[][] buySummary, double[][] sellSummary) {}

    private static volatile Map<String, Product> products = Map.of();
    private static volatile Map<String, Book> books = Map.of();
    private static volatile long fetched;
    private static volatile boolean loading;
    public static volatile String error = "";

    private Prices() {}

    public static void tick() {
        long now = System.currentTimeMillis();
        if (loading || now - fetched < 90_000) return;
        loading = true;
        Net.getJson("https://api.hypixel.net/v2/skyblock/bazaar", null).whenComplete((json, e) -> {
            loading = false;
            fetched = System.currentTimeMillis();
            if (e != null) {
                error = "Bazaar unavailable";
                return;
            }
            Map<String, Product> map = new HashMap<>();
            Map<String, Book> bookMap = new HashMap<>();
            JsonObject ps = json.getAsJsonObject().getAsJsonObject("products");
            for (Map.Entry<String, JsonElement> en : ps.entrySet()) {
                JsonObject q = en.getValue().getAsJsonObject().getAsJsonObject("quick_status");
                if (q == null) continue;
                map.put(en.getKey(), new Product(en.getKey(), d(q, "buyPrice"), d(q, "sellPrice"), d(q, "buyMovingWeek"),
                        d(q, "sellMovingWeek"), (int) d(q, "buyOrders"), (int) d(q, "sellOrders")));
                JsonObject product = en.getValue().getAsJsonObject();
                bookMap.put(en.getKey(), new Book(summary(product, "buy_summary"), summary(product, "sell_summary")));
            }
            products = map;
            books = bookMap;
            error = "";
        });
    }

    private static double[][] summary(JsonObject product, String key) {
        if (!product.has(key) || !product.get(key).isJsonArray()) return new double[0][];
        var a = product.getAsJsonArray(key);
        double[][] out = new double[a.size()][];
        for (int i = 0; i < out.length; i++) {
            JsonObject o = a.get(i).getAsJsonObject();
            out[i] = new double[]{d(o, "amount"), d(o, "pricePerUnit")};
        }
        return out;
    }

    public static Book book(String id) {
        return books.get(id);
    }

    private static double d(JsonObject o, String k) {
        return o.has(k) ? o.get(k).getAsDouble() : 0;
    }

    // from skyblocker ItemPrice.refreshItemPrices: next tick fetches again
    public static void refresh() {
        fetched = 0;
    }

    public static boolean ready() {
        return !products.isEmpty();
    }

    public static Map<String, Product> all() {
        return products;
    }

    public static Product get(String id) {
        return products.get(id);
    }

    // insta sell price (highest buy order)
    public static double sell(String id) {
        Product p = products.get(id);
        return p == null ? 0 : p.sell();
    }

    public static String idFromName(String displayName) {
        String n = GameState.strip(displayName).trim();
        int x = n.lastIndexOf(" x");
        if (x > 0 && n.substring(x + 2).chars().allMatch(Character::isDigit)) n = n.substring(0, x);
        return n.toUpperCase(Locale.ROOT).replace("'", "").replace("-", "_").replaceAll("[^A-Z0-9_]+", "_").replaceAll("_+", "_").replaceAll("^_|_$", "");
    }

    // order flip ideas: buy order at the top bid, sell offer at the lowest ask. profit = spread minus 1.25% tax.
    // per hour = a safe 5% share of the slower sides weekly volume
    public static List<Flip> flips(double capital, int limit) {
        List<Flip> out = new ArrayList<>();
        for (Product p : products.values()) {
            if (p.sell() <= 0 || p.buy() <= p.sell()) continue;
            double margin = p.buy() * (1 - 0.0125) - p.sell();
            if (margin <= 0) continue;
            double slowerPerHour = Math.min(p.buyWeek(), p.sellWeek()) / (7 * 24);
            if (slowerPerHour < 20) continue;
            double share = slowerPerHour * 0.05;
            long affordable = capital > 0 ? (long) (capital / p.sell()) : Long.MAX_VALUE;
            double perHour = Math.min(share, affordable);
            out.add(new Flip(p, margin, margin / p.sell() * 100, perHour, perHour * margin, affordable));
        }
        out.sort(Comparator.comparingDouble(Flip::coinsPerHour).reversed());
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    public static String coins(double v) {
        double a = Math.abs(v);
        if (a >= 1e9) return String.format(Locale.ROOT, "%.2fB", v / 1e9);
        if (a >= 1e6) return String.format(Locale.ROOT, "%.2fM", v / 1e6);
        if (a >= 1e3) return String.format(Locale.ROOT, "%.1fk", v / 1e3);
        return String.format(Locale.ROOT, "%.0f", v);
    }
}
