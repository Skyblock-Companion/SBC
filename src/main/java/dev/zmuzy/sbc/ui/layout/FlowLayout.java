// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.container.FlowLayout, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package dev.zmuzy.sbc.ui.layout;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class FlowLayout extends BaseParentUIComponent {

    protected final List<UIComponent> children = new ArrayList<>();
    protected final List<UIComponent> childrenView = Collections.unmodifiableList(this.children);
    protected final Algorithm algorithm;

    protected Size contentSize = Size.zero();
    protected Observable<Integer> gap = Observable.of(0);

    protected FlowLayout(Sizing horizontalSizing, Sizing verticalSizing, Algorithm algorithm) {
        super(horizontalSizing, verticalSizing);
        this.algorithm = algorithm;

        this.gap.observe(integer -> this.updateLayout());
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return this.contentSize.width() + this.padding.get().horizontal();
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return this.contentSize.height() + this.padding.get().vertical();
    }

    @Override
    public void layout(Size space) {
        this.algorithm.layout(this);
    }

    // one child. for many use children(Collection)
    public FlowLayout child(UIComponent child) {
        this.children.add(child);
        this.updateLayout();
        return this;
    }

    public FlowLayout children(Collection<? extends UIComponent> children) {
        this.children.addAll(children);
        this.updateLayout();
        return this;
    }

    public FlowLayout child(int index, UIComponent child) {
        this.children.add(index, child);
        this.updateLayout();
        return this;
    }

    public FlowLayout children(int index, Collection<? extends UIComponent> children) {
        this.children.addAll(index, children);
        this.updateLayout();
        return this;
    }

    @Override
    public FlowLayout removeChild(UIComponent child) {
        if (this.children.remove(child)) {
            child.dismount(DismountReason.REMOVED);
            this.updateLayout();
        }

        return this;
    }

    public FlowLayout clearChildren() {
        for (var child : this.children) {
            child.dismount(DismountReason.REMOVED);
        }

        this.children.clear();
        this.updateLayout();

        return this;
    }

    @Override
    public List<UIComponent> children() {
        return this.childrenView;
    }

    public FlowLayout gap(int gap) {
        this.gap.set(gap);
        return this;
    }

    public int gap() {
        return this.gap.get();
    }

    @Override
    public void draw(UIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, this.children);
    }

    @FunctionalInterface
    public interface Algorithm {
        void layout(FlowLayout container);

        Algorithm HORIZONTAL = container -> {
            var layoutWidth = new int[]{0};
            var layoutHeight = new int[]{0};

            final var layout = new ArrayList<UIComponent>();
            final var padding = container.padding.get();
            final var childSpace = container.calculateChildSpace(container.space);

            MountingHelper.inflateWithExpand(container.children, childSpace, false, container.gap());
            var mountState = MountingHelper.mountEarly(container::mountChild, container.children, child -> {
                layout.add(child);

                child.mount(container,
                        container.x + padding.left() + child.margins().get().left() + layoutWidth[0],
                        container.y + padding.top() + child.margins().get().top());

                final var childSize = child.fullSize();
                layoutWidth[0] += childSize.width() + container.gap();
                if (childSize.height() > layoutHeight[0]) {
                    layoutHeight[0] = childSize.height();
                }
            });

            layoutWidth[0] -= container.gap();

            container.contentSize = Size.of(layoutWidth[0], layoutHeight[0]);
            container.applySizing();

            if (container.verticalAlignment() != VerticalAlignment.TOP) {
                for (var component : layout) {
                    component.updateY(component.baseY() + container.verticalAlignment().align(component.fullSize().height(), container.height - padding.vertical()));
                }
            }

            if (container.horizontalAlignment() != HorizontalAlignment.LEFT) {
                for (var component : layout) {
                    if (container.horizontalAlignment() == HorizontalAlignment.CENTER) {
                        component.updateX(component.baseX() + (container.width - padding.horizontal() - layoutWidth[0]) / 2);
                    } else {
                        component.updateX(component.baseX() + (container.width - padding.horizontal() - layoutWidth[0]));
                    }
                }
            }

            mountState.mountLate();
        };

        Algorithm VERTICAL = container -> {
            var layoutHeight = new int[]{0};
            var layoutWidth = new int[]{0};

            final var layout = new ArrayList<UIComponent>();
            final var padding = container.padding.get();
            final var childSpace = container.calculateChildSpace(container.space);

            MountingHelper.inflateWithExpand(container.children, childSpace, true, container.gap());
            var mountState = MountingHelper.mountEarly(container::mountChild, container.children, child -> {
                layout.add(child);

                child.mount(container,
                        container.x + padding.left() + child.margins().get().left(),
                        container.y + padding.top() + child.margins().get().top() + layoutHeight[0]);

                final var childSize = child.fullSize();
                layoutHeight[0] += childSize.height() + container.gap();
                if (childSize.width() > layoutWidth[0]) {
                    layoutWidth[0] = childSize.width();
                }
            });

            layoutHeight[0] -= container.gap();

            container.contentSize = Size.of(layoutWidth[0], layoutHeight[0]);
            container.applySizing();

            if (container.horizontalAlignment() != HorizontalAlignment.LEFT) {
                for (var component : layout) {
                    component.updateX(component.baseX() + container.horizontalAlignment().align(component.fullSize().width(), container.width - padding.horizontal()));
                }
            }

            if (container.verticalAlignment() != VerticalAlignment.TOP) {
                for (var component : layout) {
                    if (container.verticalAlignment() == VerticalAlignment.CENTER) {
                        component.updateY(component.baseY() + (container.height - padding.vertical() - layoutHeight[0]) / 2);
                    } else {
                        component.updateY(component.baseY() + (container.height - padding.vertical() - layoutHeight[0]));
                    }
                }
            }

            mountState.mountLate();
        };
    }
}
