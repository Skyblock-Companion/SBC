// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui.hud;

import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.List;

// one thing on the hud. knows its corner, order, if it has stuff to show and how to draw through the theme.
// layout places it, the editor moves/scales/styles it
public abstract class HudElement {
    // one line: icon, label, value, value color (0 = theme text). progress 0..1 draws a meter under it in
    // barColor (0 = card color). "x/y" or "n%" lines get one automatically
    public record Row(ItemStack icon, String label, String value, int color, double progress, int barColor) {
        public Row(ItemStack icon, String label, String value, int color) {
            this(icon, label, value, color, -1, 0);
        }

        public Row(String label, String value, int color) {
            this(null, label, value, color, -1, 0);
        }

        public Row(String label) {
            this(null, label, "", 0, -1, 0);
        }

        public static Row meter(String label, String value, int color, double progress) {
            return new Row(null, label, value, color, progress, 0);
        }

        public static Row meter(String label, String value, int color, double progress, int barColor) {
            return new Row(null, label, value, color, progress, barColor);
        }
    }

    // these already say what they are, a heading would just repeat it
    private static final java.util.Set<String> SELF_TITLED = java.util.Set.of("crimson_volcano", "mining_cold_value", "mining_pity", "rift_ubik",
            "rift_timer", "rift_crux", "sack_display", "diana_warp_text", "hoppity_cf_stats");

    public final String id, title, section;
    public final Zone zone;
    public final int priority;
    private final String featureId;
    // editor sets this: show demo stuff instead of live
    public boolean demo;
    // layout sets this: column width, 0 = own
    public int forcedWidth;

    protected HudElement(String id, String title, String section, Zone zone, int priority, String featureId) {
        this.id = id;
        this.title = title;
        this.section = section;
        this.zone = zone;
        this.priority = priority;
        this.featureId = featureId;
    }

    // bigger element this is part of, or null. that one draws it until you move it out
    public HudElement partOf() {
        return null;
    }

    // where it sits inside that one, before scaling
    public int[] partAt() {
        return new int[2];
    }

    // where the game draws this, for stuff that only joins the layout once moved. null for the rest
    public int[] homeAt(int sw, int sh) {
        return null;
    }

    // what you can tick for this one, empty if it doesnt pick
    public List<String> pickable() {
        return List.of();
    }

    public boolean picked(String name) {
        return false;
    }

    public void pick(String name, boolean on) {
    }

    public Feature feature() {
        return featureId == null ? null : Features.get(featureId);
    }

    // on and in the right place (island, screen, state)
    public boolean active() {
        Feature f = feature();
        return f == null || f.active();
    }

    public abstract boolean visible();

    // "When needed" (only with content), "Always", or "Never" (hidden, feature stays on)
    public String showMode() {
        Feature f = feature();
        var o = f == null ? null : f.option("show");
        if (o == null) return "When needed";
        return o.isDefault() ? defaultShow() : o.choice();
    }

    public String defaultShow() {
        return "When needed";
    }

    // does "Always" make sense here (row ones can draw without data)
    public boolean supportsAlways() {
        return true;
    }

    // --- one card made of several ---
    // a host draws its members under its own lines, each with a slim heading
    // a member shows there while its host shows, alone when the host doesnt (off, hidden, nothing to say)
    // so nothing gets lost and nothing shows twice

    private record Member(HudElement card, boolean onlyAlone) {}

    private final List<Member> members = new java.util.ArrayList<>();
    private HudElement host;
    // drawn as a section of its host: no panel of its own, a heading line instead of the band
    protected boolean asSection;
    // set while a host draws, how tall its members are so the panel wraps them
    protected int below;
    public static final int SECTION_HEAD = 10;

    // cards whose lines go in this one, in this order
    public final HudElement absorb(HudElement... cards) {
        for (HudElement c : cards) {
            if (c == null || c == this) continue;
            c.leaveHost();
            members.add(new Member(c, false));
            c.host = this;
        }
        return this;
    }

