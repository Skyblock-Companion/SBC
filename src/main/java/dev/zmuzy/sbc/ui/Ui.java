// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.core.LegacyItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// tiny immediate mode ui kit over the vanilla gui renderer: glass panels, gradients, shadows, hover anims,
// bars, chips, charts, click/drag boxes. everything the menu and hud draw goes through here
public final class Ui {
    // --- palette --- applyTheme picks the colors every frame
    public static int BG = 0xE0080706, PANEL = 0xF01B1814, PANEL2 = 0xF0231F19, PANEL3 = 0xF02E2820, STROKE = 0x33FAC775;
    public static int TEXT = 0xFFF1EFE8, MUTED = 0xFFB4B2A9, DIM = 0xFF8B8478;
    public static int VIOLET = 0xFFBA7517, CYAN = 0xFFFAC775, GREEN = 0xFF97C459, GOLD = 0xFFFAC775, RED = 0xFFF09595, PINK = 0xFFF0997B, ORANGE = 0xFFEF9F27;
    // buttons, ghost buttons, text fields. set in applyTheme
    public static int BTN_BORDER = 0xFF854F0B, BTN_BORDER_HOVER = 0xFFFAC775, BTN_FILL = 0xFF4A2C07, BTN_FILL_HOVER = 0xFF6B4410;
    public static int SOFT_BORDER = 0xFF5A5852, SOFT_BORDER_HOVER = 0xFFBA7517, SOFT_FILL = 0xFF2A2927, SOFT_FILL_HOVER = 0xFF3C3A36;
    public static int GHOST_FILL = 0xFF4A4338, INPUT_BG = 0xFF100E0B, INPUT_BORDER = 0xFF4A4338;
    private static boolean paletteRpg = true;

    // switch the shared palette to the current theme. cheap, runs at the start of every draw
    public static void applyTheme() {
        if (!paletteRpg) return;
        paletteRpg = false;
        DripKit.applyTheme();
        BG = 0xE00A0F17; PANEL = 0xF0121924; PANEL2 = 0xF01A2330; PANEL3 = 0xF0242F3E; STROKE = 0x33FFFFFF;
        TEXT = 0xFFF2F6FA; MUTED = 0xFFB9C6D3; DIM = 0xFF8E9CAD;
        VIOLET = 0xFF5F7FA3; CYAN = 0xFF5CD8FF; GREEN = 0xFF8CDB6A; GOLD = 0xFFFFD866; RED = 0xFFFF6B6B; PINK = 0xFFFF8AB4; ORANGE = 0xFFFFAA4D;
        BTN_BORDER = 0xFF3A5A7A; BTN_BORDER_HOVER = 0xFF55D7FF; BTN_FILL = 0xFF1E3346; BTN_FILL_HOVER = 0xFF27465F;
        SOFT_BORDER = 0xFF3C4A5C; SOFT_BORDER_HOVER = 0xFF7F93AB; SOFT_FILL = 0xFF1F2A38; SOFT_FILL_HOVER = 0xFF2B394B;
        GHOST_FILL = 0xFF2F3B4C; INPUT_BG = 0xFF0C121B; INPUT_BORDER = 0xFF3C4A5C;
    }

    public record Hit(int x, int y, int w, int h, Runnable click, Runnable right, Drag drag) {}

    public interface Drag {
        void at(double mx, double my);
    }

    // no graphics, just for measuring text
    public static final Ui MEASURE = new Ui();

    public GuiGraphicsExtractor g;
    // multiplies everything drawn here. the hud fades stuff in with it
    public float opacity = 1f;
    public Font font = Minecraft.getInstance().font;

    // font fetched late bc this class can load before mc has one
    private Font f() {
        if (font == null) font = Minecraft.getInstance().font;
        return font;
    }
    public int mx, my;
    public long now;
    public final List<Hit> hits = new ArrayList<>();
    public final List<List<Component>> tooltip = new ArrayList<>();
    private static final Map<String, Float> anim = new HashMap<>();
    private long lastFrame = System.currentTimeMillis();
    // real unscaled mouse pos, for vanilla tooltips drawn outside our scaling
    public int realMx, realMy;
    private float frameDt;
    // content clip. hits outside it get ignored so scrolled away buttons cant be clicked
    public int clipX0 = Integer.MIN_VALUE, clipY0 = Integer.MIN_VALUE, clipX1 = Integer.MAX_VALUE, clipY1 = Integer.MAX_VALUE;

