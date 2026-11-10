// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.ui.UiFeatures;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.EffectsInInventory;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// from skyblocker InventoryScreenMixin: hides the potion effect list next to the inventory in skyblock
@Mixin(InventoryScreen.class)
public abstract class InventoryEffectsMixin {
    @Redirect(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/inventory/EffectsInInventory;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;II)V"),
            require = 0)
    private void sbc$hideEffects(EffectsInInventory effects, GuiGraphicsExtractor g, int mouseX, int mouseY) {
        boolean hide = false;
        try {
            hide = UiFeatures.hidePotionEffects();
        } catch (Throwable t) {
            Safe.caught("InventoryEffectsMixin.hideEffects", t);
        }
        if (!hide) effects.extractRenderState(g, mouseX, mouseY);
    }

    // from skyblocker InventoryScreenMixin: with equipment next to the player the offhand slot moves right,
    // recipe book button too
    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "getRecipeBookButtonPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/navigation/ScreenPosition;<init>(II)V"), index = 0, require = 0)
    private int sbc$moveBookButton(int x) {
        if (Safe.resting("InventoryEffectsMixin.moveBookButton")) return x;
        try {
            return io.github.zmuzy.sbc.ui.panels.EquipmentSlots.on() ? x + 21 : x;
        } catch (Throwable t) {
            Safe.caught("InventoryEffectsMixin.moveBookButton", t);
            return x;
        }
    }

    @Inject(method = "extractBackground", at = @At("TAIL"), require = 0)
    private void sbc$equipmentBack(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (Safe.resting("InventoryEffectsMixin.equipmentBack")) return;
        try {
            io.github.zmuzy.sbc.ui.panels.EquipmentSlots.background(g, (ContainerAccess) (Object) this);
        } catch (Throwable t) {
            Safe.caught("InventoryEffectsMixin.equipmentBack", t);
        }
    }

    @Inject(method = "showsActiveEffects", at = @At("RETURN"), cancellable = true, require = 0)
    private void sbc$noEffectsZone(CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("InventoryEffectsMixin.noEffectsZone")) return;
        try {
            if (UiFeatures.hidePotionEffects()) cir.setReturnValue(false);
        } catch (Throwable t) {
            Safe.caught("InventoryEffectsMixin.noEffectsZone", t);
        }
    }
}