    // a card that says the same kind of thing at another time (next contest before one starts)
    // never shows with this one, alone when this one has nothing
    public final HudElement absorbWhenAlone(HudElement card) {
        if (card != null && card != this) {
            card.leaveHost();
            members.add(new Member(card, true));
            card.host = this;
        }
        return this;
    }

    // leave the card that carried it (a re-registered card object leaves the old one behind)
    private void leaveHost() {
        if (host == null) return;
        host.members.removeIf(m -> m.card == this);
        host.sectionsAt = 0;
        host = null;
    }

    // pictures only: just these members are drawn in their hosts, to show the hud of one moment
    public static java.util.Set<String> picturesOnly;

    // drop every member, theyre their own cards again (before linking anew)
    public final void clearMembers() {
        for (Member m : members) m.card.host = null;
        members.clear();
        sectionsAt = 0;
    }

    public final HudElement host() {
        return host;
    }

    public final List<HudElement> members() {
        List<HudElement> out = new java.util.ArrayList<>(members.size());
        for (Member m : members) out.add(m.card);
        return out;
    }

    // its host takes it, so the editor shows its lines in the host
    // the editor shows every card thats switched on, so this goes by the switch not by where you are
    public final boolean folded() {
        return host != null && switchedOn(host) && !"Never".equals(host.showMode());
    }

    private static boolean switchedOn(HudElement e) {
        return e.feature() == null || e.feature().enabled();
    }

    // on and not hidden: its members are its business
    public final boolean takesMembers() {
        return active() && !"Never".equals(showMode());
    }

    private List<HudElement> sectionsMemo = List.of();
    private long sectionsAt;
    private boolean sectionsDemo;

    // members drawn under this card now: on, not hidden, with something to say (the editor: all of them)
    public final List<HudElement> sections() {
        if (members.isEmpty()) return List.of();
        long now = System.currentTimeMillis();
        if (sectionsAt != 0 && sectionsDemo == demo && now - sectionsAt < 100) return sectionsMemo;
        List<HudElement> out = new java.util.ArrayList<>();
        for (Member m : members) {
            HudElement c = m.card;
            // the editor shows what is switched on, the game what is on here and now
            if (m.onlyAlone || !(demo ? switchedOn(c) : c.active()) || "Never".equals(c.showMode())) continue;
            if (demo && picturesOnly != null && !picturesOnly.contains(c.id)) continue;
            c.demo = demo;
            boolean says;
            try {
                says = demo || ("Always".equals(c.showMode()) && c.supportsAlways()) || (c.visible() && !c.allHidden());
            } catch (Throwable t) {
                says = false;
            }
            if (says) out.add(c);
        }
        sectionsMemo = out;
        sectionsAt = now;
        sectionsDemo = demo;
        return out;
    }

    // is that member drawn here now, then our own rows for it go, no saying it twice
    protected final boolean carries(String memberId) {
        for (HudElement m : sections()) if (m.id.equals(memberId)) return true;
        return false;
    }

    // a row of this card that a member drawn in it says better (HudMerges.COVERS)
    protected final boolean coveredBySection(String label) {
        for (var e : HudMerges.covers(id).entrySet()) if (e.getValue().contains(label) && carries(e.getKey())) return true;
        return false;
    }

    // whole card + members stacked at one width, what the layout measures and draws
    // stays a normal cards width. too tall = split it in the merge table, not two columns
    private record Spot(HudElement card, int y) {}

    private record Arrangement(int width, int height, List<Spot> spots) {}

    private Arrangement arranged;
    private long arrangedAt;
    private boolean arrangedDemo;
    private int arrangedForced;

