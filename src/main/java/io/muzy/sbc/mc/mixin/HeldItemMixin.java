// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.render.HeldItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// first person only. the item gets placed before the arm moves and sized right before drawing
// the bare arm takes another path, untouched
@Mixin(FirstPersonHandsAndItemsRenderer.class)
public abstract class HeldItemMixin {
    // the first push of the pose: before the arm, the equip drop and the swing are added
    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 0, shift = At.Shift.AFTER), require = 0)
    private void sbc$place(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector nodes, int light, CallbackInfo ci) {
        if (Safe.resting("HeldItemMixin.place")) return;
        try {
            var me = Minecraft.getInstance().player;
            if (!HeldItem.on() || me == null || stack.isEmpty() || stack.has(net.minecraft.core.component.DataComponents.MAP_ID)) return;
            boolean main = hand == InteractionHand.MAIN_HAND;
            HeldItem.place(pose, main == (me.getMainArm() == HumanoidArm.RIGHT));
        } catch (Throwable t) {
            Safe.caught("HeldItemMixin.place", t);
        }
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"), require = 0)
    private void sbc$size(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partialTick, float pitch, InteractionHand hand, float swing, ItemStack stack, float equip, PoseStack pose, SubmitNodeCollector nodes, int light, CallbackInfo ci) {
        if (Safe.resting("HeldItemMixin.size")) return;
        try {
            if (HeldItem.on()) HeldItem.size(pose);
        } catch (Throwable t) {
            Safe.caught("HeldItemMixin.size", t);
        }
    }

    // swing 0: the item stays put
    @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 2, require = 0)
    private float sbc$swing(float swing) {
        try {
            return HeldItem.on() && !HeldItem.swings() ? 0 : swing;
        } catch (Throwable t) {
            Safe.caught("HeldItemMixin.swing", t);
            return swing;
        }
    }

    @ModifyVariable(method = "submitArmWithItem", at = @At("HEAD"), argsOnly = true, ordinal = 3, require = 0)
    private float sbc$equip(float equip) {
        try {
            return HeldItem.on() && HeldItem.noEquip() ? 0 : equip;
        } catch (Throwable t) {
            Safe.caught("HeldItemMixin.equip", t);
            return equip;
        }
    }
}
