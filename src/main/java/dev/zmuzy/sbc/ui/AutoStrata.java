// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui;

import net.minecraft.client.renderer.state.gui.GuiRenderState;

// new frame layer every n pieces while one of our screens draws, like Ui.strataEvery for the hud
// the game checks each piece against every piece in its layer, so hundreds of pieces = hundreds squared
public final class AutoStrata {
    public static int every;
    private static int count;

    private AutoStrata() {}

    public static void start(int n) {
        every = n;
        count = 0;
    }

    public static void stop() {
        every = 0;
    }

    public static void piece(GuiRenderState s) {
        if (every > 0 && ++count >= every) {
            count = 0;
            s.nextStratum();
        }
    }
}
