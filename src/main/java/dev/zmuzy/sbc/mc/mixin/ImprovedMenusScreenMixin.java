// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.item.menus.ImprovedMenus;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

// from skyhanni MixinContainerScreen. optional, if it fails the chest stays as is
@Mixin(ContainerScreen.class)
public abstract class ImprovedMenusScreenMixin {
    @ModifyArg(method = "extractBackground", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIII)V"), index = 1, require = 0)
    private Identifier sbc$background(Identifier sprite) {
        if (Safe.resting("ImprovedMenusScreenMixin.background")) return sprite;
        try {
            return ImprovedMenus.background(sprite);
        } catch (Throwable t) {
            Safe.caught("ImprovedMenusScreenMixin.background", t);
            return sprite;
        }
    }
}
