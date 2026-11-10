// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.FrameProbe;
import io.muzy.sbc.core.Safe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.world.entity.Entity;

// /sbc perf record: the world and its entities read for the frame
@Mixin(LevelExtractor.class)
public abstract class ProbeLevelExtractorMixin {
    @Inject(method = "extract", at = @At("HEAD"), require = 0)
    private void sbc$extractIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.WORLD_EXTRACT);
        } catch (Throwable t) {
            Safe.caught("ProbeLevelExtractorMixin.extractIn", t);
        }
    }

    @Inject(method = "extract", at = @At("RETURN"), require = 0)
    private void sbc$extractOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.WORLD_EXTRACT);
        } catch (Throwable t) {
            Safe.caught("ProbeLevelExtractorMixin.extractOut", t);
        }
    }

    @Inject(method = "extractVisibleEntities", at = @At("HEAD"), require = 0)
    private void sbc$extractVisibleEntitiesIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.ENTITY_EXTRACT);
        } catch (Throwable t) {
            Safe.caught("ProbeLevelExtractorMixin.extractVisibleEntitiesIn", t);
        }
    }

    @Inject(method = "extractVisibleEntities", at = @At("RETURN"), require = 0)
    private void sbc$extractVisibleEntitiesOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.ENTITY_EXTRACT);
        } catch (Throwable t) {
            Safe.caught("ProbeLevelExtractorMixin.extractVisibleEntitiesOut", t);
        }
    }

    @Inject(method = "extractEntity", at = @At("HEAD"), require = 0)
    private void sbc$entity(Entity entity, float partialTick, CallbackInfoReturnable<?> ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.count(FrameProbe.C_ENTITIES, 1);
            if (entity instanceof net.minecraft.world.entity.player.Player) FrameProbe.count(FrameProbe.C_PLAYERS, 1);
            else if (entity instanceof net.minecraft.world.entity.decoration.ArmorStand) FrameProbe.count(FrameProbe.C_STANDS, 1);
        } catch (Throwable t) {
            Safe.caught("ProbeLevelExtractorMixin.entity", t);
        }
    }
}
