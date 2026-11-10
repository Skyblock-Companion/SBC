// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// from skyblocker MobGlow + skyhanni EntityDisplayNameEvent through the render state, for crimson isle,
// kuudra and rift. optional, if it fails nothing changes
@Mixin(EntityRenderer.class)
public abstract class AreaGlowMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void sbc$area(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (Safe.resting("AreaGlowMixin.area")) return;
        try {
            int color = io.muzy.sbc.mc.EntityTickCache.color(io.muzy.sbc.mc.EntityTickCache.AREA_GLOW, entity, e -> {
                int c = io.muzy.sbc.areas.AreaHooks.glow(e);
                // never through walls
                return c >= 0 && !io.muzy.sbc.combat.CombatMarks.inSight(e) ? -1 : c;
            });
            if (color >= 0) state.outlineColor = color | 0xFF000000;
            var name = state.nameTag == null ? null : io.muzy.sbc.mc.EntityTickCache.name(io.muzy.sbc.mc.EntityTickCache.NAME_AREA, entity, state.nameTag, io.muzy.sbc.areas.AreaHooks::nameTag);
            if (name != null) state.nameTag = name;
        } catch (Throwable t) {
            Safe.caught("AreaGlowMixin.area", t);
        }
    }
}
