// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// from skyhanni MixinMouseHandler: mouse turning goes through mouse lock first
@Mixin(MouseHandler.class)
public abstract class MouseTurnMixin {
    @ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 0, require = 0)
    private double sbc$turnX(double x) {
        if (Safe.resting("MouseTurnMixin.turnX")) return x;
        try {
            return io.github.zmuzy.sbc.garden.farming.MouseLock.remap(x);
        } catch (Throwable t) {
            Safe.caught("MouseTurnMixin.turnX", t);
            return x;
        }
    }

    @ModifyArg(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"), index = 1, require = 0)
    private double sbc$turnY(double y) {
        if (Safe.resting("MouseTurnMixin.turnY")) return y;
        try {
            return io.github.zmuzy.sbc.garden.farming.MouseLock.remap(y);
        } catch (Throwable t) {
            Safe.caught("MouseTurnMixin.turnY", t);
            return y;
        }
    }
}
