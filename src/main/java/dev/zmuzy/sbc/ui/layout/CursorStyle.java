// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.core.CursorStyle, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package dev.zmuzy.sbc.ui.layout;

public enum CursorStyle {
    NONE(0),
    POINTER(GameLink.ARROW_CURSOR),
    TEXT(GameLink.IBEAM_CURSOR),
    HAND(GameLink.HAND_CURSOR),
    MOVE(GameLink.MOVE_CURSOR);

    public final int shape;

    CursorStyle(int shape) {
        this.shape = shape;
    }
}
