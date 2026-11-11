// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.areas;

import io.muzy.sbc.ui.SideHelper;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.theme.Modern;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// plain list next to a hypixel menu for this packs small panels: name + value per line, like the DungeonChests panel
final class AreaPanel implements SideHelper.Content {
    record Row(String name, String value, int color) {}

    private final String title, tab;
    private final java.util.function.Supplier<ItemStack> icon;
    private final int accent;
    private final Function<AbstractContainerScreen<?>, List<Row>> read;
    private List<Row> rows = new ArrayList<>();

    AreaPanel(String title, String tab, java.util.function.Supplier<ItemStack> icon, int accent, Function<AbstractContainerScreen<?>, List<Row>> read) {
        this.title = title;
        this.tab = tab;
        this.icon = icon;
        this.accent = accent;
        this.read = read;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public ItemStack icon() {
        return icon.get();
    }

    @Override
    public int accent() {
        return accent;
    }

    @Override
    public String[] tabs() {
        return new String[]{tab + " " + rows.size()};
    }

    @Override
    public void refresh(AbstractContainerScreen<?> screen) {
        rows = read.apply(screen);
    }

    @Override
    public int summary(Ui ui, int x, int y, int w) {
        return 0;
    }

    @Override
    public int list(Ui ui, int tab, int x, int y, int w) {
        int top = y;
        if (rows.isEmpty()) return SideHelper.emptyText(ui, "Nothing here yet.", x, y, w);
        for (Row r : rows) {
            int vw = ui.width(r.value());
            ui.textShadow(ui.ellipsize(r.name(), w - vw - 6), x + 2, y, Modern.INK);
            ui.textShadow(r.value(), x + w - 2 - vw, y, r.color());
            y += 11;
        }
        return y - top;
    }
}
