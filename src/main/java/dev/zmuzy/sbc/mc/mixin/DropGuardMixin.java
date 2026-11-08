// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.item.SlotLocks;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// drop key (Q) respects locked slots and protected items
@Mixin(LocalPlayer.class)
public abstract class DropGuardMixin {
    @Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$noDrop(boolean all, CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("DropGuardMixin.noDrop")) return;
        try {
            if (SlotLocks.blocksDrop((LocalPlayer) (Object) this)) cir.setReturnValue(false);
        } catch (Throwable t) {
            Safe.caught("DropGuardMixin.noDrop", t);
        }
    }
}
