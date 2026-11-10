// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemInfo;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.core.SbTime;
import io.github.zmuzy.sbc.nav.Navigator;
import io.github.zmuzy.sbc.tasks.Mission;
import io.github.zmuzy.sbc.web.WebPage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

// item card: the real tooltip, live prices, buttons for bz / ah (or walk there without a cookie) and the
// wiki, recipes as item slots
public final class ItemScreen extends Screen {
    private final Ui ui = new Ui();
    private final Screen parent;
    private final String id;
    private float scale = 1f;
    private int loreScroll, tab, page;

    public ItemScreen(Screen parent, String id) {
        super(Component.literal(ItemRepo.name(id)));
        this.parent = parent;
        this.id = id;
    }

    public static void open(String id) {
        open(id, false);
    }

    // usage = open on the "used in" list
    public static void open(String id, boolean usage) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        ItemScreen s = new ItemScreen(mc.gui.screen(), id);
        s.tab = usage ? 1 : 0;
        mc.gui.setScreen(s);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int w = 460, h = 280;
        scale = Math.min(1f, Math.min(width / (w + 20f), height / (h + 20f)));
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        ui.begin(g, Math.round(mouseX / scale), Math.round(mouseY / scale), mouseX, mouseY);
        int W = Math.round(width / scale), H = Math.round(height / scale);
        ui.rect(0, 0, W, H, 0x99060810);
        int x = W / 2 - w / 2, y = H / 2 - h / 2;
        ui.panel(x, y, w, h, 10, Ui.alpha(Ui.PANEL, 0.97f));
        ui.textLimit = x + w - 8;
        ui.roundGradH(x, y, w, 3, 2, Ui.VIOLET, Ui.CYAN);

        // header: icon, name, buttons
        ui.icon(ItemInfo.stack(id), x + 12, y + 12, 2f);
        ui.textScaled(ItemRepo.coloredName(id), x + 52, y + 14, 1.4f, Ui.TEXT, true);
        ui.text(id, x + 52, y + 30, Ui.DIM);
        int bx = x + 52, by = y + 44;
        boolean cookie = GameState.hasCookie();
        String name = ItemRepo.name(id);
        List<ItemInfo.Shop> shops = ItemInfo.shops(id);
        if (ItemInfo.onBazaar(id))
            bx += button("ib_bz", bx, by, cookie ? "Bazaar" : "Go to Bazaar", () -> {
                if (cookie) Mission.run("bz " + name);
                else goTo("HUB", "Bazaar");
                onClose();
            });
        else
            bx += button("ib_ah", bx, by, cookie ? "Auction House" : "Go to Auction House", () -> {
                if (cookie) Mission.run("ahs " + name);
                else goTo("HUB", "Auction House", "Auction Master", "Auctioneer");
                onClose();
            });
        bx += button("ib_wiki", bx, by, "Wiki", () -> minecraft.gui.setScreen(new BrowserScreen(this, WebPage.WIKI + "/w/" + name.replace(' ', '_'))));
        // hypixels own recipe viewer (/viewrecipe <id>), supercraft button included
        bx += button("ib_recipe", bx, by, "Recipe", () -> {
            Mission.run("viewrecipe " + id);
            onClose();
        });
        if (!shops.isEmpty()) {
            String npc = titleCase(shops.get(0).npc());
            bx += button("ib_nav", bx, by, "Navigate", () -> {
                String msg = io.github.zmuzy.sbc.nav.Destinations.go(npc);
                if (minecraft.player != null) minecraft.player.sendSystemMessage(Component.literal("\u00a7b[SBC] \u00a7f" + (msg == null ? "No known path to " + npc : msg + " - press J to warp")));
                onClose();
            });
        }
        button("ib_back", x + w - 70, y + 12, "‹ Back", this::onClose);

        // left: the real tooltip
        int ly = y + 74, colW = 220;
        List<String> tip = ItemInfo.tooltip(id);
        int visible = (h - 84) / 10;
        loreScroll = Math.max(0, Math.min(loreScroll, Math.max(0, tip.size() - visible)));
        int cardH = Math.min(tip.size(), visible) * 10 + 10;
        drawTooltipBox(x + 12, ly, colW, cardH);
        for (int i = 0; i < Math.min(tip.size() - loreScroll, visible); i++)
            ui.textShadow(ui.ellipsize(tip.get(loreScroll + i), colW - 12), x + 18, ly + 5 + i * 10, 0xFFAAAAAA);
        if (tip.size() > visible) ui.text("scroll for more", x + 12, ly + cardH + 2, Ui.DIM);

