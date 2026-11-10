// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item.market;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni BazaarApi + BazaarOrderApi + BazaarOrderHelper
// knows when a bazaar menu is open (Go Back -> "To Bazaar", custom amount, order menus, known titles), the
// item just searched for (highlighted in results)
// instant sell tax from the Sell Instantly lore, transaction chat lines
// your open orders from "Your Bazaar Orders" / "Co-op Bazaar Orders" (full = green, priced worse than instant = gold)
public final class BazaarApi {
    public enum TransactionType { BUY_ORDER, SELL_OFFER }

    // from skyhanni BazaarOrder
    public record Order(int slot, TransactionType type, String itemName, String internalName, int amount, int filled, boolean isFull, int claimable, double pricePerUnit, boolean isOwn) {
        public int outstanding() {
            return amount - filled;
        }
    }

    // one menu item: formatted name, clean name, formatted lore
    public record Item(String formattedName, String cleanName, List<String> lore) {
        public List<String> cleanLore() {
            List<String> out = new ArrayList<>();
            for (String l : lore) out.add(GameState.strip(l));
            return out;
        }
    }

    static final Patterns.P TRANSACTION = Patterns.p("SH.inventory.bazaar.transaction");
    static final Patterns.P[] INVENTORY_NAMES = {Patterns.p("SH.inventory.bazaar.inventory-name.0"), Patterns.p("SH.inventory.bazaar.inventory-name.1"), Patterns.p("SH.inventory.bazaar.inventory-name.2"),
            Patterns.p("SH.inventory.bazaar.inventory-name.3"), Patterns.p("SH.inventory.bazaar.inventory-name.4"), Patterns.p("SH.inventory.bazaar.inventory-name.5"), Patterns.p("SH.inventory.bazaar.inventory-name.6"), Patterns.p("SH.inventory.bazaar.inventory-name.7")};
    static final Patterns.P[] ORDER_INVENTORIES = {Patterns.p("SH.inventory.bazaar.inventory-bazaar-orders.0"), Patterns.p("SH.inventory.bazaar.inventory-bazaar-orders.1")};
    static final Patterns.P TAX = Patterns.p("SH.inventory.bazaar.instantsell.tax");
    static final Patterns.P ORDER_ITEM_NAME = Patterns.p("SH.bazaar.orders.itemname");
    static final Patterns.P ORDER_AMOUNT = Patterns.p("SH.bazaar.orders.amount.colorless");
    static final Patterns.P ORDER_FILLED = Patterns.p("SH.bazaar.orders.filled.colorless");
    static final Patterns.P ORDER_CLAIMABLE = Patterns.p("SH.bazaar.orders.claimable.colorless");
    static final Patterns.P ORDER_PRICE = Patterns.p("SH.bazaar.orders.price.colorless");
    static final Patterns.P ORDER_OWNER = Patterns.p("SH.bazaar.orders.owner.colorless");
    static final Patterns.P ORDER_SETUP = Patterns.p("SH.bazaar.orders.chat.setup.colorless");
    static final Patterns.P ORDER_CLAIMED = Patterns.p("SH.bazaar.orders.chat.claimed.colorless");
    static final Patterns.P ORDER_CANCELLED = Patterns.p("SH.bazaar.orders.chat.cancelled.colorless");

    public static boolean inBazaarInventory;
    static String currentSearchedItem = "";
    public static double taxRate = 1.25;
    public static String orderOptionProduct;
    // skyhanni currentlyOpenedProduct: the product whose page is open
    public static String currentlyOpenedProduct;
    // skyhanni BazaarTransactionEvent: the lines type word + its coins
    public static java.util.function.BiConsumer<String, Double> onTransaction = (type, coins) -> {};
    static List<Order> orders = List.of();
    // skyhanni BazaarOrderApi storage: internal name -> amount still open
    public static final Map<String, Integer> buyOrders = new java.util.HashMap<>(), sellOffers = new java.util.HashMap<>();

    // skyhanni NeuInternalName.fromItemName, swappable in tests
    public static java.util.function.Function<String, String> idFromName = n -> n;
    public static java.util.function.Function<String, double[]> bazaarPrices = id -> null; // {instantBuy, instantSell}
    public static String playerName = "";

