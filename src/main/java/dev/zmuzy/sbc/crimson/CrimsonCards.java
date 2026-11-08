// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.crimson;

import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.core.TabWidgets;
import dev.zmuzy.sbc.crimson.CrimsonMinibossRespawnTimer.Kind;
import dev.zmuzy.sbc.crimson.CrimsonMinibossRespawnTimer.Status;
import dev.zmuzy.sbc.crimson.DailyQuestHelper.KuudraTier;
import dev.zmuzy.sbc.crimson.DailyQuestHelper.Quest;
import dev.zmuzy.sbc.crimson.DailyQuestHelper.QuestCategory;
import dev.zmuzy.sbc.crimson.DailyQuestHelper.QuestState;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.hud.Compact;
import dev.zmuzy.sbc.ui.hud.ElementStyle;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.hud.RowText;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// crimson isle cards: the day's quests, bosses and tiers, respawn timers, volcano. they only draw what the
// ported helpers already know
// hidable rows are the named parts ("Daily Bosses", "Ashfang") bc those dont change every second like the text does
public final class CrimsonCards {
    private static final int MAX_WIDTH = 236;
    private static final int GAP = 4;
    private CrimsonCards() {}

    // --- drawing ---

    private record Ctx(int rh, int label, int value) {}

    // a piece of a card, knows its size and draws itself. every y lands on an even number so text stays sharp
    // when scaled down
    private abstract static class Line {
        // name it gets hidden by, empty = cant hide
        final String part;

        Line(String part) {
            this.part = part;
        }

        // a line that draws itself in the brief methods below needs none of these three
        int height(Ctx c) {
            return c.rh();
        }

        int width(Ui ui) {
            return 0;
        }

        void draw(Ui ui, int x, int y, int w, Ctx c) {
        }

        // short form says the same in less room. a line without its own short form stays as is at short row height
        int briefWidth(Ui ui) {
            return width(ui);
        }

        // height in the room the card has. what doesnt fit on one row takes a second, nothing gets cut
        int briefHeight(Ctx c, int room) {
            return height(c);
        }

        void drawBrief(Ui ui, int x, int y, int w, int room, Ctx c) {
            draw(ui, x, y, w, c);
        }

        // width it needs to share a row with a neighbour, 0 = doesnt share
        int cell(Ui ui) {
            return 0;
        }
    }

    // two short lines on one row
    private static final class Pair extends Line {
        private final Line a, b;

        Pair(Line a, Line b) {
            super("");
            this.a = a;
            this.b = b;
        }

        @Override int width(Ui ui) {
            return a.cell(ui) + Compact.GAP + b.cell(ui);
        }

        @Override int briefHeight(Ctx c, int room) {
            return c.rh();
        }

        @Override void drawBrief(Ui ui, int x, int y, int w, int room, Ctx c) {
            int left = Math.max(a.cell(ui), Math.min((w - Compact.GAP) / 2, w - Compact.GAP - b.cell(ui)));
            a.drawBrief(ui, x, y, left, left, c);
            ui.rect(x + left + Compact.GAP / 2, y, 1, 8, 0x30FFFFFF);
            b.drawBrief(ui, x + left + Compact.GAP, y, w - left - Compact.GAP, w - left - Compact.GAP, c);
        }
    }

    private static int even(int v) {
        return v & ~1;
    }

    // text ending at right on an even pixel, returns where it starts
    private static int textRight(Ui ui, String s, int right, int y, int color) {
        int left = even(right - ui.width(s));
        ui.textShadow(s, left, y, color);
        return left;
    }

    private static int chipWidth(Ui ui, String s) {
        return ui.width(s) + 6;
    }

    // chip ending at right. its text sits 3px in, so the chip starts on an odd pixel
    private static int chipRight(Ui ui, String s, int right, int y, int color) {
        int left = (right - chipWidth(ui, s) - 1) | 1;
        Modern.chip(ui, left, y, s, color);
        return left;
    }

    private static String number(int n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private abstract static class Card extends HudElement {
        Card(String id, String title, int priority, String feature) {
            this(id, title, Zone.TOP_LEFT, priority, feature);
        }

        Card(String id, String title, Zone zone, int priority, String feature) {
            super(id, title, "Crimson Isle", zone, priority, feature);
        }

        // lines to draw rn, live or demo
        abstract List<Line> lines();

        // behind the lines, over the glass
        void backdrop(Ui ui, int x, int y, int w, int h) {}

        // fixed width, else lines that change every second make the whole column hop
        boolean fullWidth() {
            return false;
        }

        private List<Line> shown(ElementStyle st) {
            List<Line> all = lines();
            if (st.hiddenRows().isEmpty()) return all;
            List<Line> out = new ArrayList<>();
            for (Line l : all) if (l.part.isEmpty() || !st.isHidden(l.part)) out.add(l);
            return out;
        }

        // room the icon takes in a card with no title
        private int iconRoom(ElementStyle st) {
            return titleShown(st) || icon().isEmpty() ? 0 : 12;
        }

        // max a row of two can take in the short form
        int pairWidth() {
            return Compact.WIDE;
        }

        private Ctx briefCtx(Theme t, ElementStyle st) {
            return new Ctx(Math.min(rowH(t, st), Compact.ROW_H), st.textColor() != null ? st.textColor() : Modern.LABEL, ElementStyle.or(st.valueColor(), Modern.INK));
        }

        private int briefInner(Theme t, ElementStyle st) {
            return 2 * pad(t, st) + iconRoom(st);
        }

        // short form lines: nothing left out, two short ones share a row
        private List<Line> packed(Theme t, ElementStyle st) {
            Ui ui = Ui.MEASURE;
            List<Line> lines = shown(st), out = new ArrayList<>(lines.size());
            int room = pairWidth() - briefInner(t, st);
            for (int i = 0; i < lines.size(); i++) {
                Line a = lines.get(i), b = i + 1 < lines.size() ? lines.get(i + 1) : null;
                if (b != null && a.cell(ui) > 0 && b.cell(ui) > 0 && a.cell(ui) + Compact.GAP + b.cell(ui) <= room) {
                    out.add(new Pair(a, b));
                    i++;
                } else out.add(a);
            }
            return out;
        }

        private int briefWidth() {
            Theme t = Theme.current();
            ElementStyle st = style();
            Ui ui = Ui.MEASURE;
            boolean titled = titleShown(st);
            // a card that keeps its width keeps it here too, its band has enough room for what it says
            int w = fullWidth() ? Compact.WIDE : titled ? 16 + ui.width(heading()) + 6 + (headingValue().isEmpty() ? 0 : ui.width(headingValue()) + 8) : 30;
            for (Line l : packed(t, st)) w = Math.max(w, l.briefWidth(ui) + briefInner(t, st));
            return Math.min(MAX_WIDTH, Math.max(titled ? 80 : 40, even(w + 1)));
        }

        private int briefHeight() {
            Theme t = Theme.current();
            ElementStyle st = style();
            Ctx c = briefCtx(t, st);
            int room = width() - briefInner(t, st), h = titleShown(st) ? Modern.BAND + 3 : 5;
            for (Line l : packed(t, st)) h += l.briefHeight(c, room);
            return h;
        }

        private void drawBrief(Ui ui, int x, int y) {
            Theme t = Theme.current();
            ElementStyle st = style();
            int w = drawWidth(), h = height(), pad = pad(t, st), accent = st.titleColor() != null ? st.titleColor() : accent();
            t.panel(ui, x, y, w, h, st);
            backdrop(ui, x, y, w, h);
            int cx = x + pad, cw = w - 2 * pad, cy;
            if (titleShown(st)) {
                cy = y + Modern.band(ui, heading(), icon(), headingValue(), accent, x, y, w);
            } else {
                Modern.edge(ui, x, y, h, accent);
                cy = y + 3;
                if (!icon().isEmpty()) {
                    ui.icon(icon(), x + GAP, y + (h - Modern.ICON) / 2, Modern.ICON_SCALE);
                    cx += iconRoom(st);
                    cw -= iconRoom(st);
                }
            }
            Ctx c = briefCtx(t, st);
            int room = width() - briefInner(t, st);
            for (Line l : packed(t, st)) {
                l.drawBrief(ui, cx, cy, cw, room, c);
                cy += l.briefHeight(c, room);
            }
        }

        @Override public int width() {
            return briefWidth();
        }

        @Override public int height() {
            return briefHeight();
        }

        @Override public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
            drawBrief(ui, x, y);
        }
    }

