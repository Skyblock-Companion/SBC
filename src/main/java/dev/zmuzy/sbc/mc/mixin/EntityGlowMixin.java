// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
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
            int color = dev.zmuzy.sbc.mc.EntityTickCache.color(dev.zmuzy.sbc.mc.EntityTickCache.GLOW, entity, e -> {
                int c = dev.zmuzy.sbc.dungeon.DungeonObjects.glow(e);
                if (c < 0) c = dev.zmuzy.sbc.areas.FishHooks.glowOf(e);
                if (c < 0) c = dev.zmuzy.sbc.combat.CombatMarks.glow(e);
                if (c < 0) c = dev.zmuzy.sbc.chat.PartyMarks.glow(e);
                return c;
            });
            if (color >= 0) state.outlineColor = color | 0xFF000000;
        } catch (Throwable t) {
            Safe.caught("EntityGlowMixin.glow", t);
        }
    }
}
