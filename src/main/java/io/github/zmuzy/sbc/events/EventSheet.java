// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.events;

import io.github.zmuzy.sbc.core.LegacyItems;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.hud.Compact;
import io.github.zmuzy.sbc.ui.hud.ElementStyle;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.hud.RowText;
import io.github.zmuzy.sbc.ui.theme.ClassicTheme;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

// what event cards use beyond a list of rows: number tiles, 2 column grid, sub headings, one big number, a
// bar split into shares, rows with a state tag or charge pips
// rebuilt from data every time, live or sample through the same code, so the editor and pictures show the real layout
// every named piece can be hidden in the hud editor under its label like any row
public final class EventSheet {
    // rarities readable on the glass, the game's dark blue and purple arent
    public static final int COMMON = Modern.INK, UNCOMMON = Modern.GOOD, RARE = 0xFF7FB3FF, EPIC = 0xFFC77DFF, LEGENDARY = 0xFFFFC14D, MYTHIC = 0xFFFF7CE5, DIVINE = Modern.AQUA;

    private static final int TAIL = 32, TILE = 22, HERO = 14, PIP = 5, GAP = 6, RIGHT = -2;
    private static final int RULE = 0x1CFFFFFF, GROOVE = 0xB0050911;
    private static final int SHORT_GAP = 4, SHORT_TAIL = 30, HAIRLINE = 0x30FFFFFF;

    // right end of the card's band
    public String right = "";
    private final List<Block> blocks = new ArrayList<>();

    private record Paint(int accent, int label, int value, int rowH) {}

    private abstract static class Block {
        abstract int height(Paint p);

        abstract int width(Ui ui);

        abstract void draw(Ui ui, Paint p, int x, int y, int w);

        void addRows(List<HudElement.Row> out) {}

        // applies what got hidden, false = nothing left
        boolean keep(ElementStyle st) {
            return true;
        }

        // in a compact card: lines that can share a row go on the run, the rest straight out
        void shorten(List<Block> out, List<Line> run, int room) {
            flush(out, run, room);
            out.add(this);
        }
    }

    private static String readable(String s) {
        return ClassicTheme.readable(s);
    }

    // waiting lines into rows, two neighbours share one if they fit. nothing moves past anything else
    private static void flush(List<Block> out, List<Line> run, int room) {
        Ui ui = Ui.MEASURE;
        for (int i = 0; i < run.size(); i++) {
            Line a = run.get(i);
            if (i + 1 < run.size() && a.pairWidth(ui) + run.get(i + 1).pairWidth(ui) + Compact.GAP <= room) {
                out.add(new ShortRow(List.of(a, run.get(++i)), room));
            } else out.add(new ShortRow(List.of(a), room));
        }
        run.clear();
    }

    private static ItemStack item(String id) {
        return LegacyItems.stack("minecraft:" + id);
    }

    private static void pip(Ui ui, int x, int y, int color, boolean filled) {
        int c = color | 0xFF000000;
        if (filled) {
            ui.rect(x, y, PIP, PIP, Modern.darken(c, 0.1f));
            ui.rect(x, y, PIP, 1, Modern.lighten(c, 0.35f));
        } else {
            ui.rect(x, y, PIP, PIP, GROOVE);
            int dim = Ui.alpha(c, 0.55f);
            ui.rect(x, y, PIP, 1, dim);
            ui.rect(x, y + PIP - 1, PIP, 1, dim);
            ui.rect(x, y + 1, 1, PIP - 2, dim);
            ui.rect(x + PIP - 1, y + 1, 1, PIP - 2, dim);
        }
    }

    // --- a row ---

    // label + value, with whatever icon, pip, state tag, charge pips, share column and meter it needs
    public static final class Line extends Block {
        private final String label, value;
        private final int valueColor;
        private int labelColor, pip, chipColor, chipAt = -1, pipsNow, pipsMax, pipsColor, barColor, steps;
        private boolean pipFilled, alarm, indented;
        private ItemStack icon;
        private String chip, tail = "", brief, briefValue;
        private double progress = -1;

