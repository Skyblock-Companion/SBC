// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.market;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Store;
import net.minecraft.network.chat.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;

// from skyhanni bazaar: BazaarLimitTracker (daily coin limit), BazaarBestSellMethod (selling by offer vs selling now)
// MaxPurseItems (how many your purse buys), BazaarCancelledBuyOrderClipboard, BazaarOrderTooltip,
// BazaarOpenPriceWebsite
public final class BazaarExtras {
    // repo constants Bazaar.json: dailyLimit + capOrdersAtIntLimit
    public static final double DAILY_LIMIT = 15_000_000_000.0;
    static final boolean CAP_ORDERS_AT_INT_LIMIT = true;

    static final Patterns.P CANCEL_AMOUNT = Patterns.p("SH.bazaar.cancelledorder.lastamount.colorless");
    static final Patterns.P CANCEL_TITLE = Patterns.p("SH.bazaar.cancelledorder.inventorytitle");
    static final Patterns.P TRADE_PARTNERS = Patterns.p("SH.bazaar.orders.tooltip.tradepartners.colorless");
    static final Patterns.P MAX_ORDER = Patterns.p("SH.inventory.maxpurse.order");
    static final Patterns.P MAX_INSTANT = Patterns.p("SH.inventory.maxpurse.instant");
    static final Patterns.P MAX_CREATE_ORDER = Patterns.p("SH.inventory.maxpurse.createorder");
    static final Patterns.P MAX_CREATE_INSTANT = Patterns.p("SH.inventory.maxpurse.createinstant");

    // skyhanni MaxPurseItems, read once per product page
    static Double buyOrderPrice, instantBuyPrice;
    // from skyhanni BazaarCancelledBuyOrderClipboard.latestAmount
    static Integer latestAmount;

    private BazaarExtras() {}

    // daily limit

    // skyhanni BazaarLimitTracker.checkDate: a new utc day starts at 0
    static void checkDate(LocalDate currentDay) {
        Store s = Store.I;
        LocalDate last = s.bazaarLimitDay == null || s.bazaarLimitDay.isEmpty() ? null : LocalDate.parse(s.bazaarLimitDay);
        if (last == null || currentDay.isAfter(last)) {
            s.bazaarCoinsTowardsLimit = 0;
            s.bazaarLimitDay = currentDay.toString();
            Store.markDirty();
        }
    }

    static void checkDate() {
        checkDate(LocalDate.now(ZoneOffset.UTC));
    }

    // skyhanni BazaarLimitTracker.onBazaarTransaction: true when this one hit the limit. flips dont count
    static boolean onTransaction(String type, double coins) {
        if (type.equals("Order Flipped!")) return false;
        double counted = CAP_ORDERS_AT_INT_LIMIT && coins >= Integer.MAX_VALUE ? Integer.MAX_VALUE : coins;
        Store.I.bazaarCoinsTowardsLimit += counted;
        Store.markDirty();
        return Store.I.bazaarCoinsTowardsLimit >= DAILY_LIMIT;
    }

    // best sell method

    // skyhanni BazaarBestSellMethod.updateDisplay: the diff over everything you hold of it, 0 if you hold none
    public static double sellDifference(double instantBuy, double instantSell, long having) {
        if (having <= 0) return 0;
        return (instantBuy - instantSell) * having;
    }

    // max purse

    static void resetPurse() {
        buyOrderPrice = null;
        instantBuyPrice = null;
    }

    private static double coins(String s) {
        return Double.parseDouble(s.replace(",", ""));
    }

    // skyhanni MaxPurseItems.getPrices: buy instantly comes first in slot order. +0.1 bc people use the gold nugget option
    static void readPrices(Map<Integer, BazaarApi.Item> items) {
        for (var e : new java.util.TreeMap<>(items).entrySet()) {
            String name = e.getValue().formattedName();
            if (MAX_CREATE_ORDER.matcher(name).matches()) {
                for (String l : e.getValue().lore()) {
                    Matcher m = MAX_ORDER.matcher(l);
                    if (m.matches()) {
                        buyOrderPrice = coins(m.group("coins")) + 0.1;
                        return;
                    }
                }
            }
            if (MAX_CREATE_INSTANT.matcher(name).matches()) {
                for (String l : e.getValue().lore()) {
                    Matcher m = MAX_INSTANT.matcher(l);
                    if (m.matches()) {
                        instantBuyPrice = coins(m.group("coins"));
                        break;
                    }
                }
            }
        }
    }

    public static Double buyOrderPrice() {
        return buyOrderPrice;
    }

    public static Double instantBuyPrice() {
        return instantBuyPrice;
    }

    public static int maxItems(long purse, Double price) {
        return price == null || price <= 0 ? 0 : (int) (purse / price);
    }

    // cancelled buy order

    // from skyhanni BazaarCancelledBuyOrderClipboard.onInventoryFullyOpened
    static void readCancel(String title, Map<Integer, BazaarApi.Item> items) {
        if (!CANCEL_TITLE.matcher(title).matches()) return;
        BazaarApi.Item stack = items.get(11);
        if (stack == null || !stack.cleanName().contains("Cancel Order")) return;
        for (String l : stack.cleanLore()) {
            Matcher m = CANCEL_AMOUNT.matcher(l);
            if (m.matches()) {
                try {
                    latestAmount = BazaarApi.formatInt(m.group("amount"));
                } catch (NumberFormatException ignored) {
                }
                return;
            }
        }
    }

    // replaces hypixels line after a cancelled buy order, the amount goes on the clipboard
    static String cancelledMessage(int amount, String itemName, String coinsText) {
        return "§b[SBC] §eBazaar buy order cancelled. Click to re-order. §e(§8" + String.format(java.util.Locale.ROOT, "%,d", amount) + "x §r" + itemName + "§e for §6" + coinsText + " coins§e)";
    }

    // order tooltips

    // skyhanni BazaarOrderTooltip.removeBlock: from the header line down to and including the blank line after it
    static void removeBlock(List<Component> lines, Predicate<String> isHeader) {
        int start = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (isHeader.test(GameState.strip(lines.get(i).getString()))) {
                start = i;
                break;
            }
        }
        if (start < 0) return;
        int end = start;
        while (end < lines.size() && !lines.get(end).getString().isBlank()) end++;
        lines.subList(start, Math.min(end + 1, lines.size())).clear();
    }

    // price website

    // from skyhanni BazaarOpenPriceWebsite.getSkyBlockBzName
    public static String priceWebsite(String internalName) {
        String name = internalName.contains(";") ? "ENCHANTMENT_" + internalName.replace(";", "_") : internalName;
        return "https://www.skyblock.bz/product/" + name;
    }
}