        // right: prices + recipes
        int rx = x + 12 + colW + 14, ry = ly, rw = w - (rx - x) - 12;
        ui.text("Prices", rx, ry, Ui.CYAN);
        ry += 12;
        List<String> prices = ItemInfo.priceLines(id);
        if (prices.isEmpty()) prices = List.of("§8No live price (not traded)");
        for (String p : prices) {
            ui.text(ui.ellipsize(p, rw), rx, ry, Ui.TEXT);
            ry += 11;
        }
        ry += 6;
        List<ItemInfo.Usage> uses = ItemInfo.usages(id);
        List<ItemInfo.Recipe> recipes = ItemInfo.recipes(id);
        List<ItemInfo.KatStep> kat = ItemInfo.kat(id);
        List<ItemInfo.Drop> drops = ItemInfo.drops(id);
        String[] labels = {"Recipes" + count(recipes.size()), "Used in" + count(uses.size()), "Shop" + count(shops.size()), "Kat" + count(kat.size()),
                "Dropped by" + count(drops.size())};
        boolean[] have = {true, true, !shops.isEmpty(), !kat.isEmpty(), !drops.isEmpty()};
        if (!have[tab]) tab = 0;
        int tx = rx;
        for (int i = 0; i < labels.length; i++) {
            if (!have[i]) continue;
            int bw = ui.width(labels[i]) + 16;
            if (tx + bw > rx + rw && tx > rx) {
                tx = rx;
                ry += 22;
            }
            final int t = i;
            tx += button("ib_tab" + i, tx, ry, labels[i], () -> {
                tab = t;
                page = 0;
            });
        }
        ry += 24;
        if (tab == 0) {
            if (recipes.isEmpty()) ui.text("No crafting or Forge recipe", rx, ry, Ui.DIM);
            int per = 2;
            page = Math.max(0, Math.min(page, (recipes.size() - 1) / per));
            for (ItemInfo.Recipe r : recipes.stream().skip((long) page * per).limit(per).toList()) {
                ry = drawRecipe(r, rx, ry, rw) + 8;
                if (ry > y + h - 40) break;
            }
            if (recipes.size() > per) pager(rx + rw - 90, y + h - 24, (recipes.size() - 1) / per);
        } else if (tab >= 2) {
            drawRows(shops, kat, drops, rx, ry, rw, y + h - 30);
        } else {
            if (uses.isEmpty()) ui.text("Not an ingredient of anything known", rx, ry, Ui.DIM);
            int s = 22, cols = Math.max(1, rw / (s + 2)), rows = Math.max(1, (y + h - 40 - ry) / (s + 2)), per = cols * rows;
            int pages = Math.max(1, (uses.size() + per - 1) / per);
            page = Math.max(0, Math.min(page, pages - 1));
            for (int i = page * per; i < Math.min(uses.size(), (page + 1) * per); i++) {
                int k = i - page * per;
                ItemInfo.Usage u = uses.get(i);
                slot(u.result(), u.recipe().resultCount(), rx + (k % cols) * (s + 2), ry + (k / cols) * (s + 2), s);
            }
            if (pages > 1) pager(rx + rw - 90, y + h - 24, pages - 1);
        }
        g.pose().popMatrix();
        ui.flushTooltip();
    }

    private static String pct(double chance) {
        String t = String.format(java.util.Locale.ROOT, "%.2f", chance * 100);
        if (t.contains(".")) t = t.replaceAll("0+$", "").replaceAll("[.]$", "");
        return t + "%";
    }

    private static String count(int n) {
        return n == 0 ? "" : " (" + n + ")";
    }

    private static String titleCase(String s) {
        StringBuilder sb = new StringBuilder();
        for (String w : s.toLowerCase(java.util.Locale.ROOT).replace("_npc", "").split("_")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    private static final java.util.Map<String, Integer> TIER_COLOR = java.util.Map.of("COMMON", 0xFFFFFFFF, "UNCOMMON", 0xFF55FF55, "RARE", 0xFF5555FF,
            "EPIC", 0xFFAA00AA, "LEGENDARY", 0xFFFFAA00, "MYTHIC", 0xFFFF55FF);

    // shop offers, kat upgrades or drop sources as short rows
    private void drawRows(List<ItemInfo.Shop> shops, List<ItemInfo.KatStep> kat, List<ItemInfo.Drop> drops, int x, int y, int w, int bottom) {
        int rowH = 24;
        int total = tab == 2 ? shops.size() : tab == 3 ? kat.size() : drops.size();
        int per = Math.max(1, (bottom - y) / rowH);
        int pages = Math.max(1, (total + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = page * per; i < Math.min(total, (page + 1) * per); i++) {
            int ry = y + (i - page * per) * rowH;
            ui.round(x, ry, w, rowH - 2, 3, 0xFF1A1F33);
            if (tab == 2) {
                var s = shops.get(i);
                ui.text(ui.ellipsize(titleCase(s.npc()), 78), x + 5, ry + 7, Ui.TEXT);
                int cx = x + 86;
                for (ItemInfo.Cost c : s.costs()) {
                    if (c.itemId() != null) {
                        slot(c.itemId(), c.count(), cx, ry + 1, 20);
                        cx += 22;
                    } else {
                        String t = Prices.coins(c.count()) + " " + (c.currency().equals("COIN") ? "coins" : titleCase(c.currency()));
                        ui.text(t, cx, ry + 7, Ui.GOLD);
                        cx += ui.width(t) + 6;
                    }
                }
                if (s.resultCount() > 1) ui.text("x" + s.resultCount(), x + w - 6 - ui.width("x" + s.resultCount()), ry + 7, Ui.MUTED);
            } else if (tab == 3) {
                var k = kat.get(i);
                // tiers on top, cost under them, items on the right: all inside the row
                int itemsW = k.items().size() * 22, room = Math.max(40, w - 12 - itemsW);
                String from = titleCase(k.from()), to = titleCase(k.to());
                int cx = x + 5;
                ui.text(ui.ellipsize(from, room / 2 - 8), cx, ry + 3, TIER_COLOR.getOrDefault(k.from(), Ui.TEXT));
                cx += Math.min(ui.width(from), room / 2 - 8);
                ui.text(" > ", cx, ry + 3, Ui.DIM);
                cx += ui.width(" > ");
                ui.text(ui.ellipsize(to, x + 5 + room - cx), cx, ry + 3, TIER_COLOR.getOrDefault(k.to(), Ui.TEXT));
                String t = Prices.coins(k.coins()) + " coins, " + SbTime.dur(k.seconds() * 1000);
                ui.text(ui.ellipsize(t, room), x + 5, ry + 12, Ui.GOLD);
                int ix = x + w - 3 - itemsW;
                for (ItemInfo.Part p : k.items()) {
                    slot(p.id(), p.count(), ix, ry + 1, 20);
                    ix += 22;
                }
            } else {
                var d = drops.get(i);
                ui.text(ui.ellipsize(d.mob(), w - 150), x + 5, ry + 7, Ui.TEXT);
                String amount = d.min() == d.max() ? "x" + d.min() : "x" + d.min() + "-" + d.max();
                String chance = d.chance() >= 0.01 ? pct(d.chance()) : "1 in " + Prices.coins(Math.round(1 / d.chance()));
                ui.text(amount, x + w - 140, ry + 7, Ui.MUTED);
                ui.text(chance, x + w - 6 - ui.width(chance), ry + 7, Ui.GOLD);
            }
        }
        if (total == 0) ui.text("Nothing known", x, y, Ui.DIM);
        if (pages > 1) pager(x + w - 90, bottom + 6, pages - 1);
    }

    private void pager(int x, int y, int last) {
        ui.button("ib_prev", x, y, 24, 16, "‹", Ui.GHOST, () -> page = Math.max(0, page - 1));
        ui.textCenter((page + 1) + "/" + (last + 1), x + 45, y + 4, Ui.MUTED);
        ui.button("ib_next", x + 66, y, 24, 16, "›", Ui.GHOST, () -> page = Math.min(last, page + 1));
    }

    private int button(String key, int x, int y, String label, Runnable r) {
        int bw = ui.width(label) + 16;
        ui.button(key, x, y, bw, 18, label, Ui.SOFT, r);
        return bw + 6;
    }

    private void goTo(String island, String... nodes) {
        for (String n : nodes) if (Navigator.navigateTo(island, n)) break;
        if (!island.equals(GameState.island)) Mission.run("warp " + island.toLowerCase());
    }

    private void drawTooltipBox(int x, int y, int w, int h) {
        ui.rect(x + 1, y, w - 2, h, 0xF0100010);
        ui.rect(x, y + 1, w, h - 2, 0xF0100010);
        ui.gradV(x + 1, y + 1, 1, h - 2, 0x505000FF, 0x5028007F);
        ui.gradV(x + w - 2, y + 1, 1, h - 2, 0x505000FF, 0x5028007F);
        ui.rect(x + 1, y + 1, w - 2, 1, 0x505000FF);
        ui.rect(x + 1, y + h - 2, w - 2, 1, 0x5028007F);
    }

    // 3x3 for crafting, a row for the forge
    private int drawRecipe(ItemInfo.Recipe r, int x, int y, int w) {
        boolean forge = r.type().equals("forge");
        ui.text(forge ? "Forge recipe · " + SbTime.dur(r.seconds() * 1000) + (r.coins() > 0 ? " · " + Prices.coins(r.coins()) + " coins" : "")
                : "Crafting recipe", x, y, Ui.MUTED);
        y += 12;
        int s = 20;
        double cost = r.coins();
        if (forge) {
            int cx = x;
            for (ItemInfo.Part p : r.parts()) {
                if (cx + s > x + w - 40) {
                    cx = x;
                    y += s + 2;
                }
                slot(p.id(), p.count(), cx, y, s);
                cost += io.github.zmuzy.sbc.core.AuctionPrices.value(p.id()) * p.count();
                cx += s + 2;
            }
            ui.text("→", cx + 4, y + 6, Ui.TEXT);
            slot(id, r.resultCount(), cx + 16, y, s);
            y += s + 4;
        } else {
            for (int i = 0; i < 9; i++) ui.round(x + (i % 3) * (s + 2), y + (i / 3) * (s + 2), s, s, 3, 0xFF1A1F33);
            for (ItemInfo.Part p : r.parts()) {
                slot(p.id(), p.count(), x + (p.slot() % 3) * (s + 2), y + (p.slot() / 3) * (s + 2), s);
                cost += io.github.zmuzy.sbc.core.AuctionPrices.value(p.id()) * p.count();
            }
            ui.text("→", x + 3 * (s + 2) + 6, y + s + 8, Ui.TEXT);
            slot(id, r.resultCount(), x + 3 * (s + 2) + 18, y + s + 2, s);
            y += 3 * (s + 2) + 2;
        }
        if (cost > 0) {
            ui.text("Ingredients cost ≈ " + Prices.coins(cost), x, y, Ui.GOLD);
            y += 11;
        }
        return y;
    }

    private void slot(String itemId, int count, int x, int y, int s) {
        boolean hv = ui.hover(x, y, s, s);
        ui.round(x, y, s, s, 3, hv ? 0xFF3C3A36 : 0xFF2A2927);
        ui.icon(ItemInfo.stack(itemId), x + 2, y + 2, 1f);
        if (count > 1) {
            String c = String.valueOf(count);
            ui.textShadow(c, x + s - 1 - ui.width(c), y + s - 9, 0xFFFFFFFF);
        }
        if (hv) {
            List<String> t = new java.util.ArrayList<>(ItemInfo.tooltip(itemId));
            if (!itemId.equals(id)) t.add("§eClick to open");
            ui.tooltip(t.toArray(new String[0]));
            if (!itemId.equals(id)) ui.click(x, y, s, s, () -> minecraft.gui.setScreen(new ItemScreen(this, itemId)));
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x() / scale, my = e.y() / scale;
        for (int i = ui.hits.size() - 1; i >= 0; i--) {
            Ui.Hit hit = ui.hits.get(i);
            if (mx < hit.x() || my < hit.y() || mx >= hit.x() + hit.w() || my >= hit.y() + hit.h() || hit.click() == null) continue;
            hit.click().run();
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        loreScroll -= (int) Math.signum(sy) * 2;
        return true;
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