    // sub-heading whose line is the meter of what it counts: "Daily Quests ===---- 2/5 collected 40%"
    private static final class Section extends Line {
        private final String title, right, brief;
        private final int color;
        private final double progress;

        Section(String title, int done, int total, String word, int color) {
            super(title);
            this.title = title;
            this.color = color;
            this.progress = (double) done / total;
            boolean full = done >= total;
            this.right = Modern.fraction(String.valueOf(done), String.valueOf(total), full ? 'a' : 'e', full ? 'a' : 'f') + " §7" + word + "  §7" + RowText.percent(progress);
            this.brief = Modern.fraction(String.valueOf(done), String.valueOf(total), full ? 'a' : 'e', full ? 'a' : 'f') + " §7" + (word.equals("collected") ? "done" : word) + " §7" + RowText.percent(progress);
        }

        @Override int briefWidth(Ui ui) {
            return ui.width(title) + ui.width(brief) + GAP;
        }

        @Override int briefHeight(Ctx c, int room) {
            return c.rh();
        }

        @Override void drawBrief(Ui ui, int x, int y, int w, int room, Ctx c) {
            int rx = textRight(ui, brief, x + w, y, Modern.INK);
            ui.textShadow(title, x, y, Modern.lighten(color | 0xFF000000, 0.3f));
            int mx = even(x + ui.width(title) + 6), mw = rx - GAP - mx;
            if (mw >= 12) Modern.meter(ui, mx, y + GAP, mw, Modern.METER, progress, progress >= 1 ? Modern.GOOD : color);
        }

    }

    // a warning + what to do about it
    private static final class Notice extends Line {
        private final String text, hint;

        Notice(String text, String hint) {
            super("");
            this.text = text;
            this.hint = hint;
        }

        @Override int height(Ctx c) {
            return c.rh() * (hint == null ? 1 : 2) + 2;
        }

        @Override int width(Ui ui) {
            return Math.max(ui.width(text), hint == null ? 0 : ui.width(hint));
        }

        @Override void draw(Ui ui, int x, int y, int w, Ctx c) {
            ui.textShadow(ui.ellipsize(text, w), x, y, Modern.BAD);
            if (hint != null) ui.textShadow(ui.ellipsize(hint, w), x, y + c.rh(), c.label());
        }
    }

    // --- reputation ---

    private record Boss(String name, boolean done) {}

    // tier colours, getting hotter
    private static final int[] HEAT = {0xFFFFE082, 0xFFFFC14D, 0xFFFF9F45, 0xFFFF6B4A, 0xFFFF4D8D};

    // everything the reputation card shows, same for live and demo
    private record Day(ReputationApi.FactionType faction, Integer reputation, Standing standing, boolean questWidgetMissing, boolean hideComplete, List<Quest> quests,
                       Map<Quest, String> distances, String boardDistance, String rescueRank, boolean rescueRunning, List<Boss> bosses, boolean[] tiers) {
        int questsDone() {
            int n = 0;
            for (Quest q : quests) if (q.state == QuestState.COLLECTED) n++;
            return n;
        }

        int bossesDone() {
            int n = 0;
            for (Boss b : bosses) if (b.done) n++;
            return n;
        }

        int tiersDone() {
            int n = 0;
            for (boolean t : tiers) if (t) n++;
            return n;
        }

        int ready() {
            int n = 0;
            for (Quest q : quests) if (q.state == QuestState.READY_TO_COLLECT) n++;
            return n;
        }
    }

    // what hypixel's Reputation widget says beyond the number: bar percent + the tiers at both ends
    private record Standing(String from, String to, double percent) {
        boolean known() {
            return percent >= 0 || from != null;
        }
    }

    private static final Standing NO_STANDING = new Standing(null, null, -1);
    private static final Pattern BAR = Pattern.compile("^[\\s|]*\\|{6,}[\\s|]*\\(?(\\d+(?:\\.\\d+)?)%\\)?\\s*$");
    private static final Pattern TIERS = Pattern.compile("^\\s*([A-Za-z]+)(?:\\s{2,}([A-Za-z]+))?\\s*$");
    private static final Pattern ASHFALL = Pattern.compile("^\\s*Ashfall: (.+?)\\s*$");

    private static List<String> tabRead;
    private static Standing standingRead = NO_STANDING;
    private static String ashfallRead = "";
    private static boolean loggedWidget;

