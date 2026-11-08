// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.rift;

import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.hud.Compact;
import dev.zmuzy.sbc.ui.hud.ElementStyle;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.hud.RowText;
import dev.zmuzy.sbc.ui.theme.ClassicTheme;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

// card with more to say than label/value rows: sub headings, state chips, aligned number columns, thick
// meters, segment bars. answers the same stuff as any hud element
public abstract class RichCard extends HudElement {
    private static final int COLUMN_GAP = 6, ICON_SLOT = 12;

    // what a card says: text at the right of its band + its lines
    public record Content(String right, List<Line> lines) {}

    // one piece of a segment bar
    public record Seg(double weight, int color) {}

    private enum Kind { ROW, TABLE, SECTION, RULE, BAR, SEGMENTS, PAIR }

    // one card line. its key = its name in the editors row list, by default what its called
    public static final class Line {
        private final Kind kind;
        private String key, label = "", value = "", chip, brief;
        private Line left, right;
        private ItemStack icon;
        private int color, labelColor, barColor, chipColor, gap;
        private double progress = -1;
        private boolean noMeter, raw;
        private String[] cells = new String[0];
        private List<Seg> segs = List.of();

        private Line(Kind kind) {
            this.kind = kind;
        }

        // label left, value right. "a/b" in the value gets a percent + meter like any card
        public static Line row(String label, String value) {
            Line l = new Line(Kind.ROW);
            l.setLabel(label);
            l.value = value == null ? "" : value;
            l.key = RowText.plain(label).trim();
            return l;
        }

        public static Line row(String label, String value, int color) {
            Line l = row(label, value);
            l.color = color;
            return l;
        }

        // label left, cells in right aligned columns shared with the table lines next to it
        public static Line table(String label, String... cells) {
            Line l = new Line(Kind.TABLE);
            l.setLabel(label);
            l.cells = cells;
            l.key = RowText.plain(label).trim();
            return l;
        }

        public static Line section(String title) {
            return section(title, "");
        }

        // sub heading with something on its right. goes away when everything under it is hidden or missing
        public static Line section(String title, String right) {
            Line l = new Line(Kind.SECTION);
            l.label = title;
            l.value = right;
            l.key = "";
            return l;
        }

        public static Line rule() {
            Line l = new Line(Kind.RULE);
            l.key = "";
            return l;
        }

        // thick meter for the one number a card is about
        public static Line bar(String key, double progress, int color) {
            Line l = new Line(Kind.BAR);
            l.key = key;
            l.progress = Math.max(0, Math.min(1, progress));
            l.barColor = color;
            return l;
        }

        // bar in colored pieces: shares of a whole, or one block per thing with gaps
        public static Line segments(String key, int gap, List<Seg> segs) {
            Line l = new Line(Kind.SEGMENTS);
            l.key = key;
            l.gap = gap;
            l.segs = segs;
            return l;
        }

        private void setLabel(String text) {
            int dark = darkColor(text);
            label = dark == 0 ? text : RowText.plain(text);
            labelColor = dark;
        }

        public Line key(String key) {
            this.key = key;
            return this;
        }

        // label in its own color whatever the card text color is
        public Line labelColor(int color) {
            labelColor = color;
            return this;
        }

        public Line icon(ItemStack icon) {
            this.icon = icon;
            return this;
        }

        public Line icon(String modernId) {
            this.icon = LegacyItems.stack(modernId);
            return this;
        }

        public Line meter(double progress) {
            this.progress = Math.max(0, Math.min(1, progress));
            return this;
        }

        public Line meter(double progress, int color) {
            this.barColor = color;
            return meter(progress);
        }

        // no meter whatever the text looks like, numbers that arent progress
        public Line noMeter() {
            noMeter = true;
            return this;
        }

        // text as is, not split into label, value, percent
        public Line raw() {
            raw = true;
            return this;
        }

        // compact label when the shared short forms dont cover it
        public Line brief(String shortLabel) {
            brief = shortLabel;
            return this;
        }

