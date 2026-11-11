// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.mc.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

// pos, size and hovered slot of any container screen
@Mixin(AbstractContainerScreen.class)
public interface ContainerAccess {
    @Accessor("leftPos")
    int sbc$left();

    @Accessor("topPos")
    int sbc$top();

    @Accessor("imageWidth")
    int sbc$width();

    @Accessor("imageHeight")
    int sbc$height();

    @Accessor("hoveredSlot")
    Slot sbc$hovered();
}
