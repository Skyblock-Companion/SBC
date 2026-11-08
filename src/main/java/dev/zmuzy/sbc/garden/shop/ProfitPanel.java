// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.shop;

import dev.zmuzy.sbc.core.ItemIcons;
import dev.zmuzy.sbc.item.menus.MenuMarks;
import dev.zmuzy.sbc.ui.SideHelper;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// a shop's table next to the shop, skyhanni DisplayTableEntry + RenderableUtils.fillTable: best per what you
// pay first, hovering a line frames its slot
// skymart, anita and the starlyn sisters are all this one panel with different numbers
final class ProfitPanel implements SideHelper.Content {
    // one table line
    record Entry(String name, String id, String value, double sortBy, int slot, List<String> tip) {}

    private static final int ROW = 12;
    private static int framed = -1;

    private final String title, heading, emptyText;
    private final ItemStack icon;
    private final int accent;
    private List<Entry> entries = List.of();

    ProfitPanel(String title, String heading, ItemStack icon, int accent, String emptyText) {
        this.title = title;
        this.heading = heading;
        this.icon = icon;
        this.accent = accent;
        this.emptyText = emptyText;
    }

    // what a rule read from the shop
    void set(List<Entry> list) {
        List<Entry> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparingDouble(Entry::sortBy).reversed());
        entries = sorted;
    }

    List<Entry> entries() {
        return entries;
    }

    // slot to frame, for the rule
    static int framed() {
        return framed;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public ItemStack icon() {
        return icon;
    }

    @Override
    public int accent() {
        return accent;
    }

    @Override
    public String[] tabs() {
        return new String[]{heading};
    }

    @Override
    public void refresh(AbstractContainerScreen<?> screen) {
        MenuMarks.current();
    }

    @Override
    public int summary(Ui ui, int x, int y, int w) {
        return 0;
    }

    @Override
    public int list(Ui ui, int tab, int x, int y, int w) {
        int y0 = y, hovered = -1;
        for (Entry e : entries) {
            boolean hover = ui.hover(x, y, w, ROW);
            if (hover) {
                ui.rect(x, y, w, ROW, 0x22FFFFFF);
                ui.tooltip(e.tip().toArray(new String[0]));
                hovered = e.slot();
            }
            if (e.id() != null) ui.icon(ItemIcons.of(e.id()), x, y + 1, 0.625f);
            ui.textShadow(e.value(), x + w - ui.width(e.value()), y + 2, Modern.INK);
            ui.textShadow(ui.ellipsize(e.name(), w - 15 - ui.width(e.value())), x + 12, y + 2, Modern.INK);
            y += ROW;
        }
        if (entries.isEmpty()) y += SideHelper.emptyText(ui, emptyText, x, y, w);
        if (hovered != framed) {
            framed = hovered;
            MenuMarks.invalidate();
        }
        return y - y0;
    }
}
