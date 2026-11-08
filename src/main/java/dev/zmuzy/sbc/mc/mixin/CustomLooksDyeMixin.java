// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.zmuzy.sbc.core.Safe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// from skyblocker DyedItemColorMixin: dye of one item, animated or not
@Mixin(DyedItemColor.class)
public abstract class CustomLooksDyeMixin {
    @ModifyReturnValue(method = "getOrDefault", at = @At("RETURN"), require = 0)
    private static int sbc$dye(int original, ItemStack stack, int fallback) {
        if (Safe.resting("CustomLooksDyeMixin.dye")) return original;
        try {
            return dev.zmuzy.sbc.item.custom.CustomLooks.dye(stack, original);
        } catch (Throwable t) {
            Safe.caught("CustomLooksDyeMixin.dye", t);
            return original;
        }
    }
}