    // tab list changes way less often than frames get drawn
    private static void readTab() {
        List<String> tab = CrimsonEnv.world.tab();
        if (tab == tabRead) return;
        tabRead = tab;
        standingRead = NO_STANDING;
        ashfallRead = "";
        tabDailies = readDailies(tab);
        for (String l : tab) {
            Matcher a = ASHFALL.matcher(l);
            if (a.matches()) ashfallRead = a.group(1);
        }
        for (TabWidgets.Widget w : TabWidgets.parse(tab)) {
            if (!w.def().id().equals("REPUTATION")) continue;
            String from = null, to = null;
            double percent = -1;
            for (int i : w.lines()) {
                String l = tab.get(i);
                Matcher b = BAR.matcher(l);
                if (b.matches()) {
                    percent = Math.min(1, Double.parseDouble(b.group(1)) / 100);
                    continue;
                }
                Matcher t = TIERS.matcher(l);
                if (t.matches() && t.group(2) != null) {
                    from = t.group(1);
                    to = t.group(2);
                } else if (t.matches() && from == null && t.group(1).matches("[A-Z].*")) {
                    from = t.group(1); // top tier has nothing after it
                }
            }
            standingRead = new Standing(from, to, percent);
            if (!loggedWidget) { // havent seen this line format on a real dump yet
                loggedWidget = true;
                List<String> lines = new ArrayList<>();
                for (int i : w.lines()) lines.add(tab.get(i));
                dev.zmuzy.sbc.SbcMod.LOG.info("[sbc] reputation widget: {}", lines);
            }
            break;
        }
    }

    // first lines: whose side you're on and how far along
    private static final class Faction extends Line {
        private final String name, brief, value, icon;
        private final int color;
        private final Standing s;

        Faction(ReputationApi.FactionType f, Integer reputation, Standing s) {
            super("Reputation");
            boolean mage = f == ReputationApi.FactionType.MAGE;
            this.name = f.factionName + " Reputation";
            this.brief = f.factionName + " Rep";
            this.value = reputation == null ? "§7-" : "§6" + number(reputation);
            this.icon = mage ? "blaze_rod" : "golden_axe";
            this.color = mage ? 0xFF7FB3FF : 0xFFFF8A50;
            this.s = s;
        }

        @Override int briefWidth(Ui ui) {
            return 12 + ui.width(brief) + ui.width(value) + GAP;
        }

        @Override int briefHeight(Ctx c, int room) {
            return s.known() ? 2 * c.rh() : c.rh();
        }

        @Override void drawBrief(Ui ui, int x, int y, int w, int room, Ctx c) {
            boolean two = s.known();
            int tx = x + 12;
            ui.icon(LegacyItems.stack(icon), x, y + (two ? 3 : -1), Modern.ICON_SCALE);
            int vx = textRight(ui, value, x + w, y, Modern.GOLD);
            ui.textShadow(ui.ellipsize(brief, vx - tx - GAP), tx, y, Modern.lighten(color, 0.2f));
            if (!two) return;
            int ly = y + c.rh(), right = x + w, left = tx;
            if (s.percent >= 0) right = textRight(ui, RowText.percent(s.percent), right, ly, c.value()) - GAP;
            if (s.to != null) right = textRight(ui, s.to, right, ly, Modern.GOOD) - GAP;
            if (s.from != null) {
                ui.textShadow(s.from, left, ly, c.label());
                left = even(left + ui.width(s.from) + GAP + 1);
            }
            if (s.percent >= 0 && right - left >= 12) Modern.meter(ui, left, ly + GAP, right - left, Modern.METER, s.percent, color);
        }

    }

    private static final Map<String, String> DOJO_ICONS = Map.of("Mastery", "bow", "Tenacity", "fire_charge", "Stamina", "rabbit_foot", "Swiftness", "lead",
            "Discipline", "diamond_sword", "Control", "ender_eye", "Force", "stick");
    private static final Map<String, String> FETCH_ICONS = Map.ofEntries(Map.entry("Magmag", "magma_cream"), Map.entry("Sulphur", "glowstone_dust"), Map.entry("Spectre Dust", "sugar"),
            Map.entry("Tentacle Meat", "rotten_flesh"), Map.entry("Corrupted Fragment", "prismarine_shard"), Map.entry("Gunpowder", "gunpowder"), Map.entry("Digested Mushrooms", "brown_mushroom"),
            Map.entry("Mycelium", "mycelium"), Map.entry("Kada Lead", "lead"), Map.entry("Wither Soul", "wither_skeleton_skull"), Map.entry("Mutated Blaze Ashes", "blaze_powder"),
            Map.entry("Burning Eye", "ender_eye"), Map.entry("Gazing Pearl", "ender_pearl"), Map.entry("Red Sand", "red_sand"));
    private static final Map<String, String> BOSS_ICONS = Map.of("Bladesoul", "iron_sword", "Mage Outlaw", "enchanted_book", "Barbarian Duke X", "golden_axe", "Ashfang", "blaze_powder", "Magma Boss", "magma_cream");

    private static String bossIcon(String name) {
        return BOSS_ICONS.getOrDefault(name, "wither_skeleton_skull");
    }

    private static String bossBrief(String name) {
        return name.equals("Barbarian Duke X") ? "Duke X" : name;
    }

    // the quest's own item if the game has its model, else the closest vanilla item
    private static net.minecraft.world.item.ItemStack questStack(Quest q) {
        if (q.displayItem != null && !q.displayItem.isEmpty()) {
            net.minecraft.world.item.ItemStack real = dev.zmuzy.sbc.core.ItemIcons.of(q.displayItem);
            if (!real.is(net.minecraft.world.item.Items.PAPER) || real.has(net.minecraft.core.component.DataComponents.ITEM_MODEL)) return real;
        }
        return LegacyItems.stack(questIcon(q));
    }

    private static String questIcon(Quest q) {
        return switch (q.category) {
            case FISHING -> "tropical_fish";
            case RESCUE -> "painting";
            case MINIBOSS -> bossIcon(q.target);
            case FETCH -> FETCH_ICONS.getOrDefault(q.target, "chest");
            case DOJO -> DOJO_ICONS.getOrDefault(q.target, "iron_sword");
            case KUUDRA -> "tripwire_hook";
            case UNKNOWN -> "paper";
        };
    }

    private static int questColor(QuestCategory c) {
        return switch (c) {
            case FISHING -> 0xFF4FC3F7;
            case RESCUE -> 0xFF9CE37D;
            case MINIBOSS -> 0xFFFF4D6A;
            case FETCH -> Modern.GOLD;
            case DOJO -> 0xFFC77DFF;
            case KUUDRA -> 0xFFFF9F45;
            case UNKNOWN -> Modern.BAD;
        };
    }

