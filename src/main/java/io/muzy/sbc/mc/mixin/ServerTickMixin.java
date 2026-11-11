// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundPingPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// from skyhanni MinecraftData: hypixel sends a ping packet every server tick. hooked at the end of the
// handler, on the game thread
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ServerTickMixin {
    @Inject(method = "handlePing", at = @At("TAIL"))
    private void sbc$ping(ClientboundPingPacket packet, CallbackInfo ci) {
        if (Safe.resting("ServerTickMixin.ping")) return;
        try {
            io.muzy.sbc.commands.ServerStats.onServerTick(packet.getId());
        } catch (Throwable t) {
            Safe.caught("ServerTickMixin.ping", t);
        }
    }
}
