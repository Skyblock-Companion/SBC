// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.garden.visitor.VisitorWorld;
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
    private void sbc$hideClick(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("VisitorClickMixin.hideClick")) return;
        try {
            if (dev.zmuzy.sbc.mc.EntityTickCache.test(dev.zmuzy.sbc.mc.EntityTickCache.VISITOR_HIDE, entity, VisitorWorld::hidden)) cir.setReturnValue(false);
        } catch (Throwable t) {
            Safe.caught("VisitorClickMixin.hideClick", t);
        }
    }
}
