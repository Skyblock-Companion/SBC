// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.github.zmuzy.sbc.core.Safe;
import net.minecraft.core.component.DataComponentHolder;
import net.minecraft.core.component.DataComponentType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// from skyblocker DataComponentHolderMixin: custom trim, head or glint
// runs for every component of every item, so first thing it checks is whether any look is set at all
@Mixin(DataComponentHolder.class)
public interface CustomLooksComponentMixin {
    @ModifyReturnValue(method = "get", at = @At("RETURN"), require = 0)
    private <T> T sbc$look(T original, DataComponentType<? extends T> type) {
        if (Safe.resting("CustomLooksComponentMixin.look")) return original;
        try {
            return io.github.zmuzy.sbc.item.custom.CustomLooks.component(this, type, original);
        } catch (Throwable t) {
            Safe.caught("CustomLooksComponentMixin.look", t);
            return original;
        }
    }
}