        private Line(String label, String value, int valueColor) {
            this.label = label;
            this.value = value == null ? "" : value;
            this.valueColor = valueColor;
        }

        public Line icon(String id) {
            icon = item(id);
            return this;
        }

        public Line icon(ItemStack stack) {
            icon = stack;
            return this;
        }

        public Line pip(int color, boolean filled) {
            pip = color;
            pipFilled = filled;
            return this;
        }

        // sits under a line with an icon and belongs to it
        public Line indent() {
            indented = true;
            return this;
        }

        public Line labelColor(int color) {
            labelColor = color;
            return this;
        }

        // state tag right after the label
        public Line chip(String text, int color) {
            chip = text;
            chipColor = color;
            return this;
        }

        // state tag in its own column, this far from the label start
        public Line chipAt(int offset) {
            chipAt = offset;
            return this;
        }

        // state tag at the right end, value next to it
        public Line chipRight() {
            chipAt = RIGHT;
            return this;
        }

        // second number in its own column on the right, usually a share
        public Line tail(String text) {
            tail = text;
            return this;
        }

        public Line pips(int now, int max, int color) {
            pipsNow = now;
            pipsMax = max;
            pipsColor = color;
            return this;
        }

        public Line meter(double fraction) {
            return meter(fraction, 0);
        }

        // meter under the line, green when full
        public Line meter(double fraction, int color) {
            progress = Math.max(0, Math.min(1, fraction));
            barColor = color;
            return this;
        }

        // marks the meter's steps when it counts in steps, like a chain of 4 burrows
        public Line steps(int n) {
            steps = n;
            return this;
        }

        // meter that keeps its colour when full, a full barn is no good news lol
        public Line alarmMeter(double fraction, int color) {
            alarm = true;
            return meter(fraction, color);
        }

        // label in a compact card when the shared short forms dont cover it
        public Line brief(String shortLabel) {
            brief = shortLabel;
            return this;
        }

        // value in a compact card, same numbers fewer letters
        public Line briefValue(String shortValue) {
            briefValue = shortValue;
            return this;
        }

        private int inset() {
            return (icon != null && !icon.isEmpty()) || indented ? 12 : pip != 0 ? 8 : 0;
        }

        private int pipsWidth() {
            return pipsMax <= 0 ? 0 : pipsMax * (PIP + 1) + 4;
        }

        @Override
        int height(Paint p) {
            return p.rowH + (progress >= 0 ? Modern.METER_BLOCK : 0);
        }

        @Override
        int width(Ui ui) {
            int w = inset() + ui.width(label);
            if (!value.isEmpty()) w += ui.width(value) + GAP + 2;
            if (!tail.isEmpty()) w += TAIL;
            if (chip != null) w += ui.width(chip) + 10;
            return w + pipsWidth();
        }

        @Override
        void draw(Ui ui, Paint p, int x, int y, int w) {
            int lx = x + inset(), right = x + w;
            if (icon != null && !icon.isEmpty()) ui.icon(icon, x - 1, y - 1, 0.6f);
            else if (pip != 0) EventSheet.pip(ui, x, y + 1, pip, pipFilled);
            if (!tail.isEmpty()) {
                String t = readable(tail);
                ui.textShadow(t, right - ui.width(t), y, Modern.LABEL);
                right -= TAIL;
            }
            if (chip != null && chipAt == RIGHT) {
                int cw = ui.width(chip) + 6;
                Modern.chip(ui, right - cw, y, chip, chipColor);
                right -= cw + 4;
            }
            if (!value.isEmpty()) {
                String v = readable(value);
                int vw = ui.width(v);
                ui.textShadow(v, right - vw, y, valueColor != 0 ? valueColor : p.value);
                right -= vw + GAP;
            }
            if (pipsMax > 0) {
                int px = right - pipsMax * (PIP + 1);
                for (int i = 0; i < pipsMax; i++) EventSheet.pip(ui, px + i * (PIP + 1), y + 1, pipsColor, i < pipsNow);
                right = px - 4;
            }
            int labelEnd = right;
            if (chip != null && chipAt != RIGHT) {
                int cx = chipAt >= 0 ? lx + chipAt : lx + ui.width(readable(label)) + 4;
                Modern.chip(ui, cx, y, chip, chipColor);
                labelEnd = cx - 2;
            }
            int lc = p.label != Modern.LABEL ? p.label : labelColor != 0 ? labelColor : Modern.LABEL;
            ui.textShadow(ui.ellipsize(readable(label), Math.max(8, labelEnd - lx)), lx, y, lc);
            if (progress >= 0) {
                int c = progress >= 1 && !alarm ? Modern.GOOD : barColor != 0 ? barColor : p.accent;
                Modern.meter(ui, x, y + p.rowH - 1, w, Modern.METER, progress, c);
                for (int i = 1; i < steps; i++) ui.rect(x + w * i / steps, y + p.rowH - 1, 1, Modern.METER, 0xFF050911);
            }
        }

