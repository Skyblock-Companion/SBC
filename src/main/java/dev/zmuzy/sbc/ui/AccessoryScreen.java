// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.core.AuctionPrices;
import dev.zmuzy.sbc.core.ItemInfo;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Profile;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.core.GameState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// accessory bag helper on top of hypixels bag view: every family, what tier you have, top tier, cost and how
// much MP it adds. sorted by value
public final class AccessoryScreen extends Screen {
    private static final int[] MP = {3, 5, 8, 12, 16, 22, 3, 5};
    private static final int[] RCOL = {0xFFFFFFFF, 0xFF55FF55, 0xFF5555FF, 0xFFAA00AA, 0xFFFFAA00, 0xFFFF55FF, 0xFFFF5555, 0xFFFF5555};

    private record Tier(String id, String name, int rarity) {}

    private record Chain(String root, List<Tier> tiers, int owned) {
        Tier top() {
            return tiers.get(tiers.size() - 1);
        }

        int ownedMp() {
            return owned < 0 ? 0 : MP[tiers.get(owned).rarity()];
        }

        int gain() {
            return MP[top().rarity()] - ownedMp();
        }
    }

    private final Ui ui = new Ui();
    private final ContainerScreen chest;
    private float scale = 1f, scroll, scrollTarget;
    private int contentH, viewH, tab;
    private boolean hideUnbuyable = true, typing;
    private int sort;
    private String query = "";
    private net.minecraft.world.inventory.Slot hoveredSlot;
    private static final String[] SORTS = {"Coins per MP", "Price", "MP gained", "Rarity", "Name"};
    private List<Chain> chains;
    private int countedMp, ownedCount;

    public AccessoryScreen(ContainerScreen chest) {
        super(Component.literal("Accessory Bag"));
        this.chest = chest;
    }

    private void build() {
        Profile.Data d = Profile.current;
        Set<String> owned = new HashSet<>(dev.zmuzy.sbc.core.game.GameProfile.ownedAccessories(d));
        // the open bag is newer than the profile, it might not be read in yet
        if (d != null && d.fromGame && chest != null) {
            var bag = chest.getMenu();
            for (int i = 0; i < bag.getRowCount() * 9 && i < bag.slots.size(); i++) {
                String id = dev.zmuzy.sbc.core.Gain.sbId(bag.slots.get(i).getItem());
                if (!id.isEmpty()) owned.add(id);
            }
        }
        List<Chain> out = new ArrayList<>();
        countedMp = 0;
        ownedCount = 0;
        for (dev.zmuzy.sbc.plan.AccessoryHelper.Family f : dev.zmuzy.sbc.plan.AccessoryHelper.families(ItemRepo.accessories(), owned)) {
            List<Tier> list = new ArrayList<>();
            for (dev.zmuzy.sbc.plan.AccessoryHelper.Tier t : f.tiers()) list.add(new Tier(t.id(), t.name(), t.rarity()));
            Chain c = new Chain(f.root(), list, f.owned());
            if (f.owned() >= 0) {
                countedMp += c.ownedMp();
                ownedCount++;
            }
            out.add(c);
        }
        chains = out;
    }

