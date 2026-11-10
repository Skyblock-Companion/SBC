// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.component.BoxComponent, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package io.muzy.sbc.ui.layout;

// one color rect, filled or just the outline
public class BoxComponent extends BaseUIComponent {

    protected boolean fill = false;

    protected final Observable<Color> color = Observable.of(Color.BLACK);

    public BoxComponent(Sizing horizontalSizing, Sizing verticalSizing) {
        this.sizing(horizontalSizing, verticalSizing);
    }

    @Override
    public void draw(UIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        final int color = this.color.get().argb();

        if (this.fill) {
            graphics.drawGradientRect(this.x, this.y, this.width, this.height, color, color, color, color);
        } else {
            graphics.drawRectOutline(this.x, this.y, this.width, this.height, color);
        }
    }

    public BoxComponent fill(boolean fill) {
        this.fill = fill;
        return this;
    }

    public boolean fill() {
        return this.fill;
    }

    public BoxComponent color(Color color) {
        this.color.set(color);
        return this;
    }
}