    private Arrangement arrangement() {
        long now = System.currentTimeMillis();
        if (arranged != null && arrangedDemo == demo && arrangedForced == forcedWidth && now - arrangedAt < 50) return arranged;
        List<HudElement> parts = sections();
        int w = width(), y = height();
        List<Spot> spots = new java.util.ArrayList<>();
        for (HudElement m : parts) {
            m.asSection = true;
            try {
                w = Math.max(w, m.width());
                spots.add(new Spot(m, y));
                y += m.height();
            } finally {
                m.asSection = false;
            }
        }
        Arrangement a = new Arrangement(Math.max(w, forcedWidth), y, spots);
        arranged = a;
        arrangedAt = now;
        arrangedDemo = demo;
        arrangedForced = forcedWidth;
        return a;
    }

    public final int wholeWidth() {
        return sections().isEmpty() ? width() : arrangement().width();
    }

    public final int wholeHeight() {
        return sections().isEmpty() ? height() : arrangement().height();
    }

    public final void drawWhole(Ui ui, GuiGraphicsExtractor g, int x, int y) {
        if (sections().isEmpty()) {
            draw(ui, g, x, y);
            return;
        }
        Arrangement a = arrangement();
        int forced = forcedWidth;
        forcedWidth = a.width();
        below = a.height() - height();
        try {
            draw(ui, g, x, y);
        } finally {
            below = 0;
            forcedWidth = forced;
        }
        for (Spot s : a.spots()) {
            HudElement m = s.card();
            m.asSection = true;
            int mf = m.forcedWidth;
            m.forcedWidth = a.width();
            try {
                m.draw(ui, g, x, y + s.y());
            } catch (Throwable t) {
                io.github.zmuzy.sbc.SbcMod.LOG.warn("HUD section {} in {} failed", m.id, id, t);
            } finally {
                m.asSection = false;
                m.forcedWidth = mf;
            }
        }
    }

    // the panel behind a card, down round its members. a section has none, its host drew it
    protected final void cardPanel(Theme t, Ui ui, int x, int y, int w, int h, ElementStyle st) {
        if (!asSection) t.panel(ui, x, y, w, h + below, st);
    }

    // coloured edge of a card without a title, the whole height
    protected final void cardEdge(Ui ui, int x, int y, int h, int accent) {
        if (!asSection) io.github.zmuzy.sbc.ui.theme.Modern.edge(ui, x, y, h + below, accent);
    }

    // the title band, or the slim heading in a section. hairline, icon + name in the cards colour, main number on the right
    // returns where the lines start
    protected final int cardBand(Ui ui, String title, ItemStack icon, String right, int accent, int x, int y, int w) {
        if (!asSection) return io.github.zmuzy.sbc.ui.theme.Modern.band(ui, title, icon, right, accent, x, y, w);
        // one row tall: the name's own line to the right is the divider
        int pad = io.github.zmuzy.sbc.ui.theme.Modern.PAD, tx = x + pad;
        if (icon != null && !icon.isEmpty()) {
            ui.icon(icon, tx - 1, y, 0.6f);
            tx += 11;
        }
        int rw = right == null || right.isEmpty() ? 0 : ui.width(right) + 4;
        io.github.zmuzy.sbc.ui.theme.Modern.section(ui, ui.ellipsize(title, Math.max(8, x + w - pad - rw - tx)), tx, y + 1, x + w - pad - rw - tx, accent);
        if (rw > 0) ui.textShadow(right, x + w - pad - rw + 4, y + 1, io.github.zmuzy.sbc.ui.theme.Modern.INK);
        return SECTION_HEAD;
    }

    // how tall the band + its air is, before the first line
    protected final int headH() {
        return asSection ? SECTION_HEAD : io.github.zmuzy.sbc.ui.theme.Modern.BAND + 3;
    }

    // a section shows no icon in a gutter: its heading carries it
    protected final boolean gutterIcon() {
        return !asSection && !icon().isEmpty();
    }

    // on, in place, and (unless always) something to show
    public boolean shows() {
        if (host != null && host.takesMembers() && host.shows()) return false;
        if (!active()) return false;
        String m = showMode();
        if (m.equals("Never")) return false;
        if (m.equals("Always") && supportsAlways()) return true;
        return visible() && (demo || !allHidden());
    }

