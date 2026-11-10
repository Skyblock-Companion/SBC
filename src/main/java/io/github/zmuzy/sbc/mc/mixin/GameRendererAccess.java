// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mc.mixin;

import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccess {
    @Accessor("guiRenderer")
    GuiRenderer sbc$guiRenderer();
}
