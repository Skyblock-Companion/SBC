// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// every sb item like hypixel sends it (vanilla material, model, head texture, glint) from the repo cache on
// disk, plus npc sell price + display name from hypixels public items resource
public final class ItemRepo {
    private ItemRepo() {}

    public record Entry(String material, String model, String texture, boolean glint, String name) {}

    private static final Map<String, Entry> items = new HashMap<>();
    // pet type -> tier -> head texture (base64)
    private static final Map<String, Map<String, String>> petTextures = new HashMap<>();
    private static final Map<String, Double> npc = new ConcurrentHashMap<>();
    private static final Map<String, String> names = new ConcurrentHashMap<>();
    private static final Map<String, com.google.gson.JsonArray> upgradeCosts = new ConcurrentHashMap<>();
    private static final Map<String, com.google.gson.JsonArray> gemSlots = new ConcurrentHashMap<>();
    private static final Map<String, Integer> hotm = new ConcurrentHashMap<>();
    // hypixels dyed armor color "r,g,b" + what a rift item sells for in motes
    private static final Map<String, String> colors = new ConcurrentHashMap<>();
    private static final Map<String, Integer> motes = new ConcurrentHashMap<>();
    // museum category, hypixels name for it
    private static final Map<String, String> museumTypes = new ConcurrentHashMap<>();

    public static String color(String id) {
        return colors.get(id);
    }

    public static int motesPrice(String id) {
        return motes.getOrDefault(id, 0);
    }

    public static String museumType(String id) {
        return museumTypes.getOrDefault(id, "");
    }

    // every accessory hypixel lists: id -> display name, rarity
    private static final Map<String, String[]> accessories = new ConcurrentHashMap<>();

    // every pet + its rarities
    public static Map<String, java.util.Set<String>> petTiers() {
        Map<String, java.util.Set<String>> out = new java.util.TreeMap<>();
        for (Map.Entry<String, Map<String, String>> e : petTextures.entrySet()) out.put(e.getKey(), e.getValue().keySet());
        return out;
    }

    public static java.util.Set<String> petTypes() {
        return petTextures.keySet();
    }

    // museum items -> museum xp for donating one (0 if hypixel doesnt say)
    private static final Map<String, Integer> museum = new ConcurrentHashMap<>();

    public static boolean museumEligible(String id) {
        return museum.containsKey(id);
    }

    public static int museumXp(String id) {
        return museum.getOrDefault(id, 0);
    }

    // hypixels museum_data for every item that has it: type, category, donation_xp, armor_set_donation_xp,
    // parent, mapped_item_ids
    private static final Map<String, JsonObject> museumData = new ConcurrentHashMap<>();

    public static Map<String, JsonObject> museumData() {
        return museumData;
    }

    public static Map<String, String[]> accessories() {
        return accessories;
    }

    // hotm tier hypixel lists for an item, 0 = none
    public static int hotmRequirement(String id) {
        return hotm.getOrDefault(id, 0);
    }

    // per star: costs (ESSENCE with essence_type + amount, or ITEM with item_id + amount)
    public static com.google.gson.JsonArray upgradeCosts(String id) {
        return upgradeCosts.get(id);
    }

    // gemstone slots in order, each with its unlock costs
    public static com.google.gson.JsonArray gemSlots(String id) {
        return gemSlots.get(id);
    }

    public static void load() {
        try {
            String itemsJson = RepoData.read("items.min.json"), petsJson = RepoData.read("pets.min.json");
            if (itemsJson != null) {
                for (JsonElement e : JsonParser.parseString(itemsJson).getAsJsonArray()) add(e.getAsJsonObject());
            }
            if (petsJson != null) {
                JsonObject root = JsonParser.parseString(petsJson).getAsJsonObject();
                for (Map.Entry<String, JsonElement> p : root.entrySet()) {
                    JsonObject tiers = p.getValue().getAsJsonObject().getAsJsonObject("tiers");
                    if (tiers == null) continue;
                    Map<String, String> t = new HashMap<>();
                    for (Map.Entry<String, JsonElement> te : tiers.entrySet()) {
                        JsonObject to = te.getValue().getAsJsonObject();
                        if (to.has("texture")) t.put(te.getKey(), to.get("texture").getAsString());
                    }
                    petTextures.put(p.getKey(), t);
                }
            }
            SbcMod.LOG.info("Item repo: {} items, {} pets", items.size(), petTextures.size());
        } catch (Exception ex) {
            SbcMod.LOG.warn("Could not read the item repo", ex);
        }
        Net.getJson("https://api.hypixel.net/v2/resources/skyblock/items", null).whenComplete((json, err) -> {
            if (err != null) return;
            try {
                for (JsonElement e : json.getAsJsonObject().getAsJsonArray("items")) {
                    JsonObject o = e.getAsJsonObject();
                    String id = o.get("id").getAsString();
                    if (o.has("npc_sell_price")) npc.put(id, o.get("npc_sell_price").getAsDouble());
                    if (o.has("color")) colors.put(id, o.get("color").getAsString());
                    if (o.has("motes_sell_price")) motes.put(id, o.get("motes_sell_price").getAsInt());
                    if (o.has("museum_data") && o.get("museum_data").isJsonObject()) {
                        JsonObject md = o.getAsJsonObject("museum_data");
                        museum.put(id, md.has("donation_xp") ? md.get("donation_xp").getAsInt() : 0);
                        museumData.put(id, md);
                        if (md.has("type")) museumTypes.put(id, md.get("type").getAsString());
                    } else if (o.has("museum") && o.get("museum").getAsBoolean()) museum.put(id, 0);
                    if (o.has("name")) names.put(id, o.get("name").getAsString());
                    String cat = o.has("category") ? o.get("category").getAsString() : "";
                    if ((cat.equals("ACCESSORY") || cat.equals("HATCCESSORY")) && o.has("name"))
                        accessories.put(id, new String[]{o.get("name").getAsString(), o.has("tier") ? o.get("tier").getAsString() : "COMMON"});
                    if (o.has("upgrade_costs") && o.get("upgrade_costs").isJsonArray()) upgradeCosts.put(id, o.getAsJsonArray("upgrade_costs"));
                    if (o.has("requirements") && o.get("requirements").isJsonArray())
                        for (JsonElement q : o.getAsJsonArray("requirements")) {
                            JsonObject qo = q.getAsJsonObject();
                            if (qo.has("type") && "HEART_OF_THE_MOUNTAIN".equals(qo.get("type").getAsString()) && qo.has("tier"))
                                hotm.put(id, qo.get("tier").getAsInt());
                        }
                    if (o.has("gemstone_slots") && o.get("gemstone_slots").isJsonArray()) gemSlots.put(id, o.getAsJsonArray("gemstone_slots"));
                }
            } catch (Exception ignored) {
            }
        });
    }

