// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.zmuzy.sbc.core.Safe;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// from skyhanni MixinBlaze, same for endermen: the particles they make client side. optional, if it fails they stay
@Mixin({Blaze.class, EnderMan.class})
public abstract class WorldHideParticlesMixin {
    @WrapWithCondition(method = "aiStep", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"), require = 0)
    private boolean sbc$particle(Level level, ParticleOptions particle, double x, double y, double z, double xd, double yd, double zd) {
        if (Safe.resting("WorldHideParticlesMixin.particle")) return true;
        try {
            return !((Object) this instanceof Blaze ? dev.zmuzy.sbc.world.WorldHiders.hideBlaze() : dev.zmuzy.sbc.world.WorldHiders.hideEnderman());
        } catch (Throwable t) {
            Safe.caught("WorldHideParticlesMixin.particle", t);
            return true;
        }
    }
}