        // this line in a compact card: same value, fewer letters
        private Line compacted() {
            if (kind != Kind.ROW && kind != Kind.TABLE) return this;
            Line l = new Line(kind);
            l.key = key;
            l.label = brief != null ? brief : kind == Kind.ROW ? Compact.label(label) : label;
            l.value = Compact.value(value);
            l.labelColor = labelColor;
            l.chip = chip;
            l.chipColor = chipColor;
            l.icon = icon;
            l.color = color;
            l.barColor = barColor;
            l.progress = progress;
            l.noMeter = noMeter;
            l.raw = raw;
            l.cells = new String[cells.length];
            for (int i = 0; i < cells.length; i++) l.cells[i] = Compact.value(cells[i]);
            return l;
        }

        private boolean mayPair() {
            return kind == Kind.ROW && !hasMeter() && chip == null && !value.isEmpty();
        }

        private int rowWidth(Ui ui) {
            return ui.width(label) + ui.width(value) + 6 + (icon != null ? ICON_SLOT : 0);
        }

        private static Line pair(Line a, Line b) {
            Line l = new Line(Kind.PAIR);
            l.key = "";
            l.left = a;
            l.right = b;
            return l;
        }

        // small colored tag before the value
        public Line chip(String text, int color) {
            chip = text;
            chipColor = color;
            return this;
        }

        private Line shaped() {
            if (kind != Kind.ROW || raw) return this;
            RowText.Shaped s = RowText.shape(label, value);
            Line l = new Line(Kind.ROW);
            l.key = key;
            l.label = s.label();
            l.value = s.value();
            l.labelColor = labelColor;
            l.chip = chip;
            l.chipColor = chipColor;
            l.icon = icon;
            l.color = color;
            l.barColor = barColor;
            l.progress = progress >= 0 ? progress : noMeter ? -1 : s.progress();
            l.brief = brief;
            return l;
        }

        private boolean hasMeter() {
            return (kind == Kind.ROW || kind == Kind.TABLE) && progress >= 0;
        }
    }

    // the games dark colors were made for black tooltips and are hard to read on glass. text in one of those gets
    // a brighter version, 0 = text has none
    private static int darkColor(String s) {
        char code = 0;
        for (int i = 0; i + 1 < s.length(); i++) {
            if (s.charAt(i) != '§') continue;
            char c = Character.toLowerCase(s.charAt(++i));
            if (code != 0 && c != code) return 0;
            code = c;
        }
        return switch (code) {
            case '0' -> Modern.DIM;
            case '1', '9' -> 0xFF7F9BFF;
            case '2' -> 0xFF4FD86A;
            case '3' -> 0xFF4FD8C4;
            case '4' -> 0xFFFF6B6B;
            case '5' -> 0xFFC77DFF;
            case '8' -> Modern.LABEL;
            default -> 0;
        };
    }

    private Content cached, state;
    private long cachedAt;
    private boolean cachedDemo, cachedCompact;

    protected RichCard(String id, String title, String section, Zone zone, int priority, String featureId) {
        super(id, title, section, zone, priority, featureId);
    }

    // what it says rn
    protected abstract Content live();

    // what the editor + pictures show: everything the card can say, with real looking numbers
    protected abstract Content sample();

    // the plain rows version of this card
    protected abstract List<Row> classicRows();

    protected abstract List<Row> classicDemo();

    // other looks the sample doesnt show, for pictures
    public List<Content> states() {
        return List.of();
    }

    // draws this instead of the live stuff till told null, for pictures
    public final void showState(Content state) {
        this.state = state;
    }

    // max width before scale. a card next to a menu has more room than one over the game
    protected int maxWidth() {
        return 236;
    }

    private Content content() {
        if (state != null) return state;
        long now = System.currentTimeMillis();
        boolean compact = compact();
        if (cached == null || cachedDemo != demo || cachedCompact != compact || now - cachedAt > 50) {
            cached = demo ? sample() : live();
            cachedDemo = demo;
            cachedCompact = compact;
            cachedAt = now;
        }
        return cached;
    }

    @Override
    public final String headingValue() {
        return heading().isEmpty() ? "" : content().right();
    }

    // what the editor lists as hideable rows
    private static List<Row> keys(Content c) {
        Set<String> seen = new LinkedHashSet<>();
        for (Line l : c.lines()) if (!l.key.isEmpty()) seen.add(l.key);
        List<Row> out = new ArrayList<>();
        for (String k : seen) out.add(new Row(k));
        return out;
    }

    @Override
    public final List<Row> rows() {
        return keys(live());
    }

    @Override
    public final List<Row> demo() {
        return keys(sample());
    }

