// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.FrameProbe;
import io.github.zmuzy.sbc.core.Safe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

// /sbc perf record: entities ticked
@Mixin(ClientLevel.class)
public abstract class ProbeClientLevelMixin {
    @Inject(method = "tickEntities", at = @At("HEAD"), require = 0)
    private void sbc$tickEntitiesIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.ENTITY_TICK);
        } catch (Throwable t) {
            Safe.caught("ProbeClientLevelMixin.tickEntitiesIn", t);
        }
    }

    @Inject(method = "tickEntities", at = @At("RETURN"), require = 0)
    private void sbc$tickEntitiesOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.ENTITY_TICK);
        } catch (Throwable t) {
            Safe.caught("ProbeClientLevelMixin.tickEntitiesOut", t);
        }
    }

    @Inject(method = "tickNonPassenger", at = @At("HEAD"), require = 0)
    private void sbc$ticked(Entity entity, CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.count(FrameProbe.C_TICKED, 1);
        } catch (Throwable t) {
            Safe.caught("ProbeClientLevelMixin.ticked", t);
        }
    }
}
