// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.garden.milestones;

import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.hud.Compact;
import dev.zmuzy.sbc.ui.hud.ElementStyle;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.theme.ClassicTheme;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

// a garden card: title band with a value + state chip on the right, then a list of blocks for what the card is about
// the hud editor still reads rows()
public abstract class GardenCard extends HudElement {
    // sizes and colours from the style, as far as a block needs them
    public record Look(int rowH, int label, int value, int accent) {}

    public record Chip(String text, int color) {
        int width(Ui ui) {
            return ui.width(text) + 6;
        }
    }

    // one piece of a card
    public interface Block {
        int height(int rowH);

        int width(Ui ui);

        void draw(Ui ui, int x, int y, int w, Look look);
    }

    protected GardenCard(String id, String title, Zone zone, int priority, String feature) {
        super(id, title, "Garden", zone, priority, feature);
    }

    // what the card shows rn, live or demo
    protected abstract List<Block> blocks();

    // state tag at the right end of the band, null = none
    protected Chip chip() {
        return null;
    }

    // text just left of the chip in the band
    protected String bandValue() {
        return "";
    }

    // not hidden, and not said by a section drawn in this card
    protected final boolean shown(String rowLabel) {
        return !style().isHidden(rowLabel) && !coveredBySection(rowLabel);
    }

    // lines of a ported display that werent hidden. the raw line is the row label there
    protected final List<String> shownLines(List<String> lines) {
        return lines.stream().filter(this::shown).toList();
    }

    // last read. tab and sidebar change a few times a sec, frames come way more often
    private record Built(List<Block> blocks, Chip chip, String value, boolean demo, boolean compact, long at) {}

    private Built built;

    private Built built() {
        long now = System.currentTimeMillis();
        boolean compact = compact();
        if (built == null || built.demo() != demo || built.compact() != compact || now - built.at() > 100)
            built = new Built(compact ? packed(blocks()) : blocks(), chip(), bandValue(), demo, compact, now);
        return built;
    }

    // short form blocks: nothing left out, two short neighbours share a row
    private List<Block> packed(List<Block> blocks) {
        List<Block> brief = new ArrayList<>(blocks.size());
        for (Block b : blocks) {
            if (b instanceof Line l) brief.add(l.compacted());
            else if (b instanceof Rule) brief.add(GAP_BLOCK);
            else if (b instanceof Pair p) brief.add(new TightPair(p.left().compacted(), p.right().compacted()));
            else if (b instanceof Beside s) {
                List<Block> lines = new ArrayList<>(s.lines().size());
                for (Block in : s.lines()) lines.add(in instanceof Line l ? l.compacted() : in instanceof Rule ? GAP_BLOCK : in);
                brief.add(new Beside(s.side(), lines));
            } else brief.add(b);
        }
        ElementStyle st = style();
        int room = Compact.WIDE - 2 * pad(Theme.current(), st) - (titleShown(st) ? 0 : !gutterIcon() ? 2 : 14);
        List<Block> out = new ArrayList<>(brief.size());
        for (List<Block> row : Compact.rows(brief, b -> b instanceof Line l && l.mayPair(), b -> b.width(Ui.MEASURE), room))
            out.add(row.size() == 2 ? new TightPair((Line) row.get(0), (Line) row.get(1)) : row.get(0));
        return out;
    }

    private List<Block> content(boolean titled) {
        Built b = built();
        List<Block> blocks = b.blocks();
        Chip chip = b.chip();
        String value = b.value();
        if (titled || (chip == null && value.isEmpty())) return blocks;
        // no band to carry them, they get their own line
        List<Block> out = new ArrayList<>(blocks.size() + 1);
        out.add(new Line("").chip(chip).value(value));
        out.addAll(blocks);
        return out;
    }

    @Override
    public int width() {
        Theme t = Theme.current();
        ElementStyle st = style();
        Ui ui = Ui.MEASURE;
        boolean titled = titleShown(st);
        int w = 30;
        if (titled) {
            Chip chip = built().chip();
            String value = built().value();
            w = 14 + ui.width(heading()) + 6 + (chip == null ? 0 : chip.width(ui) + 4) + (value.isEmpty() ? 0 : ui.width(value) + 4);
        }
        int extra = 2 * pad(t, st) + (titled ? 0 : !gutterIcon() ? 2 : 14);
        for (Block b : content(titled)) w = Math.max(w, b.width(ui) + extra);
        return Math.min(236, Math.max(titled ? 100 : 40, w));
    }

    @Override
    public int height() {
        Theme t = Theme.current();
        ElementStyle st = style();
        boolean titled = titleShown(st);
        int rh = Math.min(rowH(t, st), Compact.ROW_H), h = titled ? headH() : 5;
        for (Block b : content(titled)) h += b.height(rh);
        return h;
    }

