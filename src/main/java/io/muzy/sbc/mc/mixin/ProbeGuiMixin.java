// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.FrameProbe;
import io.muzy.sbc.core.Safe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.Gui;

// /sbc perf record: the hud and screens read for the frame
@Mixin(Gui.class)
public abstract class ProbeGuiMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
    private void sbc$extractRenderStateIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.GUI_EXTRACT);
        } catch (Throwable t) {
            Safe.caught("ProbeGuiMixin.extractRenderStateIn", t);
        }
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"), require = 0)
    private void sbc$extractRenderStateOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.GUI_EXTRACT);
        } catch (Throwable t) {
            Safe.caught("ProbeGuiMixin.extractRenderStateOut", t);
        }
    }
}
