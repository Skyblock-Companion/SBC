// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mc.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.component.CustomData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// read only. copyTag() copies the whole item data just to read the id lol
@Mixin(CustomData.class)
public interface CustomDataAccess {
    @Accessor("tag")
    CompoundTag sbc$tag();
}
