// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.mining;

import io.github.zmuzy.sbc.core.LegacyItems;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.hud.Compact;
import io.github.zmuzy.sbc.ui.hud.ElementStyle;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.ClassicTheme;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

// mining card: lines with a state chip, lined up columns, a thick meter or a sub heading, stuff plain rows cant do
// the editor still lists and hides lines by label
abstract class MiningCard extends HudElement {
    private static final int GAP = 6, WOBBLE = 20, FRESH_MS = 50, SHORT_GAP = 4;

    enum Kind { ROW, HEAD, RULE, PAIR }

    static final class Line {
        final Kind kind;
        final ItemStack icon;
        final String label;
        String[] cells = {};
        int[] colors = {};
        int labelColor;
        String chip;
        int chipColor;
        double progress = -1;
        int barColor, barH = Modern.METER;
        double[] ticks = {};
        private int below;
        // shared by the lines of one table so the columns line up
        private int[] columns;
        private String brief, briefChip, beside;
        private String[] briefCells;
        private Line left, right;
        private int room;

        private Line(Kind kind, ItemStack icon, String label) {
            this.kind = kind;
            this.icon = icon;
            this.label = label;
        }

        static Line row(String label, String... cells) {
            return new Line(Kind.ROW, null, label).cells(cells);
        }

        // line with an item icon in front, id is a vanilla item
        static Line item(String icon, String label, String... cells) {
            return new Line(Kind.ROW, LegacyItems.stack("minecraft:" + icon), label).cells(cells);
        }

        // sub heading: title in the card color, then column names below it or a note on the right
        static Line head(String title, String... cells) {
            return new Line(Kind.HEAD, null, title).cells(cells);
        }

        static Line rule() {
            return new Line(Kind.RULE, null, "");
        }

        Line cells(String... cells) {
            this.cells = cells;
            return this;
        }

        // cell colors in order, 0 or missing = value color. color codes inside a cell still work
        Line colors(int... colors) {
            this.colors = colors;
            return this;
        }

        Line labelColor(int color) {
            this.labelColor = color;
            return this;
        }

        Line chip(String text, int color) {
            this.chip = text;
            this.chipColor = color;
            return this;
        }

        Line meter(double progress, int color) {
            this.progress = Math.max(0, Math.min(1, progress));
            this.barColor = color;
            return this;
        }

        // the meter this card is about, drawn thicker
        Line thick() {
            this.barH = Modern.METER * 2;
            return this;
        }

        // a bit of air under the line, for chips stacked on each other
        Line spaced() {
            this.below = 2;
            return this;
        }

        // threshold marks on the meter, each 0 to 1
        Line ticks(double... ticks) {
            this.ticks = ticks;
            return this;
        }

        // label in a compact card when the shared short forms dont cover it
        Line brief(String label) {
            this.brief = label;
            return this;
        }

        // cells in a compact card: same numbers, fewer letters
        Line briefCells(String... cells) {
            this.briefCells = cells;
            return this;
        }

        // chip text in a compact card
        Line briefChip(String text) {
            this.briefChip = text;
            return this;
        }

        // in a compact card this line goes right after the one with that label (if shown) so they can share a row
        Line beside(String label) {
            this.beside = label;
            return this;
        }

        // compact version of this line: same values, fewer letters, less air
        private Line shortForm() {
            if (kind == Kind.RULE) return this;
            Line l = new Line(kind, icon, brief != null ? brief : kind == Kind.ROW ? Compact.label(label) : label);
            String[] from = briefCells != null ? briefCells : cells;
            l.cells = new String[from.length];
            for (int i = 0; i < from.length; i++) l.cells[i] = Compact.value(from[i]);
            l.colors = colors;
            l.labelColor = labelColor;
            l.chip = briefChip != null ? briefChip : chip;
            l.chipColor = chipColor;
            l.progress = progress;
            l.barColor = barColor;
            l.barH = Compact.THICK;
            l.ticks = ticks;
            return l;
        }

        private static Line pair(Line a, Line b, int room) {
            Line l = new Line(Kind.PAIR, null, "");
            l.left = a;
            l.right = b;
            l.room = room;
            return l;
        }

