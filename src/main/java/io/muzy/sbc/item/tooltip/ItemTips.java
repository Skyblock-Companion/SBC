// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.item.tooltip;

import io.muzy.sbc.mc.mixin.GuiGraphicsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.ArrayList;
import java.util.List;

// an items tooltip on our own pages exactly like in a chest or the ah: vanilla lines + every features
// additions (prices etc), in the card look when thats on
// lines a page adds go at the end
public final class ItemTips {

    private ItemTips() {}

    public static void draw(GuiGraphicsExtractor g, ItemStack stack, int mouseX, int mouseY, List<String> extra) {
        Minecraft mc = Minecraft.getInstance();
        if (stack == null || stack.isEmpty() || mc.level == null) return;
        TooltipFlag flag = mc.options.advancedItemTooltips ? TooltipFlag.ADVANCED : TooltipFlag.NORMAL;
        List<Component> lines = new ArrayList<>(stack.getTooltipLines(Item.TooltipContext.of(mc.level), mc.player, flag));
        if (extra != null) for (String s : extra) lines.add(Component.literal(s));
        if (FancyTooltip.active()) {
            List<ClientTooltipComponent> comps = FancyTooltip.build(stack, lines);
            if (comps != null) {
                ((GuiGraphicsAccessor) g).sbc$tooltipLast(mc.font, comps, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, FancyTooltip.style(), true, true);
                return;
            }
        }
        g.setTooltipForNextFrame(mc.font, lines, stack.getTooltipImage(), mouseX, mouseY, stack.get(DataComponents.TOOLTIP_STYLE));
    }
}
