// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// skyhanni fire overlay hider + skyblocker ScreenEffectRendererMixin flame sizes. optional, if it fails the fire stays
@Mixin(ScreenEffectRenderer.class)
public abstract class WorldFireOverlayMixin {
    @Inject(method = "submitFire", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sbc$fire(PoseStack pose, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
        if (Safe.resting("WorldFireOverlayMixin.fire")) return;
        try {
            if (io.github.zmuzy.sbc.world.WorldHiders.hideFireOverlay()) ci.cancel();
        } catch (Throwable t) {
            Safe.caught("WorldFireOverlayMixin.fire", t);
        }
    }

    @ModifyArg(method = "buildFireQuad", index = 4, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;buildSpriteQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;FFFFFI)V"), require = 0)
    private static float sbc$y1(float y) {
        if (Safe.resting("WorldFireOverlayMixin.y1")) return y;
        try {
            return io.github.zmuzy.sbc.world.WorldHiders.flameY(y);
        } catch (Throwable t) {
            Safe.caught("WorldFireOverlayMixin.y1", t);
            return y;
        }
    }

    @ModifyArg(method = "buildFireQuad", index = 6, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;buildSpriteQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;FFFFFI)V"), require = 0)
    private static float sbc$y2(float y) {
        if (Safe.resting("WorldFireOverlayMixin.y2")) return y;
        try {
            return io.github.zmuzy.sbc.world.WorldHiders.flameY(y);
        } catch (Throwable t) {
            Safe.caught("WorldFireOverlayMixin.y2", t);
            return y;
        }
    }

    @ModifyArg(method = "buildFireQuad", index = 8, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;buildSpriteQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;FFFFFI)V"), require = 0)
    private static int sbc$colour(int colour) {
        if (Safe.resting("WorldFireOverlayMixin.colour")) return colour;
        try {
            return io.github.zmuzy.sbc.world.WorldHiders.flameColour(colour);
        } catch (Throwable t) {
            Safe.caught("WorldFireOverlayMixin.colour", t);
            return colour;
        }
    }
}
