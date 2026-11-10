// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.component.ItemComponent, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 *
 * Changed: only the library's way of drawing an item of 16 by 16 or smaller, the only size the mod shows here. Above that the
 * library renders the item through a picture-in-picture state of its own, which is not ported: a bigger item here is the same
 * small item scaled up. Also not ported: the count overlay and the tooltip from the stack, which the mod does not use.
 */
package io.muzy.sbc.ui.layout;

import net.minecraft.world.item.ItemStack;

// item picture, 16x16 unless sized otherwise
public class ItemComponent extends BaseUIComponent {

    protected ItemStack stack;

    protected ItemComponent(ItemStack stack) {
        this.stack = stack;
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return 16;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return 16;
    }

    @Override
    public void draw(UIGraphics graphics, int mouseX, int mouseY, float partialTicks, float delta) {
        var matrices = graphics.pose();
        matrices.pushMatrix();

        // Translate to the root of the component
        matrices.translate(this.x, this.y);

        // Scale according to component size and translate to the center
        matrices.scale(this.width / 16f, this.height / 16f);

        graphics.item(this.stack, 0, 0);

        // Clean up
        matrices.popMatrix();
    }

    public ItemComponent stack(ItemStack stack) {
        this.stack = stack;
        return this;
    }

    public ItemStack stack() {
        return this.stack;
    }
}
