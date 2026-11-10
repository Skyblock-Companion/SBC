// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemInfo;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.item.market.BazaarApi;
import dev.zmuzy.sbc.item.market.BazaarExtras;
import dev.zmuzy.sbc.item.market.Market;
import dev.zmuzy.sbc.item.market.MoreMarket;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// market helpers next to hypixels menus: bazaar numbers (daily limit, what your purse buys, offer vs sell
// now), a recipes materials and where to buy them...
public final class MarketPanel {
    private static final int LINE = 22;
    private static final Bazaar BAZAAR = new Bazaar();
    private static final Craft CRAFT = new Craft();
    private static final Craftables CRAFTABLES = new Craftables();
    private static final Trade TRADE = new Trade();

    private MarketPanel() {}

    // the list on its own, beside any menu while it holds something
    private static final class Buy implements SideHelper.Content {
        @Override public String title() { return "Buy Helper"; }
        @Override public ItemStack icon() { return LegacyItems.stack("minecraft:gold_ingot"); }
        @Override public int accent() { return Modern.GOLD; }
        @Override public String[] tabs() { return new String[]{"To buy " + MoreMarket.buying.size()}; }
        @Override public void refresh(AbstractContainerScreen<?> screen) { }
        @Override public int summary(Ui ui, int x, int y, int w) { return 0; }
        @Override public int list(Ui ui, int tab, int x, int y, int w) { return buyList(ui, x, y, w); }
    }

    private static final Buy BUY = new Buy();

    static boolean isBuy(SideHelper.Content c) {
        return c == BUY;
    }

    public static SideHelper.Content buyFallback() {
        return MoreMarket.purchasing && !MoreMarket.buying.isEmpty() ? BUY : null;
    }

    static SideHelper.Content of(String title) {
        if (MoreMarket.inTrade) return TRADE;
        if (MoreMarket.inCraftMenu) return CRAFTABLES;
        if (MoreMarket.inRecipe) return CRAFT;
        if (Features.on(Market.BAZAAR) && BazaarApi.inBazaarInventory && (BAZAAR.anything() || MoreMarket.purchasing)) return BAZAAR;
        return null;
    }

    private static boolean bz(String key, boolean def) {
        return Market.b(Market.BAZAAR, key, def);
    }

    private static String count(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static void open(String url) {
        try {
            com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(url));
        } catch (RuntimeException ignored) {
        }
    }

    // icon in a well, name, value at the right end. click optional
    private static int row(Ui ui, ItemStack icon, String name, int nameColor, String value, int x, int y, int w, String[] tip, Runnable click) {
        if (y + LINE < ui.clipY0 || y > ui.clipY1) return LINE;
        boolean hv = ui.hover(x - 1, y, w + 2, LINE - 2);
        if (hv && click != null) ui.rect(x - 1, y, w + 2, LINE - 2, Ui.alpha(Modern.GOLD, 0.14f));
        ui.rect(x, y + 1, 18, 18, 0x50000000);
        if (icon != null) ui.icon(icon, x + 1, y + 2, 1f);
        int tx = x + 21, tw = w - 21, vw = value.isEmpty() ? 0 : ui.width(value);
        if (vw > 0 && ui.width(name) + 4 + vw > tw) {
            // long name keeps all its words, value goes under it
            ui.textShadow(ui.ellipsize(name, tw), tx, y + 1, nameColor);
            ui.textShadow(value, x + w - vw, y + 11, Modern.GOLD);
        } else {
            ui.textShadow(ui.ellipsize(name, tw - (vw > 0 ? vw + 4 : 0)), tx, y + 6, nameColor);
            if (vw > 0) ui.textShadow(value, x + w - vw, y + 6, Modern.GOLD);
        }
        if (hv && tip != null) ui.tooltip(tip);
        if (click != null) ui.click(x - 1, y, w + 2, LINE - 2, click);
        return LINE;
    }

    private static ItemStack stack(String id) {
        return id.isEmpty() ? LegacyItems.stack("minecraft:gold_nugget") : ItemInfo.stack(id);
    }

    // --- bazaar ---

    private static final class Bazaar implements SideHelper.Content {
        boolean anything() {
            return bz("market_bazaar.dailyLimit", true) || bz("market_bazaar.bestSellMethod", true) || bz("market_bazaar.maxPurse", true) || bz("market_bazaar.priceWebsite", false);
        }

        @Override
        public String title() {
            return "Bazaar Helper";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("minecraft:gold_ingot");
        }

        @Override
        public int accent() {
            return Modern.GOLD;
        }

