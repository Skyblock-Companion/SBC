// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.muzy.sbc.core.Safe;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// from skyblocker ItemStackMixin.customItemNames
@Mixin(ItemStack.class)
public abstract class CustomLooksNameMixin {
    @ModifyReturnValue(method = "getHoverName", at = @At("RETURN"), require = 0)
    private Component sbc$name(Component original) {
        if (Safe.resting("CustomLooksNameMixin.name")) return original;
        try {
            return io.muzy.sbc.item.custom.CustomLooks.name((ItemStack) (Object) this, original);
        } catch (Throwable t) {
            Safe.caught("CustomLooksNameMixin.name", t);
            return original;
        }
    }
}
