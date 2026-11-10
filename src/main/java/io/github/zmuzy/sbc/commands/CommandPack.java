// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.commands;

import io.github.zmuzy.sbc.ui.layout.UIComponent;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import java.util.function.Function;

// commands, keys, shortcuts and tab complete. everything the player types or presses
public final class CommandPack {
    private CommandPack() {}

    public static void keys(KeyMapping.Category category) {
        TrapperKey.keys(category);
        CompactorKey.keys(category);
    }

    public static void register() {
        TabComplete.register();
        CommandRewrites.register();
        LastStorage.register();
        SpeedPresets.register();
        Shortcuts.register();
        Todos.register();
        Reminders.register();
        NavigateAll.register();
        MoreCommands.register();
        TrapperKey.register();
        CompactorKey.register();
        ServerStats.register();
    }

    // button that opens a features editor inside its settings card, null for the rest
    public static UIComponent settingsButton(String featureId) {
        return switch (featureId) {
            case Shortcuts.ID -> opener("Edit shortcuts", ShortcutsScreen::new);
            case Todos.ID -> opener("Edit todos", TodosScreen::new);
            case SpeedPresets.ID -> opener("Edit presets", SpeedPresetsScreen::new);
            case "waypoints" -> opener("See and change them", WaypointsScreen::new);
            default -> null;
        };
    }

    private static UIComponent opener(String label, Function<Screen, Screen> screen) {
        return EditorUi.button(label, () -> {
            Minecraft mc = Minecraft.getInstance();
            mc.gui.setScreen(screen.apply(mc.gui.screen()));
        });
    }
}
