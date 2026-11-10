// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.ui.storage.StorageOverlayScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

// like skyblocker: when the open pages items change, redo the search marks
@Mixin(AbstractContainerMenu.class)
public abstract class StorageOverlayMenuMixin {
    @Inject(method = "setItem", at = @At("RETURN"))
    private void sbc$onSetItem(int slot, int revision, ItemStack stack, CallbackInfo ci) {
        sbc$refresh();
    }

    @Inject(method = "initializeContents", at = @At("RETURN"))
    private void sbc$onInitializeContents(int stateId, List<ItemStack> items, ItemStack carried, CallbackInfo ci) {
        sbc$refresh();
    }

    private void sbc$refresh() {
        if (Safe.resting("StorageOverlayMenuMixin.refresh")) return;
        try {
            if ((Object) this instanceof ChestMenu && Minecraft.getInstance().gui.screen() instanceof StorageOverlayScreen screen) screen.refreshSearch();
        } catch (Throwable t) {
            Safe.caught("StorageOverlayMenuMixin.refresh", t);
        }
    }
}
