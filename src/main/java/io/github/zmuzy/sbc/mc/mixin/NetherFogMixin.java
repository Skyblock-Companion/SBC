// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// from skyblocker FogRendererMixin: on crimson isle the fog stays as far as in the overworld. optional
@Mixin(FogRenderer.class)
public abstract class NetherFogMixin {
    @Inject(method = "setupFog", at = @At(value = "FIELD", target = "Lnet/minecraft/client/renderer/fog/FogData;renderDistanceEnd:F", opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER), require = 0)
    private void sbc$fog(CallbackInfoReturnable<FogData> cir, @Local FogData fog) {
        if (Safe.resting("NetherFogMixin.fog")) return;
        try {
            if (!io.github.zmuzy.sbc.crimson.CrimsonMarks.extendFog()) return;
            fog.environmentalStart = Float.MAX_VALUE;
            fog.environmentalEnd = Float.MAX_VALUE;
            fog.renderDistanceStart = Float.MAX_VALUE;
            fog.renderDistanceEnd = Float.MAX_VALUE;
        } catch (Throwable t) {
            Safe.caught("NetherFogMixin.fog", t);
        }
    }
}
