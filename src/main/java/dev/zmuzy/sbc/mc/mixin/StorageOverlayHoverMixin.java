// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.ui.storage.StorageOverlayScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// the pages slots under the search box dont take clicks. method is private so cant just override it
@Mixin(AbstractContainerScreen.class)
public abstract class StorageOverlayHoverMixin {
    @Inject(method = "isHovering(Lnet/minecraft/world/inventory/Slot;DD)Z", at = @At("HEAD"), cancellable = true)
    private void sbc$reachable(Slot slot, double x, double y, CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("StorageOverlayHoverMixin.reachable")) return;
        try {
            if ((Object) this instanceof StorageOverlayScreen s && !s.slotReachable(slot, x, y)) cir.setReturnValue(false);
        } catch (Throwable t) {
            Safe.caught("StorageOverlayHoverMixin.reachable", t);
        }
    }
}