    private static double cost(String id) {
        double v = AuctionPrices.value(id);
        return v > 0 ? v : Prices.sell(id);
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
        if (chains == null || (chains.isEmpty() && !ItemRepo.accessories().isEmpty())) build();
        scale = Math.min(1.3f, Math.min(width / 900f, height / 480f));
        int W = Math.round(width / scale), H = Math.round(height / scale);
        g.pose().pushMatrix();
        g.pose().scale(scale, scale);
        ui.begin(g, Math.round(mouseX / scale), Math.round(mouseY / scale), mouseX, mouseY);
        DripKit.backdrop(ui, W, H, 1f);
        int pad = 34;
        DripKit.panel(ui, 12, 12, W - 24, H - 24);
        DripKit.plaque(ui, W / 2, 5, 220, 18, "ACCESSORY BAG");
        ui.textLimit = W - pad;

        Profile.Data d = Profile.current;
        int hy = pad;
        ui.textScaled("Magical Power", pad, hy + 2, 1.5f, DripKit.GOLD, true);
        ui.textScaled(String.valueOf(d != null && d.magicalPower > 0 ? d.magicalPower : countedMp), pad, hy + 18, 2f, DripKit.TEXT, true);
        ui.text(ownedCount + " families owned  ·  ~" + countedMp + " MP counted  ·  from your last profile load", pad + 90, hy + 22, DripKit.DIM);
        int rx = W - pad;
        int bw = 96;
        DripKit.button(ui, "ac_classic", rx - bw, hy + 4, bw, 20, "Classic bag", false, this::classic);
        rx -= bw + 8;
        DripKit.button(ui, "ac_close", rx - 28, hy + 4, 28, 20, "x", false, this::onClose);

        int ty = hy + 44;
        String[] names = {"Upgrades", "Missing", "Owned", "Bag"};
        int tx = pad;
        for (int i = 0; i < 4; i++) {
            String label = i == 3 ? "Bag - add / remove" : names[i] + " (" + count(i) + ")";
            int w = ui.width(label) + 28;
            final int idx = i;
            DripKit.button(ui, "ac_t" + i, tx, ty, w, 22, label, tab == i, () -> {
                tab = idx;
                scrollTarget = 0;
            });
            tx += w + 6;
        }
        int cx2 = W - pad;
        if (tab != 3) {
            cx2 -= 150;
            DripKit.button(ui, "ac_unb", cx2, ty, 150, 22, hideUnbuyable ? "Buyable only" : "Show everything", hideUnbuyable, () -> hideUnbuyable = !hideUnbuyable);
            cx2 -= 176;
            DripKit.button(ui, "ac_sort", cx2, ty, 170, 22, "Sort: " + SORTS[sort], false, () -> sort = (sort + 1) % SORTS.length);
        } else {
            for (String nav : new String[]{"Next Page", "Previous Page"}) {
                int slot = findSlot(nav);
                if (slot < 0) continue;
                cx2 -= 110;
                DripKit.button(ui, "ac_nav" + nav, cx2, ty, 104, 22, nav.startsWith("Next") ? "Next page ›" : "‹ Previous page", true, () -> clickSlot(slot, 0));
            }
        }
        int sbx = Math.max(tx + 6, cx2 - 190);
        if (sbx + 180 <= cx2 - 4 || tab == 3) {
            boolean sh = ui.hover(sbx, ty, 180, 22);
            DripKit.well(ui, sbx, ty, 180, 22);
            ui.click(sbx, ty, 180, 22, () -> typing = true);
            String shown = query.isEmpty() && !typing ? "Search..." : query + (typing && (System.currentTimeMillis() / 500) % 2 == 0 ? "|" : "");
            ui.text(ui.ellipsize(shown, 164), sbx + 8, ty + 7, query.isEmpty() && !typing ? DripKit.DIM : sh || typing ? DripKit.GOLD : DripKit.TEXT);
        }

        int top = ty + 32, bottom = H - pad - 14;
        viewH = bottom - top;
        scrollTarget = Math.max(0, Math.min(scrollTarget, Math.max(0, contentH - viewH)));
        scroll += (scrollTarget - scroll) * 0.35f;
        g.enableScissor(pad - 4, top, W - pad + 4, bottom);
        ui.clipX0 = pad - 4;
        ui.clipY0 = top;
        ui.clipX1 = W - pad + 4;
        ui.clipY1 = bottom;
        hoveredSlot = null;
        if (tab == 3) {
            net.minecraft.world.inventory.Slot[] hv = new net.minecraft.world.inventory.Slot[1];
            contentH = BagPane.draw(ui, chest.getMenu(), pad, top + 2 - Math.round(scroll), W - pad * 2, hv, this::clickSlot, query) + 8;
            hoveredSlot = hv[0];
        } else {
            contentH = rows(pad, top + 2 - Math.round(scroll), W - pad * 2) + 8;
        }
        g.disableScissor();
        ui.clipX0 = Integer.MIN_VALUE;
        ui.clipY0 = Integer.MIN_VALUE;
        ui.clipX1 = Integer.MAX_VALUE;
        ui.clipY1 = Integer.MAX_VALUE;
        if (contentH > viewH) {
            int barH = Math.max(20, viewH * viewH / contentH);
            int barY = top + (int) ((viewH - barH) * (scroll / Math.max(1, contentH - viewH)));
            ui.rect(W - pad + 8, top, 3, viewH, 0x22000000);
            ui.rect(W - pad + 8, barY, 3, barH, DripKit.GOLD_MID);
        }
        String hint = "Click a row: item card  ·  Sorted by coins per Magical Power  ·  Esc: close  ·  Tab: classic bag";
        ui.text(hint, W / 2 - ui.width(hint) / 2, H - 28, DripKit.DIM);
        ui.textLimit = Integer.MAX_VALUE;
        g.pose().popMatrix();
        if (hoveredSlot != null && !hoveredSlot.getItem().isEmpty()) g.setTooltipForNextFrame(font, hoveredSlot.getItem(), mouseX, mouseY);
        ui.flushTooltip();
    }