        private static String product() {
            String id = BazaarApi.currentlyOpenedProduct;
            return id == null ? "Your orders" : GameState.strip(ItemRepo.name(id));
        }

        @Override
        public String[] tabs() {
            // buy list first, its why you came
            return MoreMarket.purchasing ? new String[]{"To buy " + MoreMarket.buying.size(), product()} : new String[]{product()};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            if (!bz("market_bazaar.dailyLimit", true)) return 0;
            double done = Store.I.bazaarCoinsTowardsLimit, share = done / BazaarExtras.DAILY_LIMIT;
            boolean reached = done >= BazaarExtras.DAILY_LIMIT;
            int color = reached ? Modern.BAD : share >= 0.75 ? Modern.WARN : Modern.GOOD;
            Modern.row(ui, "Daily limit", Modern.fraction(Prices.coins(done), Prices.coins(BazaarExtras.DAILY_LIMIT), 'f', '7') + " §b" + ItemList.percent(Math.min(1, share)), x, y, w, Modern.LABEL, Modern.INK);
            Modern.meter(ui, x, y + Modern.ROW - 1, w, Modern.METER, Math.min(1, share), color);
            if (ui.hover(x, y, w, Modern.ROW + Modern.METER_BLOCK))
                ui.tooltip("§7Traded today: §6" + count((long) done) + " §7coins", reached ? "§cLimit reached!" : "§7Left: §6" + count((long) (BazaarExtras.DAILY_LIMIT - done)) + " §7coins", "§8Resets at midnight UTC. Flips do not count.");
            return Modern.ROW + Modern.METER_BLOCK;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            if (MoreMarket.purchasing && tab == 0) return buyList(ui, x, y, w);
            int y0 = y;
            String id = BazaarApi.currentlyOpenedProduct;
            if (id == null) return orders(ui, x, y, w);
            if (bz("market_bazaar.maxPurse", true)) {
                long purse = Math.max(0, GameState.purse);
                Modern.section(ui, "Most your purse buys", x, y, w, Modern.GOLD);
                y += Modern.ROW;
                Modern.row(ui, "Buy order +0.1", "§e" + count(BazaarExtras.maxItems(purse, BazaarExtras.buyOrderPrice())) + "x", x, y, w, Modern.LABEL, Modern.INK);
                y += Modern.ROW;
                Modern.row(ui, "Instant buy", "§e" + count(BazaarExtras.maxItems(purse, BazaarExtras.instantBuyPrice())) + "x", x, y, w, Modern.LABEL, Modern.INK);
                y += Modern.ROW + 3;
            }
            Prices.Product p = Prices.get(id);
            long having = Market.having(id);
            if (bz("market_bazaar.bestSellMethod", true) && p != null && having > 0) {
                Modern.section(ui, "Selling yours", x, y, w, Modern.GOLD);
                y += Modern.ROW;
                Modern.row(ui, "You carry", "§f" + count(having), x, y, w, Modern.LABEL, Modern.INK);
                y += Modern.ROW;
                Modern.row(ui, "Offer over instant", "§6+" + Prices.coins(BazaarExtras.sellDifference(p.buy(), p.sell(), having)), x, y, w, Modern.LABEL, Modern.INK);
                y += Modern.ROW + 3;
            }
            if (bz("market_bazaar.priceWebsite", false)) {
                ui.button("bz_history", x, y, w, 14, "Price history", Ui.GHOST, () -> open(BazaarExtras.priceWebsite(id)));
                y += 16;
            }
            return y - y0;
        }

        // BazaarOrderApi open amounts, read from the order menu
        private int orders(Ui ui, int x, int y, int w) {
            int y0 = y;
            y = orderList(ui, "Buy orders", BazaarApi.buyOrders, x, y, w);
            y = orderList(ui, "Sell offers", BazaarApi.sellOffers, x, y, w);
            if (y == y0) y += SideHelper.emptyText(ui, "Open your orders once to list them.", x, y, w);
            return y - y0;
        }

        private int orderList(Ui ui, String heading, Map<String, Integer> orders, int x, int y, int w) {
            if (orders.isEmpty()) return y;
            Modern.section(ui, heading, x, y, w, Modern.GOLD);
            y += Modern.ROW;
            for (var e : orders.entrySet()) y += row(ui, stack(e.getKey()), ItemRepo.coloredName(e.getKey()), Modern.INK, "§f" + count(e.getValue()) + "x", x, y, w, null, null);
            return y + 2;
        }
    }

