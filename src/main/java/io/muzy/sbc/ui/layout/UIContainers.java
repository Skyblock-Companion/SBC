// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.container.UIContainers, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package io.muzy.sbc.ui.layout;

public final class UIContainers {

    private UIContainers() {}

    public static FlowLayout verticalFlow(Sizing horizontalSizing, Sizing verticalSizing) {
        return new FlowLayout(horizontalSizing, verticalSizing, FlowLayout.Algorithm.VERTICAL);
    }

    public static FlowLayout horizontalFlow(Sizing horizontalSizing, Sizing verticalSizing) {
        return new FlowLayout(horizontalSizing, verticalSizing, FlowLayout.Algorithm.HORIZONTAL);
    }

    public static StackLayout stack(Sizing horizontalSizing, Sizing verticalSizing) {
        return new StackLayout(horizontalSizing, verticalSizing);
    }
}
