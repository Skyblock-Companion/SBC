// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, SkyHelper; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item.value;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.zmuzy.sbc.core.AuctionPrices;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.item.enchants.EnchantParser;
import dev.zmuzy.sbc.item.enchants.EnchantsData;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;

// from skyhanni EstimatedItemValueCalculator + EstimatedItemValue.draw: base item + every modifier at market
// price, one lore line each, in skyhannis order
// prices from our sources (AuctionPrices.value: skyhelper / bz / lbin / npc), star + gem slot costs from the
// item repo cache
// reforge stones not done, no reforge data in the local repo
public final class EstimatedItemValue {
    // result: lines (skyhannis strings) + totals
    public record Result(List<String> lines, double totalPrice, double basePrice) {}

    public static int enchantmentsCap = 7, starMaterialCap = 3;
    public static boolean ignoreHelmetSkins, ignoreArmorDyes, ignoreRunes, exactPrice;

    // price source, swappable in tests
    public static ToDoubleFunction<String> price = id -> {
        double v = AuctionPrices.value(id);
        return v > 0 ? v : 0;
    };
    // from skyhanni isBazaarItem
    public static java.util.function.Predicate<String> isBazaarItem = id -> Prices.get(id) != null;
    public static Function<String, String> itemName = id -> ItemRepo.name(id);
    // repo lookups (ItemRepo), swappable in tests
    public static Function<String, JsonArray> upgradeCosts = id -> ItemRepo.upgradeCosts(id), gemSlots = id -> ItemRepo.gemSlots(id);

    private EstimatedItemValue() {}

    private static Double priceOrNull(String id) {
        double v = price.applyAsDouble(id);
        return v > 0 ? v : null;
    }

    private static double priceOf(String id) {
        Double v = priceOrNull(id);
        return v == null ? 0 : v;
    }

    public static String formatCoin(double v) {
        return "§6" + (exactPrice ? String.format(Locale.ROOT, "%,d", Math.round(v)) : EnchantParser.shortFormat(v));
    }

    static String formatCoinWithBrackets(double v) {
        return "§7(" + formatCoin(v) + "§7)";
    }

    static String formatProgress(String label, int have, int max, double price) {
        return "§7" + label + ": §e" + have + "§7/§e" + max + " " + formatCoinWithBrackets(price);
    }

    // skyhanni getPriceName / getNumberedName: " §8<amount>x §r<name> §7(§6price§7)"
    private static String priceName(String id, double amount, double price) {
        String name = itemName.apply(id);
        String amountText = amount == 1 ? "" : "§8" + (amount == Math.floor(amount) ? String.format(Locale.ROOT, "%,d", (long) amount) : String.valueOf(amount)) + "x §r";
        return " " + amountText + name + " " + formatCoinWithBrackets(price * amount);
    }

