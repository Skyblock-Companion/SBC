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

// skyhanni CheckRenderEntityEvent for crimson isle and rift: entities they want gone dont get drawn.
// optional, if it fails nothing is hidden
@Mixin(EntityRenderDispatcher.class)
public abstract class AreaHideMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$hide(Entity entity, Frustum frustum, double x, double y, double z, float partialTick, CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("AreaHideMixin.hide")) return;
        try {
            if (dev.zmuzy.sbc.mc.EntityTickCache.test(dev.zmuzy.sbc.mc.EntityTickCache.AREA_HIDE, entity, dev.zmuzy.sbc.areas.AreaHooks::hidden)) cir.setReturnValue(false);
        } catch (Throwable t) {
            Safe.caught("AreaHideMixin.hide", t);
        }
    }
}
