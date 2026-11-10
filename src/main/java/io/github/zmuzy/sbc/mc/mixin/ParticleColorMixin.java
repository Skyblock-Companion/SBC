// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// from skyhanni ColorParticleFix: 1.8.9 kept particle color in the speed fields and hypixel still sends it
// there, each channel inverted lol. off unless asked
@Mixin(ClientboundLevelParticlesPacket.class)
public abstract class ParticleColorMixin {
    @Inject(method = "particle", at = @At("RETURN"), cancellable = true, require = 0)
    @SuppressWarnings("unchecked")
    private void sbc$colour(CallbackInfoReturnable<ParticleOptions> cir) {
        if (Safe.resting("ParticleColorMixin.colour")) return;
        try {
            if (!(cir.getReturnValue() instanceof ColorParticleOption option) || !io.github.zmuzy.sbc.misc.SmallMisc.fixParticles()) return;
            ClientboundLevelParticlesPacket self = (ClientboundLevelParticlesPacket) (Object) this;
            cir.setReturnValue(ColorParticleOption.create((ParticleType<ColorParticleOption>) option.getType(), ARGB.colorFromFloat(option.getAlpha(), 1 - self.xDist(), 1 - self.yDist(), 1 - self.zDist())));
        } catch (Throwable t) {
            Safe.caught("ParticleColorMixin.colour", t);
        }
    }
}
