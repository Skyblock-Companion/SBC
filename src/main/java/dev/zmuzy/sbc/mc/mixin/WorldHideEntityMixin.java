// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// skyhanni CheckRenderEntityEvent for stuff you chose not to see. optional, if it fails nothing is hidden
@Mixin(EntityRenderDispatcher.class)
public abstract class WorldHideEntityMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$hide(Entity entity, Frustum frustum, double x, double y, double z, float partialTick, CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("WorldHideEntityMixin.hide")) return;
        try {
            if (dev.zmuzy.sbc.mc.EntityTickCache.test(dev.zmuzy.sbc.mc.EntityTickCache.WORLD_HIDE, entity, dev.zmuzy.sbc.world.WorldHiders::hidden)) cir.setReturnValue(false);
        } catch (Throwable t) {
            Safe.caught("WorldHideEntityMixin.hide", t);
        }
    }
}
