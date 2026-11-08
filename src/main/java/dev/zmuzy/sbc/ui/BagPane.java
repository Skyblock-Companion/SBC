// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.core.GameState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.function.BiConsumer;

// bag/chest as two grids, chest left inventory right. clicks work like the real thing (pick up, split, shift move)
final class BagPane {

    private BagPane() {
    }
    private static void heading(Ui ui, int x, int cx, int y, int w, int plaqueW, String shouty, String classic) {
        dev.zmuzy.sbc.ui.theme.Modern.section(ui, classic, x, y + 4, w, dev.zmuzy.sbc.ui.theme.Modern.AQUA);
    }

    private static void slot(Ui ui, int x, int y, int s, ItemStack st, boolean over) {
        ui.rect(x, y, s, s, over ? 0x60FFFFFF : 0x50000000);
        ui.rect(x, y, s, 1, 0x60000000);
        if (!st.isEmpty()) ui.icon(st, x + (s - 16) / 2, y + (s - 16) / 2, 1f);
    }

    static int draw(Ui ui, ChestMenu menu, int x, int y, int w, Slot[] hov, BiConsumer<Integer, Integer> click) {
        return draw(ui, menu, x, y, w, hov, click, "");
    }

    static int draw(Ui ui, ChestMenu menu, int x, int y, int w, Slot[] hov, BiConsumer<Integer, Integer> click, String filter) {
        int rows = menu.getRowCount(), s = 36, gap = 2, cell = s + gap;
        int gridW = 9 * cell - gap;
        boolean side = w >= gridW * 2 + 50;
        int bx = side ? x + (w - (gridW * 2 + 50)) / 2 : x + (w - gridW) / 2;
        int ix = side ? bx + gridW + 50 : bx;
        int top = y + 26;

        heading(ui, bx, bx + gridW / 2, y + 2, gridW, 150, "THE BAG", "The Bag");
        int bagBottom = grid(ui, menu, 0, rows * 9, bx, top, s, gap, hov, click, filter, true);

        int iy = side ? top : bagBottom + 34;
        heading(ui, ix, ix + gridW / 2, iy - 24, gridW, 170, "YOUR INVENTORY", "Your inventory");
        int base = rows * 9;
        int invBottom = grid(ui, menu, base, base + 27, ix, iy, s, gap, hov, click, filter, false);
        int hotbarY = invBottom + 8;
        int end = grid(ui, menu, base + 27, base + 36, ix, hotbarY, s, gap, hov, click, filter, false);

        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) {
            ui.icon(carried, ui.mx - 8, ui.my - 8, 1f);
            if (carried.getCount() > 1) ui.textShadow(String.valueOf(carried.getCount()), ui.mx + 4, ui.my + 2, 0xFFFFFFFF);
        }
        return Math.max(bagBottom, end) - y + 12;
    }

    // one block of slots, 9 a row, returns y under it
    private static int grid(Ui ui, ChestMenu menu, int from, int to, int x, int y, int s, int gap, Slot[] hov, BiConsumer<Integer, Integer> click, String filter, boolean bag) {
        int cell = s + gap, n = 0;
        String f = filter == null ? "" : filter.toLowerCase(Locale.ROOT);
        for (int i = from; i < Math.min(to, menu.slots.size()); i++, n++) {
            Slot slot = menu.slots.get(i);
            ItemStack st = slot.getItem();
            int sx = x + (n % 9) * cell, sy = y + (n / 9) * cell;
            boolean filler = bag && !st.isEmpty() && GameState.strip(st.getHoverName().getString()).isBlank();
            boolean over = ui.hover(sx, sy, s, s) && !filler;
            slot(ui, sx, sy, s, filler ? ItemStack.EMPTY : st, over);
            if (!st.isEmpty() && !filler) {
                if (st.getCount() > 1) {
                    String c = String.valueOf(st.getCount());
                    ui.textShadow(c, sx + s - 3 - ui.width(c), sy + s - 10, 0xFFFFFFFF);
                }
                if (!f.isEmpty() && !GameState.strip(st.getHoverName().getString()).toLowerCase(Locale.ROOT).contains(f)) ui.rect(sx, sy, s, s, 0xB0000000);
            }
            if (over) {
                hov[0] = slot;
                final int idx = i;
                ui.clickRight(sx, sy, s, s, () -> click.accept(idx, 0), () -> click.accept(idx, 1));
            }
        }
        return y + ((Math.max(1, n) + 8) / 9) * cell;
    }
}
