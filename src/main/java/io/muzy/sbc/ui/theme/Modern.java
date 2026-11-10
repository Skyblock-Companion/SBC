// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui.theme;

import io.muzy.sbc.ui.Ui;
import net.minecraft.world.item.ItemStack;

// classic drawing kit: slate glass card with a lit top + soft corners, title band in the cards color fading
// out to the right, label/value colors, meters
public final class Modern {
    public static final int BAND = 14, ROW = 10, PAD = 4, METER = 2, METER_BLOCK = 4, RULE_BLOCK = 6, ICON = 10;
    public static final float ICON_SCALE = 0.625f;

    public static final int INK = 0xFFF2F6FA, LABEL = 0xFFC2CEDB, DIM = 0xFF8E9CAD, GOLD = 0xFFFFD166, AQUA = 0xFF5CD8FF, GOOD = 0xFF5FEA8C, BAD = 0xFFFF6B6B, WARN = 0xFFFFB454;
    private static final int GLASS_TOP = 0x151E2B, GLASS_BOTTOM = 0x0A0F17, GROOVE = 0xB0050911, RULE = 0x1CFFFFFF;

    private Modern() {}

    public static int lighten(int c, float t) {
        return Ui.lerpColor(c, 0xFF000000 | 0xFFFFFF, t) & 0x00FFFFFF | (c & 0xFF000000);
    }

    public static int darken(int c, float t) {
        return Ui.lerpColor(c, 0xFF000000, t) & 0x00FFFFFF | (c & 0xFF000000);
    }

    private static int solid(int rgb, float a) {
        return (Math.round(255 * Math.max(0f, Math.min(1f, a))) << 24) | (rgb & 0xFFFFFF);
    }

    // opacity 0 draws nothing, edges too
    // a mod window the size of the K menu: 80% of the width (at most max), 90% of the height, in the middle, with
    // the game still around it. x, y, w, h
    public static int[] window(int W, int H, int max) {
        int w = Math.min(max, Math.max(Math.round(W * 0.8f), Math.min(W - 16, 420)));
        int h = Math.max(Math.round(H * 0.9f), Math.min(H - 16, 260));
        return new int[]{(W - w) / 2, (H - h) / 2, w, h};
    }

    public static void card(Ui ui, int x, int y, int w, int h, float opacity) {
        if (opacity <= 0f || w < 3 || h < 3) return;
        int top = solid(GLASS_TOP, opacity), bottom = solid(GLASS_BOTTOM, opacity);
        ui.gradV(x + 1, y, w - 2, h, top, bottom);
        ui.gradV(x, y + 1, 1, h - 2, top, bottom);
        ui.gradV(x + w - 1, y + 1, 1, h - 2, top, bottom);
        float edge = Math.min(1f, opacity / 0.5f);
        ui.rect(x + 1, y, w - 2, 1, solid(0xFFFFFF, 0.16f * edge));
        ui.rect(x, y + 1, 1, h - 2, solid(0xFFFFFF, 0.05f * edge));
        ui.rect(x + w - 1, y + 1, 1, h - 2, solid(0xFFFFFF, 0.05f * edge));
        ui.rect(x + 1, y + h - 1, w - 2, 1, solid(0x000000, 0.35f * edge));
    }

    // card with a flat color you picked instead of glass
    public static void flat(Ui ui, int x, int y, int w, int h, int color) {
        ui.rect(x + 1, y, w - 2, h, color);
        ui.rect(x, y + 1, 1, h - 2, color);
        ui.rect(x + w - 1, y + 1, 1, h - 2, color);
    }

    // title band across the top, returns where content starts relative to y
    public static int band(Ui ui, String title, ItemStack icon, String right, int accent, int x, int y, int w) {
        int opaque = accent | 0xFF000000;
        ui.gradH(x + 1, y + 1, w - 2, BAND - 2, Ui.alpha(opaque, 0.34f), Ui.alpha(opaque, 0.03f));
        ui.gradH(x + 1, y + BAND - 1, w - 2, 1, Ui.alpha(opaque, 0.95f), Ui.alpha(opaque, 0.08f));
        int tx = x + PAD;
        if (icon != null && !icon.isEmpty()) {
            ui.icon(icon, x + 2, y + 2, ICON_SCALE);
            tx = x + 2 + ICON + 2;
        }
        int rw = right == null || right.isEmpty() ? 0 : ui.width(right) + 4;
        ui.textShadow(ui.ellipsize(title, x + w - PAD - rw - tx), tx, y + 3, lighten(opaque, 0.45f));
        if (rw > 0) ui.textShadow(right, x + w - PAD - rw + 4, y + 3, INK);
        return BAND + 2;
    }

    // card with no title says whose it is with a colored edge
    public static void edge(Ui ui, int x, int y, int h, int accent) {
        ui.gradV(x, y + 1, 2, h - 2, accent | 0xFF000000, Ui.alpha(accent | 0xFF000000, 0.35f));
    }

    public static void rule(Ui ui, int x, int y, int w) {
        ui.rect(x, y, w, 1, RULE);
    }

    // meter: dark groove, color fill, lit top line, brighter tip
    public static void meter(Ui ui, int x, int y, int w, int h, double pct, int color) {
        ui.rect(x, y, w, h, GROOVE);
        double p = Math.max(0, Math.min(1, pct));
        int f = (int) Math.round(w * p);
        if (f <= 0) return;
        int c = color | 0xFF000000;
        ui.rect(x, y, f, h, h >= 3 ? darken(c, 0.12f) : c);
        if (h >= 3) ui.rect(x, y, f, 1, lighten(c, 0.35f));
        if (f < w && h >= 3) ui.rect(x + f - 1, y, 1, h, lighten(c, 0.55f));
    }

    // label left, value right. too long to share a line = value takes it alone
    public static void row(Ui ui, String label, String value, int x, int y, int w, int labelColor, int valueColor) {
        label = ClassicTheme.readable(label);
        if (value == null || value.isEmpty()) {
            ui.textShadow(ui.ellipsize(label, w), x, y, labelColor);
            return;
        }
        value = ClassicTheme.readable(value);
        int vw = ui.width(value);
        if (vw > w - 8) {
            ui.textShadow(ui.ellipsize(label + " " + value, w), x, y, labelColor);
            return;
        }
        ui.textShadow(ui.ellipsize(label, Math.max(8, w - vw - 6)), x, y, labelColor);
        ui.textShadow(value, x + w - vw, y, valueColor);
    }

    // small colored tag, returns width. good for states: READY, ACTIVE, T5
    public static int chip(Ui ui, int x, int y, String text, int color) {
        int w = ui.width(text) + 6, c = color | 0xFF000000;
        ui.rect(x, y - 1, w, 10, Ui.alpha(c, 0.22f));
        ui.rect(x, y + 8, w, 1, Ui.alpha(c, 0.8f));
        ui.textShadow(text, x + 3, y, lighten(c, 0.35f));
        return w;
    }

    // sub heading in a card: name in card color + a line fading after it
    public static void section(Ui ui, String title, int x, int y, int w, int accent) {
        int c = accent | 0xFF000000, tw = ui.width(title);
        ui.textShadow(title, x, y, lighten(c, 0.3f));
        if (w > tw + 6) ui.gradH(x + tw + 4, y + 4, w - tw - 4, 1, Ui.alpha(c, 0.6f), Ui.alpha(c, 0.02f));
    }

    // "1,256,000/1,300,000" both numbers in their colors + a dim slash, as coded text
    public static String fraction(String now, String of, char nowCode, char ofCode) {
        return "§" + nowCode + now + "§7/§" + ofCode + of;
    }
}
