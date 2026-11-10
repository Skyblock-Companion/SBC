// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// skyhanni PacketReceivedEvent for the shadow assassins world border. dungeon particle drops go through ParticleMixin
// each method runs twice, only the game thread pass counts
@Mixin(ClientPacketListener.class)
public abstract class DungeonPacketsMixin {
    @Inject(method = "handleInitializeBorder", at = @At("HEAD"), require = 0)
    private void sbc$border(ClientboundInitializeBorderPacket packet, CallbackInfo ci) {
        if (Safe.resting("DungeonPacketsMixin.border")) return;
        try {
            if (Minecraft.getInstance().isSameThread()) io.github.zmuzy.sbc.dungeon.DungeonAlerts.onBorder(packet);
        } catch (Throwable t) {
            Safe.caught("DungeonPacketsMixin.border", t);
        }
    }
}
