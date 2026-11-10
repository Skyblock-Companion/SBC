// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.container.ScrollContainer, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package io.muzy.sbc.ui.layout;

import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.BiConsumer;
import java.util.function.Function;

public class ScrollContainer<C extends UIComponent> extends WrappingParentUIComponent<C> {

    protected double scrollOffset = 0;
    protected double currentScrollPosition = 0;
    protected int lastScrollPosition = -1;
    protected int scrollStep = 0;

    protected int fixedScrollbarLength = 0;
    protected double lastScrollbarLength = 0;

    protected Scrollbar scrollbar = Scrollbar.flat(Color.ofArgb(0xA0000000));
    protected int scrollbarThiccness = 3;

    protected long lastScrollbarInteractTime = 0;
    protected int scrollbarOffset = 0;
    protected boolean scrollbaring = false;

    protected int maxScroll = 0;
    protected int childSize = 0;

    protected final ScrollDirection direction;

    protected ScrollContainer(ScrollDirection direction, Sizing horizontalSizing, Sizing verticalSizing, C child) {
        super(horizontalSizing, verticalSizing, child);
        this.direction = direction;
    }

    private static double clamp(double value, double min, double max) {
        return value < min ? min : Math.min(value, max);
    }

    // how far to move to a target in one frame, never past it
    private static double approach(double current, double target, double delta) {
        double diff = target - current;
        delta = diff * delta;

        return Math.abs(delta) > Math.abs(diff) ? diff : delta;
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        if (this.direction == ScrollDirection.VERTICAL) {
            return super.determineHorizontalContentSize(sizing);
        } else {
            throw new UnsupportedOperationException("Horizontal ScrollContainer cannot be horizontally content-sized");
        }
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        if (this.direction == ScrollDirection.HORIZONTAL) {
            return super.determineVerticalContentSize(sizing);
        } else {
            throw new UnsupportedOperationException("Vertical ScrollContainer cannot be vertically content-sized");
        }
    }

    @Override
    public void layout(Size space) {
        super.layout(space);

        this.maxScroll = Math.max(0, this.direction.sizeGetter.apply(child) - (this.direction.sizeGetter.apply(this) - this.direction.insetGetter.apply(this.padding.get())));
        this.scrollOffset = clamp(this.scrollOffset, 0, this.maxScroll + .5);
        this.childSize = this.direction.sizeGetter.apply(this.child);
        this.lastScrollPosition = -1;
    }

    @Override
    protected int childMountX() {
        return (int) (super.childMountX() - this.direction.choose(this.currentScrollPosition, 0));
    }

    @Override
    protected int childMountY() {
        return (int) (super.childMountY() - this.direction.choose(0, this.currentScrollPosition));
    }

    @Override
    protected void parentUpdate(float delta, int mouseX, int mouseY) {
        super.parentUpdate(delta, mouseX, mouseY);
        this.currentScrollPosition += approach(this.currentScrollPosition, this.scrollOffset, delta * .5);
    }

    @Override
    public void draw(UIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);

        // Update child
        int effectiveScrollOffset = this.scrollStep > 0
                ? ((int) this.scrollOffset / this.scrollStep) * this.scrollStep
                : (int) this.currentScrollPosition;
        if (this.scrollStep > 0 && this.maxScroll - this.scrollOffset == -1) {
            effectiveScrollOffset += this.scrollOffset % this.scrollStep;
        }

        int newScrollPosition = this.direction.coordinateGetter.apply(this) - effectiveScrollOffset;
        if (newScrollPosition != this.lastScrollPosition) {
            this.direction.coordinateSetter.accept(this.child, newScrollPosition + (this.direction == ScrollDirection.VERTICAL
                    ? this.padding.get().top() + this.child.margins().get().top()
                    : this.padding.get().left() + this.child.margins().get().left())
            );
            this.lastScrollPosition = newScrollPosition;
        }

        // Draw, adding the fractional part of the offset via matrix translation
        graphics.pose().pushMatrix();

        double visualOffset = -(this.currentScrollPosition % 1d);
        if (visualOffset > 9999999e-7 || visualOffset < .1e-6) visualOffset = 0;