    // all it has to say right now is rows you hid in the editor: an empty card would be left
    final boolean allHidden() {
        ElementStyle st = style();
        if (st.hiddenRows().isEmpty()) return false;
        List<Row> rows;
        try {
            rows = rows();
        } catch (Throwable t) {
            return false;
        }
        boolean any = false;
        for (Row r : rows) {
            if (spacer(r)) continue;
            any = true;
            if (!st.isHidden(r.label())) return false;
        }
        return any;
    }

    // default size: corner stuff is smaller than the hotbar bars
    public float defaultScale() {
        return switch (zone) {
            case TOP_LEFT, TOP_RIGHT, BOTTOM_RIGHT -> io.github.zmuzy.sbc.ui.HudLayout.infoScale();
            default -> 1f;
        };
    }

    public int accent() {
        return Looks.of(id).accent();
    }

    // empty = none
    public ItemStack icon() {
        return Looks.of(id).stack();
    }

    // do "x/y" / "n%" lines get a meter. numbers that arent progress turn it off
    protected boolean meters() {
        return true;
    }

    // can it take its columns width, everything drawn by draw() here can
    public boolean stretchable() {
        return true;
    }

    // right end of the title line
    public String headingValue() {
        return "";
    }

    protected final int drawWidth() {
        return forcedWidth > 0 ? forcedWidth : width();
    }

    // empty row = small gap, not a whole blank line
    protected static boolean spacer(Row r) {
        return r.icon() == null && r.value().isEmpty() && plain(r.label()).isEmpty();
    }

    protected static final int SPACER = 4;

    // empty = none
    public String heading() {
        return SELF_TITLED.contains(id) ? "" : title;
    }

    public List<Row> rows() {
        return List.of();
    }

    // sample rows for the editor when theres nothing live
    public List<Row> demo() {
        return List.of(new Row("Example", "123", 0));
    }

    private List<Row> shown() {
        return demo ? demo() : rows();
    }

    public ElementStyle style() {
        return ElementStyle.of(id);
    }

    protected int pad(Theme t, ElementStyle st) {
        return st.padding() == null ? t.pad() : st.padding();
    }

    protected int rowH(Theme t, ElementStyle st) {
        return st.rowH() == null ? t.rowH() : st.rowH();
    }

    protected boolean titleShown(ElementStyle st) {
        return !heading().isEmpty() && (st.showTitle() == null || st.showTitle());
    }

    // live or demo rows minus the ones you hid
    protected List<Row> visibleRows(ElementStyle st) {
        List<Row> src = withoutRepeatedHeading(titleShown(st) ? heading() : "", shown());
        if (st.hiddenRows().isEmpty()) return src;
        List<Row> out = new java.util.ArrayList<>();
        for (Row r : src) if (!st.isHidden(r.label())) out.add(r);
        return out;
    }

    // ported displays start with their own name ("Crop Milestones", "Reputation Helper" + a blank line...). under
    // a heading that says the same thing it gets dropped
    static List<Row> withoutRepeatedHeading(String heading, List<Row> rows) {
        if (heading.isEmpty() || rows.isEmpty() || !rows.get(0).value().isEmpty()) return rows;
        String first = plain(rows.get(0).label()).toLowerCase(java.util.Locale.ROOT), h = heading.toLowerCase(java.util.Locale.ROOT);
        if (!first.startsWith(h)) return rows;
        String rest = first.substring(h.length());
        if (!rest.isEmpty() && !rest.startsWith(" (") && !rest.startsWith(" when")) return rows;
        int from = 1;
        while (from < rows.size() && rows.get(from).value().isEmpty() && plain(rows.get(from).label()).isEmpty()) from++;
        return rows.subList(from, rows.size());
    }

    private static String plain(String s) {
        return s.replaceAll("§.", "").trim();
    }

    private static boolean modern() {
        return true;
    }

    // is this a classic card (they all have the short form)
    protected final boolean compact() {
        return modern();
    }

