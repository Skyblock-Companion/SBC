// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.FrameProbe;
import io.muzy.sbc.core.Safe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.GameRenderer;

// /sbc perf record: the frame's extract and render halves
@Mixin(GameRenderer.class)
public abstract class ProbeGameRendererMixin {
    @Inject(method = "extract", at = @At("HEAD"), require = 0)
    private void sbc$extractIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.EXTRACT);
        } catch (Throwable t) {
            Safe.caught("ProbeGameRendererMixin.extractIn", t);
        }
    }

    @Inject(method = "extract", at = @At("RETURN"), require = 0)
    private void sbc$extractOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.EXTRACT);
        } catch (Throwable t) {
            Safe.caught("ProbeGameRendererMixin.extractOut", t);
        }
    }

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void sbc$renderIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.RENDER);
        } catch (Throwable t) {
            Safe.caught("ProbeGameRendererMixin.renderIn", t);
        }
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void sbc$renderOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.RENDER);
        } catch (Throwable t) {
            Safe.caught("ProbeGameRendererMixin.renderOut", t);
        }
    }
}
