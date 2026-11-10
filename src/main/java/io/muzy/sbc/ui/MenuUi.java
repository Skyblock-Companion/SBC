// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui;

import com.mojang.blaze3d.platform.InputConstants;
import io.muzy.sbc.core.Config;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.map.MapRender;
import io.muzy.sbc.ui.screens.SbcWidgets;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import io.muzy.sbc.ui.layout.BaseUIComponent;
import io.muzy.sbc.ui.layout.FlowLayout;
import io.muzy.sbc.ui.layout.UIContainers;
import io.muzy.sbc.ui.layout.CursorStyle;
import io.muzy.sbc.ui.layout.UIGraphics;
import io.muzy.sbc.ui.layout.Positioning;
import io.muzy.sbc.ui.layout.Sizing;
import io.muzy.sbc.ui.layout.UIComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

// what a K menu page is written against. each call adds one thing at a spot
// a page runs a few times a sec and describes everything, nothing gets built till it differs from last time
public final class MenuUi {
    public interface Host {
        // describe the page again next frame
        void changed();

        void goTo(int tab);

        void showTip(Supplier<String[]> lines);

        // the items real tooltip this frame, page lines after it
        default void showItemTip(Supplier<net.minecraft.world.item.ItemStack> item, Supplier<String[]> extra) {
            showTip(extra);
        }

        // not over the scrolled away part
        boolean mouseInView(int mouseX, int mouseY);
    }

    // page reads/writes value, the component just shows it
    public static final class Field {
        public String value = "";
        public final String placeholder;
        public final boolean masked;
        public Consumer<String> onEnter;

        public Field(String placeholder, boolean masked) {
            this.placeholder = placeholder;
            this.masked = masked;
        }
    }

    // leaf drawn with the immediate kit: charts, the hovered map marker card
    public interface Painter {
        void paint(Ui ui, int x, int y, int w, int h);
    }

    private record Op(int x, int y, Supplier<UIComponent> make) {}

    private final Host host;
    private final Map<String, UIComponent> kept;
    private final int rightLimit;
    private final List<Op> ops = new ArrayList<>();
    private long sig = 1125899906842597L;

    // text already placed. later text gets trimmed so 2 strings never print over each other (same as Ui)
    private final List<int[]> placed = new ArrayList<>();
    private int outX;
    private boolean rightAnchored;
    private float rightEdge;

    // rightLimit = how far text can go (visible page width)
    public MenuUi(Host host, Map<String, UIComponent> kept, int rightLimit) {
        this.host = host;
        this.kept = kept;
        this.rightLimit = rightLimit;
    }

    // --- result ---

    // same value = nothing on the page has to change
    public long signature() {
        return sig;
    }

    public int size() {
        return ops.size();
    }

    // one flow, every child at its own spot, in draw order (later = on top)
    public FlowLayout materialize(int width, int height) {
        FlowLayout page = UIContainers.verticalFlow(Sizing.fixed(Math.max(1, width)), Sizing.fixed(Math.max(1, height)));
        for (Op op : ops) {
            UIComponent c = op.make().get();
            c.positioning(Positioning.absolute(op.x(), op.y()));
            page.child(c);
        }
        return page;
    }

    private void mix(long v) {
        sig = 31 * sig + v;
    }

    private void mix(String s) {
        mix(s == null ? 0 : s.hashCode());
    }

    private void add(int x, int y, Supplier<UIComponent> make) {
        mix(x);
        mix(y);
        ops.add(new Op(x, y, make));
    }

    private Runnable afterClick(Runnable r) {
        return () -> {
            r.run();
            Config.save();
            Store.markDirty();
            host.changed();
        };
    }

    public void goTo(int tab) {
        host.goTo(tab);
    }

    // --- text measuring ---

