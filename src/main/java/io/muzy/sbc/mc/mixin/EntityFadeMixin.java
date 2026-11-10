// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

// skyhanni EntityTransparencyManager, done through what the game already does for an invisible mob a player can see
// body drawn see through with a fixed tint, here the settings tint. armor + held items stay
// optional, if it fails nothing is see through
@Mixin(LivingEntityRenderer.class)
public abstract class EntityFadeMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void sbc$fade(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (Safe.resting("EntityFadeMixin.fade")) return;
        try {
            if (io.muzy.sbc.mc.EntityTickCache.test(io.muzy.sbc.mc.EntityTickCache.FADE, entity, e -> io.muzy.sbc.combat.CombatMarks.faded(e))) {
                state.isInvisible = true;
                state.isInvisibleToPlayer = false;
            }
        } catch (Throwable t) {
            Safe.caught("EntityFadeMixin.fade", t);
        }
    }

    @ModifyConstant(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            constant = @Constant(intValue = 654311423), require = 0)
    private int sbc$tint(int vanilla, LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (Safe.resting("EntityFadeMixin.tint")) return vanilla;
        try {
            return io.muzy.sbc.combat.CombatMarks.fadeTint(vanilla);
        } catch (Throwable t) {
            Safe.caught("EntityFadeMixin.tint", t);
            return vanilla;
        }
    }
}