    public void begin(GuiGraphicsExtractor g, int mx, int my) {
        begin(g, mx, my, mx, my);
    }

    // new frame layer every this many pieces, 0 = never. the game checks each piece against its whole layer to
    // know whats on top
    // so hundreds of pieces = hundreds squared. later pieces go on top anyway, same picture
    public int strataEvery;
    private int sinceStratum;

    private void piece() {
        if (strataEvery > 0 && ++sinceStratum >= strataEvery) {
            g.nextStratum();
            sinceStratum = 0;
        }
    }

    // start counting again: the caller just opened a new layer
    public void newLayer() {
        sinceStratum = 0;
    }

    public void begin(GuiGraphicsExtractor g, int mx, int my, int realMx, int realMy) {
        applyTheme();
        f();
        sinceStratum = 0;
        this.g = g;
        this.mx = mx;
        this.my = my;
        this.realMx = realMx;
        this.realMy = realMy;
        this.now = System.currentTimeMillis();
        frameDt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        hits.clear();
        tooltip.clear();
        boxN = 0;
    }

    // --- primitives ---

    // gui elements we sent this frame, the profiler shows it
    public static int elements;

    public void rect(int x, int y, int w, int h, int c) {
        if (w > 0 && h > 0) {
            g.fill(x, y, x + w, y + h, opacity >= 1f ? c : alpha(c, opacity));
            piece();
            elements++;
        }
    }

