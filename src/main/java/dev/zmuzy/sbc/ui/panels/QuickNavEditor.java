// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui.panels;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.item.QuickNav;
import dev.zmuzy.sbc.ui.screens.SbcScreen;
import dev.zmuzy.sbc.ui.screens.SbcWidgets;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.layout.UIComponents;
import dev.zmuzy.sbc.ui.layout.FlowLayout;
import dev.zmuzy.sbc.ui.layout.UIContainers;
import dev.zmuzy.sbc.ui.layout.Color;
import dev.zmuzy.sbc.ui.layout.Insets;
import dev.zmuzy.sbc.ui.layout.Sizing;
import dev.zmuzy.sbc.ui.layout.UIComponent;
import dev.zmuzy.sbc.ui.layout.VerticalAlignment;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// small editor for the 14 quick nav buttons inside its settings card. everything skyblockers
// QuickNavigationCategory has per button
public final class QuickNavEditor {
    private static int chosen;
    private static String search = "";

    private QuickNavEditor() {}

    private static UIComponent ui(Object vanillaBased) {
        return (UIComponent) vanillaBased;
    }

    public static FlowLayout editor(int listWidth) {
        FlowLayout box = UIContainers.verticalFlow(Sizing.fill(100), Sizing.content());
        box.gap(2);
        fill(box, listWidth);
        return box;
    }

    private static void fill(FlowLayout box, int listWidth) {
        box.clearChildren();
        box.child(new SbcWidgets.Heading("Edit a button", Modern.AQUA));
        int perRow = Math.max(3, Math.min(14, (listWidth - 14) / 26));
        FlowLayout row = null;
        for (int i = 0; i < QuickNav.count(); i++) {
            if (i % perRow == 0) {
                row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
                row.gap(2);
                box.child(row);
            }
            int idx = i;
            UIComponent pick = ui(SbcWidgets.button(Component.literal((i == chosen ? "§b" : QuickNav.bool(i, "render", true) ? "" : "§8") + (i + 1)), b -> {
                chosen = idx;
                fill(box, listWidth);
            }));
            pick.horizontalSizing(Sizing.fixed(24));
            pick.tooltip(Component.literal(QuickNav.label(i)));
            row.child(pick);
        }
        int i = chosen, fieldW = Math.max(100, Math.min(200, listWidth - 150));
        FlowLayout head = line();
        head.child(UIComponents.item(QuickNav.icon(QuickNav.get(i, "item", QuickNav.defaultItem(i)), QuickNav.get(i, "components", ""), (int) Math.round(QuickNav.number(i, "count", 1)))));
        head.child(SbcScreen.label(QuickNav.label(i), Color.ofArgb(Modern.INK)).horizontalSizing(Sizing.expand()));
        UIComponent reset = ui(SbcWidgets.button(Component.literal("↺"), b -> {
            QuickNav.reset(i);
            search = "";
            fill(box, listWidth);
        }));
        reset.tooltip(Component.literal("Back to how this button ships"));
        head.child(reset);
        box.child(head);

        box.child(toggle("Render", "Off: the button is not there and the others close up.", QuickNav.bool(i, "render", true), v -> {
            QuickNav.set(i, "render", v ? null : (Object) false);
            fill(box, listWidth);
        }));
        box.child(toggle("Require double click", "The second click, within a second, sends the command.", QuickNav.bool(i, "doubleClick", false), v -> QuickNav.set(i, "doubleClick", v ? (Object) true : null)));
        box.child(text("Item", "An item of the game or a SkyBlock id.", QuickNav.get(i, "item", QuickNav.defaultItem(i)), fieldW, s -> QuickNav.set(i, "item", s.isBlank() ? null : s.trim())));
        FlowLayout count = line();
        count.child(SbcScreen.label("Item count", Color.ofArgb(Modern.LABEL)).horizontalSizing(Sizing.expand()));
        SbcWidgets.Slider slider = SbcWidgets.slider(Sizing.fixed(fieldW), 1, 99);
        slider.decimalPlaces(0);
        slider.setFromDiscreteValue(QuickNav.number(i, "count", 1));
        slider.onChanged(v -> {
            int n = (int) Math.round(v);
            QuickNav.set(i, "count", n == 1 ? null : (Object) (double) n);
        });
        count.child(ui(slider));
        box.child(count);
        box.child(text("Item components", "As in the /give command, in square brackets. Example: [minecraft:enchantment_glint_override=true]", QuickNav.get(i, "components", ""), fieldW, s -> QuickNav.set(i, "components", s.isBlank() ? null : s)));
        box.child(text("Command", "What a click sends. The slash may stay out.", QuickNav.get(i, "command", QuickNav.defaultCommand(i)), fieldW, s -> QuickNav.set(i, "command", s)));
        box.child(text("Tooltip", "Shown over the button. Plain text or text json. Empty: no tooltip.", QuickNav.get(i, "tooltip", QuickNav.label(i)), fieldW, s -> QuickNav.set(i, "tooltip", s)));
        box.child(text("Menu title", "A regular expression for the whole title of the menu this button stays lit in.", QuickNav.get(i, "uiTitle", QuickNav.defaultTitle(i)), fieldW, s -> QuickNav.set(i, "uiTitle", s)));

        box.child(SbcScreen.label("Choose SkyBlock item", Color.ofArgb(Modern.LABEL)).margins(Insets.top(2)));
        FlowLayout results = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        results.gap(2);
        SbcWidgets.Input find = SbcWidgets.input(Sizing.fixed(fieldW), () -> search, s -> search = s);
        find.setHint(Component.literal("Search..."));
        find.onChanged(s -> {
            search = s;
            matches(results, box, listWidth, i);
        });
        box.child(ui(find));
        box.child(results);
        matches(results, box, listWidth, i);
    }

