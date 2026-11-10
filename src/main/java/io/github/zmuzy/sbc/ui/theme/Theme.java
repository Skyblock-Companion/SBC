// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui.theme;

import io.github.zmuzy.sbc.core.Config;
import io.github.zmuzy.sbc.ui.Ui;

// the look of everything we draw, in one place. classic = compact see through cards with the game font. hud
// elements + screens all go through here
public interface Theme {
    String id();

    // bg of one hud element
    void panel(Ui ui, int x, int y, int w, int h);

    // bg with the elements style overrides
    default void panel(Ui ui, int x, int y, int w, int h, io.github.zmuzy.sbc.ui.hud.ElementStyle st) {
        if (st == null || st.isEmpty() || (st.bg() == null && st.alpha() == null)) {
            panel(ui, x, y, w, h);
        } else if (this == ClassicTheme.I && st.bg() == null) {
            Modern.card(ui, x, y, w, h, st.alpha());
        } else {
            int base = st.bg() == null ? 0xFF000000 : st.bg();
            float a = st.alpha() == null ? backgroundOpacity() : st.alpha();
            fill(ui, x, y, w, h, Ui.alpha(base | 0xFF000000, a));
        }
        if (Boolean.TRUE.equals(st == null ? null : st.border())) {
            int c = st.borderColor() == null ? accent() : st.borderColor();
            ui.rect(x, y, w, 1, c);
            ui.rect(x, y + h - 1, w, 1, c);
            ui.rect(x, y, 1, h, c);
            ui.rect(x + w - 1, y, 1, h, c);
        }
    }

    // one opacity for every element bg (hud editor slider)
    default float backgroundOpacity() {
        return Config.I.classicAlpha;
    }

    // flat panel bg in the themes shape
    default void fill(Ui ui, int x, int y, int w, int h, int color) {
        ui.rect(x, y, w, h, color);
    }

    // title line height + whatever splits it from the rows
    default int titleHeight(int rowH) {
        return rowH;
    }

    // whatever splits title from rows. y = where row 1 would start without it
    default void titleRule(Ui ui, int x, int y, int w) {
    }

    // small inset inside an element (bar groove, slot, sub box)
    void inset(Ui ui, int x, int y, int w, int h);

    // title line, returns height
    int title(Ui ui, String text, int x, int y, int w);

    // label left, value right (value can be empty)
    void row(Ui ui, String label, String value, int x, int y, int w, int valueColor);

    void bar(Ui ui, int x, int y, int w, int h, double pct, int color);

    int text();

    int dim();

    int accent();

    int good();

    int bad();

    // one text row height + padding between edge and content
    int rowH();

    int pad();

    // gap between label and value
    default int gap() {
        return 6;
    }

    static Theme current() {
        return ClassicTheme.I;
    }

}
