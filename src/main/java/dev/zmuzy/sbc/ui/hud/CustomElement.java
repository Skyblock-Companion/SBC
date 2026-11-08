// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui.hud;

import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

// element that draws itself (maps, cards, bars) instead of rows
public final class CustomElement extends HudElement {
    @Override
    public boolean supportsAlways() {
        return false;
    }

    @Override
    public boolean stretchable() {
        return false;
    }

    public interface Drawer {
        void draw(Ui ui, GuiGraphicsExtractor g, int x, int y);
    }

    private final BooleanSupplier visible;
    private final IntSupplier width, height;
    private final Drawer drawer;
    private final int demoW, demoH;

    public CustomElement(String id, String title, String section, Zone zone, int priority, String featureId,
                         BooleanSupplier visible, IntSupplier width, IntSupplier height, Drawer drawer, int demoW, int demoH) {
        super(id, title, section, zone, priority, featureId);
        this.visible = visible;
        this.width = width;
        this.height = height;
        this.drawer = drawer;
        this.demoW = demoW;
        this.demoH = demoH;
    }

    @Override
    public boolean visible() {
        return visible.getAsBoolean();
    }

    @Override
    public int width() {
        if (demo && !visible()) return demoW;
        int w = width.getAsInt();
        return w > 0 ? w : demoW;
    }

    @Override
    public int height() {
        if (demo && !visible()) return demoH;
        int h = height.getAsInt();
        return h > 0 ? h : demoH;
    }

    @Override
    public List<Row> demo() {
        return List.of();
    }

    @Override
    public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
        if (demo && !visible()) {
            // nothing live: themed box with the name so you can still place it
            var t = dev.zmuzy.sbc.ui.theme.Theme.current();
            t.panel(ui, x, y, demoW, demoH, style());
            int top = Modern.band(ui, title, icon(), "", accent(), x, y, demoW);
            String empty = "No data right now";
            ui.textShadow(ui.ellipsize(empty, demoW - 2 * Modern.PAD), x + (demoW - ui.width(empty)) / 2, y + top + (demoH - top - 8) / 2, Modern.DIM);
            return;
        }
        drawer.draw(ui, g, x, y);
    }
}
