// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.render.HeldItem;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// your own swing with an item in hand: how long it lasts + that it finishes. visual only
// the swing packet still goes out every click. a bare hand swings like vanilla
@Mixin(LivingEntity.class)
public abstract class SwingSpeedMixin {
    private ItemStack sbc$held() {
        LivingEntity self = (LivingEntity) (Object) this;
        if (!(self instanceof LocalPlayer) || !HeldItem.on()) return ItemStack.EMPTY;
        return self.getItemInHand(self.swingingArm != null ? self.swingingArm : InteractionHand.MAIN_HAND);
    }

    @Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true, require = 0)
    private void sbc$swingSpeed(CallbackInfoReturnable<Integer> cir) {
        if (Safe.resting("SwingSpeedMixin.swingSpeed")) return;
        try {
            ItemStack held = sbc$held();
            if (held.isEmpty()) return;
            int base = HeldItem.ignoreHaste() ? held.getSwingAnimation().duration() : cir.getReturnValueI();
            cir.setReturnValue(HeldItem.duration(base));
        } catch (Throwable t) {
            Safe.caught("SwingSpeedMixin.swingSpeed", t);
        }
    }

    // vanilla restarts the swing from half way on every click so a slow swing jumps around
    // we let the current one finish, fast clicking stays one smooth motion
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$keepSwing(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        if (Safe.resting("SwingSpeedMixin.keepSwing")) return;
        try {
            LivingEntity self = (LivingEntity) (Object) this;
            if (!self.swinging || !(self instanceof LocalPlayer) || !HeldItem.on() || self.getItemInHand(hand).isEmpty()) return;
            ci.cancel();
        } catch (Throwable t) {
            Safe.caught("SwingSpeedMixin.keepSwing", t);
        }
    }
}