    // shown() shapes every line (regexes) + packs the short form, and width/height/draw all ask every frame
    // inputs are just the content (new at most every 50ms), style and theme, so cache it
    private List<Line> shownMemo;
    private Content shownFor;
    private int shownStyle;
    private Theme shownTheme;
    private boolean shownCompact;

    private List<Line> shown(ElementStyle st) {
        Content c = content();
        Theme t = Theme.current();
        int stamp = st.stamp();
        boolean compact = compact();
        if (shownMemo != null && c == shownFor && stamp == shownStyle && t == shownTheme && compact == shownCompact) return shownMemo;
        shownMemo = shownNow(c, st);
        shownFor = c;
        shownStyle = stamp;
        shownTheme = t;
        shownCompact = compact;
        return shownMemo;
    }

    // lines to draw minus what you hid, empty sub headings and rules with nothing to split
    private List<Line> shownNow(Content c, ElementStyle st) {
        List<Line> kept = new ArrayList<>();
        // the band carries the cards main number, without a band it needs its own line
        if (!heading().isEmpty() && !titleShown(st) && !c.right().isEmpty()) kept.add(Line.row(title, c.right()).raw());
        for (Line l : c.lines()) if (l.key.isEmpty() || !st.isHidden(l.key)) kept.add(l.shaped());
        List<Line> out = new ArrayList<>();
        for (int i = 0; i < kept.size(); i++) {
            Line l = kept.get(i);
            if (l.kind == Kind.SECTION || l.kind == Kind.RULE) {
                boolean content = i + 1 < kept.size() && kept.get(i + 1).kind != Kind.SECTION && kept.get(i + 1).kind != Kind.RULE;
                if (!content || (l.kind == Kind.RULE && out.isEmpty())) continue;
            }
            out.add(l);
        }
        return compact() ? packed(out, st) : out;
    }

    // short form lines: nothing left out, 2 short ones next to each other share a row
    private List<Line> packed(List<Line> lines, ElementStyle st) {
        List<Line> brief = new ArrayList<>(lines.size());
        for (Line l : lines) brief.add(l.compacted());
        int room = Compact.WIDE - 2 * pad(Theme.current(), st) - (titleShown(st) ? 0 : !gutterIcon() ? 2 : 14);
        List<Line> out = new ArrayList<>(lines.size());
        for (List<Line> row : Compact.rows(brief, Line::mayPair, l -> l.rowWidth(Ui.MEASURE), room)) out.add(row.size() == 2 ? Line.pair(row.get(0), row.get(1)) : row.get(0));
        return out;
    }

    private static int lineHeight(Line l, int rh) {
        return switch (l.kind) {
            case ROW, TABLE -> rh + (l.hasMeter() ? Compact.METER_BLOCK : 0);
            case PAIR, SECTION -> rh;
            case RULE -> Compact.RULE_BLOCK;
            case BAR, SEGMENTS -> Compact.THICK_BLOCK;
        };
    }

    private static boolean inTable(Line l) {
        return l.kind == Kind.TABLE || l.kind == Kind.RULE;
    }

    // does the table line at i belong to the table above, a hairline doesnt end a table
    private static boolean continuesTable(List<Line> lines, int i) {
        int p = i - 1;
        while (p >= 0 && lines.get(p).kind == Kind.RULE) p--;
        return p >= 0 && lines.get(p).kind == Kind.TABLE;
    }

    // width of each column of the table lines from index from, for as long as they go
    private static int[] columns(Ui ui, List<Line> lines, int from) {
        int n = 0;
        for (int i = from; i < lines.size() && inTable(lines.get(i)); i++) n = Math.max(n, lines.get(i).cells.length);
        int[] w = new int[n];
        for (int i = from; i < lines.size() && inTable(lines.get(i)); i++) {
            String[] c = lines.get(i).cells;
            // numbered from the right so a line with fewer columns still ends flush
            for (int k = 0; k < c.length; k++) w[n - c.length + k] = Math.max(w[n - c.length + k], ui.width(c[k]));
        }
        return w;
    }

