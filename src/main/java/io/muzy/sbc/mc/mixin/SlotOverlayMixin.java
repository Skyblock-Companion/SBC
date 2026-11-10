// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import io.muzy.sbc.item.SlotInfo;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// rarity glow behind, level numbers + lock marks on top, every slot of every container screen
@Mixin(AbstractContainerScreen.class)
public abstract class SlotOverlayMixin {
    @Inject(method = "extractSlot", at = @At("HEAD"), require = 0)
    private void sbc$back(GuiGraphicsExtractor g, Slot slot, int mx, int my, CallbackInfo ci) {
        if (Safe.resting("SlotOverlayMixin.back")) return;
        try {
            SlotInfo.back(g, slot);
        } catch (Throwable t) {
            Safe.caught("SlotOverlayMixin.back", t);
        }
    }

    @Inject(method = "extractSlot", at = @At("TAIL"), require = 0)
    private void sbc$front(GuiGraphicsExtractor g, Slot slot, int mx, int my, CallbackInfo ci) {
        if (Safe.resting("SlotOverlayMixin.front")) return;
        try {
            SlotInfo.front(g, slot);
            io.muzy.sbc.item.InventorySearch.slot(g, slot);
            io.muzy.sbc.item.MarketOverlay.draw(g, slot);
            io.muzy.sbc.dungeon.DungeonExtras.slot(g, slot);
            io.muzy.sbc.events.hoppity.Hoppity.slot(g, slot);
            io.muzy.sbc.rift.Rift.slot(g, slot);
            io.muzy.sbc.crimson.Crimson.slot(g, slot);
            io.muzy.sbc.item.market.Market.slot(g, slot);
            io.muzy.sbc.item.sacks.Sacks.slot(g, slot);
            io.muzy.sbc.commands.CompactorKey.slot(g, slot);
        } catch (Throwable t) {
            Safe.caught("SlotOverlayMixin.front", t);
        }
    }
}
