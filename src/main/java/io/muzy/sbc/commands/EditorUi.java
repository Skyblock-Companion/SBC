// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.commands;

import io.muzy.sbc.ui.layout.Color;
import io.muzy.sbc.ui.layout.FlowLayout;
import io.muzy.sbc.ui.layout.HorizontalAlignment;
import io.muzy.sbc.ui.layout.Insets;
import io.muzy.sbc.ui.layout.Sizing;
import io.muzy.sbc.ui.layout.UIContainers;
import io.muzy.sbc.ui.layout.VerticalAlignment;
import io.muzy.sbc.ui.screens.SbcScreen;
import io.muzy.sbc.ui.screens.SbcWidgets;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

// bits for the small editors in this package, same look as the settings screen
final class EditorUi {
    private EditorUi() {}

    // centered panel on the backdrop. children go in the scroll list it returns, foot goes under it
    static FlowLayout panel(FlowLayout root, int screenWidth, String title, String hint, FlowLayout foot) {
        root.surface(SbcScreen.backdrop());
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        int w = Math.min(460, screenWidth - 20);
        FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(w), Sizing.fill(88));
        panel.surface(SbcScreen.panel());
        panel.padding(Insets.of(8));
        panel.gap(4);
        panel.child(SbcScreen.label(title, SbcScreen.accent()));
        if (hint != null) panel.child(SbcScreen.label(hint, SbcScreen.dim()).maxWidth(w - 20));
        FlowLayout list = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        list.gap(3);
        list.padding(Insets.right(6));
        panel.child(SbcWidgets.scroll(Sizing.fill(100), Sizing.expand(), list));
        panel.child(foot);
        root.child(panel);
        return list;
    }

    static FlowLayout row() {
        FlowLayout r = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        r.gap(3);
        r.verticalAlignment(VerticalAlignment.CENTER);
        return r;
    }

    static SbcWidgets.Button button(String text, Runnable press) {
        return SbcWidgets.button(Component.literal(text), b -> press.run());
    }

    // writes through on every key
    static SbcWidgets.Input field(int percent, String value, String hint, Consumer<String> set) {
        String[] store = {value == null ? "" : value};
        SbcWidgets.Input in = SbcWidgets.input(Sizing.fill(percent), () -> store[0], v -> {
            store[0] = v;
            set.accept(v);
        });
        in.maxLength(256);
        if (hint != null) in.setHint(Component.literal(hint));
        return in;
    }

    static FlowLayout labelled(String label, String value, Consumer<String> set) {
        FlowLayout r = row();
        r.child(SbcScreen.label(label, SbcScreen.dim()).horizontalSizing(Sizing.fill(34)));
        r.child(field(64, value, null, set));
        return r;
    }

    static FlowLayout toggle(String label, boolean value, Consumer<Boolean> set, Runnable after) {
        FlowLayout r = row();
        r.child(SbcScreen.label(label, SbcScreen.dim()).horizontalSizing(Sizing.fill(34)));
        r.child(button((value ? "§aOn" : "§7Off"), () -> {
            set.accept(!value);
            after.run();
        }));
        return r;
    }

    static Color ink() {
        return SbcScreen.text();
    }
}
