// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item.market;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.AuctionPrices;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.value.EstimatedItemValue;
import io.github.zmuzy.sbc.mc.Interactions;
import io.github.zmuzy.sbc.mc.mixin.ContainerAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// glue for the skyhanni + skyblocker bazaar and ah ports: menu reading, slot highlights, chat, copy underbid
// command, options
// the rules live in BazaarApi, BazaarOrders, BazaarExtras, BazaarSign, Auctions
public final class Market {
    public static final String BAZAAR = "market_bazaar", AUCTIONS = "market_auctions";

    private static int lastMenuId = -1;
    private static String lastMenuTitle = "";
    private static int lastMenuHash;
    private static String lastCopiedItem = "";
    private static String lastIsland = "";
    // skyblocker BazaarHelper: what goes in the corner of each order in the open menu
    private static Map<Integer, BazaarOrders.Icon> icons = Map.of();
    // skyhanni BazaarBestSellMethod.lastClickedItem: the stack you clicked to open a product isnt in your
    // inventory for a moment
    private static String lastClickedId = "";
    private static int lastClickedCount;
    private static boolean nextCloseWillResetItem;

    private Market() {}

    public static boolean b(String feature, String key, boolean def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    private static boolean sb() {
        return GameState.inSkyBlock || SbcMod.DEV;
    }

    private static BazaarApi.Item item(ItemStack st) {
        return new BazaarApi.Item(LegacyText.name(st), GameState.strip(LegacyText.name(st)).trim(), LegacyText.lore(st));
    }

    private static Map<Integer, BazaarApi.Item> items(ChestMenu menu, int n) {
        Map<Integer, BazaarApi.Item> out = new HashMap<>();
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (!st.isEmpty()) out.put(i, item(st));
        }
        return out;
    }

