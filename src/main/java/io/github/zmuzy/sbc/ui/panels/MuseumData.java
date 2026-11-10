// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker, NotEnoughUpdates, SkyBlockAPI; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.ui.panels;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zmuzy.sbc.core.AuctionPrices;
import io.github.zmuzy.sbc.core.Config;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemInfo;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.Net;
import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.core.Profile;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.ToDoubleFunction;

// what the museum takes + what you gave. port of skyblocker skyblock/museum (MuseumItemCache, Donation,
// PriceData, ItemSorter, MuseumUtils)
public final class MuseumData {
    // one list entry: an item, or an armor set with its pieces
    public static final class Donation {
        public final String category, id;
        public final List<String> set;
        public final int xp;
        final List<String> downgrades = new ArrayList<>();
        // what ItemSorter.updateDonationData works out
        double lBin, craftCost, effective, ratio, discount;
        String discountId;
        int totalXp;
        final Map<String, double[]> pieces = new HashMap<>();
        List<Donation> countsFor = List.of();
        // name, lore, pieces lowercase for the search
        String search;

        Donation(String category, String id, List<String> set, int xp) {
            this.category = category;
            this.id = id;
            this.set = set;
            this.xp = xp;
        }

        public boolean isSet() {
            return !set.isEmpty();
        }

        public boolean craftable() {
            return craftCost > 0;
        }

        public boolean hasPrice() {
            return effective > 0;
        }

        public boolean hasDiscount() {
            return discount > 0;
        }

        public double effective() {
            return effective;
        }

        public int totalXp() {
            return totalXp;
        }

        public double ratio() {
            return ratio;
        }

        public double lBin() {
            return lBin;
        }

        public double craftCost() {
            return craftCost;
        }
    }

    public enum Sort {LOWEST_BIN, CRAFT_COST, COINS_PER_XP}

    // hypixel set ids whose name cant be guessed from the id (reverse of skyblockapi MuseumData)
    private static final Map<String, String> SET_NAMES = Map.ofEntries(
            Map.entry("MINER_OUTFIT", "Prospector's Outfit"), Map.entry("TANK_MINER", "Miner Armor"), Map.entry("SALMON_NEW", "Salmon Armor"),
            Map.entry("FLAME_BREAKER", "Flamebreaker Armor"), Map.entry("END", "Ender Armor"), Map.entry("SPEED_WITHER", "Maxor's Armor"),
            Map.entry("POWER_WITHER", "Necron's Armor"), Map.entry("WISE_WITHER", "Storm's Armor"), Map.entry("TANK_WITHER", "Goldor's Armor"),
            Map.entry("VANQUISHED", "Vanquisher Set"), Map.entry("PERFECT_TIER_12", "Perfect Armor - Tier XII"), Map.entry("PERFECT_TIER_13", "Perfect Armor - Tier XIII"));
    private static final List<String> ARMOR_WORDS = List.of("set", "suit", "armor", "outfit", "equipment", "'s special armor", "'s armor", "armor of", "tuxedo");
    private static final Set<String> EQUIPMENT_TYPES = Set.of("BELT", "GLOVES", "CLOAK", "GAUNTLET", "NECKLACE", "BRACELET", "HAT", "LOCKET", "VINE", "GRIPPERS");
    private static final long RESYNC_EVERY = 600_000L;
    private static final int MAX_DEPTH = 15;

    private static List<Donation> donations = List.of();
    private static final Map<String, Donation> byId = new HashMap<>();
    private static final Map<String, String> displayItem = new HashMap<>(), names = new HashMap<>(), mapped = new HashMap<>();
    // sets a piece is in + categories in the order they came
    private static final Map<String, String> setOfPiece = new HashMap<>();
    private static List<String> categories = List.of();
    private static int loadedFrom = -1;

    private MuseumData() {}

    // --- the list ---

    // builds the list from every items museum_data when its there and new
    public static void refreshCatalog() {
        Map<String, JsonObject> data = ItemRepo.museumData();
        if (data.size() == loadedFrom) return;
        load(data);
        loadedFrom = data.size();
    }

