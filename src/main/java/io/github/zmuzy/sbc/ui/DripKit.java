// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui;

import com.mojang.blaze3d.platform.NativeImage;
import io.github.zmuzy.sbc.track.Watcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Random;

// drawing kit for the classic menus: baked stone + leather textures, gold frames, inset slots that glow in
// rarity color, segmented bars, ribbons
public final class DripKit {
    // shared palette: greys + a yellow accent (see applyTheme)
    public static int GOLD = 0xFFFAC775, GOLD_MID = 0xFFBA7517, GOLD_DARK = 0xFF854F0B, GOLD_DEEP = 0xFF3A2208;
    public static int INK = 0xFF121211, TEXT = 0xFFF1EFE8, DIM = 0xFFB4B2A9, GREEN = 0xFF97C459, RED = 0xFFF09595;

    static void applyTheme() {
        GOLD = 0xFFFFD866; GOLD_MID = 0xFF4A4A52; GOLD_DARK = 0xFF303036; GOLD_DEEP = 0xFF1B1B1F;
        INK = 0xFF101012; TEXT = 0xFFF0F0F0; DIM = 0xFFB0B0B4; GREEN = 0xFF8CDB6A; RED = 0xFFFF6B6B;
    }

    public static void edge(Ui ui, int x, int y, int w, int h, int c) {
        ui.rect(x, y, w, 1, c);
        ui.rect(x, y + h - 1, w, 1, c);
        ui.rect(x, y + 1, 1, h - 2, c);
        ui.rect(x + w - 1, y + 1, 1, h - 2, c);
    }

    private static final int T = 64;
    private static final Identifier STONE = Identifier.fromNamespaceAndPath("sbcompanion", "rpg_stone");
    private static final Identifier LEATHER = Identifier.fromNamespaceAndPath("sbcompanion", "rpg_leather");
    private static boolean baked;
    private static final int[] stonePx = new int[T * T];
    private DripKit() {}

    // --- materials ---

    private static void bake() {
        if (baked) return;
        baked = true;
        make(STONE, 0x2B2B29, 7, 5, 11);
        make(LEATHER, 0x2A211A, 6, 4, 23);
    }

    // tileable texture: grain + 8x8 blotches + a few light specks so it looks worked
    private static void make(Identifier id, int base, int grain, int blotch, long seed) {
        Random r = new Random(seed);
        int[] cell = new int[(T / 8) * (T / 8)];
        for (int i = 0; i < cell.length; i++) cell[i] = r.nextInt(blotch * 2 + 1) - blotch;
        DynamicTexture tex = new DynamicTexture(() -> "sbcompanion " + id.getPath(), T, T, true);
        NativeImage img = tex.getPixels();
        for (int y = 0; y < T; y++)
            for (int x = 0; x < T; x++) {
                int d = r.nextInt(grain * 2 + 1) - grain + cell[(y / 8) * (T / 8) + x / 8];
                if (r.nextInt(90) == 0) d += 14; // light speck
                if (y % 16 == 0) d -= 3; // faint mortar lines
                int rr = clamp(((base >> 16) & 0xFF) + d), gg = clamp(((base >> 8) & 0xFF) + d), bb = clamp((base & 0xFF) + d);
                int px = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
                img.setPixel(x, y, px);
                if (id == STONE) stonePx[y * T + x] = px;
            }
        tex.upload();
        Minecraft.getInstance().getTextureManager().register(id, tex);
    }

    public static Identifier stoneTexture() {
        bake();
        return STONE;
    }