        private boolean hasMeter() {
            return kind == Kind.PAIR ? left.progress >= 0 || right.progress >= 0 : kind == Kind.ROW && progress >= 0;
        }

        private int shortWidth(Ui ui) {
            if (kind == Kind.RULE) return 0;
            if (kind == Kind.PAIR) {
                int a = left.shortWidth(ui), b = right.shortWidth(ui), even = 2 * Math.max(a, b) + Compact.GAP;
                return even <= room ? even : a + b + Compact.GAP;
            }
            int w = (icon != null ? 12 : 0) + ui.width(ClassicTheme.readable(label));
            for (int i = 0; i < cells.length; i++) if (columns != null || !cells[i].isEmpty()) w += SHORT_GAP + cellWidth(ui, i);
            if (chip != null) w += 4 + ui.width(chip) + 6;
            return w;
        }

        private int shortHeight(int rowH) {
            return kind == Kind.RULE ? Compact.RULE_BLOCK : rowH + (hasMeter() ? Compact.METER_BLOCK : 0);
        }

        private String cell(int i) {
            return ClassicTheme.readable(cells[i]);
        }

        private int cellWidth(Ui ui, int i) {
            return columns != null ? columns[i] : ui.width(cell(i));
        }

        private List<Row> asRow() {
            if (kind == Kind.HEAD && !label.isEmpty()) return List.of(new Row(label));
            if (kind != Kind.ROW) return List.of();
            StringBuilder value = new StringBuilder();
            for (String c : cells) if (!c.isEmpty()) value.append(value.isEmpty() ? "" : "  ").append(c);
            if (chip != null) value.append(value.isEmpty() ? "" : "  ").append(chip);
            return List.of(new Row(icon, label, value.toString(), 0, progress, barColor));
        }
    }

    // what a card says rn: its lines + whatever sits at the right end of the title
    static final class Body {
        final List<Line> lines = new ArrayList<>();
        String right = "", briefRight;

        Body add(Line... more) {
            lines.addAll(List.of(more));
            return this;
        }

        Body right(String text) {
            right = ClassicTheme.readable(text);
            return this;
        }

        // right end of the title in a compact card
        Body briefRight(String text) {
            briefRight = ClassicTheme.readable(text);
            return this;
        }
    }

    private int steadyWidth, steadyShort;
    private Body cached;
    private long cachedAt;
    private boolean cachedDemo;

    MiningCard(String id, String title, int priority, String feature) {
        super(id, title, "Mining", Zone.TOP_LEFT, priority, feature);
    }

    abstract Body live();

    // what the pictures and editor show: same drawing as live with realistic numbers
    abstract Body sample();

    int maxWidth() {
        return 236;
    }

    // measuring + drawing ask several times a frame for stuff that changes about once a sec, so cache
    private Body body() {
        long now = System.currentTimeMillis();
        if (cached == null || cachedDemo != demo || now - cachedAt > FRESH_MS || now < cachedAt) {
            cached = demo ? sample() : live();
            cachedAt = now;
            cachedDemo = demo;
        }
        return cached;
    }

    private static List<Row> asRows(Body b) {
        List<Row> out = new ArrayList<>();
        for (Line l : b.lines) out.addAll(l.asRow());
        return out;
    }

    @Override
    public final List<Row> rows() {
        return asRows(live());
    }

    @Override
    public final List<Row> demo() {
        return asRows(sample());
    }

    @Override
    public final String headingValue() {
        Body b = body();
        return compact() && b.briefRight != null ? b.briefRight : b.right;
    }

