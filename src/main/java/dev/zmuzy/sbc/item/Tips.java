// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.item;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.zmuzy.sbc.core.AuctionPrices;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.ItemInfo;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Worth;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

// extra tooltip lines on skyblock items: where it sells, what its worth, what it costs to make, whats on it
public final class Tips {
    private static final Map<String, String> COUNTERS = Map.ofEntries(
            Map.entry("farmed_cultivating", "Cultivating"), Map.entry("expertise_kills", "Expertise kills"), Map.entry("compact_blocks", "Compact blocks"),
            Map.entry("blocks_walked", "Blocks walked"), Map.entry("mob_kills", "Mob kills"), Map.entry("eman_kills", "Enderman kills"),
            Map.entry("champion_combat_xp", "Champion XP"), Map.entry("hecatomb_s_runs", "Hecatomb runs"), Map.entry("collected_coins", "Coins collected"),
            Map.entry("bow_kills", "Bow kills"), Map.entry("sword_kills", "Sword kills"), Map.entry("ethermerge", "Ethermerge"),
            Map.entry("blood_god_kills", "Blood God kills"), Map.entry("raider_kills", "Raider kills"), Map.entry("spider_kills", "Spider kills"),
            Map.entry("zombie_kills", "Zombie kills"), Map.entry("wood_singularity_count", "Wood singularity"), Map.entry("pickonimbus_durability", "Pickonimbus durability"));

    private Tips() {}

    private static CompoundTag data(ItemStack st) {
        var cd = st.get(DataComponents.CUSTOM_DATA);
        return cd == null ? new CompoundTag() : cd.copyTag();
    }

    private static void line(List<Component> out, String s) {
        out.add(Component.literal(s));
    }

    private static boolean on(String feature, String key) {
        return dev.zmuzy.sbc.item.market.Market.b(feature, key, true);
    }

    // skyhanni GeorgePriceTooltip: pet info from the item, -1 when he doesnt buy it
    private static double georgePrice(CompoundTag ea) {
        try {
            var info = com.google.gson.JsonParser.parseString(ea.getStringOr("petInfo", "{}")).getAsJsonObject();
            return dev.zmuzy.sbc.core.data.GeorgePrices.forPet(info.has("type") ? info.get("type").getAsString() : "",
                    info.has("tier") ? info.get("tier").getAsString() : "", info.has("exp") ? info.get("exp").getAsDouble() : 0);
        } catch (Exception e) {
            return -1;
        }
    }

    // from skyhanni DungeonQualityTooltip.getItemTierFloor
    static String tierFloor(int tier) {
        return switch (tier) {
            case 0 -> "E";
            case 1 -> "F1";
            case 2 -> "F2";
            case 3 -> "F3";
            case 4 -> "F4/M1";
            case 5 -> "F5/M2";
            case 6 -> "F6/M3";
            case 7 -> "F7/M4";
            case 8 -> "M5";
            case 9 -> "M6";
            case 10 -> "M7";
            default -> "Unknown";
        };
    }

    private static final Map<String, Double> CRAFT_COSTS = new java.util.concurrent.ConcurrentHashMap<>();
    private static long craftCostsAt;
    private static final int MAX_RECURSION_DEPTH = 15;

    // skyhanni CraftPriceTooltip.getItemCost: cost to make one, each ingredient at market price or crafted if
    // thats cheaper. -1 = no recipe
    static double itemCost(String id, int depth, boolean buyOrder) {
        List<ItemInfo.Recipe> rs = ItemInfo.recipes(id);
        if (rs.isEmpty()) return -1;
        ItemInfo.Recipe recipe = rs.getFirst();
        double cost = recipeCost(recipe, depth + 1, buyOrder);
        return cost < 0 ? -1 : cost / Math.max(1, recipe.resultCount());
    }

    // from skyhanni CraftPriceTooltip.getRecipeCost
    private static double recipeCost(ItemInfo.Recipe recipe, int depth, boolean buyOrder) {
        if (depth >= MAX_RECURSION_DEPTH) return -1;
        double total = 0;
        for (ItemInfo.Part p : recipe.parts()) {
            Double cached = CRAFT_COSTS.get(p.id());
            if (cached != null) {
                total += cached * p.count();
                continue;
            }
            double cost = 0;
            Prices.Product product = Prices.get(p.id());
            if (product != null) cost = buyOrder ? product.buy() : product.sell();
            else if (AuctionPrices.lowestBin(p.id()) > 0) cost = AuctionPrices.lowestBin(p.id());
            if (cost > 0) CRAFT_COSTS.put(p.id(), cost);
            double craft = itemCost(p.id(), depth + 1, buyOrder);
            if (craft > 0) {
                cost = cost > 0 ? Math.min(cost, craft) : craft;
                CRAFT_COSTS.put(p.id(), cost);
            }
            total += cost * p.count();
        }
        return total;
    }

