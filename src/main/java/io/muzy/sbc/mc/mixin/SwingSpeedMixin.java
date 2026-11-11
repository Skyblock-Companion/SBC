// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import io.muzy.sbc.render.HeldItem;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
//? if >=26.3 {
import net.minecraft.world.item.component.SwingAnimation;
//?} else {
/*import net.minecraft.world.item.ItemStack;
*///?}
import org.spongepowered.asm.mixin.Mixin;
//? if >=26.3 {
import org.spongepowered.asm.mixin.Unique;
//?}
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if <26.3 {
/*import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
*///?}
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// your own swing with an item in hand: how long it lasts + that it finishes. visual only
// a bare hand swings like vanilla. the length is fixed when a swing starts, so it is set there
@Mixin(LivingEntity.class)
public abstract class SwingSpeedMixin {
    //? if >=26.3 {
    @Unique
    private InteractionHand sbc$hand = InteractionHand.MAIN_HAND;

    @Unique
    private boolean sbc$mine(InteractionHand hand) {
    //?} else {
    /*private ItemStack sbc$held() {
    *///?}
        LivingEntity self = (LivingEntity) (Object) this;
        //? if >=26.3 {
        return self instanceof LocalPlayer && HeldItem.on() && !self.getItemInHand(hand).isEmpty();
        //?} else {
        /*if (!(self instanceof LocalPlayer) || !HeldItem.on()) return ItemStack.EMPTY;
        return self.getItemInHand(self.swingingArm != null ? self.swingingArm : InteractionHand.MAIN_HAND);
        *///?}
    }

    //? if >=26.3 {
    // vanilla restarts the swing from half way on every click so a slow swing jumps around
    // we let the current one finish, fast clicking stays one smooth motion
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$keepSwing(InteractionHand hand, SwingAnimation animation, boolean send, CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("SwingSpeedMixin.keepSwing")) return;
        try {
            sbc$hand = hand;
            if (((LivingEntity) (Object) this).isSwinging() && sbc$mine(hand)) cir.setReturnValue(false);
        } catch (Throwable t) {
            Safe.caught("SwingSpeedMixin.keepSwing", t);
        }
    }

    @Inject(method = "getModifiedSwingDuration", at = @At("RETURN"), cancellable = true, require = 0)
    private void sbc$swingSpeed(SwingAnimation animation, CallbackInfoReturnable<Integer> cir) {
    //?} else {
    /*@Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true, require = 0)
    private void sbc$swingSpeed(CallbackInfoReturnable<Integer> cir) {
    *///?}
        if (Safe.resting("SwingSpeedMixin.swingSpeed")) return;
        try {
            //? if >=26.3 {
            if (!sbc$mine(sbc$hand)) return;
            int base = HeldItem.ignoreHaste() ? animation.duration() : cir.getReturnValueI();
            //?} else {
            /*ItemStack held = sbc$held();
            if (held.isEmpty()) return;
            int base = HeldItem.ignoreHaste() ? held.getSwingAnimation().duration() : cir.getReturnValueI();
            *///?}
            cir.setReturnValue(HeldItem.duration(base));
        } catch (Throwable t) {
            Safe.caught("SwingSpeedMixin.swingSpeed", t);
        }
    }
    //? if <26.3 {

    /*// vanilla restarts the swing from half way on every click so a slow swing jumps around
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
    *///?}
}
