// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.mc.Interactions;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// skyhanni MixinMinecraft.handleLeftClickMouse: left click, also at air or a mob. nothing cancelled here
@Mixin(Minecraft.class)
public abstract class LeftClickMixin {
    @Shadow
    public HitResult hitResult;

    @Shadow
    private int missTime;

    @Inject(method = "startAttack", at = @At("HEAD"), require = 0)
    private void sbc$startAttack(CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("LeftClickMixin.startAttack")) return;
        try {
            if (missTime <= 0) Interactions.leftClick(hitResult);
        } catch (Throwable t) {
            Safe.caught("LeftClickMixin.startAttack", t);
        }
    }
}
