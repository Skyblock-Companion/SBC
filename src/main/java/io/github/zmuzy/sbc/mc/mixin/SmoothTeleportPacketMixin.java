// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// from skyblocker beforeTeleport. optional, if it fails teleports just jump
@Mixin(ClientPacketListener.class)
public abstract class SmoothTeleportPacketMixin {
    @Inject(method = "handleMovePlayer", at = @At("HEAD"), require = 0)
    private void sbc$teleport(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        if (Safe.resting("SmoothTeleportPacketMixin.teleport")) return;
        try {
            io.github.zmuzy.sbc.world.SmoothTeleport.playerGoingToTeleport();
        } catch (Throwable t) {
            Safe.caught("SmoothTeleportPacketMixin.teleport", t);
        }
    }
}
