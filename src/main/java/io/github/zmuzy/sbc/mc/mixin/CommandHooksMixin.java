// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// from skyblocker GameRendererMixin.onGetNightVisionStrength: the strength /sbc nightvision set
@Mixin(GameRenderer.class)
public abstract class CommandHooksMixin {
    @Inject(method = "nightVisionScale", at = @At("RETURN"), cancellable = true)
    private static void sbc$nightVision(LivingEntity entity, float partialTicks, CallbackInfoReturnable<Float> cir) {
        if (Safe.resting("CommandHooksMixin.nightVision")) return;
        try {
            cir.setReturnValue(io.github.zmuzy.sbc.commands.MoreCommands.nightVisionScale(cir.getReturnValueF()));
        } catch (Throwable t) {
            Safe.caught("CommandHooksMixin.nightVision", t);
        }
    }
}
