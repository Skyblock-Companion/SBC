// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// from skyhanni MixinClientLevel: block bits as it breaks. optional, if it fails they stay
@Mixin(ClientLevel.class)
public abstract class WorldHideBlockBreakMixin {
    @Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$destroy(BlockPos pos, BlockState state, CallbackInfo ci) {
        if (Safe.resting("WorldHideBlockBreakMixin.destroy")) return;
        try {
            if (dev.zmuzy.sbc.world.WorldHiders.hideBlockBreak()) ci.cancel();
        } catch (Throwable t) {
            Safe.caught("WorldHideBlockBreakMixin.destroy", t);
        }
    }

    @Inject(method = "addBreakingBlockEffect", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$breaking(BlockPos pos, Direction direction, CallbackInfo ci) {
        if (Safe.resting("WorldHideBlockBreakMixin.breaking")) return;
        try {
            if (dev.zmuzy.sbc.world.WorldHiders.hideBlockBreak()) ci.cancel();
        } catch (Throwable t) {
            Safe.caught("WorldHideBlockBreakMixin.breaking", t);
        }
    }
}
