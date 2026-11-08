// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.item.StorageCache;
import dev.zmuzy.sbc.ui.storage.StorageOverlayMenu;
import dev.zmuzy.sbc.ui.storage.StorageOverlayScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

// like skyblocker: storage menus open as the overlay straight from the server packet so a plain chest never
// flashes, same for party finder
// priority so we go before other mods doing the same and the player sees ours
@Mixin(value = MenuScreens.ScreenConstructor.class, priority = 500)
public interface MenuScreensConstructorMixin<T extends AbstractContainerMenu> {
    @Inject(method = "fromPacket", at = @At("HEAD"), cancellable = true)
    private void sbc$open(Component name, MenuType<T> type, Minecraft client, int id, CallbackInfo ci) {
        if (Safe.resting("MenuScreensConstructorMixin.open")) return;
        try {
            LocalPlayer player = client.player;
            if (player == null || !GameState.inSkyBlock) return;
            T menu = type.create(id, player.getInventory());
            if (!(menu instanceof ChestMenu chest)) return;
            if (dev.zmuzy.sbc.ui.PartyFinderScreen.open(client, chest, name)) {
                ci.cancel();
                return;
            }
            if (dev.zmuzy.sbc.ui.panels.CustomWardrobe.open(client, chest, name) || dev.zmuzy.sbc.ui.panels.CraftingTable.open(client, chest, name)) {
                ci.cancel();
                return;
            }
            String title = name.getString().trim().toLowerCase(Locale.ENGLISH);
            if (!StorageOverlayScreen.enabled(title)) return;
            int height = client.getWindow().getGuiScaledHeight() - (client.getWindow().getGuiScaledHeight() / 5);
            boolean isBackpack = StorageCache.indexFromTitle(title) != -1;
            StorageOverlayMenu overlay = new StorageOverlayMenu(chest, isBackpack, height, player.getInventory());
            player.containerMenu = overlay;
            client.gui.setScreen(new StorageOverlayScreen(overlay, chest, name, player.getInventory(), height));
            ci.cancel();
        } catch (Throwable t) {
            Safe.caught("MenuScreensConstructorMixin.open", t);
        }
    }
}
