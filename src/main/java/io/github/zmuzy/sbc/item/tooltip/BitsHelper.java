// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item.tooltip;

import io.github.zmuzy.sbc.core.AuctionPrices;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.item.Tips;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyblocker BitsHelper: in the community shop, coins per bit on each tooltip
// best of what sells well in green, best overall in yellow. lists are skyblockers, dated 14.08.2024
public final class BitsHelper {
    public static final String ID = "bits_helper";
    private static final Pattern BITS_PATTERN = Pattern.compile("Cost (?<amount>[\\d,]+) Bits");
    private static final Pattern CATEGORY_PATTERN = Pattern.compile("Click to browse!");
    private static final Pattern TITLE_PATTERN = Pattern.compile(".*(?:Community Shop|Bits Shop).*");
    private static final Set<String> GOOD_SELLING_ITEMS = Set.of("KAT_FLOWER", "KAT_BOUQUET", "HEAT_CORE", "ULTIMATE_CARROT_CANDY_UPGRADE", "TALISMAN_ENRICHMENT_SWAPPER", "GOD_POTION_2", "KISMET_FEATHER", "MATRIARCH_PARFUM", "AUTOPET_RULES_2");
    private static final Map<String, Map<String, Integer>> CATEGORIES = Map.of(
            "Kat Items", Map.of("KAT_FLOWER", 500, "KAT_BOUQUET", 2500),
            "Upgrade Components", Map.of("HEAT_CORE", 3000, "HYPER_CATALYST_UPGRADE", 300, "ULTIMATE_CARROT_CANDY_UPGRADE", 8000, "COLOSSAL_EXP_BOTTLE_UPGRADE", 1200, "JUMBO_BACKPACK_UPGRADE", 4000, "MINION_STORAGE_EXPANDER", 1500),
            "Sacks", Map.of("POCKET_SACK_IN_A_SACK", 8000, "LARGE_DUNGEON_SACK", 14000, "RUNE_SACK", 14000, "FLOWER_SACK", 14000, "DWARVEN_MINES_SACK", 14000, "CRYSTAL_HOLLOWS_SACK", 14000),
            "Abiphone Supershop", Map.of("TRIO_CONTACTS_ADDON", 6450, "ABICASE_SUMSUNG_1", 15000, "ABICASE_SUMSUNG_2", 25000, "ABICASE_REZAR", 26000, "ABICASE_BLUE_AQUA", 17000, "ABICASE_BLUE_BLUE", 17000, "ABICASE_BLUE_GREEN", 17000, "ABICASE_BLUE_RED", 17000, "ABICASE_BLUE_YELLOW", 17000),
            "Dyes", Map.of("DYE_PURE_WHITE", 250000, "DYE_PURE_BLACK", 250000),
            "Stacking Enchants", Map.of("ENCHANTMENT_HECATOMB_1", 6000, "ENCHANTMENT_EXPERTISE_1", 4000, "ENCHANTMENT_COMPACT_1", 4000, "ENCHANTMENT_CULTIVATING_1", 4000, "ENCHANTMENT_CHAMPION_1", 4000, "ENCHANTMENT_TOXOPHILITE_1", 4000),
            "Enrichments", Map.ofEntries(Map.entry("TALISMAN_ENRICHMENT_SWAPPER", 200), Map.entry("TALISMAN_ENRICHMENT_WALK_SPEED", 5000), Map.entry("TALISMAN_ENRICHMENT_INTELLIGENCE", 5000), Map.entry("TALISMAN_ENRICHMENT_CRITICAL_DAMAGE", 5000),
                    Map.entry("TALISMAN_ENRICHMENT_CRITICAL_CHANCE", 5000), Map.entry("TALISMAN_ENRICHMENT_STRENGTH", 5000), Map.entry("TALISMAN_ENRICHMENT_DEFENSE", 5000), Map.entry("TALISMAN_ENRICHMENT_HEALTH", 5000),
                    Map.entry("TALISMAN_ENRICHMENT_MAGIC_FIND", 5000), Map.entry("TALISMAN_ENRICHMENT_FEROCITY", 5000), Map.entry("TALISMAN_ENRICHMENT_SEA_CREATURE_CHANCE", 5000), Map.entry("TALISMAN_ENRICHMENT_ATTACK_SPEED", 5000)));
    private static final int GREEN = 0x8055FF55, YELLOW = 0x80FFFF55;

