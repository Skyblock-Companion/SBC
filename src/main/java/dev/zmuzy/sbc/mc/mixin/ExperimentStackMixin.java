// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.item.menus.Experiments;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// from skyblocker modifyDisplayStack: in experiments a slot can show the card or number it holds under a blank
@Mixin(AbstractContainerScreen.class)
public abstract class ExperimentStackMixin {
    @Unique
    private Slot sbc$drawing;

    @Inject(method = "extractSlot", at = @At("HEAD"), require = 0)
    private void sbc$slot(net.minecraft.client.gui.GuiGraphicsExtractor g, Slot slot, int mx, int my, CallbackInfo ci) {
        sbc$drawing = slot;
    }

    @ModifyArg(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fakeItem(Lnet/minecraft/world/item/ItemStack;III)V"), index = 0, require = 0)
    private ItemStack sbc$fake(ItemStack stack) {
        if (Safe.resting("ExperimentStackMixin.fake")) return stack;
        try {
            return Experiments.display(sbc$drawing, stack);
        } catch (Throwable t) {
            Safe.caught("ExperimentStackMixin.fake", t);
            return stack;
        }
    }

    @ModifyArg(method = "extractSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;item(Lnet/minecraft/world/item/ItemStack;III)V"), index = 0, require = 0)
    private ItemStack sbc$item(ItemStack stack) {
        if (Safe.resting("ExperimentStackMixin.item")) return stack;
        try {
            return Experiments.display(sbc$drawing, stack);
        } catch (Throwable t) {
            Safe.caught("ExperimentStackMixin.item", t);
            return stack;
        }
    }
}