    // one pixel of the stone texture (tiles forever), for plates baked into their own texture
    static int stoneAt(int x, int y) {
        bake();
        return stonePx[Math.floorMod(y, T) * T + Math.floorMod(x, T)];
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static void tile(Ui ui, Identifier id, int x, int y, int w, int h) {
        bake();
        for (int ty = 0; ty < h; ty += T)
            for (int tx = 0; tx < w; tx += T) {
                int bw = Math.min(T, w - tx), bh = Math.min(T, h - ty);
                ui.g.blit(RenderPipelines.GUI_TEXTURED, id, x + tx, y + ty, 0f, 0f, bw, bh, bw, bh, T, T, 0xFFFFFFFF);
            }
    }

    // corners drawn narrower = rounded
    public static void stoneRound(Ui ui, int x, int y, int w, int h, int r) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        tile(ui, STONE, x, y + r, w, h - 2 * r);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            tile(ui, STONE, x + inset, y + i, w - 2 * inset, 1);
            tile(ui, STONE, x + inset, y + h - 1 - i, w - 2 * inset, 1);
        }
    }

    // --- frames ---

    // black outline, 2px bevelled gold border, stone face
    public static void panel(Ui ui, int x, int y, int w, int h) {
        ui.round(x, y, w, h, 6, 0xF0131316);
        edge(ui, x, y, w, h, 0x40FFFFFF);
    }

    // thin gold edge, stone face, soft shadow under the top
    public static void box(Ui ui, int x, int y, int w, int h, float hover) {
        ui.rect(x - 1, y - 1, w + 2, h + 2, 0xFF000000);
        ui.rect(x, y, w, h, Ui.lerpColor(GOLD_DARK, GOLD, hover));
        tile(ui, STONE, x + 1, y + 1, w - 2, h - 2);
        if (hover > 0) ui.rect(x + 1, y + 1, w - 2, h - 2, Ui.alpha(GOLD, 0.10f * hover));
        ui.gradV(x + 1, y + 1, w - 2, Math.min(10, h / 3), 0x58000000, 0x00000000);
        ui.rect(x + 1, y + 1, w - 2, 1, 0x1CFFFFFF);
    }

    // recessed leather, for quest scrolls + stat wells
    public static void well(Ui ui, int x, int y, int w, int h) {
        ui.rect(x - 1, y - 1, w + 2, h + 2, 0xFF000000);
        tile(ui, LEATHER, x, y, w, h);
        ui.gradV(x, y, w, Math.min(9, h / 3), 0x70000000, 0x00000000);
        ui.rect(x, y + h - 1, w, 1, 0x22FFFFFF);
    }

    // ribbon with a title centered on cx, gold diamonds on both ends
    public static void plaque(Ui ui, int cx, int y, int w, int h, String text) {
        int x = cx - w / 2;
        ui.rect(x - 1, y - 1, w + 2, h + 2, 0xFF000000);
        ui.rect(x, y, w, h, GOLD_DARK);
        ui.gradV(x + 1, y + 1, w - 2, h - 2, 0xFF4A2C07, 0xFF1E1204);
        ui.rect(x + 1, y + 1, w - 2, 1, GOLD);
        ui.rect(x + 1, y + h - 2, w - 2, 1, GOLD_MID);
        diamond(ui, x - 2, y + h / 2);
        diamond(ui, x + w + 1, y + h / 2);
        ui.textShadow(text, cx - ui.width(text) / 2, y + (h - 8) / 2, GOLD);
    }

    private static void diamond(Ui ui, int cx, int cy) {
        ui.rect(cx - 1, cy - 3, 3, 7, GOLD);
        ui.rect(cx - 3, cy - 1, 7, 3, GOLD);
        ui.rect(cx - 1, cy - 1, 3, 3, GOLD_DEEP);
    }

    // gold rule with a diamond in the middle
    public static void divider(Ui ui, int x, int y, int w) {
        ui.gradH(x, y, w / 2, 1, 0x00BA7517, GOLD_MID);
        ui.gradH(x + w / 2, y, w - w / 2, 1, GOLD_MID, 0x00BA7517);
        ui.rect(x + w / 2 - 1, y - 1, 3, 3, GOLD);
    }

    // --- items ---

    // last lore line ("LEGENDARY SWORD", "RARE"...), 0 = none
    public static int rarity(ItemStack st) {
        if (st.isEmpty()) return 0;
        List<String> lore = Watcher.lore(st);
        for (int i = lore.size() - 1; i >= Math.max(0, lore.size() - 3); i--) {
            String l = lore.get(i).toUpperCase(Locale.ROOT);
            if (l.contains("ULTIMATE")) return 0xFFFF5555;
            if (l.contains("DIVINE")) return 0xFF55FFFF;
            if (l.contains("MYTHIC")) return 0xFFFF55FF;
            if (l.contains("LEGENDARY")) return 0xFFFFAA00;
            if (l.contains("EPIC")) return 0xFFAA00AA;
            if (l.contains("RARE")) return 0xFF5555FF;
            if (l.contains("UNCOMMON")) return 0xFF55FF55;
            if (l.contains("COMMON")) return 0xFFE0E0E0;
        }
        return 0;
    }

    // dark well, light lower edge, colored rim + glow when it has a rarity
    public static void slotBox(Ui ui, int x, int y, int s, int rarity, boolean hover) {
        ui.rect(x - 1, y - 1, s + 2, s + 2, 0xFF000000);
        ui.rect(x, y, s, s, hover ? 0xFF34322E : 0xFF191917);
        ui.gradV(x, y, s, s / 2, 0x40000000, 0x00000000);
        ui.rect(x, y + s - 1, s, 1, 0x2CFFFFFF);
        ui.rect(x + s - 1, y, 1, s, 0x2CFFFFFF);
        if (rarity != 0) {
            ui.rect(x, y, s, s, Ui.alpha(rarity, 0.16f));
            ui.rect(x, y, s, 1, Ui.alpha(rarity, 0.85f));
            ui.rect(x, y + s - 1, s, 1, Ui.alpha(rarity, 0.85f));
            ui.rect(x, y, 1, s, Ui.alpha(rarity, 0.85f));
            ui.rect(x + s - 1, y, 1, s, Ui.alpha(rarity, 0.85f));
        }
        if (hover) {
            ui.rect(x - 1, y - 1, s + 2, 1, GOLD);
            ui.rect(x - 1, y + s, s + 2, 1, GOLD);
            ui.rect(x - 1, y - 1, 1, s + 2, GOLD);
            ui.rect(x + s, y - 1, 1, s + 2, GOLD);
        }
    }

    public static void slot(Ui ui, int x, int y, int s, ItemStack st, boolean hover) {
        slotBox(ui, x, y, s, rarity(st), hover);
        if (!st.isEmpty()) ui.icon(st, x + (s - Math.round(16 * ((s - 6) / 16f))) / 2, y + (s - Math.round(16 * ((s - 6) / 16f))) / 2, (s - 6) / 16f);
    }

    // --- bars, buttons, scenery ---

    // black trough, glossy fill, 10 segments, bright top edge
    public static void bar(Ui ui, int x, int y, int w, int h, double pct, int color) {
        ui.rect(x - 1, y - 1, w + 2, h + 2, 0xFF000000);
        ui.rect(x, y, w, h, 0xFF22201C);
        int fw = (int) Math.round(w * Math.max(0, Math.min(1, pct)));
        if (fw > 0) {
            ui.rect(x, y, fw, h, Ui.lerpColor(color, 0xFF000000, 0.22f));
            ui.rect(x, y, fw, Math.max(1, h / 2), Ui.lerpColor(color, 0xFFFFFFFF, 0.22f));
            ui.rect(x, y, fw, 1, 0x66FFFFFF);
        }
        for (int i = 1; i < 10; i++) ui.rect(x + w * i / 10, y, 1, h, 0x38000000);
    }

    // registers the click too
    public static void button(Ui ui, String id, int x, int y, int w, int h, String label, boolean primary, Runnable click) {
        float hv = ui.hoverAnim("button" + id, x, y, w, h);
        ui.rect(x - 1, y - 1, w + 2, h + 2, 0xFF000000);
        ui.rect(x, y, w, h, Ui.lerpColor(primary ? GOLD_DARK : 0xFF5A5852, GOLD, hv));
        ui.rect(x + 1, y + 1, w - 2, h - 2, Ui.lerpColor(primary ? 0xFF4A2C07 : 0xFF2A2927, primary ? 0xFF6B4410 : 0xFF3C3A36, hv));
        ui.gradV(x + 1, y + 1, w - 2, h / 2, 0x28FFFFFF, 0x00FFFFFF);
        ui.textShadow(label, x + w / 2 - ui.width(label) / 2, y + (h - 8) / 2, Ui.lerpColor(primary ? GOLD : TEXT, 0xFFFFFFFF, hv * 0.4f));
        if (click != null) ui.click(x, y, w, h, click);
    }

    public static void ellipse(Ui ui, int cx, int cy, int rx, int ry, int c) {
        for (int i = -ry; i < ry; i++) {
            double dy = (i + 0.5) / ry;
            int hw = (int) Math.round(rx * Math.sqrt(Math.max(0, 1 - dy * dy)));
            if (hw > 0) ui.rect(cx - hw, cy + i, hw * 2, 1, c);
        }
    }

    // level badge: gold ring, dark center
    public static void badge(Ui ui, int cx, int cy, int r, String text) {
        ui.disc(cx, cy, r + 2, 0xFF000000);
        ui.disc(cx, cy, r + 1, GOLD_DARK);
        ui.disc(cx, cy, r, GOLD_DEEP);
        ui.disc(cx, cy, r - 2, 0xFF17140F);
        ui.textShadow(text, cx - ui.width(text) / 2, cy - 4, GOLD);
    }

    // dark room, warm glow in the middle, shaded edges
    public static void backdrop(Ui ui, int w, int h, float open) {
        ui.rect(0, 0, w, h, Ui.alpha(0xFF070706, 0.8f * open));
        ui.gradV(0, 0, w, h / 5, Ui.alpha(0xFF000000, 0.55f * open), 0x00000000);
        ui.gradV(0, h - h / 5, w, h / 5, 0x00000000, Ui.alpha(0xFF000000, 0.6f * open));
        ui.gradH(0, 0, w / 8, h, Ui.alpha(0xFF000000, 0.5f * open), 0x00000000);
        ui.gradH(w - w / 8, 0, w / 8, h, 0x00000000, Ui.alpha(0xFF000000, 0.5f * open));
    }
}
