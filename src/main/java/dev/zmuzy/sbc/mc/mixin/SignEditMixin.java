// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.ui.PartyFinderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// like skyblocker: the sign hypixel opens for a level range gets answered inside party finder
@Mixin(value = LocalPlayer.class, priority = 500)
public abstract class SignEditMixin {
    @Inject(method = "openTextEdit", at = @At("HEAD"), cancellable = true)
    private void sbc$sign(SignBlockEntity sign, boolean front, CallbackInfo ci) {
        if (Safe.resting("SignEditMixin.sign")) return;
        try {
            if (Minecraft.getInstance().gui.screen() instanceof PartyFinderScreen screen && screen.onSign(sign, front)) ci.cancel();
            else if (dev.zmuzy.sbc.ui.MarketSearchScreen.onSign(sign, front)) ci.cancel();
        } catch (Throwable t) {
            Safe.caught("SignEditMixin.sign", t);
        }
    }
}
