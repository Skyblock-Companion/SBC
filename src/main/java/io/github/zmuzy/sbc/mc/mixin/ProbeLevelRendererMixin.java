// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.FrameProbe;
import io.github.zmuzy.sbc.core.Safe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.renderer.LevelRenderer;

// /sbc perf record: the world drawn, chunks compiled
@Mixin(LevelRenderer.class)
public abstract class ProbeLevelRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void sbc$renderIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.WORLD_RENDER);
        } catch (Throwable t) {
            Safe.caught("ProbeLevelRendererMixin.renderIn", t);
        }
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void sbc$renderOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.WORLD_RENDER);
        } catch (Throwable t) {
            Safe.caught("ProbeLevelRendererMixin.renderOut", t);
        }
    }

    @Inject(method = "compileSections", at = @At("HEAD"), require = 0)
    private void sbc$compileSectionsIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.CHUNK_COMPILE);
        } catch (Throwable t) {
            Safe.caught("ProbeLevelRendererMixin.compileSectionsIn", t);
        }
    }

    @Inject(method = "compileSections", at = @At("RETURN"), require = 0)
    private void sbc$compileSectionsOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.CHUNK_COMPILE);
        } catch (Throwable t) {
            Safe.caught("ProbeLevelRendererMixin.compileSectionsOut", t);
        }
    }
}