    @Override
    public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
        Theme t = Theme.current();
        ElementStyle st = style();
        boolean titled = titleShown(st);
        int w = drawWidth(), h = height(), pad = pad(t, st), rh = Math.min(rowH(t, st), Compact.ROW_H);
        int accent = st.titleColor() != null ? st.titleColor() : accent();
        cardPanel(t, ui, x, y, w, h, st);
        int cx = x + pad, cw = w - 2 * pad, cy;
        if (titled) {
            cy = y + cardBand(ui, heading(), icon(), "", accent, x, y, w);
            int right = x + w - Modern.PAD;
            Chip chip = built().chip();
            if (chip != null) {
                right -= chip.width(ui);
                Modern.chip(ui, right, y + (asSection ? 1 : 3), chip.text(), chip.color());
                right -= 4;
            }
            String value = built().value();
            if (!value.isEmpty()) ui.textShadow(value, right - ui.width(value), y + (asSection ? 1 : 3), Modern.INK);
        } else {
            cardEdge(ui, x, y, h, accent);
            cy = y + 3;
            if (gutterIcon()) {
                ui.icon(icon(), x + 4, y + (h - Modern.ICON) / 2, Modern.ICON_SCALE);
                cx += 12;
                cw -= 12;
            }
        }
        Look look = new Look(rh, st.textColor() != null ? st.textColor() : Modern.LABEL, ElementStyle.or(st.valueColor(), Modern.INK), accent);
        for (Block b : content(titled)) {
            b.draw(ui, cx, cy, cw, look);
            cy += b.height(rh);
        }
    }

    // --- blocks ---

    // label left, value right, plus whatever the line needs: rank, icon, chip, meter, data bar
    public static final class Line implements Block {
        private final String label;
        private String value = "", rank, brief;
        private boolean compact, pairs;
        private int labelColor, valueColor, rankColor, rankColumn, barColor, shareColor;
        private ItemStack icon;
        private final List<Chip> chips = new ArrayList<>(1);
        private List<ItemStack> tail = List.of();
        private double progress = -1, share = -1;

        public Line(String label) {
            this.label = label;
        }

        // label in a compact card when the shared short forms dont cover it
        public Line brief(String shortLabel) {
            brief = shortLabel;
            return this;
        }

        // in a compact card it can share a row even with a chip, end icons or no value, a name is what it says
        public Line pairs() {
            pairs = true;
            return this;
        }

        // width in a compact card
        public int compactWidth(Ui ui) {
            return compacted().width(ui);
        }

        // compact version: same value, fewer letters
        private Line compacted() {
            Line l = new Line(brief != null ? brief : Compact.label(label));
            l.value = Compact.value(value);
            l.pairs = pairs;
            l.rank = rank;
            l.labelColor = labelColor;
            l.valueColor = valueColor;
            l.rankColor = rankColor;
            l.rankColumn = rankColumn;
            l.barColor = barColor;
            l.shareColor = shareColor;
            l.icon = icon;
            l.chips.addAll(chips);
            l.tail = tail;
            l.progress = progress;
            l.share = share;
            l.compact = true;
            return l;
        }

        private boolean mayPair() {
            return progress < 0 && share < 0 && rank == null && (pairs || chips.isEmpty() && tail.isEmpty() && !value.isEmpty());
        }

        public Line value(String v) {
            value = v == null ? "" : v;
            return this;
        }

        public Line value(String v, int color) {
            valueColor = color;
            return value(v);
        }

        public Line labelColor(int c) {
            labelColor = c;
            return this;
        }

        public Line icon(ItemStack st) {
            icon = st;
            return this;
        }

        public Line icon(String modernId) {
            icon = dev.zmuzy.sbc.core.LegacyItems.stack(modernId);
            return this;
        }

        // rank in its own column, same width on every line of the ranking
        public Line rank(String r, int color, int column) {
            rank = r;
            rankColor = color;
            rankColumn = column;
            return this;
        }

        // state tag after the label, null = none
        public Line chip(Chip c) {
            if (c != null) chips.add(c);
            return this;
        }

        public Line chip(String text, int color) {
            return chip(new Chip(text, color));
        }

        private int chipsWidth(Ui ui) {
            int w = 0;
            for (Chip c : chips) w += c.width(ui) + 3;
            return w;
        }

        // small icons at the right end
        public Line tail(List<ItemStack> icons) {
            tail = icons;
            return this;
        }

        // meter under the line. color 0 = value's colour, else the card's
        public Line meter(double p, int color) {
            progress = p;
            barColor = color;
            return this;
        }

        // bar behind the line to compare ranking lines, color 0 = card's
        public Line share(double s, int color) {
            share = s;
            shareColor = color;
            return this;
        }

        private int lead() {
            return (rank == null ? 0 : rankColumn) + (icon == null || icon.isEmpty() ? 0 : 12);
        }

        @Override
        public int height(int rowH) {
            return rowH + (progress < 0 ? 0 : Compact.METER_BLOCK);
        }

        @Override
        public int width(Ui ui) {
            return lead() + ui.width(label) + chipsWidth(ui) + (value.isEmpty() ? 0 : ui.width(value) + 8) + (tail.isEmpty() ? 0 : tail.size() * 11 + 2);
        }

        @Override
        public void draw(Ui ui, int x, int y, int w, Look look) {
            if (share > 0) {
                int f = Math.max(1, (int) Math.round(w * Math.min(1, share)));
                int c = (shareColor != 0 ? shareColor : look.accent()) | 0xFF000000;
                ui.gradH(x - 1, y - 1, f + 1, 9, Ui.alpha(c, 0.26f), Ui.alpha(c, 0.08f));
            }
            int lx = x + lead();
            if (rank != null) ui.textShadow(rank, x, y, rankColor);
            if (icon != null && !icon.isEmpty()) ui.icon(icon, lx - 12 - 1, y - 1, 0.6f);
            int right = x + w;
            for (ItemStack st : tail) {
                right -= 11;
                ui.icon(st, right, y - 1, 0.6f);
            }
            if (!tail.isEmpty()) right -= 2;
            String v = ClassicTheme.readable(value);
            int vw = v.isEmpty() ? 0 : ui.width(v);
            String l = ClassicTheme.readable(label);
            if (!l.isEmpty()) {
                l = ui.ellipsize(l, Math.max(8, right - lx - (vw == 0 ? 0 : vw + 6) - chipsWidth(ui)));
                ui.textShadow(l, lx, y, labelColor != 0 ? labelColor : look.label());
            }
            int chipX = lx + (l.isEmpty() ? 0 : ui.width(l) + 3);
            for (Chip c : chips) chipX += Modern.chip(ui, chipX, y, c.text(), c.color()) + 3;
            if (vw > 0) ui.textShadow(v, right - vw, y, valueColor != 0 ? valueColor : look.value());
            if (progress >= 0) {
                int c = barColor != 0 ? barColor : valueColor != 0 ? valueColor : look.accent();
                Modern.meter(ui, x, y + look.rowH(), w, Modern.METER, progress, c);
            }
        }
    }

    // a hairline in a compact card becomes a bit of air
    private static final Block GAP_BLOCK = new Block() {
        @Override
        public int height(int rowH) {
            return Compact.RULE_BLOCK;
        }

        @Override
        public int width(Ui ui) {
            return 0;
        }

        @Override
        public void draw(Ui ui, int x, int y, int w, Look look) {
        }
    };

    // two lines on one compact row
    private record TightPair(Line left, Line right) implements Block {
        @Override
        public int height(int rowH) {
            return Math.max(left.height(rowH), right.height(rowH));
        }

        @Override
        public int width(Ui ui) {
            return 2 * Math.max(left.width(ui), right.width(ui)) + Compact.GAP;
        }

        @Override
        public void draw(Ui ui, int x, int y, int w, Look look) {
            int half = (w - Compact.GAP) / 2;
            left.draw(ui, x, y, half, look);
            ui.rect(x + half + Compact.GAP / 2, y, 1, 8, 0x30FFFFFF);
            right.draw(ui, x + half + Compact.GAP, y, w - half - Compact.GAP, look);
        }
    }

    // hairline with a bit of air around it
    public static final class Rule implements Block {
        @Override
        public int height(int rowH) {
            return Modern.RULE_BLOCK;
        }

        @Override
        public int width(Ui ui) {
            return 0;
        }

        @Override
        public void draw(Ui ui, int x, int y, int w, Look look) {
            Modern.rule(ui, x, y + 2, w);
        }
    }

    // two lines on one row, thin divider so each value stays with its label
    public record Pair(Line left, Line right) implements Block {
        private static final int GAP = 14;

        @Override
        public int height(int rowH) {
            return Math.max(left.height(rowH), right.height(rowH));
        }

        @Override
        public int width(Ui ui) {
            return 2 * Math.max(left.width(ui), right.width(ui)) + GAP;
        }

        @Override
        public void draw(Ui ui, int x, int y, int w, Look look) {
            int half = (w - GAP) / 2;
            left.draw(ui, x, y, half, look);
            ui.rect(x + half + GAP / 2, y, 1, 8, 0x30FFFFFF);
            right.draw(ui, x + half + GAP, y, w - half - GAP, look);
        }
    }

    // something fixed size with lines next to it in the width left
    public record Beside(Block side, List<Block> lines) implements Block {
        public static final int GAP = 6;

        private int linesHeight(int rowH) {
            int h = 0;
            for (Block b : lines) h += b.height(rowH);
            return h;
        }

        @Override
        public int height(int rowH) {
            return Math.max(side.height(rowH), linesHeight(rowH));
        }

        @Override
        public int width(Ui ui) {
            int w = 0;
            for (Block b : lines) w = Math.max(w, b.width(ui));
            return side.width(ui) + GAP + w;
        }

        @Override
        public void draw(Ui ui, int x, int y, int w, Look look) {
            int sw = side.width(ui);
            side.draw(ui, x, y, sw, look);
            int cy = y;
            for (Block b : lines) {
                b.draw(ui, x + sw + GAP, cy, w - sw - GAP, look);
                cy += b.height(look.rowH());
            }
        }
    }
}
