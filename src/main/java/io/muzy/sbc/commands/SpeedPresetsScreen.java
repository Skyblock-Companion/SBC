// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.commands;

import io.muzy.sbc.ui.layout.FlowLayout;
import io.muzy.sbc.ui.screens.SbcScreen;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// from skyblocker SpeedPresetsScreen + SpeedPresetListWidget. name + speed per line, saved on close
public final class SpeedPresetsScreen extends SbcScreen {
    private final List<String[]> rows = new ArrayList<>();
    private FlowLayout root;

    public SpeedPresetsScreen(Screen parent) {
        super("Speed presets", parent);
        SpeedPresets.presets().forEach((k, v) -> rows.add(new String[]{k, Integer.toString(v)}));
    }

    private void rebuild() {
        root.clearChildren();
        build(root);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        root = rootComponent;
        FlowLayout foot = EditorUi.row();
        foot.child(EditorUi.button("Add", () -> {
            rows.add(new String[]{"", "100"});
            rebuild();
        }));
        foot.child(EditorUi.button("Done", this::onClose));
        FlowLayout list = EditorUi.panel(root, width, "Speed presets", "/setmaxspeed name sends its speed. /sbc speed name does the same.", foot);
        for (String[] row : rows) {
            FlowLayout r = EditorUi.row();
            r.child(EditorUi.field(60, row[0], "Name", v -> row[0] = v));
            r.child(EditorUi.field(28, row[1], "Speed", v -> row[1] = v));
            r.child(EditorUi.button("§cX", () -> {
                rows.remove(row);
                rebuild();
            }));
            list.child(r);
        }
    }

    // skyblockers command pattern wants a letter first then letters, digits, underscores. any other name would never match
    static boolean validName(String name) {
        return name.matches("[a-zA-Z]\\w*");
    }

    @Override
    public void onClose() {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String[] row : rows) {
            String name = row[0].trim();
            if (!validName(name)) continue;
            try {
                out.put(name, Math.max(0, Integer.parseInt(row[1].trim())));
            } catch (NumberFormatException ignored) {
                // speed thats not a number gets skipped, same as an empty line
            }
        }
        Saved.get().speedPresets = out;
        Saved.save();
        super.onClose();
    }
}
