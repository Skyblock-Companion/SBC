// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// from skyblocker ItemModelResolverMixin: model of one item
@Mixin(ItemModelResolver.class)
public abstract class CustomLooksModelMixin {
    @ModifyExpressionValue(method = {"appendItemLayers", "shouldPlaySwapAnimation"}, at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/ItemStack;get(Lnet/minecraft/core/component/DataComponentType;)Ljava/lang/Object;"), require = 0)
    private Object sbc$model(Object original, @Local(argsOnly = true) ItemStack stack) {
        if (Safe.resting("CustomLooksModelMixin.model")) return original;
        try {
            return io.github.zmuzy.sbc.item.custom.CustomLooks.model(original, stack);
        } catch (Throwable t) {
            Safe.caught("CustomLooksModelMixin.model", t);
            return original;
        }
    }
}
