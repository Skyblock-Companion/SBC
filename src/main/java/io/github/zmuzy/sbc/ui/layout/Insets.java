// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.core.Insets, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package io.github.zmuzy.sbc.ui.layout;

public record Insets(int top, int bottom, int left, int right) {

    private static final Insets NONE = new Insets(0, 0, 0, 0);

    public int horizontal() {
        return this.left + this.right;
    }

    public int vertical() {
        return this.top + this.bottom;
    }

    public static Insets both(int horizontal, int vertical) {
        return new Insets(vertical, vertical, horizontal, horizontal);
    }

    public static Insets top(int top) {
        return new Insets(top, 0, 0, 0);
    }

    public static Insets bottom(int bottom) {
        return new Insets(0, bottom, 0, 0);
    }

    public static Insets left(int left) {
        return new Insets(0, 0, left, 0);
    }

    public static Insets right(int right) {
        return new Insets(0, 0, 0, right);
    }

    public static Insets of(int top, int bottom, int left, int right) {
        return new Insets(top, bottom, left, right);
    }

    public static Insets of(int inset) {
        return new Insets(inset, inset, inset, inset);
    }

    public static Insets vertical(int inset) {
        return new Insets(inset, inset, 0, 0);
    }

    public static Insets horizontal(int inset) {
        return new Insets(0, 0, inset, inset);
    }

    public static Insets none() {
        return NONE;
    }
}