    // what you came to buy (the visitors wants or a recipes materials). click a line to search it, x removes it
    // clear all forgets the list. shows next to the bazaar and next to a visitor
    public static int buyList(Ui ui, int x, int y, int w) {
        {
            int y0 = y, times = MoreMarket.fromVisitors ? 1 : MoreMarket.multiplier;
            String from = MoreMarket.fromVisitors ? "Your visitors want" : "Craft " + GameState.strip(MoreMarket.recipeName);
            SideHelper.tile(ui, x, y, w, from, Prices.coins(MoreMarket.totalPrice(MoreMarket.buying, times)), times > 1 ? "x" + times : "", Modern.GOLD);
            y += 25;
            if (!MoreMarket.fromVisitors) {
                int n = MoreMarket.MULTIPLIERS.length, each = (w - (n - 1) * 2) / n, bx = x;
                for (int i = 0; i < n; i++) {
                    int m = MoreMarket.MULTIPLIERS[i];
                    ui.button("buy_x" + m, bx, y, each, 14, "x" + m, m == MoreMarket.multiplier ? Ui.PRIMARY : Ui.GHOST, () -> MoreMarket.multiplier = m);
                    bx += each + 2;
                }
                y += 16;
            }
            for (MoreMarket.Material m : new ArrayList<>(MoreMarket.buying)) {
                long amount = (long) m.amount() * times;
                String name = "§7" + count(amount) + "x §r" + ItemRepo.coloredName(m.id());
                boolean market = MoreMarket.onMarket(m.id());
                String where = Prices.get(m.id()) != null ? "Bazaar" : "Auction House";
                String[] tip = market ? new String[]{"§eClick to search for " + GameState.strip(ItemRepo.name(m.id())) + " in " + where + "!"} : new String[]{"§cNot on the Auction House or Bazaar"};
                int rowY = y;
                y += row(ui, stack(m.id()), name, Modern.INK, market ? Prices.coins(MoreMarket.price(m.id()) * amount) : "", x, y, w - 14, tip, market ? () -> Market.buy(m.id(), amount) : null);
                boolean hv = ui.hover(x + w - 12, rowY + 4, 12, 12);
                if (hv) ui.rect(x + w - 12, rowY + 4, 12, 12, Ui.alpha(Modern.BAD, 0.35f));
                ui.textShadow("✕", x + w - 9, rowY + 6, hv ? 0xFFFFFFFF : Modern.DIM);
                if (hv) ui.tooltip("§cTake it off the list");
                ui.click(x + w - 12, rowY + 4, 12, 12, () -> MoreMarket.remove(m.id()));
            }
            ui.button("buy_clear", x, y + 2, w, 14, "Clear all", Ui.GHOST, MoreMarket::stopBuying);
            y += 18;
            return y - y0;
        }
    }

    // --- recipe materials ---

    private static final class Craft implements SideHelper.Content {
        private static List<MoreMarket.Material> shown() {
            return MoreMarket.purchasing ? MoreMarket.buying : MoreMarket.materials;
        }

        @Override
        public String title() {
            return "Craft Materials";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("minecraft:crafting_table");
        }

        @Override
        public int accent() {
            return Modern.GOOD;
        }

        @Override
        public String[] tabs() {
            return new String[]{"Materials " + shown().size()};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            int times = MoreMarket.purchasing ? MoreMarket.multiplier : 1;
            String recipe = GameState.strip(MoreMarket.recipeName);
            SideHelper.tile(ui, x, y, w, MoreMarket.purchasing ? "Total" : recipe == null || recipe.isEmpty() ? "Costs to craft" : "Craft " + recipe, Prices.coins(MoreMarket.totalPrice(shown(), times)), times > 1 ? "x" + times : "", Modern.GOLD);
            int cy = y + 25;
            if (!MoreMarket.purchasing) {
                ui.button("craft_buy", x, cy, w, 14, "Buy these at the Bazaar", Ui.PRIMARY, MoreMarket::startBuying);
                if (ui.hover(x, cy, w, 14)) ui.tooltip("§7The list waits in the Bazaar Helper.");
                return 41;
            }
            int n = MoreMarket.MULTIPLIERS.length, each = (w - (n - 1) * 2) / n, bx = x;
            for (int i = 0; i < n; i++) {
                int m = MoreMarket.MULTIPLIERS[i];
                ui.button("craft_x" + m, bx, cy, each, 14, "x" + m, m == MoreMarket.multiplier ? Ui.PRIMARY : Ui.GHOST, () -> MoreMarket.multiplier = m);
                if (ui.hover(bx, cy, each, 14)) ui.tooltip("§7x" + m + ": §6" + Prices.coins(MoreMarket.totalPrice(MoreMarket.buying, m)));
                bx += each + 2;
            }
            ui.button("craft_stop", x, cy + 16, w, 14, "Stop", Ui.GHOST, MoreMarket::stopBuying);
            return 57;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y, times = MoreMarket.purchasing ? MoreMarket.multiplier : 1;
            for (MoreMarket.Material m : shown()) {
                long amount = (long) m.amount() * times;
                String name = "§7" + count(amount) + "x §r" + ItemRepo.coloredName(m.id());
                boolean market = MoreMarket.onMarket(m.id());
                String where = Prices.get(m.id()) != null ? "Bazaar" : "Auction House";
                String[] tip = market ? new String[]{"§eClick to search for " + GameState.strip(ItemRepo.name(m.id())) + " in " + where + "!"} : new String[]{"§cNot on the Auction House or Bazaar"};
                y += row(ui, stack(m.id()), name, Modern.INK, market ? Prices.coins(MoreMarket.price(m.id()) * amount) : "", x, y, w, tip, market ? () -> Market.buy(m.id(), amount) : null);
            }
            return y - y0;
        }
    }

