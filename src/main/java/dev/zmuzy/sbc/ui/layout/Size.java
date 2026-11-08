// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.core.Size, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package dev.zmuzy.sbc.ui.layout;

public record Size(int width, int height) {

    private static final Size ZERO = new Size(0, 0);

    public static Size of(int width, int height) {
        return new Size(width, height);
    }

    public static Size zero() {
        return ZERO;
    }
}
