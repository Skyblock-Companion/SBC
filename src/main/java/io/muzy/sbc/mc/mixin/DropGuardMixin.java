// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import io.muzy.sbc.item.SlotLocks;
//? if >=26.3 {
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
//?}
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if >=26.3 {
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?} else {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?}

// drop key (Q) respects locked slots and protected items
//? if >=26.3 {
@Mixin(MultiPlayerGameMode.class)
//?} else {
/*@Mixin(LocalPlayer.class)
*///?}
public abstract class DropGuardMixin {
    //? if >=26.3 {
    @Inject(method = "dropItem", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$noDrop(LocalPlayer player, boolean all, CallbackInfo ci) {
    //?} else {
    /*@Inject(method = "drop(Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$noDrop(boolean all, CallbackInfoReturnable<Boolean> cir) {
    *///?}
        if (Safe.resting("DropGuardMixin.noDrop")) return;
        try {
            //? if >=26.3 {
            if (SlotLocks.blocksDrop(player)) ci.cancel();
            //?} else {
            /*if (SlotLocks.blocksDrop((LocalPlayer) (Object) this)) cir.setReturnValue(false);
            *///?}
        } catch (Throwable t) {
            Safe.caught("DropGuardMixin.noDrop", t);
        }
    }
}
