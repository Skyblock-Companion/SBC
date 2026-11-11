// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.areas;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.util.Map;

// from skyhanni fishing/LavaReplacement: lava drawn with the water texture so you can see the hook when lava
// fishing. the chunks get rebuilt when it turns on or off
public final class LavaReplacement {
    public static final String ID = "fishing_lava_replacement";
    private static volatile boolean active;
    private static volatile FluidModel opaqueWater;

    private LavaReplacement() {}

    public static void onModelsBaked(Map<Fluid, FluidModel> models) {
        FluidModel water = models.get(Fluids.WATER);
        opaqueWater = water == null ? null : new FluidModel(ChunkSectionLayer.SOLID, water.stillMaterial(), water.flowingMaterial(), water.overlayMaterial(), water.tintSource());
    }

    public static FluidModel model(FluidState state, FluidModel original) {
        if (!active) return original;
        FluidModel water = opaqueWater;
        return water != null && (state.getType() == Fluids.LAVA || state.getType() == Fluids.FLOWING_LAVA) ? water : original;
    }

    private static boolean opt(String key) {
        return Features.get(ID).option(key).bool();
    }

    // from skyhanni shouldReplace
    static boolean here(String island, boolean everywhere, boolean kuudra, boolean catacombs, boolean crimson) {
        return everywhere || (kuudra && "KUUDRA".equals(island)) || (catacombs && "CATACOMBS".equals(island)) || (crimson && "CRIMSON_ISLE".equals(island));
    }

    private static void set(boolean now) {
        if (now == active) return;
        active = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) mc.levelExtractor.allChanged();
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Fishing", "Lava replacement", "Draws lava with the water texture, so you see your hook while lava fishing.", "SkyHanni: Lava Replacement")
                .when(() -> GameState.inSkyBlock).defaultOff()
                .tick(mc -> {
                    if (mc.player != null && mc.player.tickCount % 20 == 0) set(here(GameState.island, opt("fishing_lava_replacement.everywhere"), opt("fishing_lava_replacement.kuudra"), opt("fishing_lava_replacement.catacombs"), opt("fishing_lava_replacement.crimson")));
                })
                .left(() -> set(false))
                .option(Option.bool("fishing_lava_replacement.everywhere", "Everywhere", "On all islands, whatever the three below say.", true))
                .option(Option.bool("fishing_lava_replacement.kuudra", "In Kuudra", "", false))
                .option(Option.bool("fishing_lava_replacement.catacombs", "In dungeons", "", false))
                .option(Option.bool("fishing_lava_replacement.crimson", "On the Crimson Isle", "", false)));
    }
}
