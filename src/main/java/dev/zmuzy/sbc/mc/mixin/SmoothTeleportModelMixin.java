// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// from skyblocker EntityRendererMixin: in third person your model slides along with the camera. optional
@Mixin(EntityRenderer.class)
public abstract class SmoothTeleportModelMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void sbc$model(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (Safe.resting("SmoothTeleportModelMixin.model")) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (entity != mc.player || mc.options.getCameraType().isFirstPerson()) return;
            Vec3 pos = dev.zmuzy.sbc.world.SmoothTeleport.player(partialTicks);
            if (pos == null) return;
            state.x = pos.x;
            state.y = pos.y;
            state.z = pos.z;
        } catch (Throwable t) {
            Safe.caught("SmoothTeleportModelMixin.model", t);
        }
    }
}
