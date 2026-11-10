// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import io.muzy.sbc.item.ArmorDye;
import net.minecraft.client.renderer.entity.layers.EquipmentLayerRenderer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

// swaps in a dyed copy of the stack for the armor layers on your own body
@Mixin(EquipmentLayerRenderer.class)
public abstract class ArmorDyeMixin {
    @ModifyVariable(method = "renderLayers", at = @At("HEAD"), argsOnly = true, require = 0)
    private ItemStack sbc$dye(ItemStack stack) {
        if (Safe.resting("ArmorDyeMixin.dye")) return stack;
        try {
            return ArmorDye.dyed(stack);
        } catch (Throwable t) {
            Safe.caught("ArmorDyeMixin.dye", t);
            return stack;
        }
    }
}