    private BazaarApi() {}

    public static boolean isBazaarOrderInventory(String inventoryName) {
        for (Patterns.P p : ORDER_INVENTORIES) if (p.matcher(inventoryName).matches()) return true;
        return false;
    }

    // from skyhanni checkIfInBazaar
    public static boolean checkIfInBazaar(String inventoryName, int inventorySize, Map<Integer, Item> items) {
        for (int i : new int[]{inventorySize - 5, inventorySize - 6}) {
            Item it = items.get(i);
            if (it != null && it.cleanName().equals("Go Back") && !it.lore().isEmpty() && it.lore().get(0).equals("§7To Bazaar")) return true;
        }
        Item custom = items.get(16);
        if (custom != null && custom.cleanName().equals("Custom Amount") && !custom.lore().isEmpty() && custom.lore().get(0).equals("§8Buy Order Quantity")) return true;
        if (isBazaarOrderInventory(inventoryName)) return true;
        for (Patterns.P p : INVENTORY_NAMES) if (p.matcher(inventoryName).matches()) return true;
        return false;
    }

    // skyhanni onInventoryFullyOpened: sets inBazaarInventory, the tax rate (sell instantly in slot 11) and reads orders
    public static void onInventoryFullyOpened(String inventoryName, int inventorySize, Map<Integer, Item> items) {
        inBazaarInventory = checkIfInBazaar(inventoryName, inventorySize, items);
        if (!inBazaarInventory) return;
        Item sell = items.get(11);
        if (sell != null && sell.formattedName().equals("§6Sell Instantly")) for (String l : sell.lore()) {
            Matcher m = TAX.matcher(l);
            if (m.matches()) {
                taxRate = Double.parseDouble(m.group("tax"));
                break;
            }
        }
        String opened = getOpenedProduct(items);
        if (opened != null) currentlyOpenedProduct = opened;
        if (isBazaarOrderInventory(inventoryName)) readOrders(items);
    }

    // skyhanni getOpenedProduct: a product page has buy instantly in slot 10 and the product in slot 13
    static String getOpenedProduct(Map<Integer, Item> items) {
        Item buyInstantly = items.get(10);
        if (buyInstantly == null || !buyInstantly.formattedName().equals("§aBuy Instantly")) return null;
        Item bazaarItem = items.get(13);
        return bazaarItem == null ? null : idFromName.apply(bazaarItem.cleanName());
    }

    public static void onInventoryClose() {
        inBazaarInventory = false;
        currentlyOpenedProduct = null;
        orders = List.of();
    }

    // skyhanni searchForBazaarItem: remember the display name so the results page can highlight it
    public static void onSearch(String displayName) {
        currentSearchedItem = GameState.strip(displayName);
    }

    // skyhanni onBackgroundDrawn: a results slot (9..44) whose clean name is the searched item
    public static boolean highlightSearched(int slot, String cleanName) {
        return inBazaarInventory && !currentSearchedItem.isEmpty() && slot >= 9 && slot <= 44 && cleanName.equals(currentSearchedItem);
    }

    // skyhanni onChat, clean line. true if it was a bazaar transaction
    public static boolean onChat(String clean) {
        Matcher m = TRANSACTION.matcher(clean);
        if (m.matches()) {
            try {
                onTransaction.accept(m.group("type"), Double.parseDouble(m.group("coins").replace(",", "")));
            } catch (NumberFormatException ignored) {
            }
            if (currentSearchedItem.equals(m.group("item"))) currentSearchedItem = "";
            return true;
        }
        Matcher s = ORDER_SETUP.matcher(clean);
        if (s.matches()) {
            buyOrders.merge(idFromName.apply(s.group("item")), Integer.parseInt(s.group("amount").replace(",", "")), Integer::sum);
            return true;
        }
        Matcher c = ORDER_CLAIMED.matcher(clean);
        if (c.matches()) {
            String id = idFromName.apply(c.group("item"));
            Integer have = buyOrders.get(id);
            if (have != null) {
                int left = have - Integer.parseInt(c.group("amount").replace(",", ""));
                if (left > 0) buyOrders.put(id, left);
                else buyOrders.remove(id);
            }
            return true;
        }
        if (ORDER_CANCELLED.matcher(clean).matches()) {
            if (orderOptionProduct != null) buyOrders.remove(orderOptionProduct);
            return true;
        }
        return false;
    }

