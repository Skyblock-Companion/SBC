// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.container.StackLayout, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package dev.zmuzy.sbc.ui.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class StackLayout extends BaseParentUIComponent {

    protected final List<UIComponent> children = new ArrayList<>();
    protected final List<UIComponent> childrenView = Collections.unmodifiableList(this.children);

    protected Size contentSize = Size.zero();

    protected StackLayout(Sizing horizontalSizing, Sizing verticalSizing) {
        super(horizontalSizing, verticalSizing);
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
        var childSpace = this.calculateChildSpace(space);
        this.children.forEach(child -> child.inflate(childSpace));

        var layoutWidth = new int[]{0};
        var layoutHeight = new int[]{0};

        var layout = new ArrayList<UIComponent>();
        var helper = MountingHelper.mountEarly(this::mountChild, this.childrenView, child -> {
            layout.add(child);
            child.mount(this, this.x + this.padding.get().left() + child.margins().get().left(), this.y + this.padding.get().top() + child.margins().get().top());

            var fullChildSize = child.fullSize();
            layoutWidth[0] = Math.max(layoutWidth[0], fullChildSize.width());
            layoutHeight[0] = Math.max(layoutHeight[0], fullChildSize.height());
        });

        this.contentSize = Size.of(layoutWidth[0], layoutHeight[0]);
        this.applySizing();

        var horizontalAlignment = this.horizontalAlignment();
        var verticalAlignment = this.verticalAlignment();

        for (var child : layout) {
            child.updateX(child.baseX() + horizontalAlignment.align(child.fullSize().width(), this.width - this.padding.get().horizontal()));
            child.updateY(child.baseY() + verticalAlignment.align(child.fullSize().height(), this.height - this.padding.get().vertical()));
        }

        helper.mountLate();
    }

    @Override
    public void draw(UIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(graphics, mouseX, mouseY, partialTicks, delta);
        this.drawChildren(graphics, mouseX, mouseY, partialTicks, delta, this.children);
    }

    public StackLayout child(UIComponent child) {
        this.children.add(child);
        this.updateLayout();
        return this;
    }

    @Override
    public StackLayout removeChild(UIComponent child) {
        if (this.children.remove(child)) {
            child.dismount(DismountReason.REMOVED);
            this.updateLayout();
        }

        return this;
    }

    public StackLayout clearChildren() {
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
}
