// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// skyblocker MobGlow through the render state: entities the dungeon helpers want glowing get an outline
// color. optional, if it fails nothing glows
@Mixin(EntityRenderer.class)
public abstract class EntityGlowMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void sbc$glow(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (Safe.resting("EntityGlowMixin.glow")) return;
        try {
            int color = io.muzy.sbc.mc.EntityTickCache.color(io.muzy.sbc.mc.EntityTickCache.GLOW, entity, e -> {
                int c = io.muzy.sbc.dungeon.DungeonObjects.glow(e);
                if (c < 0) c = io.muzy.sbc.areas.FishHooks.glowOf(e);
                if (c < 0) c = io.muzy.sbc.combat.CombatMarks.glow(e);
                if (c < 0) c = io.muzy.sbc.chat.PartyMarks.glow(e);
                // never through walls
                return c >= 0 && !io.muzy.sbc.combat.CombatMarks.inSight(e) ? -1 : c;
            });
            if (color >= 0) state.outlineColor = color | 0xFF000000;
        } catch (Throwable t) {
            Safe.caught("EntityGlowMixin.glow", t);
        }
    }
}