    protected static int rowWidth(Ui ui, Row r) {
        return ui.width(r.label()) + (r.value().isEmpty() ? 0 : ui.width(r.value()) + 6) + (r.icon() != null ? 12 : 0);
    }

    // the short form, cached. width/height/draw each ask every frame and shaping (regexes per row) is the expensive bit
    // rebuilt as soon as any row changes so a meter still moves
    private List<List<Row>> compactMemo;
    private List<Row> compactSrc;
    private int compactRoom;
    private boolean compactMeters, compactDemo;

    // short form rows, 1 or 2 per row
    private List<List<Row>> compactRows(ElementStyle st, int room) {
        List<Row> src = visibleRows(st);
        boolean meters = meters();
        if (compactMemo != null && room == compactRoom && meters == compactMeters && demo == compactDemo && sameRows(src, compactSrc)) return compactMemo;
        List<Row> brief = new java.util.ArrayList<>();
        for (Row r : shape(src, meters)) brief.add(new Row(r.icon(), Compact.label(r.label()), Compact.value(r.value()), r.color(), r.progress(), r.barColor()));
        compactMemo = Compact.rows(brief, r -> !spacer(r) && r.progress() < 0 && !r.value().isEmpty(), r -> rowWidth(Ui.MEASURE, r), room);
        // a copy, so a card that reuses + changes its own list cant fool the check above
        compactSrc = new java.util.ArrayList<>(src);
        compactRoom = room;
        compactMeters = meters;
        compactDemo = demo;
        return compactMemo;
    }

    // same text, colors, meter, icon. icons are often a fresh stack each call so compare what they are
    static boolean sameRows(List<Row> a, List<Row> b) {
        if (b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Row x = a.get(i), y = b.get(i);
            if (x == y) continue;
            if (x.color() != y.color() || x.barColor() != y.barColor() || Double.compare(x.progress(), y.progress()) != 0
                    || !x.label().equals(y.label()) || !x.value().equals(y.value())) return false;
            ItemStack p = x.icon(), q = y.icon();
            if (p != q && (p == null || q == null || p.getCount() != q.getCount() || !ItemStack.isSameItemSameComponents(p, q))) return false;
        }
        return true;
    }

    // what each line says -> label, value, meter
    protected final List<Row> shapedRows(ElementStyle st) {
        return shape(visibleRows(st), meters());
    }

    private static List<Row> shape(List<Row> src, boolean meters) {
        List<Row> out = new java.util.ArrayList<>(src.size());
        for (Row r : src) {
            RowText.Shaped s = RowText.shape(r.label(), r.value());
            double progress = r.progress() >= 0 ? r.progress() : meters ? s.progress() : -1;
            out.add(new Row(r.icon(), s.label(), s.value(), r.color(), progress, r.barColor()));
        }
        return out;
    }

    // own color, else the values, else the cards. full = green
    protected static int meterColor(Row r, int accent) {
        if (r.progress() >= 1) return io.github.zmuzy.sbc.ui.theme.Modern.GOOD;
        if (r.barColor() != 0) return r.barColor();
        if (saturated(r.color())) return r.color();
        int c = firstCode(r.value());
        if (c == 0) c = firstCode(r.label());
        return saturated(c) ? c : accent;
    }

