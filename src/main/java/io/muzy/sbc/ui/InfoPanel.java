// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.Config;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Profile;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

// top right stat plate: sb level, networth, purse, bank, each with its own gem. vanilla scoreboard stays alone
public final class InfoPanel {
    public static final int W = 134;
    private static final int ROW = 12;

    private InfoPanel() {}

    public static boolean active() {
        return Config.I.infoPanel && (GameState.inSkyBlock || SbcMod.DEV);
    }

    static List<String[]> rows() {
        Profile.Data d = Profile.current;
        List<String[]> out = new ArrayList<>();
        String lvl = d != null && d.sbLevel() >= 0 ? String.valueOf(d.sbLevel()) : GameState.sbLevel;
        out.add(new String[]{"Level", lvl.isEmpty() ? "—" : lvl, "FAC775"});
        out.add(new String[]{"Networth", d != null && d.networth() > 0 && d.knows(io.muzy.sbc.core.game.GameProfile.NETWORTH) ? Prices.coins(d.networth()) : "—", "97C459"});
        long purse = GameState.purse >= 0 ? GameState.purse : d != null ? (long) d.purse : -1;
        out.add(new String[]{"Purse", purse >= 0 ? Prices.coins(purse) : "—", "F2D45A"});
        long bank = GameState.bank >= 0 ? GameState.bank : d != null ? (long) d.bank : -1;
        out.add(new String[]{"Bank", bank >= 0 ? Prices.coins(bank) : "—", "85B7EB"});
        return out;
    }

    public static int height() {
        return 14 + rows().size() * ROW;
    }

    // right edge at rightX, top at y
    public static void draw(Ui ui, GuiGraphicsExtractor g, int rightX, int y) {
        ui.g = g;
        List<String[]> rows = rows();
        int h = height(), x = rightX - W;
        Bling.plate(ui, x, y, W, h, 9);
        int cy = y + 8;
        for (String[] r : rows) {
            int col = (int) Long.parseLong(r[2], 16) | 0xFF000000;
            Bling.gem(ui, x + 12, cy + 4, 3, col);
            ui.g.text(ui.font, r[0], x + 21, cy, DripKit.DIM, true);
            ui.g.text(ui.font, r[1], x + W - 9 - ui.width(r[1]), cy, DripKit.TEXT, true);
            cy += ROW;
        }
    }
}
