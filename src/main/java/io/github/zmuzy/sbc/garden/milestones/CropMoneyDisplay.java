// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, NotEnoughUpdates; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.milestones;

import io.github.zmuzy.sbc.item.enchants.EnchantParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// from skyhanni CropMoneyDisplay (calculateCropMoney / buildCropMoneyLine): coins/h from crop speed for every
// bazaar form (base, enchanted x160, double enchanted), sell offer + instant sell
// skyhanni takes forms + multipliers from the neu repo, here theyre just listed
public final class CropMoneyDisplay {
    private CropMoneyDisplay() {}

    // bazaar id -> {crop, crops per item} (NeuItems.getPrimitiveMultiplier)
    static final Map<String, Object[]> MULTIPLIERS = new LinkedHashMap<>();

    // bazaar id -> {crop, crops per item}, for the crop commands
    public static Map<String, Object[]> forms() {
        return MULTIPLIERS;
    }

    static {
        put("WHEAT", CropType.WHEAT, 1);
        put("ENCHANTED_WHEAT", CropType.WHEAT, 160);
        put("ENCHANTED_HAY_BLOCK", CropType.WHEAT, 1296);
        put("SEEDS", CropType.WHEAT, 1);
        put("ENCHANTED_SEEDS", CropType.WHEAT, 160);
        put("CARROT_ITEM", CropType.CARROT, 1);
        put("ENCHANTED_CARROT", CropType.CARROT, 160);
        put("ENCHANTED_GOLDEN_CARROT", CropType.CARROT, 20480);
        put("POTATO_ITEM", CropType.POTATO, 1);
        put("ENCHANTED_POTATO", CropType.POTATO, 160);
        put("ENCHANTED_BAKED_POTATO", CropType.POTATO, 25600);
        put("NETHER_STALK", CropType.NETHER_WART, 1);
        put("ENCHANTED_NETHER_STALK", CropType.NETHER_WART, 160);
        put("MUTANT_NETHER_STALK", CropType.NETHER_WART, 25600);
        put("PUMPKIN", CropType.PUMPKIN, 1);
        put("ENCHANTED_PUMPKIN", CropType.PUMPKIN, 160);
        put("POLISHED_PUMPKIN", CropType.PUMPKIN, 25600);
        put("MELON", CropType.MELON, 1);
        put("ENCHANTED_MELON", CropType.MELON, 160);
        put("ENCHANTED_MELON_BLOCK", CropType.MELON, 25600);
        put("INK_SACK:3", CropType.COCOA_BEANS, 1);
        put("ENCHANTED_COCOA", CropType.COCOA_BEANS, 160);
        put("SUGAR_CANE", CropType.SUGAR_CANE, 1);
        put("ENCHANTED_SUGAR", CropType.SUGAR_CANE, 160);
        put("ENCHANTED_SUGAR_CANE", CropType.SUGAR_CANE, 25600);
        put("CACTUS", CropType.CACTUS, 1);
        put("ENCHANTED_CACTUS_GREEN", CropType.CACTUS, 160);
        put("ENCHANTED_CACTUS", CropType.CACTUS, 25600);
        put("RED_MUSHROOM", CropType.MUSHROOM, 1);
        put("BROWN_MUSHROOM", CropType.MUSHROOM, 1);
        put("ENCHANTED_RED_MUSHROOM", CropType.MUSHROOM, 160);
        put("ENCHANTED_BROWN_MUSHROOM", CropType.MUSHROOM, 160);
    }

    private static void put(String id, CropType crop, int amount) {
        MULTIPLIERS.put(id, new Object[]{crop, amount});
    }

    // bazaar prices: id -> {instantBuy (sell offer), instantSell}, null if not on bazaar. swappable in tests
    public static Function<String, double[]> bazaar = id -> null;
    public static Function<String, Double> npcPrice = id -> 0.0;
    public static Function<String, String> itemName = id -> id;
    public static boolean compactPrice = true, bountiful = true, mergeSeeds = true;
    public static int showOnlyBest = 5;
    public static boolean showCurrent = true;
    // ExtraMoneyData.total: coins/h from the mooshroom cow + rare crops on top of every line
    public static double extraPerHour;