    // from skyblocker loadMuseumItems on hypixels museum_data
    public static void load(Map<String, JsonObject> items) {
        byId.clear();
        displayItem.clear();
        names.clear();
        mapped.clear();
        setOfPiece.clear();
        Map<String, List<String>> sets = new HashMap<>();
        Map<String, Integer> setXp = new HashMap<>();
        Map<String, String> setCategory = new HashMap<>();
        // parent -> items right below it
        Map<String, List<String>> below = new HashMap<>();
        Set<String> cats = new TreeSet<>();
        List<Donation> out = new ArrayList<>();
        for (Map.Entry<String, JsonObject> e : new java.util.TreeMap<>(items).entrySet()) {
            String id = e.getKey();
            JsonObject md = e.getValue();
            String category = category(md);
            if (md.has("mapped_item_ids") && md.get("mapped_item_ids").isJsonArray())
                for (JsonElement m : md.getAsJsonArray("mapped_item_ids")) mapped.put(m.getAsString(), id);
            if (md.has("parent") && md.get("parent").isJsonObject())
                for (Map.Entry<String, JsonElement> p : md.getAsJsonObject("parent").entrySet())
                    below.computeIfAbsent(p.getValue().getAsString(), k -> new ArrayList<>()).add(p.getKey());
            if (md.has("armor_set_donation_xp") && md.get("armor_set_donation_xp").isJsonObject() && !md.getAsJsonObject("armor_set_donation_xp").isEmpty()) {
                for (Map.Entry<String, JsonElement> s : md.getAsJsonObject("armor_set_donation_xp").entrySet()) {
                    sets.computeIfAbsent(s.getKey(), k -> new ArrayList<>()).add(id);
                    setXp.put(s.getKey(), s.getValue().getAsInt());
                    setCategory.put(s.getKey(), category);
                    setOfPiece.put(id, s.getKey());
                }
                continue;
            }
            cats.add(category);
            Donation d = new Donation(category, id, List.of(), md.has("donation_xp") ? md.get("donation_xp").getAsInt() : 0);
            out.add(d);
            byId.put(id, d);
        }
        for (Map.Entry<String, List<String>> s : sets.entrySet()) {
            String set = s.getKey();
            cats.add(setCategory.get(set));
            Donation d = new Donation(setCategory.get(set), set, List.copyOf(s.getValue()), setXp.getOrDefault(set, 0));
            out.add(d);
            byId.put(set, d);
            boolean equipment = true;
            for (String piece : s.getValue()) if (equipment) equipment = isEquipment(piece);
            names.put(set, SET_NAMES.getOrDefault(set, formatArmorName(set, equipment)));
            displayItem.put(set, displayPiece(s.getValue()));
        }
        for (Donation d : out) {
            // nearest first, ORDERED_UPGRADES backwards
            Set<String> seen = new LinkedHashSet<>();
            List<String> level = new ArrayList<>(below.getOrDefault(d.id, List.of()));
            while (!level.isEmpty()) {
                List<String> next = new ArrayList<>();
                for (String id : level) if (seen.add(id)) next.addAll(below.getOrDefault(id, List.of()));
                level = next;
            }
            d.downgrades.addAll(seen);
        }
        out.sort(Comparator.comparing(d -> d.id));
        donations = out;
        categories = new ArrayList<>(cats);
    }

    private static String category(JsonObject md) {
        String c = md.has("category") ? md.get("category").getAsString() : md.has("type") ? md.get("type").getAsString() : "OTHER";
        return c.toUpperCase(Locale.ROOT);
    }

    // chestplate shows a set best, like their armor_to_id mostly does
    private static String displayPiece(List<String> pieces) {
        for (String want : List.of("CHESTPLATE", "HELMET", "NECKLACE", "CLOAK")) for (String p : pieces) if (p.contains(want)) return p;
        return pieces.get(0);
    }

