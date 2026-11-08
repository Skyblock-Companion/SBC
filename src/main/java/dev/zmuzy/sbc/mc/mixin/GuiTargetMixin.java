// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.zmuzy.sbc.ui.HudTexture;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// the gui renderer draws into the hud texture while HudTexture asks for it
@Mixin(GuiRenderer.class)
public abstract class GuiTargetMixin {
    @WrapOperation(method = "draw", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;mainRenderTarget()Lcom/mojang/blaze3d/pipeline/RenderTarget;"), require = 0)
    private RenderTarget sbc$hudTarget(GameRenderer gameRenderer, Operation<RenderTarget> original) {
        try {
            RenderTarget t = HudTexture.redirect;
            if (t != null) return t;
        } catch (Throwable e) {
            dev.zmuzy.sbc.core.Safe.caught("GuiTargetMixin.hudTarget", e);
        }
        return original.call(gameRenderer);
    }
}
