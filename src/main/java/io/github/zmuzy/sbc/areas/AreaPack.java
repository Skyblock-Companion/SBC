// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.areas;

import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;

// registers the fishing, foraging + hunting helpers that came after FishForageHunt
public final class AreaPack {
    private AreaPack() {}

    public static void register() {
        Features.add(new Feature("area_hooks", "Misc", "Area helpers", "Keeps what the fishing, foraging and hunting helpers know about the things around you up to date.", "").alwaysOn().tick(FishHooks::tick));
        FishCards.register();
        FishWorld.register();
        ForageHelpers.register();
        HuntHelpers.register();
        HideyhoFinder.register();
        LavaReplacement.register();
        SeaCreatureHider.register();
    }
}
