// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.mining.BlockStrength;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// from skyblocker ItemStackMixin: drill fuel as the item bar. optional, if it fails the item keeps its own bar
@Mixin(ItemStack.class)
public abstract class ItemBarMixin {
    @Inject(method = "isBarVisible", at = @At("RETURN"), cancellable = true, require = 0)
    private void sbc$visible(CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("ItemBarMixin.visible")) return;
        try {
            if (!cir.getReturnValue() && BlockStrength.fill((ItemStack) (Object) this) >= 0) cir.setReturnValue(true);
        } catch (Throwable t) {
            Safe.caught("ItemBarMixin.visible", t);
        }
    }

    @Inject(method = "getBarWidth", at = @At("RETURN"), cancellable = true, require = 0)
    private void sbc$width(CallbackInfoReturnable<Integer> cir) {
        if (Safe.resting("ItemBarMixin.width")) return;
        try {
            float f = BlockStrength.fill((ItemStack) (Object) this);
            if (f >= 0) cir.setReturnValue((int) (Math.min(f, 1f) * 13));
        } catch (Throwable t) {
            Safe.caught("ItemBarMixin.width", t);
        }
    }

    @Inject(method = "getBarColor", at = @At("RETURN"), cancellable = true, require = 0)
    private void sbc$color(CallbackInfoReturnable<Integer> cir) {
        if (Safe.resting("ItemBarMixin.color")) return;
        try {
            float f = BlockStrength.fill((ItemStack) (Object) this);
            if (f >= 0) cir.setReturnValue(BlockStrength.fillColor(f));
        } catch (Throwable t) {
            Safe.caught("ItemBarMixin.color", t);
        }
    }
}
