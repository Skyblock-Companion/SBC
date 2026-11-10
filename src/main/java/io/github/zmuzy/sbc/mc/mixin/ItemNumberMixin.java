// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.item.AbilityCooldowns;
import io.github.zmuzy.sbc.item.ItemNumbers;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// a number on an items icon where the stack count goes. hotbar, chests and our own screens alike
@Mixin(GuiGraphicsExtractor.class)
public abstract class ItemNumberMixin {
    @ModifyVariable(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("HEAD"), argsOnly = true, require = 0)
    private String sbc$number(String countText, Font font, ItemStack stack) {
        if (Safe.resting("ItemNumberMixin.number")) return countText;
        try {
            String cooldown = AbilityCooldowns.textOn(stack);
            if (cooldown != null) return cooldown;
            if (countText != null) return countText;
            return ItemNumbers.of(stack);
        } catch (Throwable t) {
            Safe.caught("ItemNumberMixin.number", t);
            return countText;
        }
    }

    // from skyhanni ItemAbilityCooldown.onRenderItem: color behind an item while its ability is on cooldown
    @Inject(method = "itemDecorations(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;IILjava/lang/String;)V", at = @At("HEAD"), require = 0)
    private void sbc$cooldownBackground(Font font, ItemStack stack, int x, int y, String countText, CallbackInfo ci) {
        if (Safe.resting("ItemNumberMixin.cooldownBackground")) return;
        try {
            int color = AbilityCooldowns.backgroundOn(stack);
            if (color != 0) ((GuiGraphicsExtractor) (Object) this).fill(x, y, x + 16, y + 16, color);
            io.github.zmuzy.sbc.item.menus.ItemMarks.draw((GuiGraphicsExtractor) (Object) this, font, stack, x, y);
        } catch (Throwable t) {
            Safe.caught("ItemNumberMixin.cooldownBackground", t);
        }
    }
}