    public record CropMoneyData(CropType crop, String internalName, double speed, double speedPerHour, double cropsPerHour, double npcCoins, double sellOfferCoins, double instantSellCoins, double bountifulCoins) {
        List<Double> toPrices() {
            return List.of(sellOfferCoins + bountifulCoins, instantSellCoins + bountifulCoins, npcCoins + bountifulCoins);
        }
    }

    static boolean isSeeds(String id) {
        return id.equals("SEEDS") || id.equals("ENCHANTED_SEEDS");
    }

    // calculateCropMoney
    static CropMoneyData calculateCropMoney(String internalName, int amount, boolean toolHasBountiful) {
        CropType crop = (CropType) MULTIPLIERS.get(internalName)[0];
        if (amount < 10) return null;
        Integer s = GardenCropSpeed.getSpeed(crop);
        if (s == null) return null;
        double speed = s;
        boolean seeds = isSeeds(internalName);
        if (seeds) speed *= 1.5;
        double replenishReduction = crop.replenish || seeds ? crop.multiplier() * GardenCropSpeed.getRecentBPS() : 0.0;
        speed -= replenishReduction;
        double speedPerHour = speed * 60 * 60;
        double cropsPerHour = speedPerHour / amount;
        double[] bz = bazaar.apply(internalName);
        if (bz == null) return null;
        double npcCoins = npcPrice.apply(internalName) * cropsPerHour;
        double sellOffer = bz[0] * cropsPerHour, instantSell = bz[1] * cropsPerHour;
        double bountifulCoins = toolHasBountiful && bountiful ? speedPerHour * 0.2 : 0.0;
        return new CropMoneyData(crop, internalName, speed, speedPerHour, cropsPerHour, npcCoins, sellOffer, instantSell, bountifulCoins);
    }

    // buildDisplayBody: ranked lines (sell offer price) or the "farm crops" hint
    public static List<String> build(CropType currentCrop, boolean toolHasBountiful) {
        Map<String, CropMoneyData> moneyPerHour = new LinkedHashMap<>();
        for (var e : MULTIPLIERS.entrySet()) {
            CropMoneyData d = calculateCropMoney(e.getKey(), (Integer) e.getValue()[1], toolHasBountiful);
            if (d != null) moneyPerHour.put(e.getKey(), d);
        }
        List<String> out = new ArrayList<>();
        out.add("§7Money per Hour when selling: §7(§eSell Offer§7)");
        if (moneyPerHour.isEmpty()) {
            out.add("§cFarm crops to add them to this list!");
            return out;
        }
        // createDescendingCropList with mergeSeeds
        double bestSeeds = 0;
        for (String id : new String[]{"SEEDS", "ENCHANTED_SEEDS"}) {
            CropMoneyData d = moneyPerHour.get(id);
            if (d != null) bestSeeds = Math.max(bestSeeds, d.toPrices().get(0));
        }
        List<Map.Entry<String, Double>> list = new ArrayList<>();
        for (var e : moneyPerHour.entrySet()) {
            if (mergeSeeds && isSeeds(e.getKey())) continue;
            double price = e.getValue().toPrices().get(0) + extraPerHour;
            if (mergeSeeds && e.getValue().crop() == CropType.WHEAT) price += bestSeeds;
            list.add(Map.entry(e.getKey(), price));
        }
        list.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        int number = 0;
        for (var e : list) {
            number++;
            CropType crop = (CropType) MULTIPLIERS.get(e.getKey())[0];
            boolean isCurrent = crop == currentCrop;
            if (number > showOnlyBest && (!showCurrent || !isCurrent)) continue;
            String currentColor = isCurrent ? "§e" : "§7";
            // GardenNextJacobContest.isNextCrop
            if (io.github.zmuzy.sbc.garden.contest.NextContest.isNextCrop(crop)) currentColor += "§n";
            String formatted = compactPrice ? EnchantParser.shortFormat(e.getValue()) : String.format(java.util.Locale.ROOT, "%,d", e.getValue().longValue());
            out.add("§7#" + number + " " + currentColor + itemName.apply(e.getKey()) + "§7: §6" + formatted);
        }
        return out;
    }
}
