// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// from skyblocker HudMixin.hideSafariColdOverlay. optional, if it fails the frost stays
@Mixin(Hud.class)
public abstract class ColdOverlayMixin {
    @ModifyExpressionValue(method = "extractCameraOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getTicksFrozen()I"), require = 0)
    private int sbc$frost(int ticks) {
        if (Safe.resting("ColdOverlayMixin.frost")) return ticks;
        try {
            return ticks > 0 && io.github.zmuzy.sbc.areas.HuntHelpers.hidesCold() ? 0 : ticks;
        } catch (Throwable t) {
            Safe.caught("ColdOverlayMixin.frost", t);
            return ticks;
        }
    }
}
