// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// from skyhanni TeleportPadCompactName through the render state, only whats drawn changes. optional
@Mixin(EntityRenderer.class)
public abstract class WorldNameTagMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void sbc$name(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (Safe.resting("WorldNameTagMixin.name")) return;
        try {
            if (state.nameTag != null) state.nameTag = dev.zmuzy.sbc.mc.EntityTickCache.name(dev.zmuzy.sbc.mc.EntityTickCache.NAME_WORLD, entity, state.nameTag, n -> dev.zmuzy.sbc.world.WorldMarks.nameTag(entity, n));
        } catch (Throwable t) {
            Safe.caught("WorldNameTagMixin.name", t);
        }
    }
}
