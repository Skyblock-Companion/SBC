// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Safe;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// buttons, item list and value panel of a container go down right before the game draws its tooltip so the
// tooltip ends up on top
// fabrics afterExtract only comes after that tooltip, anything drawn there covers it
@Mixin(Screen.class)
public abstract class ScreenOverlayMixin {
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;extractDeferredElements(IIF)V"), require = 0)
    private void sbc$overlays(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (Safe.resting("ScreenOverlayMixin.overlays")) return;
        try {
            SbcMod.containerOverlays((Screen) (Object) this, g, mouseX, mouseY);
        } catch (Throwable t) {
            Safe.caught("ScreenOverlayMixin.overlays", t);
        }
    }
}