    // one quest: what it wants on top, kind + progress under it, state at the right end
    private static final class QuestEntry extends Line {
        private final Quest q;
        private final String name, category, note, distance;

        QuestEntry(Quest q, String distance, String rescueRank, boolean rescueRunning) {
            super("Daily Quests");
            this.q = q;
            this.distance = distance;
            this.category = q.category.displayName.replaceAll("§.", "");
            int rank = q.category == QuestCategory.DOJO ? q.internalName.indexOf(" Rank ") : -1;
            this.name = rank >= 0 ? q.internalName.substring(0, rank) : q.displayName;
            String note = null;
            if (rank >= 0) {
                String letter = q.internalName.substring(rank + 6).trim();
                int points = DailyQuestHelper.getPointsForDojoRank(letter);
                if (!letter.isEmpty()) note = "§7Rank §f" + letter + (points > 0 ? "  §e" + number(points) + " §7points" : "");
            } else if (q.category == QuestCategory.RESCUE) {
                if (rescueRank != null) note = "§7Rank §f" + rescueRank;
                if (rescueRunning && q.state == QuestState.ACCEPTED) note = (note == null ? "" : note + "  ") + "§bin the parkour";
            }
            this.note = note;
        }

        private boolean progress() {
            return q.isProgress() && q.state != QuestState.COLLECTED;
        }

        private String chip() {
            if (q.category == QuestCategory.UNKNOWN) return null;
            return switch (q.state) {
                case ACCEPTED -> "TODO";
                case READY_TO_COLLECT -> "READY";
                case COLLECTED -> "DONE";
            };
        }

        private int chipColor() {
            return switch (q.state) {
                case ACCEPTED -> Modern.AQUA;
                case READY_TO_COLLECT -> Modern.GOOD;
                case COLLECTED -> Modern.DIM;
            };
        }

        // the tab's check mark = target reached, even if the kills or fish werent counted in chat
        private int have() {
            return q.state == QuestState.READY_TO_COLLECT ? q.needAmount : q.haveAmount;
        }

        // collected = nothing left to say, one quiet line
        private boolean folded() {
            return q.state == QuestState.COLLECTED;
        }

        @Override int height(Ctx c) {
            return folded() ? c.rh() + 2 : 2 * c.rh() + 2;
        }

        private String briefCategory() {
            return switch (q.category) {
                case FISHING -> "Trophy";
                case RESCUE -> "Rescue";
                case MINIBOSS -> "Boss";
                case FETCH -> "Fetch";
                case DOJO -> "Dojo";
                case KUUDRA -> "Kuudra";
                case UNKNOWN -> category;
            };
        }

        private String briefNumbers() {
            boolean full = have() >= q.needAmount;
            double p = q.needAmount > 0 ? Math.min(1, (double) have() / q.needAmount) : 1;
            return Modern.fraction(number(have()), number(q.needAmount), full ? 'a' : 'e', full ? 'a' : 'f') + " §7" + RowText.percent(p);
        }

        private String briefNote() {
            return note == null ? null : note.replace(" §7points", " §7pts").replace("§bin the parkour", "§bparkour");
        }

        // a collected quest stays on one row if it fits, a long name pushes its kind to its own row
        private boolean foldedRow(Ui ui, int room) {
            String chip = chip();
            return folded() && 12 + ui.width(name) + GAP + ui.width(briefCategory()) + (chip == null ? 0 : chipWidth(ui, chip) + 4) <= room;
        }

        @Override int briefWidth(Ui ui) {
            String chip = chip();
            int top = 12 + ui.width(name) + (chip == null ? 0 : chipWidth(ui, chip) + 4);
            if (folded()) return top;
            // room for any distance so the card doesnt change width while you walk
            int under = 12 + ui.width(briefCategory()) + (distance == null ? 0 : ui.width("9,999m") + GAP);
            if (progress()) under += ui.width(briefNumbers()) + GAP;
            else if (note != null) under += ui.width(briefNote()) + GAP;
            return Math.max(top, under);
        }

        @Override int briefHeight(Ctx c, int room) {
            return foldedRow(Ui.MEASURE, room) ? c.rh() : 2 * c.rh();
        }

        @Override void drawBrief(Ui ui, int x, int y, int w, int room, Ctx c) {
            String chip = chip();
            int tx = x + 12, top = x + w;
            if (foldedRow(ui, room)) {
                ui.icon(questStack(q), x, y - 1, Modern.ICON_SCALE);
                if (chip != null) top = chipRight(ui, chip, top, y, chipColor()) - 3;
                top = textRight(ui, briefCategory(), top, y, Modern.DIM) - GAP;
                ui.textShadow(ui.ellipsize(name, top - tx), tx, y, Modern.DIM);
                return;
            }
            if (q.state == QuestState.READY_TO_COLLECT) ui.gradH(x - 2, y - 1, w + 4, 2 * c.rh() - 1, Ui.alpha(Modern.GOOD, 0.16f), Ui.alpha(Modern.GOOD, 0.02f));
            ui.icon(questStack(q), x, y + 3, Modern.ICON_SCALE);
            if (chip != null) top = chipRight(ui, chip, top, y, chipColor()) - 3;
            ui.textShadow(ui.ellipsize(name, top - tx), tx, y, folded() ? Modern.DIM : c.value());
            int ly = y + c.rh(), right = x + w;
            String kind = briefCategory();
            ui.textShadow(kind, tx, ly, q.category == QuestCategory.UNKNOWN ? Modern.BAD : folded() ? Modern.DIM : c.label());
            if (folded()) return;
            if (progress()) right = textRight(ui, briefNumbers(), right, ly, c.value());
            else if (note != null) right = textRight(ui, briefNote(), right, ly, c.label());
            if (distance != null) right = textRight(ui, distance, right - GAP, ly, Modern.DIM);
            int mx = even(tx + ui.width(kind) + 6);
            if (progress() && right - GAP - mx >= 12) {
                double p = q.needAmount > 0 ? Math.min(1, (double) have() / q.needAmount) : 1;
                Modern.meter(ui, mx, ly + GAP, right - GAP - mx, Modern.METER, p, p >= 1 ? Modern.GOOD : questColor(q.category));
            }
        }

    }

    // what the green rows mean
    private static final class ReadyHint extends Line {
        private final String text, brief, distance;

        ReadyHint(int ready, String distance) {
            super("Daily Quests");
            this.text = "§a" + ready + " §7ready to collect at the §fTown Board";
            this.brief = "§a" + ready + " §7ready at §fTown Board";
            this.distance = distance;
        }

