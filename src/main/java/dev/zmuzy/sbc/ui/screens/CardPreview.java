// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui.screens;

import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.layout.BaseUIComponent;
import dev.zmuzy.sbc.ui.layout.Sizing;
import dev.zmuzy.sbc.ui.layout.UIGraphics;

// a HUD card the way the HUD editor shows it, with its example numbers, made small enough to sit under the helper
// it belongs to in the setup and in what's new. "Example" over it says the numbers are not yours
final class CardPreview extends BaseUIComponent {
    private final HudElement element;
    private final float scale;
    private final int w, h;
    private final Ui ui = new Ui();

    private CardPreview(HudElement element, int maxWidth) {
        this.element = element;
        boolean was = element.demo;
        element.demo = true;
        int cw, ch;
        try {
            cw = Math.max(1, element.width());
            ch = Math.max(1, element.height());
        } finally {
            element.demo = was;
        }
        scale = Math.min(1f, maxWidth / (float) cw);
        w = Math.round(cw * scale);
        h = Math.round(ch * scale) + 10;
        horizontalSizing(Sizing.fixed(w));
        verticalSizing(Sizing.fixed(h));
    }

    // the card of a feature, or null when it has none
    static CardPreview of(Feature f, int maxWidth) {
        for (HudElement e : HudLayout.elements()) if (e.feature() == f) return new CardPreview(e, maxWidth);
        return null;
    }

    @Override
    public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
        g.text(net.minecraft.client.Minecraft.getInstance().font, "Example", x, y, 0xFF8E9CAD, true);
        boolean was = element.demo;
        element.demo = true;
        try {
            ui.begin(g, -1, -1);
            g.pose().pushMatrix();
            g.pose().translate(x, y + 10);
            g.pose().scale(scale, scale);
            element.draw(ui, g, 0, 0);
            g.pose().popMatrix();
        } catch (RuntimeException ignored) {
            // a card that needs the game to draw: the example line stays alone
        } finally {
            element.demo = was;
        }
    }
}
