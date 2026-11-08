// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui.screens;

import dev.zmuzy.sbc.ui.layout.FlowLayout;
import dev.zmuzy.sbc.ui.layout.UIComponent;
import net.minecraft.client.gui.screens.Screen;

// settings as its own screen (/sbc config, hud editor right click). the K menu embeds the same panel
public final class SettingsScreen extends SbcScreen {
    private final SettingsPanel panel = new SettingsPanel(this, false);

    public SettingsScreen(Screen parent) {
        super("Settings", parent);
    }

    // opens on one feature: its section selected + search set to its title
    public SettingsScreen(Screen parent, String featureId) {
        this(parent);
        panel.focus(featureId);
    }

    static UIComponent ui(Object vanillaBased) {
        return (UIComponent) vanillaBased;
    }

    @Override
    protected void build(FlowLayout root) {
        panel.build(root, width);
    }

    public static void open(Screen parent) {
        net.minecraft.client.Minecraft.getInstance().gui.setScreen(new SettingsScreen(parent));
    }

    public static void open(Screen parent, String featureId) {
        net.minecraft.client.Minecraft.getInstance().gui.setScreen(new SettingsScreen(parent, featureId));
    }
}
