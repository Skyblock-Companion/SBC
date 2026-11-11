// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.FrameProbe;
import io.muzy.sbc.core.Safe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.network.PacketProcessor;

// /sbc perf record: the packets the server sent this frame
@Mixin(PacketProcessor.class)
public abstract class ProbePacketsMixin {
    @Inject(method = "processQueuedPackets", at = @At("HEAD"), require = 0)
    private void sbc$processQueuedPacketsIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.PACKETS);
        } catch (Throwable t) {
            Safe.caught("ProbePacketsMixin.processQueuedPacketsIn", t);
        }
    }

    @Inject(method = "processQueuedPackets", at = @At("RETURN"), require = 0)
    private void sbc$processQueuedPacketsOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.PACKETS);
        } catch (Throwable t) {
            Safe.caught("ProbePacketsMixin.processQueuedPacketsOut", t);
        }
    }
}
