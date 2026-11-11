// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.market;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyblocker BazaarHelper (order icon: about to expire, expired, partly filled, filled),
// BazaarOrderTracker (your orders marked among a products top orders)
// and ReorderHelper (ctrl on cancel order copies the unfilled amount). works on clean lore, Market feeds it
// from the open menu
public final class BazaarOrders {
    private static final Pattern FILLED_PATTERN = Pattern.compile("Filled: \\S+ \\(?([\\d.]+)%\\)?!?");
    private static final int RED = 0xFFE60B1E, YELLOW = 0xFFE6BA0B, GREEN = 0xFF1EE60B;
    private static final Pattern ORDER_AMOUNT_PATTERN = Pattern.compile("(?:Order|Offer) amount: ([0-9,]+)x");
    private static final Pattern UNIT_PRICE_PATTERN = Pattern.compile("Price per unit: ([0-9,.]+) coins");
    private static final Pattern ORDER_PATTERN = Pattern.compile("- ([0-9,.]+) coins? each \\| ([0-9,]+)x (in|from) ([0-9,]+) (order|offer)s?");
    private static final Pattern BUY_PATTERN = Pattern.compile("([\\d,]+)x missing items\\.");
    private static final Pattern SELL_PATTERN = Pattern.compile("([\\d,]+)x items\\.");
    static final Pattern ORDER_MENUS = Pattern.compile("(?:Co-op|Your) Bazaar Orders");
    static final Pattern PRODUCT_MENUS = Pattern.compile(".* ➜ .*");
    static final Pattern ORDER_OPTIONS = Pattern.compile("^Order options");

    // what goes in the slots top left corner
    public record Icon(String text, int color) {}

    record Order(String skyblockId, double unitPrice, int amount, boolean sell) {}

    private static final Map<Integer, Order> ORDERS = new HashMap<>();

    private BazaarOrders() {}

    private static Matcher firstMatch(List<String> lore, Pattern p) {
        for (String l : lore) {
            Matcher m = p.matcher(l);
            if (m.matches()) return m;
        }
        return null;
    }

    // skyblocker BazaarHelper.getText: glass rows + columns skipped, only a filled order with something to claim
    // shows the fill
    public static Icon icon(int slotId, int containerSize, List<String> cleanLore) {
        if (slotId < 10 || slotId > containerSize - 11) return null;
        int column = slotId % 9;
        if (column == 0 || column == 8) return null;
        Matcher filled = firstMatch(cleanLore, FILLED_PATTERN);
        if (filled != null && !cleanLore.isEmpty() && cleanLore.getLast().equals("Click to claim!")) {
            int percent = toInt(filled.group(1));
            return percent < 100 ? new Icon("§l%", YELLOW) : new Icon("§l✅", GREEN);
        }
        for (String l : cleanLore) if (l.equals("Expired!")) return new Icon("§l⏰", RED);
        for (String l : cleanLore) if (l.startsWith("Expires in")) return new Icon("§l⏰", YELLOW);
        return null;
    }

    // skyblocker NumberUtils.toInt: "37.5" isnt an int so it reads as 0, still below 100 so whatever
    private static int toInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static void clearOrders() {
        ORDERS.clear();
    }

    // skyblocker BazaarOrderTracker.processOrder: first row of orders = the sell offers
    static void processOrder(int slotId, String skyblockId, List<String> cleanLore) {
        Matcher price = firstMatch(cleanLore, UNIT_PRICE_PATTERN), amount = firstMatch(cleanLore, ORDER_AMOUNT_PATTERN);
        if (price == null || amount == null) return;
        ORDERS.put(slotId, new Order(skyblockId, Double.parseDouble(price.group(1).replace(",", "")), Integer.parseInt(amount.group(1).replace(",", "")), slotId < 18));
    }

    // skyblocker BazaarOrderTracker.addOrderMarker: under each price line of the tooltip, how much of it is yours
    static void addOrderMarker(List<Component> lines, boolean sell, String skyblockId) {
        List<Order> yourOrders = new ArrayList<>();
        for (Order o : ORDERS.values()) if (o.sell() == sell && o.skyblockId().equals(skyblockId)) yourOrders.add(o);
        yourOrders.sort(Comparator.comparingDouble(Order::unitPrice));
        if (yourOrders.isEmpty()) return;
        if (!sell) yourOrders = yourOrders.reversed();
        for (int i = 0, index = 0; i < lines.size() && index < yourOrders.size(); i++) {
            Matcher matcher = ORDER_PATTERN.matcher(lines.get(i).getString());
            if (!matcher.matches()) continue;
            double unitPrice = Double.parseDouble(matcher.group(1).replace(",", ""));
            while (index < yourOrders.size() && (sell ? yourOrders.get(index).unitPrice() < unitPrice : yourOrders.get(index).unitPrice() > unitPrice)) index++;
            int count = 0, amount = 0;
            while (index < yourOrders.size() && yourOrders.get(index).unitPrice() == unitPrice) {
                count++;
                amount += yourOrders.get(index).amount();
                index++;
            }
            if (count == 0) continue;
            lines.add(++i, Component.literal("§8  - §7You have §a" + amount + "x §7in §f" + count + " §7order" + (count == 1 ? "" : "s")));
        }
    }

    // skyblocker ReorderHelper.onClickSlot: amount to copy from the green cancel order button. slot 13 on sell
    // offers, 11 on buy orders
    static String reorderAmount(int slot, List<String> cleanLore) {
        Pattern p = slot == 13 ? SELL_PATTERN : BUY_PATTERN;
        for (String l : cleanLore) {
            Matcher m = p.matcher(l);
            if (m.find()) return m.group(1).replace(",", "");
        }
        return null;
    }
}
