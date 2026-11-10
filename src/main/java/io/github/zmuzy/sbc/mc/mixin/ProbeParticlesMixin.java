// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.FrameProbe;
import io.github.zmuzy.sbc.core.Safe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.particle.ParticleEngine;

// /sbc perf record: particles ticked and read
@Mixin(ParticleEngine.class)
public abstract class ProbeParticlesMixin {
    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void sbc$tickIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.PARTICLE_TICK);
        } catch (Throwable t) {
            Safe.caught("ProbeParticlesMixin.tickIn", t);
        }
    }

    @Inject(method = "tick", at = @At("RETURN"), require = 0)
    private void sbc$tickOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.PARTICLE_TICK);
        } catch (Throwable t) {
            Safe.caught("ProbeParticlesMixin.tickOut", t);
        }
    }

    @Inject(method = "extract", at = @At("HEAD"), require = 0)
    private void sbc$extractIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.PARTICLE_EXTRACT);
        } catch (Throwable t) {
            Safe.caught("ProbeParticlesMixin.extractIn", t);
        }
    }

    @Inject(method = "extract", at = @At("RETURN"), require = 0)
    private void sbc$extractOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.PARTICLE_EXTRACT);
        } catch (Throwable t) {
            Safe.caught("ProbeParticlesMixin.extractOut", t);
        }
    }
}
