// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.core.HorizontalAlignment, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package io.muzy.sbc.ui.layout;

public enum HorizontalAlignment {
    LEFT, CENTER, RIGHT;

    public int align(int componentWidth, int span) {
        return switch (this) {
            case LEFT -> 0;
            case CENTER -> span / 2 - componentWidth / 2;
            case RIGHT -> span - componentWidth;
        };
    }
}