    // skyblocker ItemUtils.getItemPrice: bz sell price, else lowest bin. swappable in tests
    static ToDoubleFunction<String> price = id -> {
        Prices.Product p = Prices.get(id);
        return p != null ? p.sell() : AuctionPrices.lowestBin(id);
    };

    // one shop thing, the way this class needs it
    record Item(String name, String id, int count, String lore) {}

    record Pick(String id, long coinsPerBit) {}

    static final Map<String, Pick> CATEGORY_OUTPUT = new HashMap<>();
    static int bestSlotIndexSelling = -1, bestSlotIndexAll = -1;
    private static int lastHash;

    private BitsHelper() {}

    // skyblocker calculateBestInCategory: fills in the best overall + the best that sells well ("GREEN")
    private static Pick bestInCategory(String catName) {
        long bestSelling = 0, bestAll = 0;
        CATEGORY_OUTPUT.put(catName, new Pick("", 0));
        CATEGORY_OUTPUT.put(catName + "GREEN", new Pick("", 0));
        for (var e : processCategory(catName).entrySet()) {
            String id = e.getKey();
            long coinsPerBit = e.getValue();
            if (GOOD_SELLING_ITEMS.contains(id) && coinsPerBit > bestSelling) {
                CATEGORY_OUTPUT.put(catName + "GREEN", new Pick(id, coinsPerBit));
                bestSelling = coinsPerBit;
            }
            if (coinsPerBit > bestAll) {
                CATEGORY_OUTPUT.put(catName, new Pick(id, coinsPerBit));
                bestAll = coinsPerBit;
            }
        }
        return CATEGORY_OUTPUT.get(catName);
    }

    // from skyblocker processCategory
    private static Map<String, Long> processCategory(String categoryName) {
        Map<String, Long> results = new HashMap<>();
        Map<String, Integer> category = CATEGORIES.get(categoryName);
        if (category != null) {
            for (var e : category.entrySet()) results.put(e.getKey(), Math.round(price.applyAsDouble(e.getKey()) / e.getValue()));
        } else if (categoryName.contains("Fuel Blocks")) {
            double itemCost = price.applyAsDouble("INFERNO_FUEL_BLOCK");
            results.put("INFERNO_FUEL_BLOCK", (long) Math.max(itemCost / 75, itemCost * 64 / 3600));
        }
        return results;
    }

    // from skyblocker calculateBestItems
    static void calculate(Map<Integer, Item> slots) {
        long bestSelling = 0, bestAll = 0;
        bestSlotIndexSelling = -1;
        bestSlotIndexAll = -1;
        // categories first
        for (var e : slots.entrySet()) {
            if (!CATEGORY_PATTERN.matcher(e.getValue().lore()).find()) continue;
            Pick p = bestInCategory(e.getValue().name());
            if (GOOD_SELLING_ITEMS.contains(p.id()) && p.coinsPerBit() > bestSelling) {
                bestSelling = p.coinsPerBit();
                bestSlotIndexSelling = e.getKey();
            }
            if (p.coinsPerBit() > bestAll) {
                bestAll = p.coinsPerBit();
                bestSlotIndexAll = e.getKey();
            }
        }
        for (var e : slots.entrySet()) {
            Item it = e.getValue();
            Matcher bits = BITS_PATTERN.matcher(it.lore());
            if (!bits.find()) continue;
            long bitsCost = Long.parseLong(bits.group("amount").replace(",", ""));
            double itemCost = price.applyAsDouble(it.id()) * it.count();
            if (itemCost == 0 || bitsCost == 0) continue;
            long coinsPerBit = Math.round(itemCost / bitsCost);
            if (GOOD_SELLING_ITEMS.contains(it.id()) && coinsPerBit > bestSelling) {
                bestSelling = coinsPerBit;
                bestSlotIndexSelling = e.getKey();
            }
            if (coinsPerBit > bestAll) {
                bestAll = coinsPerBit;
                bestSlotIndexAll = e.getKey();
            }
        }
    }

