// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.minion;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.properties.Property;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.core.game.GameProfile;
import dev.zmuzy.sbc.core.game.Seen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// every minion type + tier with its real speed + storage from the item repo on disk. each tier has its own
// head texture so minions on your island get recognised by their head
public final class MinionCatalog {
    public record Spec(String type, int tier, double delaySeconds, int storage) {}

    private static final dev.zmuzy.sbc.core.Patterns.P ID = dev.zmuzy.sbc.core.Patterns.p("MinionCatalog.ID");
    private static final dev.zmuzy.sbc.core.Patterns.P DELAY = dev.zmuzy.sbc.core.Patterns.p("MinionCatalog.DELAY");
    private static final dev.zmuzy.sbc.core.Patterns.P STORAGE = dev.zmuzy.sbc.core.Patterns.p("MinionCatalog.STORAGE");
    // "Tungsten Minion XI" or "Tungsten Minion 11", on a name stand, a menu title or a remembered spot
    private static final dev.zmuzy.sbc.core.Patterns.P NAME = dev.zmuzy.sbc.core.Patterns.p("MinionCatalog.NAME");
    // where the games minion name and its repo id differ
    private static final Map<String, String> ID_OF_NAME = Map.of("NETHER_WART", "NETHER_WARTS", "END_STONE", "ENDER_STONE",
            "CAVE_SPIDER", "CAVESPIDER");
    private static final Map<String, String> NAME_OF_ID = Map.of("NETHER_WARTS", "Nether Wart", "ENDER_STONE", "End Stone",
            "CAVESPIDER", "Cave Spider");
    private static final Map<String, Spec> byTexture = new HashMap<>();
    private static final Map<String, Spec> byKey = new HashMap<>();
    private static int scan;

    // what a minion type drops per harvest: item id + avg amount, main product first (from the wiki minion pages)
    private static final Map<String, List<Object[]>> DROPS = new HashMap<>();

    private static void drops(String type, Object... idAmount) {
        List<Object[]> l = new ArrayList<>();
        for (int i = 0; i < idAmount.length; i += 2) l.add(new Object[]{idAmount[i], ((Number) idAmount[i + 1]).doubleValue()});
        DROPS.put(type, l);
    }

    static {
        drops("COBBLESTONE", "COBBLESTONE", 1); drops("OBSIDIAN", "OBSIDIAN", 1); drops("GLOWSTONE", "GLOWSTONE_DUST", 3);
        drops("GRAVEL", "GRAVEL", 0.9, "FLINT", 0.1); drops("SAND", "SAND", 1); drops("RED_SAND", "SAND:1", 1); drops("ENDER_STONE", "ENDER_STONE", 1);
        drops("ICE", "ICE", 1); drops("SNOW", "SNOW_BALL", 4); drops("COAL", "COAL", 1); drops("IRON", "IRON_INGOT", 1); drops("GOLD", "GOLD_INGOT", 1);
        drops("DIAMOND", "DIAMOND", 1); drops("LAPIS", "INK_SACK:4", 6); drops("REDSTONE", "REDSTONE", 4.5); drops("EMERALD", "EMERALD", 1);
        drops("QUARTZ", "QUARTZ", 1); drops("MITHRIL", "MITHRIL_ORE", 1); drops("HARD_STONE", "HARD_STONE", 1); drops("CLAY", "CLAY_BALL", 4);
        drops("MYCELIUM", "MYCEL", 1); drops("TUNGSTEN", "TUNGSTEN", 1); drops("UMBER", "UMBER", 1); drops("GLACITE", "GLACITE", 1);
        drops("WHEAT", "WHEAT", 1, "SEEDS", 1.5); drops("CARROT", "CARROT_ITEM", 3); drops("POTATO", "POTATO_ITEM", 3, "POISONOUS_POTATO", 0.02);
        drops("PUMPKIN", "PUMPKIN", 1); drops("MELON", "MELON", 5); drops("MUSHROOM", "RED_MUSHROOM", 0.5, "BROWN_MUSHROOM", 0.5);
        drops("COCOA", "INK_SACK:3", 2.5); drops("CACTUS", "CACTUS", 1); drops("SUGAR_CANE", "SUGAR_CANE", 1); drops("NETHER_WARTS", "NETHER_STALK", 2.5);
        drops("CHICKEN", "RAW_CHICKEN", 1, "FEATHER", 1, "EGG", 0.5); drops("COW", "RAW_BEEF", 1, "LEATHER", 1); drops("PIG", "PORK", 1);
        drops("SHEEP", "MUTTON", 1, "WOOL", 1); drops("RABBIT", "RABBIT", 1, "RABBIT_FOOT", 0.5, "RABBIT_HIDE", 0.5);
        drops("ZOMBIE", "ROTTEN_FLESH", 1); drops("REVENANT", "ROTTEN_FLESH", 2, "DIAMOND", 0.2); drops("SKELETON", "BONE", 1);
        drops("CREEPER", "SULPHUR", 1); drops("SPIDER", "STRING", 1, "SPIDER_EYE", 0.5); drops("TARANTULA", "STRING", 2, "SPIDER_EYE", 1, "IRON_INGOT", 0.2);
        drops("CAVESPIDER", "SPIDER_EYE", 1, "STRING", 1); drops("BLAZE", "BLAZE_ROD", 1); drops("MAGMA_CUBE", "MAGMA_CREAM", 1.5);
        drops("ENDERMAN", "ENDER_PEARL", 1); drops("GHAST", "GHAST_TEAR", 1); drops("SLIME", "SLIME_BALL", 1.5);
        drops("FISHING", "RAW_FISH", 0.5, "RAW_FISH:1", 0.25, "PRISMARINE_SHARD", 0.1, "PRISMARINE_CRYSTALS", 0.1, "SPONGE", 0.03, "CLAY_BALL", 0.1);
        drops("OAK", "LOG", 1); drops("SPRUCE", "LOG:1", 1); drops("BIRCH", "LOG:2", 1); drops("JUNGLE", "LOG:3", 1); drops("ACACIA", "LOG_2", 1);
        drops("DARK_OAK", "LOG_2:1", 1); drops("FLOWER", "YELLOW_FLOWER", 0.5, "RED_ROSE", 0.5); drops("VOIDLING", "QUARTZ", 1);
        drops("LILY_PAD", "WATER_LILY", 1); drops("SUNFLOWER", "DOUBLE_PLANT", 1); drops("INFERNO", "CRUDE_GABAGOOL", 1);
        drops("VAMPIRE", "HEMOVIBE", 1);
    }

