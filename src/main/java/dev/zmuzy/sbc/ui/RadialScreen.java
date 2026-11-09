// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

// any hypixel menu as a wheel. real entries on a ring, hovered one named in the middle, click = the exact
// click on that slot
// the chest stays open underneath
public final class RadialScreen extends Screen {
    private static final int TILE = 28;

    private final Ui ui = new Ui();
    private final Screen parent;
    private final ChestMenu menu;
    private final String title;

    record Spot(int slot, ItemStack stack, int x, int y) {}

    public RadialScreen(Screen parent, ChestMenu menu, Component title) {
        super(title);
        this.parent = parent;
        this.menu = menu;
        this.title = GameState.strip(title.getString());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
    }

    // nothing, or the glass hypixel fills menus with
    static boolean filler(ItemStack st) {
        if (st.isEmpty()) return true;
        String n = GameState.strip(st.getHoverName().getString()).trim();
        String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
        return n.isEmpty() || (path.endsWith("glass_pane") && n.length() <= 1);
    }

    private List<Spot> spots() {
        List<Integer> entries = new ArrayList<>();
        List<ItemStack> stacks = new ArrayList<>();
        int n = Math.min(menu.getRowCount() * 9, menu.slots.size());
        for (int i = 0; i < n; i++) {
            if (filler(menu.slots.get(i).getItem())) continue;
            entries.add(i);
            stacks.add(menu.slots.get(i).getItem());
        }
        return place(entries, stacks, width / 2, height / 2);
    }

    // 2 rings around the middle
    static List<Spot> place(List<Integer> slots, List<ItemStack> stacks, int cx, int cy) {
        List<Spot> out = new ArrayList<>();
        int inner = Math.min(slots.size(), 10);
        int outer = slots.size() - inner;
        // classic names the entry in the hub so its ring is a bit wider
        double r1 = Math.max(62, inner * (TILE + 4) / (2 * Math.PI)), r2 = r1 + TILE + 12;
        for (int k = 0; k < slots.size(); k++) {
            boolean in = k < inner;
            int count = in ? inner : outer, idx = in ? k : k - inner;
            double ang = -Math.PI / 2 + idx * 2 * Math.PI / count + (in ? 0 : Math.PI / count);
            double r = in ? r1 : r2;
            out.add(new Spot(slots.get(k), stacks.get(k), (int) Math.round(cx + Math.cos(ang) * r), (int) Math.round(cy + Math.sin(ang) * r)));
        }
        return out;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        ui.begin(g, mouseX, mouseY);
        ui.rect(0, 0, width, height, 0xAA060810);
        List<Spot> spots = spots();
        Spot hot = paintWheel(ui, width, height, title, spots);
        if (hot != null && minecraft.level != null && minecraft.player != null)
            ui.tooltip.add(new ArrayList<>(hot.stack.getTooltipLines(net.minecraft.world.item.Item.TooltipContext.of(minecraft.level), minecraft.player,
                    net.minecraft.world.item.TooltipFlag.NORMAL)));
        ui.flushTooltip();
    }

    private static boolean over(Ui ui, Spot s) {
        return (ui.mx - s.x) * (ui.mx - s.x) + (ui.my - s.y) * (ui.my - s.y) < (TILE / 2) * (TILE / 2);
    }

    // first sentence of the lore. hypixel wraps at ~30 chars so it runs to the first blank line
    private static String firstLore(ItemStack st) {
        var lore = st.get(DataComponents.LORE);
        if (lore == null) return "";
        StringBuilder out = new StringBuilder();
        for (Component c : lore.lines()) {
            String l = GameState.strip(c.getString()).trim();
            if (l.isEmpty()) {
                if (!out.isEmpty()) break;
                continue;
            }
            if (!out.isEmpty()) out.append(' ');
            out.append(l);
        }
        return out.toString();
    }

    private static void center(Ui ui, String s, int cx, int y, int c) {
        ui.textShadow(s, cx - ui.width(s) / 2, y, c);
    }

    // max lines, leftover gets an ellipsis on the last one
    private static List<String> lines(Ui ui, String s, int w, int max) {
        List<String> out = new ArrayList<>();
        String[] words = s.split(" ");
        String cur = "";
        for (int i = 0; i < words.length; i++) {
            String next = cur.isEmpty() ? words[i] : cur + " " + words[i];
            if (ui.width(next) <= w) {
                cur = next;
                continue;
            }
            if (out.size() == max - 1) {
                out.add(ui.ellipsize(next, w));
                return out;
            }
            if (!cur.isEmpty()) out.add(cur);
            cur = ui.ellipsize(words[i], w);
        }
        if (!cur.isEmpty()) out.add(cur);
        return out;
    }

    private static void orbit(Ui ui, int cx, int cy, int r) {
        int dots = (int) (2 * Math.PI * r / 4);
        for (int i = 0; i < dots; i++) {
            double a = 2 * Math.PI * i / dots;
            ui.rect((int) Math.round(cx + Math.cos(a) * r), (int) Math.round(cy + Math.sin(a) * r), 1, 1, 0x40FFFFFF);
        }
    }

