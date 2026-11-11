// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.item.ArmorDye;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// marks the part of rendering where the body being drawn is the local players
@Mixin(LivingEntityRenderer.class)
public abstract class AvatarSubmitMixin {
    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("HEAD"), require = 0)
    private void sbc$begin(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (Safe.resting("AvatarSubmitMixin.begin")) return;
        try {
            var mc = Minecraft.getInstance();
            ArmorDye.drawingSelf = state instanceof AvatarRenderState a && mc.player != null && a.id == mc.player.getId();
        } catch (Throwable t) {
            Safe.caught("AvatarSubmitMixin.begin", t);
        }
    }

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("RETURN"), require = 0)
    private void sbc$end(LivingEntityRenderState state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        ArmorDye.drawingSelf = false;
    }
}
