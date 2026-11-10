// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.visitor;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.garden.GardenItems;
import io.github.zmuzy.sbc.garden.milestones.CropType;
import io.github.zmuzy.sbc.garden.milestones.GardenCropSpeed;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

// what an Accept Offer button says, from skyhanni VisitorTooltipParser, GardenVisitorTooltip,
// VisitorPriceCalculator: what the visitor wants, pays, and per copper
// needs no game so tests run it on lines
public final class VisitorOffer {
    // GardenVisitorTooltip.readItemLine
    public record Item(String id, String name, int amount) {}

    public final Map<String, Integer> shopping = new LinkedHashMap<>();
    // shopping list item names with their colour
    public final Map<String, String> names = new LinkedHashMap<>();
    public final List<Item> rewards = new ArrayList<>();
    public final List<VisitorReward> rareRewards = new ArrayList<>();
    public double totalPrice, fullPrice;
    public Integer copper, pricePerCopper, gardenExp;
    public Double totalReward;
    public boolean ready;

    private VisitorOffer() {}

    private static final String CHARMED = " §d❤";

    private static boolean notAnItem(String colouredName) {
        return Patterns.get("SH.garden.visitor.compact.disregardrewardname").matcher(GameState.strip(colouredName).trim()).matches();
    }

    // GardenVisitorTooltip.readItemLine
    static Item readItemLine(String formattedLine, boolean readingShoppingList) {
        String line = !readingShoppingList && formattedLine.endsWith(CHARMED) ? formattedLine.substring(0, formattedLine.length() - CHARMED.length()) : formattedLine;
        GardenItems.Amount read = GardenItems.readItemAmount(line);
        if (read == null || notAnItem(read.name())) return null;
        String id = GardenItems.idOf(read.name());
        // ignore custom items like copper
        if (id == null || id.startsWith("SKYBLOCK_")) return null;
        return new Item(id, read.name(), read.amount());
    }

    // VisitorPriceCalculator.getEstimatedCopperValue: green thumb I gives 1500 copper
    public static double copperValue() {
        return GardenItems.price("ENCHANTMENT_GREEN_THUMB_1") / 1500;
    }

    // readVisitorOffer, first pass over the lore
    public static VisitorOffer read(List<String> coloredLore) {
        VisitorOffer o = new VisitorOffer();
        boolean shoppingList = true;
        // the patterns want the colors side by side, hypixel puts resets between them
        List<String> lore = new java.util.ArrayList<>(coloredLore.size());
        for (String l : coloredLore) lore.add(l.replace("§r", ""));
        for (String line : lore) {
            if (line.contains("Rewards")) shoppingList = false;
            if (line.equals("§eClick to give!")) o.ready = true;
            Matcher copper = Patterns.get("SH.garden.visitor.tooltip.copper").matcher(line);
            if (copper.matches()) {
                long c = GameState.parseShort(GameState.strip(copper.group("amount")));
                if (c >= 0) o.copper = (int) c;
            }
            Matcher exp = Patterns.get("SH.garden.visitor.tooltip.gardenexperience").matcher(line);
            if (exp.matches()) {
                long e = GameState.parseShort(GameState.strip(exp.group("amount")));
                if (e >= 0) o.gardenExp = (int) e;
            }
            if (!shoppingList) {
                GardenItems.Amount named = GardenItems.readItemAmount(line.endsWith(CHARMED) ? line.substring(0, line.length() - CHARMED.length()) : line);
                VisitorReward rare = named == null ? null : VisitorReward.byName(named.name());
                if (rare != null) o.rareRewards.add(rare);
            }
            Item item = readItemLine(line, shoppingList);
            if (item == null) continue;
            double price = GardenItems.price(item.id()) * item.amount();
            if (shoppingList) {
                o.shopping.put(item.id(), item.amount());
                o.names.put(item.id(), item.name());
                o.totalPrice += price;
                o.fullPrice += price;
            } else {
                o.rewards.add(item);
                o.totalPrice -= price;
            }
        }
        if (o.totalPrice < 0) o.totalPrice = 0;
        if (o.copper != null) {
            o.pricePerCopper = o.copper <= 0 ? 0 : (int) (o.totalPrice / o.copper);
            o.totalReward = o.copper * copperValue();
        }
        return o;
    }

    // plain crop an item is made of + how many, or null
    private static CropType cropOf(String id) {
        return CropType.getByNameOrNull(GameState.strip(ItemRepo.name(GardenItems.primitive(id).id())));
    }

    // calculateFarmingTime in ms, -1 without a crop or speed data
    public static long farmingTime(String id, int amount) {
        CropType crop = cropOf(id);
        if (crop == null) return -1;
        Integer speed = GardenCropSpeed.getSpeed(crop);
        if (speed == null || speed <= 0) return -1;
        return GardenItems.primitive(id).amount() * (long) amount / speed * 1000;
    }

    // settings of the second pass
    public record Show(boolean price, boolean exact, boolean copperPrice, boolean copperTime, boolean expPrice) {}

    // readToolTip's second pass: lines with their cost, a crop line under each farmed item
    public List<String> enrich(List<String> lines, Show show) {
        List<String> out = new ArrayList<>(lines.size() + 4);
        long farmingTime = 0;
        boolean shoppingList = true;
        for (String line : lines) {
            String text = line;
            if (show.expPrice() && Patterns.get("SH.garden.visitor.tooltip.gardenexperience").matcher(line).matches() && gardenExp != null && gardenExp > 0)
                text = line + " §7(paying §6" + Prices.coins((int) (totalPrice / gardenExp)) + " §7per)";
            if (Patterns.get("SH.garden.visitor.tooltip.copper").matcher(line).matches() && copper != null) {
                if (show.copperPrice()) text += " §7(paying §6" + Prices.coins(pricePerCopper) + " §7per)";
                if (show.copperTime())
                    text += farmingTime != 0 && copper > 0 ? " §7(paying §b" + GardenItems.time(farmingTime / copper) + " §7per)" : " §7(§cno speed data!§7)";
            }
            if (line.contains("Rewards")) shoppingList = false;
            Item item = readItemLine(line, shoppingList);
            if (item != null) {
                int amount = shoppingList ? shopping.getOrDefault(item.id(), item.amount()) : item.amount();
                if (show.price()) text += " §7(§6" + Prices.coins(GardenItems.price(item.id()) * amount) + "§7)";
            }
            out.add(text);
            if (item == null || !shoppingList || !show.exact()) continue;
            int amount = shopping.getOrDefault(item.id(), item.amount());
            long time = farmingTime(item.id(), amount);
            if (time < 0) continue;
            farmingTime += time;
            CropType crop = cropOf(item.id());
            long crops = GardenItems.primitive(item.id()).amount() * (long) amount;
            out.add("§7- §e" + String.format(java.util.Locale.ROOT, "%,d", crops) + "§7x " + crop.cropName + " (in §b" + GardenItems.time(time) + "§7)");
        }
        return out;
    }
}
