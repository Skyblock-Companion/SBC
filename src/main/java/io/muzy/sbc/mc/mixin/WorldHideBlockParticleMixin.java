// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// from skyhanni MixinParticleEngine: block particles the server asks for. optional, if it fails they stay
@Mixin(ParticleEngine.class)
public abstract class WorldHideBlockParticleMixin {
    @Inject(method = "createParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)Lnet/minecraft/client/particle/Particle;", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$create(ParticleOptions options, double x, double y, double z, double xd, double yd, double zd, CallbackInfoReturnable<Particle> cir) {
        if (Safe.resting("WorldHideBlockParticleMixin.create")) return;
        try {
            if (options.getType() == ParticleTypes.BLOCK && io.muzy.sbc.world.WorldHiders.hideBlockBreak()) cir.setReturnValue(null);
        } catch (Throwable t) {
            Safe.caught("WorldHideBlockParticleMixin.create", t);
        }
    }
}
