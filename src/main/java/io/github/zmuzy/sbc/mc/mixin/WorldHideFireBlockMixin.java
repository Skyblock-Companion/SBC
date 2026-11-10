// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// from skyhanni MixinBaseFireBlock. optional, if it fails the particles stay
@Mixin(BaseFireBlock.class)
public abstract class WorldHideFireBlockMixin {
    @WrapWithCondition(method = "animateTick", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"), require = 0)
    private boolean sbc$particle(Level level, ParticleOptions particle, double x, double y, double z, double xd, double yd, double zd) {
        if (Safe.resting("WorldHideFireBlockMixin.particle")) return true;
        try {
            return !io.github.zmuzy.sbc.world.WorldHiders.hideFireBlock();
        } catch (Throwable t) {
            Safe.caught("WorldHideFireBlockMixin.particle", t);
            return true;
        }
    }
}
