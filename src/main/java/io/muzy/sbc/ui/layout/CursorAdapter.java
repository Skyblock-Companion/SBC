// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.util.CursorAdapter, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 *
 * Changed: the window's cursors are made once on first use and shared, instead of made per screen and destroyed by a hook on
 * the game's screen changes, because that hook was one more thing to carry from one Minecraft version to the next.
 */
package io.muzy.sbc.ui.layout;

import java.util.EnumMap;

public class CursorAdapter {

    // the one line to the window. tests put a listener here, the game is the default
    interface Window {
        //? if >=26.3 {
        long createCursor(int shape);
        //?} else {
        /*long createCursor(int glfwShape);
        *///?}

        void setCursor(long cursor);
    }

    private static final Window GAME_WINDOW = new Window() {
        @Override
        //? if >=26.3 {
        public long createCursor(int shape) {
            return GameLink.createCursor(shape);
        //?} else {
        /*public long createCursor(int glfwShape) {
            return GameLink.createCursor(glfwShape);
        *///?}
        }

        @Override
        public void setCursor(long cursor) {
            GameLink.setCursor(cursor);
        }
    };

    private static final EnumMap<CursorStyle, Long> GAME_CURSORS = new EnumMap<>(CursorStyle.class);

    private final Window window;
    private final EnumMap<CursorStyle, Long> cursors;

    protected CursorStyle lastCursorStyle = CursorStyle.POINTER;

    public CursorAdapter() {
        this(GAME_WINDOW, GAME_CURSORS);
    }

    CursorAdapter(Window window, EnumMap<CursorStyle, Long> cursors) {
        this.window = window;
        this.cursors = cursors;
    }

    public void applyStyle(CursorStyle style) {
        if (this.lastCursorStyle == style) return;

        if (style == CursorStyle.NONE) {
            this.window.setCursor(0);
        } else {
            //? if >=26.3 {
            this.window.setCursor(this.cursors.computeIfAbsent(style, s -> this.window.createCursor(s.shape)));
            //?} else {
            /*this.window.setCursor(this.cursors.computeIfAbsent(style, s -> this.window.createCursor(s.glfw)));
            *///?}
        }
        this.lastCursorStyle = style;
    }
}
