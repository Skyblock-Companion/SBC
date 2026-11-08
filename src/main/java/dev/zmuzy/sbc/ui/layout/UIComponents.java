// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.component.UIComponents, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package dev.zmuzy.sbc.ui.layout;

import net.minecraft.world.item.ItemStack;

public final class UIComponents {

    private UIComponents() {}

    public static ItemComponent item(ItemStack item) {
        return new ItemComponent(item);
    }

    public static BoxComponent box(Sizing horizontalSizing, Sizing verticalSizing) {
        return new BoxComponent(horizontalSizing, verticalSizing);
    }
}
