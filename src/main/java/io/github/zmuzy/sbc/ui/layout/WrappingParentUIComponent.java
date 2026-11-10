// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.container.WrappingParentUIComponent, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package io.github.zmuzy.sbc.ui.layout;

import java.util.Collections;
import java.util.List;

public abstract class WrappingParentUIComponent<C extends UIComponent> extends BaseParentUIComponent {

    protected C child;
    protected List<UIComponent> childView;

    protected WrappingParentUIComponent(Sizing horizontalSizing, Sizing verticalSizing, C child) {
        super(horizontalSizing, verticalSizing);
        this.child = child;
        this.childView = Collections.singletonList(this.child);
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return this.child.fullSize().width() + this.padding.get().horizontal();
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return this.child.fullSize().height() + this.padding.get().vertical();
    }

    @Override
    public void layout(Size space) {
        this.child.inflate(this.calculateChildSpace(space));
        this.child.mount(this, this.childMountX(), this.childMountY());
    }

    protected int childMountX() {
        return this.x + child.margins().get().left() + this.padding.get().left();
    }

    protected int childMountY() {
        return this.y + child.margins().get().top() + this.padding.get().top();
    }

    public WrappingParentUIComponent<C> child(C newChild) {
        if (this.child != null) {
            this.child.dismount(DismountReason.REMOVED);
        }

        this.child = newChild;
        this.childView = Collections.singletonList(this.child);

        this.updateLayout();
        return this;
    }

    public C child() {
        return this.child;
    }

    @Override
    public List<UIComponent> children() {
        return this.childView;
    }

    @Override
    public ParentUIComponent removeChild(UIComponent child) {
        throw new UnsupportedOperationException("Cannot remove the child of a wrapping component");
    }
}
