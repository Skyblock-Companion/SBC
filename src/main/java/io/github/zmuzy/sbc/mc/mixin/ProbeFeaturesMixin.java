// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.FrameProbe;
import io.github.zmuzy.sbc.core.Safe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;

// /sbc perf record: entities, text and items prepared and drawn
@Mixin(FeatureRenderDispatcher.class)
public abstract class ProbeFeaturesMixin {
    @Inject(method = "prepareFrame", at = @At("HEAD"), require = 0)
    private void sbc$prepareFrameIn(CallbackInfoReturnable<?> ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.FEATURE_PREPARE);
        } catch (Throwable t) {
            Safe.caught("ProbeFeaturesMixin.prepareFrameIn", t);
        }
    }

    @Inject(method = "prepareFrame", at = @At("RETURN"), require = 0)
    private void sbc$prepareFrameOut(CallbackInfoReturnable<?> ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.FEATURE_PREPARE);
        } catch (Throwable t) {
            Safe.caught("ProbeFeaturesMixin.prepareFrameOut", t);
        }
    }

    @Inject(method = "renderAllFeatures", at = @At("HEAD"), require = 0)
    private static void sbc$renderAllFeaturesIn(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.enter(FrameProbe.FEATURE_DRAW);
        } catch (Throwable t) {
            Safe.caught("ProbeFeaturesMixin.renderAllFeaturesIn", t);
        }
    }

    @Inject(method = "renderAllFeatures", at = @At("RETURN"), require = 0)
    private static void sbc$renderAllFeaturesOut(CallbackInfo ci) {
        if (!FrameProbe.on) return;
        try {
            FrameProbe.exit(FrameProbe.FEATURE_DRAW);
        } catch (Throwable t) {
            Safe.caught("ProbeFeaturesMixin.renderAllFeaturesOut", t);
        }
    }
}
