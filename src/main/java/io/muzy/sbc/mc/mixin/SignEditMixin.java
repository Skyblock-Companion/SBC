// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import io.muzy.sbc.core.Safe;
import io.muzy.sbc.ui.PartyFinderScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// like skyblocker: the sign hypixel opens for a level range gets answered inside party finder
@Mixin(value = LocalPlayer.class, priority = 500)
public abstract class SignEditMixin {
    @Inject(method = "openTextEdit", at = @At("HEAD"), cancellable = true)
    private void sbc$sign(SignBlockEntity sign, SignTextSlot front, CallbackInfo ci) {
        if (Safe.resting("SignEditMixin.sign")) return;
        try {
            if (Minecraft.getInstance().gui.screen() instanceof PartyFinderScreen screen && screen.onSign(sign, front)) ci.cancel();
            else if (io.muzy.sbc.ui.MarketSearchScreen.onSign(sign, front)) ci.cancel();
        } catch (Throwable t) {
            Safe.caught("SignEditMixin.sign", t);
        }
    }
}
