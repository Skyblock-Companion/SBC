// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.FrameProbe;
import io.muzy.sbc.core.Safe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.systems.RenderSystem;

// /sbc perf record: window and input events
@Mixin(RenderSystem.class)
public abstract class ProbeSystemMixin {
    @Inject(method = "pollEvents", at = @At("HEAD"), require = 0)
    private static void sbc$pollEventsIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.POLL);
        } catch (Throwable t) {
            Safe.caught("ProbeSystemMixin.pollEventsIn", t);
        }
    }

    @Inject(method = "pollEvents", at = @At("RETURN"), require = 0)
    private static void sbc$pollEventsOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.POLL);
        } catch (Throwable t) {
            Safe.caught("ProbeSystemMixin.pollEventsOut", t);
        }
    }
}
