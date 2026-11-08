// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// everything the item cards show: real tooltip (name + lore like hypixel sends it), recipes (crafting grid or
// forge) from the item repo, prices (bazaar, lbin, value)
public final class ItemInfo {
    // one ingredient. slot = spot in the 3x3 grid (-1 for forge inputs)
    public record Part(String id, int count, int slot) {}

    public record Recipe(String type, List<Part> parts, int resultCount, long seconds, double coins) {}

    private static final Map<String, List<Recipe>> recipes = new HashMap<>();
    private static final Map<String, ItemStack> tipStacks = new HashMap<>();

    // what an npc shop wants: coins or another currency by name, or an item
    public record Cost(String currency, String itemId, int count) {}

    public record Shop(String npc, int resultCount, List<Cost> costs) {}

    // one kat step: pet tier from -> to for coins, items, time
    public record KatStep(String pet, String from, String to, long seconds, double coins, List<Part> items) {}

    // something that drops an item: mob, chance, how many
    public record Drop(String mob, double chance, int min, int max) {}

    private static final Map<String, List<Shop>> shops = new HashMap<>();
    private static final Map<String, List<KatStep>> kat = new HashMap<>();
    private static final Map<String, List<Drop>> drops = new HashMap<>();

    private ItemInfo() {}

    public static void load() {
        String recipesJson = RepoData.read("recipes.min.json");
        if (recipesJson == null) return;
        try {
            for (JsonElement e : JsonParser.parseString(recipesJson).getAsJsonArray()) {
                JsonObject r = e.getAsJsonObject();
                String type = r.has("type") ? r.get("type").getAsString() : "";
                if (type.equals("shop") && r.has("result") && r.getAsJsonObject("result").has("id") && r.has("npc")) {
                    List<Cost> costs = new ArrayList<>();
                    for (JsonElement ie : r.getAsJsonArray("inputs")) {
                        JsonObject in = ie.getAsJsonObject();
                        int n = in.has("count") ? in.get("count").getAsInt() : 1;
                        if (in.has("currency")) costs.add(new Cost(in.get("currency").getAsString(), null, n));
                        else if (in.has("id")) costs.add(new Cost(null, in.get("id").getAsString(), n));
                    }
                    JsonObject rs = r.getAsJsonObject("result");
                    shops.computeIfAbsent(rs.get("id").getAsString(), k -> new ArrayList<>())
                            .add(new Shop(r.get("npc").getAsString(), rs.has("count") ? rs.get("count").getAsInt() : 1, costs));
                    continue;
                }
                if (type.equals("kat") && r.has("input") && r.has("output")) {
                    JsonObject in = r.getAsJsonObject("input"), out = r.getAsJsonObject("output");
                    if (!in.has("pet")) continue;
                    List<Part> items = new ArrayList<>();
                    if (r.has("items"))
                        for (JsonElement ie : r.getAsJsonArray("items")) {
                            JsonObject io = ie.getAsJsonObject();
                            if (io.has("id")) items.add(new Part(io.get("id").getAsString(), io.has("count") ? io.get("count").getAsInt() : 1, -1));
                        }
                    String pet = in.get("pet").getAsString();
                    kat.computeIfAbsent(pet, k -> new ArrayList<>()).add(new KatStep(pet, in.get("tier").getAsString(), out.get("tier").getAsString(),
                            r.has("time") ? r.get("time").getAsLong() : 0, r.has("coins") ? r.get("coins").getAsDouble() : 0, items));
                    continue;
                }
                JsonObject res = r.has("result") ? r.getAsJsonObject("result") : null;
                if (res == null || !res.has("id")) continue;
                List<Part> parts = new ArrayList<>();
                if (type.equals("crafting") && r.has("keys") && r.has("pattern")) {
                    var keys = r.getAsJsonArray("keys");
                    var pattern = r.getAsJsonArray("pattern");
                    for (int i = 0; i < pattern.size() && i < 9; i++) {
                        int k = pattern.get(i).getAsInt();
                        if (k < 0 || k >= keys.size()) continue;
                        JsonObject key = keys.get(k).getAsJsonObject();
                        parts.add(new Part(key.get("id").getAsString(), key.has("count") ? key.get("count").getAsInt() : 1, i));
                    }
                } else if (type.equals("forge") && r.has("inputs")) {
                    for (JsonElement ie : r.getAsJsonArray("inputs")) {
                        JsonObject in = ie.getAsJsonObject();
                        if (in.has("id")) parts.add(new Part(in.get("id").getAsString(), in.has("count") ? in.get("count").getAsInt() : 1, -1));
                    }
                } else continue;
                recipes.computeIfAbsent(res.get("id").getAsString(), k -> new ArrayList<>())
                        .add(new Recipe(type, parts, res.has("count") ? res.get("count").getAsInt() : 1,
                                r.has("time") ? r.get("time").getAsLong() : 0, r.has("coins") ? r.get("coins").getAsDouble() : 0));
            }
        } catch (Exception ex) {
            SbcMod.LOG.warn("Could not read recipes", ex);
        }
    }

