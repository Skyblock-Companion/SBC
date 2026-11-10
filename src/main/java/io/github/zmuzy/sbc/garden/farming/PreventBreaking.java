// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.farming;

import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.garden.GardenPack;
import io.github.zmuzy.sbc.garden.milestones.CropType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;

// from skyhanni NoBreak: holding a rod or sprayonator, left clicking a crop does nothing. only blocks your own click
public final class PreventBreaking {
    public static final String ID = "garden_no_break";

    private PreventBreaking() {}

    // NoBreakItem
    static boolean blocks(boolean rod, boolean sprayonator, boolean rodOn, boolean sprayOn) {
        return rod && rodOn || sprayonator && sprayOn;
    }

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Prevent breaking crops", "Stops you from breaking crops while you hold a fishing rod or a Sprayonator.", "SkyHanni: Prevent Breaking Crops")
                .when(GardenPack::garden).defaultOff()
                .option(Option.bool("garden_no_break.rod", "With a fishing rod", "", true))
                .option(Option.bool("garden_no_break.sprayonator", "With a Sprayonator", "", true)));
        Safe.attackBlock("PreventBreaking.attackBlock", (player, level, hand, pos, direction) -> {
            if (!level.isClientSide() || !Features.on(ID) || !GardenPack.garden()) return InteractionResult.PASS;
            var mc = Minecraft.getInstance();
            boolean night = mc.level != null && mc.level.getOverworldClockTime() % 24000 >= 12000;
            if (CropType.fromBlock(BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock()).getPath(), night) == null) return InteractionResult.PASS;
            boolean rod = player.getMainHandItem().is(Items.FISHING_ROD), spray = GardenPack.sprayonatorMinutes(GardenPack.heldId()) > 0;
            return blocks(rod, spray, GardenPack.on(ID, "rod"), GardenPack.on(ID, "sprayonator")) ? InteractionResult.FAIL : InteractionResult.PASS;
        });
    }
}
