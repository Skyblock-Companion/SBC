// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.base.BaseParentUIComponent, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package io.muzy.sbc.ui.layout;

import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.List;
import java.util.function.Consumer;

// reference parent: layout state, root focus, input routing, drawing children
public abstract class BaseParentUIComponent extends BaseUIComponent implements ParentUIComponent {

    protected final Observable<VerticalAlignment> verticalAlignment = Observable.of(VerticalAlignment.TOP);
    protected final Observable<HorizontalAlignment> horizontalAlignment = Observable.of(HorizontalAlignment.LEFT);

    protected final Observable<Insets> padding = Observable.of(Insets.none());

    protected FocusHandler focusHandler = null;

    protected Surface surface = Surface.BLANK;
    protected boolean allowOverflow = false;

    protected BaseParentUIComponent(Sizing horizontalSizing, Sizing verticalSizing) {
        this.horizontalSizing.set(horizontalSizing);
        this.verticalSizing.set(verticalSizing);

        Observable.observeAll(this::updateLayout, horizontalAlignment, verticalAlignment, padding);
    }

    @Override
    public final void update(float delta, int mouseX, int mouseY) {
        ParentUIComponent.super.update(delta, mouseX, mouseY);
        super.update(delta, mouseX, mouseY);
        this.parentUpdate(delta, mouseX, mouseY);
    }

    // update before the next frame, children already updated
    protected void parentUpdate(float delta, int mouseX, int mouseY) {}

    @Override
    public void draw(UIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        this.surface.draw(graphics, this);
    }

    @Override
    public FocusHandler focusHandler() {
        if (this.focusHandler == null) {
            return super.focusHandler();
        } else {
            return this.focusHandler;
        }
    }

    @Override
    public ParentUIComponent verticalAlignment(VerticalAlignment alignment) {
        this.verticalAlignment.set(alignment);
        return this;
    }

    @Override
    public VerticalAlignment verticalAlignment() {
        return this.verticalAlignment.get();
    }

    @Override
    public ParentUIComponent horizontalAlignment(HorizontalAlignment alignment) {
        this.horizontalAlignment.set(alignment);
        return this;
    }

    @Override
    public HorizontalAlignment horizontalAlignment() {
        return this.horizontalAlignment.get();
    }

    @Override
    public ParentUIComponent padding(Insets padding) {
        this.padding.set(padding);
        this.updateLayout();
        return this;
    }

    @Override
    public Observable<Insets> padding() {
        return this.padding;
    }

    @Override
    public ParentUIComponent allowOverflow(boolean allowOverflow) {
        this.allowOverflow = allowOverflow;
        return this;
    }

    @Override
    public boolean allowOverflow() {
        return this.allowOverflow;
    }

    @Override
    public ParentUIComponent surface(Surface surface) {
        this.surface = surface;
        return this;
    }

    @Override
    public Surface surface() {
        return this.surface;
    }

    @Override
    public void mount(ParentUIComponent parent, int x, int y) {
        super.mount(parent, x, y);
        if (parent == null && this.focusHandler == null) {
            this.focusHandler = new FocusHandler(this);
        }
    }

    @Override
    public void inflate(Size space) {
        if (this.space.equals(space) && !this.dirty) return;
        this.space = space;

        for (var child : this.children()) {
            child.dismount(DismountReason.LAYOUT_INFLATION);
        }

        super.inflate(space);
        this.layout(space);
        super.inflate(space);
    }

    protected void updateLayout() {
        if (!this.mounted) return;

        if (this.batchedEvents > 0) {
            this.batchedEvents++;
            return;
        }

        var previousSize = this.fullSize();

        this.dirty = true;
        this.inflate(this.space);

        if (!previousSize.equals(this.fullSize()) && this.parent != null) {
            this.parent.onChildMutated(this);
        }
    }

    @Override
    protected void runAndDeferEvents(Runnable action) {
        try {
            this.batchedEvents = 1;
            action.run();
        } finally {
            if (this.batchedEvents > 1) {
                this.batchedEvents = 0;
                this.updateLayout();
            } else {
                this.batchedEvents = 0;
            }
        }
    }

