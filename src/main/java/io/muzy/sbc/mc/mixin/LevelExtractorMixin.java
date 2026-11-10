// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// adds our path line + waypoint gizmos right before the game hands its gizmos to the renderer
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Inject(method = "extractGizmos", at = @At("HEAD"))
    private void sbc$emitWaypoints(CallbackInfo ci) {
        if (Safe.resting("LevelExtractorMixin.emitWaypoints")) return;
        // drawing the route must never crash the game
        try (var ignored = ((LevelExtractor) (Object) this).collectPerFrameMainThreadGizmos()) {
            long t0 = io.muzy.sbc.core.Perf.begin();
            io.muzy.sbc.feature.Features.world(new io.muzy.sbc.render.WorldDraw(Minecraft.getInstance()));
            io.muzy.sbc.core.Perf.end("world:all features", t0);
        } catch (Throwable t) {
            Safe.caught("LevelExtractorMixin.emitWaypoints", t);
        }
    }
}