    private static void add(JsonObject o) {
        JsonObject c = o.getAsJsonObject("components");
        if (c == null || !c.has("minecraft:custom_data")) return;
        JsonObject cd = c.getAsJsonObject("minecraft:custom_data");
        if (!cd.has("id")) return;
        String texture = null;
        if (c.has("minecraft:profile")) {
            JsonObject p = c.getAsJsonObject("minecraft:profile");
            if (p.has("properties")) for (JsonElement pe : p.getAsJsonArray("properties")) {
                JsonObject po = pe.getAsJsonObject();
                if (po.has("value")) texture = po.get("value").getAsString();
            }
        }
        String name = "", colored = "";
        if (c.has("minecraft:custom_name")) {
            JsonElement n = c.get("minecraft:custom_name");
            colored = n.isJsonObject() && n.getAsJsonObject().has("text") ? n.getAsJsonObject().get("text").getAsString() : n.getAsString();
            name = GameState.strip(colored);
        }
        String sid = cd.get("id").getAsString();
        if (c.has("minecraft:lore") && c.get("minecraft:lore").isJsonArray()) {
            List<String> lore = new java.util.ArrayList<>();
            for (JsonElement l : c.getAsJsonArray("minecraft:lore"))
                lore.add(l.isJsonObject() && l.getAsJsonObject().has("text") ? l.getAsJsonObject().get("text").getAsString() : l.isJsonPrimitive() ? l.getAsString() : "");
            lores.put(sid, lore);
        }
        if (!colored.isEmpty()) coloredNames.put(sid, colored);
        if (!name.isEmpty()) byName.putIfAbsent(name.toLowerCase(java.util.Locale.ROOT), sid);
        String material = o.has("id") ? o.get("id").getAsString() : "minecraft:paper";
        String model = c.has("minecraft:item_model") ? c.get("minecraft:item_model").getAsString() : null;
        boolean glint = c.has("minecraft:enchantment_glint_override") && c.get("minecraft:enchantment_glint_override").getAsBoolean();
        items.put(cd.get("id").getAsString(), new Entry(material, model, texture, glint, name));
    }

    // every sb id the repo knows
    public static java.util.Set<String> ids() {
        return items.keySet();
    }

    public static Entry get(String id) {
        return items.get(id);
    }

    private static final Map<String, List<String>> lores = new HashMap<>();
    private static final Map<String, String> coloredNames = new HashMap<>();
    private static final Map<String, String> byName = new HashMap<>();

    // tooltip lines like hypixel shows them (with § colors), empty = unknown
    public static List<String> lore(String id) {
        List<String> l = lores.get(id);
        if (l != null) return l;
        String[] pet = PetCard.parse(id);
        return pet != null ? PetCard.lore(pet) : List.of();
    }

    // name in its rarity color ("§6Hyperion")
    public static String coloredName(String id) {
        String c = coloredNames.get(id);
        if (c != null) return c;
        String[] pet = PetCard.parse(id);
        return pet != null ? PetCard.coloredName(pet) : name(id);
    }

    // sb id for a display name ("Hyperion" -> HYPERION), or null
    public static String idForName(String name) {
        if (name == null) return null;
        String id = byName.get(GameState.strip(name).trim().toLowerCase(java.util.Locale.ROOT));
        if (id != null) return id;
        for (Map.Entry<String, String> e : names.entrySet()) if (e.getValue().equalsIgnoreCase(name.trim())) return e.getKey();
        return null;
    }

    public static String petTexture(String type, String tier) {
        Map<String, String> t = petTextures.get(type);
        if (t == null || t.isEmpty()) return null;
        String s = t.get(tier);
        return s != null ? s : t.values().iterator().next();
    }

    // from skyhanni HypixelItemApi.getNpcPrice: npc price, or georges for a pet "TYPE;tier" id
    public static double npcPrice(String id) {
        Double price = npc.get(id);
        if (price != null) return price;
        return id != null && id.indexOf(';') > 0 ? Math.max(0, dev.zmuzy.sbc.core.data.GeorgePrices.price(id)) : 0;
    }

    // real display name for an sb id, or a cleaned up id
    public static String name(String id) {
        String n = names.get(id);
        if (n != null) return n;
        Entry e = items.get(id);
        if (e != null && !e.name().isEmpty()) return e.name();
        String[] pet = PetCard.parse(id);
        return pet != null ? PetCard.name(pet) : GameState.pretty(id);
    }
}