        @Override int briefWidth(Ui ui) {
            return ui.width(brief) + (distance == null ? 0 : ui.width(distance) + GAP);
        }

        @Override int briefHeight(Ctx c, int room) {
            return c.rh();
        }

        @Override void drawBrief(Ui ui, int x, int y, int w, int room, Ctx c) {
            int right = distance == null ? x + w : textRight(ui, distance, x + w, y, Modern.DIM) - GAP;
            ui.textShadow(ui.ellipsize(brief, right - x), x, y, c.label());
        }

    }

    // names in two columns. whats left keeps its icon and brightness, done gets a tick
    private static final class BossGrid extends Line {
        private final List<Boss> bosses;

        BossGrid(List<Boss> bosses) {
            super("Daily Bosses");
            this.bosses = bosses;
        }

        private int rows() {
            return (bosses.size() + 1) / 2;
        }

        @Override int briefWidth(Ui ui) {
            int cell = 0;
            for (Boss b : bosses) cell = Math.max(cell, ui.width(bossBrief(b.name)));
            return 2 * (cell + 12) + 6;
        }

        @Override int briefHeight(Ctx c, int room) {
            return rows() * c.rh();
        }

        @Override void drawBrief(Ui ui, int x, int y, int w, int room, Ctx c) {
            int half = even((w + GAP) / 2);
            for (int i = 0; i < bosses.size(); i++) {
                Boss b = bosses.get(i);
                int cx = x + i % 2 * half, cy = y + i / 2 * c.rh();
                if (b.done) ui.textShadow("✔", cx + 2, cy, Modern.GOOD);
                else ui.icon(LegacyItems.stack(bossIcon(b.name)), cx, cy - 1, Modern.ICON_SCALE);
                ui.textShadow(ui.ellipsize(bossBrief(b.name), half - 14), cx + 12, cy, b.done ? Modern.DIM : c.value());
            }
        }

    }

    // the 5 kuudra tiers side by side getting hotter, one done today goes quiet with a tick
    private static final class TierStrip extends Line {
        private final boolean[] done;
        private final boolean hideComplete;

        TierStrip(boolean[] done, boolean hideComplete) {
            super("Daily Kuudra");
            this.done = done;
            this.hideComplete = hideComplete;
        }

        private String top(KuudraTier[] tiers, int i) {
            return done[i] ? "§a✔ §7T" + tiers[i].tierNumber : "T" + tiers[i].tierNumber;
        }

        // short form: a tier is as wide as its text so no name gets cut
        private int cell(Ui ui, KuudraTier[] tiers, int i) {
            return Math.max(ui.width(top(tiers, i)), ui.width(tiers[i].displayName));
        }

        @Override int briefWidth(Ui ui) {
            KuudraTier[] tiers = KuudraTier.values();
            int w = -2;
            for (int i = 0; i < tiers.length; i++) if (!hideComplete || !done[i]) w += cell(ui, tiers, i) + 2;
            return w;
        }

        @Override int briefHeight(Ctx c, int room) {
            return 2 * c.rh();
        }

        @Override void drawBrief(Ui ui, int x, int y, int w, int room, Ctx c) {
            KuudraTier[] tiers = KuudraTier.values();
            int shown = 0;
            for (int i = 0; i < tiers.length; i++) if (!hideComplete || !done[i]) shown++;
            if (shown == 0) return;
            int h = 2 * c.rh(), spare = Math.max(0, w - briefWidth(ui)) / shown, cx = x, cell = 0;
            for (int i = 0; i < tiers.length; i++) {
                if (hideComplete && done[i]) continue;
                int cw = ++cell == shown ? x + w - cx : cell(ui, tiers, i) + spare;
                int heat = HEAT[i];
                ui.rect(cx, y, cw, h, Ui.alpha(done[i] ? Modern.GOOD : heat, done[i] ? 0.07f : 0.16f));
                ui.rect(cx, y + h - 1, cw, 1, Ui.alpha(done[i] ? Modern.GOOD : heat, done[i] ? 0.45f : 0.85f));
                String top = top(tiers, i), name = tiers[i].displayName;
                ui.textShadow(top, cx + (cw - ui.width(top)) / 2, y + 1, done[i] ? Modern.DIM : Modern.lighten(heat, 0.25f));
                ui.textShadow(name, cx + (cw - ui.width(name)) / 2, y + c.rh(), done[i] ? Modern.DIM : c.value());
                cx += cw + 2;
            }
        }

    }

    private static String metres(Vec3 from, Vec3 to) {
        return number((int) Math.round(from.distanceTo(to))) + "m";
    }

    private static Day liveDay() {
        CrimsonEnv.World world = CrimsonEnv.world;
        Vec3 player = world.playerPos();
        Map<Quest, String> distances = new java.util.HashMap<>();
        for (Quest q : DailyQuestHelper.quests)
            if (q.state == QuestState.ACCEPTED && q.location != null) distances.put(q, metres(player, q.location));
        List<Boss> bosses = new ArrayList<>();
        for (DailyQuestHelper.MiniBoss m : DailyQuestHelper.miniBosses) bosses.add(new Boss(m.displayName, m.doneToday));
        KuudraTier[] tiers = KuudraTier.values();
        boolean[] done = new boolean[tiers.length];
        for (int i = 0; i < tiers.length; i++) done[i] = tiers[i].doneToday;
        readTab();
        ReputationApi.FactionType f = ReputationApi.factionType();
        Vec3 board = f == null ? null : Crimson.questBoard();
        return new Day(f, f == null ? null : CrimsonEnv.profile().reputation.get(f.name()), standingRead, DailyQuestHelper.tabListQuestsMissing, Crimson.b(Crimson.PANEL, "hideComplete", false),
                DailyQuestHelper.quests, distances, board == null ? null : metres(player, board), RescueMissionWaypoints.tier(), RescueMissionWaypoints.inParkour(), bosses, done);
    }

    private static Quest demoQuest(QuestCategory category, String internal, String name, QuestState state, int need, int have, String target) {
        Quest q = new Quest(category, internal, name, "", null, state, need, target);
        q.haveAmount = have;
        return q;
    }

