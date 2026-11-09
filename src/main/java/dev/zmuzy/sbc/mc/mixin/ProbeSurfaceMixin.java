// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.FrameProbe;
import dev.zmuzy.sbc.core.Safe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.renderpearl.frontend.FrontendGpuSurface;

// /sbc perf record: the frame handed to the graphics card
@Mixin(FrontendGpuSurface.class)
public abstract class ProbeSurfaceMixin {
    @Inject(method = "present", at = @At("HEAD"), require = 0)
    private void sbc$presentIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.PRESENT);
        } catch (Throwable t) {
            Safe.caught("ProbeSurfaceMixin.presentIn", t);
        }
    }

    @Inject(method = "present", at = @At("RETURN"), require = 0)
    private void sbc$presentOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.PRESENT);
        } catch (Throwable t) {
            Safe.caught("ProbeSurfaceMixin.presentOut", t);
        }
    }
}
