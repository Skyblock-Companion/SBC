// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;

// gradient rect drawn the games way. custom render state that reports its spot without the pose, and the game
// layers by those rects. inside a scaled scrolled thing the normal way breaks
public final class SbcGradient {
    // max strips across a horizontal gradient, nobody sees the steps
    private static final int STRIPS = 48;

    private SbcGradient() {}

    private static int lerp(int a, int b, float t) {
        int aa = a >>> 24, ar = a >> 16 & 255, ag = a >> 8 & 255, ab = a & 255;
        int ba = b >>> 24, br = b >> 16 & 255, bg = b >> 8 & 255, bb = b & 255;
        return Math.round(aa + (ba - aa) * t) << 24 | Math.round(ar + (br - ar) * t) << 16 | Math.round(ag + (bg - ag) * t) << 8 | Math.round(ab + (bb - ab) * t);
    }

    // colors: top left, top right, bottom right, bottom left
    public static void rect(GuiGraphicsExtractor g, int x, int y, int w, int h, int topLeft, int topRight, int bottomRight, int bottomLeft) {
        if (w <= 0 || h <= 0) return;
        if (topLeft == topRight && bottomLeft == bottomRight) {
            g.fillGradient(x, y, x + w, y + h, topLeft, bottomLeft);
            return;
        }
        int step = Math.max(1, (int) Math.ceil(w / (double) STRIPS));
        for (int i = 0; i < w; i += step) {
            int end = Math.min(w, i + step);
            float t = w == 1 ? 0 : (i + (end - i) / 2f) / w;
            g.fillGradient(x + i, y, x + end, y + h, lerp(topLeft, topRight, t), lerp(bottomLeft, bottomRight, t));
        }
    }
}