    // skyblocker addToTooltip text, empty when theres nothing to say
    static List<String> lines(String loreText, String name, String id, int count, int slot) {
        long coinsPerBit;
        String itemId = "";
        boolean isGreen = false;
        if (!CATEGORY_PATTERN.matcher(loreText).find()) {
            Matcher bits = BITS_PATTERN.matcher(loreText);
            if (!bits.find()) return List.of();
            long bitsCost = Long.parseLong(bits.group("amount").replace(",", ""));
            double itemCost = price.applyAsDouble(id) * count;
            if (itemCost == 0) return List.of();
            coinsPerBit = Math.round(itemCost / bitsCost);
        } else {
            isGreen = bestSlotIndexSelling != -1 && bestSlotIndexSelling == slot;
            Pick pick = CATEGORY_OUTPUT.get(isGreen ? name + "GREEN" : name);
            if (pick == null) return List.of();
            itemId = pick.id();
            coinsPerBit = pick.coinsPerBit();
        }
        String cost = "§bBits Cost: §3" + String.format(java.util.Locale.ROOT, "%,d", coinsPerBit) + " Coins per bit";
        if (itemId.isEmpty()) return List.of(cost);
        String from = GameState.strip(ItemRepo.name(itemId));
        return List.of(cost, (isGreen ? "§a" : "§3") + "From " + (from == null || from.isBlank() ? itemId : from));
    }

    private static boolean inShop(String title) {
        return TITLE_PATTERN.matcher(GameState.strip(title)).matches();
    }

    // from Markets tick when a menu changed
    public static void onMenu(String title, net.minecraft.world.inventory.ChestMenu menu, int n) {
        bestSlotIndexSelling = -1;
        bestSlotIndexAll = -1;
        if (!Features.on(ID) || !inShop(title)) return;
        Map<Integer, Item> slots = new HashMap<>();
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st.isEmpty()) continue;
            slots.put(i, new Item(GameState.strip(st.getHoverName().getString()), Tips.bazaarId(st), st.getCount(), String.join(" ", cleanLore(st))));
        }
        calculate(slots);
    }

    private static List<String> cleanLore(ItemStack st) {
        return LegacyText.lore(st).stream().map(GameState::strip).toList();
    }

    public static void slot(GuiGraphicsExtractor g, Slot slot, int idx) {
        if (!Features.on(ID)) return;
        Integer color = idx == bestSlotIndexSelling ? Integer.valueOf(GREEN) : idx == bestSlotIndexAll ? Integer.valueOf(YELLOW) : null;
        if (color != null) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
    }

    private static void tooltip(ItemStack st, List<Component> lines) {
        var mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || !inShop(cs.getTitle().getString()) || !(cs instanceof io.github.zmuzy.sbc.mc.mixin.ContainerAccess acc)) return;
        Slot focused = acc.sbc$hovered();
        if (focused == null || focused.getItem() != st) return;
        StringBuilder lore = new StringBuilder();
        for (Component c : lines) lore.append(lore.isEmpty() ? "" : " ").append(GameState.strip(c.getString()));
        for (String l : lines(lore.toString(), GameState.strip(st.getHoverName().getString()), Tips.bazaarId(st), st.getCount(), focused.getContainerSlot())) lines.add(Component.literal(l));
    }

    public static void register() {
        Features.add(new Feature(ID, "Menus", "Bits helper", "In the Community Shop: coins per bit on the tooltip, the best of what sells well in green and the best overall in yellow.", "Skyblocker: bits helper")
                .when(() -> GameState.inSkyBlock || io.github.zmuzy.sbc.SbcMod.DEV).tooltip(BitsHelper::tooltip));
    }
}