        @Override
        void addRows(List<HudElement.Row> out) {
            out.add(new HudElement.Row(label, value, valueColor));
        }

        @Override
        boolean keep(ElementStyle st) {
            return !st.isHidden(label);
        }

        @Override
        void shorten(List<Block> out, List<Line> run, int room) {
            Line l = new Line(brief != null ? brief : Compact.label(label), briefValue != null ? briefValue : Compact.value(value), valueColor);
            l.labelColor = labelColor;
            l.pip = pip;
            l.pipFilled = pipFilled;
            l.indented = indented;
            l.icon = icon;
            l.chip = chip;
            l.chipColor = chipColor;
            l.chipAt = chipAt;
            l.tail = tail;
            l.pipsNow = pipsNow;
            l.pipsMax = pipsMax;
            l.pipsColor = pipsColor;
            l.progress = progress;
            l.barColor = barColor;
            l.steps = steps;
            l.alarm = alarm;
            run.add(l);
        }

        private int shortWidth(Ui ui) {
            int w = inset() + ui.width(readable(label));
            if (chip != null && chipAt >= 0) w = Math.max(w, inset() + chipAt);
            if (!value.isEmpty()) w += ui.width(readable(value)) + SHORT_GAP;
            if (!tail.isEmpty()) w += SHORT_TAIL;
            if (chip != null) w += ui.width(chip) + 6 + SHORT_GAP;
            return w + (pipsMax <= 0 ? 0 : pipsMax * (PIP + 1) + SHORT_GAP);
        }

        // next to another line it stands alone, doesnt hang under the one above
        private int pairWidth(Ui ui) {
            return shortWidth(ui) - (indented ? 12 : 0);
        }

        // compact version of draw: same pieces less air, meter right under the text
        private void drawShort(Ui ui, Paint p, int x, int y, int w) {
            int lx = x + inset(), right = x + w;
            if (icon != null && !icon.isEmpty()) ui.icon(icon, x - 1, y - 1, 0.6f);
            else if (pip != 0) EventSheet.pip(ui, x, y + 1, pip, pipFilled);
            if (!tail.isEmpty()) {
                String t = readable(tail);
                ui.textShadow(t, right - ui.width(t), y, Modern.LABEL);
                right -= SHORT_TAIL;
            }
            if (chip != null && chipAt == RIGHT) {
                int cw = ui.width(chip) + 6;
                Modern.chip(ui, right - cw, y, chip, chipColor);
                right -= cw + SHORT_GAP;
            }
            if (!value.isEmpty()) {
                String v = readable(value);
                int vw = ui.width(v);
                ui.textShadow(v, right - vw, y, valueColor != 0 ? valueColor : p.value);
                right -= vw + SHORT_GAP;
            }
            if (pipsMax > 0) {
                int px = right - pipsMax * (PIP + 1);
                for (int i = 0; i < pipsMax; i++) EventSheet.pip(ui, px + i * (PIP + 1), y + 1, pipsColor, i < pipsNow);
                right = px - SHORT_GAP;
            }
            int labelEnd = right;
            if (chip != null && chipAt != RIGHT) {
                int cx = chipAt >= 0 ? lx + chipAt : lx + ui.width(readable(label)) + SHORT_GAP;
                Modern.chip(ui, cx, y, chip, chipColor);
                labelEnd = cx - 2;
            }
            int lc = p.label != Modern.LABEL ? p.label : labelColor != 0 ? labelColor : Modern.LABEL;
            ui.textShadow(ui.ellipsize(readable(label), Math.max(8, labelEnd - lx)), lx, y, lc);
            if (progress >= 0) {
                int c = progress >= 1 && !alarm ? Modern.GOOD : barColor != 0 ? barColor : p.accent;
                Modern.meter(ui, x, y + p.rowH, w, Modern.METER, progress, c);
                for (int i = 1; i < steps; i++) ui.rect(x + w * i / steps, y + p.rowH, 1, Modern.METER, 0xFF050911);
            }
        }
    }

