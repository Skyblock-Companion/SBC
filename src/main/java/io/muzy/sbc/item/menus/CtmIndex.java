// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.menus;

// from skyhanni CTMUtils (which is from neu): which piece of a connected texture a slot takes, by which of
// its 8 neighbours are the same kind. generated from their table
record CtmIndex(boolean up, boolean right, boolean down, boolean left, boolean upLeft, boolean upRight, boolean downRight, boolean downLeft) {
    int index() {
        if (up) return getCTMIndexUp();
        if (!up) return getCTMIndexNotUp();
        throw new IllegalStateException();
    }

    private int getCTMIndexUp() {
        if (right) return getCTMIndexUpRight();
        if (!right) return getCTMIndexUpNotRight();
        throw new IllegalStateException();
    }

    private int getCTMIndexUpRight() {
        if (down) return getCTMIndexUpRightDown();
        if (!down && left && !upLeft && !upRight) return 18;
        if (!down && left && !upLeft) return 40;
        if (!down && left && !upRight) return 42;
        if (!down && left) return 38;
        if (!upRight) return 16;
        return 37;
    }

    private int getCTMIndexUpRightDown() {
        if (left) return getCTMIndexUpRightDownLeft();
        if (!upRight && !downRight) return 6;
        if (!upRight) return 28;
        if (!downRight) return 30;
        return 25;
    }

    private int getCTMIndexUpRightDownLeft() {
        if (upLeft && upRight && downRight && downLeft) return 26;
        if (upLeft && upRight && downRight && !downLeft) return 33;
        if (upLeft && upRight && !downRight && downLeft) return 32;
        if (upLeft && upRight && !downRight && !downLeft) return 11;
        if (upLeft && !upRight && downRight && downLeft) return 44;
        if (upLeft && !upRight && downRight && !downLeft) return 35;
        if (upLeft && !upRight && !downRight && downLeft) return 10;
        if (upLeft && !upRight && !downRight && !downLeft) return 20;
        if (!upLeft && upRight && downRight && downLeft) return 45;
        if (!upLeft && upRight && downRight && !downLeft) return 23;
        if (!upLeft && upRight && !downRight && downLeft) return 34;
        if (!upLeft && upRight && !downRight && !downLeft) return 8;
        if (downRight && downLeft) return 22;
        if (downRight && !downLeft) return 9;
        if (downLeft) return 21;
        return 46;
    }

    private int getCTMIndexUpNotRight() {
        if (down && left && !upLeft && !downLeft) return 19;
        if (down && left && !upLeft) return 43;
        if (down && left && !downLeft) return 41;
        if (down && left) return 27;
        if (down && !left) return 24;
        if (!down && left && !upLeft) return 17;
        if (!down && left) return 39;
        return 36;
    }

    private int getCTMIndexNotUp() {
        if (right && down && left && !downLeft && !downRight) return 7;
        if (right && down && left && !downLeft) return 31;
        if (right && down && left && !downRight) return 29;
        if (right && down && left) return 14;
        if (right && down && !left && !downRight) return 4;
        if (right && down && !left) return 13;
        if (right && !down && left) return 2;
        if (right && !down && !left) return 1;
        if (!right && down && left && !downLeft) return 5;
        if (!right && down && left) return 15;
        if (!right && down && !left) return 12;
        if (left) return 3;
        return 0;
    }
}
