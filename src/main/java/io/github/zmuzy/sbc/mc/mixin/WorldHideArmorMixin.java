// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// from skyhanni MixinHumanoidArmorLayer + MixinCustomHeadLayer through the render state. optional, if it
// fails armor stays
@Mixin(HumanoidMobRenderer.class)
public abstract class WorldHideArmorMixin {
    @Inject(method = "extractHumanoidRenderState", at = @At("TAIL"), require = 0)
    private static void sbc$armor(LivingEntity entity, HumanoidRenderState state, float partialTicks, ItemModelResolver resolver, CallbackInfo ci) {
        if (Safe.resting("WorldHideArmorMixin.armor")) return;
        try {
            io.github.zmuzy.sbc.world.WorldHiders.armor(entity, state);
        } catch (Throwable t) {
            Safe.caught("WorldHideArmorMixin.armor", t);
        }
    }
}
