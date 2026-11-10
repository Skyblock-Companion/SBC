// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mc.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
    @Accessor("footer")
    Component sbc$footer();

    @Accessor("header")
    Component sbc$header();
}
