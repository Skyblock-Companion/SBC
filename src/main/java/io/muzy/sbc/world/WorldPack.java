// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.world;

import io.muzy.sbc.ui.hud.MoveableVanilla;

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
        io.muzy.sbc.ui.hud.TabWidgetCards.register();
    }
}
