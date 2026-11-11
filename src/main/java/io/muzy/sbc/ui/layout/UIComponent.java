// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.core.UIComponent, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package io.muzy.sbc.ui.layout;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

public interface UIComponent extends PositionedRectangle {

    // draw the current state
    void draw(UIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta);

    default void drawTooltip(UIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
        if (!this.shouldDrawTooltip(mouseX, mouseY)) return;
        context.drawTooltip(GameLink.font(), mouseX, mouseY, this.tooltip());
    }

    // something that clearly shows this is focused
    default void drawFocusHighlight(UIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
        context.drawRectOutline(this.x(), this.y(), this.width(), this.height(), 0xFFFFFFFF);
    }

    ParentUIComponent parent();

    FocusHandler focusHandler();

    UIComponent positioning(Positioning positioning);

    Observable<Positioning> positioning();

    UIComponent margins(Insets margins);

    Observable<Insets> margins();

    default UIComponent sizing(Sizing horizontalSizing, Sizing verticalSizing) {
        this.horizontalSizing(horizontalSizing);
        this.verticalSizing(verticalSizing);
        return this;
    }

    default UIComponent sizing(Sizing sizing) {
        this.sizing(sizing, sizing);
        return this;
    }

    UIComponent horizontalSizing(Sizing horizontalSizing);

    Observable<Sizing> horizontalSizing();

    UIComponent verticalSizing(Sizing verticalSizing);

    Observable<Sizing> verticalSizing();

    UIComponent tooltip(List<ClientTooltipComponent> tooltip);

    default UIComponent tooltip(Collection<Component> tooltip) {
        var components = new ArrayList<ClientTooltipComponent>();
        for (var line : tooltip) components.add(GameLink.tooltipLine(line.getVisualOrderText()));
        this.tooltip(components);
        return this;
    }

    default UIComponent tooltip(Component tooltip) {
        var components = new ArrayList<ClientTooltipComponent>();
        for (var line : GameLink.font().split(tooltip, Integer.MAX_VALUE)) {
            components.add(GameLink.tooltipLine(line));
        }
        this.tooltip(components);
        return this;
    }

    List<ClientTooltipComponent> tooltip();

    default boolean shouldDrawTooltip(double mouseX, double mouseY) {
        return this.tooltip() != null && !this.tooltip().isEmpty() && this.isInBoundingBox(mouseX, mouseY);
    }

    // size from the space the parent offers
    void inflate(Size space);

    // mounted during the parents layout
    void mount(ParentUIComponent parent, int x, int y);

    // dismounted from the parent
    void dismount(DismountReason reason);

    // runs the closure and lays out once after it
    <C extends UIComponent> C configure(Consumer<C> closure);

    default boolean hasParent() {
        return this.parent() != null;
    }

    default ParentUIComponent root() {
        var root = this.parent();
        if (root == null) return null;

        while (root.hasParent()) root = root.parent();
        return root;
    }

    // click inside our box, coords relative to us
    boolean onMouseDown(MouseButtonEvent click, boolean doubled);

    // who wants our clicks
    MouseDown.Source mouseDown();

    boolean onMouseUp(MouseButtonEvent click);

    boolean onMouseScroll(double mouseX, double mouseY, double amount);

    boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY);

    boolean onKeyPress(KeyEvent input);

    boolean onCharTyped(CharacterEvent input);

    // can it get focus from this source
    default boolean canFocus(FocusSource source) {
        return false;
    }

    void onFocusGained(FocusSource source);

    void onFocusLost();

    CursorStyle cursorStyle();

    UIComponent cursorStyle(CursorStyle style);

    // update before the next frame
    default void update(float delta, int mouseX, int mouseY) {}

    @Override
    default boolean isInBoundingBox(double x, double y) {
        return PositionedRectangle.super.isInBoundingBox(x, y);
    }

    // content size + margins
    default Size fullSize() {
        var margins = this.margins().get();
        return Size.of(this.width() + margins.horizontal(), this.height() + margins.vertical());
    }

    @Override
    int width();

    @Override
    int height();

    @Override
    int x();

    // x to use for the childrens layout
    default int baseX() {
        return this.x();
    }

    void updateX(int x);

    @Override
    int y();

    // y to use for the childrens layout
    default int baseY() {
        return this.y();
    }

    void updateY(int y);

    default void moveTo(int x, int y) {
        this.updateX(x);
        this.updateY(y);
    }

    enum FocusSource {
        MOUSE_CLICK,

        KEYBOARD_CYCLE
    }

    enum DismountReason {
        LAYOUT_INFLATION,
        REMOVED
    }
}