    private static Font font() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : mc.font;
    }

    public int width(String s) {
        Font f = font();
        return f != null ? f.width(s) : GameState.strip(s).length() * 6;
    }

    public String ellipsize(String s, int maxW) {
        if (width(s) <= maxW) return s;
        String e = "…";
        while (!s.isEmpty() && width(s + e) > maxW) s = s.substring(0, s.length() - 1);
        return s + e;
    }

    private String fit(String s, float x, float scale) {
        if (s == null) return null;
        int room = (int) ((rightLimit - 2 - x) / scale);
        if (room <= 0 || width(s) <= room) return s;
        return ellipsize(s, room);
    }

    private String avoid(String s, float x, float y, float scale) {
        outX = (int) x;
        if (s == null || s.isEmpty()) return s;
        float w = width(s) * scale, h = 9 * scale;
        float right = x + w;
        for (int[] b : placed) {
            if (y + h - 2 <= b[1] || b[1] + b[3] - 2 <= y) continue;
            if (x >= b[0] + b[2] || right <= b[0]) continue;
            if (x == b[0] && y == b[1]) continue;
            if (x < b[0]) {
                int room = (int) ((b[0] - 3 - x) / scale);
                if (room < 12) return null;
                s = ellipsize(s, room);
                right = x + width(s) * scale;
            } else if (rightAnchored) {
                int nx = b[0] + b[2] + 3;
                int room = (int) ((rightEdge - nx) / scale);
                if (room < 12) return null;
                s = ellipsize(s, room);
                x = rightEdge - width(s) * scale;
                rightAnchored = false;
                return avoid(s, x, y, scale);
            } else {
                return null;
            }
        }
        placed.add(new int[]{(int) x, (int) y, (int) Math.ceil(right - x), (int) Math.ceil(h)});
        return s;
    }

    // --- text ---

    private void putText(String s, float x, float y, float scale, int c, boolean shadow) {
        String f = avoid(fit(s, x, scale), x, y, scale);
        if (f == null || f.isEmpty()) return;
        int px = outX, py = (int) y;
        mix(f);
        mix(c);
        mix(Float.floatToIntBits(scale));
        int w = Math.max(1, Math.round(width(f) * scale)), h = Math.max(1, Math.round(9 * scale));
        add(px, py, () -> new Txt(f, w, h, scale, c, shadow));
    }

    public void text(String s, int x, int y, int c) {
        putText(s, x, y, 1f, c, true);
    }

    public void textShadow(String s, int x, int y, int c) {
        putText(s, x, y, 1f, c, true);
    }

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
        putText(s, x, y, scale, c, shadow);
    }

    // returns height
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

    // --- shapes ---

    public void rect(int x, int y, int w, int h, int c) {
        if (w <= 0 || h <= 0) return;
        mix(w);
        mix(h);
        mix(c);
        add(x, y, () -> new Box(w, h, c, c));
    }

    // corners are sharp everywhere in the menu, r gets ignored
    public void round(int x, int y, int w, int h, int r, int c) {
        rect(x, y, w, h, c);
    }

    public void hoverRound(int x, int y, int w, int h, int r, int c, int hover) {
        if (w <= 0 || h <= 0) return;
        mix(w);
        mix(h);
        mix(c);
        mix(hover);
        add(x, y, () -> new Box(w, h, c, hover));
    }

    public void panel(int x, int y, int w, int h, int r, int c) {
        if (w <= 0 || h <= 0) return;
        int edge = 0x50FFFFFF;
        mix(w);
        mix(h);
        mix(c);
        mix(edge);
        add(x, y, () -> new Framed(w, h, c, edge));
    }

    public void gradV(int x, int y, int w, int h, int top, int bottom) {
        grad(x, y, w, h, top, bottom, false);
    }

    public void gradH(int x, int y, int w, int h, int left, int right) {
        grad(x, y, w, h, left, right, true);
    }

    public void roundGradH(int x, int y, int w, int h, int r, int c1, int c2) {
        grad(x, y, w, h, c1, c2, true);
    }

    private void grad(int x, int y, int w, int h, int c1, int c2, boolean horizontal) {
        if (w <= 0 || h <= 0) return;
        mix(w);
        mix(h);
        mix(c1);
        mix(c2);
        add(x, y, () -> new Grad(w, h, c1, c2, horizontal));
    }

    // light to clear gradient over the top + 1px highlight
    public void sheen(int x, int y, int w, int h, int r) {
        int sh = Math.min(h / 2, 40);
        gradV(x + r / 2, y + 1, w - r, sh, 0x12FFFFFF, 0x00FFFFFF);
        rect(x + r, y, w - 2 * r, 1, 0x30FFFFFF);
    }

    // --- classic kit ---

    // where card content starts, under the title band
    public static final int TOP = 18;
    public static final int GAP = 6;
    public static final int PURPLE = 0xFFC77DFF;

    // a color that means something by itself
    public static int tone(int classic) {
        return classic;
    }

    // "62%", or "4.2%" when a whole number would hide progress
    public static String pct(double fraction) {
        double p = Math.max(0, fraction) * 100;
        if (p <= 0) return "0%";
        if (p >= 99.95) return Math.round(p) + "%";
        return p >= 10 ? String.format(java.util.Locale.ROOT, "%.0f%%", p) : String.format(java.util.Locale.ROOT, "%.1f%%", p);
    }

    // "12,345" if it fits, short form above
    public static String num(double v) {
        return Math.abs(v) < 100_000 ? String.format(java.util.Locale.ROOT, "%,d", Math.round(v)) : io.muzy.sbc.core.Prices.coins(v);
    }

    // "1.26M/1.30M  97%": 2 numbers colored, dim slash, then the share
    public static String fraction(double now, double of, char code) {
        return Modern.fraction(num(now), num(of), code, code) + "  §b" + pct(of <= 0 ? 0 : now / of);
    }

    // glass face + band in the card color when it has a title. returns where content starts
    public int card(int x, int y, int w, int h, String title, String icon, String right, int accent) {
        if (w <= 0 || h <= 0) return TOP;
        int a = accent | 0xFF000000;
        String fitted = title == null ? null : ellipsize(title, w - (icon == null ? 8 : 18) - (right == null || right.isEmpty() ? 0 : width(right) + 8));
        mix(w);
        mix(h);
        mix(a);
        mix(fitted);
        mix(right);
        mix(icon);
        add(x, y, () -> new CardFace(w, h, fitted, icon, right, a));
        return TOP;
    }

    // no title = colored edge on the left says whose it is
    public void edgeCard(int x, int y, int w, int h, int accent) {
        card(x, y, w, h, null, null, null, accent);
        gradV(x, y + 1, 2, h - 2, accent | 0xFF000000, Ui.alpha(accent | 0xFF000000, 0.35f));
    }

    // card color name + a line fading out after it
    public void section(String title, int x, int y, int w, int accent) {
        int c = accent | 0xFF000000, tw = width(title);
        textShadow(title, x, y, Modern.lighten(c, 0.3f));
        if (w > tw + 6) gradH(x + tw + 4, y + 4, w - tw - 4, 1, Ui.alpha(c, 0.6f), Ui.alpha(c, 0.02f));
    }

    public void hair(int x, int y, int w) {
        rect(x, y, w, 1, 0x1CFFFFFF);
    }

    public void row(String label, String value, int x, int y, int w, int labelColor, int valueColor) {
        if (value == null || value.isEmpty()) {
            text(ellipsize(label, w), x, y, labelColor);
            return;
        }
        int vw = width(value);
        text(ellipsize(label, Math.max(8, w - vw - 6)), x, y, labelColor);
        text(value, x + w - vw, y, valueColor);
    }

    public void row(String label, String value, int x, int y, int w, int valueColor) {
        row(label, value, x, y, w, Ui.MUTED, valueColor);
    }

    // dark groove + fill with a lit top
    public void meter(int x, int y, int w, int h, double pct, int color) {
        bar(x, y, w, h, pct, Modern.darken(color | 0xFF000000, 0.22f), color | 0xFF000000);
    }

    // returns height
    public int progress(String label, String value, int x, int y, int w, double pct, int color, int valueColor) {
        row(label, value, x, y, w, valueColor);
        meter(x, y + 10, w, 2, pct, color);
        return 14;
    }

    // small label, the big number, optional line + optional meter at the bottom (pct < 0 = none). 40+ tall prints
    // the number bigger
    public void tile(int x, int y, int w, int h, String label, String value, String sub, int valueColor, double pct, int barColor) {
        gradV(x, y, w, h, 0x1CFFFFFF, 0x0AFFFFFF);
        rect(x, y, w, 1, 0x16FFFFFF);
        boolean big = h >= 40;
        text(ellipsize(label, w - 8), x + 4, y + 3, Ui.MUTED);
        float scale = big ? 1.5f : 1f;
        String v = ellipsize(value, (int) ((w - 8) / scale));
        textScaled(v, x + 4, y + 13, scale, valueColor, true);
        if (sub != null && !sub.isEmpty()) text(ellipsize(sub, w - 8), x + 4, y + (big ? 27 : 23), Ui.DIM);
        if (pct >= 0) meter(x + 1, y + h - 3, w - 2, 2, pct, barColor);
    }

    // --- widgets ---

    public void button(String id, int x, int y, int w, int h, String label, int style, Runnable onClick) {
        mix(w);
        mix(h);
        mix(label);
        mix(style);
        int[] p = switch (style) {
            case Ui.PRIMARY -> new int[]{Ui.BTN_FILL, Ui.BTN_FILL_HOVER, Ui.BTN_BORDER, Ui.BTN_BORDER_HOVER, Ui.TEXT};
            case Ui.DANGER -> new int[]{0xFF3A1E2A, 0xFF5A2433, 0xFF3A1E2A, 0xFF5A2433, Ui.RED};
            case Ui.SOFT -> new int[]{Ui.SOFT_FILL, Ui.SOFT_FILL_HOVER, Ui.SOFT_BORDER, Ui.SOFT_BORDER_HOVER, Ui.TEXT};
            default -> new int[]{Ui.alpha(Ui.GHOST_FILL, 0.25f), Ui.GHOST_FILL, Ui.alpha(Ui.GHOST_FILL, 0.25f), Ui.GHOST_FILL, Ui.MUTED};
        };
        Runnable click = onClick == null ? null : afterClick(onClick);
        add(x, y, () -> {
            SbcWidgets.Button b = SbcWidgets.button(net.minecraft.network.chat.Component.literal(label), bt -> {
                if (click != null) click.run();
            });
            b.palette(p[0], p[1], p[2], p[3], p[4]);
            b.sizing(Sizing.fixed(Math.max(1, w)), Sizing.fixed(Math.max(1, h)));
            return b;
        });
    }

    public void click(int x, int y, int w, int h, Runnable r) {
        mix(w);
        mix(h);
        Runnable click = afterClick(r);
        add(x, y, () -> new Zone(w, h, click, null));
    }

    // tooltip zone only, clicks go to whatever is under it
    public void tip(int x, int y, int w, int h, String... lines) {
        if (w <= 0 || h <= 0) return;
        mix(w);
        mix(h);
        for (String l : lines) mix(l);
        add(x, y, () -> new Zone(w, h, null, () -> lines));
    }

    // the items own tooltip like in a chest + extra lines after
    public void tipItem(int x, int y, int w, int h, Supplier<net.minecraft.world.item.ItemStack> item, Supplier<String[]> extra) {
        if (w <= 0 || h <= 0) return;
        mix(w);
        mix(h);
        add(x, y, () -> new Zone(w, h, null, extra, item));
    }

    // same but the lines only get built while the mouse is on it
    public void tip(int x, int y, int w, int h, Supplier<String[]> lines) {
        if (w <= 0 || h <= 0) return;
        mix(w);
        mix(h);
        add(x, y, () -> new Zone(w, h, null, lines));
    }

    public int chipWidth(String label) {
        return width(label) + (6);
    }

    public int chip(int x, int y, String label, int color) {
        int w = chipWidth(label);
        int c = color | 0xFF000000;
        rect(x, y, w, 11, Ui.alpha(c, 0.22f));
        rect(x, y + 10, w, 1, Ui.alpha(c, 0.8f));
        textShadow(label, x + 3, y + 1, Modern.lighten(c, 0.35f));
        return w;
    }

    public void checkbox(int x, int y, boolean on, float unused) {
        if (on) {
            rect(x - 1, y - 1, 14, 14, Ui.alpha(Ui.GREEN, 0.25f));
            roundGradH(x, y, 12, 12, 4, 0xFF5BF0A0, 0xFF2FCB7A);
            text("✔", x + 2, y + 2, 0xFF07210F);
        } else {
            rect(x, y, 12, 12, Ui.SOFT_BORDER);
            rect(x + 1, y + 1, 10, 10, Ui.INPUT_BG);
        }
    }

    public void sectionTitle(String s, int x, int y) {
        section(s, x, y, Math.max(120, rightLimit - x - 8), Modern.AQUA);
        return;
    }

    // dark track, gradient fill with a light top edge
    public void bar(int x, int y, int w, int h, double pct, int c1, int c2) {
        if (w <= 0 || h <= 0) return;
        double p = Math.max(0, Math.min(1, pct));
        mix(w);
        mix(h);
        mix(Math.round(p * 1000));
        mix(c1);
        mix(c2);
        add(x, y, () -> new Bar(w, h, p, c1, c2));
    }

    public void icon(String modernId, int x, int y, float scale) {
        mix(modernId);
        mix(Float.floatToIntBits(scale));
        add(x, y, () -> new Icon(() -> LegacyItems.stack(modernId), scale));
    }

    public void icon(ItemStack st, int x, int y, float scale) {
        if (st == null || st.isEmpty()) return;
        mix(st.getItem().hashCode());
        mix(st.getCount());
        mix(Float.floatToIntBits(scale));
        add(x, y, () -> new Icon(() -> st, scale));
    }

    public void lineChart(int x, int y, int w, int h, double[] v, int color) {
        mix(w);
        mix(h);
        mix(color);
        for (double d : v) mix(Double.doubleToLongBits(d));
        add(x, y, () -> new Painted(w, h, (u, px, py, pw, ph) -> u.lineChart(px, py, pw, ph, v, color)));
    }

    // drawn by its own code every frame, for stuff that moves or follows the mouse
    public void paint(int x, int y, int w, int h, Painter painter) {
        mix(w);
        mix(h);
        add(x, y, () -> new Painted(w, h, painter));
    }

    // npc face, or a colored dot when we dont know the skin
    public void head(MapRender.Marker m, int cx, int cy, int size, int fallback) {
        mix(m.name());
        mix(size);
        add(cx - size / 2, cy - size / 2, () -> new Painted(size, size, (u, px, py, pw, ph) -> {
            if (!MapRender.drawHead(u, m, px + pw / 2, py + ph / 2, pw)) u.round(px + pw / 2 - 4, py + ph / 2 - 4, 8, 8, 4, fallback);
        }));
    }

    // same object across page swaps so focus / a drag in progress survives
    @SuppressWarnings("unchecked")
    public <T extends UIComponent> T keep(String key, int x, int y, int w, int h, Supplier<T> make) {
        T c = (T) kept.computeIfAbsent(key, k -> make.get());
        mix(key);
        mix(w);
        mix(h);
        add(x, y, () -> {
            c.sizing(Sizing.fixed(Math.max(1, w)), Sizing.fixed(Math.max(1, h)));
            return c;
        });
        return c;
    }

    public void input(Field f, int x, int y, int w) {
        keep("input:" + System.identityHashCode(f), x, y, w, 18, () -> {
            SbcWidgets.Input in = SbcWidgets.input(Sizing.fixed(w), () -> f.value, v -> f.value = v);
            in.setHint(net.minecraft.network.chat.Component.literal(f.placeholder));
            in.masked(f.masked);
            in.maxLength(120);
            in.onChanged(v -> host.changed());
            in.onEnter(() -> {
                if (f.onEnter != null) f.onEnter.accept(f.value);
                Config.save();
                host.changed();
            });
            return in;
        });
    }

    // label over a 0..1 slider
    public void slider(String id, int x, int y, int w, double value, String label, Consumer<Double> set) {
        text(label, x, y, Ui.MUTED);
        SbcWidgets.Slider s = keep("slider:" + id, x, y + 12, w, 9, () -> {
            SbcWidgets.Slider made = SbcWidgets.slider(Sizing.fixed(w), 0, 1);
            made.decimalPlaces(3).showValue(false);
            made.onReleased(Config::save);
            return made;
        });
        s.setFromDiscreteValue(value);
        s.replaceListeners(v -> {
            set.accept(v);
            host.changed();
        });
    }

    // --- components ---

    private abstract static class Leaf extends BaseUIComponent {
        Leaf(int w, int h) {
            this.horizontalSizing(Sizing.fixed(w));
            this.verticalSizing(Sizing.fixed(h));
        }
    }

    private static final class Txt extends Leaf {
        private final String text;
        private final float scale;
        private final int color;
        private final boolean shadow;

        Txt(String text, int w, int h, float scale, int color, boolean shadow) {
            super(w, h);
            this.text = text;
            this.scale = scale;
            this.color = color;
            this.shadow = shadow;
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            Font f = font();
            if (f == null) return;
            if (scale == 1f) {
                g.text(f, text, x, y, color, shadow);
                return;
            }
            var p = g.pose();
            p.pushMatrix();
            p.translate(x, y);
            p.scale(scale, scale);
            g.text(f, text, 0, 0, color, shadow);
            p.popMatrix();
        }
    }

    // slate glass, soft corners, lit top, title band in the card color
    private static final class CardFace extends Leaf {
        private static final int FACE_TOP = 0xEC1C2735, FACE_BOTTOM = 0xEC121A25;
        private final String title, icon, right;
        private final int accent;
        private ItemStack stack;

        CardFace(int w, int h, String title, String icon, String right, int accent) {
            super(w, h);
            this.title = title;
            this.icon = icon;
            this.right = right;
            this.accent = accent;
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            io.muzy.sbc.ui.screens.SbcGradient.rect(g, x + 1, y, width - 2, height, FACE_TOP, FACE_TOP, FACE_BOTTOM, FACE_BOTTOM);
            io.muzy.sbc.ui.screens.SbcGradient.rect(g, x, y + 1, 1, height - 2, FACE_TOP, FACE_TOP, FACE_BOTTOM, FACE_BOTTOM);
            io.muzy.sbc.ui.screens.SbcGradient.rect(g, x + width - 1, y + 1, 1, height - 2, FACE_TOP, FACE_TOP, FACE_BOTTOM, FACE_BOTTOM);
            g.fill(x + 1, y, x + width - 1, y + 1, 0x2CFFFFFF);
            g.fill(x, y + 1, x + 1, y + height - 1, 0x10FFFFFF);
            g.fill(x + width - 1, y + 1, x + width, y + height - 1, 0x10FFFFFF);
            g.fill(x + 1, y + height - 1, x + width - 1, y + height, 0x60000000);
            if (title == null) return;
            int band = Modern.BAND;
            io.muzy.sbc.ui.screens.SbcGradient.rect(g, x + 1, y + 1, width - 2, band - 2, Ui.alpha(accent, 0.36f), Ui.alpha(accent, 0.03f), Ui.alpha(accent, 0.03f), Ui.alpha(accent, 0.36f));
            io.muzy.sbc.ui.screens.SbcGradient.rect(g, x + 1, y + band - 1, width - 2, 1, Ui.alpha(accent, 0.95f), Ui.alpha(accent, 0.08f), Ui.alpha(accent, 0.08f), Ui.alpha(accent, 0.95f));
            Font f = font();
            if (f == null) return;
            int tx = x + 4;
            if (icon != null) {
                if (stack == null) stack = LegacyItems.stack(icon);
                var p = g.pose();
                p.pushMatrix();
                p.translate(x + 2, y + 2);
                p.scale(Modern.ICON_SCALE, Modern.ICON_SCALE);
                g.item(stack, 0, 0);
                p.popMatrix();
                tx = x + 14;
            }
            g.text(f, title, tx, y + 3, Modern.lighten(accent, 0.45f), true);
            if (right != null && !right.isEmpty()) g.text(f, right, x + width - 4 - f.width(right), y + 3, Modern.INK, true);
        }
    }

    // another color on hover
    private final class Box extends Leaf {
        private final int color, hover;

        Box(int w, int h, int color, int hover) {
            super(w, h);
            this.color = color;
            this.hover = hover;
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            boolean over = hover != color && isInBoundingBox(mouseX, mouseY) && host.mouseInView(mouseX, mouseY);
            g.fill(x, y, x + width, y + height, over ? hover : color);
        }
    }

    private static final class Framed extends Leaf {
        private final int face, edge;

        Framed(int w, int h, int face, int edge) {
            super(w, h);
            this.face = face;
            this.edge = edge;
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            g.fill(x, y, x + width, y + height, face);
            g.drawRectOutline(x, y, width, height, edge);
        }
    }

    private static final class Grad extends Leaf {
        private final int c1, c2;
        private final boolean horizontal;

        Grad(int w, int h, int c1, int c2, boolean horizontal) {
            super(w, h);
            this.c1 = c1;
            this.c2 = c2;
            this.horizontal = horizontal;
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            if (horizontal) io.muzy.sbc.ui.screens.SbcGradient.rect(g, x, y, width, height, c1, c2, c2, c1);
            else io.muzy.sbc.ui.screens.SbcGradient.rect(g, x, y, width, height, c1, c1, c2, c2);
        }
    }

    private static final class Bar extends Leaf {
        private final double pct;
        private final int c1, c2;

        Bar(int w, int h, double pct, int c1, int c2) {
            super(w, h);
            this.pct = pct;
            this.c1 = c1;
            this.c2 = c2;
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            g.fill(x, y, x + width, y + height, 0xC0050911);
            int f = (int) Math.round(width * pct);
            if (f <= 0) return;
            io.muzy.sbc.ui.screens.SbcGradient.rect(g, x, y, f, height, c1, c2, c2, c1);
            if (height >= 4 || (height >= 3)) g.fill(x, y, x + f, y + Math.max(1, height / 3), 0x38FFFFFF);
            if (f < width && height >= 2) g.fill(x + f - 1, y, x + f, y + height, 0x90FFFFFF);
        }
    }

    private static final class Icon extends Leaf {
        private final Supplier<ItemStack> stack;
        private final float scale;
        private ItemStack resolved;

        Icon(Supplier<ItemStack> stack, float scale) {
            super(Math.max(1, Math.round(16 * scale)), Math.max(1, Math.round(16 * scale)));
            this.stack = stack;
            this.scale = scale;
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            if (resolved == null) resolved = stack.get();
            if (resolved == null || resolved.isEmpty()) return;
            var p = g.pose();
            p.pushMatrix();
            p.translate(x, y);
            p.scale(scale, scale);
            g.item(resolved, 0, 0);
            p.popMatrix();
        }
    }

    private static final class Painted extends Leaf {
        private final Painter painter;
        private final Ui ui = new Ui();

        Painted(int w, int h, Painter painter) {
            super(w, h);
            this.painter = painter;
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            ui.begin(g, mouseX, mouseY);
            painter.paint(ui, x, y, width, height);
        }
    }

    // invisible area: takes a click and/or shows a tooltip
    private final class Zone extends Leaf {
        private final Runnable click;
        private final Supplier<String[]> tip;
        private final Supplier<net.minecraft.world.item.ItemStack> item;

        Zone(int w, int h, Runnable click, Supplier<String[]> tip) {
            this(w, h, click, tip, null);
        }

        Zone(int w, int h, Runnable click, Supplier<String[]> tip, Supplier<net.minecraft.world.item.ItemStack> item) {
            super(w, h);
            this.click = click;
            this.tip = tip;
            this.item = item;
            if (click != null) this.cursorStyle(CursorStyle.HAND);
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            if (tip == null || !isInBoundingBox(mouseX, mouseY) || !host.mouseInView(mouseX, mouseY)) return;
            if (item != null) host.showItemTip(item, tip);
            else host.showTip(tip);
        }

        @Override
        public boolean onMouseDown(MouseButtonEvent e, boolean doubled) {
            if (click == null || e.button() != InputConstants.MOUSE_BUTTON_LEFT) return super.onMouseDown(e, doubled);
            SbcWidgets.clickSound();
            click.run();
            return true;
        }
    }
}
