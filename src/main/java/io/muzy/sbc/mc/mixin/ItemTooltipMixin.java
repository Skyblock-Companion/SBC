// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import io.muzy.sbc.item.tooltip.FancyTooltip;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// item card shows wherever its tooltip does, also on non chest screens like the storage overlay
@Mixin(GuiGraphicsExtractor.class)
public abstract class ItemTooltipMixin {
    @Inject(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Lnet/minecraft/world/item/ItemStack;II)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$item(Font font, ItemStack stack, int x, int y, CallbackInfo ci) {
        if (Safe.resting("ItemTooltipMixin.item")) return;
        try {
            if (!FancyTooltip.active()) return;
            List<ClientTooltipComponent> comps = FancyTooltip.build(stack, Screen.getTooltipFromItem(Minecraft.getInstance(), stack));
            if (comps == null) return;
            //? if >=26.3 {
            ((GuiGraphicsAccessor) this).sbc$tooltipLast(font, comps, x, y, DefaultTooltipPositioner.INSTANCE, FancyTooltip.style(), true, true);
            //?} else {
            /*((GuiGraphicsAccessor) this).sbc$tooltipLast(font, comps, x, y, DefaultTooltipPositioner.INSTANCE, FancyTooltip.style(), true);
            *///?}
            ci.cancel();
        } catch (Throwable t) {
            Safe.caught("ItemTooltipMixin.item", t);
        }
    }
}