    // what shares a row in a compact card: one line, two, or the tiles that fit. even widths if possible, else
    // each gets what it needs
    private static final class ShortRow extends Block {
        private final List<Line> lines;
        private final int room, gap;

        ShortRow(List<Line> lines, int room) {
            this.lines = lines;
            this.room = room;
            gap = lines.size() > 2 ? 3 : Compact.GAP;
            if (lines.size() > 1) for (Line l : lines) l.indented = false;
        }

        @Override
        int height(Paint p) {
            for (Line l : lines) if (l.progress >= 0) return p.rowH + Compact.METER_BLOCK;
            return p.rowH;
        }

        @Override
        int width(Ui ui) {
            int sum = (lines.size() - 1) * gap, widest = 0;
            for (Line l : lines) {
                int w = l.shortWidth(ui);
                sum += w;
                widest = Math.max(widest, w);
            }
            int even = lines.size() * widest + (lines.size() - 1) * gap;
            return even <= room ? even : sum;
        }

        @Override
        void draw(Ui ui, Paint p, int x, int y, int w) {
            int n = lines.size(), free = w - (n - 1) * gap, each = free / n, widest = 0;
            for (Line l : lines) {
                int lw = l.shortWidth(ui);
                free -= lw;
                widest = Math.max(widest, lw);
            }
            int cx = x;
            for (int i = 0; i < n; i++) {
                Line l = lines.get(i);
                int cw = i == n - 1 ? x + w - cx : widest <= each ? each : l.shortWidth(ui) + free / n;
                l.drawShort(ui, p, cx, y, cw);
                if (i < n - 1) ui.rect(cx + cw + gap / 2, y, 1, 8, HAIRLINE);
                cx += cw + gap;
            }
        }
    }

    // --- one big number ---

    public static final class Hero extends Block {
        private final String label, value;
        private final int color;
        private String chip, brief;
        private int chipColor;

        private Hero(String label, String value, int color) {
            this.label = label;
            this.value = value;
            this.color = color;
        }

        public Hero chip(String text, int c) {
            chip = text;
            chipColor = c;
            return this;
        }

        // label in a compact card, there the big number is a line like the others
        public Hero brief(String shortLabel) {
            brief = shortLabel;
            return this;
        }

        // on the font's pixel grid: one font pixel = whole number of screen pixels at reading size
        private static float scale() {
            float info = HudLayout.infoScale();
            return info >= 1f ? 1f : 1f / info;
        }

        @Override
        int height(Paint p) {
            return HERO;
        }

        @Override
        int width(Ui ui) {
            return ui.width(label) + 8 + Math.round(ui.width(value) * scale()) + (chip == null ? 0 : ui.width(chip) + 10);
        }

        @Override
        void draw(Ui ui, Paint p, int x, int y, int w) {
            float s = scale();
            int vx = x + w - Math.round(ui.width(value) * s);
            ui.textScaled(value, vx, y + (s > 1f ? 0 : 2), s, color | 0xFF000000, true);
            ui.textShadow(ui.ellipsize(label, Math.max(8, vx - x - 4)), x, y + 3, p.label);
            if (chip != null) Modern.chip(ui, x + ui.width(label) + 4, y + 3, chip, chipColor);
        }

        @Override
        void addRows(List<HudElement.Row> out) {
            out.add(new HudElement.Row(label, value, color));
        }

        @Override
        boolean keep(ElementStyle st) {
            return !st.isHidden(label);
        }