    // skyhanni getTotalAndNames: total + lines sorted by value, unknown prices last
    static double getTotalAndNames(Map<String, ? extends Number> items, List<String> names) {
        double totalPrice = 0;
        Map<String, Double> map = new LinkedHashMap<>();
        for (var e : items.entrySet()) {
            Double p = priceOrNull(e.getKey());
            double amount = e.getValue().doubleValue();
            if (p != null) {
                totalPrice += p * amount;
                map.put(priceName(e.getKey(), amount, p), p * amount);
            } else {
                String n = itemName.apply(e.getKey());
                map.put(" " + (amount == 1 ? "" : "§8" + (long) amount + "x §r") + n + " §cUnknown price!", Double.MAX_VALUE);
            }
        }
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(map.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        for (var e : sorted) names.add(e.getKey());
        return totalPrice;
    }

    // from skyhanni EstimatedItemValueCalculator.calculate
    public static Result calculate(CompoundTag ea, String internalName, boolean isDungeonItem, List<String> loreLines) {
        ItemValueData.load();
        List<String> list = new ArrayList<>();
        double basePrice = addBaseItem(ea, internalName, list);
        double total = internalName.equals("ENCHANTED_BOOK") ? 0 : basePrice;
        // once
        total += addRecombobulator(ea, list);
        total += addArtOfWar(ea, list);
        total += addArtOfPeace(ea, list);
        total += addEtherwarp(ea, list);
        total += addPowerScrolls(ea, list);
        total += addWoodSingularity(ea, list);
        total += addJalapenoBook(ea, list);
        total += addStatsBook(ea, list);
        total += addEnrichment(ea, list);
        total += addDivanPowderCoating(ea, list);
        total += addMithrilInfusion(ea, list);
        total += addFreeWill(ea, list);
        // counted
        total += addCrimsonPrestige(internalName, list);
        total += addStars(ea, internalName, isDungeonItem, list);
        total += addMasterStars(ea, isDungeonItem, list);
        total += addHotPotatoBooks(ea, list);
        total += counted(ea, list, ItemNbt.getWetBookCount(ea), "Wet Book", "WET_BOOK", 5);
        total += counted(ea, list, ItemNbt.getFarmingForDummiesCount(ea), "Farming for Dummies", "FARMING_FOR_DUMMIES", 5);
        total += counted(ea, list, ItemNbt.getOverclockerCount(ea), "Overclocker 3000", "OVERCLOCKER_3000", 10);
        total += addSilex(ea, internalName, list);
        total += counted(ea, list, ItemNbt.getTransmissionTunerCount(ea), "Transmission Tuners", "TRANSMISSION_TUNER", 4);
        total += counted(ea, list, ItemNbt.getManaDisintegrators(ea), "Mana Disintegrators", "MANA_DISINTEGRATOR", 10);
        total += counted(ea, list, ItemNbt.getPolarvoidBookCount(ea), "Polarvoid", "POLARVOID_BOOK", 5);
        total += counted(ea, list, ItemNbt.getBookwormBookCount(ea), "Bookworm's Favorite Book", "BOOKWORM_BOOK", 5);
        total += counted(ea, list, ItemNbt.getAppliedPocketSackInASack(ea), "Pocket Sack-in-a-Sack", "POCKET_SACK_IN_A_SACK", 3);
        // cosmetic
        total += addCosmetic(ItemNbt.getHelmetSkin(ea), list, "Skin", ignoreHelmetSkins);
        total += addCosmetic(ItemNbt.getArmorDye(ea), list, "Dye", ignoreArmorDyes);
        total += addRune(ea, internalName, list);
        // dynamic
        total += addList(ItemNbt.getAbilityScrolls(ea), "Ability Scrolls", list);
        total += addList(ItemNbt.readBoosters(ea), "Boosters", list);
        total += addList(ItemNbt.getDrillUpgrades(ea), "Drill upgrades", list);
        total += addList(ItemNbt.getRodParts(ea), "Rod upgrades", list);
        total += addGemstoneSlotUnlockCost(ea, internalName, loreLines, list);
        total += addGemstones(ea, list);
        total += addEnchantments(ea, internalName, list);
        return new Result(list, total, basePrice);
    }

    // skyhanni EstimatedItemValue.draw: null when nothing is applied (total == base)
    public static List<String> draw(CompoundTag ea, String internalName, boolean isDungeonItem, List<String> loreLines) {
        Result r = calculate(ea, internalName, isDungeonItem, loreLines);
        if (r.basePrice() == r.totalPrice()) return null;
        List<String> out = new ArrayList<>();
        out.add("§aEstimated Item Value:");
        out.addAll(r.lines());
        String number = exactPrice ? String.format(Locale.ROOT, "%,d", Math.round(r.totalPrice())) : EnchantParser.shortFormat(r.totalPrice());
        out.add("§aTotal: §6§l" + number + " coins");
        return out;
    }

    // once

    private static double formatHaving(List<String> list, String label, String id) {
        double p = priceOf(id);
        list.add("§7" + label + ": §a§l✔ " + formatCoinWithBrackets(p));
        return p;
    }

    private static double addRecombobulator(CompoundTag ea, List<String> list) {
        return ItemNbt.isRecombobulated(ea) ? formatHaving(list, "Recombobulated", "RECOMBOBULATOR_3000") : 0;
    }

    private static double addJalapenoBook(CompoundTag ea, List<String> list) {
        return ItemNbt.hasJalapenoBook(ea) ? formatHaving(list, "Jalapeno Book", "JALAPENO_BOOK") : 0;
    }

    private static double addWoodSingularity(CompoundTag ea, List<String> list) {
        return ItemNbt.hasWoodSingularity(ea) ? formatHaving(list, "Wood Singularity", "WOOD_SINGULARITY") : 0;
    }

    private static double addDivanPowderCoating(CompoundTag ea, List<String> list) {
        return ItemNbt.hasDivanPowderCoating(ea) ? formatHaving(list, "Divan Powder Coating", "DIVAN_POWDER_COATING") : 0;
    }

    private static double addMithrilInfusion(CompoundTag ea, List<String> list) {
        return ItemNbt.getMithrilInfusion(ea) ? formatHaving(list, "Mithril Infusion", "MITHRIL_INFUSION") : 0;
    }

    private static double addFreeWill(CompoundTag ea, List<String> list) {
        return ItemNbt.getFreeWill(ea) ? formatHaving(list, "Free Will", "FREE_WILL") : 0;
    }

    private static double addArtOfWar(CompoundTag ea, List<String> list) {
        return ItemNbt.hasArtOfWar(ea) ? formatHaving(list, "The Art of War", "THE_ART_OF_WAR") : 0;
    }

    private static double addStatsBook(CompoundTag ea, List<String> list) {
        return ItemNbt.hasBookOfStats(ea) ? formatHaving(list, "Book of Stats", "BOOK_OF_STATS") : 0;
    }

    private static double addArtOfPeace(CompoundTag ea, List<String> list) {
        return ItemNbt.hasArtOfPeace(ea) ? formatHaving(list, "The Art Of Peace", "THE_ART_OF_PEACE") : 0;
    }

    private static double addPowerScrolls(CompoundTag ea, List<String> list) {
        String id = ItemNbt.getPowerScroll(ea);
        return id == null ? 0 : formatHaving(list, GameState.strip(itemName.apply(id)), id);
    }

    private static double addEtherwarp(CompoundTag ea, List<String> list) {
        if (!ItemNbt.hasEtherwarp(ea)) return 0;
        double p = priceOf("ETHERWARP_CONDUIT") + priceOf("ETHERWARP_MERGER");
        list.add("§7Etherwarp: §a§l✔ " + formatCoinWithBrackets(p));
        return p;
    }

    private static double addEnrichment(CompoundTag ea, List<String> list) {
        String e = ItemNbt.getEnrichment(ea);
        if (e == null) return 0;
        String id = "TALISMAN_ENRICHMENT_" + e.toUpperCase(Locale.ROOT);
        double p = priceOf(id);
        list.add("§7Enrichment: " + itemName.apply(id) + " " + formatCoinWithBrackets(p));
        return p;
    }

    // counted

    private static double counted(CompoundTag ea, List<String> list, Integer count, String label, String id, int max) {
        if (count == null) return 0;
        double p = priceOf(id) * count;
        list.add(formatProgress(label, count, max, p));
        return p;
    }

    private static double addHotPotatoBooks(CompoundTag ea, List<String> list) {
        Integer count = ItemNbt.getHotPotatoCount(ea);
        if (count == null) return 0;
        int hpb = Math.min(10, count), fuming = Math.max(0, count - 10);
        double total = priceOf("HOT_POTATO_BOOK") * hpb;
        list.add(formatProgress("HPB's", hpb, 10, total));
        if (fuming > 0) {
            double f = priceOf("FUMING_POTATO_BOOK") * fuming;
            list.add(formatProgress("Fuming", fuming, 5, f));
            total += f;
        }
        return total;
    }

    private static double addSilex(CompoundTag ea, String internalName, List<String> list) {
        Integer tier = ItemNbt.getSilexCount(ea, internalName);
        if (tier == null) return 0;
        int maxTier = internalName.equals("STONK_PICKAXE") ? 4 : 5;
        double p = priceOf("SIL_EX") * tier;
        list.add(formatProgress("Silex", tier, maxTier, p));
        return p;
    }

    private static double addMasterStars(CompoundTag ea, boolean isDungeonItem, List<String> list) {
        Integer total = ItemNbt.getDungeonStarCount(ea, isDungeonItem);
        if (total == null) return 0;
        int masterStars = Math.min(total - 5, 5);
        if (masterStars < 1) return 0;
        String[] prefixes = {"FIRST", "SECOND", "THIRD", "FOURTH", "FIFTH"};
        double p = 0;
        for (int i = 0; i < prefixes.length; i++) if (masterStars >= i + 1) p += priceOf(prefixes[i] + "_MASTER_STAR");
        list.add(formatProgress("Master Stars", masterStars, 5, p));
        return p;
    }

    // from skyhanni KuudraApi.kuudraArmorPattern
    private static final java.util.regex.Pattern KUUDRA_ARMOR = java.util.regex.Pattern.compile("(?<tier>HOT|BURNING|FIERY|INFERNAL|)_?(?<type>AURORA|CRIMSON|TERROR|HOLLOW|FERVOR)_(?:HELMET|CHESTPLATE|LEGGINGS|BOOTS)");
    private static final List<String> KUUDRA_TIERS = List.of("", "HOT", "BURNING", "FIERY", "INFERNAL");

    public static Integer kuudraTier(String internalName) {
        var m = KUUDRA_ARMOR.matcher(internalName);
        if (!m.matches()) return null;
        int i = KUUDRA_TIERS.indexOf(m.group("tier")) + 1;
        return i == 0 ? null : i;
    }

    public static String removeKuudraTier(String internalName) {
        var m = KUUDRA_ARMOR.matcher(internalName);
        if (!m.matches() || m.group("tier").isEmpty()) return internalName;
        return internalName.substring(m.group("tier").length() + 1);
    }

    private static double addCrimsonPrestige(String internalName, List<String> list) {
        Integer tierIndex = kuudraTier(internalName);
        if (tierIndex == null || tierIndex <= 1) return 0;
        String armorTier = KUUDRA_TIERS.get(tierIndex - 1);
        Map<String, Integer> all = new LinkedHashMap<>();
        for (int index = 1; index < tierIndex; index++) {
            Map<String, Integer> cost = ItemValueData.crimsonPrestigeCosts.get(KUUDRA_TIERS.get(index));
            if (cost == null) return 0;
            for (var e : cost.entrySet()) all.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        List<String> names = new ArrayList<>();
        double total = getTotalAndNamesWithCoins(all, names);
        String pretty = armorTier.charAt(0) + armorTier.substring(1).toLowerCase(Locale.ROOT);
        list.add("§7Prestige Tier: " + pretty + " " + formatCoinWithBrackets(total));
        list.addAll(names);
        return total;
    }

    // getTotalAndNames where SKYBLOCK_COIN is worth 1 each
    private static double getTotalAndNamesWithCoins(Map<String, Integer> items, List<String> names) {
        double coins = 0;
        Map<String, Integer> rest = new LinkedHashMap<>();
        for (var e : items.entrySet()) {
            if (e.getKey().equals("SKYBLOCK_COIN")) coins += e.getValue();
            else rest.put(e.getKey(), e.getValue());
        }
        double total = getTotalAndNames(rest, names);
        if (coins > 0) {
            names.add(" §8" + String.format(Locale.ROOT, "%,d", (long) coins) + "x §r§6Coins " + formatCoinWithBrackets(coins));
            total += coins;
        }
        return total;
    }

    // skyhanni addStars: repo per star costs (essence, items, coins) for the stars the item has
    private static double addStars(CompoundTag ea, String internalName, boolean isDungeonItem, List<String> list) {
        Integer s = ItemNbt.getDungeonStarCount(ea, isDungeonItem);
        int totalStars = s != null ? s : (ItemNbt.getStarCount(ea) != null ? ItemNbt.getStarCount(ea) : 0);
        Integer kuudra = kuudraTier(internalName);
        if (kuudra != null) totalStars += (kuudra - 1) * 10;
        if (totalStars == 0) return 0;
        JsonArray costs = upgradeCosts.apply(kuudra != null ? removeKuudraTier(internalName) : internalName);
        if (costs == null || costs.isEmpty()) return 0;
        int maxStars = costs.size();
        int having = Math.min(totalStars, maxStars);
        Map<String, Integer> items = new LinkedHashMap<>();
        for (int i = 0; i < having; i++)
            for (JsonElement ce : costs.get(i).getAsJsonArray()) {
                JsonObject c = ce.getAsJsonObject();
                String type = c.has("type") ? c.get("type").getAsString() : "";
                int amount = c.has("amount") ? c.get("amount").getAsInt() : 1;
                switch (type) {
                    case "ESSENCE" -> items.merge("ESSENCE_" + c.get("essence_type").getAsString(), amount, Integer::sum);
                    case "ITEM" -> items.merge(c.get("item_id").getAsString(), amount, Integer::sum);
                    case "COINS" -> items.merge("SKYBLOCK_COIN", c.has("coins") ? c.get("coins").getAsInt() : 0, Integer::sum);
                    default -> {
                    }
                }
            }
        List<String> names = new ArrayList<>();
        double total = getTotalAndNamesWithCoins(items, names);
        list.add(formatProgress("Stars", having, maxStars, total));
        list.addAll(names.subList(0, Math.min(names.size(), starMaterialCap)));
        return total;
    }

    // cosmetic

    private static double addCosmetic(String id, List<String> list, String label, boolean ignore) {
        if (id == null) return 0;
        double p = priceOf(id);
        String name = itemName.apply(id);
        list.add("§7" + label + ": " + name + " " + (ignore ? "§7(§7" + GameState.strip(formatCoin(p)) + "§7)" : formatCoinWithBrackets(p)));
        return ignore ? 0 : p;
    }

    private static double addRune(CompoundTag ea, String internalName, List<String> list) {
        if (internalName.contains("_RUNE")) return 0;
        String rune = ItemNbt.getRune(ea);
        if (rune == null) return 0;
        // "<NAME>_RUNE;<tier>" -> bz id "<NAME>_RUNE_<tier>"
        return addCosmetic(rune.replace(';', '_'), list, "Rune", ignoreRunes);
    }

    // dynamic

    private static double addList(List<String> ids, String label, List<String> list) {
        if (ids == null || ids.isEmpty()) return 0;
        Map<String, Integer> items = new LinkedHashMap<>();
        for (String id : ids) items.merge(id, 1, Integer::sum);
        List<String> names = new ArrayList<>();
        double total = getTotalAndNames(items, names);
        if (!names.isEmpty()) {
            list.add("§7" + label + ": " + formatCoin(total));
            list.addAll(names);
        }
        return total;
    }

    private static double addGemstones(CompoundTag ea, List<String> list) {
        List<String> gems = ItemNbt.getGemstones(ea);
        if (gems == null || gems.isEmpty()) return 0;
        Map<String, Integer> items = new LinkedHashMap<>();
        for (String g : gems) items.merge(g, 1, Integer::sum);
        List<String> names = new ArrayList<>();
        double total = getTotalAndNames(items, names);
        if (!names.isEmpty()) {
            list.add("§7Gemstones Applied: " + formatCoin(total));
            list.addAll(names);
        }
        return total;
    }

    private static char slotColor(String type) {
        return switch (type) {
            case "JADE", "DEFENSIVE" -> 'a';
            case "AMBER" -> '6';
            case "TOPAZ" -> 'e';
            case "SAPPHIRE" -> 'b';
            case "AMETHYST", "MINING" -> '5';
            case "JASPER" -> 'd';
            case "RUBY" -> 'c';
            case "OPAL", "UNIVERSAL" -> 'f';
            case "ONYX" -> '8';
            case "AQUAMARINE" -> '3';
            case "CITRINE", "COMBAT" -> '4';
            case "PERIDOT" -> '2';
            default -> '7';
        };
    }

    // skyhanni addGemstoneSlotUnlockCost: repo slot costs for the slots in gems.unlocked_slots
    private static double addGemstoneSlotUnlockCost(CompoundTag ea, String internalName, List<String> loreLines, List<String> list) {
        List<String> unlocked = ItemNbt.readUnlockedSlots(ea);
        if (unlocked == null || unlocked.isEmpty()) return 0;
        JsonArray slots = gemSlots.apply(internalName);
        if (slots == null) {
            // skyhanni: items with legacy slots or "This item has unused Gemstones!" arent an error
            return 0;
        }
        Map<String, Integer> items = new LinkedHashMap<>();
        List<String> slotNames = new ArrayList<>();
        for (String key : unlocked) {
            int us = key.lastIndexOf('_');
            if (us < 0) continue;
            String type = key.substring(0, us);
            int idx;
            try {
                idx = Integer.parseInt(key.substring(us + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            int seen = 0;
            for (JsonElement se : slots) {
                JsonObject so = se.getAsJsonObject();
                if (!so.has("slot_type") || !so.get("slot_type").getAsString().equals(type)) continue;
                if (seen++ != idx) continue;
                if (so.has("costs")) for (JsonElement ce : so.getAsJsonArray("costs")) {
                    JsonObject c = ce.getAsJsonObject();
                    String ctype = c.has("type") ? c.get("type").getAsString() : "";
                    int amount = c.has("amount") ? c.get("amount").getAsInt() : 1;
                    if (ctype.equals("ITEM")) items.merge(c.get("item_id").getAsString(), amount, Integer::sum);
                    else if (ctype.equals("COINS")) items.merge("SKYBLOCK_COIN", c.has("coins") ? c.get("coins").getAsInt() : 0, Integer::sum);
                }
                String display = type.charAt(0) + type.substring(1).toLowerCase(Locale.ROOT) + " Slot" + (idx != 0 ? " " + (idx + 1) : "");
                slotNames.add("§" + slotColor(type) + display);
            }
        }
        if (slotNames.isEmpty()) return 0;
        List<String> names = new ArrayList<>();
        double total = getTotalAndNamesWithCoins(items, names);
        list.add("§7Gemstone Slot Unlock Cost: " + formatCoin(total));
        list.addAll(names);
        list.add(" §7Unlocked slots: " + String.join("§7, ", slotNames));
        return total;
    }

    // skyhanni fetchEnchantmentItems: which books (bz id ENCHANTMENT_<NAME>_<level>) and how many of each
    public static Map<String, Integer> fetchEnchantmentItems(Map<String, Integer> enchantments, String internalName) {
        ItemValueData.load();
        Map<String, Integer> items = new LinkedHashMap<>();
        for (var e : enchantments.entrySet()) {
            String rawName = e.getKey();
            int rawLevel = e.getValue();
            // efficiency 1-5 is cheap, 6-10 is handled by silex
            if (rawName.equals("efficiency")) continue;
            ItemValueData.AlwaysActive aa = ItemValueData.alwaysActiveEnchants.get(rawName);
            if (aa != null && aa.level() == rawLevel && aa.items().contains(internalName)) continue;
            List<ItemValueData.Endcap> endcap = ItemValueData.endcapEnchants.get(rawName);
            if (endcap != null && endcap.isEmpty()) endcap = null;
            int level = rawLevel;
            if (endcap != null) {
                int minRequired = endcap.stream().mapToInt(ItemValueData.Endcap::requiredLevel).min().orElse(rawLevel);
                if (rawLevel >= minRequired) level = minRequired;
            }
            int multiplier = 1;
            if (ItemValueData.onlyTierOnePrices.contains(rawName) && level >= 2 && level <= 5) {
                multiplier = 1 << (level - 1);
                level = 1;
            } else if (ItemValueData.onlyTierFivePrices.contains(rawName) && level >= 6 && level <= 10) {
                multiplier = 1 << (level - 5);
                if (multiplier > 1) level = 5;
            }
            if (internalName.startsWith("ENCHANTED_BOOK_BUNDLE_")) multiplier = ItemValueData.bookBundleAmount.getOrDefault(rawName, 5);
            if (EnchantsData.stacking().values().stream().anyMatch(s -> s.nbtName.equals(rawName))) level = 1;
            if (endcap != null) for (ItemValueData.Endcap ec : endcap) if (rawLevel >= ec.requiredLevel() + 1) items.put(ec.endcapItem(), 1);
            String bookId = "ENCHANTMENT_" + rawName.toUpperCase(Locale.ROOT) + "_" + level;
            if (isBazaarItem.test(bookId)) items.put(bookId, multiplier);
        }
        return items;
    }

    private static double addEnchantments(CompoundTag ea, String internalName, List<String> list) {
        Map<String, Integer> enchantments = ItemNbt.getHypixelEnchantments(ea);
        if (enchantments == null) return 0;
        Map<String, Integer> items = fetchEnchantmentItems(enchantments, internalName);
        List<String> names = new ArrayList<>();
        double total = getTotalAndNames(items, names);
        if (names.isEmpty()) return 0;
        list.add("§7Enchantments: " + formatCoin(total));
        for (int i = 0; i < names.size(); i++) {
            if (i == enchantmentsCap) {
                list.add(" §7§o" + (names.size() - enchantmentsCap) + " more enchantments..");
                break;
            }
            list.add(names.get(i));
        }
        return total;
    }

    // skyhanni addBaseItem: the item itself (kuudra armor by its base tier, craft cost when above npc price)
    private static double addBaseItem(CompoundTag ea, String internalName, List<String> list) {
        String id = removeKuudraTier(internalName);
        if (id.isEmpty()) return 0;
        double p = priceOf(id);
        String name = itemName.apply(id);
        if (id.startsWith("ENCHANTED_BOOK_BUNDLE_")) {
            list.add("§7Base item: " + name);
            return 0;
        }
        list.add("§7Base item: " + name + " " + formatCoinWithBrackets(p));
        return p;
    }
}
