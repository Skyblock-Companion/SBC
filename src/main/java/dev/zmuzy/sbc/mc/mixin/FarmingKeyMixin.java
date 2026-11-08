// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

// from skyhanni MixinKeyMapping: while farming keys is on, the games keys answer to the ones the player set.
// nothing gets pressed for the player
@Mixin(KeyMapping.class)
public abstract class FarmingKeyMixin {
    @Shadow
    private int clickCount;

    @Shadow
    private boolean isDown;

    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$isDown(CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("FarmingKeyMixin.isDown")) return;
        try {
            Boolean v = dev.zmuzy.sbc.garden.farming.FarmingKeys.isDown((KeyMapping) (Object) this, isDown);
            if (v != null) cir.setReturnValue(v);
        } catch (Throwable t) {
            Safe.caught("FarmingKeyMixin.isDown", t);
        }
    }

    @Inject(method = "consumeClick", at = @At("HEAD"), cancellable = true, require = 0)
    private void sbc$consume(CallbackInfoReturnable<Boolean> cir) {
        if (Safe.resting("FarmingKeyMixin.consume")) return;
        try {
            Boolean v = dev.zmuzy.sbc.garden.farming.FarmingKeys.consumeClick((KeyMapping) (Object) this);
            if (v == null) return;
            clickCount = 0;
            cir.setReturnValue(v);
        } catch (Throwable t) {
            Safe.caught("FarmingKeyMixin.consume", t);
        }
    }
}