        @Override
        void shorten(List<Block> out, List<Line> run, int room) {
            Line l = new Line(brief != null ? brief : label, Compact.value(value), color);
            if (chip != null) l.chip(chip, chipColor);
            run.add(l);
        }
    }

    // --- number tiles ---

    // name over number. brief = name in a compact card, or null
    public record Tile(String label, String value, int color, String brief) {}

    private static final class Tiles extends Block {
        private final List<Tile> cells = new ArrayList<>();

        @Override
        int height(Paint p) {
            return TILE;
        }

        @Override
        int width(Ui ui) {
            int w = 0;
            for (Tile t : cells) w = Math.max(w, Math.max(ui.width(t.label), ui.width(t.value)));
            return cells.size() * (w + 6) + (cells.size() - 1) * 2;
        }

        @Override
        void draw(Ui ui, Paint p, int x, int y, int w) {
            int n = cells.size(), room = w - 2 * (n - 1), each = room / n, extra = room - each * n, cx = x;
            int tint = p.accent | 0xFF000000;
            for (int i = 0; i < n; i++) {
                Tile t = cells.get(i);
                int cw = each + (i < extra ? 1 : 0);
                ui.rect(cx, y, cw, TILE - 2, Ui.alpha(tint, 0.09f));
                ui.rect(cx, y, cw, 1, Ui.alpha(tint, 0.4f));
                String label = ui.ellipsize(t.label, cw - 2), value = ui.ellipsize(readable(t.value), cw - 2);
                ui.textShadow(label, cx + (cw - ui.width(label)) / 2, y + 2, p.label);
                ui.textShadow(value, cx + (cw - ui.width(value)) / 2, y + 11, t.color != 0 ? t.color : p.value);
                cx += cw + 2;
            }
        }

        @Override
        void addRows(List<HudElement.Row> out) {
            for (Tile t : cells) out.add(new HudElement.Row(t.label, t.value, t.color));
        }

        @Override
        boolean keep(ElementStyle st) {
            cells.removeIf(t -> st.isHidden(t.label));
            return !cells.isEmpty();
        }

        // compact: tiles become one row of name + number, or as many rows as needed
        @Override
        void shorten(List<Block> out, List<Line> run, int room) {
            flush(out, run, room);
            Ui ui = Ui.MEASURE;
            List<Line> row = new ArrayList<>();
            int used = 0;
            for (Tile t : cells) {
                Line l = new Line(t.brief != null ? t.brief : t.label, Compact.value(t.value), t.color);
                int w = l.shortWidth(ui);
                if (!row.isEmpty() && used + 3 + w > room) {
                    out.add(new ShortRow(row, room));
                    row = new ArrayList<>();
                    used = 0;
                }
                used += (row.isEmpty() ? 0 : 3) + w;
                row.add(l);
            }
            if (!row.isEmpty()) out.add(new ShortRow(row, room));
        }
    }

    // --- two columns ---

    private record Cell(String label, String value, int color, int labelColor, int pip, String tail, String brief) {}

    // short pairs side by side, left to right
    public static final class Grid extends Block {
        private final List<Cell> cells = new ArrayList<>();

        public Grid cell(String label, String value, int color) {
            cells.add(new Cell(label, value, color, 0, 0, "", null));
            return this;
        }

        // cell whose name carries the colour, like a meal or a rarity
        public Grid tinted(String label, String value, int color, int nameColor) {
            cells.add(new Cell(label, value, color, nameColor, 0, "", null));
            return this;
        }

        // same with a share in its own column
        public Grid tinted(String label, String value, int color, int nameColor, String share) {
            cells.add(new Cell(label, value, color, nameColor, 0, share, null));
            return this;
        }

        // colour key: pip + what it means
        public Grid key(String label, int color) {
            cells.add(new Cell(label, "", 0, 0, color, "", null));
            return this;
        }

        // last cell's name in a compact card
        public Grid brief(String shortLabel) {
            Cell c = cells.get(cells.size() - 1);
            cells.set(cells.size() - 1, new Cell(c.label, c.value, c.color, c.labelColor, c.pip, c.tail, shortLabel));
            return this;
        }

