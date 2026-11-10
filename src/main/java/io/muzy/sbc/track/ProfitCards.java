// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.track;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.core.SbTime;
import io.muzy.sbc.core.TabWidgets;
import io.muzy.sbc.events.hoppity.ChocolateFactory;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.item.menus.AttributeShards;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.mc.Interactions;
import io.muzy.sbc.mining.Mineshaft;
import io.muzy.sbc.rift.RiftApi;
import io.muzy.sbc.track.Profit.Card;
import io.muzy.sbc.track.Profit.Session;
import io.muzy.sbc.ui.HudLayout;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// one tracker per activity on Profits sessions + card, so they all count session, day, week and forever. from
// skyhanni DragonProfitTracker and friends
public final class ProfitCards {
    private ProfitCards() {}

    private static final String ESSENCE_DRAGON = "ESSENCE_DRAGON";

    static final Session LOOT = new Session("items_gained", Trackers.get("loot", "Items gained")),
            CHESTS = new Session("dungeon", "Dungeon chests"), CORPSES = new Session("corpse", "Corpse profit"),
            DRAGON = new Session("dragon", "Dragon profit"), GHOST = new Session("ghost", "Ghost profit"),
            NODES = new Session("ender_node", "Ender Node profit"), SACRIFICE = new Session("sacrifice", "Sacrifice profit"),
            NUCLEUS = new Session("nucleus", "Crystal Nucleus profit"), HUNTING = new Session("hunting", "Hunting profit"),
            SHARDS = new Session("shards", "Shard tracker"), INFERNO = new Session("inferno", "Inferno Minion profit"),
            TIMITE = new Session("timite", "Timite tracker"), STRAY = new Session("stray", "Stray rabbits"),
            ORB = new Session("shiny_orb", "Shiny Orb profit"), GIFT = new Session("gift", "Gift profit"),
            TREVOR = new Session("trevor", "Trevor tracker"), SEA = new Session("sea_creatures", "Sea creatures"),
            DIANA = new Session("diana", "Diana profit"), MYTH = new Session("mythological", "Mythological mobs"),
            BEACH = new Session("beach_ball", "Beach Ball tracker"), MONOLITH = new Session("dark_monolith", "Dark Monolith");

    private static boolean island(String id) {
        return id.equals(GameState.island) || SbcMod.DEV;
    }

    private static boolean on(String feature, String key, boolean def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    private static String choice(String feature, String key, String def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.choice();
    }

    private static int integer(String feature, String key, int def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.integer();
    }

    private static double price(String id) {
        return Math.max(0, Gain.ofId(id));
    }

    private static void chat(Component line) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(line);
    }

