// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui.screens;

import dev.zmuzy.sbc.core.ProfileServer;
import dev.zmuzy.sbc.ui.layout.Color;
import dev.zmuzy.sbc.ui.layout.FlowLayout;
import dev.zmuzy.sbc.ui.layout.HorizontalAlignment;
import dev.zmuzy.sbc.ui.layout.Insets;
import dev.zmuzy.sbc.ui.layout.Sizing;
import dev.zmuzy.sbc.ui.layout.UIContainers;
import dev.zmuzy.sbc.ui.layout.VerticalAlignment;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

// card saying a newer version is out: the servers message + where to get it. once per game start, over the
// title screen (after setup), or when you join skyblock
public final class UpdateScreen extends SbcScreen {
    public static final String RELEASES = "https://github.com/Skyblock-Companion/SBC/releases";
    public static final String MODRINTH = "https://modrinth.com/mod/skyblockcompanion";
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
        net.minecraft.util.Util.getPlatform().openUri(java.net.URI.create(url));
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
        int half = (w - 20 - 6) / 2;
        FlowLayout links = UIContainers.horizontalFlow(Sizing.fill(100), Sizing.content());
        links.gap(6);
        links.margins(Insets.top(4));
        links.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("GitHub releases"), b -> openLink(RELEASES))).horizontalSizing(Sizing.fixed(half)));
        links.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Modrinth"), b -> openLink(MODRINTH))).horizontalSizing(Sizing.fixed(half)));
        panel.child(links);
        panel.child(SettingsScreen.ui(SbcWidgets.button(Component.literal("Later"), b -> onClose())).horizontalSizing(Sizing.fill(100)));
        root.child(panel);
    }
}
