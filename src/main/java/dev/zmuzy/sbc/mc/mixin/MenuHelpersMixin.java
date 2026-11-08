// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Features;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

// from skyhanni fixMiddleClick: middle click in a menu goes out like in creative, hypixel menus read that
@Mixin(AbstractContainerScreen.class)
public abstract class MenuHelpersMixin {
    @Redirect(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;hasInfiniteMaterials()Z"), require = 0)
    private boolean sbc$middleClick(LocalPlayer player) {
        boolean vanilla = player.hasInfiniteMaterials();
        try {
            return vanilla || Features.on("middle_click_fix");
        } catch (Throwable t) {
            Safe.caught("MenuHelpersMixin.middleClick", t);
            return vanilla;
        }
    }
}
