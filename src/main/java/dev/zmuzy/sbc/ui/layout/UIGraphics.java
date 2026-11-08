// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.core.OwoUIGraphics, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 *
 * Changed: drawGradientRect goes through the game's own fillGradient (see SbcGradient for why), so the library's custom render
 * state is not carried along.
 */
package dev.zmuzy.sbc.ui.layout;

import dev.zmuzy.sbc.ui.screens.SbcGradient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.function.Consumer;

// game graphics + what the layout draws. shares pose + scissor stack with what it wraps. a tooltip set during
// the frame gets drawn at the end
public class UIGraphics extends GuiGraphicsExtractor {

    private final Consumer<Runnable> setTooltipDrawer;

    protected UIGraphics(Minecraft client, GuiRenderState renderState, int mouseX, int mouseY, Consumer<Runnable> setTooltipDrawer) {
        super(client, renderState, mouseX, mouseY);
        this.setTooltipDrawer = setTooltipDrawer;
    }

    public static UIGraphics of(GuiGraphicsExtractor graphics) {
        // y before x is how the lib has it, the game only uses them to tell whats under the mouse in a scissor
        var ui = new UIGraphics(
            Minecraft.getInstance(),
            graphics.guiRenderState,
            graphics.mouseY,
            graphics.mouseX,
            drawer -> graphics.deferredTooltip = drawer
        );

        ui.scissorStack = graphics.scissorStack;
        ui.pose = graphics.pose;

        return ui;
    }

    public boolean intersectsScissor(PositionedRectangle other) {
        other = other.transform(pose());

        var rect = this.scissorStack.peek();

        if (rect == null) return true;

        var pos = rect.position();

        return other.x() < pos.x() + rect.width()
            && other.x() + other.width() >= pos.x()
            && other.y() < pos.y() + rect.height()
            && other.y() + other.height() >= pos.y();
    }

    public void drawRectOutline(int x, int y, int width, int height, int color) {
        this.fill(x, y, x + width, y + 1, color);
        this.fill(x, y + height - 1, x + width, y + height, color);

        this.fill(x, y + 1, x + 1, y + height - 1, color);
        this.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }

    public void drawGradientRect(int x, int y, int width, int height, int topLeftColor, int topRightColor, int bottomRightColor, int bottomLeftColor) {
        SbcGradient.rect(this, x, y, width, height, topLeftColor, topRightColor, bottomRightColor, bottomLeftColor);
    }

    public UIGraphics push() {
        pose().pushMatrix();
        return this;
    }

    public UIGraphics translate(float x, float y) {
        pose().translate(x, y);
        return this;
    }

    public UIGraphics pop() {
        pose().popMatrix();
        return this;
    }

    public void drawTooltip(Font textRenderer, int x, int y, List<ClientTooltipComponent> components) {
        this.tooltip(textRenderer, components, x, y, DefaultTooltipPositioner.INSTANCE, null);
    }

    @Override
    protected void setTooltipForNextFrameInternal(Font textRenderer, List<ClientTooltipComponent> components, int x, int y, ClientTooltipPositioner positioner, Identifier texture, boolean replaceExisting) {
        super.setTooltipForNextFrameInternal(textRenderer, components, x, y, positioner, texture, replaceExisting);
        this.setTooltipDrawer.accept(this.deferredTooltip);
    }
}
