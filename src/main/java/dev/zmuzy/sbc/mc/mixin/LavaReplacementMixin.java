// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.zmuzy.sbc.core.Safe;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.block.FluidStateModelSet;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

// from skyhanni MixinFluidStateModelSet: lava drawn with waters model when lava replacement is on. optional,
// if it fails lava stays lava
@Mixin(FluidStateModelSet.class)
public abstract class LavaReplacementMixin {
    @Inject(method = "bake", at = @At("RETURN"), require = 0)
    private static void sbc$baked(CallbackInfoReturnable<Map<Fluid, FluidModel>> cir) {
        if (Safe.resting("LavaReplacementMixin.baked")) return;
        try {
            dev.zmuzy.sbc.areas.LavaReplacement.onModelsBaked(cir.getReturnValue());
        } catch (Throwable t) {
            Safe.caught("LavaReplacementMixin.baked", t);
        }
    }

    @ModifyReturnValue(method = "get", at = @At("RETURN"), require = 0)
    private FluidModel sbc$lava(FluidModel original, FluidState state) {
        if (Safe.resting("LavaReplacementMixin.lava")) return original;
        try {
            return dev.zmuzy.sbc.areas.LavaReplacement.model(state, original);
        } catch (Throwable t) {
            Safe.caught("LavaReplacementMixin.lava", t);
            return original;
        }
    }
}
