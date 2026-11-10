// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.base.BaseUIComponent, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package io.github.zmuzy.sbc.ui.layout;

import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;
import java.util.function.Consumer;

// default component. only has to say how it draws + how big it is when sized by content
public abstract class BaseUIComponent implements UIComponent {

    protected ParentUIComponent parent = null;

    protected boolean mounted = false;

    protected int batchedEvents = 0;

    protected final Observable<Insets> margins = Observable.of(Insets.none());

    protected final Observable<Positioning> positioning = Observable.of(Positioning.layout());
    protected final Observable<Sizing> horizontalSizing = Observable.of(Sizing.content());
    protected final Observable<Sizing> verticalSizing = Observable.of(Sizing.content());

    protected final MouseDown.Source mouseDownEvents = new MouseDown.Source();

    protected boolean hovered = false;
    protected boolean dirty = false;

    protected CursorStyle cursorStyle = CursorStyle.NONE;
    protected List<ClientTooltipComponent> tooltip = List.of();

    protected int x, y;
    protected int width, height;

    protected Size space = Size.zero();

    protected BaseUIComponent() {
        Observable.observeAll(this::notifyParentIfMounted, margins, positioning, horizontalSizing, verticalSizing);
    }

    // width needed for the content
    protected int determineHorizontalContentSize(Sizing sizing) {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + " does not support Sizing.content() on the horizontal axis");
    }

    // height needed for the content
    protected int determineVerticalContentSize(Sizing sizing) {
        throw new UnsupportedOperationException(this.getClass().getSimpleName() + " does not support Sizing.content() on the vertical axis");
    }

    @Override
    public void inflate(Size space) {
        this.space = space;
        this.applySizing();
        this.dirty = false;
    }

    // work out + store width and height from the sizing
    protected void applySizing() {
        final var horizontalSizing = this.horizontalSizing.get();
        final var verticalSizing = this.verticalSizing.get();

        final var margins = this.margins.get();

        this.width = horizontalSizing.inflate(this.space.width() - margins.horizontal(), this::determineHorizontalContentSize);
        this.height = verticalSizing.inflate(this.space.height() - margins.vertical(), this::determineVerticalContentSize);
    }

    protected void notifyParentIfMounted() {
        if (!this.hasParent()) return;

        if (this.batchedEvents > 0) {
            this.batchedEvents++;
            return;
        }

        this.dirty = true;
        this.parent.onChildMutated(this);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <C extends UIComponent> C configure(Consumer<C> closure) {
        try {
            this.runAndDeferEvents(() -> closure.accept((C) this));
        } catch (ClassCastException theUserDidBadItWasNotMyFault) {
            throw new IllegalArgumentException(
                    "Invalid target class passed when configuring component of type " + this.getClass().getSimpleName(),
                    theUserDidBadItWasNotMyFault
            );
        }

        return (C) this;
    }

    protected void runAndDeferEvents(Runnable action) {
        try {
            this.batchedEvents = 1;
            action.run();
        } finally {
            if (this.batchedEvents > 1) {
                this.batchedEvents = 0;
                this.notifyParentIfMounted();
            } else {
                this.batchedEvents = 0;
            }
        }
    }

    @Override
    public void update(float delta, int mouseX, int mouseY) {
        UIComponent.super.update(delta, mouseX, mouseY);

        boolean nowHovered = this.isInBoundingBox(mouseX, mouseY);
        if (this.hovered != nowHovered) {
            this.updateHoveredState(mouseX, mouseY, nowHovered);
        }
    }

    protected void updateHoveredState(int mouseX, int mouseY, boolean nowHovered) {
        this.hovered = nowHovered;

        if (nowHovered) {
            if (this.root() == null || this.root().childAt(mouseX, mouseY) != this) {
                this.hovered = false;
            }
        }
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        return this.mouseDownEvents.fire(click, doubled);
    }

    @Override
    public MouseDown.Source mouseDown() {
        return this.mouseDownEvents;
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        return false;
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        return false;
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        return false;
    }

    @Override
    public boolean onKeyPress(KeyEvent input) {
        return false;
    }

    @Override
    public boolean onCharTyped(CharacterEvent input) {
        return false;
    }

    @Override
    public void onFocusGained(FocusSource source) {}

    @Override
    public void onFocusLost() {}

    @Override
    public CursorStyle cursorStyle() {
        return this.cursorStyle;
    }

    @Override
    public BaseUIComponent cursorStyle(CursorStyle style) {
        this.cursorStyle = style;
        return this;
    }

    @Override
    public UIComponent tooltip(List<ClientTooltipComponent> tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Override
    public List<ClientTooltipComponent> tooltip() {
        return this.tooltip;
    }

    @Override
    public void mount(ParentUIComponent parent, int x, int y) {
        this.parent = parent;
        this.mounted = true;
        this.moveTo(x, y);
    }

    @Override
    public void dismount(DismountReason reason) {
        this.parent = null;
        this.mounted = false;
    }

    @Override
    public ParentUIComponent parent() {
        return this.parent;
    }

    @Override
    public FocusHandler focusHandler() {
        return this.hasParent() ? this.parent.focusHandler() : null;
    }

    @Override
    public BaseUIComponent positioning(Positioning positioning) {
        this.positioning.set(positioning);
        return this;
    }

    @Override
    public Observable<Positioning> positioning() {
        return this.positioning;
    }

    @Override
    public BaseUIComponent margins(Insets margins) {
        this.margins.set(margins);
        return this;
    }

    @Override
    public Observable<Insets> margins() {
        return this.margins;
    }

    @Override
    public UIComponent horizontalSizing(Sizing horizontalSizing) {
        this.horizontalSizing.set(horizontalSizing);
        return this;
    }

    @Override
    public Observable<Sizing> horizontalSizing() {
        return this.horizontalSizing;
    }

    @Override
    public UIComponent verticalSizing(Sizing verticalSizing) {
        this.verticalSizing.set(verticalSizing);
        return this;
    }

    @Override
    public Observable<Sizing> verticalSizing() {
        return this.verticalSizing;
    }

    @Override
    public int x() {
        return this.x;
    }

    @Override
    public void updateX(int x) {
        this.x = x;
    }

    @Override
    public int y() {
        return this.y;
    }

    @Override
    public void updateY(int y) {
        this.y = y;
    }

    @Override
    public int width() {
        return this.width;
    }

    @Override
    public int height() {
        return this.height;
    }
}
