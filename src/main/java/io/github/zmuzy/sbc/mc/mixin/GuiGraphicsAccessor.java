// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mc.mixin;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

// where the warp menus fractional blits go + the games own tooltip at the end of the frame
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsAccessor {
    @Accessor("guiRenderState")
    GuiRenderState sbc$guiRenderState();

    @Invoker("setTooltipForNextFrameInternal")
    // the last flag is the gap under the first line, always there before 26.3
    void sbc$tooltipLast(Font font, List<ClientTooltipComponent> lines, int x, int y, ClientTooltipPositioner positioner, Identifier style, boolean focused, boolean gap);
}