    // craft cost line number, same answer till prices move
    private static double craftCost(String id, boolean buyOrder) {
        long now = System.currentTimeMillis();
        if (now - craftCostsAt > 60_000) {
            CRAFT_COSTS.clear();
            craftCostsAt = now;
        }
        return itemCost(id, 0, buyOrder);
    }

    // the bazaar product an item sells as. most items use their skyblock id
    // enchanted books are one product per enchant + level, and items inside bazaar menus often have no id at all,
    // just a name
    public static String bazaarId(ItemStack st) {
        String id = Gain.sbId(st);
        if (id.equals("ENCHANTED_BOOK")) {
            CompoundTag en = data(st).getCompoundOrEmpty("enchantments");
            if (en.size() == 1) {
                for (String k : en.keySet()) {
                    String b = "ENCHANTMENT_" + k.toUpperCase(Locale.ROOT) + "_" + en.getIntOr(k, 1);
                    if (Prices.get(b) != null) return b;
                }
            }
            return id;
        }
        if (!id.isEmpty() && Prices.get(id) != null) return id;
        String byName = Prices.idFromName(st.getHoverName().getString());
        if (!byName.isEmpty() && Prices.get(byName) != null) return byName;
        return id;
    }

    // prices, estimated value, craft profit
    public static void prices(ItemStack st, List<Component> lines) {
        String id = Gain.sbId(st);
        String bzId = bazaarId(st);
        if (id.isEmpty() && bzId.isEmpty()) return;
        int n = Math.max(1, st.getCount());
        boolean any = false;
        Prices.Product bz = on("tip_prices", "tip_prices.bazaar") ? Prices.get(bzId) : null;
        if (id.isEmpty() || id.equals("ENCHANTED_BOOK")) {
            if (bz != null) {
                line(lines, "");
                line(lines, "§6Bazaar §7buy §e" + Prices.coins(bz.buy() * n) + " §7· sell §e" + Prices.coins(bz.sell() * n) + (n > 1 ? " §8(" + n + "x)" : ""));
            } else if (on("tip_prices", "tip_prices.lowestBin")) {
                double bookBin = AuctionPrices.lowestBinOf(data(st));
                if (bookBin > 0) {
                    line(lines, "");
                    line(lines, "§6Lowest BIN §e" + Prices.coins(bookBin * n) + (n > 1 ? " §8(" + n + "x)" : ""));
                } else if (AuctionPrices.noneListed(data(st))) {
                    line(lines, "");
                    line(lines, "§6Lowest BIN §cNone on the AH");
                }
            }
            return;
        }
        if (bz != null) {
            line(lines, "");
            any = true;
            line(lines, "§6Bazaar §7buy §e" + Prices.coins(bz.buy() * n) + " §7· sell §e" + Prices.coins(bz.sell() * n) + (n > 1 ? " §8(" + n + "x)" : ""));
        }
        double bin = on("tip_prices", "tip_prices.lowestBin") ? AuctionPrices.lowestBinOf(data(st)) : 0;
        if (bin > 0 && Prices.get(bzId) == null) {
            if (!any) line(lines, "");
            any = true;
            line(lines, "§6Lowest BIN §e" + Prices.coins(bin * n) + (n > 1 ? " §8(" + n + "x)" : ""));
        } else if (bin <= 0 && Prices.get(bzId) == null && on("tip_prices", "tip_prices.lowestBin") && AuctionPrices.noneListed(data(st))) {
            if (!any) line(lines, "");
            any = true;
            line(lines, "§6Lowest BIN §cNone on the AH");
        }
        CompoundTag ea = data(st);
        double est = 0;
        try {
            est = Worth.item(ea, n);
        } catch (Throwable ignored) {
        }
        double base = AuctionPrices.value(id) * n;
        if (!on("tip_prices", "tip_prices.estimated")) {
            // no estimated value line
        } else if (est > 0 && (base <= 0 || est > base * 1.05 || id.equals("PET"))) {
            if (!any) line(lines, "");
            any = true;
            line(lines, "§6Estimated value §e" + Prices.coins(est) + (base > 0 ? " §8(base " + Prices.coins(base) + ")" : ""));
        } else if (bz == null && bin <= 0 && base > 0) {
            if (!any) line(lines, "");
            any = true;
            line(lines, "§6Value §e" + Prices.coins(base));
        }
        double npc = on("tip_prices", "tip_prices.npc") ? ItemRepo.npcPrice(id) : 0;
        if (npc > 0) {
            if (!any) line(lines, "");
            any = true;
            line(lines, "§6NPC sell §e" + Prices.coins(npc * n));
        }
        double george = id.equals("PET") && on("tip_prices", "tip_prices.george") ? georgePrice(ea) : -1;
        if (george >= 0) {
            if (!any) line(lines, "");
            any = true;
            line(lines, "§6George pays §e" + Prices.coins(george));
        }
        var craftOption = dev.zmuzy.sbc.feature.Features.get("tip_prices").option("tip_prices.craftCost");
        String craftChoice = craftOption == null ? "SELL_OFFER" : craftOption.choice();
        if (!craftChoice.equals("OFF")) {
            double cost = craftCost(id, craftChoice.equals("BUY_ORDER"));
            if (cost > 0) {
                if (!any) line(lines, "");
                double each = base > 0 ? base / n : 0;
                String profit = each > 0 ? (each - cost >= 0 ? " §a+" : " §c") + Prices.coins(each - cost) : "";
                line(lines, "§6Craft cost §e" + Prices.coins(cost * n) + (n > 1 ? " §8(" + n + "x)" : "") + profit);
            }
        }
    }

