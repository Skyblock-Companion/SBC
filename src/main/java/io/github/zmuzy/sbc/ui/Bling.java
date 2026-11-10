// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

// drawing bits for the hud: soft fades, hairlines, gems, capsule bars, rounded slots. nothing here draws a
// box with a border
public final class Bling {
    static final int GOLD = DripKit.GOLD, GOLD_MID = DripKit.GOLD_MID, GOLD_DARK = DripKit.GOLD_DARK, GOLD_DEEP = DripKit.GOLD_DEEP;
    private Bling() {}

    private record PlateKey(int w, int h, int r) {}

    private static final Map<PlateKey, Identifier> PLATES = new LinkedHashMap<>(64, 0.75f, true);
    private static final Map<PlateKey, Integer> SEEN = new HashMap<>();
    private static int plateSerial, bakedThisFrame;
    private static long bakeFrame;

    // a plate is ~100 rects and the hud shows a few every frame. 2nd time the same size is asked we bake it into
    // a texture and draw 1 image
    public static void plate(Ui ui, int x, int y, int w, int h, int r) {
        // translucent dark box with one thin grey edge
        ui.round(x, y, w, h, Math.min(r, 4), 0xE6141416);
        DripKit.edge(ui, x, y, w, h, 0x38FFFFFF);
        return;
    }