    private static boolean saturated(int c) {
        if (c == 0) return false;
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) > 40;
    }

    private static final int[] CODE_COLORS = {0xFF000000, 0xFF0000AA, 0xFF00AA00, 0xFF00AAAA, 0xFFAA0000, 0xFFAA00AA, 0xFFFFAA00, 0xFFAAAAAA, 0xFF555555, 0xFF5555FF, 0xFF55FF55, 0xFF55FFFF, 0xFFFF5555, 0xFFFF55FF, 0xFFFFFF55, 0xFFFFFFFF};

    private static int firstCode(String s) {
        for (int i = 0; i + 1 < s.length(); i++) {
            if (s.charAt(i) != '§') continue;
            int n = "0123456789abcdef".indexOf(Character.toLowerCase(s.charAt(i + 1)));
            if (n >= 0) return CODE_COLORS[n];
            i++;
        }
        return 0;
    }

    // width in gui px before the elements own scale
    public int width() {
        Theme t = Theme.current();
        ElementStyle st = style();
        Ui ui = Ui.MEASURE;
        if (compact()) {
            boolean titled = titleShown(st);
            int inner = 2 * pad(t, st) + (titled ? 0 : !gutterIcon() ? 2 : 14);
            int w = titled ? 16 + ui.width(heading()) + 6 + (headingValue().isEmpty() ? 0 : ui.width(headingValue()) + 8) : 30;
            for (List<Row> row : compactRows(st, Compact.WIDE - inner)) {
                int rw = row.size() == 2 ? 2 * Math.max(rowWidth(ui, row.get(0)), rowWidth(ui, row.get(1))) + Compact.GAP : rowWidth(ui, row.get(0));
                w = Math.max(w, rw + inner);
            }
            return Math.min(236, Math.max(titled ? 80 : 40, w));
        }
        int w = titleShown(st) ? ui.width(heading()) + 4 + (headingValue().isEmpty() ? 0 : ui.width(headingValue()) + t.gap()) : 40;
        for (Row r : visibleRows(st)) {
            int rw = ui.width(r.label()) + (r.value().isEmpty() ? 0 : ui.width(r.value()) + t.gap()) + (r.icon() != null ? 12 : 0);
            w = Math.max(w, rw);
        }
        return Math.min(220, w + 2 * pad(t, st));
    }

    public int height() {
        Theme t = Theme.current();
        ElementStyle st = style();
        int rh = rowH(t, st);
        if (compact()) {
            boolean titled = titleShown(st);
            int mh = titled ? headH() : 5, crh = Math.min(rh, Compact.ROW_H);
            int inner = 2 * pad(t, st) + (titled ? 0 : !gutterIcon() ? 2 : 14);
            for (List<Row> row : compactRows(st, Compact.WIDE - inner)) {
                Row r = row.get(0);
                mh += spacer(r) ? Compact.RULE_BLOCK : crh + (r.progress() >= 0 ? Compact.METER_BLOCK : 0);
            }
            return mh;
        }
        int h = 2 * pad(t, st) + (titleShown(st) ? t.titleHeight(rh) : 0);
        for (Row r : visibleRows(st)) h += spacer(r) ? SPACER : rh;
        return h;
    }

    // top left at x, y (layout already moved + scaled it)
    public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
        Theme t = Theme.current();
        ElementStyle st = style();
        if (compact()) {
            drawCompactCard(ui, x, y, t, st);
            return;
        }
        int w = drawWidth(), h = height(), pad = pad(t, st), rh = rowH(t, st);
        cardPanel(t, ui, x, y, w, h, st);
        int cx = x + pad, cy = y + pad, cw = w - 2 * pad;
        boolean shadow = st.shadow() == null || st.shadow();
        if (titleShown(st)) {
            if (st.titleColor() != null) ui.textShadow(ui.ellipsize(heading(), cw), cx, cy, st.titleColor());
            else t.title(ui, heading(), cx, cy, cw - (headingValue().isEmpty() ? 0 : ui.width(headingValue()) + t.gap()));
            if (!headingValue().isEmpty()) ui.textShadow(headingValue(), cx + cw - ui.width(headingValue()), cy, t.dim());
            t.titleRule(ui, cx, cy + rh - 1, cw);
            cy += t.titleHeight(rh);
        }
        int textColor = ElementStyle.or(st.textColor(), t.text());
        for (Row r : visibleRows(st)) {
            if (spacer(r)) {
                cy += SPACER;
                continue;
            }
            int lx = cx, lw = cw;
            if (r.icon() != null) {
                ui.icon(r.icon(), lx - 1, cy - 1, 0.6f);
                lx += 12;
                lw -= 12;
            }
            int vc = r.color() != 0 ? r.color() : ElementStyle.or(st.valueColor(), t.text());
            if (st.textColor() == null && st.valueColor() == null && shadow) t.row(ui, r.label(), r.value(), lx, cy, lw, r.color());
            else {
                if (r.value().isEmpty()) {
                    if (shadow) ui.textShadow(ui.ellipsize(r.label(), lw), lx, cy, textColor);
                    else ui.text(ui.ellipsize(r.label(), lw), lx, cy, textColor);
                } else {
                    int vw = ui.width(r.value());
                    String lab = ui.ellipsize(r.label(), Math.max(10, lw - vw - t.gap()));
                    if (shadow) {
                        ui.textShadow(lab, lx, cy, textColor);
                        ui.textShadow(r.value(), lx + lw - vw, cy, vc);
                    } else {
                        ui.text(lab, lx, cy, textColor);
                        ui.text(r.value(), lx + lw - vw, cy, vc);
                    }
                }
            }
            cy += rh;
        }
    }

    private void drawCompactRow(Ui ui, Row r, int x, int y, int w, int labelColor, ElementStyle st) {
        int lx = x, lw = w;
        if (r.icon() != null) {
            ui.icon(r.icon(), lx - 1, y - 1, 0.6f);
            lx += 12;
            lw -= 12;
        }
        int vc = r.color() != 0 ? r.color() : ElementStyle.or(st.valueColor(), io.github.zmuzy.sbc.ui.theme.Modern.INK);
        io.github.zmuzy.sbc.ui.theme.Modern.row(ui, r.label(), r.value(), lx, y, lw, labelColor, vc);
    }

    private void drawCompactCard(Ui ui, int x, int y, Theme t, ElementStyle st) {
        boolean titled = titleShown(st);
        int w = drawWidth(), h = height(), pad = pad(t, st), rh = Math.min(rowH(t, st), Compact.ROW_H), accent = st.titleColor() != null ? st.titleColor() : accent();
        cardPanel(t, ui, x, y, w, h, st);
        int cx = x + pad, cy, cw = w - 2 * pad, inner = 2 * pad + (titled ? 0 : !gutterIcon() ? 2 : 14);
        if (titled) {
            cy = y + cardBand(ui, heading(), icon(), headingValue(), accent, x, y, w);
        } else {
            cardEdge(ui, x, y, h, accent);
            cy = y + 3;
            if (gutterIcon()) {
                ui.icon(icon(), x + 4, y + (h - io.github.zmuzy.sbc.ui.theme.Modern.ICON) / 2, io.github.zmuzy.sbc.ui.theme.Modern.ICON_SCALE);
                cx += 12;
                cw -= 12;
            }
        }
        int labelColor = st.textColor() != null ? st.textColor() : io.github.zmuzy.sbc.ui.theme.Modern.LABEL;
        for (List<Row> row : compactRows(st, Compact.WIDE - inner)) {
            Row r = row.get(0);
            if (spacer(r)) {
                cy += Compact.RULE_BLOCK;
                continue;
            }
            if (row.size() == 2) {
                int half = (cw - Compact.GAP) / 2;
                drawCompactRow(ui, r, cx, cy, half, labelColor, st);
                ui.rect(cx + half + Compact.GAP / 2, cy, 1, 8, 0x30FFFFFF);
                drawCompactRow(ui, row.get(1), cx + half + Compact.GAP, cy, cw - half - Compact.GAP, labelColor, st);
                cy += rh;
                continue;
            }
            drawCompactRow(ui, r, cx, cy, cw, labelColor, st);
            cy += rh;
            if (r.progress() >= 0) {
                io.github.zmuzy.sbc.ui.theme.Modern.meter(ui, cx, cy, cw, io.github.zmuzy.sbc.ui.theme.Modern.METER, r.progress(), meterColor(r, accent));
                cy += Compact.METER_BLOCK;
            }
        }
    }

}