    // obtained date, dungeon quality, counters, essence for the next star
    public static void details(ItemStack st, List<Component> lines) {
        String id = Gain.sbId(st);
        if (id.isEmpty()) return;
        CompoundTag ea = data(st);
        boolean head = false;
        String ts = on("tip_details", "tip_details.obtained") ? dev.zmuzy.sbc.item.tooltip.TipExtras.obtained(ea) : "";
        if (!ts.isEmpty()) {
            line(lines, "");
            head = true;
            line(lines, "§7Obtained §f" + ts);
        }
        int boost = on("tip_details", "tip_details.quality") ? ea.getIntOr("baseStatBoostPercentage", -1) : -1;
        if (boost >= 0) {
            if (!head) line(lines, "");
            head = true;
            int tier = ea.getIntOr("item_tier", -1);
            line(lines, "§7Quality §" + (boost >= 50 ? "6" : boost >= 40 ? "a" : "f") + boost + "§7/50" + (tier >= 0 ? " §8· floor tier " + tier + " (" + tierFloor(tier) + ")" : ""));
        }
        for (var e : COUNTERS.entrySet()) {
            if (!ea.contains(e.getKey())) continue;
            long v = ea.getLongOr(e.getKey(), ea.getIntOr(e.getKey(), 0));
            if (v <= 0) continue;
            if (!head) line(lines, "");
            head = true;
            line(lines, "§7" + e.getValue() + " §b" + String.format(Locale.ROOT, "%,d", v));
        }
        JsonArray costs = ItemRepo.upgradeCosts(id);
        if (costs != null && on("tip_details", "tip_details.nextStar")) {
            String name = st.getHoverName().getString();
            int stars = (int) name.chars().filter(c -> c == '✪').count();
            if (stars < costs.size()) {
                StringBuilder sb = new StringBuilder();
                for (var pe : costs.get(stars).getAsJsonArray()) {
                    JsonObject p = pe.getAsJsonObject();
                    String type = p.has("type") ? p.get("type").getAsString() : "";
                    int amt = p.has("amount") ? p.get("amount").getAsInt() : 0;
                    String what = type.equals("ESSENCE") && p.has("essence_type") ? p.get("essence_type").getAsString() + " Essence"
                            : p.has("item_id") ? ItemRepo.name(p.get("item_id").getAsString()) : "";
                    if (what.isEmpty()) continue;
                    if (sb.length() > 0) sb.append("§7, ");
                    sb.append("§d").append(String.format(Locale.ROOT, "%,d", amt)).append(" §7").append(what);
                }
                if (sb.length() > 0) {
                    if (!head) line(lines, "");
                    line(lines, "§7Next star: " + sb);
                }
            }
        }
    }
}
