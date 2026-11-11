// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
//? if >=26.3 {
import net.minecraft.world.entity.monster.Enderman;
//?} else {
/*import net.minecraft.world.entity.monster.EnderMan;
*///?}
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// from skyhanni MixinEnderMan + MiscFeatures.onEndermanTeleport: the clients own guess of an enderman
// teleporting. optional, if it fails they jump around like normal
//? if >=26.3 {
@Mixin(Enderman.class)
//?} else {
/*@Mixin(EnderMan.class)
*///?}
public abstract class EndermanTeleportMixin {
    @Inject(method = "teleport(DDD)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$teleport(double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("EndermanTeleportMixin.teleport")) return;
        try {
            if (io.muzy.sbc.combat.MobMarks.hideTeleport()) cir.setReturnValue(false);
        } catch (Throwable t) {
            Safe.caught("EndermanTeleportMixin.teleport", t);
        }
    }
}
