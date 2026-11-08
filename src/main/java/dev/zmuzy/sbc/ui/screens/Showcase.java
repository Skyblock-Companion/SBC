// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui.screens;

import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.layout.BaseUIComponent;
import dev.zmuzy.sbc.ui.layout.Sizing;
import dev.zmuzy.sbc.ui.layout.Surface;
import dev.zmuzy.sbc.ui.layout.UIGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

// look of the first run pages (setup, keys, update): slow moving night sky, 2 soft glows drifting, twinkling
// stars, the floating sbc island, big title, glass panel
final class Showcase {
    static final Identifier LOGO = Identifier.fromNamespaceAndPath("sbcompanion", "textures/gui/setup/logo.png");
    private static final long START = System.currentTimeMillis();
    private static final int STARS = 90;

    private Showcase() {}

    private static float time() {
        return (System.currentTimeMillis() - START) / 1000f;
    }

    // full screen bg
    static Surface backdrop() {
        return (g, c) -> {
            int x = c.x(), y = c.y(), w = c.width(), h = c.height();
            float t = time();
            SbcGradient.rect(g, x, y, w, h, 0xFF0C1630, 0xFF0A1226, 0xFF05080F, 0xFF070B16);
            glow(g, x + (int) (w * (0.28 + 0.06 * Math.sin(t * 0.21))), y + (int) (h * (0.32 + 0.05 * Math.cos(t * 0.17))), Math.max(w, h) * 5 / 12, 0x2E6BFF);
            glow(g, x + (int) (w * (0.74 + 0.05 * Math.cos(t * 0.19))), y + (int) (h * (0.70 + 0.06 * Math.sin(t * 0.23))), Math.max(w, h) * 5 / 12, 0x8A3CFF);
            for (int i = 0; i < STARS; i++) {
                int hsh = i * 0x9E3779B1;
                float sx = (hsh >>> 8 & 0xFFFF) / 65535f, sy = (hsh >>> 3 & 0xFFFF) / 65535f;
                float speed = 2 + (i % 5);
                int px = x + (int) (sx * w);
                int py = y + Math.floorMod((int) (sy * h - t * speed), Math.max(1, h));
                float tw = 0.25f + 0.75f * (float) Math.abs(Math.sin(t * (0.6 + (i % 7) * 0.15) + i));
                int size = i % 11 == 0 ? 2 : 1;
                g.fill(px, py, px + size, py + size, Ui.alpha(0xFFFFFFFF, tw * (size == 2 ? 0.9f : 0.6f)));
            }
            int edge = Math.min(w, h) / 6;
            SbcGradient.rect(g, x, y, w, edge, 0x99000000, 0x99000000, 0x00000000, 0x00000000);
            SbcGradient.rect(g, x, y + h - edge, w, edge, 0x00000000, 0x00000000, 0xAA000000, 0xAA000000);
        };
    }

    static final Identifier GLOW = Identifier.fromNamespaceAndPath("sbcompanion", "textures/gui/setup/glow.png");

    // soft round glow in a color: one smooth gradient picture tinted + stretched
    private static void glow(UIGraphics g, int cx, int cy, int r, int rgb) {
        try {
            g.blit(RenderPipelines.GUI_TEXTURED, GLOW, cx - r, cy - r, 0f, 0f, 2 * r, 2 * r, 256, 256, 256, 256, 0x55000000 | (rgb & 0xFFFFFF));
        } catch (RuntimeException noTextures) {
            // picture made without game textures (tests): faint discs instead
            for (int s = 1; s <= 6; s++) {
                int rad = r * (7 - s) / 6;
                for (int dy = -rad; dy <= rad; dy += 3) {
                    int half = (int) Math.sqrt((double) rad * rad - (double) dy * dy);
                    g.fill(cx - half, cy + dy, cx + half, cy + dy + 3, (0x0D << 24) | (rgb & 0xFFFFFF));
                }
            }
        }
    }

    // main panel: dark glass, lit top edge in the accent, shadow under
    static Surface panel(int accent) {
        int a = accent | 0xFF000000;
        return (g, c) -> {
            int x = c.x(), y = c.y(), w = c.width(), h = c.height();
            g.fill(x + 3, y + 5, x + w + 3, y + h + 5, 0x60000000);
            SbcGradient.rect(g, x, y, w, h, 0xE6142036, 0xE6111B2E, 0xEE0A111E, 0xEE0C1424);
            g.drawRectOutline(x, y, w, h, 0x40FFFFFF);
            SbcGradient.rect(g, x + 1, y, w - 2, 1, Ui.alpha(a, 0.05f), Ui.alpha(a, 1f), Ui.alpha(a, 1f), Ui.alpha(a, 0.05f));
            SbcGradient.rect(g, x + 1, y + 1, w - 2, 16, Ui.alpha(a, 0.16f), Ui.alpha(a, 0.16f), Ui.alpha(a, 0f), Ui.alpha(a, 0f));
        };
    }

    // bigger text in the game font, centered
    static final class BigText extends BaseUIComponent {
        private final String text;
        private final float scale;
        private final int top, bottom;

        BigText(String text, float scale, int top, int bottom) {
            this.text = text;
            this.scale = scale;
            this.top = top | 0xFF000000;
            this.bottom = bottom | 0xFF000000;
            this.horizontalSizing(Sizing.fill(100));
            this.verticalSizing(Sizing.fixed(Math.round(10 * scale)));
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            Font f = Minecraft.getInstance().font;
            int tw = f.width(text);
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(x + (width - tw * scale) / 2f, y);
            pose.scale(scale, scale);
            g.text(f, text, 1, 1, 0xFF0A1830, false);
            g.text(f, text, 0, 0, top, false);
            pose.popMatrix();
            // bottom half in the 2nd color: same text again cut to its bottom half
            g.enableScissor(x, y + Math.round(5 * scale), x + width, y + Math.round(10 * scale));
            pose.pushMatrix();
            pose.translate(x + (width - tw * scale) / 2f, y);
            pose.scale(scale, scale);
            g.text(f, text, 0, 0, bottom, false);
            pose.popMatrix();
            g.disableScissor();
        }
    }

    // sbc island floating up and down
    static final class Logo extends BaseUIComponent {
        private final int size;

        Logo(int size) {
            this.size = size;
            this.horizontalSizing(Sizing.fill(100));
            this.verticalSizing(Sizing.fixed(size + 6));
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            int bob = Math.round((float) Math.sin(time() * 1.6) * 3);
            int lx = x + (width - size) / 2, ly = y + 3 + bob;
            try {
                g.blit(RenderPipelines.GUI_TEXTURED, LOGO, lx, ly, 0f, 0f, size, size, 256, 256, 256, 256);
            } catch (RuntimeException noTextures) {
                // picture without game textures (tests): a disc where the island goes
                for (int dy = 0; dy < size; dy++) {
                    int half = (int) Math.sqrt(size * size / 4.0 - (dy - size / 2.0) * (dy - size / 2.0));
                    g.fill(lx + size / 2 - half, ly + dy, lx + size / 2 + half, ly + dy + 1, 0xFF3B7BD8);
                }
            }
        }
    }
}
