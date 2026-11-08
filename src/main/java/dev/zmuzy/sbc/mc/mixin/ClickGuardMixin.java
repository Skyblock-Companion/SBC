// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.item.SlotLocks;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// blocks clicks that would move or drop a locked slot or protected item
@Mixin(MultiPlayerGameMode.class)
public abstract class ClickGuardMixin {
    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$guard(int containerId, int slotNum, int button, ContainerInput input, Player player, CallbackInfo ci) {
        if (Safe.resting("ClickGuardMixin.guard")) return;
        try {
            if (SlotLocks.blocks(slotNum, button, input, player)) ci.cancel();
        } catch (Throwable t) {
            Safe.caught("ClickGuardMixin.guard", t);
        }
    }
}