    // BazaarOrderApi

    static int formatInt(String s) {
        s = s.replace(",", "").toLowerCase(java.util.Locale.ROOT);
        double mult = s.endsWith("k") ? 1000 : s.endsWith("m") ? 1_000_000 : 1;
        if (mult > 1) s = s.substring(0, s.length() - 1);
        return (int) Math.round(Double.parseDouble(s) * mult);
    }

    // from skyhanni BazaarOrderApi.parseOrder
    public static Order parseOrder(int slot, Item stack) {
        Matcher m = ORDER_ITEM_NAME.matcher(stack.formattedName());
        if (!m.matches()) return null;
        TransactionType type = m.group("type").equals("BUY") ? TransactionType.BUY_ORDER : TransactionType.SELL_OFFER;
        String itemName = GameState.strip(m.group("name"));
        List<String> lore = stack.cleanLore();
        int claimable = 0;
        String owner = null;
        Integer amount = null;
        Double pricePerUnit = null;
        int filled = 0;
        boolean isFull = false;
        for (String l : lore) {
            Matcher x;
            if ((x = ORDER_CLAIMABLE.matcher(l)).matches()) claimable = formatInt(x.group("amount"));
            else if ((x = ORDER_OWNER.matcher(l)).matches()) owner = x.group("name");
            else if ((x = ORDER_AMOUNT.matcher(l)).matches()) amount = formatInt(x.group("amount"));
            else if ((x = ORDER_PRICE.matcher(l)).matches()) pricePerUnit = Double.parseDouble(x.group("price").replace(",", ""));
            else if ((x = ORDER_FILLED.matcher(l)).matches()) {
                try {
                    filled = formatInt(x.group("filled"));
                } catch (NumberFormatException ignored) {
                }
                isFull = x.group("progress").equals("100%!");
            }
        }
        if (amount == null || pricePerUnit == null) return null;
        return new Order(slot, type, itemName, idFromName.apply(itemName), amount, isFull ? amount : filled, isFull, claimable, pricePerUnit, owner == null || owner.equals(playerName));
    }

    // skyhanni readOrders: the order menu replaces all stored orders
    public static void readOrders(Map<Integer, Item> items) {
        List<Order> out = new ArrayList<>();
        for (var e : items.entrySet()) {
            Order o = parseOrder(e.getKey(), e.getValue());
            if (o != null) out.add(o);
        }
        orders = out;
        buyOrders.clear();
        sellOffers.clear();
        for (Order o : out) {
            if (!o.isOwn() || o.outstanding() <= 0) continue;
            (o.type() == TransactionType.BUY_ORDER ? buyOrders : sellOffers).merge(o.internalName(), o.outstanding(), Integer::sum);
        }
    }

    public static List<Order> orders() {
        return orders;
    }

    // skyhanni BazaarOrderHelper.highlightColor: green when full, gold when priced worse than instant, else null
    public static Integer highlightOrder(Order order) {
        if (order.isFull()) return 0x8055FF55;
        double[] data = bazaarPrices.apply(order.internalName());
        if (data == null) return null;
        boolean badPrice = order.type() == TransactionType.BUY_ORDER ? order.pricePerUnit() < data[1] : order.pricePerUnit() > data[0];
        return badPrice ? 0x80FFAA00 : null;
    }

    // slot -> highlight color for the open order menu
    public static Integer highlightSlot(int slot) {
        for (Order o : orders) if (o.slot() == slot) return highlightOrder(o);
        return null;
    }

    // skyhanni onSlotClick in an order menu: remember the product for cancel handling (BUY/SELL prefix stripped)
    public static void onOrderSlotClick(String cleanName) {
        String n = cleanName;
        if (n.startsWith("BUY ")) n = n.substring(4);
        else if (n.startsWith("SELL ")) n = n.substring(5);
        else return;
        orderOptionProduct = idFromName.apply(n);
    }
}