    // one line of search matches, click puts the id in the item field
    private static void matches(FlowLayout results, FlowLayout box, int listWidth, int i) {
        results.clearChildren();
        String q = search.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 2) return;
        List<String> found = new ArrayList<>();
        int room = Math.max(4, (listWidth - 14) / 18);
        for (String id : ItemRepo.ids()) {
            if ((GameState.strip(ItemRepo.name(id)) + " " + id).toLowerCase(Locale.ROOT).contains(q)) found.add(id);
            if (found.size() >= room) break;
        }
        for (String id : found) {
            UIComponent item = UIComponents.item(QuickNav.icon(id, "", 1));
            item.tooltip(Component.literal(GameState.strip(ItemRepo.name(id))));
            item.mouseDown().subscribe((click, doubled) -> {
                QuickNav.set(i, "item", id);
                QuickNav.set(i, "components", null);
                search = "";
                fill(box, listWidth);
                return true;
            });
            results.child(item);
        }
    }

    private static FlowLayout line() {
        FlowLayout row = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        row.gap(4);
        row.verticalAlignment(VerticalAlignment.CENTER);
        return row;
    }

    private static FlowLayout toggle(String label, String tip, boolean on, java.util.function.Consumer<Boolean> set) {
        FlowLayout row = line();
        var name = SbcScreen.label(label, Color.ofArgb(Modern.LABEL));
        name.horizontalSizing(Sizing.expand());
        name.tooltip(Component.literal(tip));
        row.child(name);
        SbcWidgets.Toggle t = SbcWidgets.toggle(Component.empty()).checked(on);
        t.onChanged(set::accept);
        row.child(ui(t));
        return row;
    }

    private static FlowLayout text(String label, String tip, String value, int w, java.util.function.Consumer<String> set) {
        FlowLayout row = line();
        var name = SbcScreen.label(label, Color.ofArgb(Modern.LABEL));
        name.horizontalSizing(Sizing.expand());
        name.tooltip(Component.literal(tip));
        row.child(name);
        SbcWidgets.Input in = SbcWidgets.input(Sizing.fixed(w), value);
        in.maxLength(512);
        in.onChanged(set::accept);
        row.child(ui(in));
        return row;
    }
}
