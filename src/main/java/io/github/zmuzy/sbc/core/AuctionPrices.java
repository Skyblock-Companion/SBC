// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker, SkyHelper; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zmuzy.sbc.SbcMod;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// lowest bin per item, for stuff that doesnt trade on the bazaar (weapons, armor, accs...). a job in the data
// repo reads every page of hypixels auctions every 10 min
public final class AuctionPrices {
    private static volatile Map<String, Double> lowest = Map.of();
    private static volatile long fetched, soldFetched, freshFetched;
    private static volatile boolean loading, soldLoading, freshLoading, fromShared;
    private static final String SHARED = "https://raw.githubusercontent.com/Skyblock-Companion/sbc-data/prices/lowest_bin.json";
    private static final String SOLD = "https://api.hypixel.net/v2/skyblock/auctions_ended";
    private static final long SHARED_MAX_AGE_MS = 3600 * 1000L, SHARED_EVERY_MS = 10 * 60_000L, SCAN_EVERY_MS = 20 * 60_000L, SOLD_EVERY_MS = 60_000L;
    private static final long REMEMBER_SOLD_MS = 90 * 60_000L, REMEMBER_FRESH_MS = 30 * 60_000L, OVERLAP_MS = 2 * 60_000L, FRESH_EVERY_MS = 60_000L;
    private static final String PAGES = "https://api.hypixel.net/v2/skyblock/auctions?page=";
    private static final int SHARED_MIN_ITEMS = 500;
    private static final Object LOCK = new Object();
    private static Map<String, List<Listings.Listing>> listings = new HashMap<>();
    private static volatile long scanStarted;
    // last whole list. only a whole list can say something isnt listed
    private static volatile long lastFullDump;
    // items with more listings than we kept. when the kept ones sell there might be others
    private static final java.util.Set<String> moreThanKept = java.util.concurrent.ConcurrentHashMap.newKeySet();
    // whats been on the ah since the game started
    private static final java.util.Set<String> everListed = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final long TOO_OLD_MS = 2 * 3600 * 1000L, WHOLE_FOR_MS = 3600 * 1000L;
    // what sold lately + when. a list made before a sale must not bring it back
    private static final Map<String, Long> recentlySold = new HashMap<>();
    // when each item sold over the last hour, by key. how much it really trades
    private static final Map<String, java.util.ArrayDeque<Long>> SALES = new HashMap<>();
    private static final long SALES_FOR_MS = 60 * 60_000L;
    // first time we read sales. before an hour of that, few sales doesnt mean low trade yet
    private static volatile long watchingSince;
    // what the first pages brought lately, a list made before these were listed must not lose them
    private static final List<Listings.Fresh> recentlyListed = new ArrayList<>();
    // start time of the newest listing we saw
    private static long seenUpTo;
    private AuctionPrices() {}

    public static void tick() {
        long now = System.currentTimeMillis();
        if (!lowest.isEmpty() && lastFullDump > 0 && now - lastFullDump > TOO_OLD_MS) {
            // no list for ages = an old price would be a wrong one
            synchronized (LOCK) {
                listings = new HashMap<>();
                lowest = Map.of();
            }
        }
        if (!lowest.isEmpty() && (GameState.inSkyBlock || SbcMod.DEV)) {
            if (!soldLoading && now - soldFetched >= SOLD_EVERY_MS) readSold();
            if (!freshLoading && now - freshFetched >= FRESH_EVERY_MS) {
                freshLoading = true;
                readFresh(0, new ArrayList<>(), Long.MIN_VALUE);
            }
        }
        if (loading || now - fetched < (fromShared ? SHARED_EVERY_MS : SCAN_EVERY_MS)) return;
        loading = true;
        Net.getJson(SHARED, null).whenComplete((json, e) -> {
            Map<String, List<Listings.Listing>> shared = e == null ? parseShared(json, System.currentTimeMillis()) : null;
            if (shared != null) {
                use(shared, true, json.getAsJsonObject().get("updated").getAsLong());
                SbcMod.LOG.info("Auction prices: {} items from the data repository", shared.size());
            } else {
                scanStarted = System.currentTimeMillis();
                page(0, new HashMap<>());
            }
        });
    }

