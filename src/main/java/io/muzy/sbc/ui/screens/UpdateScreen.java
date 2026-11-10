// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui.screens;

import io.muzy.sbc.core.ProfileServer;
import io.muzy.sbc.ui.layout.Color;
import io.muzy.sbc.ui.layout.FlowLayout;
import io.muzy.sbc.ui.layout.HorizontalAlignment;
import io.muzy.sbc.ui.layout.Insets;
import io.muzy.sbc.ui.layout.Sizing;
import io.muzy.sbc.ui.layout.UIContainers;
import io.muzy.sbc.ui.layout.VerticalAlignment;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// card saying a newer version is out: the servers message + where to get it. once per game start, over the
// title screen (after setup), or when you join skyblock
public final class UpdateScreen extends SbcScreen {
    public static final String RELEASES = "https://github.com/Skyblock-Companion/SBC/releases";
    public static final String MODRINTH = "https://modrinth.com/mod/skyblockcompanion";
    public static final String CURSEFORGE = "https://www.curseforge.com/minecraft/mc-mods/sbc-hypixel-skyblock-companion";
    private static boolean shown;

    private final ProfileServer.Update update;

    public UpdateScreen(Screen parent, ProfileServer.Update update) {
        super("Update available", parent);
        this.update = update;
    }

    // opens over parent when theres an update and it wasnt shown since start
    public static void maybeShow(Minecraft mc, Screen parent) {
        ProfileServer.Update u = ProfileServer.update();
        if (u == null || shown) return;
        shown = true;
        mc.gui.setScreen(new UpdateScreen(parent, u));
    }

    // opens a link in the browser, they pressed the button for it
    public static void openLink(String url) {
        com.mojang.blaze3d.Blaze3D.openUri(java.net.URI.create(url));
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Showcase.backdrop());
        root.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        int w = Math.min(320, width - 20);
        FlowLayout panel = UIContainers.verticalFlow(Sizing.fixed(w), Sizing.content());
        panel.surface(Showcase.panel(0xFFFFB300));
        panel.padding(Insets.of(12));
        panel.gap(6);
        panel.child(new Showcase.Logo(Math.max(40, Math.min(64, height / 5))));
        panel.child(new Showcase.BigText("UPDATE AVAILABLE", 1.5f, 0xFFE082, 0xFFB300));
        panel.child(label(update.message(), text()).maxWidth(w - 20));
        panel.child(label("You have " + ProfileServer.VERSION + (update.latest().isEmpty() ? "" : ", the newest is " + update.latest()) + ".", dim()).maxWidth(w - 20));
        int third = (w - 20 - 12) / 3;
        FlowLayout links = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        links.gap(6);
        links.margins(Insets.top(4));
        links.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("CurseForge"), b -> openLink(CURSEFORGE))).horizontalSizing(Sizing.fixed(third)));
        links.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Modrinth"), b -> openLink(MODRINTH))).horizontalSizing(Sizing.fixed(third)));
        links.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("GitHub"), b -> openLink(RELEASES))).horizontalSizing(Sizing.fixed(third)));
        panel.child(links);
        panel.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Later"), b -> onClose())).horizontalSizing(Sizing.fill(100)));
        root.child(panel);
    }
}