        private int rows() {
            return (cells.size() + 1) / 2;
        }

        @Override
        int height(Paint p) {
            return rows() * p.rowH;
        }

        @Override
        int width(Ui ui) {
            int w = 0;
            for (Cell c : cells) w = Math.max(w, ui.width(c.label) + (c.value.isEmpty() ? 0 : ui.width(c.value) + GAP) + (c.pip != 0 ? 8 : 0) + (c.tail.isEmpty() ? 0 : TAIL));
            return 2 * w + 2 * GAP + 1;
        }

        @Override
        void draw(Ui ui, Paint p, int x, int y, int w) {
            int each = (w - 2 * GAP - 1) / 2;
            if (cells.size() > 1) ui.rect(x + each + GAP, y, 1, rows() * p.rowH - 2, RULE);
            for (int i = 0; i < cells.size(); i++) {
                Cell c = cells.get(i);
                int cx = x + (i % 2 == 0 ? 0 : each + 2 * GAP + 1), cy = y + (i / 2) * p.rowH, room = each;
                if (c.pip != 0) {
                    pip(ui, cx, cy + 1, c.pip, true);
                    cx += 8;
                    room -= 8;
                }
                if (!c.tail.isEmpty()) {
                    ui.textShadow(c.tail, cx + room - ui.width(c.tail), cy, Modern.LABEL);
                    room -= TAIL;
                }
                String v = readable(c.value);
                int vw = ui.width(v);
                if (vw > 0) ui.textShadow(v, cx + room - vw, cy, c.color != 0 ? c.color : p.value);
                int lc = p.label != Modern.LABEL ? p.label : c.labelColor != 0 ? c.labelColor : Modern.LABEL;
                ui.textShadow(ui.ellipsize(readable(c.label), Math.max(8, room - vw - (vw > 0 ? 4 : 0))), cx, cy, lc);
            }
        }

        @Override
        void addRows(List<HudElement.Row> out) {
            for (Cell c : cells) out.add(new HudElement.Row(c.label, c.value, c.color));
        }

        @Override
        boolean keep(ElementStyle st) {
            cells.removeIf(c -> st.isHidden(c.label));
            return !cells.isEmpty();
        }

        @Override
        void shorten(List<Block> out, List<Line> run, int room) {
            for (Cell c : cells) {
                Line l = new Line(c.brief != null ? c.brief : Compact.label(c.label), Compact.value(c.value), c.color);
                l.labelColor = c.labelColor;
                l.pip = c.pip;
                l.pipFilled = true;
                l.tail = c.tail;
                run.add(l);
            }
        }
    }

    // --- structure ---

    public static final class Section extends Block {
        private final String title, right;
        private String brief;
        private boolean inLine;

        Section(String title, String right) {
            this.title = title;
            this.right = right == null ? "" : right;
        }

        // title in a compact card
        public Section brief(String shortTitle) {
            brief = shortTitle;
            return this;
        }

        // compact card has no row for it, the one line under it says the name
        public Section inLine() {
            inLine = true;
            return this;
        }

        @Override
        void shorten(List<Block> out, List<Line> run, int room) {
            flush(out, run, room);
            if (!inLine) out.add(new Section(brief != null ? brief : title, Compact.value(right)));
        }

        @Override
        int height(Paint p) {
            return p.rowH;
        }

        @Override
        int width(Ui ui) {
            return ui.width(title) + (right.isEmpty() ? 12 : ui.width(right) + 16);
        }

        @Override
        void draw(Ui ui, Paint p, int x, int y, int w) {
            int ty = y, rw = right.isEmpty() ? 0 : ui.width(readable(right));
            Modern.section(ui, title, x, ty, w - (rw > 0 ? rw + 4 : 0), p.accent);
            if (rw > 0) ui.textShadow(readable(right), x + w - rw, ty, Modern.INK);
        }
    }

    private static final class Rule extends Block {
        @Override
        int height(Paint p) {
            return Compact.RULE_BLOCK;
        }

        @Override
        int width(Ui ui) {
            return 0;
        }

        @Override
        void draw(Ui ui, Paint p, int x, int y, int w) {
        }
    }

