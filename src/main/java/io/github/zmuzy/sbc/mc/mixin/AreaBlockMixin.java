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

// from skyblocker WorldEvents.BLOCK_STATE_UPDATE (ClientLevelMixin) + skyhanni ServerBlockChangeEvent, read
// before the change so the old block is still there. optional
@Mixin(ClientLevel.class)
public abstract class AreaBlockMixin {
    @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"), require = 0)
    private void sbc$block(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        if (Safe.resting("AreaBlockMixin.block")) return;
        try {
            io.github.zmuzy.sbc.areas.AreaHooks.onBlockChange(pos, ((ClientLevel) (Object) this).getBlockState(pos), state);
        } catch (Throwable t) {
            Safe.caught("AreaBlockMixin.block", t);
        }
    }
}
