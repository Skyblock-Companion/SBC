// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.core.Positioning, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package io.github.zmuzy.sbc.ui.layout;

import java.util.Objects;

public class Positioning {

    private static final Positioning LAYOUT_POSITIONING = new Positioning(0, 0, Type.LAYOUT);

    public final Type type;
    public final int x, y;

    private Positioning(int x, int y, Type type) {
        this.type = type;
        this.x = x;
        this.y = y;
    }

    public boolean isRelative() {
        return this.type == Type.RELATIVE || this.type == Type.ACROSS;
    }

    public static Positioning absolute(int Pl27___, int yPixels) {
        return new Positioning(Pl27___, yPixels, Type.ABSOLUTE);
    }

    public static Positioning relative(int xPercent, int yPercent) {
        return new Positioning(xPercent, yPercent, Type.RELATIVE);
    }

    public static Positioning across(int xPercent, int yPercent) {
        return new Positioning(xPercent, yPercent, Type.ACROSS);
    }

    public static Positioning layout() {
        return LAYOUT_POSITIONING;
    }

    public enum Type {
        RELATIVE, ACROSS, ABSOLUTE, LAYOUT
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Positioning that = (Positioning) o;
        return x == that.x && y == that.y && type == that.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, x, y);
    }
}
