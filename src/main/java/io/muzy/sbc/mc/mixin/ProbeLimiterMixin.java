// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.FrameProbe;
import io.muzy.sbc.core.Safe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.FramerateLimiter;

// /sbc perf record: the wait for the frame cap
@Mixin(FramerateLimiter.class)
public abstract class ProbeLimiterMixin {
    @Inject(method = "limitDisplayFPS", at = @At("HEAD"), require = 0)
    private static void sbc$limitDisplayFPSIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.LIMITER);
        } catch (Throwable t) {
            Safe.caught("ProbeLimiterMixin.limitDisplayFPSIn", t);
        }
    }

    @Inject(method = "limitDisplayFPS", at = @At("RETURN"), require = 0)
    private static void sbc$limitDisplayFPSOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.LIMITER);
        } catch (Throwable t) {
            Safe.caught("ProbeLimiterMixin.limitDisplayFPSOut", t);
        }
    }
}
