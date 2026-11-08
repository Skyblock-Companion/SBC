// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.mc.EntitySlots;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

// gives every entity a field for EntityTickCache, so its per-frame answers need no lookup in a shared table
@Mixin(Entity.class)
public abstract class EntitySlotsMixin implements EntitySlots {
    @Unique
    private Object[] sbc$slots;

    @Override
    public Object[] sbc$slots() {
        return sbc$slots;
    }

    @Override
    public void sbc$slots(Object[] slots) {
        sbc$slots = slots;
    }
}
