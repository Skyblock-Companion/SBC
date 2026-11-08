// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// skyhanni TitleReceivedEvent + MobEvent.Hurt for crimson isle and rift. hiding particles goes through ParticleMixin
// each method runs twice, only the game thread pass counts. optional
@Mixin(ClientPacketListener.class)
public abstract class AreaPacketsMixin {
    @Inject(method = "setTitleText", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$title(ClientboundSetTitleTextPacket packet, CallbackInfo ci) {
        if (Safe.resting("AreaPacketsMixin.title")) return;
        try {
            if (dev.zmuzy.sbc.areas.AreaHooks.onTitle(packet.text())) ci.cancel();
        } catch (Throwable t) {
            Safe.caught("AreaPacketsMixin.title", t);
        }
    }

    @Inject(method = "handleDamageEvent", at = @At("HEAD"), require = 0)
    private void sbc$damage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        if (Safe.resting("AreaPacketsMixin.damage")) return;
        try {
            dev.zmuzy.sbc.areas.AreaHooks.onDamage(packet);
        } catch (Throwable t) {
            Safe.caught("AreaPacketsMixin.damage", t);
        }
    }
}
