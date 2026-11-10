// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.Fireball;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// from skyhanni MixinAbstractHurtingProjectile. optional, if it fails the trail stays
@Mixin(AbstractHurtingProjectile.class)
public abstract class WorldHideFireballMixin {
    @Inject(method = "createParticleTrail", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$trail(CallbackInfo ci) {
        if (Safe.resting("WorldHideFireballMixin.trail")) return;
        try {
            if ((Object) this instanceof Fireball && io.github.zmuzy.sbc.world.WorldHiders.hideFireball()) ci.cancel();
        } catch (Throwable t) {
            Safe.caught("WorldHideFireballMixin.trail", t);
        }
    }
}
