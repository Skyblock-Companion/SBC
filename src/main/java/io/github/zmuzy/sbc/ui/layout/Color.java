// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.core.Color, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package io.github.zmuzy.sbc.ui.layout;

public record Color(float red, float green, float blue, float alpha) {

    public static final Color BLACK = Color.ofRgb(0);
    public static final Color WHITE = Color.ofRgb(0xFFFFFF);

    public static Color ofArgb(int argb) {
        return new Color(
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f,
                (argb >>> 24) / 255f
        );
    }

    public static Color ofRgb(int rgb) {
        return new Color(
                ((rgb >> 16) & 0xFF) / 255f,
                ((rgb >> 8) & 0xFF) / 255f,
                (rgb & 0xFF) / 255f,
                1f
        );
    }

    public int argb() {
        return (int) (this.alpha * 255) << 24
                | (int) (this.red * 255) << 16
                | (int) (this.green * 255) << 8
                | (int) (this.blue * 255);
    }
}