    private static Day demoDay() {
        Quest fetch = demoQuest(QuestCategory.FETCH, "Digested Mushrooms", "Digested Mushrooms", QuestState.READY_TO_COLLECT, 20, 20, "Digested Mushrooms");
        Quest fish = demoQuest(QuestCategory.FISHING, "Steaming-Hot Flounder", "Steaming-Hot Flounder", QuestState.ACCEPTED, 3, 1, "Steaming-Hot Flounder");
        Quest boss = demoQuest(QuestCategory.MINIBOSS, "Barbarian Duke X", "Barbarian Duke X", QuestState.ACCEPTED, 2, 1, "Barbarian Duke X");
        Quest dojo = demoQuest(QuestCategory.DOJO, "Control Rank C", "Control §7(§e400 points§7)", QuestState.ACCEPTED, -1, 0, "Control");
        Quest kuudra = demoQuest(QuestCategory.KUUDRA, "Kill Kuudra HOT Tier", "Tier 2 (Hot)", QuestState.COLLECTED, -1, 0, "HOT");
        return new Day(ReputationApi.FactionType.BARBARIAN, 5410, new Standing("Trusted", "Honored", 0.773), false, false, List.of(fetch, fish, boss, dojo, kuudra),
                Map.of(fish, "312m", boss, "642m", dojo, "86m"), "164m", "C", false,
                List.of(new Boss("Bladesoul", true), new Boss("Mage Outlaw", false), new Boss("Barbarian Duke X", true), new Boss("Ashfang", false), new Boss("Magma Boss", false)),
                new boolean[]{true, true, false, false, false});
    }

    private static List<Line> reputationLines(Day d) {
        List<Line> out = new ArrayList<>();
        if (d.faction == null) {
            out.add(new Notice("Faction not found!", null));
            return out;
        }
        out.add(new Faction(d.faction, d.reputation, d.standing));
        if (d.questWidgetMissing) {
            out.add(new Notice("Faction Quests Widget not found!", "§7Open §e/tab §7and enable it!"));
            return out;
        }
        int done = d.questsDone();
        out.add(new Section("Daily Quests", done, 5, "collected", 0xFFFFB454));
        if (done != 5) {
            for (Quest q : d.quests) {
                if (d.hideComplete && q.state == QuestState.COLLECTED) continue;
                out.add(new QuestEntry(q, d.distances.get(q), d.rescueRank, d.rescueRunning));
            }
            if (d.ready() > 0) out.add(new ReadyHint(d.ready(), d.boardDistance));
        }
        done = d.bossesDone();
        out.add(new Section("Daily Bosses", done, 5, "killed", 0xFFFF4D6A));
        if (done != 5) {
            List<Boss> left = new ArrayList<>();
            for (Boss b : d.bosses) if (!d.hideComplete || !b.done) left.add(b);
            out.add(new BossGrid(left));
        }
        done = d.tiersDone();
        out.add(new Section("Daily Kuudra", done, 5, "killed", 0xFFFF9F45));
        if (done < 5) out.add(new TierStrip(d.tiers, d.hideComplete));
        return out;
    }

    // the tab's other "Daily Quests": name + state as hypixel says them ("Matriarch", "Available")
    private static List<String[]> tabDailies = List.of();
    private static final List<String[]> DEMO_DAILIES = List.of(new String[]{"Matriarch", "Available"}, new String[]{"Aranya", "Completed"});

    // "Daily Quests:" in the tab ends at the first blank line
    public static List<String[]> readDailies(List<String> tab) {
        List<String[]> out = new ArrayList<>();
        boolean in = false;
        for (String raw : tab) {
            String l = dev.zmuzy.sbc.core.GameState.strip(raw).trim();
            if (l.equals("Daily Quests:")) {
                in = true;
                continue;
            }
            if (!in) continue;
            int colon = l.indexOf(": ");
            if (colon <= 0) break;
            out.add(new String[]{l.substring(0, colon), l.substring(colon + 2).trim()});
        }
        return out;
    }

    private static final class TabDaily extends Line {
        private final String name, state;

        TabDaily(String name, String state) {
            super("Daily Tasks");
            this.name = name;
            this.state = state;
        }

        private boolean open() {
            return state.equalsIgnoreCase("Available");
        }

        private String chip() {
            return open() ? "TO DO" : state.toUpperCase(Locale.ROOT);
        }

        // done shows as a tick, like the bosses
        private boolean ticked() {
            return state.equalsIgnoreCase("Completed");
        }

        @Override int cell(Ui ui) {
            return 12 + ui.width(name) + (ticked() ? 0 : GAP + 2 + chipWidth(ui, chip()));
        }

        @Override int briefWidth(Ui ui) {
            return cell(ui);
        }

        @Override int briefHeight(Ctx c, int room) {
            return c.rh();
        }

        @Override void drawBrief(Ui ui, int x, int y, int w, int room, Ctx c) {
            if (ticked()) ui.textShadow("✔", x + 2, y, Modern.GOOD);
            else ui.icon(LegacyItems.stack(name.equals("Matriarch") ? "ender_pearl" : "spider_eye"), x, y - 1, Modern.ICON_SCALE);
            int right = ticked() ? x + w : chipRight(ui, chip(), x + w, y, open() ? Modern.WARN : Modern.GOOD) - GAP;
            ui.textShadow(ui.ellipsize(name, right - x - 12), x + 12, y, open() ? c.value() : Modern.DIM);
        }

    }

    private static void addDailies(List<Line> out, List<String[]> dailies) {
        if (dailies.isEmpty()) return;
        int done = 0;
        for (String[] d : dailies) if (!d[1].equalsIgnoreCase("Available")) done++;
        out.add(new Section("Daily Tasks", done, dailies.size(), "done", 0xFF5CD8FF));
        for (String[] d : dailies) out.add(new TabDaily(d[0], d[1]));
    }

    public static HudElement reputation() {
        return new Card(Crimson.PANEL, "Reputation Helper", 44, Crimson.PANEL) {
            private Day day() {
                return demo ? demoDay() : liveDay();
            }

            @Override List<Line> lines() {
                List<Line> out = new ArrayList<>(reputationLines(day()));
                addDailies(out, demo ? DEMO_DAILIES : tabDailies);
                return out;
            }

            @Override boolean fullWidth() {
                Day d = day();
                return d.faction != null && !d.questWidgetMissing;
            }

            @Override public boolean visible() {
                return Crimson.crimson() && Crimson.reputationShown();
            }

            @Override public String heading() {
                return compact() ? "Reputation" : super.heading();
            }

            // whole day at a glance: quests, bosses and tiers
            @Override public String headingValue() {
                Day d = day();
                if (d.faction == null || d.questWidgetMissing) return "";
                int done = d.questsDone() + d.bossesDone() + d.tiersDone();
                return Modern.fraction(String.valueOf(done), "15", done >= 15 ? 'a' : 'e', done >= 15 ? 'a' : 'f') + " §7today" + (compact() ? " " : "  ") + RowText.percent(done / 15.0);
            }

            private List<Row> parts(Day d) {
                return List.of(new Row("Reputation", d.reputation == null ? "-" : number(d.reputation), 0), new Row("Daily Quests", d.questsDone() + "/5", 0),
                        new Row("Daily Bosses", d.bossesDone() + "/5", 0), new Row("Daily Kuudra", d.tiersDone() + "/5", 0));
            }

            @Override public List<Row> rows() {
                return parts(liveDay());
            }

            @Override public List<Row> demo() {
                return parts(demoDay());
            }
        };
    }

