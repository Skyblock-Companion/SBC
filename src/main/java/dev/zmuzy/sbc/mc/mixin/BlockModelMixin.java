// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import net.minecraft.client.renderer.block.BlockStateModelSet;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// from skyhanni MixinBlockStateModelSet: ores by commission look like another block. optional, if it fails
// ores look normal
@Mixin(BlockStateModelSet.class)
public abstract class BlockModelMixin {
    @Inject(method = "get", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$model(BlockState state, CallbackInfoReturnable<BlockStateModel> cir) {
        if (Safe.resting("BlockModelMixin.model")) return;
        try {
            BlockState other = dev.zmuzy.sbc.mining.CommissionBlocks.processState(state);
            if (other != state) cir.setReturnValue(((BlockStateModelSet) (Object) this).get(other));
        } catch (Throwable t) {
            Safe.caught("BlockModelMixin.model", t);
        }
    }
}