    // skyhanni InventoryFullyOpenedEvent / InventoryUpdatedEvent / InventoryCloseEvent
    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        if (!GameState.island.equals(lastIsland)) {
            lastIsland = GameState.island;
            NpcTrade.clear();
        }
        var screen = mc.gui.screen();
        if (screen == null || !(mc.player.containerMenu instanceof ChestMenu menu)) {
            if (lastMenuId != -1) {
                lastMenuId = -1;
                lastMenuTitle = "";
                lastMenuHash = 0;
                menuClosed();
            }
            return;
        }
        if (!sb()) return;
        String title = screen.getTitle().getString();
        int n = Math.min(menu.getRowCount() * 9, menu.slots.size());
        int hash = 0;
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (!st.isEmpty()) hash = hash * 31 + st.getHoverName().getString().hashCode() + st.getCount();
        }
        if (menu.containerId == lastMenuId && title.equals(lastMenuTitle) && hash == lastMenuHash) return;
        // close also comes between two menus, hypixel opening the next one closes this one
        if (lastMenuId != -1 && menu.containerId != lastMenuId) menuClosed();
        lastMenuId = menu.containerId;
        lastMenuTitle = title;
        lastMenuHash = hash;
        if (hash == 0) return;
        Map<Integer, BazaarApi.Item> items = items(menu, n);
        if (Features.on(BAZAAR)) {
            BazaarApi.playerName = mc.getUser().getName();
            BazaarApi.onInventoryFullyOpened(title, n, items);
            Store.I.bazaarTaxRate = BazaarApi.taxRate;
        }
        // a line hypixel words differently one day must not take the tick down with it
        String menuTitle = GameState.strip(title);
        guarded(() -> {
            if (Features.on(BAZAAR)) bazaarMenu(menuTitle, n, menu, items);
        });
        guarded(() -> {
            if (Features.on(AUCTIONS)) auctionMenu(menuTitle, n, menu);
        });
        guarded(() -> Cakes.onMenu(menuTitle, menu, n));
        guarded(() -> MoreMarket.onMenu(menuTitle, n, menu));
        guarded(() -> io.github.zmuzy.sbc.item.tooltip.BitsHelper.onMenu(menuTitle, menu, n));
        guarded(() -> NpcTrade.onMenu(menuTitle, menu, n));
        guarded(() -> io.github.zmuzy.sbc.item.MagicalPowerDisplay.onMenu(menuTitle, menu, n));
        if (Features.on(AUCTIONS) && b(AUCTIONS, "autoCopyUnderbidPrice", false) && title.equals("Create BIN Auction")) {
            ItemStack st = n > 13 ? menu.slots.get(13).getItem() : ItemStack.EMPTY;
            String id = Gain.sbId(st);
            if (!id.isEmpty() && !id.equals(lastCopiedItem)) {
                lastCopiedItem = id;
                long price = (long) AuctionPrices.value(id);
                if (price <= 0) mc.keyboardHandler.setClipboard("");
                else copyPrice(price * st.getCount());
            }
        }
    }

    private static void menuClosed() {
        BazaarApi.onInventoryClose();
        icons = Map.of();
        BazaarExtras.resetPurse();
        Auctions.clearComparison();
        MoreMarket.onClose();
        if (!lastClickedId.isEmpty()) {
            if (nextCloseWillResetItem) lastClickedId = "";
            nextCloseWillResetItem = !nextCloseWillResetItem;
        }
    }

    private static void guarded(Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            SbcMod.LOG.warn("market menu reader failed", e);
        }
    }

    // the bazaar helpers that read the menu once when it changes
    private static void bazaarMenu(String title, int n, ChestMenu menu, Map<Integer, BazaarApi.Item> items) {
        Map<Integer, BazaarOrders.Icon> found = new HashMap<>();
        if (BazaarOrders.ORDER_MENUS.matcher(title).matches()) {
            BazaarOrders.clearOrders();
            for (var e : items.entrySet()) {
                int slot = e.getKey();
                List<String> clean = e.getValue().cleanLore();
                if (slot < 10 || slot > n - 11 || slot % 9 == 0 || slot % 9 == 8) continue;
                BazaarOrders.processOrder(slot, Gain.sbId(menu.slots.get(slot).getItem()), clean);
                BazaarOrders.Icon icon = BazaarOrders.icon(slot, n, clean);
                if (icon != null) found.put(slot, icon);
            }
        }
        icons = found;
        if (!BazaarApi.inBazaarInventory) return;
        BazaarExtras.checkDate();
        BazaarExtras.readCancel(title, items);
        if (BazaarApi.currentlyOpenedProduct == null) BazaarExtras.resetPurse();
        else if (BazaarExtras.buyOrderPrice == null && BazaarExtras.instantBuyPrice == null) BazaarExtras.readPrices(items);
    }

    // from skyhanni AuctionHousePriceComparison.onInventoryOpen
    private static void auctionMenu(String title, int n, ChestMenu menu) {
        Auctions.clearComparison();
        if (!b(AUCTIONS, "market_auctions.comparison", true) || !Auctions.comparisonMenu(title)) return;
        Map<Integer, Long> map = new HashMap<>();
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st.isEmpty()) continue;
            List<String> lore = LegacyText.lore(st);
            Auctions.compare(map, i, lore, estimated(st, lore));
        }
        Auctions.finishComparison(map);
    }

    // skyhanni getTotalPrice(stack, ignoreBasePrice = true): null when nothing was added to the plain item
    private static Double estimated(ItemStack st, List<String> lore) {
        var cd = st.get(DataComponents.CUSTOM_DATA);
        String id = Gain.sbId(st);
        if (cd == null || id.isEmpty()) return null;
        boolean dungeon = lore.stream().anyMatch(l -> l.contains("DUNGEON "));
        var r = EstimatedItemValue.calculate(cd.copyTag(), id, dungeon, lore);
        return r.totalPrice() != r.basePrice() ? r.totalPrice() : null;
    }

    private static int color(String key, int def) {
        Feature f = Features.get(AUCTIONS);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.color();
    }

    // how many you carry, plus the stack you just clicked to open its page
    public static long having(String id) {
        var mc = Minecraft.getInstance();
        if (mc.player == null || id == null) return 0;
        long n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (id.equals(Gain.sbId(st))) n += st.getCount();
        }
        return id.equals(lastClickedId) ? n + lastClickedCount : n;
    }

    // skyhanni ItemBuyApi.buy: sends the markets own search once, from our press. amount goes on the clipboard
    public static void buy(String id, long amount) {
        var mc = Minecraft.getInstance();
        var conn = mc.getConnection();
        if (conn == null) return;
        String name = GameState.strip(ItemRepo.name(id)).trim();
        if (Prices.get(id) != null) {
            BazaarApi.onSearch(name);
            conn.sendCommand("bz " + name);
            mc.keyboardHandler.setClipboard(String.valueOf(amount));
        } else if (AuctionPrices.lowestBin(id) > 0) {
            conn.sendCommand("auctionsearch " + name);
        } else if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal("§b[SBC] §cCould not find " + name + " on the Auction House or Bazaar!"));
        }
    }

    // bazaar menu tooltip
    private static void bazaarTooltip(ItemStack st, List<Component> lines) {
        var mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || !(cs instanceof ContainerAccess acc)) return;
        String title = GameState.strip(cs.getTitle().getString());
        if (b(BAZAAR, "market_bazaar.orderTracker", true) && BazaarOrders.PRODUCT_MENUS.matcher(title).matches()) {
            String name = st.getHoverName().getString();
            boolean buy = st.is(Items.FILLED_MAP) && "Create Buy Order".equals(name), sell = st.is(Items.MAP) && "Create Sell Offer".equals(name);
            if ((buy || sell) && cs.getMenu().slots.size() > 13) BazaarOrders.addOrderMarker(lines, sell, Gain.sbId(cs.getMenu().slots.get(13).getItem()));
        }
        Slot hovered = acc.sbc$hovered();
        if (b(BAZAAR, "market_bazaar.reorder", true) && hovered != null && hovered.getItem() == st && st.is(Items.DYED_TERRACOTTA.green()) && (hovered.index == 11 || hovered.index == 13)
                && BazaarOrders.ORDER_OPTIONS.matcher(title).find()) {
            lines.add(Component.empty());
            lines.add(Component.literal("§8Hold CTRL to copy the remaining amount."));
        }
        if (BazaarApi.isBazaarOrderInventory(title)) {
            if (b(BAZAAR, "market_bazaar.hideTradePartners", false)) BazaarExtras.removeBlock(lines, l -> BazaarExtras.TRADE_PARTNERS.matcher(l).matches());
            // skyhanni BazaarOrderTooltip: the owner only matters while a coop member has orders in here too
            if (b(BAZAAR, "market_bazaar.hideOwner", false) && BazaarApi.orders().stream().allMatch(BazaarApi.Order::isOwn)) BazaarExtras.removeBlock(lines, l -> BazaarApi.ORDER_OWNER.matcher(l).matches());
        }
    }

    private static void auctionTooltip(ItemStack st, List<Component> lines) {
        var mc = Minecraft.getInstance();
        if (!b(AUCTIONS, "market_auctions.comparison", true) || !(mc.gui.screen() instanceof AbstractContainerScreen<?> cs) || !(cs instanceof ContainerAccess acc)) return;
        Slot hovered = acc.sbc$hovered();
        if (hovered == null || hovered.getItem() != st || mc.player == null || hovered.container == mc.player.getInventory()) return;
        Auctions.comparisonTooltip(hovered.getContainerSlot(), lines);
    }

    private static void copyPrice(long price) {
        long underbid = price - 1;
        Minecraft.getInstance().keyboardHandler.setClipboard(String.valueOf(underbid));
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §rCopied " + String.format(Locale.ROOT, "%,d", underbid) + " to clipboard. (Copy Underbid Price)"));
    }

    // skyhanni onGuiKeyPress: hovered auction price minus 1 (/sbc copyunderbid, or the parents key)
    public static void copyUnderbidHovered() {
        var mc = Minecraft.getInstance();
        var screen = mc.gui.screen();
        if (!(screen instanceof AbstractContainerScreen<?> cs) || !(cs instanceof ContainerAccess acc)) return;
        Slot hovered = acc.sbc$hovered();
        if (hovered == null || !hovered.hasItem()) return;
        long price = Auctions.underbidPriceOf(GameState.strip(screen.getTitle().getString()), LegacyText.lore(hovered.getItem()));
        if (price >= 0) copyPrice(price + 1);
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!sb()) return ChatBus.PASS;
        if (Features.on(BAZAAR)) {
            String clean = line.plain().trim();
            Component cancelled = cancelledOrder(clean);
            BazaarApi.onChat(clean);
            if (cancelled != null) return new ChatBus.Replace(cancelled);
        }
        if (Features.on(AUCTIONS) && b(AUCTIONS, "auctionOutbid", true) && Auctions.isOutbid(LegacyText.of(line.component()))) {
            var mc = Minecraft.getInstance();
            mc.gui.hud.setTimes(5, 40, 10);
            mc.gui.hud.setTitle(Component.literal("§cYou have been outbid!"));
            mc.gui.hud.setSubtitle(Component.empty());
            if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1f, 1.2f);
        }
        return ChatBus.PASS;
    }

    // skyhanni BazaarCancelledBuyOrderClipboard.onChat: line becomes clickable to order the same again, amount on
    // the clipboard
    private static Component cancelledOrder(String clean) {
        if (!b(BAZAAR, "market_bazaar.cancelClipboard", false) || BazaarExtras.latestAmount == null) return null;
        var m = BazaarApi.ORDER_CANCELLED.matcher(clean);
        if (!m.matches() || BazaarApi.orderOptionProduct == null) return null;
        int amount = BazaarExtras.latestAmount;
        String id = BazaarApi.orderOptionProduct, name = ItemRepo.name(id);
        BazaarExtras.latestAmount = null;
        Minecraft.getInstance().keyboardHandler.setClipboard(String.valueOf(amount));
        String plain = GameState.strip(name).trim();
        BazaarApi.onSearch(plain);
        return Component.literal(BazaarExtras.cancelledMessage(amount, name, m.group("coins"))).withStyle(s -> s.withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand("/bz " + plain))
                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(Component.literal("§eClick to order " + plain + " again"))));
    }

    // slot highlights (SlotOverlayMixin): searched bazaar item, order helper, manage auctions
    public static void slot(GuiGraphicsExtractor g, Slot slot) {
        if (!sb()) return;
        var mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.containerMenu instanceof ChestMenu) || slot.container == mc.player.getInventory()) return;
        ItemStack st = slot.getItem();
        if (st.isEmpty()) return;
        int idx = slot.getContainerSlot();
        Integer color = null;
        if (Features.on(BAZAAR)) {
            if (BazaarApi.highlightSearched(idx, GameState.strip(LegacyText.name(st)).trim()) && b(BAZAAR, "purchaseHelper", true)) color = 0x8055FF55;
            else if (b(BAZAAR, "orderHelper", true) && BazaarApi.isBazaarOrderInventory(lastMenuTitle)) color = BazaarApi.highlightSlot(idx);
        }
        if (color == null && Features.on(AUCTIONS) && b(AUCTIONS, "market_auctions.comparison", true) && Auctions.comparisonMenu(lastMenuTitle))
            color = Auctions.comparisonColor(idx, color("market_auctions.good", 0x8055FF55), color("market_auctions.veryGood", 0x80008B00), color("market_auctions.bad", 0x80FFFF55), color("market_auctions.veryBad", 0x80E12B1E));
        if (color == null) color = Cakes.color(lastMenuTitle, st);
        if (color == null && Features.on(AUCTIONS) && lastMenuTitle.equals("Manage Auctions") && b(AUCTIONS, "highlightAuctions", true)) {
            Auctions.highlightAuctionsUnderbid = b(AUCTIONS, "highlightAuctionsUnderbid", true);
            Auctions.soldColor = color("market_auctions.soldColor", Auctions.soldColor);
            Auctions.expiredColor = color("market_auctions.expiredColor", Auctions.expiredColor);
            Auctions.underbidColor = color("market_auctions.underbidColor", Auctions.underbidColor);
            Double est = null;
            if (Auctions.highlightAuctionsUnderbid) {
                var cd = st.get(DataComponents.CUSTOM_DATA);
                String id = Gain.sbId(st);
                if (cd != null && !id.isEmpty()) {
                    List<String> lore = LegacyText.lore(st);
                    boolean dungeon = lore.stream().anyMatch(l -> l.contains("DUNGEON "));
                    var r = EstimatedItemValue.calculate(cd.copyTag(), id, dungeon, lore);
                    if (r.totalPrice() != r.basePrice()) est = r.totalPrice();
                }
            }
            color = Auctions.highlightManageAuctions(LegacyText.lore(st), est);
        }
        if (color != null) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, color);
        io.github.zmuzy.sbc.item.tooltip.BitsHelper.slot(g, slot, idx);
        NpcTrade.slot(g, slot, st);
        BazaarOrders.Icon icon = Features.on(BAZAAR) && b(BAZAAR, "market_bazaar.orderIcons", true) ? icons.get(idx) : null;
        if (icon != null) g.text(mc.font, icon.text(), slot.x, slot.y, icon.color(), true);
    }

    public static void register() {
        BazaarApi.idFromName = n -> {
            String id = ItemRepo.idForName(n);
            return id != null ? id : Prices.idFromName(n);
        };
        BazaarApi.bazaarPrices = id -> {
            Prices.Product p = Prices.get(id);
            return p == null ? null : new double[]{p.buy(), p.sell()};
        };
        BazaarApi.taxRate = Store.I.bazaarTaxRate > 0 ? Store.I.bazaarTaxRate : 1.25;

        Features.add(new Feature(BAZAAR, "Menus", "Bazaar helpers", "Highlights the item you searched for in the results, marks your orders and where they stand, turns max into the biggest order in the amount sign, and beside the menu shows the daily limit, what your purse buys and what a recipe needs.", "SkyHanni: Bazaar purchase helper, order helper")
                .when(Market::sb).tooltip(Market::bazaarTooltip)
                .option(Option.bool("purchaseHelper", "Highlight searched item", "", true))
                .option(Option.bool("orderHelper", "Order helper", "Colour your orders in the order menu.", true))
                .option(Option.bool("market_bazaar.orderIcons", "Icons on orders", "In Your Bazaar Orders: a clock when an order is about to expire (red once it has), % when partly filled, a tick when filled.", true))
                .option(Option.bool("market_bazaar.orderTracker", "Your place in the list", "On Create Buy Order and Create Sell Offer: how much of each top price is yours.", true))
                .option(Option.bool("market_bazaar.max", "Max in the quantity sign", "Write max, m or x and a space in the amount sign: it becomes the biggest order you can make.", true))
                .option(Option.bool("market_bazaar.reorder", "Ctrl copies the unfilled amount", "Hold Ctrl when cancelling an order to copy what was not filled.", true))
                .option(Option.bool("market_bazaar.bestSellMethod", "Sell difference", "On a product page beside the menu: what selling everything you carry by offer would earn over selling it now.", true))
                .option(Option.bool("market_bazaar.dailyLimit", "Daily limit", "Beside the Bazaar: how far you are towards the 15 billion coins a day limit.", true))
                .option(Option.bool("market_bazaar.maxPurse", "Most your purse buys", "On a product page beside the menu: how many your purse buys by order and instantly.", true))
                .option(Option.bool("market_bazaar.craftMaterials", "Craft materials", "Beside a recipe: what it needs with prices, and a list to buy them from, at the amount you want.", true))
                .option(Option.bool("market_bazaar.hideOwner", "Hide order owner", "Hide the By line on your orders while all of them are yours.", false))
                .option(Option.bool("market_bazaar.hideTradePartners", "Hide trade partners", "Hide the players who traded with an order from its tooltip.", false))
                .option(Option.bool("market_bazaar.cancelClipboard", "Cancelled order clipboard", "After cancelling a buy order the missing amount is copied and the message can be clicked to order it again.", false))
                .option(Option.bool("market_bazaar.priceWebsite", "Price history button", "A button beside a product page that opens its price history in your browser.", false))
                .option(Option.bool("market_bazaar.quickQuantities", "Quick quantity buttons", "Buttons with your amounts, and the clipboard's short number, in the amount sign.", false))
                .option(Option.bool("market_bazaar.quickClose", "Close the sign with them", "Pressing one of those buttons also sends the sign.", false))
                .option(Option.integer("market_bazaar.quick1", "Quick amount 1", "", 28, 1, 71680))
                .option(Option.integer("market_bazaar.quick2", "Quick amount 2", "", 2240, 1, 71680))
                .option(Option.integer("market_bazaar.quick3", "Quick amount 3", "", 256, 1, 71680)));
        Features.add(new Feature(AUCTIONS, "Menus", "Auction House helpers", "In Manage Auctions marks sold (green), expired (red) and underbid (gold) auctions; a title when you are outbid; copy the underbid price of the hovered auction with /sbc copyunderbid (or of your item in Create BIN Auction).", "SkyHanni: Auctions highlighter, copy underbid price, outbid warning")
                .when(Market::sb)
                .option(Option.bool("highlightAuctions", "Highlight auctions", "", true))
                .option(Option.bool("highlightAuctionsUnderbid", "Highlight underbid", "Gold when the BIN is above the estimated value.", true))
                .option(Option.bool("auctionOutbid", "Outbid warning", "", true))
                .option(Option.bool("autoCopyUnderbidPrice", "Auto copy underbid price", "In Create BIN Auction, copy the market price minus one of your item.", false))
                .option(Option.color("market_auctions.soldColor", "Sold colour", "", 0x8055FF55))
                .option(Option.color("market_auctions.expiredColor", "Expired colour", "", 0x80FF5555))
                .option(Option.color("market_auctions.underbidColor", "Underbid colour", "", 0x80FFAA00))
                .option(Option.bool("market_auctions.comparison", "Price comparison", "Colours auctions by how far the asking price is under or over the estimated value, with the difference on the tooltip.", true))
                .option(Option.color("market_auctions.good", "Comparison: good", "", 0x8055FF55))
                .option(Option.color("market_auctions.veryGood", "Comparison: very good", "", 0x80008B00))
                .option(Option.color("market_auctions.bad", "Comparison: bad", "", 0x80FFFF55))
                .option(Option.color("market_auctions.veryBad", "Comparison: very bad", "", 0x80E12B1E)));
        Features.get(AUCTIONS).onTooltip = Market::auctionTooltip;
        BazaarApi.onTransaction = (type, coins) -> {
            if (BazaarExtras.onTransaction(type, coins)) {
                var p = Minecraft.getInstance().player;
                if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §eYou reached your daily trade limit in the bazaar!"));
            }
        };
        Cakes.register();
        ChatBus.register(ChatBus.OBSERVE + 23, "market", null, Market::onChat);
        Safe.endTick("Market.tick", Market::tick);
        Interactions.onSlotClick((screen, slot, slotId, button) -> {
            if (!sb() || !Features.on(BAZAAR) || slot == null) return false;
            // from skyhanni BazaarBestSellMethod.onSlotClick
            lastClickedId = Gain.sbId(slot.getItem());
            lastClickedCount = slot.getItem().getCount();
            nextCloseWillResetItem = false;
            if (slot.getItem().isEmpty()) return false;
            var mc = Minecraft.getInstance();
            if (slot.container == mc.player.getInventory()) return false;
            String name = GameState.strip(LegacyText.name(slot.getItem())).trim();
            if (BazaarApi.isBazaarOrderInventory(lastMenuTitle)) BazaarApi.onOrderSlotClick(name);
            // skyblocker ReorderHelper.onClickSlot: only copies, the click still goes through
            if (b(BAZAAR, "market_bazaar.reorder", true) && (slotId == 11 || slotId == 13) && slot.getItem().is(Items.DYED_TERRACOTTA.green())
                    && BazaarOrders.ORDER_OPTIONS.matcher(lastMenuTitle).find() && InputConstants.isKeyDown(InputConstants.KEY_LCONTROL)) {
                List<String> clean = new ArrayList<>();
                for (String l : LegacyText.lore(slot.getItem())) clean.add(GameState.strip(l));
                String amount = BazaarOrders.reorderAmount(slotId, clean);
                if (amount != null) mc.keyboardHandler.setClipboard(amount);
            }
            return false;
        });
        Safe.commands("Market.commands", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("copyunderbid").executes(c -> {
                    copyUnderbidHovered();
                    return 1;
                }))));
        io.github.zmuzy.sbc.core.Commands.describe("copyunderbid", "Copy the hovered auction's price minus one to the clipboard");
    }

    // skyhanni searchForBazaarItem, other features use it after sending /bz
    public static void searched(String displayName) {
        BazaarApi.onSearch(displayName);
    }

    // skyhanni HypixelData.noTrade: ironman, stranded or bingo
    public static boolean noTrade() {
        return GameState.noTrade();
    }

    public static String menuTitle() {
        return lastMenuTitle;
    }
}