    // --- miniboss timer ---

    private static int statusColor(Status s) {
        return switch (s.kind()) {
            case ON_MAP, SPAWNED -> Modern.GOOD;
            case COUNTDOWN -> s.left() < 20_000 ? Modern.GOLD : Modern.AQUA;
            case SOON -> Modern.WARN;
            case PROBABLY_UP -> Modern.GOLD;
            case WINDOW -> Modern.AQUA;
            case UNKNOWN -> Modern.DIM;
        };
    }

    // one boss. state is the chip at the right end, the other marks only show if theres room left
    private record BossState(String name, Status status, boolean doneToday, List<String> quest, boolean here) {}

    // longest wording that fits, or null
    private static String fitting(Ui ui, List<String> wordings, int room) {
        for (String w : wordings) if (chipWidth(ui, w) <= room) return w;
        return null;
    }

    private static List<String> questChip(int have, int need) {
        if (need <= 1) return List.of("QUEST");
        String numbers = "QUEST " + have + "/" + need;
        return List.of(numbers + " " + RowText.percent(Math.min(1, (double) have / need)), numbers, "QUEST");
    }

    private static final class BossLine extends Line {
        private final BossState b;

        BossLine(BossState b) {
            super(b.name);
            this.b = b;
        }

        private String briefState() {
            return switch (b.status.kind()) {
                case ON_MAP -> "On map";
                case PROBABLY_UP -> "Maybe up";
                default -> b.status.state();
            };
        }

        // the word goes before the number does. "SPAWNED! 1m 5s" means the same without the "ago"
        private String briefDetail(Ui ui, int room) {
            String d = b.status.detail();
            return d.endsWith(" ago") && marksAndName(ui) + stateAndDetail(ui, d) > room ? d.substring(0, d.length() - 4) : d;
        }

        // the wording that has to fit, quest numbers stay in
        private String questBrief() {
            return b.quest == null ? null : b.quest.get(Math.min(1, b.quest.size() - 1));
        }

        private int marksAndName(Ui ui) {
            String quest = questBrief();
            return 12 + ui.width(bossBrief(b.name)) + GAP + (b.doneToday ? 10 : 0) + (quest == null ? 0 : chipWidth(ui, quest) + 3) + (b.here ? chipWidth(ui, "HERE") + 3 : 0);
        }

        private int stateAndDetail(Ui ui, String detail) {
            return ui.width(briefState()) + (detail.isEmpty() ? 0 : ui.width(detail) + GAP);
        }

        // marks and state on one row if they fit, else the state takes a second row, nothing left out
        private boolean oneRow(Ui ui, int room) {
            return marksAndName(ui) + stateAndDetail(ui, briefDetail(ui, room)) <= room;
        }

        @Override int briefWidth(Ui ui) {
            return 12 + ui.width(bossBrief(b.name));
        }

        @Override int briefHeight(Ctx c, int room) {
            return oneRow(Ui.MEASURE, room) ? c.rh() : 2 * c.rh();
        }

        @Override void drawBrief(Ui ui, int x, int y, int w, int room, Ctx c) {
            Status s = b.status;
            boolean one = oneRow(ui, room);
            int color = statusColor(s), ly = one ? y : y + c.rh(), right = textRight(ui, briefState(), x + w, ly, color) - GAP;
            String detail = briefDetail(ui, room);
            if (!detail.isEmpty()) right = textRight(ui, detail, right, ly, s.kind() == Kind.ON_MAP ? c.label() : Modern.DIM) - GAP;
            ui.icon(LegacyItems.stack(bossIcon(b.name)), x, y - 1, Modern.ICON_SCALE);
            boolean up = s.kind() == Kind.ON_MAP || s.kind() == Kind.SPAWNED;
            String name = bossBrief(b.name);
            ui.textShadow(name, x + 12, y, up ? c.value() : s.kind() == Kind.UNKNOWN ? Modern.DIM : c.label());
            int lx = even(x + 12 + ui.width(name) + GAP);
            if (b.doneToday) {
                ui.textShadow("✔", lx, y, Modern.GOOD);
                lx += 10;
            }
            if (b.quest != null) {
                String quest = fitting(ui, b.quest, (one ? right - (b.here ? chipWidth(ui, "HERE") + 3 : 0) : x + w) - lx - 1);
                if (quest != null) lx += Modern.chip(ui, lx | 1, y, quest, Modern.GOLD) + 3;
            }
            if (!one) lx = x + 12;
            if (b.here) lx += Modern.chip(ui, lx | 1, ly, "HERE", Modern.AQUA) + 3;
            // fixed place and length, same as the open form
            int meterRight = one ? Math.min(right, x + w - 40) : right, mw = Math.min(30, meterRight - lx);
            if (s.progress() >= 0 && mw >= 12) Modern.meter(ui, meterRight - mw, ly + GAP, mw, Modern.METER, s.progress(), color);
        }

    }

    private static List<BossState> liveBosses() {
        long now = CrimsonEnv.now();
        Quest quest = DailyQuestHelper.getQuest(QuestCategory.MINIBOSS);
        List<BossState> out = new ArrayList<>();
        for (CrimsonMinibossRespawnTimer.MiniBoss it : CrimsonMinibossRespawnTimer.MiniBoss.values()) {
            boolean done = false;
            for (DailyQuestHelper.MiniBoss m : DailyQuestHelper.miniBosses) if (m.displayName.equals(it.displayName)) done = m.doneToday;
            boolean wanted = quest != null && quest.state == QuestState.ACCEPTED && it.displayName.equals(quest.target);
            out.add(new BossState(it.displayName, CrimsonMinibossRespawnTimer.statusOf(it, now), done, wanted ? questChip(quest.haveAmount, quest.needAmount) : null, it == CrimsonMinibossRespawnTimer.currentAreaBoss));
        }
        return out;
    }

