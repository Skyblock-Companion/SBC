// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import io.muzy.sbc.item.tooltip.FancyTooltip;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// the card draws its own panel
@Mixin(TooltipRenderUtil.class)
public class TooltipBackgroundMixin {
    @Inject(method = "extractTooltipBackground", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sbc$frame(GuiGraphicsExtractor g, int x, int y, int w, int h, Identifier style, CallbackInfo ci) {
        if (Safe.resting("TooltipBackgroundMixin.frame")) return;
        try {
            if (!FancyTooltip.rarityStyle(style)) return;
            FancyTooltip.drawFrame(g, x, y, w, h);
            ci.cancel();
        } catch (Throwable t) {
            Safe.caught("TooltipBackgroundMixin.frame", t);
        }
    }
}
