// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
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
    //? if >=26.3 {
    private void sbc$hide(Entity entity, Frustum frustum, double x, double y, double z, float partialTick, CallbackInfoReturnable<Boolean> cir) {
    //?} else {
    /*private void sbc$hide(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
    *///?}
        if (Safe.resting("EntityHideMixin.hide")) return;
        try {
            if (io.muzy.sbc.mc.EntityTickCache.test(io.muzy.sbc.mc.EntityTickCache.HIDE, entity, e -> io.muzy.sbc.mc.EmptyStands.hidden(e) || io.muzy.sbc.misc.FireFreeze.hidden(e) || io.muzy.sbc.dungeon.DungeonObjects.hidden(e)
                    || io.muzy.sbc.areas.FishHooks.hiddenNow(e) || io.muzy.sbc.combat.CombatMarks.hidden(e))) cir.setReturnValue(false);
        } catch (Throwable t) {
            Safe.caught("EntityHideMixin.hide", t);
        }
    }
}