    // shared file listings, null if too old or too small to trust. pure, for tests
    static Map<String, List<Listings.Listing>> parseShared(JsonElement json, long now) {
        try {
            JsonObject o = json.getAsJsonObject();
            if (now - o.get("updated").getAsLong() > SHARED_MAX_AGE_MS) return null;
            Map<String, List<Listings.Listing>> out = Listings.parse(o.getAsJsonObject("items"));
            return out.size() < SHARED_MIN_ITEMS ? null : out;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static void use(Map<String, List<Listings.Listing>> fresh, boolean shared, long madeAt) {
        synchronized (LOCK) {
            for (Listings.Fresh f : recentlyListed) if (f.start() > madeAt - OVERLAP_MS) Listings.add(fresh, f.key(), f.price(), f.id());
            seenUpTo = Math.max(seenUpTo, madeAt - OVERLAP_MS);
            moreThanKept.clear();
            fresh.forEach((key, list) -> {
                everListed.add(key);
                if (list.size() >= Listings.KEEP) moreThanKept.add(key);
            });
            Listings.removeSold(fresh, recentlySold.keySet());
            listings = fresh;
            lowest = Listings.cheapest(fresh);
        }
        lastFullDump = System.currentTimeMillis();
        fromShared = shared;
        fetched = System.currentTimeMillis();
        loading = false;
    }

    // new listings from page 1, and the next pages while a whole page is new. only new stuff gets decoded
    private static void readFresh(int p, List<Listings.Fresh> found, long newest) {
        Net.getJson(PAGES + p, null).whenComplete((json, e) -> {
            if (e != null || json == null) {
                finishFresh(found, Long.MIN_VALUE);
                return;
            }
            try {
                long since;
                synchronized (LOCK) {
                    since = seenUpTo;
                }
                JsonObject o = json.getAsJsonObject();
                long oldest = Long.MAX_VALUE, top = newest;
                boolean anyBin = false;
                for (JsonElement a : o.getAsJsonArray("auctions")) {
                    JsonObject au = a.getAsJsonObject();
                    if (!au.has("bin") || !au.get("bin").getAsBoolean()) continue;
                    long start = au.get("start").getAsLong();
                    anyBin = true;
                    oldest = Math.min(oldest, start);
                    top = Math.max(top, start);
                    if (start <= since) continue;
                    String[] idCount = decode(au.get("item_bytes").getAsString());
                    if (idCount == null) continue;
                    found.add(new Listings.Fresh(idCount[0], au.get("starting_bid").getAsDouble() / Math.max(1, Integer.parseInt(idCount[1])), au.get("uuid").getAsString(), start));
                }
                if (anyBin && Listings.readNextPage(oldest, since, p, o.get("totalPages").getAsInt())) readFresh(p + 1, found, top);
                else finishFresh(found, top);
            } catch (RuntimeException ex) {
                SbcMod.LOG.info("Could not read the new listings: {}", ex.toString());
                finishFresh(found, Long.MIN_VALUE);
            }
        });
    }

    private static void finishFresh(List<Listings.Fresh> found, long newest) {
        long now = System.currentTimeMillis();
        synchronized (LOCK) {
            recentlyListed.removeIf(f -> now - f.start() > REMEMBER_FRESH_MS);
            recentlyListed.addAll(found);
            for (Listings.Fresh f : found) {
                Listings.add(listings, f.key(), f.price(), f.id());
                everListed.add(f.key());
                if (listings.get(f.key()).size() >= Listings.KEEP) moreThanKept.add(f.key());
            }
            Listings.removeSold(listings, recentlySold.keySet());
            if (!found.isEmpty()) lowest = Listings.cheapest(listings);
            seenUpTo = Math.max(seenUpTo, newest);
        }
        freshFetched = now;
        freshLoading = false;
    }

    // what sold in the last minute, small list. fails = just wait for the next minute
    private static void readSold() {
        soldLoading = true;
        Net.getJson(SOLD, null).whenComplete((json, e) -> {
            soldFetched = System.currentTimeMillis();
            soldLoading = false;
            if (e != null || json == null) return;
            try {
                long now = System.currentTimeMillis();
                List<String> sold = new ArrayList<>();
                Map<String, String> keyOf = new HashMap<>();
                for (JsonElement a : json.getAsJsonObject().getAsJsonArray("auctions")) {
                    JsonObject au = a.getAsJsonObject();
                    String auctionId = au.get("auction_id").getAsString();
                    sold.add(auctionId);
                    if (au.has("item_bytes")) {
                        String[] idCount = decode(au.get("item_bytes").getAsString());
                        if (idCount != null && idCount[0] != null) keyOf.put(auctionId, idCount[0]);
                    }
                }
                synchronized (LOCK) {
                    if (watchingSince == 0) watchingSince = now;
                    recentlySold.values().removeIf(at -> now - at > REMEMBER_SOLD_MS);
                    for (String id : sold) {
                        // lists overlap minute to minute so a sale read twice counts once
                        if (recentlySold.containsKey(id)) continue;
                        String key = keyOf.get(id);
                        if (key != null) SALES.computeIfAbsent(key, k -> new java.util.ArrayDeque<>()).addLast(now);
                    }
                    for (String id : sold) recentlySold.put(id, now);
                    SALES.values().forEach(q -> {
                        while (!q.isEmpty() && now - q.peekFirst() > SALES_FOR_MS) q.pollFirst();
                    });
                    SALES.values().removeIf(java.util.ArrayDeque::isEmpty);
                    if (Listings.removeSold(listings, sold)) lowest = Listings.cheapest(listings);
                }
            } catch (RuntimeException ex) {
                SbcMod.LOG.info("Could not read what sold: {}", ex.toString());
            }
        });
    }

    // our own scan for when the shared file cant be used
    private static void page(int p, Map<String, List<Listings.Listing>> acc) {
        Net.getJson(PAGES + p, null).whenComplete((json, e) -> {
            if (e != null) {
                finish(new HashMap<>());
                return;
            }
            try {
                JsonObject o = json.getAsJsonObject();
                for (JsonElement a : o.getAsJsonArray("auctions")) {
                    JsonObject au = a.getAsJsonObject();
                    if (!au.has("bin") || !au.get("bin").getAsBoolean()) continue;
                    String[] idCount = decode(au.get("item_bytes").getAsString());
                    if (idCount == null) continue;
                    double each = au.get("starting_bid").getAsDouble() / Math.max(1, Integer.parseInt(idCount[1]));
                    Listings.add(acc, idCount[0], each, au.get("uuid").getAsString());
                }
                int total = o.get("totalPages").getAsInt();
                if (p + 1 < total && p < 150) page(p + 1, acc);
                else finish(acc);
            } catch (Exception ex) {
                finish(new HashMap<>()); // half a list would say the rest isnt for sale
            }
        });
    }

    private static void finish(Map<String, List<Listings.Listing>> acc) {
        if (!acc.isEmpty()) {
            use(acc, false, scanStarted);
        } else {
            fetched = System.currentTimeMillis();
            fromShared = false;
            loading = false;
        }
        SbcMod.LOG.info("Auction prices: {} items from the game's own scan", acc.size());
    }

    static String[] decode(String b64) {
        try {
            CompoundTag root = NbtIo.readCompressed(new ByteArrayInputStream(Base64.getDecoder().decode(b64)), NbtAccounter.unlimitedHeap());
            CompoundTag it = root.getListOrEmpty("i").getCompoundOrEmpty(0);
            CompoundTag ea = it.getCompoundOrEmpty("tag").getCompoundOrEmpty("ExtraAttributes");
            String key = key(ea);
            return key == null ? null : new String[]{key, key.startsWith("PET:") ? "1" : String.valueOf(it.getByteOr("Count", (byte) 1))};
        } catch (Exception e) {
            return null;
        }
    }

    // what an item gets priced under so each kind has its own price (sharpness V book != smite I book). null =
    // none, like a book with multiple enchants. pets go by type, rarity, max level or not
    public static String key(CompoundTag ea) {
        String id = ea.getStringOr("id", "");
        if (id.isEmpty()) return null;
        if (ea.contains("is_shiny")) return "SHINY_" + id;
        switch (id) {
            case "ENCHANTED_BOOK" -> {
                CompoundTag enchants = ea.getCompoundOrEmpty("enchantments");
                if (enchants.size() != 1) return null;
                String enchant = enchants.keySet().iterator().next();
                return "ENCHANTMENT_" + enchant.toUpperCase(Locale.ROOT) + "_" + enchants.getIntOr(enchant, 0);
            }
            case "RUNE", "UNIQUE_RUNE" -> {
                CompoundTag runes = ea.getCompoundOrEmpty("runes");
                if (runes.size() != 1) return null;
                String rune = runes.keySet().iterator().next();
                return rune.toUpperCase(Locale.ROOT) + "_RUNE;" + runes.getIntOr(rune, 0);
            }
            case "ATTRIBUTE_SHARD" -> {
                CompoundTag attributes = ea.getCompoundOrEmpty("attributes");
                if (attributes.size() != 1) return null;
                String attribute = attributes.keySet().iterator().next();
                return "ATTRIBUTE_SHARD_" + attribute.toUpperCase(Locale.ROOT) + ";" + attributes.getIntOr(attribute, 0);
            }
            case "POTION" -> {
                int level = ea.getIntOr("potion_level", 0);
                String name = ea.getStringOr("potion_name", "").replace(" ", "_"), potion = ea.getStringOr("potion", ""), type = ea.getStringOr("potion_type", "");
                if (!name.isEmpty()) return "POTION_" + name.toUpperCase(Locale.ROOT) + ";" + level;
                if (!potion.isEmpty()) return "POTION_" + potion.toUpperCase(Locale.ROOT) + ";" + level;
                return type.isEmpty() ? "WATER_BOTTLE" : "POTION_" + type.toUpperCase(Locale.ROOT);
            }
            case "PARTY_HAT_CRAB", "PARTY_HAT_CRAB_ANIMATED" -> {
                return "PARTY_HAT_CRAB_" + ea.getStringOr("party_hat_color", "").toUpperCase(Locale.ROOT) + (ea.getIntOr("party_hat_year", 0) == 2022 ? "_ANIMATED" : "");
            }
            case "BALLOON_HAT_2024", "BALLOON_HAT_2025" -> {
                return "BALLOON_HAT_" + ea.getIntOr("party_hat_year", 0) + "_" + ea.getStringOr("party_hat_color", "").toUpperCase(Locale.ROOT);
            }
            case "CAKE_HAT_2026" -> {
                return "CAKE_HAT_2026_" + ea.getStringOr("party_hat_color", "").toUpperCase(Locale.ROOT);
            }
            case "PARTY_HAT_SLOTH" -> {
                return "PARTY_HAT_SLOTH_" + ea.getStringOr("party_hat_emoji", "").toUpperCase(Locale.ROOT);
            }
            case "ABICASE" -> {
                return "ABICASE_" + ea.getStringOr("model", "").toUpperCase(Locale.ROOT);
            }
            case "FACTION_RABBIT" -> {
                return "FACTION_RABBIT_" + ea.getStringOr("faction_rabbit_id", "").toUpperCase(Locale.ROOT);
            }
            case "NEW_YEAR_CAKE" -> {
                return "NEW_YEAR_CAKE_" + ea.getIntOr("new_years_cake", 0);
            }
            case "MIDAS_SWORD" -> {
                return ea.getIntOr("winning_bid", 0) >= 50_000_000 ? "MIDAS_SWORD_50M" : id;
            }
            case "MIDAS_STAFF" -> {
                return ea.getIntOr("winning_bid", 0) >= 100_000_000 ? "MIDAS_STAFF_100M" : id;
            }
            default -> {
            }
        }
        if (id.equals("PET")) {
            // pets priced per type + rarity, max level separate from below
            com.google.gson.JsonObject info = com.google.gson.JsonParser.parseString(ea.getStringOr("petInfo", "{}")).getAsJsonObject();
            String type = info.has("type") ? info.get("type").getAsString() : "";
            String tier = info.has("tier") ? info.get("tier").getAsString() : "";
            double exp = info.has("exp") ? info.get("exp").getAsDouble() : 0;
            if (type.isEmpty()) return null;
            return petKey(type, tier, Pets.level(exp, tier, type) >= Pets.maxLevel(type));
        }
        return id;
    }

    // price of this exact item: its kinds if known, else its ids. books are left to their enchants, the caller adds those
    public static double valueOf(CompoundTag ea) {
        String id = ea.getStringOr("id", "");
        if (id.equals("ENCHANTED_BOOK")) return value(id);
        try {
            String k = key(ea);
            double v = k == null ? 0 : value(k);
            return v > 0 ? v : value(id);
        } catch (RuntimeException e) {
            return value(id);
        }
    }

    // lbin of this items kind, 0 = none
    public static double lowestBinOf(CompoundTag ea) {
        try {
            String k = key(ea);
            return k == null ? 0 : lowestBin(k);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // item has a market and nothing is listed rn. false whenever thats not certain
    public static boolean noneListed(CompoundTag ea) {
        try {
            String k = key(ea);
            return k != null && noneListed(k, ea.getStringOr("id", ""), System.currentTimeMillis());
        } catch (RuntimeException e) {
            return false;
        }
    }

    static boolean noneListed(String key, String id, long now) {
        if (lastFullDump == 0 || now - lastFullDump > WHOLE_FOR_MS || lowest.containsKey(key) || moreThanKept.contains(key)) return false;
        return everListed.contains(key) || SkyHelperPrices.get(id) > 0;
    }

    // tests: like a whole list just came in
    static void forTests(Map<String, List<Listings.Listing>> list, long now) {
        scanStarted = now;
        use(list, true, now);
        lastFullDump = now;
    }

    // tests: what sold, the way readSold takes it
    static void soldForTests(List<String> ids) {
        synchronized (LOCK) {
            if (Listings.removeSold(listings, ids)) lowest = Listings.cheapest(listings);
        }
    }

    public static String petKey(String type, String tier, boolean max) {
        return "PET:" + type + ":" + tier + (max ? ":MAX" : ":LOW");
    }

    // pet lbin: max level price scaled by how leveled it is (like most networth calcs)
    public static double pet(String type, String tier, double exp, String heldItem) {
        return pet(type, tier, exp, heldItem, null);
    }

    // pet value: skyhelpers price (skinned listing if there is one, else pet + skin), else ah lbin. plus the held item
    public static double pet(String type, String tier, double exp, String heldItem, String skin) {
        double frac = Math.min(1, exp / Math.max(1, Pets.xpForMax(tier, type)));
        int level = Pets.level(exp, tier, type);
        boolean skinned = skin != null && !skin.isEmpty();
        double sh = 0;
        if (SkyHelperPrices.ready()) {
            if (skinned) sh = SkyHelperPrices.pet(type, tier, level, frac, skin);
            if (sh <= 0) {
                sh = SkyHelperPrices.pet(type, tier, level, frac, null);
                if (sh > 0 && skinned) sh += value("PET_SKIN_" + skin);
            }
        }
        if (sh > 0) return sh + (heldItem == null || heldItem.isEmpty() ? 0 : value(heldItem));
        double binOnly = binPet(type, tier, exp, heldItem);
        return binOnly + (skinned && binOnly > 0 ? value("PET_SKIN_" + skin) : 0);
    }

    private static double binPet(String type, String tier, double exp, String heldItem) {
        double low = lowest.getOrDefault(petKey(type, tier, false), 0.0);
        double max = lowest.getOrDefault(petKey(type, tier, true), 0.0);
        double frac = Math.min(1, exp / Math.max(1, Pets.xpForMax(tier, type)));
        double v;
        if (low <= 0 && max <= 0) v = 0;
        else if (max <= 0) v = low;
        else if (low <= 0) v = max * frac;
        else v = low + (max - low) * frac;
        if (heldItem != null && !heldItem.isEmpty()) v += value(heldItem);
        return v;
    }

    // from skyblocker ItemPrice.refreshItemPrices: next tick fetches again
    public static void refresh() {
        fetched = 0;
    }

    // how many sold in the last hour we watched
    public static int salesLastHour(String key) {
        synchronized (LOCK) {
            var q = SALES.get(key);
            return q == null ? 0 : q.size();
        }
    }

    // how long weve been reading sales in ms, 0 before the first read
    public static long watchedFor() {
        return watchingSince == 0 ? 0 : System.currentTimeMillis() - watchingSince;
    }

    // listed more than we keep (5) = its on the ah a lot
    public static boolean manyListed(String key) {
        return moreThanKept.contains(key);
    }

    // tests: sales as if seen now, watching started agoMs before
    static void salesForTests(String key, int sales, long agoMs) {
        synchronized (LOCK) {
            SALES.remove(key);
            long now = System.currentTimeMillis();
            for (int i = 0; i < sales; i++) SALES.computeIfAbsent(key, k -> new java.util.ArrayDeque<>()).addLast(now);
            watchingSince = agoMs < 0 ? 0 : now - agoMs;
        }
    }

    // lbin for an item, or 0
    public static double lowestBin(String id) {
        return lowest.getOrDefault(id, 0.0);
    }

    public static boolean ready() {
        return !lowest.isEmpty();
    }

    // best known price: skyhelpers (what skycrypt uses), else bazaar sell, else lbin, else npc
    public static double value(String id) {
        double sh = SkyHelperPrices.get(id);
        if (sh > 0) return sh;
        double bz = Prices.sell(id);
        if (bz > 0) return bz;
        double bin = lowest.getOrDefault(id, 0.0);
        return bin > 0 ? bin : ItemRepo.npcPrice(id);
    }
}