    // lines left after what the player hid (a hidden heading takes its rows with it), no headings over nothing,
    // columns measured
    private static List<Line> shown(Body b, ElementStyle st) {
        List<Line> in = new ArrayList<>();
        boolean sectionHidden = false;
        for (Line l : b.lines) {
            if (l.kind == Kind.HEAD) sectionHidden = !l.label.isEmpty() && st.isHidden(l.label);
            if (!sectionHidden && (l.kind != Kind.ROW || !st.isHidden(l.label))) in.add(l);
        }
        for (int i = in.size() - 1; i >= 0; i--) {
            if (in.get(i).kind == Kind.ROW) continue;
            boolean nothingUnder = i + 1 >= in.size() || in.get(i + 1).kind != Kind.ROW;
            if (nothingUnder) in.remove(i);
        }
        while (!in.isEmpty() && in.get(0).kind == Kind.RULE) in.remove(0);
        Ui ui = Ui.MEASURE;
        int[] table = null;
        for (Line l : in) {
            if (l.kind == Kind.HEAD) table = null;
            if (l.cells.length < 2) continue;
            if (table == null || table.length != l.cells.length) table = new int[l.cells.length];
            for (int i = 0; i < table.length; i++) table[i] = Math.max(table[i], ui.width(l.cell(i)));
            l.columns = table;
        }
        return in;
    }

    // compact card lines: nothing left out, labels + numbers short
    // two lines next to each other share a row when they fit, side by side at the middle if possible. table rows
    // keep their columns instead
    private static List<Line> packed(Body b, ElementStyle st, int room) {
        List<Line> order = new ArrayList<>(shown(b, st));
        for (Line l : List.copyOf(order)) {
            if (l.beside == null) continue;
            for (Line target : order) {
                if (!target.label.equals(l.beside)) continue;
                order.remove(l);
                order.add(order.indexOf(target) + 1, l);
                break;
            }
        }
        Ui ui = Ui.MEASURE;
        List<Line> out = new ArrayList<>(order.size());
        boolean table = false;
        for (int i = 0; i < order.size(); i++) {
            Line a = order.get(i).shortForm();
            if (a.kind == Kind.HEAD) table = a.cells.length > 0;
            if (a.kind == Kind.RULE) table = false;
            if (!table && a.kind == Kind.ROW && i + 1 < order.size() && order.get(i + 1).kind == Kind.ROW) {
                Line next = order.get(i + 1).shortForm();
                if (a.shortWidth(ui) + next.shortWidth(ui) + Compact.GAP <= room) {
                    out.add(Line.pair(a, next, room));
                    i++;
                    continue;
                }
            }
            out.add(a);
        }
        int[] columns = null;
        for (Line l : out) {
            if (l.kind == Kind.HEAD) columns = null;
            if (l.cells.length < 2) continue;
            if (columns == null || columns.length != l.cells.length) columns = new int[l.cells.length];
            for (int i = 0; i < columns.length; i++) columns[i] = Math.max(columns[i], ui.width(l.cell(i)));
            l.columns = columns;
        }
        return out;
    }

    private int shortRoom(Theme t, ElementStyle st) {
        return Compact.WIDE - 2 * pad(t, st) - (titleShown(st) ? 0 : icon().isEmpty() ? 2 : 14);
    }

    @Override
    public int width() {
        Theme t = Theme.current();
        ElementStyle st = style();
        Ui ui = Ui.MEASURE;
        Body b = body();
        boolean titled = titleShown(st);
        String right = headingValue();
        int w = titled ? 16 + ui.width(heading()) + 6 + (right.isEmpty() ? 0 : ui.width(right) + 8) : 30;
        int around = 2 * pad(t, st) + (titled ? 0 : icon().isEmpty() ? 2 : 14);
        for (Line l : packed(b, st, shortRoom(t, st))) w = Math.max(w, l.shortWidth(ui) + around);
        w = Math.min(maxWidth(), Math.max(titled ? 80 : 40, w));
        // seconds ticking down shouldnt make the card breathe, only shrink for more than a digit or two
        if (w > steadyShort || w < steadyShort - WOBBLE) steadyShort = w;
        return steadyShort;
    }

    @Override
    public int height() {
        Theme t = Theme.current();
        ElementStyle st = style();
        int mh = titleShown(st) ? Modern.BAND + 3 : 5, crh = Math.min(rowH(t, st), Compact.ROW_H);
        for (Line l : packed(body(), st, shortRoom(t, st))) mh += l.shortHeight(crh);
        return mh;
    }