    public void round(int x, int y, int w, int h, int r, int c) {
        if (w <= 0 || h <= 0) return;
        r = 0; // classic = sharp corners everywhere
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) {
            rect(x, y, w, h, c);
            return;
        }
        rect(x, y + r, w, h - 2 * r, c);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            rect(x + inset, y + i, w - 2 * inset, 1, c);
            rect(x + inset, y + h - 1 - i, w - 2 * inset, 1, c);
        }
    }

    // panel: drop shadow, black outline, bevel edge, dark face
    public void panel(int x, int y, int w, int h, int r, int c) {
        // classic: face gets darker going down, lit top edge, thin outline
        gradV(x, y, w, h, c, lerpColor(c, 0xFF000000 | (c & 0xFF000000), 0.25f));
        rect(x, y, w, 1, 0x3CFFFFFF);
        rect(x, y + h - 1, w, 1, 0x60000000);
        rect(x, y + 1, 1, h - 2, 0x18FFFFFF);
        rect(x + w - 1, y + 1, 1, h - 2, 0x18FFFFFF);
    }

    // light to clear gradient over the top + 1px highlight, gives it depth
    public void sheen(int x, int y, int w, int h, int r) {
        int sh = Math.min(h / 2, 40);
        gradV(x + r / 2, y + 1, w - r, sh, 0x12FFFFFF, 0x00FFFFFF);
        rect(x + r, y, w - 2 * r, 1, 0x30FFFFFF);
    }

    public void shadow(int x, int y, int w, int h, int r) {
        for (int i = 6; i >= 1; i--) round(x - i, y - i + 3, w + 2 * i, h + 2 * i, r + i, (6 + (6 - i) * 5) << 24);
    }

    // rounded tile behind an icon, accent tint when active
    public void iconTile(int x, int y, int size, boolean active, float hover) {
        int base = active ? BTN_FILL : lerpColor(INPUT_BG, SOFT_FILL_HOVER, hover);
        round(x, y, size, size, 5, base);
        if (active) {
            roundGradH(x, y, size, size, 5, alpha(VIOLET, 0.55f), alpha(CYAN, 0.45f));
            rect(x + 3, y, size - 6, 1, 0x50FFFFFF);
        } else rect(x + 3, y, size - 6, 1, 0x18FFFFFF);
    }

    // filled circle, scanlines
    public void disc(int cx, int cy, int r, int c) {
        for (int i = -r; i < r; i++) {
            double dy = i + 0.5;
            int hw = (int) Math.round(Math.sqrt(Math.max(0, r * r - dy * dy)));
            if (hw > 0) rect(cx - hw, cy + i, hw * 2, 1, c);
        }
    }

    // ring made of dots (round minimap frame, progress rings)
    public void ring(int cx, int cy, int radius, int thickness, double from, double to, int c1, int c2) {
        int steps = Math.max(24, (int) (radius * 6.5 * (to - from)));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            double a = (from + (to - from) * t) * Math.PI * 2 - Math.PI / 2;
            int px = (int) Math.round(cx + Math.cos(a) * radius), py = (int) Math.round(cy + Math.sin(a) * radius);
            rect(px - thickness / 2, py - thickness / 2, thickness, thickness, lerpColor(c1, c2, (float) t));
        }
    }

    // left to right gradient as ONE piece (vanillas top to bottom one turned a quarter)
    // strips would be up to 28 pieces and every card band has 2, each piece costs time to place
    private static final org.joml.Matrix3x2f QUARTER = new org.joml.Matrix3x2f(0, -1, 1, 0, 0, 0);

    public void gradH(int x, int y, int w, int h, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        if (c1 == c2) {
            rect(x, y, w, h, c1);
            return;
        }
        var p = g.pose();
        p.pushMatrix();
        p.translate(x, y + h);
        p.mul(QUARTER);
        g.fillGradient(0, 0, h, w, opacity >= 1f ? c1 : alpha(c1, opacity), opacity >= 1f ? c2 : alpha(c2, opacity));
        p.popMatrix();
        piece();
        elements++;
    }

    public void roundGradH(int x, int y, int w, int h, int r, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        r = 0;
        r = Math.min(r, Math.min(w, h) / 2);
        gradH(x, y + r, w, h - 2 * r, c1, c2);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            float t0 = (float) inset / Math.max(1, w), t1 = 1 - t0;
            int a = lerpColor(c1, c2, t0), b = lerpColor(c1, c2, t1);
            gradH(x + inset, y + i, w - 2 * inset, 1, a, b);
            gradH(x + inset, y + h - 1 - i, w - 2 * inset, 1, a, b);
        }
    }

    public void gradV(int x, int y, int w, int h, int top, int bottom) {
        if (w > 0 && h > 0) {
            g.fillGradient(x, y, x + w, y + h, opacity >= 1f ? top : alpha(top, opacity), opacity >= 1f ? bottom : alpha(bottom, opacity));
            piece();
            elements++;
        }
    }

    public static int lerpColor(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int aa = a >>> 24, ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int ba = b >>> 24, br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    public static int alpha(int c, float a) {
        return ((int) (((c >>> 24) & 255) * Math.max(0, Math.min(1, a))) << 24) | (c & 0xFFFFFF);
    }

    // --- text ---

    public void text(String s, int x, int y, int c) {
        s = avoid(fit(s, x, 1f), x, y, 1f);
        if (s != null) {
            g.text(f(), s, outX, y, opacity >= 1f ? c : alpha(c, opacity), false);
            piece();
        }
    }

    public void textShadow(String s, int x, int y, int c) {
        s = avoid(fit(s, x, 1f), x, y, 1f);
        if (s != null) {
            g.text(f(), s, outX, y, opacity >= 1f ? c : alpha(c, opacity), true);
            piece();
        }
    }

    // text drawn this frame, later text gets trimmed so 2 strings never overlap
    private final int[] boxes = new int[4 * 700];
    private int boxN;

    // forget drawn text. each hud widget has its own coords so it starts clean
    public void resetText() {
        boxN = 0;
    }
    private int outX;

    private String avoid(String s, float x, float y, float scale) {
        outX = (int) x;
        if (s == null || s.isEmpty()) return s;
        float w = width(s) * scale, h = 9 * scale;
        float right = x + w;
        for (int i = 0; i < boxN; i += 4) {
            int bx = boxes[i], by = boxes[i + 1], bw = boxes[i + 2], bh = boxes[i + 3];
            if (y + h - 2 <= by || by + bh - 2 <= y) continue;
            if (x >= bx + bw || right <= bx) continue;
            if (x == bx && y == by) continue;
            if (x < bx) {
                int room = (int) ((bx - 3 - x) / scale);
                if (room < 12) return null;
                s = ellipsize(s, room);
                right = x + width(s) * scale;
            } else if (rightAnchored) {
                int nx = bx + bw + 3;
                int room = (int) ((rightEdge - nx) / scale);
                if (room < 12) return null;
                s = ellipsize(s, room);
                x = rightEdge - width(s) * scale;
                right = rightEdge;
                rightAnchored = false;
                return avoid(s, x, y, scale);
            } else {
                return null;
            }
        }
        if (boxN + 4 <= boxes.length) {
            boxes[boxN] = (int) x;
            boxes[boxN + 1] = (int) y;
            boxes[boxN + 2] = (int) Math.ceil(right - x);
            boxes[boxN + 3] = (int) Math.ceil(h);
            boxN += 4;
        }
        return s;
    }

    // right edge text can reach (the panel being drawn), crossing it = cut with "…"
    public int textLimit = Integer.MAX_VALUE;

    private String fit(String s, float x, float scale) {
        int right = Math.min(textLimit, clipX1);
        if (right == Integer.MAX_VALUE || s == null) return s;
        int room = (int) ((right - 2 - x) / scale);
        if (room <= 0 || width(s) <= room) return s;
        return ellipsize(s, room);
    }

    private boolean rightAnchored;
    private float rightEdge;

    public void textRight(String s, int right, int y, int c) {
        rightAnchored = true;
        rightEdge = right;
        text(s, right - width(s), y, c);
        rightAnchored = false;
    }

    public void textCenter(String s, int cx, int y, int c) {
        text(s, cx - width(s) / 2, y, c);
    }

    public void textScaled(String s, float x, float y, float scale, int c, boolean shadow) {
        var p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        p.scale(scale, scale);
        String f = avoid(fit(s, x, scale), x, y, scale);
        if (f != null) {
            g.text(f(), f, 0, 0, c, shadow);
            piece();
        }
        p.popMatrix();
    }

    // the same strings get measured over and over every frame (fit, avoid, ellipsize, the cards), so cache widths
    // same font = same width. cleared when the font changes, when it gets big, and every 2 sec bc a resource pack
    // reload can swap glyphs
    private final java.util.HashMap<String, Integer> widths = new java.util.HashMap<>();
    private Font widthsFont;
    private long widthsAt;

    public int width(String s) {
        Font font = f();
        long now = System.currentTimeMillis();
        if (font != widthsFont || widths.size() > 8192 || now - widthsAt > 2000) {
            widths.clear();
            widthsFont = font;
            widthsAt = now;
        }
        Integer w = widths.get(s);
        if (w == null) {
            w = font.width(s);
            widths.put(s, w);
        }
        return w;
    }

    public String ellipsize(String s, int maxW) {
        if (width(s) <= maxW) return s;
        String e = "…";
        while (!s.isEmpty() && width(s + e) > maxW) s = s.substring(0, s.length() - 1);
        return s + e;
    }

    // word wrap to maxW, returns height
    public int wrap(String s, int x, int y, int maxW, int c) {
        int line = 0;
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String test = cur.isEmpty() ? word : cur + " " + word;
            if (width(test) > maxW && !cur.isEmpty()) {
                text(cur.toString(), x, y + line * 10, c);
                line++;
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(test);
            }
        }
        if (!cur.isEmpty()) {
            text(cur.toString(), x, y + line * 10, c);
            line++;
        }
        return line * 10;
    }

    // --- interaction ---

    public boolean hover(int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h && mx >= clipX0 && my >= clipY0 && mx < clipX1 && my < clipY1;
    }

    // only the visible part takes clicks, a row scrolled under the tabs must not answer for them
    private void hit(int x, int y, int w, int h, Runnable left, Runnable right, Drag drag) {
        int x0 = Math.max(x, clipX0), y0 = Math.max(y, clipY0), x1 = Math.min(x + w, clipX1), y1 = Math.min(y + h, clipY1);
        if (x1 > x0 && y1 > y0) hits.add(new Hit(x0, y0, x1 - x0, y1 - y0, left, right, drag));
    }

    public void click(int x, int y, int w, int h, Runnable r) {
        hit(x, y, w, h, r, null, null);
    }

    public void clickRight(int x, int y, int w, int h, Runnable left, Runnable right) {
        hit(x, y, w, h, left, right, null);
    }

    public void drag(int x, int y, int w, int h, Drag d) {
        hit(x, y, w, h, null, null, d);
    }

    // eases a value to target (0..1) per id
    public float ease(String id, float target, float speed) {
        float v = anim.getOrDefault(id, target);
        v += (target - v) * Math.min(1, frameDt * speed);
        if (Math.abs(target - v) < 0.002f) v = target;
        anim.put(id, v);
        return v;
    }

    public float hoverAnim(String id, int x, int y, int w, int h) {
        return ease("h:" + id, hover(x, y, w, h) ? 1 : 0, 14);
    }

    // --- widgets ---

    public static final int PRIMARY = 0, GHOST = 1, SOFT = 2, DANGER = 3;

    public boolean button(String id, int x, int y, int w, int h, String label, int style, Runnable onClick) {
        float hv = hoverAnim(id, x, y, w, h);
        switch (style) {
            case PRIMARY -> {
                if (hv > 0) {
                    round(x - 2, y - 1, w + 4, h + 3, 7, alpha(CYAN, 0.12f * hv));
                    round(x - 1, y - 1, w + 2, h + 2, 6, alpha(VIOLET, 0.25f * hv));
                }
                rect(x - 1, y - 1, w + 2, h + 2, 0xFF000000);
                rect(x, y, w, h, lerpColor(BTN_BORDER, BTN_BORDER_HOVER, hv));
                rect(x + 1, y + 1, w - 2, h - 2, lerpColor(BTN_FILL, BTN_FILL_HOVER, hv));
                gradV(x + 1, y + 1, w - 2, h / 2, 0x30FFFFFF, 0x00FFFFFF);
            }
            case DANGER -> {
                round(x, y, w, h, 5, lerpColor(0xFF3A1E2A, 0xFF5A2433, hv));
                rect(x + 3, y, w - 6, 1, 0x30FF8A8A);
            }
            case SOFT -> {
                rect(x - 1, y - 1, w + 2, h + 2, 0xFF000000);
                rect(x, y, w, h, lerpColor(SOFT_BORDER, SOFT_BORDER_HOVER, hv));
                rect(x + 1, y + 1, w - 2, h - 2, lerpColor(SOFT_FILL, SOFT_FILL_HOVER, hv));
                gradV(x + 1, y + 1, w - 2, h / 2, 0x22FFFFFF, 0x00FFFFFF);
            }
            default -> round(x, y, w, h, 5, alpha(GHOST_FILL, 0.25f + 0.75f * hv));
        }
        int tc = style == DANGER ? RED : style == GHOST ? lerpColor(MUTED, TEXT, hv) : TEXT;
        textCenter(label, x + w / 2, y + (h - 8) / 2, tc);
        if (onClick != null) click(x, y, w, h, onClick);
        return hover(x, y, w, h);
    }

    // no enchant shimmer on hud icons, a shimmering item redraws every frame (5% of one with a full hud)
    public boolean plainIcons;
    private final java.util.Map<ItemStack, ItemStack> plain = new java.util.WeakHashMap<>();

    private ItemStack plain(ItemStack st) {
        if (!plainIcons || !st.hasFoil()) return st;
        ItemStack out = plain.get(st);
        if (out == null) {
            out = st.copy();
            out.set(net.minecraft.core.component.DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
            if (plain.size() > 512) plain.clear();
            plain.put(st, out);
        }
        return out;
    }

    public void icon(ItemStack st, int x, int y, float scale) {
        if (st == null || st.isEmpty()) return;
        st = plain(st);
        var p = g.pose();
        p.pushMatrix();
        p.translate(x, y);
        p.scale(scale, scale);
        g.item(st, 0, 0);
        piece();
        p.popMatrix();
    }

    public void icon(String modernId, int x, int y, float scale) {
        icon(LegacyItems.stack(modernId), x, y, scale);
    }

    // progress bar: sunk track, gradient fill, glossy top, slow shine
    public void bar(int x, int y, int w, int h, double pct, int c1, int c2) {
        round(x, y, w, h, h / 2, 0xFF0E0C09);
        rect(x + h / 2, y, w - h, 1, 0x40000000);
        int fw = (int) Math.round(w * Math.max(0, Math.min(1, pct)));
        if (fw <= 0) return;
        int fwd = Math.max(fw, h);
        roundGradH(x, y, fwd, h, h / 2, c1, c2);
        if (h >= 4) rect(x + h / 2, y, Math.max(0, fwd - h), Math.max(1, h / 3), 0x38FFFFFF);
        if (fwd > 16) {
            int sx = (int) ((now / 12) % (fwd + 40)) - 20;
            for (int i = 0; i < 12; i++) {
                int px = x + sx + i;
                if (px > x + h / 2 && px < x + fwd - h / 2) rect(px, y, 1, h, alpha(0xFFFFFFFF, 0.22f * (1 - Math.abs(i - 6) / 6f)));
            }
        }
    }

    public int chip(int x, int y, String label, int color) {
        int w = width(label) + 10;
        round(x, y, w, 12, 6, alpha(color, 0.16f));
        rect(x + 4, y, w - 8, 1, alpha(color, 0.35f));
        text(label, x + 5, y + 2, color);
        return w;
    }

    public void checkbox(int x, int y, boolean on, float anim) {
        if (on) {
            round(x - 1, y - 1, 14, 14, 5, alpha(GREEN, 0.25f));
            roundGradH(x, y, 12, 12, 4, 0xFF5BF0A0, 0xFF2FCB7A);
            text("✔", x + 2, y + 2, 0xFF07210F);
        } else {
            round(x, y, 12, 12, 4, 0xFF5A5246);
            round(x + 1, y + 1, 10, 10, 3, INPUT_BG);
        }
    }

    public void sectionTitle(String s, int x, int y) {
        roundGradH(x, y + 1, 3, 8, 1, VIOLET, CYAN);
        textShadow(s, x + 7, y, TEXT);
        gradH(x + 7, y + 11, Math.min(200, width(s) + 60), 1, alpha(VIOLET, 0.7f), 0x00BA7517);
    }

    public void lineChart(int x, int y, int w, int h, double[] v, int color) {
        round(x, y, w, h, 6, INPUT_BG);
        if (v.length < 2) {
            textCenter("Not enough data yet", x + w / 2, y + h / 2 - 4, DIM);
            return;
        }
        double max = 0, min = Double.MAX_VALUE;
        for (double d : v) {
            max = Math.max(max, d);
            min = Math.min(min, d);
        }
        if (max - min < 1e-9) max = min + 1;
        for (int i = 1; i < 4; i++) rect(x + 4, y + h * i / 4, w - 8, 1, 0x10FFFFFF);
        int prevX = -1, prevY = -1;
        for (int px = 0; px < w - 8; px++) {
            double t = (double) px / (w - 9) * (v.length - 1);
            int i0 = (int) Math.floor(t);
            int i1 = Math.min(v.length - 1, i0 + 1);
            double val = v[i0] + (v[i1] - v[i0]) * (t - i0);
            int py = y + h - 4 - (int) ((val - min) / (max - min) * (h - 10));
            int cx = x + 4 + px;
            gradV(cx, py, 1, y + h - 4 - py, alpha(color, 0.45f), alpha(color, 0.02f));
            if (prevX >= 0) {
                int a = Math.min(prevY, py), b = Math.max(prevY, py);
                rect(cx, a, 1, Math.max(2, b - a + 1), color);
            }
            prevX = cx;
            prevY = py;
        }
    }

    public void tooltip(String... lines) {
        List<Component> l = new ArrayList<>();
        for (String s : lines) l.add(Component.literal(s));
        tooltip.add(l);
    }

    // draws the last tooltip asked for, long lines wrap so it never goes off screen
    public void flushTooltip() {
        if (tooltip.isEmpty()) return;
        int max = Math.max(120, Math.min(300, g.guiWidth() * 45 / 100));
        List<net.minecraft.util.FormattedCharSequence> out = new ArrayList<>();
        for (Component c : tooltip.get(tooltip.size() - 1)) {
            if (c.getString().isEmpty()) out.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            else out.addAll(f().split(c, max));
        }
        g.setTooltipForNextFrame(f(), out, realMx, realMy);
    }
}