    // where a run of lines between 2 sub headings has icons, lines without one line up with the rest
    private static boolean[] iconSlots(List<Line> lines) {
        boolean[] slots = new boolean[lines.size()];
        for (int from = 0; from < lines.size(); ) {
            int to = from;
            boolean any = false;
            while (to < lines.size() && (to == from || lines.get(to).kind != Kind.SECTION)) any |= lines.get(to++).icon != null;
            for (int i = from; i < to; i++) slots[i] = any;
            from = to;
        }
        return slots;
    }

    private static int sum(int[] columns) {
        int s = 0;
        for (int c : columns) if (c > 0) s += c + COLUMN_GAP;
        return s;
    }

    @Override
    public int width() {
        Theme t = Theme.current();
        ElementStyle st = style();
        Ui ui = Ui.MEASURE;
        boolean titled = titleShown(st);
        String right = headingValue();
        int w = titled ? 16 + ui.width(heading()) + 6 + (right.isEmpty() ? 0 : ui.width(right) + 8) : 30;
        int inner = 2 * pad(t, st) + (titled ? 0 : !gutterIcon() ? 2 : 14);
        List<Line> lines = shown(st);
        boolean[] slots = iconSlots(lines);
        int[] columns = {};
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            int rw = 0;
            switch (l.kind) {
                case ROW -> rw = ui.width(l.label) + (l.value.isEmpty() ? 0 : ui.width(l.value) + 8) + (slots[i] ? ICON_SLOT : 0) + (l.chip != null ? ui.width(l.chip) + 10 : 0);
                case TABLE -> {
                    if (!continuesTable(lines, i)) columns = columns(ui, lines, i);
                    rw = ui.width(l.label) + sum(columns) + (slots[i] ? ICON_SLOT : 0);
                }
                case SECTION -> rw = ui.width(l.label) + (l.value.isEmpty() ? 0 : ui.width(l.value) + 12);
                case PAIR -> rw = 2 * Math.max(l.left.rowWidth(ui), l.right.rowWidth(ui)) + Compact.GAP;
                default -> {
                }
            }
            w = Math.max(w, rw + inner);
        }
        return Math.min(maxWidth(), Math.max(titled ? 100 : 40, w));
    }

    @Override
    public int height() {
        Theme t = Theme.current();
        ElementStyle st = style();
        int rh = Math.min(rowH(t, st), Compact.ROW_H), h = titleShown(st) ? headH() : 5;
        for (Line l : shown(st)) h += lineHeight(l, rh);
        return h;
    }

    @Override
    public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
        Theme t = Theme.current();
        ElementStyle st = style();
        int w = drawWidth(), h = height(), pad = pad(t, st), rh = Math.min(rowH(t, st), Compact.ROW_H), accent = st.titleColor() != null ? st.titleColor() : accent();
        cardPanel(t, ui, x, y, w, h, st);
        int cx = x + pad, cw = w - 2 * pad, cy;
        if (titleShown(st)) {
            cy = y + cardBand(ui, heading(), icon(), headingValue(), accent, x, y, w);
        } else {
            cardEdge(ui, x, y, h, accent);
            cy = y + 3;
            if (gutterIcon()) {
                ui.icon(icon(), x + 4, y + (h - Modern.ICON) / 2, Modern.ICON_SCALE);
                cx += 12;
                cw -= 12;
            }
        }
        int labelColor = st.textColor() != null ? st.textColor() : Modern.LABEL;
        int valueColor = ElementStyle.or(st.valueColor(), Modern.INK);
        List<Line> lines = shown(st);
        boolean[] slots = iconSlots(lines);
        int[] columns = {};
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            switch (l.kind) {
                case ROW -> drawRow(ui, l, slots[i], cx, cy, cw, l.labelColor != 0 ? l.labelColor : labelColor, l.color != 0 ? l.color : valueColor);
                case TABLE -> {
                    if (!continuesTable(lines, i)) columns = columns(ui, lines, i);
                    drawTable(ui, l, slots[i], columns, cx, cy, cw, l.labelColor != 0 ? l.labelColor : labelColor, l.color != 0 ? l.color : valueColor);
                }
                case SECTION -> {
                    int rw = l.value.isEmpty() ? 0 : ui.width(l.value) + 4;
                    Modern.section(ui, l.label, cx, cy, cw - rw, accent);
                    if (rw > 0) ui.textShadow(l.value, cx + cw - rw + 4, cy, Modern.INK);
                }
                case RULE -> {
                }
                case BAR -> Modern.meter(ui, cx, cy, cw, Compact.THICK, l.progress, l.barColor != 0 ? l.barColor : l.progress >= 1 ? Modern.GOOD : accent);
                case SEGMENTS -> drawSegments(ui, l, cx, cy, cw, Compact.THICK);
                case PAIR -> {
                    int half = (cw - Compact.GAP) / 2;
                    drawRow(ui, l.left, l.left.icon != null, cx, cy, half, l.left.labelColor != 0 ? l.left.labelColor : labelColor, l.left.color != 0 ? l.left.color : valueColor);
                    ui.rect(cx + half + Compact.GAP / 2, cy, 1, 8, 0x30FFFFFF);
                    drawRow(ui, l.right, l.right.icon != null, cx + half + Compact.GAP, cy, cw - half - Compact.GAP, l.right.labelColor != 0 ? l.right.labelColor : labelColor, l.right.color != 0 ? l.right.color : valueColor);
                }
            }
            cy += lineHeight(l, rh);
            if (l.hasMeter()) Modern.meter(ui, cx, cy - Compact.METER_BLOCK, cw, Modern.METER, l.progress, l.barColor != 0 ? l.barColor : meterColor(new Row(null, l.label, l.value, l.color, l.progress, 0), accent));
        }
    }

    private static void drawRow(Ui ui, Line l, boolean slot, int x, int y, int w, int labelColor, int valueColor) {
        int lx = x, lw = w;
        if (l.icon != null) ui.icon(l.icon, lx - 1, y - 1, 0.6f);
        if (slot) {
            lx += ICON_SLOT;
            lw -= ICON_SLOT;
        }
        if (l.chip == null) {
            Modern.row(ui, l.label, l.value, lx, y, lw, labelColor, valueColor);
            return;
        }
        String value = ClassicTheme.readable(l.value);
        int vw = value.isEmpty() ? 0 : ui.width(value);
        if (vw > 0) ui.textShadow(value, lx + lw - vw, y, valueColor);
        int cw = ui.width(l.chip) + 6, chipX = lx + lw - vw - (vw > 0 ? 4 : 0) - cw;
        Modern.chip(ui, chipX, y, l.chip, l.chipColor);
        ui.textShadow(ui.ellipsize(ClassicTheme.readable(l.label), Math.max(8, chipX - lx - 4)), lx, y, labelColor);
    }

    private static void drawTable(Ui ui, Line l, boolean slot, int[] columns, int x, int y, int w, int labelColor, int valueColor) {
        int lx = x;
        if (l.icon != null) ui.icon(l.icon, lx - 1, y - 1, 0.6f);
        if (slot) lx += ICON_SLOT;
        int right = x + w, first = columns.length - l.cells.length;
        for (int k = columns.length - 1; k >= 0; k--) {
            if (k >= first) {
                String cell = l.cells[k - first];
                int dark = darkColor(cell);
                cell = dark != 0 ? RowText.plain(cell) : ClassicTheme.readable(cell);
                if (!cell.isEmpty()) ui.textShadow(cell, right - ui.width(cell), y, dark != 0 ? dark : valueColor);
            }
            if (columns[k] > 0) right -= columns[k] + COLUMN_GAP;
        }
        if (!l.label.isEmpty()) ui.textShadow(ui.ellipsize(ClassicTheme.readable(l.label), Math.max(8, right - lx)), lx, y, labelColor);
    }

    private static void drawSegments(Ui ui, Line l, int x, int y, int w, int thick) {
        double total = 0;
        for (Seg s : l.segs) total += Math.max(0, s.weight());
        if (total <= 0) {
            Modern.meter(ui, x, y, w, thick, 0, 0);
            return;
        }
        int pieces = 0;
        for (Seg s : l.segs) if (s.weight() > 0) pieces++;
        int room = w - l.gap * (pieces - 1), left = room, seen = 0, sx = x;
        for (Seg s : l.segs) {
            if (s.weight() <= 0) continue;
            seen++;
            // last piece takes what rounding left so the bar always ends flush
            int sw = seen == pieces ? left : Math.max(1, Math.min(left - (pieces - seen), (int) Math.round(room * s.weight() / total)));
            Modern.meter(ui, sx, y, sw, thick, 1, s.color());
            sx += sw + l.gap;
            left -= sw;
        }
    }
}
