// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.item.tooltip.FancyTooltip;
import dev.zmuzy.sbc.item.tooltip.TooltipScroll;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// item tooltip of every container screen. drawn by FancyTooltip when thats on, moved by TooltipScroll when
// its taller than the screen
// everything else falls through to vanilla
@Mixin(AbstractContainerScreen.class)
public abstract class TooltipMixin extends Screen {
    @Shadow protected Slot hoveredSlot;
    @Shadow protected AbstractContainerMenu menu;

    @Shadow protected abstract List<Component> getTooltipFromContainerItem(ItemStack stack);

    protected TooltipMixin(Component title) {
        super(title);
    }

    // vanillas own tooltip follows these
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "extractTooltip", at = @At("HEAD"), argsOnly = true, ordinal = 0, require = 0)
    private int sbc$tipX(int mouseX) {
        if (Safe.resting("TooltipMixin.tipX")) return mouseX;
        try {
            return dev.zmuzy.sbc.events.hoppity.CFHelpers.tipX(mouseX);
        } catch (Throwable t) {
            Safe.caught("TooltipMixin.tipX", t);
            return mouseX;
        }
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "extractTooltip", at = @At("HEAD"), argsOnly = true, ordinal = 1, require = 0)
    private int sbc$tipY(int mouseY) {
        if (Safe.resting("TooltipMixin.tipY")) return mouseY;
        try {
            return dev.zmuzy.sbc.events.hoppity.CFHelpers.tipY(mouseY);
        } catch (Throwable t) {
            Safe.caught("TooltipMixin.tipY", t);
            return mouseY;
        }
    }

    @Inject(method = "extractTooltip", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$tooltip(GuiGraphicsExtractor g, int mouseX, int mouseY, CallbackInfo ci) {
        if (Safe.resting("TooltipMixin.tooltip")) return;
        try {
            mouseX = dev.zmuzy.sbc.events.hoppity.CFHelpers.tipX(mouseX);
            mouseY = dev.zmuzy.sbc.events.hoppity.CFHelpers.tipY(mouseY);
            if (dev.zmuzy.sbc.item.menus.MenuExtras.noTooltip(hoveredSlot)) {
                ci.cancel();
                return;
            }
            if (hoveredSlot != null && dev.zmuzy.sbc.garden.greenhouse.DnaSolver.hidesTip(this.title.getString().trim(), hoveredSlot.index)) {
                ci.cancel();
                return;
            }
            boolean fancy = FancyTooltip.active(), scroll = TooltipScroll.active();
            if (!fancy && !scroll) return;
            if (hoveredSlot == null || !hoveredSlot.hasItem() || !menu.getCarried().isEmpty()) return;
            ItemStack st = hoveredSlot.getItem();
            Font f = this.font;
            List<Component> lines = getTooltipFromContainerItem(st);
            int dy = scroll ? TooltipScroll.offsetY(st, hoveredSlot.index) : 0, dx = scroll ? TooltipScroll.offsetX() : 0;
            List<ClientTooltipComponent> comps = fancy ? FancyTooltip.build(st, lines) : null;
            if (comps != null) {
                TooltipScroll.lastHeight = FancyTooltip.height(comps, f);
                int w = 0;
                for (ClientTooltipComponent c : comps) w = Math.max(w, c.getWidth(f));
                TooltipScroll.lastWidth = w + 8;
                // drawn at the very end of the frame like vanilla tooltips, over the item list, buttons, everything
                ((GuiGraphicsAccessor) g).sbc$tooltipLast(f, comps, mouseX + dx, mouseY, TooltipScroll.positioner(dy), FancyTooltip.style(), true);
                ci.cancel();
                return;
            }
            if (scroll) {
                List<ClientTooltipComponent> plain = new java.util.ArrayList<>();
                for (Component c : lines) plain.add(ClientTooltipComponent.create(c.getVisualOrderText()));
                st.getTooltipImage().ifPresent(img -> plain.add(Math.min(1, plain.size()), ClientTooltipComponent.create(img)));
                // measured the way vanilla measures it (10 a line, not the font's 9)
                int h = plain.size() == 1 ? -2 : 0, w = 0;
                for (ClientTooltipComponent c : plain) {
                    h += c.getHeight(f);
                    w = Math.max(w, c.getWidth(f));
                }
                TooltipScroll.lastHeight = h + 8;
                TooltipScroll.lastWidth = w + 8;
                // taller than the screen: vanilla would pin the bottom to the screen and drop the scroll, so we place it here
                if (dy != 0 || dx != 0 || TooltipScroll.tall(g.guiHeight())) {
                    ((GuiGraphicsAccessor) g).sbc$tooltipLast(f, plain, mouseX + dx, mouseY, TooltipScroll.positioner(dy), st.get(DataComponents.TOOLTIP_STYLE), true);
                    ci.cancel();
                }
            }
        } catch (Throwable t) {
            Safe.caught("TooltipMixin.tooltip", t);
        }
    }
}
