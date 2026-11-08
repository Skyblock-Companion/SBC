// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.world;

import dev.zmuzy.sbc.ui.hud.MoveableVanilla;

// registers what gets drawn in the world + the small display bits
public final class WorldPack {
    private WorldPack() {}

    public static void register() {
        TeleportOverlay.register();
        GyroOverlay.register();
        SmoothTeleport.register();
        GlowingMushrooms.register();
        WorldHiders.register();
        WorldMarks.register();
        DisplayCards.register();
        SkyblockXpBar.register();
        MoveableVanilla.register();
        dev.zmuzy.sbc.ui.hud.TabWidgetCards.register();
    }
}
