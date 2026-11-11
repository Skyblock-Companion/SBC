// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// the few cheapest bin listings per item, so when the cheapest sells the price moves to the next one right
// away with no new scan. pure, for tests
final class Listings {
    // kept per item, an item rarely sells more than this between 2 scans
    static final int KEEP = 5;

    // max pages of new listings read in one go, however busy the ah is
    static final int MAX_NEW_PAGES = 5;

    record Listing(double price, String id) {}

    // listing seen on the first pages since the list was made
    record Fresh(String key, double price, String id, long start) {}

    private Listings() {}

    // adds a listing to a running scan, keeps only the cheapest few of that item
    static void add(Map<String, List<Listing>> all, String key, double price, String id) {
        List<Listing> list = all.computeIfAbsent(key, k -> new ArrayList<>(KEEP + 1));
        for (Listing known : list) if (known.id().equals(id)) return;
        int at = 0;
        while (at < list.size() && list.get(at).price() <= price) at++;
        if (at >= KEEP) return;
        list.add(at, new Listing(price, id));
        if (list.size() > KEEP) list.remove(KEEP);
    }

    // shared file items: each a list of [price, id] cheapest first
    static Map<String, List<Listing>> parse(JsonObject items) {
        Map<String, List<Listing>> out = new HashMap<>();
        for (Map.Entry<String, JsonElement> e : items.entrySet()) {
            List<Listing> list = new ArrayList<>();
            for (JsonElement entry : e.getValue().getAsJsonArray()) {
                JsonArray pair = entry.getAsJsonArray();
                list.add(new Listing(pair.get(0).getAsDouble(), pair.get(1).getAsString()));
            }
            if (!list.isEmpty()) out.put(e.getKey(), list);
        }
        return out;
    }

    // takes out what sold, says if anything went. an item with all its listings sold leaves the list so it doesnt
    // show an old price
    static boolean removeSold(Map<String, List<Listing>> all, Collection<String> sold) {
        boolean changed = false;
        for (List<Listing> list : all.values()) {
            for (int i = list.size() - 1; i >= 0; i--) {
                if (sold.contains(list.get(i).id())) {
                    list.remove(i);
                    changed = true;
                }
            }
        }
        all.values().removeIf(List::isEmpty);
        return changed;
    }

    // read the next page too? hypixel keeps bin listings newest first, so if even the oldest on a page is newer
    // than what we have, that whole page was new and more might be on the next
    static boolean readNextPage(long oldestBinStart, long seenUpTo, int page, int totalPages) {
        return oldestBinStart > seenUpTo && page + 1 < totalPages && page + 1 < MAX_NEW_PAGES;
    }

    // what the rest of the mod reads: cheapest price per item
    static Map<String, Double> cheapest(Map<String, List<Listing>> all) {
        Map<String, Double> out = new HashMap<>();
        all.forEach((key, list) -> out.put(key, list.get(0).price()));
        return out;
    }
}