    private int findSlot(String prefix) {
        var m = chest.getMenu();
        for (int i = 0; i < Math.min(m.getRowCount() * 9, m.slots.size()); i++)
            if (dev.zmuzy.sbc.core.GameState.strip(m.slots.get(i).getItem().getHoverName().getString()).startsWith(prefix)) return i;
        return -1;
    }

    private boolean hasShiftDown() {
        long win = minecraft.getWindow().handle();
        return GLFW.glfwGetKey(win, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS || GLFW.glfwGetKey(win, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private void clickSlot(int slot, int button) {
        if (minecraft.gameMode == null || minecraft.player == null) return;
        minecraft.gameMode.handleContainerInput(chest.getMenu().containerId, slot, button,
                hasShiftDown() ? net.minecraft.world.inventory.ContainerInput.QUICK_MOVE : net.minecraft.world.inventory.ContainerInput.PICKUP, minecraft.player);
    }

    private int count(int t) {
        int n = 0;
        for (Chain c : chains) if (inTab(c, t)) n++;
        return n;
    }

    private boolean inTab(Chain c, int t) {
        return switch (t) {
            case 3 -> false;
            case 0 -> c.owned() >= 0 && c.owned() < c.tiers().size() - 1;
            case 1 -> c.owned() < 0;
            default -> c.owned() >= 0;
        };
    }

    private int rows(int x, int y, int w) {
        List<Chain> list = new ArrayList<>();
        for (Chain c : chains) {
            if (!inTab(c, tab)) continue;
            if (!query.isEmpty() && !c.top().name().toLowerCase(java.util.Locale.ROOT).contains(query.toLowerCase(java.util.Locale.ROOT))) continue;
            if (hideUnbuyable && tab != 2 && cost(c.top().id()) <= 0) continue;
            list.add(c);
        }
        final int t = tab, so = sort;
        list.sort(Comparator.comparingDouble((Chain c) -> {
            double cost = cost(c.top().id());
            return switch (so) {
                case 1 -> cost <= 0 ? 1e12 : cost;
                case 2 -> -c.gain();
                case 3 -> -c.top().rarity();
                case 4 -> 0;
                default -> t == 2 ? -c.ownedMp() : cost <= 0 ? 1e12 : cost / Math.max(1, c.gain());
            };
        }).thenComparing(c -> c.top().name()));
        if (list.isEmpty()) {
            ui.text(chains.isEmpty() ? "Loading the accessory list..." : "Nothing here.", x, y + 6, DripKit.DIM);
            return 24;
        }
        int rowH = 44, cy = y;
        for (Chain c : list) {
            Tier top = c.top();
            boolean over = ui.hover(x, cy, w, rowH - 4);
            DripKit.box(ui, x, cy, w, rowH - 4, over ? 1 : 0);
            if (over) {
                String id = top.id();
                ui.click(x, cy, w, rowH - 4, () -> ItemScreen.open(id));
            }
            int ix = x + 8;
            int from = 0, upTo = Math.min(4, c.tiers().size());
            for (int i = from; i < upTo; i++) {
                Tier tr = c.tiers().get(i);
                DripKit.slot(ui, ix, cy + 6, 28, ItemInfo.stack(tr.id()), false);
                if (i > c.owned()) ui.rect(ix, cy + 6, 28, 28, 0xAA000000);
                else if (i == c.owned()) ui.text("✔", ix + 20, cy + 4, DripKit.GREEN);
                ix += 32;
            }
            int nx = x + 8 + 4 * 32 + 8;
            ui.textShadow(ui.ellipsize(top.name(), 230), nx, cy + 8, over ? DripKit.GOLD : RCOL[top.rarity()]);
            StringBuilder path = new StringBuilder();
            for (Tier tr : c.tiers()) {
                String[] words = tr.name().split(" ");
                path.append(path.length() == 0 ? "" : " > ").append(words[words.length - 1]);
            }
            String sub = c.owned() < 0 ? (c.tiers().size() > 1 ? c.tiers().size() + " tiers: " + path : "Not owned") : "Owned: " + c.tiers().get(c.owned()).name();
            ui.text(ui.ellipsize(sub, 230), nx, cy + 22, DripKit.DIM);
            int px = nx + 250;
            double cost = cost(top.id());
            String price = cost > 0 ? Prices.coins(cost) + " coins" : "not for sale";
            ui.textShadow(price, px, cy + 8, cost > 0 ? DripKit.GOLD : DripKit.DIM);
            if (tab != 2 && cost > 0) {
                int next = c.owned() + 1;
                if (next < c.tiers().size() - 1) {
                    double nc = cost(c.tiers().get(next).id());
                    if (nc > 0) ui.text("next tier " + Prices.coins(nc), px, cy + 22, DripKit.DIM);
                }
            }
            int mx = px + 150;
            String mp = tab == 2 ? MP[top.rarity()] + " MP" : "+" + c.gain() + " MP";
            ui.textShadow(mp, mx, cy + 8, DripKit.GREEN);
            if (tab != 2 && cost > 0) ui.text(Prices.coins(cost / Math.max(1, c.gain())) + " per MP", mx, cy + 22, DripKit.DIM);
            cy += rowH;
        }
        return cy - y;
    }

    private void classic() {
        minecraft.gui.setScreen(chest);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        double mx = e.x() / scale, my = e.y() / scale;
        for (int i = ui.hits.size() - 1; i >= 0; i--) {
            Ui.Hit hit = ui.hits.get(i);
            if (mx < hit.x() || my < hit.y() || mx >= hit.x() + hit.w() || my >= hit.y() + hit.h()) continue;
            Runnable r = e.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && hit.right() != null ? hit.right() : hit.click();
            if (r == null) continue;
            typing = false;
            r.run();
            return true;
        }
        typing = false;
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double sx, double sy) {
        scrollTarget -= (float) (Math.signum(sy) * 60);
        return true;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent e) {
        if (!typing) return super.charTyped(e);
        String c = e.codepointAsString();
        if (!c.isEmpty() && c.charAt(0) >= 32 && query.length() < 24) query += c;
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (typing) {
            if (e.key() == GLFW.GLFW_KEY_BACKSPACE && !query.isEmpty()) query = query.substring(0, query.length() - 1);
            else if (e.key() == GLFW.GLFW_KEY_ENTER || e.key() == GLFW.GLFW_KEY_ESCAPE) typing = false;
            return true;
        }
        if (e.key() == GLFW.GLFW_KEY_TAB) {
            classic();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        if (minecraft.player != null) minecraft.player.closeContainer();
        else minecraft.gui.setScreen(null);
    }
}