    @Override
    public void onChildMutated(UIComponent child) {
        this.updateLayout();
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (this.focusHandler != null) {
            this.focusHandler.updateClickFocus(this.x + click.x(), this.y + click.y());
        }

        return ParentUIComponent.super.onMouseDown(click, doubled)
            || super.onMouseDown(click, doubled);
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        if (this.focusHandler != null && this.focusHandler.focused() != null) {
            final var focused = this.focusHandler.focused();
            return focused.onMouseUp(GameLink.clickAt(click, this.x + click.x() - focused.x(), this.y + click.y() - focused.y()));
        } else {
            return super.onMouseUp(click);
        }
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        return ParentUIComponent.super.onMouseScroll(mouseX, mouseY, amount) || super.onMouseScroll(mouseX, mouseY, amount);
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.focusHandler != null && this.focusHandler.focused() != null) {
            final var focused = this.focusHandler.focused();
            return focused.onMouseDrag(GameLink.clickAt(click, this.x + click.x() - focused.x(), this.y + click.y() - focused.y()), deltaX, deltaY);
        } else {
            return super.onMouseDrag(click, deltaX, deltaY);
        }
    }

    @Override
    public boolean onKeyPress(KeyEvent input) {
        if (this.focusHandler == null) return false;

        if (input.isCycleFocus()) {
            this.focusHandler.cycle(!input.hasShiftDown());
        } else if ((input.isUp() || input.isDown() || input.isLeft() || input.isRight()) && input.hasAltDown()) {
            this.focusHandler.moveFocus(input.key());
        } else if (this.focusHandler.focused() != null) {
            return this.focusHandler.focused().onKeyPress(input);
        }

        return super.onKeyPress(input);
    }

    @Override
    public boolean onCharTyped(CharacterEvent input) {
        if (this.focusHandler == null) return false;

        if (this.focusHandler.focused() != null) {
            return this.focusHandler.focused().onCharTyped(input);
        }

        return super.onCharTyped(input);
    }

    @Override
    public void updateX(int x) {
        int offset = x - this.x;
        super.updateX(x);

        for (var child : this.children()) {
            child.updateX(child.baseX() + offset);
        }
    }

    @Override
    public void updateY(int y) {
        int offset = y - this.y;
        super.updateY(y);

        for (var child : this.children()) {
            child.updateY(child.baseY() + offset);
        }
    }

    // mount a child with the given function if its positioning is layout(), else by its own positioning
    protected void mountChild(UIComponent child, Consumer<UIComponent> layoutFunc) {
        if (child == null) return;

        final var positioning = child.positioning().get();
        final var componentMargins = child.margins().get();
        final var padding = this.padding.get();

        switch (positioning.type) {
            case LAYOUT -> layoutFunc.accept(child);
            case ABSOLUTE -> child.mount(
                this,
                this.x + positioning.x + componentMargins.left() + padding.left(),
                this.y + positioning.y + componentMargins.top() + padding.top()
            );
            case RELATIVE -> child.mount(
                this,
                this.x + padding.left() + componentMargins.left() + Math.round((positioning.x / 100f) * (this.width() - child.fullSize().width() - padding.horizontal())),
                this.y + padding.top() + componentMargins.top() + Math.round((positioning.y / 100f) * (this.height() - child.fullSize().height() - padding.vertical()))
            );
            case ACROSS -> child.mount(
                this,
                this.x + padding.left() + componentMargins.left() + Math.round((positioning.x / 100f) * (this.width() - padding.horizontal())),
                this.y + padding.top() + componentMargins.top() + Math.round((positioning.y / 100f) * (this.height() - padding.vertical()))
            );
        }
    }

    // children + focus outline + tooltip, clipped unless overflow is allowed
    protected void drawChildren(UIGraphics context, int mouseX, int mouseY, float partialTicks, float delta, List<? extends UIComponent> children) {
        if (!this.allowOverflow) {
            var padding = this.padding.get();
            context.enableScissor(this.x + padding.left(), this.y + padding.top(), this.x + padding.left() + this.width - padding.horizontal(), this.y + padding.top() + this.height - padding.vertical());
        }

        var focusHandler = this.focusHandler();
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < children.size(); i++) {
            final var child = children.get(i);

            if (!context.intersectsScissor(child)) continue;

            child.draw(context, mouseX, mouseY, partialTicks, delta);
            if (focusHandler.lastFocusSource() == FocusSource.KEYBOARD_CYCLE && focusHandler.focused() == child) {
                child.drawFocusHighlight(context, mouseX, mouseY, partialTicks, delta);
            }
        }

        if (!this.allowOverflow) {
            context.disableScissor();
        }
    }

    // space children get: our size, or what we got offered when sized by content
    protected Size calculateChildSpace(Size thisSpace) {
        final var padding = this.padding.get();

        return Size.of(
            this.horizontalSizing.get().isContent() ? thisSpace.width() - padding.horizontal() : this.width - padding.horizontal(),
            this.verticalSizing.get().isContent() ? thisSpace.height() - padding.vertical() : this.height - padding.vertical()
        );
    }

    @Override
    public BaseParentUIComponent positioning(Positioning positioning) {
        return (BaseParentUIComponent) super.positioning(positioning);
    }

    @Override
    public BaseParentUIComponent margins(Insets margins) {
        return (BaseParentUIComponent) super.margins(margins);
    }
}
