// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.garden.composter.Composter;
import io.github.zmuzy.sbc.garden.contest.Contests;
import io.github.zmuzy.sbc.garden.contest.NextContest;
import io.github.zmuzy.sbc.garden.plot.GardenPlotInfo;
import io.github.zmuzy.sbc.garden.shop.GardenShops;
import io.github.zmuzy.sbc.garden.visitor.VisitorFeatures;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

// registers visitors, composter, contests, plots and shops, and ticks what reads the game
public final class GardenHelpers {
    private static int ticks;

    private GardenHelpers() {}

    public static void keys(KeyMapping.Category category) {
        VisitorFeatures.keys(category);
        GardenPlotInfo.keys(category);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || !GameState.inSkyBlock) return;
        try {
            GardenPlotInfo.keyTick(mc);
            Contests.menuTick(mc);
            if (++ticks % 20 != 0) return;
            Composter.second(mc);
            Contests.second();
            NextContest.second();
            GardenPlotInfo.second(mc);
            GardenShops.second();
        } catch (Throwable t) {
            io.github.zmuzy.sbc.SbcMod.LOG.warn("garden helper tick failed", t);
        }
    }

    public static void register() {
        VisitorFeatures.register();
        Composter.register();
        Contests.register();
        GardenPlotInfo.register();
        GardenShops.register();
        Safe.endTick("GardenHelpers.tick", GardenHelpers::tick);
    }
}