    @Override
    public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
        drawShort(ui, x, y, Theme.current(), style(), body());
    }

    // the compact card

    private void drawShort(Ui ui, int x, int y, Theme t, ElementStyle st, Body b) {
        int w = drawWidth(), h = height(), pad = pad(t, st), rh = Math.min(rowH(t, st), Compact.ROW_H), accent = st.titleColor() != null ? st.titleColor() : accent();
        t.panel(ui, x, y, w, h, st);
        int cx = x + pad, cy, cw = w - 2 * pad;
        if (titleShown(st)) {
            cy = y + Modern.band(ui, heading(), icon(), headingValue(), accent, x, y, w);
        } else {
            Modern.edge(ui, x, y, h, accent);
            cy = y + 3;
            if (!icon().isEmpty()) {
                ui.icon(icon(), x + 4, y + (h - Modern.ICON) / 2, Modern.ICON_SCALE);
                cx += 12;
                cw -= 12;
            }
        }
        int labelColor = st.textColor() != null ? st.textColor() : Modern.LABEL;
        int valueColor = st.valueColor() != null ? st.valueColor() : Modern.INK;
        for (Line l : packed(b, st, shortRoom(t, st))) {
            switch (l.kind) {
                case RULE -> {
                }
                case HEAD -> shortHead(ui, l, cx, cy, cw, accent);
                case ROW -> shortRow(ui, l, cx, cy, cw, rh, labelColor, valueColor);
                case PAIR -> {
                    int a = l.left.shortWidth(ui), bw = l.right.shortWidth(ui), half = (cw - Compact.GAP) / 2;
                    int lw = a <= half && bw <= half ? half : a + (cw - Compact.GAP - a - bw) / 2;
                    shortRow(ui, l.left, cx, cy, lw, rh, labelColor, valueColor);
                    ui.rect(cx + lw + Compact.GAP / 2, cy, 1, 8, 0x30FFFFFF);
                    shortRow(ui, l.right, cx + lw + Compact.GAP, cy, cw - lw - Compact.GAP, rh, labelColor, valueColor);
                }
            }
            cy += l.shortHeight(rh);
        }
    }

    // cells like in cells(), closer together
    private static int shortCells(Ui ui, Line l, int right, int y, int leftEnd, int defaultColor) {
        int edge = right;
        for (int i = l.cells.length - 1; i >= 0; i--) {
            String c = l.cell(i);
            int vw = ui.width(c), room = right - leftEnd - SHORT_GAP;
            if (vw > room && l.columns == null) {
                c = ui.ellipsize(c, Math.max(8, room));
                vw = ui.width(c);
            }
            int color = i < l.colors.length && l.colors[i] != 0 ? l.colors[i] : defaultColor;
            if (!c.isEmpty()) {
                ui.textShadow(c, right - vw, y, color);
                edge = right - vw - SHORT_GAP;
            }
            right -= Math.max(vw, l.columns != null ? l.columns[i] : 0) + SHORT_GAP;
        }
        return edge;
    }

    private static void shortHead(Ui ui, Line l, int x, int y, int w, int accent) {
        int left = shortCells(ui, l, x + w, y, x + ui.width(l.label), Modern.DIM);
        if (!l.label.isEmpty()) Modern.section(ui, l.label, x, y, left - x, accent);
    }

    private static void shortRow(Ui ui, Line l, int x, int y, int w, int rh, int labelColor, int valueColor) {
        int lx = x, right = x + w;
        if (l.icon != null) {
            ui.icon(l.icon, lx - 1, y - 1, 0.6f);
            lx += 12;
        }
        if (l.chip != null) {
            right -= ui.width(l.chip) + 6;
            Modern.chip(ui, right, y, l.chip, l.chipColor);
            right -= 4;
        }
        right = shortCells(ui, l, right, y, lx, valueColor);
        if (!l.label.isEmpty()) ui.textShadow(ui.ellipsize(ClassicTheme.readable(l.label), Math.max(8, right - lx)), lx, y, l.labelColor != 0 ? l.labelColor : labelColor);
        if (l.progress < 0) return;
        int my = y + rh, mw = x + w - lx;
        Modern.meter(ui, lx, my, mw, l.barH, l.progress, l.barColor);
        for (double tick : l.ticks) ui.rect(lx + (int) Math.round(mw * tick), my, 1, l.barH, 0xB0FFFFFF);
    }
}
