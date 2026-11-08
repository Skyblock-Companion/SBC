// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.mc.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

// storage overlay moves the open pages slots around with the grid
@Mixin(Slot.class)
public interface SlotAccessor {
    @Mutable
    @Accessor("x")
    void sbc$setX(int x);

    @Mutable
    @Accessor("y")
    void sbc$setY(int y);
}
