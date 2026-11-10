// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import io.muzy.sbc.feature.Features;
import net.minecraft.client.player.FirstPersonHandsAndItems;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// from skyblocker ItemInHandRendererMixin: item whose data changes is still the same item, no equip animation again
@Mixin(FirstPersonHandsAndItems.class)
public abstract class ItemInHandUpdateMixin {
    @Inject(method = "shouldInstantlyReplaceVisibleItem", at = @At("RETURN"), cancellable = true, require = 0)
    private void sbc$noBob(ItemStack from, ItemStack to, LocalPlayer player, CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("ItemInHandUpdateMixin.noBob")) return;
        try {
            if (!cir.getReturnValueZ() && from.getItem() == to.getItem() && Features.on("cancel_item_update")) cir.setReturnValue(true);
        } catch (Throwable t) {
            Safe.caught("ItemInHandUpdateMixin.noBob", t);
        }
    }
}