    public record Named(String type, int tier) {}

    // a minion name to its repo type + tier, null = not a minion name
    public static Named named(String name) {
        if (name == null) return null;
        Matcher m = NAME.matcher(GameState.strip(name).trim());
        if (!m.matches()) return null;
        String t = m.group(2);
        int tier = t.chars().allMatch(Character::isDigit) ? Integer.parseInt(t) : GameState.roman(t);
        if (tier < 1 || tier > 12) return null;
        String type = typeId(m.group(1));
        return type.isEmpty() ? null : new Named(type, tier);
    }

    // "Nether Wart" -> NETHER_WARTS, the id the repo + DROPS use
    public static String typeId(String name) {
        if (name == null) return "";
        String id = GameState.strip(name).trim().toUpperCase(java.util.Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        if (id.endsWith("_MINION")) id = id.substring(0, id.length() - "_MINION".length());
        return ID_OF_NAME.getOrDefault(id, id);
    }

    // NETHER_WARTS -> "Nether Wart", like the game names the minion
    public static String displayType(String id) {
        if (NAME_OF_ID.containsKey(id)) return NAME_OF_ID.get(id);
        StringBuilder b = new StringBuilder();
        for (String w : id.toLowerCase(java.util.Locale.ROOT).split("_")) {
            if (w.isEmpty()) continue;
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return b.toString();
    }

    private MinionCatalog() {}

    public static void load() {
        String itemsJson = dev.zmuzy.sbc.core.RepoData.read("items.min.json");
        if (itemsJson == null) {
            SbcMod.LOG.info("SkyBlock item repo not found - minions are only measured when opened");
            return;
        }
        try {
            JsonElement root = JsonParser.parseString(itemsJson);
            Iterable<JsonElement> items = root.isJsonArray() ? root.getAsJsonArray()
                    : root.getAsJsonObject().has("items") ? root.getAsJsonObject().getAsJsonArray("items") : root.getAsJsonObject().asMap().values();
            for (JsonElement e : items) {
                if (!e.isJsonObject()) continue;
                JsonObject comp = e.getAsJsonObject().getAsJsonObject("components");
                if (comp == null || !comp.has("minecraft:custom_data")) continue;
                JsonObject cd = comp.getAsJsonObject("minecraft:custom_data");
                if (!cd.has("id")) continue;
                Matcher m = ID.matcher(cd.get("id").getAsString());
                if (!m.matches()) continue;
                double delay = 0;
                int storage = 0;
                if (comp.has("minecraft:lore")) for (JsonElement l : comp.getAsJsonArray("minecraft:lore")) {
                    String t = GameState.strip(l.isJsonObject() && l.getAsJsonObject().has("text") ? l.getAsJsonObject().get("text").getAsString() : l.toString());
                    Matcher dm = DELAY.matcher(t);
                    if (dm.find()) delay = Double.parseDouble(dm.group(1));
                    Matcher sm = STORAGE.matcher(t);
                    if (sm.find()) storage = (int) GameState.num(sm.group(1));
                }
                if (delay <= 0) continue;
                Spec s = new Spec(m.group(1), Integer.parseInt(m.group(2)), delay, storage);
                byKey.put(s.type() + "_" + s.tier(), s);
                String tex = textureFromJson(comp);
                if (tex != null) byTexture.put(tex, s);
            }
            SbcMod.LOG.info("Minion catalog: {} minion tiers, {} recognisable by head", byKey.size(), byTexture.size());
        } catch (Exception ex) {
            SbcMod.LOG.warn("Could not read minion data from the item repo", ex);
        }
    }

    private static String textureFromJson(JsonObject comp) {
        if (!comp.has("minecraft:profile")) return null;
        JsonObject p = comp.getAsJsonObject("minecraft:profile");
        if (!p.has("properties")) return null;
        for (JsonElement pe : p.getAsJsonArray("properties")) {
            JsonObject po = pe.getAsJsonObject();
            if (po.has("name") && "textures".equals(po.get("name").getAsString())) return skinHash(po.get("value").getAsString());
        }
        return null;
    }

    // texture hash at the end of the skin url, stable however the base64 wrapper was written
    static String skinHash(String base64) {
        try {
            String json = new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8);
            int i = json.indexOf("/texture/");
            if (i < 0) return null;
            int end = json.indexOf('"', i);
            return json.substring(i + 9, end < 0 ? json.length() : end);
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean ready() {
        return !byTexture.isEmpty();
    }

    // tests: a minion tier as if read from the repo
    static void specForTests(Spec s) {
        byKey.put(s.type() + "_" + s.tier(), s);
    }

    public static Spec spec(String type, int tier) {
        return byKey.get(type + "_" + tier);
    }

    // rough coins/day of one minion with no fuel or upgrades
    public static double coinsPerDay(String type, int tier) {
        return MinionMath.estimate(type, tier, null, List.of(), false).perDay();
    }

    // rough coins/day with your fuel + upgrades like the minion menu names them, through MinionMath: the games
    // own numbers per fuel/upgrade + the central prices for what it makes
    public static double coinsPerDay(String type, int tier, String fuel, List<String> upgrades) {
        List<String> ids = new ArrayList<>();
        for (String u : upgrades) ids.add(MinionMath.idOf(u));
        return MinionMath.estimate(type, tier, MinionMath.idOf(fuel), ids, false).perDay();
    }

    // what one item sells for: central price (bazaar sell offer minus tax, or npc)
    public static double unitPrice(String id) {
        return dev.zmuzy.sbc.core.ItemPrice.sell(id).coins();
    }

    // drops per harvest: id + avg amount, main first. null = unknown type
    public static List<Object[]> drops(String type) {
        return DROPS.get(type);
    }

    public static String dropName(String type) {
        List<Object[]> drop = DROPS.get(type);
        return drop == null ? "?" : GameState.pretty(((String) drop.get(0)[0]).replace(":", "_"));
    }

    // best minion types to place rn at a tier (max tier when a type stops earlier)
    public static List<Object[]> best(int tier, int limit) {
        Map<String, Integer> maxTier = new HashMap<>();
        for (Spec s : byKey.values()) maxTier.merge(s.type(), s.tier(), Math::max);
        List<Object[]> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : maxTier.entrySet()) {
            int t = Math.min(tier, e.getValue());
            MinionMath.Estimate est = MinionMath.estimate(e.getKey(), t, null, List.of(), false);
            // only stuff we can rank on, a drop that barely sells doesnt put a minion on top
            if (est.perDay() > 0 && est.certain()) out.add(new Object[]{e.getKey(), t, est.perDay()});
        }
        out.sort(Comparator.comparingDouble((Object[] o) -> (double) o[2]).reversed());
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    private static Object level;
    private static long levelAt;

    // spots minions near you on your island by their heads and records them (every 2 sec). a head the repo
    // doesnt know (newer types, skins) still counts by its name stand or the name MinionFeatures remembers
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || ++scan % 40 != 0) return;
        long now = System.currentTimeMillis();
        if (mc.level != level) {
            level = mc.level;
            levelAt = now;
        }
        Seen seen = GameProfile.current();
        Map<String, Store.MinionRecord> all = Store.minions();
        if (seen != null && Minions.fillFromSpots(seen.minionSpots, all)) Store.markDirty();
        if (!"PRIVATE_ISLAND".equals(GameState.island) && !"GARDEN".equals(GameState.island) || GameState.guest) return;
        java.util.Set<String> occupied = new java.util.HashSet<>();
        for (Entity en : mc.level.entitiesForRendering()) {
            if (!(en instanceof ArmorStand a) || a.distanceToSqr(mc.player) > 96 * 96) continue;
            ItemStack head = a.getItemBySlot(EquipmentSlot.HEAD);
            if (head.isEmpty()) continue;
            String key = GameState.island + "@" + a.blockPosition().toShortString();
            occupied.add(key);
            Spec s = byHead(head);
            Named n = s != null ? new Named(s.type(), s.tier()) : nameNear(mc, a, seen);
            if (n == null) continue;
            Store.MinionRecord r = all.get(key);
            boolean made = r == null;
            if (made) {
                r = new Store.MinionRecord();
                r.fromScan = true;
                all.put(key, r);
            }
            boolean changed = made || r.tier != n.tier() || !n.type().equals(r.catalogType);
            r.key = key;
            if (changed || r.type == null) r.type = displayType(n.type());
            r.catalogType = n.type();
            r.tier = n.tier();
            r.island = GameState.island;
            if (r.storageSlots <= 0) r.storageSlots = s != null ? Math.max(1, s.storage() / 64) : Minions.slotsForTier(n.tier());
            if (changed || now - r.lastDetected > 60_000) {
                r.lastDetected = now;
                Store.markDirty();
            }
        }
        // entities come in a moment after the world, till then every spot looks empty
        if (now - levelAt < 10_000) return;
        var p = mc.player.position();
        if (forgetGone(all, GameState.island, occupied, k -> {
            double[] c = Minions.coords(k);
            return c != null && p.distanceTo(new net.minecraft.world.phys.Vec3(c[0] + 0.5, c[1], c[2] + 0.5)) <= 30
                    && mc.level.hasChunkAt(net.minecraft.core.BlockPos.containing(c[0], c[1], c[2]));
        })) Store.markDirty();
    }

    private static Spec byHead(ItemStack head) {
        ResolvableProfile prof = head.get(DataComponents.PROFILE);
        if (prof == null) return null;
        String hash = null;
        for (Property p : prof.partialProfile().properties().get("textures")) hash = skinHash(p.value());
        return hash == null ? null : byTexture.get(hash);
    }

    // a minion whose head isnt in the repo: its own name, a name stand right above it, or the spot MinionFeatures remembers
    private static Named nameNear(Minecraft mc, ArmorStand a, Seen seen) {
        if (a.hasCustomName()) {
            Named n = named(a.getCustomName().getString());
            if (n != null) return n;
        }
        var box = new net.minecraft.world.phys.AABB(a.position(), a.position()).inflate(0.4, 0, 0.4).expandTowards(0, 2.5, 0);
        for (ArmorStand above : mc.level.getEntitiesOfClass(ArmorStand.class, box, o -> o != a && o.hasCustomName())) {
            Named n = named(above.getCustomName().getString());
            if (n != null) return n;
        }
        if (seen == null) return null;
        Seen.MinionSpot spot = seen.minionSpots.get(MinionFeatures.key(a.position()));
        return spot == null ? null : named(spot.displayName);
    }

    // from skyhanni removeBuggedMinions. a spot the head scan recorded, now loaded (in reach) and with nothing
    // standing on it, got picked up or moved. minions added by opening their menu or from a remembered spot
    // stay: their head may just be one the repo doesnt know
    static boolean forgetGone(Map<String, Store.MinionRecord> all, String island, java.util.Set<String> occupied,
                              java.util.function.Predicate<String> loaded) {
        return all.entrySet().removeIf(e -> {
            Store.MinionRecord r = e.getValue();
            return r.fromScan && island.equals(r.island) && Minions.hasCoords(e.getKey()) && !occupied.contains(e.getKey())
                    && loaded.test(e.getKey());
        });
    }
}
