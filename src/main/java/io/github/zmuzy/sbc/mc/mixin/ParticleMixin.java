// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// hands every particle packet to the mod. the one hook on this method so listeners hear it before anything hides it
// optional, if it fails the feature is off
@Mixin(ClientPacketListener.class)
public abstract class ParticleMixin {
    @Inject(method = "handleParticleEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$particle(ClientboundLevelParticlesPacket packet, CallbackInfo ci) {
        if (Safe.resting("ParticleMixin.particle")) return;
        try {
            if (io.github.zmuzy.sbc.mc.ParticleHooks.hides(packet)) ci.cancel();
        } catch (Throwable t) {
            Safe.caught("ParticleMixin.particle", t);
        }
    }
}