    // --- what you can craft ---

    private static final class Craftables implements SideHelper.Content {
        @Override
        public String title() {
            return "Craftable Items";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("minecraft:crafting_table");
        }

        @Override
        public int accent() {
            return Modern.GOOD;
        }

        @Override
        public String[] tabs() {
            return new String[]{"Craftable " + MoreMarket.craftable.size()};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            return 0;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            for (MoreMarket.Craftable c : MoreMarket.craftable) {
                List<String> tip = new ArrayList<>();
                tip.add(ItemRepo.coloredName(c.id()));
                tip.add("");
                tip.add("§7Craft cost: §6" + Prices.coins(c.price()));
                for (var e : c.need().entrySet()) tip.add(" §8" + count(e.getValue()) + "x §r" + ItemRepo.coloredName(e.getKey()) + " §7(§6" + Prices.coins(MoreMarket.price(e.getKey()) * e.getValue()) + "§7)");
                tip.add("");
                tip.add("§7You have enough materials");
                tip.add("§7to craft this item §e" + count(c.times()) + " §7" + (c.times() == 1 ? "time" : "times") + "!");
                tip.add("");
                tip.add("§eClick to craft!");
                y += row(ui, stack(c.id()), "§7x" + count(c.times()) + " §r" + ItemRepo.coloredName(c.id()), Modern.INK, Prices.coins(c.price()), x, y, w, tip.toArray(new String[0]), () -> {
                    var conn = Minecraft.getInstance().getConnection();
                    if (conn != null) conn.sendCommand("viewrecipe " + c.id() + " 1");
                });
            }
            if (y == y0) y += SideHelper.emptyText(ui, "No items in your inventory craft into anything.", x, y, w);
            return y - y0;
        }
    }

    // --- trade ---

    private static final class Trade implements SideHelper.Content {
        @Override
        public String title() {
            return "Trade Value";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("minecraft:gold_nugget");
        }

        @Override
        public int accent() {
            return Modern.GOLD;
        }

        @Override
        public String[] tabs() {
            return new String[]{"You " + MoreMarket.yourItems.size(), "Them " + MoreMarket.otherItems.size()};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            int half = (w - 3) / 2;
            SideHelper.tile(ui, x, y, half, "Your side", Prices.coins(MoreMarket.yourTotal), "", Modern.GOLD);
            SideHelper.tile(ui, x + half + 3, y, w - half - 3, "Their side", Prices.coins(MoreMarket.otherTotal), "", Modern.GOLD);
            double diff = MoreMarket.otherTotal - MoreMarket.yourTotal;
            Modern.row(ui, "You gain", (diff >= 0 ? "§a+" : "§c-") + Prices.coins(Math.abs(diff)), x, y + 26, w, Modern.LABEL, Modern.INK);
            return 36;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            for (MoreMarket.Line l : tab == 0 ? MoreMarket.yourItems : MoreMarket.otherItems)
                y += row(ui, l.icon(), "§7x" + count(l.amount()) + " §r" + l.name(), Modern.INK, Prices.coins(l.total()), x, y, w, null, null);
            if (y == y0) y += SideHelper.emptyText(ui, "Nothing with a price yet.", x, y, w);
            return y - y0;
        }
    }

    // --- ah search ---

    // --- for pictures ---

    static SideHelper.Content bazaarForPicture() {
        return BAZAAR;
    }

    static SideHelper.Content craftForPicture() {
        return CRAFT;
    }

    static SideHelper.Content craftablesForPicture() {
        return CRAFTABLES;
    }

    static SideHelper.Content tradeForPicture() {
        return TRADE;
    }
}
