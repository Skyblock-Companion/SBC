// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import io.muzy.sbc.mc.Interactions;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// skyhanni SlotClickEvent: click on a container slot, cancellable before its sent
@Mixin(AbstractContainerScreen.class)
public abstract class ContainerClickMixin {
    @Inject(method = "slotClicked(Lnet/minecraft/world/inventory/Slot;IILnet/minecraft/world/inventory/ContainerInput;)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$slotClicked(Slot slot, int slotId, int button, ContainerInput input, CallbackInfo ci) {
        if (Safe.resting("ContainerClickMixin.slotClicked")) return;
        try {
            if (Interactions.slotClick((AbstractContainerScreen<?>) (Object) this, slot, slotId, button, input)) ci.cancel();
        } catch (Throwable t) {
            Safe.caught("ContainerClickMixin.slotClicked", t);
        }
    }
}
