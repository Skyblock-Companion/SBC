// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.item.menus.ImprovedMenus;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// from skyhanni MixinAbstractContainerScreen, the BetterContainers bits: filler panes not drawn or lit up,
// labels take the style color. optional
@Mixin(AbstractContainerScreen.class)
public abstract class ImprovedMenusSlotMixin {
    @Inject(method = "extractSlot", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$slot(GuiGraphicsExtractor g, Slot slot, int x, int y, CallbackInfo ci) {
        if (Safe.resting("ImprovedMenusSlotMixin.slot")) return;
        try {
            if (!ImprovedMenus.drawsSlot(slot)) ci.cancel();
        } catch (Throwable t) {
            Safe.caught("ImprovedMenusSlotMixin.slot", t);
        }
    }

    @ModifyArg(method = "extractLabels", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"), index = 4, require = 0)
    private int sbc$label(int colour) {
        if (Safe.resting("ImprovedMenusSlotMixin.label")) return colour;
        try {
            return ImprovedMenus.labelColor(colour);
        } catch (Throwable t) {
            Safe.caught("ImprovedMenusSlotMixin.label", t);
            return colour;
        }
    }

    @WrapOperation(method = "extractSlotHighlightBack", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;isHighlightable()Z"), require = 0)
    private boolean sbc$back(Slot slot, Operation<Boolean> original) {
        boolean vanilla = original.call(slot);
        if (Safe.resting("ImprovedMenusSlotMixin.back")) return vanilla;
        try {
            return ImprovedMenus.highlights(slot, vanilla);
        } catch (Throwable t) {
            Safe.caught("ImprovedMenusSlotMixin.back", t);
            return vanilla;
        }
    }

    @WrapOperation(method = "extractSlotHighlightFront", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/inventory/Slot;isHighlightable()Z"), require = 0)
    private boolean sbc$front(Slot slot, Operation<Boolean> original) {
        boolean vanilla = original.call(slot);
        if (Safe.resting("ImprovedMenusSlotMixin.front")) return vanilla;
        try {
            return ImprovedMenus.highlights(slot, vanilla);
        } catch (Throwable t) {
            Safe.caught("ImprovedMenusSlotMixin.front", t);
            return vanilla;
        }
    }
}
