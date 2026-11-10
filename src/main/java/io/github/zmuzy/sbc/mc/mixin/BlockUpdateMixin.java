// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// from skyblocker ClientLevelMixin: a block the server sets + what was there before. optional, if it fails
// the block watching features stay quiet
@Mixin(ClientLevel.class)
public abstract class BlockUpdateMixin {
    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"), require = 0)
    private void sbc$blockUpdate(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        if (Safe.resting("BlockUpdateMixin.blockUpdate")) return;
        try {
            io.github.zmuzy.sbc.mc.BlockHooks.on(pos, ((ClientLevel) (Object) this).getBlockState(pos), state);
        } catch (Throwable t) {
            Safe.caught("BlockUpdateMixin.blockUpdate", t);
        }
    }
}
