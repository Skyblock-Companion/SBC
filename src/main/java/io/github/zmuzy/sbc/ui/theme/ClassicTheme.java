// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui.theme;

import io.github.zmuzy.sbc.core.Config;
import io.github.zmuzy.sbc.ui.Ui;

// default look: slate glass cards with soft corners + a colored title band, game font with shadow, 10px rows,
// cool grey labels with bright values. drawing lives in Modern
public final class ClassicTheme implements Theme {
    public static final ClassicTheme I = new ClassicTheme();

    private static final int TITLE = Modern.GOLD, TEXT = Modern.INK, LABEL = Modern.LABEL, RULE = 0x30FFFFFF, DIM = Modern.DIM, ACCENT = Modern.AQUA, GOOD = Modern.GOOD, BAD = Modern.BAD;

    private ClassicTheme() {}

    @Override
    public String id() {
        return "classic";
    }

    @Override
    public void panel(Ui ui, int x, int y, int w, int h) {
        Modern.card(ui, x, y, w, h, Config.I.classicAlpha);
    }

    // corners cut by 1px, thats all it takes so a dark box doesnt look like a hole in the screen
    @Override
    public void fill(Ui ui, int x, int y, int w, int h, int color) {
        ui.rect(x + 1, y, w - 2, h, color);
        ui.rect(x, y + 1, 1, h - 2, color);
        ui.rect(x + w - 1, y + 1, 1, h - 2, color);
    }

    @Override
    public int titleHeight(int rowH) {
        return rowH + 2;
    }

    @Override
    public void titleRule(Ui ui, int x, int y, int w) {
        ui.rect(x, y, w, 1, RULE);
    }

    @Override
    public void inset(Ui ui, int x, int y, int w, int h) {
        ui.rect(x, y, w, h, 0x60000000);
    }

    @Override
    public int title(Ui ui, String text, int x, int y, int w) {
        ui.textShadow(ui.ellipsize(text, w), x, y, TITLE);
        return rowH();
    }

    @Override
    public void row(Ui ui, String label, String value, int x, int y, int w, int valueColor) {
        label = readable(label);
        if (value == null || value.isEmpty()) {
            ui.textShadow(ui.ellipsize(label, w), x, y, LABEL);
            return;
        }
        int vw = ui.width(value);
        ui.textShadow(ui.ellipsize(label, Math.max(10, w - vw - gap())), x, y, LABEL);
        ui.textShadow(value, x + w - vw, y, valueColor == 0 ? TEXT : valueColor);
    }

    // dark grey was made for black tooltips, on a see through panel over the world you cant read it
    public static String readable(String coloured) {
        return coloured.indexOf('§') < 0 ? coloured : coloured.replace("§8", "§7");
    }

    @Override
    public void bar(Ui ui, int x, int y, int w, int h, double pct, int color) {
        Modern.meter(ui, x, y, w, h, pct, color);
    }

    @Override
    public int text() {
        return TEXT;
    }

    @Override
    public int dim() {
        return DIM;
    }

    @Override
    public int accent() {
        return ACCENT;
    }

    @Override
    public int good() {
        return GOOD;
    }

    @Override
    public int bad() {
        return BAD;
    }

    @Override
    public int rowH() {
        return 10;
    }

    @Override
    public int pad() {
        return 4;
    }
}
