// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.misc;

import net.minecraft.client.KeyMapping;

// stuff that fit no other area: this packages helpers, registered in one place
public final class MiscPack {
    private MiscPack() {}

    public static void keys(KeyMapping.Category category) {
        SmallMisc.keys(category);
    }

    public static void register() {
        SmallMisc.register();
        Limbo.register();
        AbilityCards.register();
        FireFreeze.register();
        EnchantedClock.register();
        FruitBowl.register();
        FrogMask.register();
        NonGodEffects.register();
        Trevor.register();
        BarnSolvers.register();
        IslandAreas.register();
        RelicRoute.register();
        CarryTracker.register();
        SbHelpers.register();
        TooltipTexts.register();
    }
}
