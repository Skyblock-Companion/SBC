// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// skyblocker CompactDamage writes on the entity, this only changes whats drawn. optional, if it fails damage
// numbers stay long
@Mixin(EntityRenderer.class)
public abstract class CompactDamageMixin {
    @Inject(method = "extractRenderState", at = @At("TAIL"), require = 0)
    private void sbc$compact(Entity entity, EntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (Safe.resting("CompactDamageMixin.compact")) return;
        try {
            if (state.nameTag != null && entity instanceof ArmorStand stand) state.nameTag = dev.zmuzy.sbc.mc.EntityTickCache.name(dev.zmuzy.sbc.mc.EntityTickCache.NAME_DAMAGE, entity, state.nameTag, n -> {
                net.minecraft.network.chat.Component out = dev.zmuzy.sbc.pets.PetExtras.nameTag(n);
                return out == null ? null : dev.zmuzy.sbc.combat.CompactDamage.tag(stand, out);
            });
        } catch (Throwable t) {
            Safe.caught("CompactDamageMixin.compact", t);
        }
    }
}