    public static boolean isEquipment(String id) {
        String upper = id.toUpperCase(Locale.ENGLISH);
        for (String t : EQUIPMENT_TYPES) if (upper.contains(t)) return true;
        return false;
    }

    // from skyblocker MuseumUtils.formatArmorName
    public static String formatArmorName(String id, boolean isEquipment) {
        String lowercase = id.toLowerCase(Locale.ENGLISH);
        StringBuilder out = new StringBuilder();
        for (String word : lowercase.replace("_", " ").split(" ")) {
            if (word.isEmpty()) continue;
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        if (isEquipment) out.append("Equipment");
        else if (!lowercase.matches(".*(armor|outfit|suit|tuxedo).*")) out.append("Armor");
        return out.toString().trim();
    }

    public static List<Donation> all() {
        return donations;
    }

    public static List<String> categories() {
        return categories;
    }

    public static Donation get(String id) {
        return byId.get(id);
    }

    public static String displayName(String id) {
        String set = names.get(id);
        return set != null ? set : GameState.strip(ItemRepo.name(id));
    }

    // item a list row shows
    public static String displayItem(String id) {
        return displayItem.getOrDefault(id, id);
    }

    public static String colored(String id) {
        return ItemRepo.coloredName(displayItem(id));
    }

    // --- what you gave ---

    private static Set<String> collected() {
        return PanelStore.data().museumCollected;
    }

    // what the museum holds as far as anything showed, for the from-game profile
    public static Set<String> donated() {
        return new HashSet<>(collected());
    }

    // from skyblocker getDonations: not in the museum yet + no piece of its set either, prices filled in
    public static List<Donation> undonated(ToDoubleFunction<String> lBin, ToDoubleFunction<String> craft) {
        Set<String> items = collected();
        List<Donation> out = new ArrayList<>();
        for (Donation d : donations) {
            if (items.contains(d.id)) continue;
            if (d.isSet()) {
                boolean some = false;
                for (String piece : d.set) if (items.contains(piece)) some = true;
                if (some) continue;
            }
            price(d, lBin, craft);
            out.add(d);
        }
        Set<String> open = new HashSet<>();
        for (Donation d : out) open.add(d.id);
        for (Donation d : out) {
            d.discount = 0;
            d.discountId = null;
            if (!d.craftable()) continue;
            for (String lower : d.downgrades) {
                if (open.contains(lower)) continue;
                d.discountId = lower;
                d.discount = setCraftCost(lower, craft);
                break;
            }
        }
        for (Donation d : out) {
            List<Donation> counts = new ArrayList<>();
            for (String lower : d.downgrades) if (open.contains(lower)) counts.add(byId.get(lower));
            counts.add(0, d);
            d.countsFor = counts;
        }
        return out;
    }

    // from skyblocker PriceData: a set costs its pieces together
    private static void price(Donation d, ToDoubleFunction<String> lBin, ToDoubleFunction<String> craft) {
        d.pieces.clear();
        if (d.isSet()) {
            double bin = 0, cost = 0;
            for (String piece : d.set) {
                double b = Math.max(0, lBin.applyAsDouble(piece)), c = Math.max(0, craft.applyAsDouble(piece));
                bin += b;
                cost += c;
                d.pieces.put(piece, new double[]{b, c, 0});
            }
            d.lBin = bin;
            d.craftCost = cost;
        } else {
            d.lBin = Math.max(0, lBin.applyAsDouble(d.id));
            d.craftCost = Math.max(0, craft.applyAsDouble(d.id));
        }
    }

    // from skyblocker MuseumUtils.getSetCraftCost
    private static double setCraftCost(String id, ToDoubleFunction<String> craft) {
        Donation d = byId.get(id);
        if (d == null) return 0;
        double cost = 0;
        for (String piece : d.set) cost += Math.max(0, craft.applyAsDouble(piece));
        return cost;
    }

    // from skyblocker updateDonationData: xp of everything it counts for + price under this sort
    private static void update(Donation d, boolean useCraftCost) {
        int total = 0;
        for (Donation c : d.countsFor) total += c.xp;
        double craft = d.craftable() ? d.craftCost : 0;
        if (d.discount > 0) craft -= d.discount;
        double effective = useCraftCost ? (craft > 0 ? (d.lBin == 0 ? craft : Math.min(craft, d.lBin)) : d.lBin) : (d.lBin > 0 ? d.lBin : craft);
        if (d.isSet()) for (double[] p : d.pieces.values()) p[2] = effective == craft ? p[1] : p[0];
        d.effective = effective;
        d.ratio = total > 0 && effective > 0 ? effective / total : 0;
        d.totalXp = total;
    }

    private static int byPrice(Donation a, Donation b) {
        if (a.effective <= 0 && b.effective <= 0) return Integer.compare(b.totalXp, a.totalXp);
        if (a.effective <= 0) return 1;
        if (b.effective <= 0) return -1;
        return Double.compare(a.effective, b.effective);
    }

    private static int byRatio(Donation a, Donation b) {
        if (a.ratio == 0 && b.ratio == 0) return Integer.compare(b.totalXp, a.totalXp);
        if (a.ratio == 0) return 1;
        if (b.ratio == 0) return -1;
        return Double.compare(a.ratio, b.ratio);
    }

    // ItemSorters 3 orders
    public static void sort(List<Donation> list, Sort mode) {
        for (Donation d : list) update(d, mode != Sort.LOWEST_BIN);
        list.sort(mode == Sort.COINS_PER_XP ? MuseumData::byRatio : MuseumData::byPrice);
    }

    // their updateSearchResults looks through name + lore of the item and its pieces
    static boolean matches(Donation d, String query) {
        if (query.isEmpty()) return true;
        if (d.search == null) {
            StringBuilder sb = new StringBuilder(displayName(d.id)).append(' ');
            if (!d.isSet()) sb.append(String.join(" ", ItemRepo.lore(d.id)));
            for (String piece : d.set) sb.append(' ').append(ItemRepo.name(piece)).append(' ').append(String.join(" ", ItemRepo.lore(piece)));
            d.search = GameState.strip(sb.toString()).toLowerCase(Locale.ENGLISH);
        }
        return d.search.contains(query.toLowerCase(Locale.ENGLISH));
    }

    // from skyblocker ItemUtils.getItemPrice: bazaar sell, else lbin
    static double lBinOf(String id) {
        Prices.Product p = Prices.get(id);
        return p != null ? p.sell() : AuctionPrices.lowestBin(id);
    }

    // from skyblocker CraftPriceTooltip.getItemCost: down the recipes, each part at the cheaper of market or its own craft
    static double craftOf(String id) {
        return craftOf(id, 0, crafts);
    }

    private static final Map<String, Double> crafts = new HashMap<>();

    // prices move so every new look redoes the crafts
    static void forgetCrafts() {
        crafts.clear();
    }

    private static double craftOf(String id, int depth, Map<String, Double> cache) {
        Double c = cache.get(id);
        if (c != null) return c;
        if (depth >= MAX_DEPTH) return -1;
        List<ItemInfo.Recipe> recipes = ItemInfo.recipes(id);
        if (recipes.isEmpty()) return -1;
        ItemInfo.Recipe r = recipes.get(0);
        cache.put(id, -1d);
        double total = r.coins();
        for (ItemInfo.Part part : r.parts()) {
            double market = market(part.id());
            double craft = craftOf(part.id(), depth + 1, cache);
            double each = craft > 0 ? (market > 0 ? Math.min(market, craft) : craft) : market;
            total += Math.max(0, each) * part.count();
        }
        double out = total / Math.max(1, r.resultCount());
        cache.put(id, out);
        return out;
    }

    private static double market(String id) {
        Prices.Product p = Prices.get(id);
        return p != null ? p.buy() : AuctionPrices.lowestBin(id);
    }

    // --- learning what you gave ---

    // from skyblocker updateData4ProfileMember: what an answers "items" means, keys are items or sets
    public static Set<String> fromApi(Collection<String> donatedSets) {
        Set<String> ids = new HashSet<>();
        for (String s : donatedSets) {
            // the answer can use an item's other id, the catalog knows it under one
            Donation d = byId.get(mapped.getOrDefault(s, s));
            if (d == null) continue;
            ids.addAll(d.downgrades);
            if (d.isSet()) {
                ids.addAll(d.set);
                for (String lower : d.downgrades) {
                    Donation low = byId.get(lower);
                    if (low != null) ids.addAll(low.set);
                }
            } else ids.add(d.id.replace("STARRED_", ""));
        }
        return ids;
    }

    // what the api profile already said, the cache keeps it
    static void fromProfile(Profile.Data d) {
        if (d == null || d.fromGame || d.museum.isEmpty() || donations.isEmpty()) return;
        Set<String> ids = fromApi(d.museum);
        PanelStore.Data data = PanelStore.data();
        if (data.museumCollected.addAll(ids) || !data.museumApi) {
            data.museumApi = true;
            PanelStore.save();
        }
    }

    public static boolean knowsFromApi() {
        return PanelStore.data().museumApi;
    }

    // from skyblocker handleClick: donation confirm, whats in the top slots is given now
    static void confirmed(java.util.function.IntFunction<ItemStack> slotAt) {
        Set<String> items = collected();
        boolean any = false;
        for (int i = 0; i < 17; i++) {
            ItemStack stack = slotAt.apply(i);
            if (stack.isEmpty()) continue;
            String id = Gain.sbId(stack);
            if (id.isEmpty()) continue;
            id = mapped.getOrDefault(id, id);
            String setId = setOfPiece.get(id);
            Donation donation = byId.get(setId != null ? setId : id);
            items.add(id);
            any = true;
            if (setId != null) items.add(setId);
            if (donation != null) {
                for (String lower : donation.downgrades) {
                    Donation low = byId.get(lower);
                    if (donation.isSet() && low != null) items.addAll(low.set);
                    items.add(lower);
                }
            }
        }
        if (any) {
            PanelStore.save();
            changed = true;
        }
    }

    // something new was learned: the panel works its list out again now instead of after its pause
    static boolean changed;

    private static String path(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    // from skyblockapi getArmorSetIdFromName: set id from the name a museum page shows. null = none
    static String setFromName(String name) {
        String lower = GameState.strip(name).trim().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : SET_NAMES.entrySet()) if (e.getValue().toLowerCase(Locale.ROOT).equals(lower)) return byId.containsKey(e.getKey()) ? e.getKey() : null;
        String best = lower;
        for (String word : ARMOR_WORDS) {
            String cut = lower.replace(word, "").trim();
            if (cut.length() < best.length()) best = cut;
        }
        for (String candidate : List.of(best, lower)) {
            String id = candidate.replace(' ', '_').toUpperCase(Locale.ROOT);
            Donation d = byId.get(id);
            if (d != null && d.isSet()) return id;
        }
        return null;
    }

    private static String idByName(String name) {
        String wanted = GameState.strip(name).trim();
        for (Donation d : donations) if (!d.isSet() && GameState.strip(ItemRepo.name(d.id)).equals(wanted)) return d.id;
        return null;
    }

    // a museum page like skyblockapi MuseumAPI.onInventoryOpen: gray dye = not donated, lime dye = donated +
    // taken out, anything else = the item. a set stands under its own name
    static void readPage(java.util.function.IntFunction<ItemStack> slotAt, int slots) {
        boolean grew = false;
        Set<String> items = collected();
        for (int i = 0; i < slots; i++) {
            ItemStack st = slotAt.apply(i);
            if (st.isEmpty() || path(st).endsWith("glass_pane") || path(st).equals("gray_dye")) continue;
            String name = st.getHoverName().getString();
            boolean setRow = false;
            for (String line : io.github.zmuzy.sbc.core.LegacyText.lore(st)) {
                String plain = GameState.strip(line);
                if (plain.equals("Right-click to view armor set!") || plain.equals("Click on an armor piece in your")) setRow = true;
            }
            String id = setRow || path(st).equals("lime_dye") ? null : Gain.sbId(st);
            if (setRow) id = setFromName(name);
            else if (path(st).equals("lime_dye")) {
                id = idByName(name);
                if (id == null) id = setFromName(name);
            }
            if (id == null || id.isEmpty()) continue;
            // same as a donation: its other id, and the lower tiers it stands for
            id = mapped.getOrDefault(id, id);
            Donation d = byId.get(id);
            String setId = setOfPiece.get(id);
            if (d == null && setId == null) continue;
            grew |= items.add(id);
            if (setId != null) grew |= items.add(setId);
            if (d != null && d.isSet()) grew |= items.addAll(d.set);
            Donation top = d != null ? d : byId.get(setId);
            if (top != null)
                for (String lower : top.downgrades) {
                    grew |= items.add(lower);
                    Donation low = byId.get(lower);
                    if (top.isSet() && low != null) grew |= items.addAll(low.set);
                }
        }
        if (grew) {
            PanelStore.save();
            changed = true;
        }
    }

    static void profileChanged() {
        requested = false;
    }

    private static boolean requested;

    // from skyblocker onProfileChange: a profile with no answer yet asks once when the server is there
    static void askOnce() {
        if (requested || donations.isEmpty()) return;
        requested = true;
        Profile.Data d = Profile.current;
        if (PanelStore.data().museumResync == 0 && d != null && !d.profileId.isEmpty() && io.github.zmuzy.sbc.core.ProfileServer.up()) resync(d, null);
    }

    // from skyblocker tryResync. say gets each thing to tell the player
    public static void tryResync(java.util.function.Consumer<String> say) {
        Profile.Data d = Profile.current;
        refreshCatalog();
        if (d == null || d.profileId.isEmpty()) {
            say.accept("§cFailed to resync your Museum donations: unknown profile. Rejoin SkyBlock and try again in a few minutes.");
            return;
        }
        if (donations.isEmpty()) {
            say.accept("§cFailed to resync your Museum donations: the item list has not loaded yet. Try again in a few minutes.");
            return;
        }
        if (!io.github.zmuzy.sbc.core.ProfileServer.up()) {
            say.accept("§7The Museum helper learns from the Museum's pages and from what you donate. Open the pages to read them again.");
            return;
        }
        if (PanelStore.data().museumResync + RESYNC_EVERY >= System.currentTimeMillis()) {
            say.accept("§cCannot resync your Museum donations yet: you can do this once every 10 minutes.");
            return;
        }
        say.accept("§7Attempting to resync your Museum donations...");
        resync(d, say);
    }

    private static void resync(Profile.Data d, java.util.function.Consumer<String> say) {
        PanelStore.data().museumResync = System.currentTimeMillis();
        Net.getJson(io.github.zmuzy.sbc.core.ProfileServer.museum(d.profileId), null).whenComplete((json, e) -> {
            String out;
            try {
                JsonObject members = e != null ? null : json.getAsJsonObject().getAsJsonObject("members");
                if (members == null) out = "§cFailed to resync your Museum donations: unknown error.";
                else if (!members.has(d.uuid)) out = "§cFailed to resync your Museum donations: the Museum API is not enabled. Enable it in your SkyBlock Settings and try again in a few minutes.";
                else {
                    Set<String> ids = fromApi(members.getAsJsonObject(d.uuid).getAsJsonObject("items").keySet());
                    PanelStore.Data data = PanelStore.data();
                    data.museumCollected = new HashSet<>(ids);
                    data.museumApi = true;
                    out = "§aSuccessfully resynced your Museum donations.";
                }
            } catch (Exception ex) {
                out = "§cFailed to resync your Museum donations: unknown error.";
            }
            PanelStore.save();
            if (say != null) say.accept(out);
        });
    }

    // for tests
    static void collect(Set<String> ids) {
        collected().clear();
        collected().addAll(ids);
    }
}
