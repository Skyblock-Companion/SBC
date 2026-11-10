// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker, SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.visitor;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemIcons;
import io.github.zmuzy.sbc.core.LegacyItems;
import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.garden.GardenData;
import io.github.zmuzy.sbc.item.market.MoreMarket;
import io.github.zmuzy.sbc.ui.SideHelper;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// next to a visitor's menu: this offer's numbers, the shopping list of all visitors with a click to the
// bazaar (skyblocker VisitorHelper, skyhanni clickable shopping list), who is left out, the charmed ones
// everything it sends is bc of that click
public final class VisitorPanel implements SideHelper.Content {
    public static final VisitorPanel I = new VisitorPanel();
    private static final int ROW = 13;

    private List<VisitorCards.Need> needs = List.of();
    private long clickedAt;

    private VisitorPanel() {}

    private static boolean anyNeeds;
    private static long needsAt;

    // a visitors menu, or your inventory where the shopping card goes (skyhanni does that too)
    public static SideHelper.Content of(Screen s) {
        if (!Features.on(VisitorCards.SHOPPING)) return null;
        if (Visitors.open() != null) return I;
        if (!(s instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) || !VisitorCards.showHere()) return null;
        long now = System.currentTimeMillis();
        if (now - needsAt > 1000) {
            needsAt = now;
            anyNeeds = !VisitorCards.needs().isEmpty();
        }
        return anyNeeds ? I : null;
    }

    @Override
    public String title() {
        return "Bazaar Helper";
    }

    @Override
    public ItemStack icon() {
        return LegacyItems.stack("minecraft:emerald");
    }

    @Override
    public int accent() {
        return 0xFF7DD36B;
    }

    @Override
    public String[] tabs() {
        return new String[]{"To buy " + (MoreMarket.purchasing ? MoreMarket.buying.size() : 0), "Visitors", "Charmed " + GardenData.get().charmedVisitors.size()};
    }

    @Override
    public void refresh(AbstractContainerScreen<?> screen) {
        needs = VisitorCards.needs();
    }

    @Override
    public int summary(Ui ui, int x, int y, int w) {
        Visitors.Visitor v = Visitors.open();
        if (v == null || v.offer == null) return 0;
        int half = (w - 3) / 2;
        VisitorOffer o = v.offer;
        SideHelper.tile(ui, x, y, half, "Per copper", o.pricePerCopper == null ? "-" : Prices.coins(o.pricePerCopper), "", Modern.GOLD);
        SideHelper.tile(ui, x + half + 3, y, w - half - 3, "This offer", Prices.coins(o.totalPrice), "", Modern.GOLD);
        int cy = y + 25;
        if (v.blockReason != null) {
            ui.textShadow(ui.ellipsize(v.blockReason.description, w), x, cy + 1, Modern.INK);
            cy += 11;
        }
        for (VisitorReward r : v.warned()) {
            ui.textShadow(ui.ellipsize(r.displayName, w), x, cy + 1, Modern.INK);
            cy += 11;
        }
        return cy - y;
    }

    private static void send(String command) {
        var c = Minecraft.getInstance().getConnection();
        if (c != null) c.sendCommand(command);
    }

    // only one per click, whatever the screen does in between
    private boolean once() {
        long now = System.currentTimeMillis();
        if (now - clickedAt < 300) return false;
        clickedAt = now;
        return true;
    }

    private void buy(VisitorCards.Need n) {
        if (!once()) return;
        String name = GameState.strip(n.name());
        var mc = Minecraft.getInstance();
        mc.keyboardHandler.setClipboard(String.valueOf(n.amount()));
        if (io.github.zmuzy.sbc.item.market.Market.noTrade()) {
            send("viewrecipe " + n.id());
            return;
        }
        io.github.zmuzy.sbc.item.market.Market.searched(name);
        send("bz " + name);
    }

    private void recipe(VisitorCards.Need n) {
        if (!once()) return;
        Minecraft.getInstance().keyboardHandler.setClipboard(String.valueOf(n.craftable()));
        send("viewrecipe " + n.id());
    }

    @Override
    public int list(Ui ui, int tab, int x, int y, int w) {
        return switch (tab) {
            case 1 -> visitors(ui, x, y, w);
            case 2 -> charmed(ui, x, y, w);
            default -> shopping(ui, x, y, w);
        };
    }

