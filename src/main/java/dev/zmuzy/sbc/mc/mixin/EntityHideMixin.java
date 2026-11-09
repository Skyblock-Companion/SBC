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

// skyhanni CheckRenderEntityEvent for the dungeon hiders: entities they want gone dont get drawn. optional,
// if it fails nothing is hidden
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityHideMixin {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$hide(Entity entity, Frustum frustum, double x, double y, double z, float partialTick, CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("EntityHideMixin.hide")) return;
        try {
            if (dev.zmuzy.sbc.mc.EntityTickCache.test(dev.zmuzy.sbc.mc.EntityTickCache.HIDE, entity, e -> dev.zmuzy.sbc.mc.EmptyStands.hidden(e) || dev.zmuzy.sbc.misc.FireFreeze.hidden(e) || dev.zmuzy.sbc.dungeon.DungeonObjects.hidden(e)
                    || dev.zmuzy.sbc.areas.FishHooks.hiddenNow(e) || dev.zmuzy.sbc.combat.CombatMarks.hidden(e))) cir.setReturnValue(false);
        } catch (Throwable t) {
            Safe.caught("EntityHideMixin.hide", t);
        }
    }
}
