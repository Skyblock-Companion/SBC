// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import io.muzy.sbc.item.SlotLocks;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// drop key (Q) respects locked slots and protected items
@Mixin(MultiPlayerGameMode.class)
public abstract class DropGuardMixin {
    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$noDrop(LocalPlayer player, boolean all, CallbackInfo ci) {
        if (Safe.resting("DropGuardMixin.noDrop")) return;
        try {
            if (SlotLocks.blocksDrop(player)) ci.cancel();
        } catch (Throwable t) {
            Safe.caught("DropGuardMixin.noDrop", t);
        }
    }
}
