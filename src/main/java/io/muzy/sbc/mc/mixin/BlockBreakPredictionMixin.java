// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.muzy.sbc.core.Safe;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.BlockBreakingRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// from skyblocker LevelExtractorMixin: cracks on the block youre mining. optional, if it fails the cracks
// stay the servers
@Mixin(LevelExtractor.class)
public abstract class BlockBreakPredictionMixin {
    @WrapOperation(method = "extractBlockDestroyAnimation", at = @At(value = "NEW", target = "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Lnet/minecraft/client/renderer/state/level/BlockBreakingRenderState;"), require = 0)
    private BlockBreakingRenderState sbc$crack(BlockPos pos, BlockState state, int progress, Operation<BlockBreakingRenderState> original) {
        int shown = progress;
        if (!Safe.resting("BlockBreakPredictionMixin.crack")) {
            try {
                shown = io.muzy.sbc.mining.BlockPrediction.progress(pos, state, progress);
            } catch (Throwable t) {
                Safe.caught("BlockBreakPredictionMixin.crack", t);
            }
        }
        return original.call(pos, state, shown);
    }
}