        graphics.pose().translate((float) this.direction.choose(visualOffset, 0), (float) this.direction.choose(0, visualOffset));
        this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, this.childView);

        graphics.pose().popMatrix();

        // Highlight the scrollbar if it's being hovered
        if (this.isInScrollbar(mouseX, mouseY) || this.scrollbaring) {
            this.lastScrollbarInteractTime = System.currentTimeMillis() + 1500;
        }

        var padding = this.padding.get();
        int selfSize = this.direction.sizeGetter.apply(this);
        int contentSize = this.direction.sizeGetter.apply(this) - this.direction.insetGetter.apply(padding);

        // Determine the offset of the scrollbar on the
        // *opposite* axis to the one we scroll on
        this.scrollbarOffset = this.direction == ScrollDirection.VERTICAL
                ? this.x + this.width - padding.right() - scrollbarThiccness
                : this.y + this.height - padding.bottom() - scrollbarThiccness;

        this.lastScrollbarLength = this.fixedScrollbarLength == 0
                ? Math.min(Math.floor(((float) selfSize / this.childSize) * contentSize), contentSize)
                : this.fixedScrollbarLength;
        double scrollbarPosition = this.maxScroll != 0
                ? (this.currentScrollPosition / this.maxScroll) * (contentSize - this.lastScrollbarLength)
                : 0;

        if (this.direction == ScrollDirection.VERTICAL) {
            this.scrollbar.draw(graphics,
                    this.scrollbarOffset,
                    (int) (this.y + scrollbarPosition + padding.top()),
                    this.scrollbarThiccness,
                    (int) (this.lastScrollbarLength),
                    this.scrollbarOffset, this.y + padding.top(),
                    this.scrollbarThiccness, this.height - padding.vertical(),
                    lastScrollbarInteractTime, this.direction,
                    this.maxScroll > 0
            );
        } else {
            this.scrollbar.draw(graphics,
                    (int) (this.x + scrollbarPosition + padding.left()),
                    this.scrollbarOffset,
                    (int) (this.lastScrollbarLength),
                    this.scrollbarThiccness,
                    this.x + padding.left(), this.scrollbarOffset,
                    this.width - padding.horizontal(), this.scrollbarThiccness,
                    lastScrollbarInteractTime, this.direction,
                    this.maxScroll > 0
            );
        }
    }

    @Override
    public boolean canFocus(FocusSource source) {
        return true;
    }

    @Override
    public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        if (this.child.onMouseScroll(this.x + mouseX - this.child.x(), this.y + mouseY - this.child.y(), amount))
            return true;

        if (this.scrollStep < 1) {
            this.scrollBy(-amount * 15, false, true);
        } else {
            this.scrollBy(-amount * this.scrollStep, true, true);
        }

        return true;
    }

    @Override
    public boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        if (this.isInScrollbar(this.x + click.x(), this.y + click.y())) {
            super.onMouseDown(click, doubled);
            return true;
        } else {
            return super.onMouseDown(click, doubled);
        }
    }

    @Override
    public boolean onMouseDrag(MouseButtonEvent click, double deltaX, double deltaY) {
        if (!this.scrollbaring && !this.isInScrollbar(this.x + click.x(), this.y + click.y()))
            return super.onMouseDrag(click, deltaX, deltaY);

        double delta = this.direction.choose(deltaX, deltaY);
        double selfSize = this.direction.sizeGetter.apply(this) - this.direction.insetGetter.apply(this.padding.get());
        double scalar = (this.maxScroll) / (selfSize - this.lastScrollbarLength);
        if (!Double.isFinite(scalar)) scalar = 0;

        this.scrollBy(delta * scalar, true, false);
        this.scrollbaring = true;

        return true;
    }

    @Override
    public boolean onKeyPress(KeyEvent input) {
        if (input.key() == this.direction.lessKeycode) {
            this.scrollBy(-10, false, true);
        } else if (input.key() == this.direction.moreKeycode) {
            this.scrollBy(10, false, true);
        } else if (input.key() == GameLink.KEY_PAGE_DOWN) {
            this.scrollBy(this.direction.choose(this.width, this.height) * .8, false, true);
            this.lastScrollbarInteractTime = System.currentTimeMillis() + 1250;
        } else if (input.key() == GameLink.KEY_PAGE_UP) {
            this.scrollBy(this.direction.choose(this.width, this.height) * -.8, false, true);
        }

        return false;
    }

    @Override
    public boolean onMouseUp(MouseButtonEvent click) {
        this.scrollbaring = false;
        return true;
    }

    @Override
    public UIComponent childAt(int x, int y) {
        if (this.isInScrollbar(x, y)) {
            return this;
        } else {
            return super.childAt(x, y);
        }
    }

    protected void scrollBy(double offset, boolean instant, boolean showScrollbar) {
        this.scrollOffset = clamp(this.scrollOffset + offset, 0, this.maxScroll + .5);
        if (instant) this.currentScrollPosition = this.scrollOffset;
        if (showScrollbar) this.lastScrollbarInteractTime = System.currentTimeMillis() + 1250;
    }

    protected boolean isInScrollbar(double mouseX, double mouseY) {
        return this.isInBoundingBox(mouseX, mouseY) && this.direction.choose(mouseY, mouseX) >= this.scrollbarOffset;
    }

    // 0 to 1
    public ScrollContainer<C> scrollTo(double progress) {
        this.scrollOffset = this.maxScroll * progress;
        return this;
    }

    public ScrollContainer<C> scrollbarThiccness(int scrollbarThiccness) {
        this.scrollbarThiccness = scrollbarThiccness;
        return this;
    }

    public int scrollbarThiccness() {
        return this.scrollbarThiccness;
    }

    public ScrollContainer<C> scrollbar(Scrollbar scrollbar) {
        this.scrollbar = scrollbar;
        return this;
    }

    public Scrollbar scrollbar() {
        return this.scrollbar;
    }

    public ScrollContainer<C> scrollStep(int scrollStep) {
        this.scrollStep = scrollStep;
        return this;
    }

    public int scrollStep() {
        return this.scrollStep;
    }

    public ScrollContainer<C> fixedScrollbarLength(int fixedScrollbarLength) {
        this.fixedScrollbarLength = fixedScrollbarLength;
        return this;
    }

    public int fixedScrollbarLength() {
        return this.fixedScrollbarLength;
    }

    @FunctionalInterface
    public interface Scrollbar {

        // flat bar that fades out a bit after the last touch
        static Scrollbar flat(Color color) {
            int scrollbarColor = color.argb();

            return (context, x, y, width, height, trackX, trackY, trackWidth, trackHeight, lastInteractTime, direction, active) -> {
                if (!active) return;

                final var progress = Math.sin(clamp(lastInteractTime - System.currentTimeMillis(), 0, 750) / 750f * Math.PI - Math.PI / 2) * 0.5 + 0.5;
                int alpha = (int) ((float) progress * (scrollbarColor >>> 24));

                context.fill(
                        x, y, x + width, y + height,
                        alpha << 24 | (scrollbarColor & 0xFFFFFF)
                );
            };
        }

        void draw(UIGraphics context, int x, int y, int width, int height, int trackX, int trackY, int trackWidth, int trackHeight,
                  long lastInteractTime, ScrollDirection direction, boolean active);
    }

    public enum ScrollDirection {
        VERTICAL(UIComponent::height, UIComponent::updateY, UIComponent::y, Insets::vertical, GameLink.KEY_UP, GameLink.KEY_DOWN),
        HORIZONTAL(UIComponent::width, UIComponent::updateX, UIComponent::x, Insets::horizontal, GameLink.KEY_LEFT, GameLink.KEY_RIGHT);

        public final Function<UIComponent, Integer> sizeGetter;
        public final BiConsumer<UIComponent, Integer> coordinateSetter;
        public final Function<ScrollContainer<?>, Integer> coordinateGetter;
        public final Function<Insets, Integer> insetGetter;

        public final int lessKeycode, moreKeycode;

        ScrollDirection(Function<UIComponent, Integer> sizeGetter, BiConsumer<UIComponent, Integer> coordinateSetter, Function<ScrollContainer<?>, Integer> coordinateGetter, Function<Insets, Integer> insetGetter, int lessKeycode, int moreKeycode) {
            this.sizeGetter = sizeGetter;
            this.coordinateSetter = coordinateSetter;
            this.coordinateGetter = coordinateGetter;
            this.insetGetter = insetGetter;
            this.lessKeycode = lessKeycode;
            this.moreKeycode = moreKeycode;
        }

        public double choose(double horizontal, double vertical) {
            return switch (this) {
                case VERTICAL -> vertical;
                case HORIZONTAL -> horizontal;
            };
        }
    }
}