    // mob drop tables, read on first use
    private static void loadDrops() {
        if (!drops.isEmpty()) return;
        String mobsJson = RepoData.read("mobs.min.json");
        if (mobsJson == null) return;
        try {
            for (var me : JsonParser.parseString(mobsJson).getAsJsonObject().entrySet()) {
                JsonObject mob = me.getValue().getAsJsonObject();
                if (!mob.has("lootTables")) continue;
                for (JsonElement te : mob.getAsJsonArray("lootTables")) {
                    JsonObject t = te.getAsJsonObject();
                    String name = t.has("name") ? t.get("name").getAsString() : mob.has("name") ? mob.get("name").getAsString() : me.getKey();
                    if (!t.has("drops")) continue;
                    for (JsonElement de : t.getAsJsonArray("drops")) {
                        JsonObject d = de.getAsJsonObject();
                        if (!d.has("id") || d.has("currency")) continue;
                        drops.computeIfAbsent(d.get("id").getAsString(), k -> new ArrayList<>()).add(new Drop(name.replaceAll("\u00a7.", ""),
                                d.has("chance") ? d.get("chance").getAsDouble() : 1, d.has("minAmount") ? d.get("minAmount").getAsInt() : 1,
                                d.has("maxAmount") ? d.get("maxAmount").getAsInt() : 1));
                    }
                }
            }
            for (List<Drop> l : drops.values()) l.sort((a, b) -> Double.compare(b.chance(), a.chance()));
        } catch (Exception ex) {
            SbcMod.LOG.warn("Could not read mob drops", ex);
        }
    }

    public static List<Shop> shops(String id) {
        return shops.getOrDefault(id, List.of());
    }

    // kat steps for a pet card. id can be "ARMADILLO", "PET_ARMADILLO" or "ARMADILLO;3"
    public static List<KatStep> kat(String id) {
        String k = id.contains(";") ? id.substring(0, id.indexOf(';')) : id;
        if (k.startsWith("PET_")) k = k.substring(4);
        return kat.getOrDefault(k, List.of());
    }

    public static List<Drop> drops(String id) {
        loadDrops();
        return drops.getOrDefault(id, List.of());
    }

    public static List<Recipe> recipes(String id) {
        return recipes.getOrDefault(id, List.of());
    }

    // an item this goes into + the recipe
    public record Usage(String result, Recipe recipe) {}

    private static Map<String, List<Usage>> usageIndex;

    // everything an item is an ingredient of (built once on first use)
    public static List<Usage> usages(String id) {
        if (usageIndex == null) {
            Map<String, List<Usage>> idx = new HashMap<>();
            for (var e : recipes.entrySet())
                for (Recipe r : e.getValue())
                    for (Part p : r.parts()) {
                        List<Usage> l = idx.computeIfAbsent(p.id(), k -> new ArrayList<>());
                        if (l.stream().noneMatch(u -> u.result().equals(e.getKey()))) l.add(new Usage(e.getKey(), r));
                    }
            for (List<Usage> l : idx.values()) l.sort((a, b) -> ItemRepo.name(a.result()).compareToIgnoreCase(ItemRepo.name(b.result())));
            usageIndex = idx;
        }
        return usageIndex.getOrDefault(id, List.of());
    }

    public static boolean craftable(String id) {
        return recipes.containsKey(id);
    }

    // tooltip lines: colored name then lore
    public static List<String> tooltip(String id) {
        List<String> out = new ArrayList<>();
        out.add(ItemRepo.coloredName(id));
        out.addAll(ItemRepo.lore(id));
        return out;
    }

    // icon stack with the real name + lore so vanilla tooltips show the sb stats
    public static ItemStack stack(String id) {
        ItemStack c = tipStacks.get(id);
        if (c != null) return c;
        ItemStack st = ItemIcons.of(id).copy();
        try {
            st.set(DataComponents.CUSTOM_NAME, Component.literal(ItemRepo.coloredName(id)));
            List<Component> lore = new ArrayList<>();
            for (String l : ItemRepo.lore(id)) lore.add(Component.literal(l));
            st.set(DataComponents.LORE, new ItemLore(lore));
        } catch (Exception ignored) {
        }
        tipStacks.put(id, st);
        if (tipStacks.size() > 2000) tipStacks.clear();
        return st;
    }

    // price lines for the card
    public static List<String> priceLines(String id) {
        List<String> out = new ArrayList<>();
        Prices.Product p = Prices.get(id);
        if (p != null) {
            out.add("§7Bazaar buy: §6" + Prices.coins(p.buy()) + "  §7sell: §6" + Prices.coins(p.sell()));
            out.add("§7Sold per week: §f" + Prices.coins(p.sellWeek()));
        }
        double value = AuctionPrices.value(id);
        if (p == null && value > 0) out.add("§7Value (lowest BIN): §6" + Prices.coins(value));
        double npc = ItemRepo.npcPrice(id);
        if (npc > 0) out.add("§7NPC sell: §6" + Prices.coins(npc));
        String[] pet = PetCard.parse(id);
        if (pet != null) out.addAll(PetCard.prices(pet));
        return out;
    }

    public static boolean onBazaar(String id) {
        return Prices.get(id) != null;
    }

    public static void clearCache() {
        tipStacks.clear();
    }
}