    // slate hub, round tiles ringed in their own color, a spoke to the hovered one
    static Spot paintWheel(Ui ui, int width, int height, String title, List<Spot> spots) {
        int cx = width / 2, cy = height / 2, hub = 46, textW = 2 * hub - 8;
        Spot hot = null;
        for (Spot s : spots) if (over(ui, s)) hot = s;
        int hotColor = hot == null ? Modern.GOLD : ItemList.colorOf(hot.stack.getHoverName());
        int accent = hot != null && (hotColor & 0xFFFFFF) == 0xFFFFFF ? Modern.AQUA : hotColor;

        // rings the entries sit on
        if (!spots.isEmpty()) {
            double outerR = 0, innerR = Double.MAX_VALUE;
            for (Spot s : spots) {
                double r = Math.sqrt((s.x - cx) * (s.x - cx) + (s.y - cy) * (s.y - cy));
                outerR = Math.max(outerR, r);
                innerR = Math.min(innerR, r);
            }
            orbit(ui, cx, cy, (int) Math.round(innerR));
            if (outerR - innerR > TILE) orbit(ui, cx, cy, (int) Math.round(outerR));
        }
        ui.disc(cx, cy, hub + 1, Ui.alpha(accent, hot == null ? 0.6f : 0.95f));
        ui.disc(cx, cy, hub, 0xF40E141D);
        if (hot != null) {
            // hub edge to tile edge, one dot at a time
            double dx = hot.x - cx, dy = hot.y - cy, len = Math.max(1, Math.sqrt(dx * dx + dy * dy));
            for (double d = hub + 3; d < len - TILE / 2.0 - 1; d += 3)
                ui.rect((int) Math.round(cx + dx / len * d), (int) Math.round(cy + dy / len * d), 2, 2, accent);
        }
        for (Spot s : spots) {
            boolean hv = s == hot;
            int c = ItemList.colorOf(s.stack.getHoverName());
            ui.disc(s.x, s.y, TILE / 2 + (hv ? 2 : 1), hv ? accent : Ui.alpha(c, 0.35f));
            ui.disc(s.x, s.y, TILE / 2, hv ? Ui.lerpColor(0xF8121924, accent | 0xF8000000, 0.3f) : 0xEC121924);
            ui.icon(s.stack, s.x - 8, s.y - 8, 1f);
            if (s.stack.getCount() > 1) {
                String n = String.valueOf(s.stack.getCount());
                ui.textShadow(n, s.x + 9 - ui.width(n), s.y + 2, Modern.INK);
            }
        }

        if (hot != null) {
            // hub fits 4 lines, the tooltip next to it has the rest
            List<String> name = lines(ui, GameState.strip(hot.stack.getHoverName().getString()).trim(), textW, 2);
            String first = firstLore(hot.stack);
            List<String> lore = first.isEmpty() ? List.of() : lines(ui, first, textW, 4 - name.size());
            int y = cy - ((name.size() + lore.size()) * Modern.ROW) / 2;
            for (String l : name) {
                center(ui, l, cx, y, hotColor);
                y += Modern.ROW;
            }
            for (String l : lore) {
                center(ui, l, cx, y, Modern.LABEL);
                y += Modern.ROW;
            }
        } else if (spots.isEmpty()) {
            center(ui, "Nothing to show", cx, cy - 4, Modern.DIM);
        } else {
            List<String> name = lines(ui, title, textW, 2);
            int y = cy - ((name.size() + 1) * Modern.ROW) / 2;
            for (String l : name) {
                center(ui, l, cx, y, Modern.GOLD);
                y += Modern.ROW;
            }
            center(ui, "§f" + spots.size() + (spots.size() == 1 ? " §7entry" : " §7entries"), cx, y, Modern.LABEL);
        }

        String keys = "§eClick§7 choose  §eRight click§7 right-clicks it  §eEsc§7 back";
        int kw = ui.width(keys) + 12, kx = cx - kw / 2, ky = height - 18;
        Theme.current().panel(ui, kx, ky, kw, 14);
        ui.textShadow(keys, kx + 6, ky + 3, Modern.LABEL);
        return hot;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        for (Spot s : spots()) {
            if ((e.x() - s.x) * (e.x() - s.x) + (e.y() - s.y) * (e.y() - s.y) >= (TILE / 2) * (TILE / 2)) continue;
            if (minecraft.gameMode == null || minecraft.player == null) return true;
            minecraft.player.playSound(SoundEvents.UI_BUTTON_CLICK.value(), 0.35f, 1.25f);
            minecraft.gameMode.handleContainerInput(menu.containerId, s.slot, e.button() == InputConstants.MOUSE_BUTTON_RIGHT ? 1 : 0, ContainerInput.PICKUP, minecraft.player);
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == InputConstants.KEY_ESCAPE || (dev.zmuzy.sbc.SbcMod.radialKey != null && dev.zmuzy.sbc.SbcMod.radialKey.matches(e))) {
            minecraft.gui.setScreen(parent);
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