    // one share of a whole
    public record Share(double amount, int color) {}

    // bar split into shares in their colours
    private static final class Stack extends Block {
        private final List<Share> shares;

        Stack(List<Share> shares) {
            this.shares = shares;
        }

        @Override
        int height(Paint p) {
            return Compact.THICK_BLOCK;
        }

        @Override
        int width(Ui ui) {
            return 0;
        }

        @Override
        void draw(Ui ui, Paint p, int x, int y, int w) {
            double total = 0;
            int thick = Compact.THICK;
            for (Share s : shares) total += Math.max(0, s.amount);
            ui.rect(x, y, w, thick, GROOVE);
            if (total <= 0) return;
            double done = 0;
            int at = x;
            for (int i = 0; i < shares.size(); i++) {
                Share s = shares.get(i);
                if (s.amount <= 0) continue;
                done += s.amount;
                int end = x + (int) Math.round(w * done / total);
                // a share too small for a pixel still shows
                if (end <= at) end = Math.min(x + w, at + 1);
                int c = s.color | 0xFF000000, sw = end - at;
                ui.rect(at, y, sw, thick, Modern.darken(c, 0.12f));
                ui.rect(at, y, sw, 1, Modern.lighten(c, 0.35f));
                if (end < x + w && sw > 1) ui.rect(end - 1, y, 1, thick, Ui.alpha(0xFF000000, 0.45f));
                at = end;
            }
        }
    }

    // --- building ---

    public Line line(String label, String value, int color) {
        Line l = new Line(label, value, color);
        blocks.add(l);
        return l;
    }

    public Line line(String label, String value) {
        return line(label, value, 0);
    }

    public Hero hero(String label, String value, int color) {
        Hero h = new Hero(label, value, color);
        blocks.add(h);
        return h;
    }

    public static Tile tile(String label, String value, int color) {
        return new Tile(label, value, color, null);
    }

    // tile with a shorter name in a compact card
    public static Tile tile(String label, String value, int color, String brief) {
        return new Tile(label, value, color, brief);
    }

    public void tiles(Tile... tiles) {
        Tiles t = new Tiles();
        t.cells.addAll(List.of(tiles));
        blocks.add(t);
    }

    public Grid grid() {
        Grid g = new Grid();
        blocks.add(g);
        return g;
    }

    public Section section(String title) {
        return section(title, "");
    }

    public Section section(String title, String right) {
        Section s = new Section(title, right);
        blocks.add(s);
        return s;
    }

    public void rule() {
        blocks.add(new Rule());
    }

    public void stack(List<Share> shares) {
        blocks.add(new Stack(shares));
    }

    // "Key: a/b" like its written elsewhere: label, value, percent and meter
    public Line shaped(String text) {
        RowText.Shaped r = RowText.shape(text, "");
        Line l = line(RowText.plain(r.label()).trim(), r.value());
        if (r.progress() >= 0) l.meter(r.progress());
        return l;
    }

    // "1,234/5,678  21.7%", the two numbers in their colours
    public static String ratio(String now, String of, double fraction, char nowCode, char ofCode) {
        return Modern.fraction(now, of, nowCode, ofCode) + "  §7" + RowText.percent(Math.max(0, Math.min(1, fraction)));
    }

    // --- what the card does with it ---

    private List<HudElement.Row> rows() {
        List<HudElement.Row> out = new ArrayList<>();
        for (Block b : blocks) b.addRows(out);
        return out;
    }

    // blocks left after hiding. a heading or a line with nothing under it goes too
    private List<Block> shown(ElementStyle st) {
        List<Block> kept = new ArrayList<>();
        for (Block b : blocks) if (b.keep(st)) kept.add(b);
        List<Block> out = new ArrayList<>();
        for (int i = 0; i < kept.size(); i++) {
            Block b = kept.get(i);
            if (b instanceof Section || b instanceof Stack) {
                boolean content = false;
                for (int j = i + 1; j < kept.size() && !(kept.get(j) instanceof Section) && !(kept.get(j) instanceof Rule); j++) {
                    if (!(kept.get(j) instanceof Stack)) content = true;
                }
                if (!content) continue;
            }
            if (b instanceof Rule) {
                boolean next = i + 1 < kept.size() && !(kept.get(i + 1) instanceof Section) && !(kept.get(i + 1) instanceof Rule);
                if (out.isEmpty() || !next) continue;
            }
            out.add(b);
        }
        return out;
    }