    // skyhannis "Profit for ...": one line, what made it up in its hover
    private static void profitLine(String what, Map<String, Long> loot, double cost, String costName, double minimum) {
        double total = -cost;
        List<String> hover = new ArrayList<>();
        for (var e : loot.entrySet()) {
            double value = price(e.getKey()) * e.getValue();
            total += value;
            if (value < minimum) continue;
            String name = ItemRepo.coloredName(e.getKey());
            hover.add("§eFound " + (e.getValue() == 1 ? "" : "§8" + e.getValue() + "x ") + (name == null || name.isEmpty() ? Profit.pretty(e.getKey()) : name) + " §7(§6" + Prices.coins(value) + "§7)");
        }
        if (cost > 0) hover.add("§c" + costName + "§7: §c-" + Prices.coins(cost));
        String text = "§eProfit for " + what + "§e: " + (total < 0 ? "§c" : "§6") + Prices.coins(total);
        hover.add(text);
        chat(Component.literal(text).setStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(Component.literal(String.join("\n", hover))))));
    }

    // --- items gained ---

    private static final Card LOOT_CARD = new Card("loot", "Items gained", "Trackers", 35, LOOT) {
        @Override
        boolean relevant() {
            return on("loot", "loot.card", true);
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Profit.samples("ENCHANTED_DIAMOND", 42, 420_000, "RECOMBOBULATOR_3000", 1, 6_100_000, "ENCHANTED_COAL", 640, 96_000);
        }
    };

    // --- dungeon chests: skyhanni InstanceChestProfit ---

    // skyblocker ChestValue titles, hypixel writes them with and without "Chest"
    private static final Set<String> CHEST_TITLES = Set.of("Wood Chest", "Wood", "Gold Chest", "Gold", "Diamond Chest", "Diamond", "Emerald Chest", "Emerald", "Obsidian Chest", "Obsidian", "Bedrock Chest", "Bedrock", "Free Chest", "Free Chest Chest", "Paid Chest", "Paid Chest Chest");
    private static long chestAt;

    private static final Card CHESTS_CARD = new Card("dungeon_profit", "Dungeon chests", "Dungeons", 42, CHESTS) {
        @Override
        boolean relevant() {
            return "CATACOMBS".equals(GameState.island) || "KUUDRA".equals(GameState.island) || System.currentTimeMillis() - chestAt < 120_000 || SbcMod.DEV;
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            double chests = c.getOrDefault("Chests", 0d);
            if (chests > 0) {
                out.add(Profit.count("Chests opened", "§f" + Profit.num(chests)).brief("Chests"));
                double total = demo ? 8_400_000 : CHESTS.total(scope());
                out.add(Profit.count("Per chest", "§6" + Prices.coins(total / chests)).brief("/chest"));
            }
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Profit.samples(Profit.COST, 1, -1_200_000, "ESSENCE_WITHER", 40, 1_600_000, "RECOMBOBULATOR_3000", 1, 6_100_000, "FIFTH_MASTER_STAR", 1, 1_900_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Chests", 3d);
        }
    };

    // what the open reward chest button of an instance chest says: slot 31 = cost, the rest = what you get
    static void chestOpened(MenuView v) {
        MenuView.Item button = v.item(31);
        if (button.loreHas("Already opened!")) return;
        double cost = 0;
        for (String line : button.lore) {
            Matcher coins = Patterns.get("SH.combat.instance-chest-profit.coins").matcher(line);
            if (coins.matches()) {
                try {
                    cost += Profit.amount(coins.group("amount"));
                } catch (NumberFormatException ignored) {
                }
            } else if (Patterns.get("SH.combat.instance-chest-profit.dungeonchestkey").matcher(line).matches()) {
                cost += price("DUNGEON_CHEST_KEY");
            } else if (Patterns.get("SH.combat.instance-chest-profit.kuudrachestkey").matcher(line).matches()) {
                String key = Profit.idFor(line);
                if (key != null) cost += price(key);
            }
        }
        if (GameState.purse >= 0 && cost > GameState.purse) return; // hypixel will refuse it and nothing is gained
        for (int slot = 0; slot < v.top; slot++) {
            if (slot == 31) continue;
            MenuView.Item it = v.item(slot);
            if (it.empty() || Patterns.get("SH.combat.instance-chest-profit.fakeitemname").matcher(it.name).matches()) continue;
            Matcher essence = Patterns.get("SH.combat.instance-chest-profit.essence").matcher(it.name);
            if (essence.matches()) {
                String id = Profit.idFor(essence.group("name"));
                if (id != null) CHESTS.item(id, Long.parseLong(essence.group("count")));
                continue;
            }
            Matcher shard = Patterns.get("SH.combat.instance-chest-profit.attributeshard").matcher(it.name);
            if (shard.matches()) {
                String id = Profit.idFor(shard.group("name"));
                if (id != null) CHESTS.item(id, Long.parseLong(shard.group("count")));
                continue;
            }
            if (it.id.isEmpty()) continue;
            CHESTS.item(it.id, Math.max(1, it.count));
        }
        CHESTS.cost(cost);
        CHESTS.count("Chests", 1);
        chestAt = System.currentTimeMillis();
    }

    private static boolean chestClick(AbstractContainerScreen<?> screen, net.minecraft.world.inventory.Slot slot, int slotId, int button) {
        if (slotId != 31 || !Features.on("dungeon_profit")) return false;
        String title = GameState.strip(screen.getTitle().getString()).trim();
        if (!CHEST_TITLES.contains(title)) return false;
        MenuView v = MenuMarks.current();
        if (v != null && v.item(31).clean.startsWith("Open Reward Chest")) chestOpened(v);
        return false;
    }

    // --- corpses: skyhanni CorpseTracker ---

    private static final Card CORPSE_CARD = new Card("corpse_profit", "Corpse profit", "Mining", 37, CORPSES) {
        @Override
        boolean relevant() {
            return "MINESHAFT".equals(GameState.island) || (!on("corpse_profit", "corpse.only_mineshaft", false) && "DWARVEN_MINES".equals(GameState.island) && glacialTunnels()) || SbcMod.DEV;
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            double all = 0;
            for (var e : c.entrySet()) if (e.getKey().startsWith("Corpse: ")) all += e.getValue();
            if (all <= 0) return out;
            out.add(Profit.count("Corpses looted", "§f" + Profit.num(all)).brief("Corpses"));
            double total = demo ? 2_400_000 : CORPSES.total(scope());
            out.add(Profit.count("Per corpse", "§6" + Prices.coins(total / all)).brief("/corpse"));
            double saved = c.getOrDefault("Keys saved", 0d);
            if (saved > 0) out.add(Profit.count("Keys saved", "§a" + Profit.num(saved)).brief("Saved"));
            c.entrySet().stream().filter(e -> e.getKey().startsWith("Corpse: ")).forEach(e -> out.add(Profit.count(e.getKey().substring(8), "§f" + Profit.num(e.getValue())).key("Corpses")));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Profit.samples(Profit.COST, 1, -640_000, "SHATTERED_PENDANT", 1, 2_100_000, "GLACITE_JEWEL", 6, 360_000, "TUNGSTEN_PLATE", 3, 580_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Corpse: Lapis", 5d, "Corpse: Umber", 2d, "Keys saved", 1d);
        }
    };

    // from skyhanni MiningApi.inGlacialTunnels: the areas the repo mining data names
    private static boolean glacialTunnels() {
        return io.muzy.sbc.mining.MiningApi.inGlacialTunnels();
    }

    private static final Pattern POWDER = Pattern.compile("^\\+?[\\d,]+ Glacite Powder$");

    // skyhanni CorpseLootedEvent
    public static void corpseLooted(Mineshaft.Corpse type, List<String> items, boolean keyConsumed) {
        if (!Features.on("corpse_profit")) return;
        CORPSES.count("Corpse: " + type.type, 1);
        if (!keyConsumed) CORPSES.count("Keys saved", 1);
        else if (type.key != null) CORPSES.cost(price(type.key));
        for (String item : items) {
            String clean = GameState.strip(item).trim();
            if (clean.equals("Glacite Powder") || POWDER.matcher(clean).matches()) continue;
            Profit.addReward(CORPSES, clean);
        }
    }

    // --- dragons: skyhanni DragonProfitTracker, ProfitPerDragon, DragonFeatures ---

    private static int yourEyes, endPlace;
    private static double endTop, endDamage, weight;
    private static boolean dragonEnd, finishedLoot = true;
    private static String dragonType = "";
    private static long lastPlacedEyes;
    private static int lastPlacement;
    private static long scannedAt, downAt;
    private static final Set<UUID> SCANNED = new HashSet<>();
    private static final Map<String, Long> DRAGON_STONKS = new LinkedHashMap<>();
    private static Map<String, Integer> dragonItems;

    private static Map<String, Integer> dragonItems() {
        if (dragonItems != null) return dragonItems;
        Map<String, Integer> out = new HashMap<>();
        try {
            JsonObject root = Repo.constant("DragonProfitTrackerItems.json");
            if (root != null) for (var e : root.getAsJsonObject("items").entrySet()) out.put(e.getKey(), e.getValue().getAsJsonObject().get("weight").getAsInt());
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read the dragon items", e);
        }
        return dragonItems = out;
    }

    private static final Card DRAGON_CARD = new Card("dragon_profit", "Dragon profit", "Combat", 40, DRAGON) {
        @Override
        boolean relevant() {
            return island("THE_END") && ("Dragon's Nest".equals(GameState.area) || SbcMod.DEV);
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            double kills = 0;
            for (var e : c.entrySet()) if (e.getKey().startsWith("Dragon: ")) kills += e.getValue();
            double eyes = c.getOrDefault("Summoning Eyes", 0d);
            if (kills > 0) {
                out.add(Profit.count("Dragons", "§f" + Profit.num(kills)));
                double total = demo ? 3_200_000 : DRAGON.total(scope());
                out.add(Profit.count("Per dragon", "§6" + Prices.coins(total / kills)).brief("/dragon"));
            }
            if (eyes > 0) out.add(Profit.count("Summoning Eyes", "§d" + Profit.num(eyes)).brief("Eyes"));
            c.entrySet().stream().filter(e -> e.getKey().startsWith("Dragon: ")).forEach(e -> out.add(Profit.count(e.getKey().substring(8), "§f" + Profit.num(e.getValue())).key("Dragons")));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Profit.samples(Profit.COST, 1, -2_400_000, "ASPECT_OF_THE_DRAGON", 1, 1_400_000, "SUPERIOR_FRAGMENT", 24, 3_800_000, ESSENCE_DRAGON, 20, 60_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Dragon: Superior", 1d, "Dragon: Old", 2d, "Summoning Eyes", 6d);
        }
    };

    // from skyhanni getWeightForPlacement
    private static int placementWeight(int place) {
        return switch (place) {
            case -1 -> 10;
            case 1 -> 200;
            case 2 -> 175;
            case 3 -> 150;
            case 4 -> 125;
            case 5 -> 110;
            case 6, 7, 8 -> 100;
            case 9, 10 -> 90;
            case 11, 12 -> 80;
            default -> 70;
        };
    }

    static double dragonWeight(int eyes, int place, double first, double yours) {
        return placementWeight(yours == 0 ? -1 : place) + 100 * (eyes + yours / (first != 0 ? first : 1));
    }

    private static void dragonChat(String message) {
        String m = message.trim();
        Matcher x = Patterns.get("SH.combat.boss.dragon.1.chat.spawn").matcher(message);
        if (x.find()) {
            dragonType = x.group("dragon");
            lastPlacedEyes = yourEyes;
            if (yourEyes > 0) {
                DRAGON.count("Summoning Eyes", yourEyes);
                DRAGON.cost(price("SUMMONING_EYE") * yourEyes);
            }
            return;
        }
        if (Patterns.get("SH.combat.boss.dragon.1.chat.eye.placed.you").matcher(message).find()) {
            yourEyes++;
            return;
        }
        if (Patterns.get("SH.combat.boss.dragon.1.chat.eye.removed.you").matcher(message).find()) {
            yourEyes--;
            return;
        }
        if (Patterns.get("SH.combat.boss.dragon.1.chat.end.boss").matcher(message).find()) {
            dragonEnd = true;
            endTop = endDamage = endPlace = 0;
            return;
        }
        if (!dragonEnd) return;
        x = Patterns.get("SH.combat.boss.dragon.1.chat.end.place").matcher(message);
        if (x.find()) {
            if (x.group("position").equals("1")) endTop = Double.parseDouble(x.group("damage").replace(",", ""));
            return;
        }
        x = Patterns.get("SH.combat.boss.dragon.1.chat.end.position").matcher(message);
        if (!x.find()) return;
        endPlace = Integer.parseInt(x.group("position"));
        endDamage = Double.parseDouble(x.group("damage").replace(",", ""));
        weight = dragonWeight(yourEyes, endPlace, endTop, endDamage);
        boolean leeched = yourEyes == 0 && !on("dragon_profit", "dragon.count_leeched", true);
        if (endDamage > 0 && !leeched && !dragonType.isEmpty()) {
            DRAGON.count("Dragon: " + dragonType, 1);
            DRAGON.item(ESSENCE_DRAGON, dragonType.equals("Superior") ? 10 : 5);
        }
        lastPlacement = endPlace;
        finishedLoot = false;
        downAt = System.currentTimeMillis();
        dragonEnd = false;
        yourEyes = 0;
    }

    // from skyhanni ProfitPerDragon.scanForLoot: name tags of whats on the ground, then what the weight leaves
    // for fragments + pearls
    private static void scanDragonLoot(Minecraft mc) {
        if (finishedLoot || mc.level == null) return;
        long now = System.currentTimeMillis();
        if (now - scannedAt < 1000) return;
        scannedAt = now;
        if (now - downAt > 120_000) {
            finishedLoot = true;
            DRAGON_STONKS.clear();
            return;
        }
        Map<String, Integer> allowed = dragonItems();
        for (var entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ArmorStand stand) || !stand.hasCustomName() || stand.getCustomName() == null) continue;
            String name = io.muzy.sbc.core.LegacyText.of(stand.getCustomName());
            int split = name.lastIndexOf("§8x");
            long amount = 1;
            if (split >= 0) {
                try {
                    amount = Long.parseLong(name.substring(split + 3).trim());
                } catch (NumberFormatException ignored) {
                }
            }
            String id = Profit.idFor(split >= 0 ? name.substring(0, split) : name);
            if (id == null || !allowed.containsKey(id) || !SCANNED.add(stand.getUUID())) continue;
            DRAGON_STONKS.merge(id, amount, Long::sum);
        }
        if (!DRAGON_STONKS.isEmpty() && weight >= 290) finishDragon(weight - allowed.getOrDefault(DRAGON_STONKS.keySet().iterator().next(), 0));
        else if (weight < 290) finishDragon(weight);
    }

    private static int standardEnderPearl(int place) {
        if (place == 1) return 30;
        if (place == 2) return 25;
        if (place == 3) return 22;
        if (place == 4) return 20;
        if (place == 5) return 18;
        if (place >= 6 && place <= 12) return 15;
        if (place >= 13 && place <= 18) return 12;
        if (place >= 19 && place <= 25) return 10;
        return 5;
    }

    private static int standardEnchantedPearl(int place) {
        if (place == 1) return 7;
        if (place == 2) return 6;
        if (place == 3) return 5;
        if (place == 4) return 4;
        if (place == 5) return 3;
        if (place >= 6 && place <= 12) return 2;
        if (place >= 13 && place <= 18) return 1;
        return 0;
    }

    // from skyhanni calculateNonUniqueLoot + addDragonLootFromList
    static void finishDragon(double weightIn) {
        double w = weightIn;
        if (!dragonType.isEmpty()) {
            int fragments = (int) Math.floor(w / 22);
            w -= fragments * 22;
            if (fragments > 0) DRAGON_STONKS.merge(dragonType.toUpperCase(Locale.ROOT) + "_FRAGMENT", (long) fragments, Long::sum);
        }
        int enchanted = (int) Math.floor(w / 15);
        w -= enchanted * 15;
        enchanted += standardEnchantedPearl(lastPlacement);
        if (enchanted > 0) DRAGON_STONKS.merge("ENCHANTED_ENDER_PEARL", (long) enchanted, Long::sum);
        int pearls = (int) Math.floor(w / 5) + standardEnderPearl(lastPlacement);
        DRAGON_STONKS.merge("ENDER_PEARL", (long) pearls, Long::sum);
        if (lastPlacedEyes != 0 || on("dragon_profit", "dragon.count_leeched", true)) {
            DRAGON_STONKS.forEach(DRAGON::item);
            if (on("dragon_profit", "dragon.chat", true)) profitLine("Dragon", DRAGON_STONKS, price("SUMMONING_EYE") * lastPlacedEyes, "Placed Summoning Eyes", 0);
        }
        DRAGON_STONKS.clear();
        finishedLoot = true;
        lastPlacedEyes = 0;
    }

    // --- ender nodes: skyhanni EnderNodeTracker, EnderNode ---

    // from skyhanni EnderNode display names, the chat name says which pet rarity
    private static final Map<String, String> NODE_ITEMS = Map.ofEntries(
            Map.entry("§aEnchanted End Stone", "ENCHANTED_ENDSTONE"), Map.entry("§aEnchanted Obsidian", "ENCHANTED_OBSIDIAN"), Map.entry("§aEnchanted Ender Pearl", "ENCHANTED_ENDER_PEARL"),
            Map.entry("§aGrand Experience Bottle", "GRAND_EXP_BOTTLE"), Map.entry("§9Titanic Experience Bottle", "TITANIC_EXP_BOTTLE"), Map.entry("§9End Stone Shulker", "END_STONE_SHULKER"),
            Map.entry("§9End Stone Geode", "ENDSTONE_GEODE"), Map.entry("§d◆ Magical Rune I", "MAGIC_RUNE;1"), Map.entry("§5Mite Gel", "MITE_GEL"), Map.entry("§cShrimp the Fish", "SHRIMP_THE_FISH"),
            Map.entry("§5Ender Helmet", "END_HELMET"), Map.entry("§5Ender Chestplate", "END_CHESTPLATE"), Map.entry("§5Ender Leggings", "END_LEGGINGS"), Map.entry("§5Ender Boots", "END_BOOTS"),
            Map.entry("§5Ender Necklace", "ENDER_NECKLACE"), Map.entry("§5Ender Gauntlet", "ENDER_GAUNTLET"),
            Map.entry("§fEnderman", "ENDERMAN;0"), Map.entry("§aEnderman", "ENDERMAN;1"), Map.entry("§9Enderman", "ENDERMAN;2"), Map.entry("§5Enderman", "ENDERMAN;3"), Map.entry("§6Enderman", "ENDERMAN;4"));
    private static final Set<String> ENDER_ARMOR = Set.of("END_HELMET", "END_CHESTPLATE", "END_LEGGINGS", "END_BOOTS", "ENDER_NECKLACE", "ENDER_GAUNTLET");
    private static final Pattern ENDERMAN_DROP = Pattern.compile("(RARE|PET) DROP! §r(.+) §r§b\\(");

    // from skyhanni EnderNodeTracker.getLootProfit: armor is a fixed sum, the rest best of market, npc, george
    private static double nodePrice(String id) {
        if (ENDER_ARMOR.contains(id)) return 10_000;
        double george = switch (id) {
            case "ENDERMAN;0" -> 100;
            case "ENDERMAN;1" -> 500;
            case "ENDERMAN;2" -> 2_000;
            case "ENDERMAN;3" -> 10_000;
            case "ENDERMAN;4" -> 1_000_000;
            default -> 0;
        };
        return Math.max(george, Math.max(price(id), ItemRepo.npcPrice(id)));
    }

    // from skyhanni hasItemInHand: ItemCategory.miningTools by the last lore line
    private static boolean holdingMiningTool() {
        var p = net.minecraft.client.Minecraft.getInstance().player;
        if (p == null) return false;
        for (String l : io.muzy.sbc.core.LegacyText.lore(p.getMainHandItem())) {
            String s = GameState.strip(l);
            if (s.endsWith("PICKAXE") || s.endsWith("DRILL") || s.endsWith("GAUNTLET")) return true;
        }
        return false;
    }

    private static final Card NODE_CARD = new Card("ender_node_profit", "Ender Nodes", "Combat", 41, NODES) {
        @Override
        boolean relevant() {
            return island("THE_END") && (!on("ender_node_profit", "ender_node.only_tool", false) || holdingMiningTool());
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            double nodes = c.getOrDefault("Nodes", 0d), nests = c.getOrDefault("Endermite Nests", 0d);
            if (nodes > 0) {
                out.add(Profit.count("Nodes mined", "§d" + Profit.num(nodes)).brief("Nodes"));
                double total = demo ? 1_480_000 : NODES.total(scope());
                out.add(Profit.count("Per node", "§6" + Prices.coins(total / nodes)).brief("/node"));
            }
            if (nests > 0) out.add(Profit.count("Endermite Nests", "§c" + Profit.num(nests)).brief("Nests"));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Profit.samples("ENCHANTED_ENDER_PEARL", 85, 340_000, "END_STONE_SHULKER", 3, 90_000, "MITE_GEL", 120, 480_000, "ENDER_NECKLACE", 2, 20_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Nodes", 214d, "Endermite Nests", 3d);
        }
    };

    private static void enderNode(String formatted) {
        String message = formatted.trim();
        String item = null;
        long amount = 1;
        Matcher m = Patterns.get("SH.combat.endernodetracker.chat.multi").matcher(message);
        if (m.matches()) {
            item = m.group("name");
            amount = Long.parseLong(m.group("amount"));
            NODES.count("Nodes", 1);
        } else if ((m = Patterns.get("SH.combat.endernodetracker.chat.one").matcher(message)).matches()) {
            item = m.group("name");
            NODES.count("Nodes", 1);
        }
        m = ENDERMAN_DROP.matcher(message);
        if (m.find()) {
            item = m.group(2);
            amount = 1;
        }
        if (item == null) return;
        if (item.equals("§cEndermite Nest")) NODES.count("Endermite Nests", 1);
        String id = NODE_ITEMS.get(item);
        if (id != null) NODES.item(id, amount, nodePrice(id) * amount);
    }

    // --- ghosts: skyhanni GhostTracker ---

    private static Set<String> ghostDrops, ghostSackDrops;
    private static long ghostBodyCount = -1, bestiaryRead;
    private static double ghostXpAt;

    private static void ghostData() {
        if (ghostDrops != null) return;
        ghostDrops = new HashSet<>();
        ghostSackDrops = new HashSet<>();
        try {
            JsonObject root = Repo.constant("GhostDrops.json");
            if (root != null) {
                for (JsonElement e : root.getAsJsonArray("ghost_drops")) ghostDrops.add(e.getAsString());
                for (JsonElement e : root.getAsJsonArray("sacks_drops")) ghostSackDrops.add(e.getAsString());
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read the ghost drops", e);
        }
    }

    private static boolean inMist() {
        return "DWARVEN_MINES".equals(GameState.island) && "The Mist".equals(GameState.area);
    }

    private static final Card GHOST_CARD = new Card("ghost_profit", "Ghost profit", "Combat", 41, GHOST) {
        @Override
        boolean relevant() {
            return inMist() || SbcMod.DEV;
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            double kills = c.getOrDefault("Kills", 0d);
            if (kills > 0) {
                out.add(Profit.count("Kills", "§e" + Profit.num(kills)));
                double total = demo ? 2_940_000 : GHOST.total(scope());
                out.add(Profit.count("Per kill", "§6" + Prices.coins(total / kills)).brief("/kill"));
            }
            if (c.containsKey("Since Sorrow")) out.add(Profit.count("Since Sorrow", "§e" + Profit.num(c.get("Since Sorrow"))).brief("No Sorrow"));
            if (c.containsKey("Max combo")) out.add(Profit.count("Max Kill Combo", "§e" + Profit.num(c.get("Max combo"))).brief("Combo"));
            if (c.containsKey("Combat XP")) out.add(Profit.count("Combat XP", "§3" + Profit.num(c.get("Combat XP"))).brief("XP"));
            double finds = c.getOrDefault("MF finds", 0d);
            if (finds > 0) out.add(Profit.count("Average Magic Find", "§b" + String.format(Locale.ROOT, "%,.1f", c.getOrDefault("MF", 0d) / finds)).brief("MF"));
            long bestiary = demo ? 71_893 : ghostBodyCount;
            if (bestiary >= 0) out.add(Profit.count("Bestiary Kills", "§e" + (bestiary >= 100_000 ? "MAX" : Profit.num(bestiary))).brief("Bestiary"));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Profit.samples(Profit.COINS, 840_000, 840_000, "SORROW", 2, 3_800_000, "VOLTA", 14, 1_200_000, "ROUGH_JADE_GEM", 640, 12_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Kills", 7_813d, "Since Sorrow", 71d, "Max combo", 681d, "Combat XP", 4_687_800d, "MF", 3_312d, "MF finds", 12d);
        }
    };

    private static void clearSinceSorrow() {
        for (Trackers.Scope s : Trackers.Scope.values()) GHOST.numbers.items(s).remove("Since Sorrow");
    }

    private static void ghostChat(String clean) {
        Matcher m = Patterns.get("SH.combat.ghosttracker.itemdrop.colorless").matcher(clean);
        if (m.matches()) {
            ghostData();
            String id = Profit.idFor(m.group("item"));
            if (id == null || !ghostDrops.contains(id)) return;
            GHOST.item(id, 1);
            GHOST.count("MF", Integer.parseInt(m.group("mf")));
            GHOST.count("MF finds", 1);
            if (id.equals("SORROW")) clearSinceSorrow();
            return;
        }
        m = Patterns.get("SH.combat.ghosttracker.killcombo.end.colorless").matcher(clean);
        if (m.matches()) {
            GHOST.peak("Max combo", Profit.amount(m.group("kill").replace(".", "")));
            return;
        }
        if (Patterns.get("SH.combat.ghosttracker.bagofcash.colorless").matcher(clean).matches()) GHOST.coins(1_000_000);
    }

    // from skyhanni parseBestiaryWidget: more kills than last time (but not too many) = kills you made
    private static void readBestiary() {
        long now = System.currentTimeMillis();
        if (now - bestiaryRead < 1000) return;
        bestiaryRead = now;
        List<String> tab = GameState.tab;
        if (ghostBodyCount >= 100_000) return;
        for (TabWidgets.Widget w : TabWidgets.parse(tab)) {
            if (!w.def().id().equals("BESTIARY")) continue;
            for (int i : w.lines()) {
                String line = GameState.strip(tab.get(i));
                if (Patterns.get("SH.combat.ghosttracker.tablist.bestiarymax-no-color").matcher(line).matches()) {
                    ghostBodyCount = 100_000;
                    return;
                }
                Matcher m = Patterns.get("SH.combat.ghosttracker.tablist.bestiary-no-color").matcher(line);
                if (!m.matches()) continue;
                long kills = Profit.amount(m.group("kills").replace(".", ""));
                if (kills <= ghostBodyCount) return;
                long difference = kills - ghostBodyCount;
                boolean first = ghostBodyCount < 0;
                ghostBodyCount = kills;
                if (first || difference > 50) return;
                GHOST.count("Kills", difference);
                GHOST.count("Since Sorrow", difference);
                return;
            }
        }
    }

    private static final Pattern GAIN_NUMBER = Pattern.compile("^\\+([\\d,.]+)");

    // from skyhanni GhostTracker.onSkillExp, on what the action bar says a skill gained
    private static void ghostXp() {
        if (io.muzy.sbc.core.Vitals.skillAt == ghostXpAt || !"Combat".equals(io.muzy.sbc.core.Vitals.skill)) return;
        ghostXpAt = io.muzy.sbc.core.Vitals.skillAt;
        Matcher m = GAIN_NUMBER.matcher(io.muzy.sbc.core.Vitals.skillGain);
        if (!m.find()) return;
        try {
            double gained = Double.parseDouble(m.group(1).replace(",", ""));
            if (gained <= 10_000) GHOST.count("Combat XP", gained);
        } catch (NumberFormatException ignored) {
        }
    }

    // --- draconic sacrifice: skyhanni DraconicSacrificeTracker ---

    private static final Card SACRIFICE_CARD = new Card("sacrifice_profit", "Sacrifice profit", "Combat", 41, SACRIFICE) {
        // from skyhanni altarArea
        private boolean atAltar() {
            var p = Minecraft.getInstance().player;
            return p != null && p.getX() >= -601 && p.getX() <= -586 && p.getY() >= 4 && p.getY() <= 15 && p.getZ() >= -282 && p.getZ() <= -269;
        }

        @Override
        boolean relevant() {
            return island("THE_END") && (!on("sacrifice_profit", "sacrifice.only_void_slate", true) || atAltar() || SbcMod.DEV);
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            double done = c.getOrDefault("Sacrificed", 0d);
            if (done <= 0) return out;
            out.add(Profit.count("Items sacrificed", "§b" + Profit.num(done)).brief("Items"));
            double total = demo ? 1_240_000 : SACRIFICE.total(scope());
            out.add(Profit.count("Per sacrifice", "§6" + Prices.coins(total / done)).brief("/item"));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Profit.samples(ESSENCE_DRAGON, 284, 860_000, "WISE_FRAGMENT", 17, 380_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Sacrificed", 36d);
        }
    };

    private static void sacrifice(String message) {
        Matcher m = Patterns.get("SH.misc.draconicsacrifice.sacrifice").matcher(message);
        if (m.matches()) {
            SACRIFICE.item(ESSENCE_DRAGON, Long.parseLong(m.group("amount")));
            SACRIFICE.count("Sacrificed", 1);
            return;
        }
        m = Patterns.get("SH.misc.draconicsacrifice.bonus").matcher(message);
        if (m.matches()) {
            String id = Profit.idFor(m.group("item"));
            if (id != null) SACRIFICE.item(id, m.group("amount") == null ? 1 : Long.parseLong(m.group("amount")));
        }
    }

    // --- crystal nucleus: skyhanni CrystalNucleusTracker, CrystalNucleusApi ---

    private static final String[] ROBOT_PARTS = {"CONTROL_SWITCH", "ELECTRON_TRANSMITTER", "FTX_3070", "ROBOTRON_REFLECTOR", "SUPERLITE_MOTOR", "SYNTHETIC_HEART"};
    private static final Pattern BOOK = Pattern.compile("^(?:§f)?Enchanted Book \\((?<item>.+)\\)");
    private static boolean nucleusLoop;
    private static final Map<String, Long> NUCLEUS_STONKS = new LinkedHashMap<>();

    private static final Card NUCLEUS_CARD = new Card("nucleus_profit", "Crystal Nucleus", "Mining", 37, NUCLEUS) {
        @Override
        boolean relevant() {
            return island("CRYSTAL_HOLLOWS") && (on("nucleus_profit", "nucleus.outside", false) || "Crystal Nucleus".equals(GameState.area) || SbcMod.DEV);
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            double runs = c.getOrDefault("Runs", 0d);
            if (runs <= 0) return out;
            out.add(Profit.count("Runs completed", "§e" + Profit.num(runs)).brief("Runs"));
            double total = demo ? 4_810_000 : NUCLEUS.total(scope());
            out.add(Profit.count("Per run", "§6" + Prices.coins(total / runs)).brief("/run"));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Profit.samples(Profit.COST, 1, -3_600_000, "ROUGH_JASPER_GEM", 480, 240_000, "DIVAN_FRAGMENT", 3, 1_200_000, "GEMSTONE_MIXTURE", 2, 1_400_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Runs", 3d);
        }
    };

    // from skyhanni getPrecursorRunPrice + the jungle key
    private static double runCost() {
        double parts = 0;
        if (choice("nucleus_profit", "nucleus.professor", "Robot parts").equals("Precursor Apparatus")) parts = price("PRECURSOR_APPARATUS");
        else for (String p : ROBOT_PARTS) parts += price(p);
        return parts + price("JUNGLE_KEY");
    }

    private static void nucleusChat(String message) {
        if (Patterns.get("SH.mining.crystalnucleus.loot.start").matcher(message).matches()) {
            nucleusLoop = true;
            NUCLEUS_STONKS.clear();
            return;
        }
        Matcher bal = Patterns.get("SH.mining.crystalnucleus.tracker.bal.obtained").matcher(message);
        if (bal.matches()) {
            var p = Minecraft.getInstance().player;
            if (p != null && bal.group("player").equalsIgnoreCase(p.getName().getString())) NUCLEUS.item(bal.group("raritycolor").equals("6") ? "BAL;4" : "BAL;3", 1);
            return;
        }
        if (!nucleusLoop) return;
        if (message.startsWith("    ")) nucleusLoot(message.substring(4));
        if (Patterns.get("SH.mining.crystalnucleus.loot.end").matcher(message).matches()) {
            nucleusLoop = false;
            NUCLEUS.count("Runs", 1);
            NUCLEUS_STONKS.forEach(NUCLEUS::item);
            double cost = runCost();
            NUCLEUS.cost(cost);
            if (on("nucleus_profit", "nucleus.profit_per", true)) profitLine("Crystal Nucleus Run", NUCLEUS_STONKS, cost, "Keys and parts", integer("nucleus_profit", "nucleus.profit_minimum", 20_000));
            NUCLEUS_STONKS.clear();
        }
    }

    // from skyhanni CrystalNucleusApi.getLoot. books it cant name wait in skyhanni for your inventory, here theyre left out
    private static void nucleusLoot(String line) {
        String clean = GameState.strip(line).trim();
        Matcher after = Pattern.compile("^(.+?) x?([\\d,]+)$").matcher(clean), before = Pattern.compile("^x?([\\d,]+)x? (.+)$").matcher(clean);
        long n = 1;
        String name = clean;
        if (after.matches()) {
            name = after.group(1).trim();
            n = Profit.amount(after.group(2));
        } else if (before.matches()) {
            n = Profit.amount(before.group(1));
            name = before.group(2).trim();
        }
        if (name.contains(" Powder")) return;
        if (name.startsWith("Enchanted")) {
            Matcher book = BOOK.matcher(name);
            String type = book.find() ? book.group("item") : "";
            if (type.equals("Lapidary I")) NUCLEUS_STONKS.merge("LAPIDARY;1", 1L, Long::sum);
            else if (type.equals("Fortune IV")) NUCLEUS_STONKS.merge("FORTUNE;4", 1L, Long::sum);
            return;
        }
        String id = Profit.idFor(name);
        if (id != null) NUCLEUS_STONKS.merge(id, n, Long::sum);
    }

    // --- hunting: skyhanni HuntingProfitTracker + AttributeShardsData chat ---

    private static final String[] SHARD_LINES = {"caught.shards.colorless", "caught-multiple.shards", "loot.share.shard.colorless", "charmed.shard.colorless", "sent.to.hunting.box.colorless", "captured.shard", "floor-drop.shard", "given.shards", "boss.shard"};
    private static long lastHuntAt, toolHeldAt;
    private static Set<String> huntingTools;

    private static boolean huntingTool(ItemStack held) {
        if (held == null || held.isEmpty()) return false;
        if (huntingTools == null) {
            Set<String> out = new HashSet<>();
            try {
                JsonObject items = Repo.constant("Items.json");
                if (items != null) for (String key : new String[]{"hunting_blackholes", "hunting_axes"}) if (items.has(key)) for (JsonElement e : items.getAsJsonArray(key)) out.add(e.getAsString());
            } catch (Exception e) {
                SbcMod.LOG.warn("Could not read the hunting tools", e);
            }
            huntingTools = out;
        }
        if (huntingTools.contains(Gain.sbId(held))) return true;
        // skyhanni ItemCategory: an items last lore line says what it is
        List<String> lore = Watcher.lore(held);
        String last = lore.isEmpty() ? "" : GameState.strip(lore.get(lore.size() - 1));
        return last.endsWith(" LASSO") || last.contains(" FISHING NET");
    }

    private static boolean hunting() {
        long now = System.currentTimeMillis();
        var p = Minecraft.getInstance().player;
        if (p != null && huntingTool(p.getMainHandItem())) toolHeldAt = now;
        return on("hunting_profit", "hunting.always", false) || now - toolHeldAt < 10_000 || (on("hunting_profit", "hunting.after_pickup", true) && now - lastHuntAt < 10_000) || SbcMod.DEV;
    }

    private static final Card HUNTING_CARD = new Card("hunting_profit", "Hunting profit", "Hunting", 39, HUNTING) {
        @Override
        boolean relevant() {
            return hunting();
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            double caught = c.getOrDefault("Caught", 0d), shards = c.getOrDefault("Shards", 0d);
            if (caught > 0) {
                out.add(Profit.count("Mobs caught", "§e" + Profit.num(caught)).brief("Caught"));
                out.add(Profit.count("Shards collected", "§e" + Profit.num(shards)).brief("Shards"));
                double total = demo ? 1_920_000 : HUNTING.total(scope());
                out.add(Profit.count("Per catch", "§6" + Prices.coins(total / caught)).brief("/catch"));
            }
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Profit.samples("SHARD_GHOST", 42, 840_000, "SHARD_SEA_ARCHER", 16, 720_000, "SHARD_INVISIBUG", 90, 360_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Caught", 112d, "Shards", 148d);
        }
    };

    private static void shardChat(String clean) {
        for (String key : SHARD_LINES) {
            Matcher m = Patterns.get("SH.inventory.attributeshards." + key).matcher(clean);
            if (!m.matches()) continue;
            long amount = 1;
            try {
                String a = m.group("amount");
                if (a != null) amount = Long.parseLong(a);
            } catch (IllegalArgumentException ignored) {
            }
            String bazaar = AttributeShards.bazaarNameOf(m.group("shardName"));
            if (bazaar == null || amount <= 0) return;
            if (Features.on("hunting_profit")) {
                HUNTING.count("Caught", 1);
                HUNTING.count("Shards", amount);
                HUNTING.item(bazaar, amount);
                lastHuntAt = System.currentTimeMillis();
            }
            if (Features.on("shard_tracker") && AttributeShards.tracked(bazaar)) SHARDS.item(bazaar, amount);
            if (Features.on("ghost_profit") && inMist() && bazaar.equals(AttributeShards.bazaarNameOf("Ghost"))) GHOST.item(bazaar, amount);
            if (Features.on("nucleus_profit") && "CRYSTAL_HOLLOWS".equals(GameState.island) && bazaar.equals(AttributeShards.bazaarNameOf("Bal"))) NUCLEUS.item(bazaar, amount);
            return;
        }
    }

    // --- shard goals: skyhanni ShardTrackerDisplay ---

    private static final Card SHARD_CARD = new Card("shard_tracker", "Shard tracker", "Hunting", 39, SHARDS) {
        @Override
        public boolean visible() {
            return (GameState.inSkyBlock || SbcMod.DEV) && (demo || !AttributeShards.trackedRows().isEmpty());
        }

        @Override
        boolean relevant() {
            return true;
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            List<AttributeShards.TrackedRow> rows = demo ? List.of(new AttributeShards.TrackedRow("Ghost", 34, 120), new AttributeShards.TrackedRow("Invisibug", 96, 96)) : AttributeShards.trackedRows();
            for (var r : rows) {
                String have = (r.goal() > 0 && r.have() >= r.goal() ? "§a" : r.have() > 0 ? "§e" : "§c") + Profit.num(r.have());
                Line line = Profit.count(r.name(), have + (r.goal() > 0 ? "§7/§a" + Profit.num(r.goal()) : ""));
                if (r.goal() > 0) line.meter(Math.min(1, r.have() / (double) r.goal()));
                out.add(line.key("Shards"));
            }
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Profit.samples("SHARD_GHOST", 12, 240_000);
        }
    };

    // --- inferno minion: skyhanni InfernoMinionProfitTracker ---

    private static final int FUEL_SLOT = 19;
    private static Set<String> infernoFuels, minionDrops;
    private static boolean inInferno;
    private static String lastFuel;
    private static long collectedAt;

    private static void infernoData() {
        if (infernoFuels != null) return;
        infernoFuels = new HashSet<>();
        minionDrops = new HashSet<>();
        try {
            JsonObject fuels = Repo.constant("InfernoMinionFuels.json");
            if (fuels != null) for (JsonElement e : fuels.getAsJsonArray("inferno_minion_fuels")) infernoFuels.add(e.getAsString());
            JsonObject drops = Repo.constant("MinionDrops.json");
            if (drops != null) for (String key : new String[]{"fuel_drops", "minions"}) {
                if (!drops.has(key)) continue;
                for (var e : drops.getAsJsonObject(key).entrySet()) for (JsonElement i : e.getValue().getAsJsonArray()) minionDrops.add(i.getAsString());
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read the inferno fuels", e);
        }
    }

    private static final Card INFERNO_CARD = new Card("inferno_profit", "Inferno Minion profit", "Minions", 43, INFERNO) {
        @Override
        boolean relevant() {
            return inInferno || (on("inferno_profit", "inferno.after_collection", true) && System.currentTimeMillis() - collectedAt < 10_000) || SbcMod.DEV;
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            double n = c.getOrDefault("Collections", 0d);
            return n <= 0 ? List.of() : List.of(Profit.count("Collections", "§e" + Profit.num(n)));
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Profit.samples(Profit.COST, 1, -4_200_000, "CHILI_PEPPER", 8, 2_400_000, "ENCHANTED_MAGMA_CREAM", 512, 1_100_000, "INFERNO_VERTEX", 2, 5_600_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Collections", 6d);
        }
    };

    private static void infernoMenu(MenuView v) {
        infernoData();
        inInferno = true;
        String id = v.item(FUEL_SLOT).id;
        String fuel = infernoFuels.contains(id) ? id : null;
        if (fuel != null && lastFuel != null && !fuel.equals(lastFuel) || fuel != null && lastFuel == null && infernoSeen) INFERNO.cost(price(fuel));
        infernoSeen = true;
        lastFuel = fuel;
    }

    private static boolean infernoSeen;

    private static void infernoTick(Minecraft mc) {
        if (!inInferno) return;
        String title = mc.gui.screen() == null ? "" : GameState.strip(mc.gui.screen().getTitle().getString()).trim();
        if (Patterns.get("SH.minion.infernominiontitle").matcher(title).matches()) return;
        inInferno = false;
        infernoSeen = false;
        lastFuel = null;
        collectedAt = System.currentTimeMillis();
    }

    // from skyhanni onItemAdd: what you got within a minute after closing the minion, when a minion or its fuel can drop it
    private static void infernoGain(String id, int amount) {
        if (!Features.on("inferno_profit") || !"PRIVATE_ISLAND".equals(GameState.island) || System.currentTimeMillis() - collectedAt > 60_000) return;
        infernoData();
        if (!minionDrops.contains(id)) return;
        INFERNO.item(id, amount);
        INFERNO.count("Collections", 1);
    }

    // --- timite: skyhanni TimiteTracker ---

    private static final Set<String> TIMITE_ITEMS = Set.of("TIMITE", "YOUNGITE", "OBSOLITE");

    // from skyhanni TimiteTracker.timiteItems
    private static final java.util.Set<String> TIMITE_TOOLS = java.util.Set.of("ANTI_SENTIENT_PICKAXE", "EON_PICKAXE", "CHRONO_PICKAXE", "TIME_GUN");

    private static final Card TIMITE_CARD = new Card("timite_tracker", "Timite tracker", "Rift", 45, TIMITE) {
        @Override
        boolean relevant() {
            if (SbcMod.DEV) return true;
            if (!RiftApi.inMountainTop()) return false;
            var f = io.muzy.sbc.feature.Features.get("timite_tracker");
            var only = f == null ? null : f.option("timite_tracker.only_holding");
            return only == null || !only.bool() || TIMITE_TOOLS.contains(io.muzy.sbc.rift.RiftScan.heldId(net.minecraft.client.Minecraft.getInstance()));
        }

        @Override
        String heading(double total, Map<String, Double> c) {
            return "§d" + Profit.num(motes(c)) + " Motes";
        }

        private double motes(Map<String, Double> c) {
            double sum = 0;
            for (String id : TIMITE_ITEMS) sum += c.getOrDefault(id, 0d) * (demo ? 20 : ItemRepo.motesPrice(id));
            return sum;
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            for (String id : List.of("TIMITE", "YOUNGITE", "OBSOLITE")) {
                double n = c.getOrDefault(id, 0d);
                if (n > 0) out.add(Profit.count(Profit.pretty(id), "§a" + Profit.num(n) + " §d" + Profit.brief(n * (demo ? 20 : ItemRepo.motesPrice(id)))));
            }
            double timite = c.getOrDefault("TIMITE", 0d);
            if (timite > 0) out.add(Profit.count("Time", "§a" + Profit.time((long) (timite * 2_000))));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Map.of();
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("TIMITE", 840d, "YOUNGITE", 120d, "OBSOLITE", 64d);
        }
    };

    // --- strays: skyhanni CFStrayTracker ---

    private static final String[] RARITIES = {"Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythic"};
    private static final Set<Integer> CLAIMED = new HashSet<>();
    private static final Map<String, String> GOLDEN = new LinkedHashMap<>();

    static {
        GOLDEN.put("sidedish", "Side Dishes");
        GOLDEN.put("jackpot", "Chocolate Jackpots");
        GOLDEN.put("mountain", "Chocolate Mountains");
        GOLDEN.put("dorado", "El Dorado Sightings");
        GOLDEN.put("stampede", "Stampedes");
        GOLDEN.put("goldenclick", "Golden Clicks");
    }

    private static String rarityColor(String rarity) {
        return switch (rarity) {
            case "Uncommon" -> "§a";
            case "Rare" -> "§9";
            case "Epic" -> "§5";
            case "Legendary" -> "§6";
            case "Mythic" -> "§d";
            default -> "§f";
        };
    }

    private static final Card STRAY_CARD = new Card("stray_tracker", "Stray rabbits", "Events", 44, STRAY) {
        @Override
        boolean relevant() {
            return ChocolateFactory.inChocolateFactory || SbcMod.DEV;
        }

        @Override
        String heading(double total, Map<String, Double> c) {
            double ms = 0;
            for (var e : c.entrySet()) if (e.getKey().startsWith("ms ")) ms += e.getValue();
            return "§a+" + Profit.time((long) ms);
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            for (String r : RARITIES) {
                Double n = c.get("caught " + r);
                if (n == null) continue;
                out.add(Profit.count(r, rarityColor(r) + Profit.num(n) + " §b" + Profit.time(c.getOrDefault("ms " + r, 0d).longValue())).key(r));
            }
            GOLDEN.forEach((key, name) -> {
                Double n = c.get("golden " + key);
                if (n != null) out.add(Profit.count(name, "§6" + Profit.num(n)).key("Golden"));
            });
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Map.of();
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("caught Common", 88d, "ms Common", 1_800_000d, "caught Rare", 12d, "ms Rare", 900_000d, "caught Legendary", 3d, "ms Legendary", 5_400_000d, "golden jackpot", 2d, "golden stampede", 1d);
        }
    };

    // from skyhanni HoppityApi.rarityByRabbit, by the color before the name
    private static String rarityOf(String coloured) {
        if (coloured.length() < 2) return null;
        return switch (coloured.charAt(1)) {
            case 'f' -> "Common";
            case 'a' -> "Uncommon";
            case '9' -> "Rare";
            case '5' -> "Epic";
            case '6' -> "Legendary";
            case 'd' -> "Mythic";
            default -> null;
        };
    }

    private static void stray(String rarity, long chocolate) {
        STRAY.count("caught " + rarity, 1);
        io.muzy.sbc.events.hoppity.HoppitySummary.addStrayCaught(rarity, chocolate);
        long ms = ChocolateFactory.timeUntilNeed(chocolate + 1);
        if (ms > 0 && ms != Long.MAX_VALUE) STRAY.count("ms " + rarity, ms);
    }

    // from skyhanni handleStrayClicked: a rabbit that says it got caught, once each time
    static void strayMenu(MenuView v) {
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            if (!Patterns.get("SH.misc.chocolatefactory.stray.caught").matcher(it.name).find()) {
                CLAIMED.remove(slot);
                continue;
            }
            if (!CLAIMED.add(slot)) continue;
            strayLore(String.join(" ", it.lore.stream().filter(l -> !l.isEmpty()).toList()));
        }
    }

    static void strayLore(String lore) {
        String key = "SH.misc.chocolatefactory.stray.";
        Matcher m = Patterns.get(key + "loreinfo").matcher(lore);
        if (m.find()) {
            String rarity = rarityOf(m.group("rabbit"));
            if (rarity != null) stray(rarity, Profit.amount(m.group("amount")));
        }
        m = Patterns.get(key + "fish").matcher(lore);
        if (m.matches()) {
            String rarity = rarityOf(m.group("color"));
            if (rarity != null) stray(rarity, m.group("amount") == null ? 0 : Profit.amount(m.group("amount")));
        }
        m = Patterns.get(key + "goldenrawchoc").matcher(lore);
        if (m.find()) {
            long amount = Profit.amount(m.group("amount"));
            stray("Legendary", amount);
            double multiplier = ChocolateFactory.chocolatePerSecond > 0 ? amount / ChocolateFactory.chocolatePerSecond : 0;
            if (multiplier >= 479 && multiplier <= 481) STRAY.count("golden jackpot", 1);
            else if (multiplier >= 1499 && multiplier <= 1501) STRAY.count("golden mountain", 1);
        }
        if (Patterns.get(key + "goldenclick").matcher(lore).find()) {
            STRAY.count("golden goldenclick", 1);
            stray("Legendary", 0);
        }
        if (Patterns.get(key + "hoard").matcher(lore.replace("§r", "")).find()) {
            STRAY.count("golden stampede", 1);
            stray("Legendary", 0);
        }
        m = Patterns.get(key + "dorado").matcher(lore);
        if (m.find()) {
            if (m.group("amount") != null) stray("Legendary", Profit.amount(m.group("amount")));
            STRAY.count("golden dorado", 1);
        }
    }

    // --- shiny orb: skyhanni ShinyOrbTracker, PigFeaturesApi ---

    private static final Card ORB_CARD = new Card("shiny_orb_profit", "Shiny Orb profit", "Events", 44, ORB) {
        @Override
        boolean relevant() {
            if (!island("HUB") || !(yearOfThePig() || SbcMod.DEV)) return false;
            if (!on("shiny_orb_profit", "shiny_orb.holding", false)) return true;
            var p = Minecraft.getInstance().player;
            String held = p == null ? "" : Gain.sbId(p.getMainHandItem());
            return held.equals("SHINY_ORB") || held.equals("SHINY_ROD") || SbcMod.DEV;
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            double done = c.getOrDefault("Charged", 0d);
            if (done > 0) {
                out.add(Profit.count("Orbs charged", "§6" + Profit.num(done)).brief("Orbs"));
                double total = demo ? 480_000 : ORB.total(scope());
                out.add(Profit.count("Per orb", "§6" + Prices.coins(total / done)).brief("/orb"));
            }
            c.entrySet().stream().filter(e -> e.getKey().startsWith("XP: ")).forEach(e -> out.add(Profit.count(e.getKey().substring(4) + " XP", "§3" + Profit.num(e.getValue())).key("XP")));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Profit.samples(Profit.COINS, 100_000, 100_000, Profit.COST, 1, -60_000, "SHINY_SHARD", 12, 240_000, "ENCHANTED_PORK", 32, 200_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Charged", 12d, "XP: Farming", 1_400d);
        }
    };

    // from skyhanni PigFeaturesApi.isYearOfThePig
    private static boolean yearOfThePig() {
        return SbTime.sbDate(System.currentTimeMillis()).year() % 12 == 11;
    }

    private static final double ORB_PRICE = 5000;
    private static final String ORB_LINES = "SH.event.year-of-the-pig.";

    // skyhanni counts an orb as used when you click a pig with it. every used orb ends in chat (charged or
    // expired) and we only read those 2 lines, so no entity click needed
    private static void orbChat(String clean) {
        if (Patterns.get(ORB_LINES + "chat.orb.charged.colorless").matcher(clean).matches()) {
            ORB.count("Charged", 1);
            ORB.cost(ORB_PRICE);
            return;
        }
        if (Patterns.get(ORB_LINES + "chat.orb.expired.colorless").matcher(clean).matches()) {
            ORB.cost(ORB_PRICE);
            return;
        }
        Matcher m = Patterns.get(ORB_LINES + "chat.orb.looted").matcher(clean);
        if (!m.matches()) return;
        ORB.item("SHINY_SHARD", 1);
        String reward = m.group("reward");
        Matcher coins = Patterns.get(ORB_LINES + "orb.reward.coins").matcher(reward);
        if (coins.matches()) {
            ORB.coins(Profit.amount(coins.group("amount")));
            return;
        }
        Matcher xp = Patterns.get(ORB_LINES + "orb.reward.skillxp").matcher(reward);
        if (xp.matches()) {
            ORB.count("XP: " + xp.group("skill"), Profit.amount(xp.group("amount")));
            return;
        }
        Profit.addReward(ProfitCards.ORB, reward);
    }

    // --- gifts: skyhanni GiftProfitTracker, IsGiftingDetection ---

    private static final String GIFTS = "SH.misc.gifting.reward.";
    private static final Set<String> GIFT_ITEMS = Set.of("WHITE_GIFT", "GREEN_GIFT", "RED_GIFT", "PARTY_GIFT");
    private static long giftAt;
    private static double giftX, giftY, giftZ;

    private static boolean holdingGift() {
        var p = Minecraft.getInstance().player;
        return p != null && GIFT_ITEMS.contains(Gain.sbId(p.getMainHandItem()));
    }

    private static boolean giftedHere() {
        var p = Minecraft.getInstance().player;
        return p != null && giftAt != 0 && System.currentTimeMillis() - giftAt < 180_000 && p.distanceToSqr(giftX, giftY, giftZ) <= 100;
    }

    private static void markGift() {
        var p = Minecraft.getInstance().player;
        giftAt = System.currentTimeMillis();
        if (p != null) {
            giftX = p.getX();
            giftY = p.getY();
            giftZ = p.getZ();
        }
    }

    private static final Card GIFT_CARD = new Card("gift_profit", "Gift profit", "Events", 44, GIFT) {
        @Override
        boolean relevant() {
            if (SbcMod.DEV) return true;
            if (on("gift_profit", "gift.holding", false)) return holdingGift();
            return holdingGift() || giftedHere();
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            double rewards = 0;
            for (var e : c.entrySet()) if (e.getKey().startsWith("Tier: ")) rewards += e.getValue();
            if (rewards > 0) {
                out.add(Profit.count("Rewards", "§e" + Profit.num(rewards)));
                double total = demo ? 1_380_000 : GIFT.total(scope());
                out.add(Profit.count("Per gift", "§6" + Prices.coins(total / rewards)).brief("/gift"));
            }
            double stars = c.getOrDefault("North Stars", 0d), used = c.getOrDefault("Gifts used", 0d);
            if (used > 0) out.add(Profit.count("Gifts used", "§c" + Profit.num(used)).brief("Used"));
            if (stars > 0) out.add(Profit.count("North Stars", "§d" + Profit.num(stars)).brief("Stars"));
            c.entrySet().stream().filter(e -> e.getKey().startsWith("Tier: ")).forEach(e -> out.add(Profit.count(e.getKey().substring(6), "§f" + Profit.num(e.getValue())).key("Tiers")));
            c.entrySet().stream().filter(e -> e.getKey().startsWith("XP: ")).forEach(e -> out.add(Profit.count(e.getKey().substring(4) + " XP", "§3" + Profit.num(e.getValue())).key("XP")));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Profit.samples(Profit.COINS, 240_000, 240_000, Profit.COST, 1, -360_000, "ICE_RUNE;1", 1, 80_000, "POTION_FORAGING_XP_BOOST;3", 3, 120_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Tier: COMMON", 42d, "Tier: RARE", 9d, "North Stars", 24d, "Gifts used", 30d, "XP: Combat", 12_500d);
        }
    };

    private static void giftChat(String message) {
        Matcher m = Patterns.get(GIFTS + "northstars").matcher(message);
        if (m.matches()) {
            GIFT.count("North Stars", Profit.amount(m.group("amount")));
            markGift();
            return;
        }
        m = Patterns.get(GIFTS + "rarity").matcher(message);
        if (!m.lookingAt()) return;
        GIFT.count("Tier: " + m.group("rarity"), 1);
        markGift();
        m = Patterns.get(GIFTS + "skillxp").matcher(message);
        if (m.matches()) {
            GIFT.count("XP: " + m.group("skill"), Profit.amount(m.group("amount")));
            return;
        }
        m = Patterns.get(GIFTS + "coins").matcher(message);
        if (m.matches()) {
            GIFT.coins(Profit.amount(m.group("amount")));
            return;
        }
        m = Patterns.get(GIFTS + "boostpotion").matcher(message);
        if (m.matches()) {
            GIFT.item("POTION_" + m.group("skill").toUpperCase(Locale.ROOT) + "_XP_BOOST;" + GameState.roman(m.group("tier")), 1);
            return;
        }
        m = Patterns.get(GIFTS + "enchantmentbook").matcher(message);
        if (m.matches()) {
            GIFT.item(m.group("enchantment").toUpperCase(Locale.ROOT).replace(' ', '_') + ";" + GameState.roman(m.group("tier")), 1);
            return;
        }
        m = Patterns.get(GIFTS + "generic").matcher(message);
        if (m.matches()) {
            String item = m.group("item");
            if (item.equals("◆ Ice Rune")) GIFT.item("ICE_RUNE;1", 1);
            else Profit.addReward(GIFT, item);
        }
    }

    // skyhannis /shaddusedgifts: gifts leave + come back to your inventory too often to catch, so you say how many you used
    private static int addUsedGifts(String type, int amount) {
        String id = type.toUpperCase(Locale.ROOT) + "_GIFT";
        if (!GIFT_ITEMS.contains(id)) return 0;
        GIFT.count("Gifts used", amount);
        GIFT.cost(price(id) * amount);
        return 1;
    }

    // --- trevor: skyhanni TrevorTracker ---

    private static final String[] TREVOR_RARITIES = {"TRACKABLE", "UNTRACKABLE", "UNDETECTED", "ENDANGERED", "ELUSIVE"};

    // tracker card is up and shows the pelts
    public static boolean trevorTrackerUp() {
        return Features.on("trevor_tracker") && TREVOR_CARD.relevant();
    }

    private static final Card TREVOR_CARD = new Card("trevor_tracker", "Trevor tracker", "Misc", 64, TREVOR) {
        @Override
        boolean relevant() {
            // from skyhanni TrevorTracker displayType: between quests too or only during one. trappers den = always
            if (!island("THE_FARMING_ISLANDS")) return false;
            var between = io.muzy.sbc.feature.Features.get("trevor_tracker").option("trevor_tracker.between");
            return between == null || between.bool() || io.muzy.sbc.misc.Trevor.questActive() || "Trapper's Den".equals(io.muzy.sbc.core.GameState.area.trim());
        }

        @Override
        String heading(double total, Map<String, Double> c) {
            return "§d" + Profit.num(c.getOrDefault("Pelts", 0d)) + " Pelts";
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            out.add(Profit.count("Quests started", "§9" + Profit.num(c.getOrDefault("Quests", 0d))).brief("Quests"));
            long active = demo ? 1_893_000 : TREVOR.activeMs();
            double session = demo ? 248 : TREVOR.count("Pelts");
            if (active >= 30_000) out.add(Profit.count("Pelts per hour", "§d" + Profit.num(session * 3_600_000 / active)).brief("Pelts/h"));
            out.add(Profit.count("Killed animals", "§c" + Profit.num(c.getOrDefault("Killed", 0d))).brief("Killed"));
            out.add(Profit.count("Self killing", "§c" + Profit.num(c.getOrDefault("Self killed", 0d))).brief("Self"));
            for (String r : TREVOR_RARITIES) {
                Double n = c.get(r);
                if (n != null) out.add(Profit.count(r.charAt(0) + r.substring(1).toLowerCase(Locale.ROOT), "§f" + Profit.num(n)).key("Animals"));
            }
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Map.of();
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Quests", 62d, "Pelts", 1_040d, "Killed", 48d, "Self killed", 9d, "TRACKABLE", 32d, "UNTRACKABLE", 21d, "UNDETECTED", 9d);
        }
    };

    private static void trevorChat(String message, String clean) {
        Matcher m = Patterns.get("SH.misc.trevor.selfkill").matcher(message);
        if (m.matches()) {
            TREVOR.count("Pelts", Long.parseLong(m.group("pelts")));
            TREVOR.count("Self killed", 1);
            return;
        }
        m = Patterns.get("SH.misc.trevor.kill").matcher(message);
        if (m.matches()) {
            TREVOR.count("Pelts", Long.parseLong(m.group("pelts")));
            TREVOR.count("Killed", 1);
            return;
        }
        m = Patterns.get("SH.misc.trevor.trapper").matcher(clean);
        if (m.matches()) {
            TREVOR.count("Quests", 1);
            TREVOR.count(m.group("rarity"), 1);
        }
    }

    // --- sea creatures: skyblocker SeaCreatureTracker ---

    private static Map<String, String> rarities(Map<String, Double> c, String prefix, boolean percent) {
        double total = 0;
        for (var e : c.entrySet()) if (e.getKey().startsWith(prefix)) total += e.getValue();
        Map<String, String> out = new LinkedHashMap<>();
        for (var e : c.entrySet()) {
            if (!e.getKey().startsWith(prefix)) continue;
            out.put(e.getKey().substring(prefix.length()), "§f" + Profit.num(e.getValue()) + (percent && total > 0 ? String.format(Locale.ROOT, " §7%.1f%%", 100 * e.getValue() / total) : ""));
        }
        return out;
    }

    private static final Card SEA_CARD = new Card("sea_creature_tracker", "Sea creatures", "Fishing", 40, SEA) {
        @Override
        boolean relevant() {
            Minecraft mc = Minecraft.getInstance();
            return mc.player != null && mc.player.fishing != null || io.muzy.sbc.areas.FishForageHunt.fishingRodHeld() || SbcMod.DEV;
        }

        @Override
        String heading(double total, Map<String, Double> c) {
            double n = 0;
            for (double v : c.values()) n += v;
            return "§b" + Profit.num(n);
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            // from skyblocker SeaCreatureTracker: whos alive vs the cap of 10 + time till the oldest despawns
            int alive = io.muzy.sbc.areas.FishState.liveCount();
            if (alive > 0 && on("sea_creature_tracker", "sea.live", true)) {
                long left = Math.max(0, io.muzy.sbc.areas.FishState.DESPAWN_MS - io.muzy.sbc.areas.FishState.oldestAge());
                out.add(Profit.count("Alive", "§b" + alive + "§7/10").key("Alive"));
                out.add(Profit.count("Time left", "§b" + io.muzy.sbc.core.SbTime.dur(left)).key("Time left"));
            }
            rarities(c, "", on("sea_creature_tracker", "sea.percent", true)).forEach((name, value) -> out.add(Profit.count(name, value).key("Caught")));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Map.of();
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Sea Walker", 42d, "Night Squid", 11d, "Water Hydra", 2d);
        }
    };

    // from skyblocker onSeaCreatureFish, nav/Fishing knows the line
    public static void seaCreature(String name) {
        if (!Features.on("sea_creature_tracker")) return;
        boolean twice = System.currentTimeMillis() - doubleHookAt < 2000 && on("sea_creature_tracker", "sea.count_double", true);
        SEA.count(name, twice ? 2 : 1);
    }

    private static long doubleHookAt;
    private static final Pattern DOUBLE_HOOK = Pattern.compile("Double Hook!(?: Woot woot!)?");

    // --- diana: skyhanni DianaProfitTracker, MythologicalCreatureTracker ---

    private static Set<String> dianaDrops;

    private static boolean dianaDrop(String id) {
        if (dianaDrops == null) {
            Set<String> out = new HashSet<>();
            try {
                JsonObject o = Repo.constant("DianaDrops.json");
                if (o != null) for (JsonElement e : o.getAsJsonArray("diana_drops")) out.add(e.getAsString());
            } catch (Exception e) {
                SbcMod.LOG.warn("Could not read the Diana drops", e);
            }
            dianaDrops = out;
        }
        return dianaDrops.contains(id);
    }

    private static final Card DIANA_CARD = new Card("diana_profit", "Diana profit", "Events", 44, DIANA) {
        @Override
        boolean relevant() {
            return io.muzy.sbc.events.diana.DianaApi.isDoingDiana() || io.muzy.sbc.events.diana.DianaApi.hasSpadeInHand() || SbcMod.DEV;
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            double dug = c.getOrDefault("Burrows", 0d);
            if (dug > 0) {
                out.add(Profit.count("Burrows dug", "§e" + Profit.num(dug)).brief("Burrows"));
                double total = demo ? 8_100_000 : DIANA.total(scope());
                out.add(Profit.count("Per burrow", "§6" + Prices.coins(total / dug)).brief("/burrow"));
            }
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Profit.samples(Profit.COINS, 2_400_000, 2_400_000, "GRIFFIN_FEATHER", 34, 3_400_000, "ANCIENT_CLAW", 120, 2_300_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Burrows", 412d);
        }
    };

    private static final Card MYTH_CARD = new Card("mythological_tracker", "Mythological mobs", "Events", 44, MYTH) {
        @Override
        boolean relevant() {
            return io.muzy.sbc.events.diana.DianaApi.isDoingDiana() || io.muzy.sbc.events.diana.DianaApi.hasSpadeInHand() || SbcMod.DEV;
        }

        @Override
        String heading(double total, Map<String, Double> c) {
            double n = 0;
            for (double v : c.values()) n += v;
            return "§c" + Profit.num(n);
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            rarities(c, "", on("mythological_tracker", "myth.percent", true)).forEach((name, value) -> out.add(Profit.count(name, value).key("Mobs")));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Map.of();
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Minos Hunter", 42d, "Siamese Lynxes", 18d, "Minos Inquisitor", 2d);
        }
    };

    private static final Set<String> BLACK_HOLE_MOBS = Set.of("Cretan Bull", "Harpy", "Minotaur");

    private static void dianaChat(String message, String clean) {
        if (Features.on("diana_profit")) {
            if (Patterns.get("SH.diana.chat.burrow.dug.colorless").matcher(clean).matches()) DIANA.count("Burrows", 1);
            Matcher coins = Patterns.get("SH.diana.chat.coins.colorless").matcher(clean);
            if (coins.matches()) DIANA.coins(Profit.amount(coins.group("coins").replace(".", "")));
        }
        Matcher mob = Patterns.get("SH.event.diana.mythological.burrows.generic-spawn").matcher(message);
        if (!mob.find()) return;
        String creature = mob.group("creatureType").trim();
        if (Features.on("mythological_tracker")) MYTH.count(creature, 1);
        if (on("mythological_tracker", "myth.black_hole", false) && BLACK_HOLE_MOBS.contains(creature)) {
            var hud = Minecraft.getInstance().gui.hud;
            hud.setTimes(5, 40, 10);
            hud.setTitle(Component.literal("Black Hole"));
            hud.setSubtitle(Component.empty());
        }
    }

    // --- beach balls: skyhanni BeachBallTracker ---

    private static long ballAt;

    private static final Card BEACH_CARD = new Card("beach_ball_tracker", "Beach Balls", "Events", 44, BEACH) {
        @Override
        boolean relevant() {
            return island("HUB") && System.currentTimeMillis() - ballAt < integer("beach_ball_tracker", "beach.hide_after", 5) * 60_000L || SbcMod.DEV;
        }

        @Override
        String heading(double total, Map<String, Double> c) {
            return "§d" + Profit.num(c.getOrDefault("Treats", 0d)) + " Treats";
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            double balls = c.getOrDefault("Beach Balls", 0d), giant = c.getOrDefault("Giant Beach Balls", 0d), treats = c.getOrDefault("Treats", 0d);
            if (balls > 0) out.add(Profit.count("Beach Balls", "§e" + Profit.num(balls)).brief("Balls"));
            if (giant > 0) out.add(Profit.count("Giant Beach Balls", "§e" + Profit.num(giant)).brief("Giant"));
            if (balls + giant > 0) out.add(Profit.count("Treats per Ball", "§e" + String.format(Locale.ROOT, "%,.1f", treats / (balls + giant))).brief("/ball"));
            long active = demo ? 1_893_000 : BEACH.activeMs();
            double session = demo ? 126 : BEACH.count("Treats");
            if (active >= 30_000) out.add(Profit.count("Treats per Hour", "§e" + Profit.num(session * 3_600_000 / active)).brief("/hour"));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Map.of();
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Beach Balls", 14d, "Giant Beach Balls", 2d, "Treats", 126d);
        }
    };

    private static void beachChat(String clean) {
        String key = "SH.event.year-of-the-seal.beach-ball-tracker.";
        if (Patterns.get(key + "start").matcher(clean).matches()) {
            ballAt = System.currentTimeMillis();
            return;
        }
        Matcher m = Patterns.get(key + "result.normal").matcher(clean);
        String type = "Beach Balls";
        if (!m.matches()) {
            m = Patterns.get(key + "result.giant").matcher(clean);
            type = "Giant Beach Balls";
            if (!m.matches()) return;
        }
        ballAt = System.currentTimeMillis();
        BEACH.count(type, 1);
        BEACH.count("Treats", Long.parseLong(m.group("treats")));
    }

    // --- dark monolith: skyhanni DarkMonolithFeatures tracker ---

    private static final Card MONOLITH_CARD = new Card("dark_monolith_tracker", "Dark Monolith", "Mining", 37, MONOLITH) {
        @Override
        boolean relevant() {
            return island("DWARVEN_MINES");
        }

        @Override
        List<Line> own(Map<String, Double> c) {
            List<Line> out = new ArrayList<>();
            double n = c.getOrDefault("Monoliths", 0d), powder = c.getOrDefault("Mithril Powder", 0d);
            if (n > 0) out.add(Profit.count("Monoliths looted", "§d" + Profit.num(n)).brief("Looted"));
            if (powder > 0) out.add(Profit.count("Mithril Powder", "§2" + Profit.num(powder)).brief("Mithril"));
            return out;
        }

        @Override
        Map<String, double[]> sampleItems() {
            return Profit.samples(Profit.COINS, 1_200_000, 1_200_000);
        }

        @Override
        Map<String, Double> sampleCounts() {
            return Map.of("Monoliths", 18d, "Mithril Powder", 14_000d);
        }
    };

    private static void monolithChat(String clean) {
        Matcher m = Patterns.get("SH.mining.dwarves.darkmonolith.drop.chat.colorless").matcher(clean);
        if (!m.matches()) return;
        MONOLITH.count("Monoliths", 1);
        if (m.group("coins") != null) MONOLITH.coins(Profit.amount(m.group("coins")));
        if (m.group("powder") != null) MONOLITH.count("Mithril Powder", Profit.amount(m.group("powder")));
    }

    // --- npc day limit: skyhanni NpcDayLimitTracker ---

    private static final long NPC_LIMIT = io.muzy.sbc.areas.MiscExtras.NPC_DAILY_LIMIT;
    private static final Trackers.Named NPC_DAY = Trackers.numbers("npc_day_limit", "NPC day limit");

    private static long gmtDay() {
        return java.time.LocalDate.now(java.time.ZoneOffset.UTC).toEpochDay();
    }

    // sold to npcs today, saved. the only count of it, the card + session panel + warning all read this
    public static double npcSold() {
        Map<String, double[]> all = NPC_DAY.items(Trackers.Scope.ALL);
        double[] day = all.get("day"), sold = all.get("sold");
        if (day == null || sold == null || day[0] != gmtDay()) return 0;
        return sold[0];
    }

    // from skyhanni rollDayIfNeeded + the sale: day turns at midnight gmt
    static void npcSale(long amount) {
        double before = npcSold();
        NPC_DAY.reset(Trackers.Scope.ALL);
        NPC_DAY.addEntry("day", gmtDay(), 0);
        NPC_DAY.addEntry("sold", before + amount, 0);
    }

    // from skyhanni NpcDayLimitTracker.onChat: true = a sale line. counted whatever switches are on so turning
    // one on later knows the day
    public static boolean npcLine(String clean) {
        Matcher sold = Patterns.get("SH.misc.npcdaylimit.sold").matcher(clean);
        if (!sold.matches()) return false;
        npcSale(Profit.amount(sold.group("amount")));
        return true;
    }

    // day count from zero, for tests
    public static void npcForget() {
        NPC_DAY.reset(Trackers.Scope.ALL);
    }

    static int showFrom() {
        var f = Features.get("npc_day_limit");
        var o = f == null ? null : f.option("npc.show_from");
        return o == null ? 25 : o.integer();
    }

    private static final io.muzy.sbc.rift.RichCard NPC_CARD = new io.muzy.sbc.rift.RichCard("npc_day_limit", "NPC sell limit", "Misc", io.muzy.sbc.ui.HudLayout.Zone.TOP_LEFT, 63, "npc_day_limit") {
        private String coins(double v) {
            return choice("npc_day_limit", "npc.format", "Short").equals("Long") ? Profit.num(v) : Prices.coins(v);
        }

        private Content of(double sold) {
            return new Content("§6" + coins(sold) + "§7/§a" + coins(NPC_LIMIT), List.of(Line.row("Left today", "§a" + coins(Math.max(0, NPC_LIMIT - sold))).meter(Math.min(1, sold / NPC_LIMIT))));
        }

        @Override
        public boolean visible() {
            return (GameState.inSkyBlock || SbcMod.DEV) && npcSold() > 0 && npcSold() >= NPC_LIMIT * showFrom() / 100.0;
        }

        @Override
        protected Content live() {
            return of(npcSold());
        }

        @Override
        protected Content sample() {
            return of(142_000_000);
        }

        @Override
        protected List<Row> classicRows() {
            return List.of(new Row("NPC sold today", coins(npcSold()), 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("NPC sold today", coins(142_000_000), 0));
        }
    };

    // --- what feeds them ---

    private static final List<Card> CARDS = List.of(CHESTS_CARD, CORPSE_CARD, DRAGON_CARD, GHOST_CARD, NODE_CARD, SACRIFICE_CARD, NUCLEUS_CARD, HUNTING_CARD, INFERNO_CARD, TIMITE_CARD, STRAY_CARD, ORB_CARD, GIFT_CARD, TREVOR_CARD, DIANA_CARD, BEACH_CARD, MONOLITH_CARD);

    // an activity card is on screen so the general profit card steps aside
    static boolean anyShowing() {
        for (Card c : CARDS) if (c.visible() && c.active()) return true;
        return false;
    }

    static void onMessage(String message, String clean) {
        String island = GameState.island;
        if ("THE_END".equals(island) || SbcMod.DEV) {
            if (Features.on("dragon_profit")) dragonChat(message);
            if (Features.on("ender_node_profit")) enderNode(message);
            if (Features.on("sacrifice_profit")) sacrifice(message);
        }
        if ((inMist() || SbcMod.DEV) && Features.on("ghost_profit")) ghostChat(clean);
        if (("CRYSTAL_HOLLOWS".equals(island) || SbcMod.DEV) && Features.on("nucleus_profit")) nucleusChat(message);
        if (("HUB".equals(island) && yearOfThePig() || SbcMod.DEV) && Features.on("shiny_orb_profit")) orbChat(clean);
        if (("THE_FARMING_ISLANDS".equals(island) || SbcMod.DEV) && Features.on("trevor_tracker")) trevorChat(message, clean);
        if ((GameState.inSkyBlock || SbcMod.DEV) && Features.on("gift_profit")) giftChat(message);
        if ((GameState.inSkyBlock || SbcMod.DEV)) shardChat(clean);
        if (DOUBLE_HOOK.matcher(clean).find()) doubleHookAt = System.currentTimeMillis();
        if ("HUB".equals(island) || SbcMod.DEV) {
            dianaChat(message, clean);
            if (Features.on("beach_ball_tracker")) beachChat(clean);
        }
        if (("DWARVEN_MINES".equals(island) || SbcMod.DEV) && Features.on("dark_monolith_tracker")) monolithChat(clean);
        if ((GameState.inSkyBlock || SbcMod.DEV) && npcLine(clean)) io.muzy.sbc.areas.MiscExtras.npcWarning(npcSold());
        if ("PRIVATE_ISLAND".equals(island) && Features.on("inferno_profit") && Patterns.get("SH.minion.eyedrops.ranout").matcher(clean).matches()) INFERNO.cost(price("CAPSAICIN_EYEDROPS_NO_CHARGES"));
    }

    static void onGain(String id, int amount) {
        if (inMist() && Features.on("ghost_profit")) {
            ghostData();
            if (ghostSackDrops.contains(id)) GHOST.item(id, amount);
        }
        if ("THE_END".equals(GameState.island) && id.equals("MITE_GEL") && Features.on("ender_node_profit")) NODES.item(id, amount, nodePrice(id) * amount);
        if (Features.on("diana_profit") && io.muzy.sbc.events.diana.DianaApi.isDoingDiana() && dianaDrop(id)) DIANA.item(id, amount);
        if ("THE_RIFT".equals(GameState.island) && TIMITE_ITEMS.contains(id) && Features.on("timite_tracker")) TIMITE.count(id, amount);
    }

    static void onCoins(double diff, Trackers.Cause cause) {
        if (cause == Trackers.Cause.MOB_KILL && inMist() && diff >= 200 && diff <= 15_000 && Features.on("ghost_profit")) GHOST.coins(diff);
    }

    private static void tick(Minecraft mc) {
        if (!GameState.inSkyBlock || mc.player == null) return;
        if ("THE_END".equals(GameState.island) && Features.on("dragon_profit")) scanDragonLoot(mc);
        if (inMist() && Features.on("ghost_profit")) {
            readBestiary();
            ghostXp();
        }
        infernoTick(mc);
    }

    // what a tracker counted doesnt need to be in chat too: skyhanni PestProfitTracker, RareCropTracker,
    // SeaCreatureTracker + MythologicalCreatureTracker hide chat
    private static io.muzy.sbc.chat.ChatBus.Result hideChat(io.muzy.sbc.chat.ChatBus.Line line) {
        String clean = GameState.strip(line.plain());
        if (on("mythological_tracker", "myth.hide_chat", false) && "HUB".equals(GameState.island) && Patterns.get("SH.event.diana.mythological.burrows.generic-spawn").matcher(io.muzy.sbc.core.LegacyText.of(line.component())).find()) return io.muzy.sbc.chat.ChatBus.CANCEL;
        if (on("sea_creature_tracker", "sea.hide_chat", false) && io.muzy.sbc.nav.Fishing.isCatchLine(clean.trim())) return io.muzy.sbc.chat.ChatBus.CANCEL;
        if (Profit.hideGardenLine(io.muzy.sbc.core.LegacyText.of(line.component()), clean)) return io.muzy.sbc.chat.ChatBus.CANCEL;
        return io.muzy.sbc.chat.ChatBus.PASS;
    }

    public static void register() {
        Feature loot = Features.get("loot");
        if (loot == null) loot = Features.add(new Feature("loot", "Tracking", "Items gained", "", ""));
        loot.option(Option.bool("loot.card", "Show as a card", "On the HUD, with what the NPC and the market pay.", true))
                .option(Option.choice("loot.scope", "Totals of", "", "This session", "This session", "Today", "This week", "All time"));
        Profit.feature("dungeon_profit", "Dungeons", "Dungeon chest tracker", "What the reward chests you open hold at market prices, what they cost, and what one is worth. Kuudra's chests count too.", "SkyHanni: Instance Chest Profit");
        Profit.feature("corpse_profit", "Mining", "Corpse profit", "What corpses in the Glacite Mineshafts give at market prices, what the keys cost, keys saved by Resourceful.", "SkyHanni: Corpse Tracker")
                .option(Option.bool("corpse.only_mineshaft", "Only in a Mineshaft", "Otherwise also in the Glacite Tunnels.", false));
        Profit.feature("dragon_profit", "Combat", "Dragon profit", "What the dragons you fight in the End leave you at market prices, less the Summoning Eyes.", "SkyHanni: Dragon Profit Tracker")
                .option(Option.bool("dragon.count_leeched", "Count leeched dragons", "Dragons you placed no eyes in count towards the total.", true))
                .option(Option.bool("dragon.chat", "Profit of each dragon in chat", "One line, with the drops on hover.", true));
        Profit.feature("ghost_profit", "Combat", "Ghost profit", "Ghost drops, coins and Combat XP in The Mist, kills from the Bestiary tab widget, ghosts since your last Sorrow, longest combo.", "SkyHanni: Ghost Tracker");
        Profit.feature("ender_node_profit", "Combat", "Ender Node profit", "What Ender Nodes give you in The End, and how many you mined.", "SkyHanni: Ender Node Tracker")
                .option(Option.bool("ender_node.only_tool", "Only with a mining tool in hand", "A pickaxe, drill or gauntlet.", false));
        Profit.feature("sacrifice_profit", "Combat", "Sacrifice profit", "Dragon Essence and bonus loot from the Draconic Altar, per item sacrificed.", "SkyHanni: Draconic Sacrifice Tracker")
                .option(Option.bool("sacrifice.only_void_slate", "Only at the altar", "", true));
        Profit.feature("nucleus_profit", "Mining", "Crystal Nucleus profit", "What each Crystal Nucleus run gives at market prices, less the Jungle Key and the Professor's parts.", "SkyHanni: Crystal Nucleus Tracker")
                .option(Option.bool("nucleus.outside", "Anywhere in the Crystal Hollows", "", false))
                .option(Option.choice("nucleus.professor", "Sapphire crystal paid with", "What the run costs you at the Professor.", "Robot parts", "Robot parts", "Precursor Apparatus"))
                .option(Option.bool("nucleus.profit_per", "Profit of each run in chat", "One line, with the items on hover.", true))
                .option(Option.integer("nucleus.profit_minimum", "On hover from (coins)", "", 20_000, 0, 100_000_000));
        Profit.feature("hunting_profit", "Hunting", "Hunting profit", "The shards you catch at market prices, mobs caught and shards collected.", "SkyHanni: Hunting Profit Tracker")
                .option(Option.bool("hunting.always", "Always show", "Whatever you are holding.", false))
                .option(Option.bool("hunting.after_pickup", "Show after a catch", "For a few seconds.", true));
        Profit.feature("shard_tracker", "Hunting", "Shard tracker", "Shards you picked with the Track shard key: how many are in your Hunting Box against what you need, and what you gained of them.", "SkyHanni: Shard Tracker");
        Profit.feature("inferno_profit", "Minions", "Inferno Minion profit", "What you collect from Inferno Minions at market prices, less the fuel and eyedrops.", "SkyHanni: Inferno Minion Profit Tracker")
                .option(Option.bool("inferno.after_collection", "Show after collecting", "For a few seconds.", true));
        Profit.feature("timite_tracker", "Rift", "Timite tracker", "Timite, Youngite and Obsolite you collected at the Mountaintop, their worth in Motes and the time they gave.", "SkyHanni: Timite Tracker")
                .option(io.muzy.sbc.feature.Option.bool("timite_tracker.only_holding", "Only while holding a Timite tool", "A Timite pickaxe or the Time Gun.", false));
        Profit.feature("stray_tracker", "Events", "Stray rabbits", "Strays you caught in the Chocolate Factory by rarity, the production time they are worth, and the Golden ones by kind.", "SkyHanni: Stray Tracker");
        Profit.feature("shiny_orb_profit", "Events", "Shiny Orb profit", "What Shiny Orbs give in the Year of the Pig, less what the orbs cost.", "SkyHanni: Shiny Orb Tracker")
                .option(Option.bool("shiny_orb.holding", "Only holding an orb or rod", "", false));
        Profit.feature("gift_profit", "Events", "Gift profit", "What gifts give at market prices, by tier, with North Stars and XP. Say what you used with /sbc addusedgifts white 10.", "SkyHanni: Gift Profit Tracker")
                .option(Option.bool("gift.holding", "Only holding a gift", "Otherwise also for a while after a gift at this spot.", false));
        Profit.feature("trevor_tracker", "Farming Islands", "Trevor tracker", "Quests, pelts and pelts per hour, animals you killed and that died on their own, by kind.", "SkyHanni: Trevor Tracker");
        io.muzy.sbc.feature.Features.get("trevor_tracker").option(io.muzy.sbc.feature.Option.bool("trevor_tracker.between", "Also between quests", "Off: only while a quest runs, and in the Trapper's Den.", true));
        Profit.feature("sea_creature_tracker", "Fishing", "Sea creatures", "How many of each sea creature you caught, with its share.", "SkyHanni: Sea Creature Tracker")
                .option(Option.bool("sea.count_double", "Count Double Hooks twice", "", true))
                .option(Option.bool("sea.percent", "Show the share", "", true))
                .option(Option.bool("sea.hide_chat", "Hide the catch lines", "", false))
                .option(Option.bool("sea.live", "Show who is alive", "How many sea creatures are around you, and when the oldest despawns.", true));
        Profit.feature("diana_profit", "Events", "Diana profit", "What Griffin burrows give at market prices, and what a burrow is worth.", "SkyHanni: Diana Profit Tracker");
        Profit.feature("mythological_tracker", "Events", "Mythological mobs", "Which mythological mobs you dug up, with their share.", "SkyHanni: Mythological Creature Tracker")
                .option(Option.bool("myth.percent", "Show the share", "", true))
                .option(Option.bool("myth.hide_chat", "Hide the lines", "When you dig up a mob.", false))
                .option(Option.bool("myth.black_hole", "Black Hole title", "On a Cretan Bull, a Harpy or a Minotaur, whose shards need one.", false));
        Profit.feature("beach_ball_tracker", "Events", "Beach Ball tracker", "The Beach Balls you used and the Fishy Treats they earned.", "SkyHanni: Beach Ball Tracker")
                .option(Option.integer("beach.hide_after", "Hide after (minutes)", "Without using a ball.", 5, 1, 60));
        Profit.feature("dark_monolith_tracker", "Mining", "Dark Monolith tracker", "Coins and Mithril Powder from Dark Monoliths, and how many you looted.", "SkyHanni: Dark Monolith Tracker");
        Features.add(new Feature("npc_day_limit", "Misc", "NPC sell limit", "What you sold to NPCs today against the 500M limit, which starts over at midnight GMT. Shows once you sold a part of it.", "SkyHanni: NPC Day Limit Tracker")
                .option(Option.choice("npc.format", "Numbers", "", "Short", "Short", "Long"))
                .option(Option.integer("npc.show_from", "Show from (% of the limit)", "", 25, 0, 100)));
        HudLayout.add(SEA_CARD);
        HudLayout.add(MYTH_CARD);
        HudLayout.add(NPC_CARD);
        io.muzy.sbc.chat.ChatBus.register(io.muzy.sbc.chat.ChatBus.FILTER + 41, "tracker_hide_chat", null, ProfitCards::hideChat);
        for (Card c : CARDS) HudLayout.add(c);
        HudLayout.add(SHARD_CARD);
        HudLayout.add(LOOT_CARD);
        Trackers.onGain((id, n) -> {
            LOOT.noted(id);
            infernoGain(id, n);
        });
        Interactions.onSlotClick(ProfitCards::chestClick);
        MenuMarks.add("stray_tracker", Patterns.get("SH.misc.chocolatefactory.cf.inventory"), ProfitCards::strayMenu);
        MenuMarks.add("inferno_profit", Patterns.get("SH.minion.infernominiontitle"), ProfitCards::infernoMenu);
        Mineshaft.lootListener = ProfitCards::corpseLooted;
        io.muzy.sbc.core.Commands.describe("addusedgifts", "Say how many gifts you used: /sbc addusedgifts white 10");
        io.muzy.sbc.dungeon.SecretCompass.register();
        Safe.endTick("ProfitCards.tick", ProfitCards::tick);
        Safe.commands("ProfitCards.commands", (d, r) -> d.register(ClientCommands.literal("sbc").then(ClientCommands.literal("addusedgifts")
                .then(ClientCommands.argument("type", com.mojang.brigadier.arguments.StringArgumentType.word())
                        .executes(c -> addUsedGifts(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "type"), 1))
                        .then(ClientCommands.argument("amount", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
                                .executes(c -> addUsedGifts(com.mojang.brigadier.arguments.StringArgumentType.getString(c, "type"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(c, "amount"))))))));
    }
}
