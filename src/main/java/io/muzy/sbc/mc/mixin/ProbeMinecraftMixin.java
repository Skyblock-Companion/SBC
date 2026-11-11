// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.FrameProbe;
import io.muzy.sbc.core.Safe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.Minecraft;

// /sbc perf record: the start of every frame, the game ticks
@Mixin(Minecraft.class)
public abstract class ProbeMinecraftMixin {
    @Inject(method = "runTick", at = @At("HEAD"), require = 0)
    private void sbc$frame(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.frame();
        } catch (Throwable t) {
            Safe.caught("ProbeMinecraftMixin.frame", t);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void sbc$tickIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.count(FrameProbe.C_TICKS, 1);
            FrameProbe.enter(FrameProbe.TICK);
        } catch (Throwable t) {
            Safe.caught("ProbeMinecraftMixin.tickIn", t);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"), require = 0)
    private void sbc$tickOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.TICK);
        } catch (Throwable t) {
            Safe.caught("ProbeMinecraftMixin.tickOut", t);
        }
    }
}
