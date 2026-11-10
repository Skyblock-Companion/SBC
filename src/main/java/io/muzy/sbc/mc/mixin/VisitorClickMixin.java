// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import io.muzy.sbc.garden.visitor.VisitorWorld;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// skyhanni VisitorListener.onCheckRender: hypixels CLICK under a visitor name makes room for its status
@Mixin(EntityRenderDispatcher.class)
public abstract class VisitorClickMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$hideClick(Entity entity, Frustum frustum, double x, double y, double z, float partialTick, CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("VisitorClickMixin.hideClick")) return;
        try {
            if (io.muzy.sbc.mc.EntityTickCache.test(io.muzy.sbc.mc.EntityTickCache.VISITOR_HIDE, entity, VisitorWorld::hidden)) cir.setReturnValue(false);
        } catch (Throwable t) {
            Safe.caught("VisitorClickMixin.hideClick", t);
        }
    }
}
