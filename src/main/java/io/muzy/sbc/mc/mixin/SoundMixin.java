// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// hands every sound packet to the mod (skyhanni PlaySoundEvent), a listener can mute it. optional, if it
// fails sound features are off
@Mixin(ClientPacketListener.class)
public abstract class SoundMixin {
    @Inject(method = "handleSoundEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$sound(ClientboundSoundPacket packet, CallbackInfo ci) {
        if (Safe.resting("SoundMixin.sound")) return;
        try {
            if (io.muzy.sbc.mc.SoundHooks.on(packet)) ci.cancel();
        } catch (Throwable t) {
            Safe.caught("SoundMixin.sound", t);
        }
    }
}