    // same list as next to the bazaar, the sacks already cover the rest
    private int shopping(Ui ui, int x, int y, int w) {
        if (MoreMarket.purchasing) return io.github.zmuzy.sbc.ui.MarketPanel.buyList(ui, x, y, w);
        if (!needs.isEmpty()) {
            Modern.row(ui, "§aYour sacks cover all of it", "", x, y, w, Modern.GOOD, Modern.DIM);
            y += 12;
        }
        int y0 = y;
        Feature f = Features.get(VisitorCards.SHOPPING);
        boolean superCraft = f != null && f.option("garden_visitor_shopping.super_craft") != null && f.option("garden_visitor_shopping.super_craft").bool();
        for (VisitorCards.Need n : needs) {
            boolean hover = ui.hover(x, y, w, ROW);
            if (hover) ui.rect(x, y, w, ROW, 0x22FFFFFF);
            ui.icon(ItemIcons.of(n.id()), x, y + 1, 0.625f);
            String right = VisitorCards.stacks(n.amount());
            ui.textShadow(right, x + w - 1 - ui.width(right), y + 3, Modern.INK);
            String name = GameState.strip(n.name());
            ui.textShadow(ui.ellipsize(n.name(), w - 15 - ui.width(right)), x + 12, y + 3, Modern.INK);
            List<String> tip = new ArrayList<>(List.of("§f" + name, "§7Need: §e" + String.format(Locale.ROOT, "%,d", n.amount()), "§7Price: §6" + Prices.coins(n.price())));
            if (n.inSacks() != null) tip.add("§7In sacks: §e" + String.format(Locale.ROOT, "%,d", n.inSacks()));
            if (n.craftable() > 0) tip.add("§aCraftable: §e" + String.format(Locale.ROOT, "%,d", n.craftable()));
            tip.add("");
            tip.add(n.craftable() > 0 && superCraft ? "§eClick for the recipe; the amount is copied" : "§eClick to look it up; the amount is copied");
            if (hover) ui.tooltip(tip.toArray(new String[0]));
            final VisitorCards.Need need = n;
            ui.click(x, y, w, ROW, n.craftable() > 0 && superCraft ? () -> recipe(need) : () -> buy(need));
            y += ROW;
        }
        if (y == y0 && needs.isEmpty()) y += SideHelper.emptyText(ui, "Talk to a visitor to read what it wants.", x, y, w);
        return y - y0 + (needs.isEmpty() ? 0 : 12);
    }

    private int visitors(Ui ui, int x, int y, int w) {
        int y0 = y;
        for (Visitors.Visitor v : Visitors.all()) {
            boolean hover = ui.hover(x, y, w, 11);
            if (hover) {
                ui.rect(x, y, w, 11, 0x22FFFFFF);
                ui.tooltip(v.ignored() ? "§eClick to include this visitor in the shopping list." : "§eClick to ignore this visitor in the shopping list.");
            }
            String name = v.ignored() ? "§7§m" + v.name : VisitorRepo.colored(v.name);
            String status = v.status.display;
            ui.textShadow(status, x + w - 1 - ui.width(status), y + 1, Modern.LABEL);
            ui.textShadow(ui.ellipsize(name, w - 4 - ui.width(status)), x, y + 1, Modern.INK);
            final Visitors.Visitor visitor = v;
            ui.click(x, y, w, 11, () -> {
                if (once()) {
                    visitor.toggleIgnored();
                    needs = VisitorCards.needs();
                }
            });
            y += 11;
        }
        return y - y0;
    }

    private int charmed(Ui ui, int x, int y, int w) {
        int y0 = y;
        GardenData data = GardenData.get();
        for (String name : new ArrayList<>(data.charmedVisitors)) {
            boolean hover = ui.hover(x, y, w, 11);
            if (hover) {
                ui.rect(x, y, w, 11, 0x22FFFFFF);
                ui.tooltip("§eClick to remove from charmed list");
            }
            ui.textShadow(ui.ellipsize(VisitorRepo.colored(name), w), x, y + 1, Modern.INK);
            ui.click(x, y, w, 11, () -> {
                if (once() && data.charmedVisitors.remove(name)) Store.markDirty();
            });
            y += 11;
        }
        if (y == y0) y += SideHelper.emptyText(ui, "No charmed visitors.", x, y, w);
        return y - y0;
    }
}