    // a card drawn from a sheet
    public abstract static class Card extends HudElement {
        protected Card(String id, String title, String section, Zone zone, int priority, String featureId) {
            super(id, title, section, zone, priority, featureId);
        }

        protected abstract void live(EventSheet s);

        // realistic numbers, what the editor and pictures show
        protected abstract void sample(EventSheet s);

        protected abstract List<Row> plainRows();

        protected abstract List<Row> plainDemo();

        private EventSheet live() {
            EventSheet s = new EventSheet();
            live(s);
            return s;
        }

        private EventSheet sample() {
            EventSheet s = new EventSheet();
            sample(s);
            return s;
        }

        private EventSheet sheet() {
            return demo ? sample() : live();
        }

        @Override
        public final List<Row> rows() {
            return live().rows();
        }

        @Override
        public final List<Row> demo() {
            return sample().rows();
        }

        @Override
        public String headingValue() {
            return sheet().right;
        }

        private Paint paint(Theme t, ElementStyle st) {
            return new Paint(st.titleColor() != null ? st.titleColor() : accent(), st.textColor() != null ? st.textColor() : Modern.LABEL,
                    ElementStyle.or(st.valueColor(), Modern.INK), Math.min(rowH(t, st), Compact.ROW_H));
        }

        // compact card blocks: nothing left out, short labels/numbers, neighbours share rows
        private List<Block> packed(EventSheet s, Theme t, ElementStyle st) {
            int room = Compact.WIDE - 2 * pad(t, st) - (titleShown(st) ? 0 : icon().isEmpty() ? 2 : 14);
            List<Block> out = new ArrayList<>();
            List<Line> run = new ArrayList<>();
            for (Block b : s.shown(st)) b.shorten(out, run, room);
            flush(out, run, room);
            return out;
        }

        @Override
        public int width() {
            Theme t = Theme.current();
            ElementStyle st = style();
            Ui ui = Ui.MEASURE;
            EventSheet s = sheet();
            boolean titled = titleShown(st);
            int w = titled ? 16 + ui.width(heading()) + 6 + (s.right.isEmpty() ? 0 : ui.width(s.right) + 8) : 30;
            for (Block b : packed(s, t, st)) w = Math.max(w, b.width(ui) + 2 * pad(t, st) + (titled ? 0 : icon().isEmpty() ? 2 : 14));
            return Math.min(236, Math.max(titled ? 80 : 40, w));
        }

        @Override
        public int height() {
            Theme t = Theme.current();
            ElementStyle st = style();
            Paint p = paint(t, st);
            int mh = titleShown(st) ? Modern.BAND + 3 : 5;
            for (Block b : packed(sheet(), t, st)) mh += b.height(p);
            return mh;
        }

        @Override
        public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
            Theme t = Theme.current();
            ElementStyle st = style();
            Paint p = paint(t, st);
            EventSheet s = sheet();
            List<Block> blocks = packed(s, t, st);
            int w = drawWidth(), pad = pad(t, st), h = titleShown(st) ? Modern.BAND + 3 : 5;
            for (Block b : blocks) h += b.height(p);
            t.panel(ui, x, y, w, h, st);
            int cx = x + pad, cy, cw = w - 2 * pad;
            if (titleShown(st)) {
                cy = y + Modern.band(ui, heading(), icon(), s.right, p.accent, x, y, w);
            } else {
                Modern.edge(ui, x, y, h, p.accent);
                cy = y + 3;
                if (!icon().isEmpty()) {
                    ui.icon(icon(), x + 4, y + (h - Modern.ICON) / 2, Modern.ICON_SCALE);
                    cx += 12;
                    cw -= 12;
                }
            }
            for (Block b : blocks) {
                b.draw(ui, p, cx, cy, cw);
                cy += b.height(p);
            }
        }
    }
}
