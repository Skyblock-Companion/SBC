// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui.screens;

import io.muzy.sbc.ui.DripKit;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.theme.Theme;
import io.muzy.sbc.ui.layout.BaseUIScreen;
import io.muzy.sbc.ui.layout.LabelComponent;
import io.muzy.sbc.ui.layout.UIComponents;
import io.muzy.sbc.ui.layout.FlowLayout;
import io.muzy.sbc.ui.layout.UIContainers;
import io.muzy.sbc.ui.layout.Color;
import io.muzy.sbc.ui.layout.UIAdapter;
import io.muzy.sbc.ui.layout.Surface;
import net.minecraft.network.chat.Component;

// base of every component screen: see through backdrop + themed panel (flat dark with a thin outline).
// screens only build their content
public abstract class SbcScreen extends BaseUIScreen<FlowLayout> {
    protected final net.minecraft.client.gui.screens.Screen parent;

    protected SbcScreen(String title, net.minecraft.client.gui.screens.Screen parent) {
        super(Component.literal(title));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected UIAdapter<FlowLayout> createAdapter() {
        return UIAdapter.create(this, UIContainers::verticalFlow);
    }

    public static Surface backdrop() {
        return Surface.flat(0x90000000);
    }

    // main panel in the current theme
    public static Surface panel() {
        return (g, c) -> {
            int x = c.x(), y = c.y(), w = c.width(), h = c.height();
            io.muzy.sbc.ui.screens.SbcGradient.rect(g, x, y, w, h, 0xF00D131C, 0xF00D131C, 0xF0080C12, 0xF0080C12);
            g.drawRectOutline(x, y, w, h, 0x38FFFFFF);
            g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x24FFFFFF);
        };
    }

    // card inside the panel
    public static Surface card() {
        return glass(0, false);
    }

    // classic card as a surface: slate glass, soft corners, lit top. with band = title band in the card color
    // across the top. the title is the first child
    public static Surface glass(int accent, boolean band) {
        int a = accent | 0xFF000000;
        return (g, c) -> {
            int x = c.x(), y = c.y(), w = c.width(), h = c.height();
            int top = 0xEC1C2735, bottom = 0xEC121A25;
            io.muzy.sbc.ui.screens.SbcGradient.rect(g, x + 1, y, w - 2, h, top, top, bottom, bottom);
            io.muzy.sbc.ui.screens.SbcGradient.rect(g, x, y + 1, 1, h - 2, top, top, bottom, bottom);
            io.muzy.sbc.ui.screens.SbcGradient.rect(g, x + w - 1, y + 1, 1, h - 2, top, top, bottom, bottom);
            g.fill(x + 1, y, x + w - 1, y + 1, 0x2CFFFFFF);
            g.fill(x, y + 1, x + 1, y + h - 1, 0x10FFFFFF);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, 0x10FFFFFF);
            g.fill(x + 1, y + h - 1, x + w - 1, y + h, 0x60000000);
            if (!band) return;
            io.muzy.sbc.ui.screens.SbcGradient.rect(g, x + 1, y + 1, w - 2, 12, Ui.alpha(a, 0.36f), Ui.alpha(a, 0.03f), Ui.alpha(a, 0.03f), Ui.alpha(a, 0.36f));
            io.muzy.sbc.ui.screens.SbcGradient.rect(g, x + 1, y + 13, w - 2, 1, Ui.alpha(a, 0.95f), Ui.alpha(a, 0.08f), Ui.alpha(a, 0.08f), Ui.alpha(a, 0.95f));
        };
    }

    public static Color accent() {
        return Color.ofArgb(Theme.current().accent());
    }

    public static Color text() {
        return Color.ofArgb(Theme.current().text());
    }

    public static Color dim() {
        return Color.ofArgb(Theme.current().dim());
    }

    public static LabelComponent label(String s, Color c) {
        return new LabelComponent(Component.literal(s)).color(c).shadow(true);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
