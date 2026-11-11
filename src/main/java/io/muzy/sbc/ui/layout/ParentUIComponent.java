// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.core.ParentUIComponent, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package io.muzy.sbc.ui.layout;

import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

public interface ParentUIComponent extends UIComponent {

    void layout(Size space);

    // a child changed in a way that affects our layout
    void onChildMutated(UIComponent child);

    // how children get arranged
    default ParentUIComponent alignment(HorizontalAlignment horizontalAlignment, VerticalAlignment verticalAlignment) {
        this.horizontalAlignment(horizontalAlignment);
        this.verticalAlignment(verticalAlignment);
        return this;
    }

    ParentUIComponent verticalAlignment(VerticalAlignment alignment);

    VerticalAlignment verticalAlignment();

    ParentUIComponent horizontalAlignment(HorizontalAlignment alignment);

    HorizontalAlignment horizontalAlignment();

    ParentUIComponent padding(Insets padding);

    Observable<Insets> padding();

    // can children overflow our box
    ParentUIComponent allowOverflow(boolean allowOverflow);

    boolean allowOverflow();

    ParentUIComponent surface(Surface surface);

    Surface surface();

    List<UIComponent> children();

    ParentUIComponent removeChild(UIComponent child);

    @Override
    default void drawTooltip(UIGraphics context, int mouseX, int mouseY, float partialTicks, float delta) {
        if (this.hasParent()) {
            UIComponent.super.drawTooltip(context, mouseX, mouseY, partialTicks, delta);
            return;
        }

        var hoveredDescendants = new ArrayList<UIComponent>();
        this.forEachDescendantWhere(hoveredDescendants::add, component -> component.isInBoundingBox(mouseX, mouseY));
        hoveredDescendants.remove(this);

        for (int i = hoveredDescendants.size() - 1; i >= 0; i--) {
            ParentUIComponent nextParent = null;
            for (int parentIdx = i - 1; parentIdx >= 0; parentIdx--) {
                if (hoveredDescendants.get(parentIdx) instanceof ParentUIComponent parent) {
                    nextParent = parent;
                    break;
                }
            }

            var current = hoveredDescendants.get(i);
            if (nextParent != null && current.parent() != nextParent) break;
            if (!current.shouldDrawTooltip(mouseX, mouseY)) continue;

            context.push();
            for (; i >= 0; i--) {
                if (i > 0 && hoveredDescendants.get(i).parent() != hoveredDescendants.get(i - 1)) break;
                context.translate(0, 0);
            }

            current.drawTooltip(context, mouseX, mouseY, partialTicks, delta);
            context.pop();

            break;
        }
    }

    @Override
    default boolean onMouseDown(MouseButtonEvent click, boolean doubled) {
        var iter = this.children().listIterator(this.children().size());

        while (iter.hasPrevious()) {
            var child = iter.previous();
            if (!child.isInBoundingBox(this.x() + click.x(), this.y() + click.y())) continue;
            if (child.onMouseDown(GameLink.clickAt(click, this.x() + click.x() - child.x(), this.y() + click.y() - child.y()), doubled)) {
                return true;
            }
        }

        return false;
    }

    @Override
    default boolean onMouseScroll(double mouseX, double mouseY, double amount) {
        var iter = this.children().listIterator(this.children().size());

        while (iter.hasPrevious()) {
            var child = iter.previous();
            if (!child.isInBoundingBox(this.x() + mouseX, this.y() + mouseY)) continue;
            if (child.onMouseScroll(this.x() + mouseX - child.x(), this.y() + mouseY - child.y(), amount)) {
                return true;
            }
        }

        return false;
    }

    @Override
    default void update(float delta, int mouseX, int mouseY) {
        for (int i = 0; i < this.children().size(); i++) {
            this.children().get(i).update(delta, mouseX, mouseY);
        }
    }

    // innermost component at this spot
    default UIComponent childAt(int x, int y) {
        var iter = this.children().listIterator(this.children().size());

        while (iter.hasPrevious()) {
            var child = iter.previous();
            if (child.isInBoundingBox(x, y)) {
                if (child instanceof ParentUIComponent parent) {
                    return parent.childAt(x, y);
                } else {
                    return child;
                }
            }
        }

        return this.isInBoundingBox(x, y) ? this : null;
    }

    default void collectDescendants(ArrayList<UIComponent> into) {
        this.forEachDescendant(into::add);
    }

    default void forEachDescendant(Consumer<UIComponent> action) {
        action.accept(this);
        for (var child : this.children()) {
            if (child instanceof ParentUIComponent parent) {
                parent.forEachDescendant(action);
            } else {
                action.accept(child);
            }
        }
    }

    default void forEachDescendantWhere(Consumer<UIComponent> action, Predicate<UIComponent> condition) {
        action.accept(this);
        for (var child : this.children()) {
            if (!condition.test(child)) continue;

            if (child instanceof ParentUIComponent parent) {
                parent.forEachDescendantWhere(action, condition);
            } else {
                action.accept(child);
            }
        }
    }
}