    // same corner steps as Ui.round. color 0 + stone = 1 uses the stone texture
    private static void roundInto(int[] px, int W, int H, int x, int y, int w, int h, int r, int color, int stone) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        for (int j = 0; j < h; j++) {
            int inset = 0;
            if (r > 0) {
                int i = j < r ? j : h - 1 - j >= r ? -1 : h - 1 - j;
                if (i >= 0) {
                    double dy = r - i - 0.5;
                    inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
                }
            }
            for (int i = inset; i < w - inset; i++) {
                int c = stone == 1 ? DripKit.stoneAt(x + i, y + j) : color;
                blend(px, W, H, x + i, y + j, c);
            }
        }
    }

    private static void blend(int[] px, int W, int H, int x, int y, int c) {
        if (x < 0 || y < 0 || x >= W || y >= H) return;
        int a = c >>> 24;
        if (a == 0) return;
        int i = y * W + x;
        if (a == 255 || px[i] == 0) {
            px[i] = a == 255 ? c : c;
            return;
        }
        int d = px[i], da = d >>> 24;
        float fa = a / 255f;
        int rr = (int) (((c >> 16) & 255) * fa + ((d >> 16) & 255) * (1 - fa));
        int gg = (int) (((c >> 8) & 255) * fa + ((d >> 8) & 255) * (1 - fa));
        int bb = (int) ((c & 255) * fa + (d & 255) * (1 - fa));
        px[i] = (Math.max(da, a) << 24) | (rr << 16) | (gg << 8) | bb;
    }

    // dark band fading out left and right, keeps text readable with no visible edge
    public static void fade(Ui ui, int x, int y, int w, int h, int color) {
        int e = Math.max(8, w / 4);
        ui.rect(x + e, y, w - 2 * e, h, color);
        ui.gradH(x, y, e, h, Ui.alpha(color, 0f), color);
        ui.gradH(x + w - e, y, e, h, color, Ui.alpha(color, 0f));
    }

    // full dark on the left, fades out to the right
    static void fadeLeft(Ui ui, int x, int y, int w, int h, int color) {
        ui.gradH(x, y, w, h, color, Ui.alpha(color, 0f));
    }

    static void fadeRight(Ui ui, int x, int y, int w, int h, int color) {
        ui.gradH(x, y, w, h, Ui.alpha(color, 0f), color);
    }

    // bright in the middle, fades to nothing at both ends
    public static void line(Ui ui, int x, int y, int w, int color) {
        ui.gradH(x, y, w / 2, 1, Ui.alpha(color, 0f), color);
        ui.gradH(x + w / 2, y, w - w / 2, 1, color, Ui.alpha(color, 0f));
    }

    // starts bright at x and fades right (dir > 0) or left (dir < 0)
    static void wing(Ui ui, int x, int y, int w, int dir, int color) {
        if (dir > 0) ui.gradH(x, y, w, 1, color, Ui.alpha(color, 0f));
        else ui.gradH(x - w, y, w, 1, Ui.alpha(color, 0f), color);
    }

    public static void diamond(Ui ui, int cx, int cy, int r, int c) {
 // plain dot
        ui.rect(cx - 1, cy - 1, 3, 3, c);
        return;
    }

    public static void gem(Ui ui, int cx, int cy, int r, int color) {
 // flat dot
        ui.disc(cx, cy, Math.max(1, r), color);
        return;
    }

    // dots around a circle like dial notches
    static void ticks(Ui ui, int cx, int cy, int r, int n, int c) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            int px = (int) Math.round(cx + Math.cos(a) * r), py = (int) Math.round(cy + Math.sin(a) * r);
            boolean major = i % 3 == 0;
            ui.rect(px - (major ? 1 : 0), py - (major ? 1 : 0), major ? 3 : 2, major ? 3 : 2, c);
        }
    }

    // dark groove, rounded fill, pale band for recent loss
    public static void capsule(Ui ui, int x, int y, int w, int h, double pct, double trail, int light, int dark) {
        int r = h / 2;
        ui.round(x - 1, y - 1, w + 2, h + 2, r + 1, 0xFF000000);
        ui.round(x, y, w, h, r, 0xFF1A1714);
        pct = Math.max(0, Math.min(1, pct));
        trail = Math.max(pct, Math.min(1, trail));
        int tw = (int) Math.round(w * trail), fw = (int) Math.round(w * pct);
        if (tw > fw) ui.round(x, y, tw, h, r, 0x55FFFFFF);
        if (fw > 0) {
            ui.round(x, y, Math.max(fw, h / 2 + 1), h, r, dark);
            ui.round(x, y, Math.max(fw, h / 2 + 1), Math.max(2, h * 2 / 3), r, Ui.lerpColor(dark, light, 0.75f));
            if (fw > 8) ui.round(x + 2, y + 1, fw - 4, Math.max(1, h / 4), Math.max(1, h / 8), 0x66FFFFFF);
        }
    }

    // dark glass slot with a thin rim (or rarity color), selected one glows
    private record SlotKey(int s, int rarity, boolean selected) {}

    private static final Map<SlotKey, Identifier> SLOTS = new HashMap<>();

    // 9 of these every frame, ~60 rects each, so bake once per look
    static void slot(Ui ui, int x, int y, int s, int rarity, boolean selected) {
        SlotKey k = new SlotKey(s, rarity, selected);
        Identifier id = SLOTS.get(k);
        if (id == null) {
            int pad = selected ? 4 : 1, W = s + 2 * pad, H = W;
            int[] px = new int[W * H];
            int o = pad;
            if (selected) {
                roundInto(px, W, H, o - 4, o - 4, s + 8, s + 8, 8, Ui.alpha(GOLD, 0.14f), 0);
                roundInto(px, W, H, o - 3, o - 3, s + 6, s + 6, 7, Ui.alpha(GOLD, 0.22f), 0);
            }
            roundInto(px, W, H, o - 1, o - 1, s + 2, s + 2, 5, 0xFF000000, 0);
            int rim = selected ? GOLD : rarity != 0 ? Ui.alpha(rarity, 0.95f) : Ui.alpha(GOLD_MID, 0.85f);
            roundInto(px, W, H, o, o, s, s, 4, rim, 0);
            roundInto(px, W, H, o + 1, o + 1, s - 2, s - 2, 3, 0xFF14120F, 0);
            if (rarity != 0) roundInto(px, W, H, o + 1, o + 1, s - 2, s - 2, 3, Ui.alpha(rarity, 0.16f), 0);
            roundInto(px, W, H, o + 2, o + 2, s - 4, Math.max(2, s / 3), 2, 0x14FFFFFF, 0);
            id = Identifier.fromNamespaceAndPath("sbcompanion", "slot_" + plateSerial++);
            DynamicTexture tex = new DynamicTexture(() -> "sbcompanion slot", W, H, true);
            NativeImage img = tex.getPixels();
            for (int yy = 0; yy < H; yy++) for (int xx = 0; xx < W; xx++) img.setPixel(xx, yy, px[yy * W + xx]);
            tex.upload();
            Minecraft.getInstance().getTextureManager().register(id, tex);
            SLOTS.put(k, id);
        }
        int pad = selected ? 4 : 1, W = s + 2 * pad;
        ui.g.blit(RenderPipelines.GUI_TEXTURED, id, x - pad, y - pad, 0f, 0f, W, W, W, W, W, W, 0xFFFFFFFF);
        Ui.elements++;
    }

    static void slotVector(Ui ui, int x, int y, int s, int rarity, boolean selected) {
        if (selected) {
            ui.round(x - 4, y - 4, s + 8, s + 8, 8, Ui.alpha(GOLD, 0.14f));
            ui.round(x - 3, y - 3, s + 6, s + 6, 7, Ui.alpha(GOLD, 0.22f));
        }
        ui.round(x - 1, y - 1, s + 2, s + 2, 5, 0xFF000000);
        int rim = selected ? GOLD : rarity != 0 ? Ui.alpha(rarity, 0.95f) : Ui.alpha(GOLD_MID, 0.85f);
        ui.round(x, y, s, s, 4, rim);
        ui.round(x + 1, y + 1, s - 2, s - 2, 3, 0xFF14120F);
        if (rarity != 0) ui.round(x + 1, y + 1, s - 2, s - 2, 3, Ui.alpha(rarity, 0.16f));
        ui.round(x + 2, y + 2, s - 4, Math.max(2, s / 3), 2, 0x14FFFFFF);
    }

    static void pointer(Ui ui, int cx, int y, int c) {
        for (int i = 0; i < 4; i++) ui.rect(cx - 3 + i, y + i, 7 - 2 * i, 1, c);
    }
}