    private static List<BossState> demoBosses() {
        return List.of(new BossState("Bladesoul", new Status(Kind.COUNTDOWN, "1m 20s", "", 1 / 3.0, 80_000, ""), true, null, false),
                new BossState("Mage Outlaw", new Status(Kind.ON_MAP, "On the map", "12m", -1, -1, ""), false, null, true),
                new BossState("Barbarian Duke X", new Status(Kind.SOON, "Soon!", "", -1, -1, ""), true, questChip(1, 2), false),
                new BossState("Ashfang", new Status(Kind.SPAWNED, "SPAWNED!", "1m 5s ago", -1, -1, ""), false, null, false),
                new BossState("Magma Boss", new Status(Kind.UNKNOWN, "Not seen yet", "", -1, -1, ""), false, null, false));
    }

    public static HudElement miniboss() {
        return new Card("crimson_miniboss_timer", "Miniboss timer", Zone.TOP_RIGHT, 21, Crimson.MINIBOSS) { // right under the scoreboard
            private List<BossState> bosses() {
                return demo ? demoBosses() : liveBosses();
            }

            @Override List<Line> lines() {
                List<Line> out = new ArrayList<>();
                for (BossState b : bosses()) out.add(new BossLine(b));
                return out;
            }

            @Override boolean fullWidth() {
                return true;
            }

            @Override public boolean visible() {
                return Crimson.crimson();
            }

            @Override public String heading() {
                return compact() ? "Minibosses" : super.heading();
            }

            // how many are up and whats next
            @Override public String headingValue() {
                int up = 0;
                long next = -1;
                String nextText = "";
                for (BossState b : bosses()) {
                    Kind k = b.status.kind();
                    if (k == Kind.ON_MAP || k == Kind.SPAWNED) up++;
                    if (k == Kind.COUNTDOWN && (next < 0 || b.status.left() < next)) {
                        next = b.status.left();
                        nextText = b.status.state();
                    }
                }
                String ups = up > 0 ? "§a" + up + " §7up" : "";
                String nexts = next >= 0 ? "§7next §b" + nextText : "";
                return ups.isEmpty() ? nexts : nexts.isEmpty() ? ups : ups + (compact() ? " " : "  ") + nexts;
            }

            private List<Row> parts(List<BossState> bosses) {
                List<Row> out = new ArrayList<>();
                for (BossState b : bosses) out.add(new Row(b.name, b.status.state(), 0));
                return out;
            }

            @Override public List<Row> rows() {
                return parts(liveBosses());
            }

            @Override public List<Row> demo() {
                return parts(demoBosses());
            }
        };
    }

    // --- volcano ---

    // colour per volcano state, a word not listed keeps the card's own
    private static int volcanoColor(String status, int accent) {
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "INACTIVE", "" -> Modern.DIM;
            case "LOW" -> Modern.GOOD;
            case "MEDIUM", "MODERATE" -> Modern.GOLD;
            case "HIGH" -> Modern.WARN;
            case "EXTREME", "ERUPTING", "CRITICAL" -> Modern.BAD;
            default -> accent;
        };
    }

    private static final class VolcanoLine extends Line {
        private final String status;
        private final int accent;

        VolcanoLine(String status, int accent) {
            super("Volcano Explosivity");
            this.status = status;
            this.accent = accent;
        }

        @Override int cell(Ui ui) {
            return ui.width("Volcano") + GAP + (status.isEmpty() ? 6 : chipWidth(ui, status));
        }

        @Override int briefWidth(Ui ui) {
            return cell(ui);
        }

        @Override int briefHeight(Ctx c, int room) {
            return c.rh();
        }

        @Override void drawBrief(Ui ui, int x, int y, int w, int room, Ctx c) {
            int right = status.isEmpty() ? textRight(ui, "-", x + w, y, Modern.DIM) : chipRight(ui, status, x + w, y, volcanoColor(status, accent));
            ui.textShadow(ui.ellipsize("Volcano", right - x - GAP + 1), x, y, Modern.lighten(accent | 0xFF000000, 0.45f));
        }

    }

    private static final class AshfallLine extends Line {
        private final String when;

        AshfallLine(String when) {
            super("Ashfall");
            this.when = when;
        }

        @Override int cell(Ui ui) {
            return ui.width("Ashfall") + GAP + ui.width(when);
        }

        @Override int briefWidth(Ui ui) {
            return cell(ui);
        }

        @Override int briefHeight(Ctx c, int room) {
            return c.rh();
        }

        // cell is exactly this wide, the time landing on an even pixel can take one of them
        @Override void drawBrief(Ui ui, int x, int y, int w, int room, Ctx c) {
            int right = textRight(ui, when, x + w, y, Modern.GOLD);
            ui.textShadow(ui.ellipsize("Ashfall", right - x - GAP + 1), x, y, c.label());
        }
    }

    public static HudElement volcano() {
        return new Card("crimson_volcano", "Volcano", 46, Crimson.VOLCANO) {
            private String status() {
                return demo ? "LOW" : CrimsonHelpers.volcanoStatus();
            }

            private int colour() {
                Integer chosen = style().titleColor();
                return chosen != null ? chosen : accent();
            }

            private String ashfall() {
                if (demo) return "for 13m";
                readTab();
                return ashfallRead;
            }

            @Override List<Line> lines() {
                List<Line> out = new ArrayList<>();
                out.add(new VolcanoLine(status(), colour()));
                if (!ashfall().isEmpty()) out.add(new AshfallLine(ashfall()));
                return out;
            }

            // glass takes the state's colour
            @Override void backdrop(Ui ui, int x, int y, int w, int h) {
                String s = status();
                if (s.isEmpty() || s.equalsIgnoreCase("INACTIVE")) return;
                int c = volcanoColor(s, colour()) | 0xFF000000;
                ui.gradH(x + 2, y + 1, w - 3, h - 2, Ui.alpha(c, 0.2f), Ui.alpha(c, 0.01f));
            }

            // one short row whatever it says, this card gets read in passing
            @Override int pairWidth() {
                return MAX_WIDTH;
            }

            @Override public boolean visible() {
                return Crimson.crimson() && !CrimsonHelpers.volcano().isEmpty();
            }

            private List<Row> parts(String status, String ashfall) {
                List<Row> out = new ArrayList<>();
                out.add(new Row("Volcano Explosivity", status, 0));
                if (!ashfall.isEmpty()) out.add(new Row("Ashfall", ashfall, 0));
                return out;
            }

            @Override public List<Row> rows() {
                readTab();
                return parts(CrimsonHelpers.volcanoStatus(), ashfallRead);
            }

            @Override public List<Row> demo() {
                return parts("LOW", "for 13m");
            }
        };
    }
}
