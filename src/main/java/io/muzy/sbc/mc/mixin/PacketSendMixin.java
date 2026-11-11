// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// from skyhanni FixDoubleClicks: only drop the use on block packet, the item still works through the use item
// packet right after
// cancelling the whole interaction would break these items completely
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class PacketSendMixin {
    @Inject(method = "send(Lnet/minecraft/network/protocol/Packet;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$send(Packet<?> packet, CallbackInfo ci) {
        if (Safe.resting("PacketSendMixin.send")) return;
        try {
            //? if >=26.3 {
            if (packet instanceof ServerboundUseItemOnPacket use && io.muzy.sbc.misc.SmallMisc.dropUseOnBlock(use.hitResult().getBlockPos())) ci.cancel();
            //?} else {
            /*if (packet instanceof ServerboundUseItemOnPacket use && io.muzy.sbc.misc.SmallMisc.dropUseOnBlock(use.getHitResult().getBlockPos())) ci.cancel();
            *///?}
        } catch (Throwable t) {
            Safe.caught("PacketSendMixin.send", t);
        }
    }
}
