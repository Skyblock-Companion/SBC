// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.core.Surface, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package io.github.zmuzy.sbc.ui.layout;

// what a container draws behind its children
public interface Surface {

    Surface BLANK = (context, component) -> {};

    static Surface flat(int color) {
        return (context, component) -> context.fill(component.x(), component.y(), component.x() + component.width(), component.y() + component.height(), color);
    }

    static Surface outline(int color) {
        return (context, component) -> context.drawRectOutline(component.x(), component.y(), component.width(), component.height(), color);
    }

    void draw(UIGraphics context, ParentUIComponent component);

    default Surface and(Surface surface) {
        return (context, component) -> {
            this.draw(context, component);
            surface.draw(context, component);
        };
    }
}
