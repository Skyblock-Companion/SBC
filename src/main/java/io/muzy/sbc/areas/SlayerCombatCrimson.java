// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.areas;

import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemIcons;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Vitals;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.nav.Slayers;
import io.muzy.sbc.nav.Waypoints;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.Toasts;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.hud.Compact;
import io.muzy.sbc.ui.hud.ElementStyle;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.hud.Looks;
import io.muzy.sbc.ui.hud.RowText;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// slayer, combat, end + crimson helpers from chat, scoreboard, tab, action bar: boss spawn warning + kills
// left, blaze attunement alerts, armor stacks, cocoons...
public final class SlayerCombatCrimson {
    // slayer (scoreboard / chat)
    static final Pattern SLAYER_KILLS = Pattern.compile("^(\\d+)/(\\d+) Kills$");
    static final Pattern SLAYER_REMAINING = Pattern.compile("^\\((\\d+)/([\\d,.k]+)\\) ([A-Za-z ]+ Slayer|Combat XP)$");
    static final Pattern ATTUNEMENT = Pattern.compile("^Strike using the (SPIRIT|ASHEN|CRYSTAL|AURIC) attunement on your dagger!$");
    static final Pattern AUTO_BANK = Pattern.compile("^Took ([\\d,.k]+) coins from your bank for auto-slayer\\.\\.\\.$");
    // combat
    static final Pattern COCOON = Pattern.compile("^CAUGHT! You cocooned an? (.+)!$");
    static final Pattern GHOST_DROP = Pattern.compile("^RARE DROP! (Sorrow|Volta|Plasma|Ghosty Boots|Bag of Cash).*$");
    static final Pattern GHOST_CASH = Pattern.compile("^The ghost's death materialized ([\\d,]+) coins from the mists!$");
    static final Pattern ENDER_NODE = Pattern.compile("^ENDER NODE! You found (?:(\\d+)x )?(.+)!$");
    static final Pattern DRAGON_SPAWN = Pattern.compile("^.*?The ([A-Za-z]+) Dragon has spawned!$");
    static final Pattern DRAGON_DOWN = Pattern.compile("^\\s*([A-Z]+) DRAGON DOWN!\\s*$");
    static final Pattern DRAGON_HP = Pattern.compile("^Dragon HP: ([\\d,]+)\\s*.*$");
    static final Pattern YOUR_DAMAGE = Pattern.compile("^Your Damage: ([\\d,.]+)$");
    static final Pattern ZEALOTS = Pattern.compile("^\\s*Zealots Contributed: (\\d+)/(\\d+)\\s*$");
    static final Pattern BROODMOTHER = Pattern.compile("^Broodmother: (.+)$");

    private static long bankSpent;
    private static int cocoons, ghostKills;
    private static final Map<String, Integer> ghostDrops = new LinkedHashMap<>(), nodes = new LinkedHashMap<>();
    private static long ghostCash;
    private static String dragon = "";
    private static long dragonAt;
    private SlayerCombatCrimson() {}

    private static boolean sb() {
        return GameState.inSkyBlock || io.muzy.sbc.SbcMod.DEV;
    }

    private static boolean end() {
        return "THE_END".equals(GameState.island) || io.muzy.sbc.SbcMod.DEV;
    }

    public static void reset() {
        bankSpent = 0;
        cocoons = ghostKills = 0;
        ghostDrops.clear();
        nodes.clear();
        ghostCash = 0;
        dragon = "";
    }

    public static int cocoons() {
        return cocoons;
    }

    public static Map<String, Integer> nodes() {
        return nodes;
    }

    private static void alert(String title, String body, int color, float pitch) {
        Toasts.push(title, body, color);
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 0.9f, pitch);
    }

    // pure: one colorless chat line. true = used
    public static boolean feed(String plain) {
        String t = plain.trim();
        Matcher m;
        if ((m = ATTUNEMENT.matcher(t)).matches()) {
            int c = switch (m.group(1)) {
                case "SPIRIT" -> 0xFFFFFFFF;
                case "ASHEN" -> 0xFF555555;
                case "CRYSTAL" -> 0xFF55FFFF;
                default -> 0xFFFFFF55;
            };
            if (Features.on("slayer_attunement")) alert(m.group(1), "Switch the dagger to " + m.group(1), c, 1.5f);
            return true;
        }
        if ((m = AUTO_BANK.matcher(t)).matches()) {
            bankSpent += parseK(m.group(1));
            return true;
        }
        if ((m = COCOON.matcher(t)).matches()) {
            cocoons++;
            return true;
        }
        if ((m = GHOST_DROP.matcher(t)).matches()) {
            ghostDrops.merge(m.group(1), 1, Integer::sum);
            return true;
        }
        if ((m = GHOST_CASH.matcher(t)).matches()) {
            ghostCash += Long.parseLong(m.group(1).replace(",", ""));
            return true;
        }
        if ((m = ENDER_NODE.matcher(t)).matches()) {
            nodes.merge(m.group(2), m.group(1) == null ? 1 : Integer.parseInt(m.group(1)), Integer::sum);
            return true;
        }
        if ((m = DRAGON_SPAWN.matcher(t)).matches()) {
            dragon = m.group(1);
            dragonAt = System.currentTimeMillis();
            if (Features.on("end_dragon")) alert(dragon + " Dragon", "has spawned", 0xFFFF55FF, 0.9f);
            return true;
        }
        if ((m = DRAGON_DOWN.matcher(t)).matches()) {
            dragon = "";
            return true;
        }
        return false;
    }

    // "4.8k" -> 4800, "120" -> 120 (skyhanni formatDouble)
    static double shortNumber(String s) {
        s = s.replace(",", "").trim().toLowerCase(Locale.ROOT);
        double mult = s.endsWith("k") ? 1e3 : s.endsWith("m") ? 1e6 : s.endsWith("b") ? 1e9 : 1;
        if (mult > 1) s = s.substring(0, s.length() - 1);
        try {
            return Double.parseDouble(s) * mult;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseK(String s) {
        s = s.replace(",", "").toLowerCase(Locale.ROOT);
        double mult = s.endsWith("k") ? 1_000 : s.endsWith("m") ? 1_000_000 : 1;
        if (mult > 1) s = s.substring(0, s.length() - 1);
        try {
            return Math.round(Double.parseDouble(s) * mult);
        } catch (Exception e) {
            return 0;
        }
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (sb()) feed(line.plain());
        return ChatBus.PASS;
    }

    private static String sbLine(Pattern p, int group) {
        for (String l : GameState.scoreboard) {
            Matcher m = p.matcher(l.trim());
            if (m.matches()) return m.group(group);
        }
        return "";
    }

    // card with a state tag on its band, sub headings between rows and a heavier meter for its main number
    private abstract static class Card extends HudElement {
        // a row in this color is a sub heading: label in card color, fading line, value at the right
        static final int SECTION = 0x00ABCDEF;
        // in a value: what comes after is coins, in its own gold column on the right, so the numbers before line up too
        static final String COLUMN = "  \t";
        private static final Pattern PIECE = Pattern.compile("§.|[^§]+");

        Card(String id, String title, String section, Zone zone, int priority, String feature) {
            super(id, title, section, zone, priority, feature);
        }

        // state at the right end of the band, empty = none
        String tag() {
            return "";
        }

        int tagColor() {
            return accent();
        }

        boolean heavy(Row r) {
            return false;
        }

        int minWidth() {
            return 130;
        }

        // hairlines only ever between things, also when the editor hides the rows around one
        private List<Row> lines(ElementStyle st) {
            List<Row> out = new ArrayList<>();
            for (Row r : shapedRows(st)) if (!spacer(r) || (!out.isEmpty() && !spacer(out.getLast()))) out.add(r);
            if (!out.isEmpty() && spacer(out.getLast())) out.removeLast();
            return out;
        }

        // a short form row: same thing in fewer letters, or 2 short rows where it says 2 things
        void brief(Row r, List<Row> out) {
            out.add(worded(r, Compact.label(r.label())));
        }

        // same row under a shorter label with long numbers shortened. a sub heading already says it counts drops
        static Row worded(Row r, String label) {
            String value = r.color() == SECTION ? r.value().replaceAll(" §7drops?", "") : r.value();
            return new Row(r.icon(), label, shortened(value), r.color(), r.progress(), r.barColor());
        }

        // piece by piece: a color code digit isnt part of the number after it
        private static String shortened(String value) {
            StringBuilder out = new StringBuilder();
            Matcher m = PIECE.matcher(value);
            while (m.find()) out.append(m.group().startsWith("§") ? m.group() : Compact.value(m.group()));
            return out.toString();
        }

        // rows that belong together can stand together in the short form and share a row
        void arrange(List<Row> rows) {}

        static int indexOf(List<Row> rows, String label) {
            for (int i = 0; i < rows.size(); i++) if (rows.get(i).label().equals(label)) return i;
            return -1;
        }

        // puts one row right after another when both are there
        static void beside(List<Row> rows, String label, String other) {
            int at = indexOf(rows, label);
            if (at < 0 || indexOf(rows, other) < 0) return;
            Row r = rows.remove(at);
            rows.add(indexOf(rows, other) + 1, r);
        }

        private int briefInner(Theme t, ElementStyle st) {
            return 2 * pad(t, st) + (titleShown(st) ? 0 : icon().isEmpty() ? 2 : 14);
        }

        // short form rows, 1 or 2 per row, nothing left out
        private List<List<Row>> packed(Theme t, ElementStyle st) {
            List<Row> brief = new ArrayList<>();
            for (Row r : lines(st)) {
                if (spacer(r)) brief.add(r);
                else brief(r, brief);
            }
            arrange(brief);
            return Compact.rows(brief, r -> !spacer(r) && r.progress() < 0 && !r.value().isEmpty() && r.color() != SECTION && !r.value().contains(COLUMN), r -> rowWidth(Ui.MEASURE, r), Compact.WIDE - briefInner(t, st));
        }

        private static int column(Ui ui, List<List<Row>> rows) {
            int column = 0;
            for (List<Row> row : rows) column = Math.max(column, ui.width(worth(row.get(0))));
            return column;
        }

        private int briefWidth() {
            Theme t = Theme.current();
            ElementStyle st = style();
            Ui ui = Ui.MEASURE;
            boolean titled = titleShown(st);
            String tag = tag();
            int w = titled ? 16 + ui.width(heading()) + 8 + (!tag.isEmpty() ? ui.width(tag) + 6 + Modern.PAD : headingValue().isEmpty() ? 0 : ui.width(headingValue()) + 8) : 30;
            List<List<Row>> rows = packed(t, st);
            int inner = briefInner(t, st), column = column(ui, rows);
            for (List<Row> row : rows) {
                Row r = row.get(0);
                int rw = row.size() == 2 ? 2 * Math.max(rowWidth(ui, r), rowWidth(ui, row.get(1))) + Compact.GAP
                        : ui.width(r.label()) + (r.icon() != null ? 12 : 0) + ui.width(beforeWorth(r)) + 6 + (r.value().contains(COLUMN) && column > 0 ? column + 6 : 0);
                if (!spacer(r)) w = Math.max(w, rw + inner);
            }
            return Math.min(236, Math.max(titled ? 80 : 40, w));
        }

        private int briefHeight() {
            Theme t = Theme.current();
            ElementStyle st = style();
            int rh = Math.min(rowH(t, st), Compact.ROW_H), h = titleShown(st) ? Modern.BAND + 3 : 5;
            for (List<Row> row : packed(t, st)) {
                Row r = row.get(0);
                h += spacer(r) ? Compact.RULE_BLOCK : rh + (r.progress() >= 0 ? Compact.METER_BLOCK : 0);
            }
            return h;
        }

        private static void briefRow(Ui ui, Row r, int x, int y, int w, int labelColor, ElementStyle st) {
            int lx = x, lw = w;
            if (r.icon() != null) {
                ui.icon(r.icon(), lx - 1, y - 1, 0.6f);
                lx += 12;
                lw -= 12;
            }
            Modern.row(ui, r.label(), beforeWorth(r), lx, y, lw, labelColor, r.color() != 0 ? r.color() : ElementStyle.or(st.valueColor(), Modern.INK));
        }

        private void drawBrief(Ui ui, int x, int y) {
            Theme t = Theme.current();
            ElementStyle st = style();
            int w = drawWidth(), h = height(), pad = pad(t, st), rh = Math.min(rowH(t, st), Compact.ROW_H), accent = st.titleColor() != null ? st.titleColor() : accent();
            t.panel(ui, x, y, w, h, st);
            int cx = x + pad, cy, cw = w - 2 * pad;
            if (titleShown(st)) {
                String tag = tag(), coins = headingValue();
                int right = !tag.isEmpty() ? ui.width(tag) + 6 : !coins.isEmpty() ? ui.width(coins) + 4 : 0;
                cy = y + Modern.band(ui, right == 0 ? heading() : ui.ellipsize(heading(), w - right - 22), icon(), "", accent, x, y, w);
                if (!tag.isEmpty()) Modern.chip(ui, x + w - Modern.PAD - right, y + 3, tag, tagColor());
                else if (!coins.isEmpty()) ui.textShadow(coins, x + w - Modern.PAD - ui.width(coins), y + 3, Modern.GOLD);
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
            List<List<Row>> rows = packed(t, st);
            int column = column(ui, rows), gap = column > 0 ? column + 6 : 0;
            for (List<Row> row : rows) {
                Row r = row.get(0);
                if (spacer(r)) {
                    cy += Compact.RULE_BLOCK;
                    continue;
                }
                if (row.size() == 2) {
                    int half = (cw - Compact.GAP) / 2;
                    briefRow(ui, r, cx, cy, half, labelColor, st);
                    ui.rect(cx + half + Compact.GAP / 2, cy, 1, 8, 0x30FFFFFF);
                    briefRow(ui, row.get(1), cx + half + Compact.GAP, cy, cw - half - Compact.GAP, labelColor, st);
                    cy += rh;
                    continue;
                }
                String worth = worth(r), value = beforeWorth(r);
                int room = cw - (r.value().contains(COLUMN) ? gap : 0);
                if (r.color() == SECTION) {
                    int vw = ui.width(value);
                    Modern.section(ui, r.label(), cx, cy, room - vw - 4, accent);
                    ui.textShadow(value, cx + room - vw, cy, Modern.INK);
                } else briefRow(ui, r, cx, cy, room, labelColor, st);
                if (!worth.isEmpty()) ui.textShadow(worth, cx + cw - ui.width(worth), cy, Modern.GOLD);
                cy += rh;
                if (r.progress() >= 0) {
                    Modern.meter(ui, cx, cy, cw, heavy(r) ? Compact.THICK : Modern.METER, r.progress(), meterColor(r, accent));
                    cy += Compact.METER_BLOCK;
                }
            }
        }

        @Override
        public int width() {
            return compact() ? briefWidth() : super.width();
        }

        @Override
        public int height() {
            return briefHeight();
        }

        @Override
        public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
            drawBrief(ui, x, y);
        }

        private static String worth(Row r) {
            int c = r.value().indexOf(COLUMN);
            return c < 0 ? "" : r.value().substring(c + COLUMN.length());
        }

        private static String beforeWorth(Row r) {
            int c = r.value().indexOf(COLUMN);
            return c < 0 ? r.value() : r.value().substring(0, c);
        }
    }

    // "(1,205/4.8k) Combat XP": done, of what, what kind. empty if the sidebar has no such line
    private static String[] goalLine() {
        Pattern progress = io.muzy.sbc.core.Patterns.get("SH.slayer.bosswarning.progress");
        for (String l : GameState.scoreboard) {
            String t = l.trim();
            Matcher m = progress.matcher(t);
            if (!m.matches() || !(t.endsWith("Combat XP") || t.endsWith(" Slayer"))) continue;
            return new String[]{m.group("progress"), m.group("total"), t.substring(t.lastIndexOf(')') + 1).trim()};
        }
        return new String[]{"", "", ""};
    }

    private static boolean questShown() {
        return Features.on("slayers") && Slayers.active();
    }

    private static long slayerSeen;
    private static String killsSeen = "";

    // the quest stays on the sidebar wherever you go. it counts as slayer while you're in its area, the kill count
    // moves or the boss is up. half a minute of anything else (mining, say) and the card goes
    static boolean doingSlayer() {
        long now = System.currentTimeMillis();
        String kills = sbLine(SLAYER_KILLS, 1);
        Slayers.Quest q = questShown() ? Slayers.quest() : null;
        boolean fight = q != null && q.phase() != Slayers.Phase.WAITING;
        if (io.muzy.sbc.combat.SlayerApi.inCorrectArea() || fight || !kills.equals(killsSeen)) slayerSeen = now;
        killsSeen = kills;
        Feature f = Features.get("slayers");
        var o = f == null ? null : f.option("slayers.hide_away");
        if (o != null && !o.bool()) return true;
        return now - slayerSeen < AWAY_MS;
    }

    static final long AWAY_MS = 30_000;

    private static boolean progressOn() {
        return Features.on("slayer_progress");
    }

    private static final Map<String, Looks.Look> BOSS_LOOKS = Map.of(
            "Revenant Horror", new Looks.Look(0xFF7BD66F, "minecraft:rotten_flesh"), "Atoned Horror", new Looks.Look(0xFF4FD8A8, "minecraft:rotten_flesh"),
            "Tarantula Broodfather", new Looks.Look(0xFFE0566B, "minecraft:cobweb"), "Conjoined Brood", new Looks.Look(0xFFE0566B, "minecraft:spider_eye"),
            "Sven Packmaster", new Looks.Look(0xFFB8C7D9, "minecraft:mutton"), "Voidgloom Seraph", new Looks.Look(0xFFC77DFF, "minecraft:ender_pearl"),
            "Inferno Demonlord", new Looks.Look(0xFFFF9F45, "minecraft:blaze_powder"), "Riftstalker Bloodfiend", new Looks.Look(0xFFFF4D6A, "minecraft:redstone"),
            "Bloodfiend", new Looks.Look(0xFFFF4D6A, "minecraft:redstone"));

    private static final Slayers.Quest DEMO_QUEST = new Slayers.Quest("Tarantula Broodfather", "IV", Slayers.Phase.FIGHTING, 12_340, 24_180, false, 24_180, "1.2M❤", 0, -1, false, 7, 1_250, 100_000, 400_000, 3, 500);

    // slayer: boss from the quest + kill timer, kills towards it from the sidebar. both features keep their own switch
    private static List<HudElement.Row> slayerRows(Slayers.Quest q, String kills, String killsOf, String goal, String goalOf, String goalName, long coins) {
        List<HudElement.Row> out = new ArrayList<>();
        boolean waiting = q != null && q.phase() == Slayers.Phase.WAITING;
        if (q != null && !waiting) fightRows(out, q);
        if (!kills.isEmpty()) {
            int h = Integer.parseInt(kills), n = Integer.parseInt(killsOf);
            if (!out.isEmpty()) out.add(new HudElement.Row(""));
            out.add(HudElement.Row.meter("Kills", Modern.fraction(kills, killsOf, 'f', '7'), 0, n > 0 ? h / (double) n : 0, h >= n * 0.9 ? Modern.BAD : 0));
        }
        if (!goal.isEmpty()) {
            if (kills.isEmpty() && !out.isEmpty()) out.add(new HudElement.Row(""));
            out.add(new HudElement.Row(goalName.isEmpty() ? "Quest" : goalName, Modern.fraction(goal, goalOf, 'f', '7'), 0));
        }
        List<HudElement.Row> records = new ArrayList<>();
        if (waiting) records.add(bestRow(q));
        if (q != null) levelRows(records, q);
        if (!records.isEmpty() && !out.isEmpty()) out.add(new HudElement.Row(""));
        out.addAll(records);
        if (coins > 0) {
            if (!out.isEmpty()) out.add(new HudElement.Row(""));
            out.add(new HudElement.Row("Auto-slayer", String.format(Locale.ROOT, "%,d coins", coins), Modern.GOLD));
        }
        return out;
    }

    private static void fightRows(List<HudElement.Row> out, Slayers.Quest q) {
        if (!q.health().isEmpty()) out.add(new HudElement.Row("Boss HP", q.health(), Modern.BAD));
        if (q.glyphs() > 0) {
            String left = String.format(Locale.ROOT, "%.1fs", q.glyphLeft() / 1000.0);
            out.add(HudElement.Row.meter("Yang Glyph", "§c" + left + (q.glyphs() > 1 ? "  §7x" + q.glyphs() : ""), 0, q.glyphLeft() / (double) Slayers.GLYPH_MS, Modern.BAD));
        }
        boolean slain = q.phase() == Slayers.Phase.SLAIN;
        if (q.time() >= 0 && q.target() > 0) {
            double p = q.time() / (double) q.target();
            int pace = p > 1 ? Modern.BAD : slain || p < 0.75 ? Modern.GOOD : Modern.WARN;
            String shown = (slain ? "§a" : "§f") + Slayers.fmt(q.time()) + "§7/§e" + Slayers.fmt(q.target()) + "  §7" + Math.round(p * 100) + "%";
            // a full meter draws green, past your best it has to stay red
            out.add(HudElement.Row.meter("Fight time", shown, 0, p >= 1 ? 0.999 : p, pace));
            long diff = q.time() - q.target();
            out.add(new HudElement.Row("Against best", (diff > 0 ? "+" : "-") + Slayers.fmt(Math.abs(diff)), diff > 0 ? Modern.BAD : Modern.GOOD));
        } else {
            out.add(new HudElement.Row("Fight time", Slayers.fmt(q.time()), slain ? Modern.GOOD : Modern.INK));
            out.add(bestRow(q));
        }
    }

    // rng meter goes at the bottom of the card
    private static List<HudElement.Row> withRng(List<HudElement.Row> rows, boolean demo) {
        List<HudElement.Row> rng = io.muzy.sbc.combat.SlayerCards.rngRows(demo);
        if (rng.isEmpty()) return rows;
        List<HudElement.Row> out = new ArrayList<>(rows);
        if (!out.isEmpty()) out.add(new HudElement.Row(""));
        out.addAll(rng);
        return out;
    }

    private static HudElement.Row bestRow(Slayers.Quest q) {
        return q.best() > 0 ? new HudElement.Row("Best time", Slayers.fmt(q.best()), Modern.GOLD) : new HudElement.Row("Best time", "none yet", Modern.DIM);
    }

    private static void levelRows(List<HudElement.Row> rows, Slayers.Quest q) {
        if (q.maxed()) rows.add(new HudElement.Row("Level", (q.level() >= 0 ? "§bLVL " + q.level() + "  " : "") + "§aMAXED", 0));
        else if (q.xpLeft() >= 0) {
            String left = String.format(Locale.ROOT, "%,d", q.xpLeft());
            if (q.level() >= 0) rows.add(new HudElement.Row("Level", "§bLVL " + q.level() + " > " + (q.level() + 1) + "  §f" + left + " §7XP left", 0));
            long of = q.xpNext() - q.xpPrev(), have = of - q.xpLeft();
            if (q.xpNext() >= 0 && have >= 0 && of > 0) {
                // through this level, like the profile page counts it
                String now = String.format(Locale.ROOT, "%,d", have), next = String.format(Locale.ROOT, "%,d", of);
                rows.add(HudElement.Row.meter("Next level", Modern.fraction(now, next, 'b', '7'), 0, have / (double) of, Modern.AQUA));
            } else if (q.level() < 0) rows.add(new HudElement.Row("Next level", left + " XP", Modern.AQUA));
        }
        if (q.bosses() > 0) rows.add(new HudElement.Row("Bosses left", "§f" + q.bosses() + (q.bosses() == 1 ? " boss" : " bosses") + (q.xpEach() > 0 ? String.format(Locale.ROOT, "  §7+%,d XP each", q.xpEach()) : ""), 0));
    }

    public static final HudElement SLAYER = new Card("slayers", "Slayer", "Slayer", Zone.TOP_LEFT, 40, "slayers") {
        private Slayers.Quest quest() {
            return demo ? DEMO_QUEST : questShown() ? Slayers.quest() : null;
        }

        private Looks.Look look(Slayers.Quest q) {
            return q == null ? null : BOSS_LOOKS.get(q.boss());
        }

        @Override
        public boolean active() {
            Feature a = Features.get("slayers"), b = Features.get("slayer_progress");
            return (a == null && b == null) || (a != null && a.active()) || (b != null && b.active());
        }

        @Override
        public boolean visible() {
            boolean any = questShown() || (progressOn() && (!sbLine(SLAYER_KILLS, 1).isEmpty() || !goalLine()[0].isEmpty()));
            return any && doingSlayer();
        }

        @Override
        public int accent() {
            Looks.Look l = look(quest());
            return l != null ? l.accent() : super.accent();
        }

        @Override
        public ItemStack icon() {
            Looks.Look l = look(quest());
            return l != null ? l.stack() : super.icon();
        }

        @Override
        public String heading() {
            Slayers.Quest q = quest();
            if (q != null && compact()) return (q.boss().split(" ")[0] + " " + q.tier()).trim();
            return q == null ? title : (q.boss() + " " + q.tier()).trim();
        }

        @Override
        void brief(Row r, List<Row> out) {
            String v = r.value();
            int two = v.indexOf("  ");
            switch (r.label()) {
                case "Fight time" -> out.add(worded(r, "Fight"));
                case "Against best" -> out.add(worded(r, "vs PB"));
                case "Best time" -> out.add(worded(r, "Best"));
                case "Level" -> {
                    if (two < 0 || !v.endsWith(" §7XP left")) out.add(worded(r, "Level"));
                    else {
                        out.add(new Row(null, "Level", v.substring(0, two).replace("LVL ", ""), r.color()));
                        out.add(worded(new Row(null, "", v.substring(two + 2, v.length() - " §7XP left".length()), r.color()), "XP left"));
                    }
                }
                case "Bosses left" -> {
                    out.add(new Row(null, "Bosses left", (two < 0 ? v : v.substring(0, two)).replaceAll(" boss(es)?$", ""), r.color()));
                    if (two >= 0) out.add(new Row(null, "XP each", v.substring(two + 2).replace(" XP each", ""), r.color()));
                }
                default -> super.brief(r, out);
            }
        }

        @Override
        void arrange(List<Row> rows) {
            beside(rows, "vs PB", "Boss HP");
            // while waiting the best time sits above the level so level + xp keep their row together
            int best = indexOf(rows, "Best");
            if (best >= 0 && best + 1 == indexOf(rows, "Level")) beside(rows, "Best", "XP left");
        }

        @Override
        String tag() {
            Slayers.Quest q = quest();
            if (q == null) return "";
            if (q.newBest()) return "NEW BEST";
            return switch (q.phase()) {
                case WAITING -> "WAITING";
                case FIGHTING -> "FIGHTING";
                case SLAIN -> "SLAIN";
            };
        }

        @Override
        int tagColor() {
            Slayers.Quest q = quest();
            if (q == null) return accent();
            if (q.newBest()) return Modern.GOLD;
            return switch (q.phase()) {
                case WAITING -> Modern.WARN;
                case FIGHTING -> Modern.BAD;
                case SLAIN -> Modern.GOOD;
            };
        }

        @Override
        boolean heavy(Row r) {
            return r.label().equals("Fight time");
        }

        @Override
        int minWidth() {
            return 170;
        }

        @Override
        public List<Row> rows() {
            boolean p = progressOn();
            String[] goal = goalLine();
            List<Row> out = withRng(slayerRows(questShown() ? Slayers.quest() : null, p ? sbLine(SLAYER_KILLS, 1) : "", sbLine(SLAYER_KILLS, 2), p ? goal[0] : "", goal[1], goal[2], p ? bankSpent : 0), false);
            return out.isEmpty() ? List.of(new Row("No slayer quest")) : out;
        }

        @Override
        public List<Row> demo() {
            return withRng(slayerRows(DEMO_QUEST, "38", "40", "120", "500", "Atomic Slayer", 42_000), true);
        }

        @Override
        public int width() {
            return super.width();
        }

        @Override
        public int height() {
            return super.height();
        }

        @Override
        public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
            super.draw(ui, g, x, y);
            return;
        }
    };

    // drops sb id + the vanilla item to draw till the item list knows it
    private record Drop(String id, String vanilla) {
        ItemStack icon() {
            return ItemIcons.of(id, vanilla, null, -1, false);
        }

        // item name is its rarity color, same as in game
        String rarity() {
            String name = id.isEmpty() ? "" : ItemRepo.coloredName(id);
            return name != null && name.length() > 1 && name.charAt(0) == '§' ? name.substring(0, 2) : "";
        }
    }

    private static final Map<String, Drop> GHOST_ITEMS = Map.of("Sorrow", new Drop("SORROW", "ghast_tear"), "Volta", new Drop("VOLTA", "paper"), "Plasma", new Drop("PLASMA", "paper"),
            "Ghosty Boots", new Drop("GHOST_BOOTS", "leather_boots"), "Bag of Cash", new Drop("BAG_OF_CASH", "gold_nugget"));
    private static final Map<String, Drop> NODE_ITEMS = Map.of("Enchanted Ender Pearl", new Drop("ENCHANTED_ENDER_PEARL", "ender_pearl"), "Enchanted Obsidian", new Drop("ENCHANTED_OBSIDIAN", "obsidian"),
            "Grand Experience Bottle", new Drop("GRAND_EXP_BOTTLE", "experience_bottle"), "Titanic Experience Bottle", new Drop("TITANIC_EXP_BOTTLE", "experience_bottle"), "Mite Gel", new Drop("MITE_GEL", "purple_dye"));
    private static final Map<String, Drop> OTHER_NODES = new HashMap<>();

    // node drops everyone gets are known, any other gets looked up by name once
    private static Drop nodeItem(String name) {
        Drop known = NODE_ITEMS.get(name);
        if (known != null) return known;
        return OTHER_NODES.computeIfAbsent(name, n -> {
            String id = ItemRepo.idForName(n);
            return new Drop(id == null ? "" : id, "ender_pearl");
        });
    }

    // what a drop is worth each: market price, 0 = none
    private static final ToDoubleFunction<String> MARKET = id -> Math.max(0, Gain.ofId(id));
    // the editor example has its own prices
    private static final Map<String, Double> EXAMPLE_PRICES = Map.of("SORROW", 1_920_000d, "VOLTA", 86_000d, "PLASMA", 412_000d, "GHOST_BOOTS", 3_450_000d, "BAG_OF_CASH", 2_100_000d,
            "ENCHANTED_ENDER_PEARL", 12_400d, "ENCHANTED_OBSIDIAN", 8_100d, "GRAND_EXP_BOTTLE", 4_300d, "MITE_GEL", 1_150d);
    private static final ToDoubleFunction<String> EXAMPLE = id -> EXAMPLE_PRICES.getOrDefault(id, 0d);

    private static double worth(Map<String, Integer> drops, Function<String, Drop> items, ToDoubleFunction<String> price) {
        double sum = 0;
        for (Map.Entry<String, Integer> e : drops.entrySet()) sum += price.applyAsDouble(items.apply(e.getKey()).id()) * e.getValue();
        return sum;
    }

    private static String dropCount(int n) {
        return "§f" + String.format(Locale.ROOT, "%,d", n) + " §7" + (n == 1 ? "drop" : "drops");
    }

    private static int count(Map<String, Integer> drops) {
        int n = 0;
        for (int c : drops.values()) n += c;
        return n;
    }

    // drops with icons, most valuable first: how many + what theyre worth in their own column when known
    private static List<HudElement.Row> dropRows(Map<String, Integer> drops, Function<String, Drop> items, ToDoubleFunction<String> price) {
        List<Map.Entry<String, Integer>> sorted = new ArrayList<>(drops.entrySet());
        sorted.sort((a, b) -> {
            int byWorth = Double.compare(price.applyAsDouble(items.apply(b.getKey()).id()) * b.getValue(), price.applyAsDouble(items.apply(a.getKey()).id()) * a.getValue());
            return byWorth != 0 ? byWorth : b.getValue() - a.getValue();
        });
        List<HudElement.Row> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : sorted) {
            Drop d = items.apply(e.getKey());
            double coins = price.applyAsDouble(d.id()) * e.getValue();
            out.add(new HudElement.Row(d.icon(), d.rarity() + e.getKey(), String.format(Locale.ROOT, "%,d", e.getValue()) + Card.COLUMN + (coins > 0 ? Prices.coins(coins) : ""), 0));
        }
        return out;
    }

    // armor stacks are a row only when switched on
    static boolean stacksShown() {
        Feature f = Features.get("combat_session");
        var o = f == null ? null : f.option("combat_session.armorStacks");
        return o != null && o.bool();
    }

    private static int armorStacks() {
        return stacksShown() ? Vitals.armorStacks() : 0;
    }

    // max stacks, the action bar writes them bold at 10
    private static final int MAX_STACKS = 10;

    private static List<HudElement.Row> combatRows(int stacks, int cocooned, Map<String, Integer> drops, long cash, ToDoubleFunction<String> price) {
        List<HudElement.Row> out = new ArrayList<>();
        if (stacks > 0)
            out.add(new HudElement.Row(LegacyItems.stack("minecraft:golden_chestplate"), "Armor stacks", Modern.fraction(String.valueOf(stacks), String.valueOf(MAX_STACKS), 'f', '7'), 0, Math.min(1, stacks / (double) MAX_STACKS), Modern.WARN));
        if (cocooned > 0) out.add(new HudElement.Row(LegacyItems.stack("minecraft:cobweb"), "Cocooned", String.valueOf(cocooned), Modern.INK));
        Map<String, Integer> items = new LinkedHashMap<>(drops);
        Integer bags = items.remove("Bag of Cash");
        if (!drops.isEmpty() || cash > 0) {
            out.add(new HudElement.Row(null, "Ghost drops", drops.isEmpty() ? "" : dropCount(count(drops)) + Card.COLUMN, Card.SECTION));
            out.addAll(dropRows(items, GHOST_ITEMS::get, price));
        }
        if (bags != null || cash > 0) {
            // the coins ghosts drop are their own number, too long for the worth column. §r goes back to the rows gold
            String coins = cash > 0 ? String.format(Locale.ROOT, "%,d", cash) : "";
            out.add(new HudElement.Row(GHOST_ITEMS.get("Bag of Cash").icon(), "Bag of cash", bags == null ? coins : "§f" + bags + "  §r" + coins, Modern.GOLD));
        }
        return out;
    }

    private static final Map<String, Integer> DEMO_GHOST = new LinkedHashMap<>(Map.of("Sorrow", 2, "Volta", 14, "Plasma", 3, "Ghosty Boots", 1));

    public static final HudElement COMBAT = new Card("combat_session", "Combat", "Combat", Zone.TOP_LEFT, 42, "combat_session") {
        private int stacks() {
            return Vitals.armorStacks();
        }

        @Override
        public boolean visible() {
            return sb() && (armorStacks() > 0 || cocoons > 0 || !ghostDrops.isEmpty() || ghostCash > 0);
        }

        // all this sessions drops in coins
        @Override
        public String headingValue() {
            double sum = demo ? worth(DEMO_GHOST, GHOST_ITEMS::get, EXAMPLE) + 1_250_000 : worth(ghostDrops, GHOST_ITEMS::get, MARKET) + ghostCash;
            return sum > 0 ? "+" + Prices.coins(sum) : "";
        }

        @Override
        protected boolean meters() {
            return false;
        }

        @Override
        boolean heavy(Row r) {
            return r.label().equals("Armor stacks");
        }

        @Override
        int minWidth() {
            return 150;
        }

        @Override
        public List<Row> rows() {
            return combatRows(armorStacks(), cocoons, ghostDrops, ghostCash, MARKET);
        }

        @Override
        public List<Row> demo() {
            return combatRows(stacksShown() ? 7 : 0, 3, DEMO_GHOST, 1_250_000, EXAMPLE);
        }
    };

    private static final Map<String, Integer> DRAGON_COLOURS = Map.of("Protector", 0xFF8FB8DE, "Old", 0xFFE3B778, "Wise", 0xFF5CD8FF, "Unstable", 0xFFB388FF, "Young", 0xFFE8EEF4, "Strong", 0xFFFF6B6B, "Superior", 0xFFFFD166);

    private static long dragonFull, dragonFullAt;

    // full hp for a dragon we saw spawn: the sidebars first number is its full hp so the highest said since. a
    // fight already going when we came has no known full hp
    private static long dragonFull(long hp) {
        if (dragon.isEmpty()) return -1;
        if (dragonFullAt != dragonAt) {
            dragonFullAt = dragonAt;
            dragonFull = System.currentTimeMillis() - dragonAt > 5000 ? -1 : 0;
        }
        if (dragonFull < 0) return -1;
        return dragonFull = Math.max(dragonFull, hp);
    }

    private static long number(String s) {
        if (s.isEmpty()) return -1;
        try {
            return (long) Double.parseDouble(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String fightTime(long seconds) {
        return seconds < 60 ? seconds + "s" : seconds / 60 + "m " + seconds % 60 + "s";
    }

    private static List<HudElement.Row> endRows(long hp, long full, long damage, long seconds, Map<String, Integer> found, ToDoubleFunction<String> price) {
        List<HudElement.Row> out = new ArrayList<>();
        if (hp >= 0) {
            String hpText = String.format(Locale.ROOT, "%,d", hp);
            // a full meter draws green, the hp stays red
            if (full > 0) out.add(HudElement.Row.meter("Dragon HP", Modern.fraction(hpText, String.format(Locale.ROOT, "%,d", full), 'c', '7'), 0, Math.min(0.999, hp / (double) full), Modern.BAD));
            else out.add(new HudElement.Row("Dragon HP", hpText, Modern.BAD));
        }
        if (damage >= 0) {
            out.add(new HudElement.Row("Your damage", "§b" + String.format(Locale.ROOT, "%,d", damage) + (full > 0 ? "  §7" + RowText.percent(damage / (double) full) : ""), 0));
            long dealt = full > 0 && hp >= 0 ? full - hp : 0;
            if (dealt > 0 && damage > 0) {
                double share = Math.min(1, damage / (double) dealt);
                out.add(HudElement.Row.meter("Your share", "§b" + RowText.percent(share) + " §7of " + Prices.coins(dealt) + " dealt", 0, share, Modern.AQUA));
            }
            if (seconds > 0 && damage > 0) out.add(new HudElement.Row("Your DPS", Prices.coins(damage / (double) seconds) + "/s", Modern.INK));
        }
        if (!found.isEmpty()) {
            double sum = worth(found, SlayerCombatCrimson::nodeItem, price);
            out.add(new HudElement.Row(null, "Ender nodes", dropCount(count(found)) + Card.COLUMN + (sum > 0 ? "+" + Prices.coins(sum) : ""), Card.SECTION));
            out.addAll(dropRows(found, SlayerCombatCrimson::nodeItem, price));
        }
        return out;
    }

    private static final Map<String, Integer> DEMO_NODES = new LinkedHashMap<>(Map.of("Enchanted Ender Pearl", 18, "Enchanted Obsidian", 12, "Grand Experience Bottle", 4, "Mite Gel", 3));

    public static final HudElement END = new Card("end_dragon", "The End", "Combat", Zone.TOP_LEFT, 43, "end_dragon") {
        private String shownDragon() {
            return demo ? "Protector" : dragon;
        }

        @Override
        public boolean visible() {
            return end() && (!dragon.isEmpty() || !sbLine(DRAGON_HP, 1).isEmpty() || !nodes.isEmpty());
        }

        @Override
        public String heading() {
            String d = shownDragon();
            return d.isEmpty() ? title : d + " Dragon";
        }

        @Override
        public int accent() {
            Integer c = DRAGON_COLOURS.get(shownDragon());
            return c != null ? c : super.accent();
        }

        @Override
        String tag() {
            String d = shownDragon();
            return d.isEmpty() ? "" : fightTime(demo ? 102 : (System.currentTimeMillis() - dragonAt) / 1000);
        }

        @Override
        void brief(Row r, List<Row> out) {
            switch (r.label()) {
                case "Your damage" -> out.add(worded(r, "Dmg"));
                case "Your share" -> out.add(worded(r, "Share"));
                case "Your DPS" -> out.add(worded(r, "DPS"));
                default -> out.add(worded(r, Compact.label(r.label()).replace("Enchanted ", "Ench ")));
            }
        }

        @Override
        void arrange(List<Row> rows) {
            beside(rows, "DPS", "Dmg");
        }

        @Override
        protected boolean meters() {
            return false;
        }

        @Override
        boolean heavy(Row r) {
            return r.label().equals("Dragon HP");
        }

        @Override
        int minWidth() {
            return 170;
        }

        @Override
        public List<Row> rows() {
            long hp = number(sbLine(DRAGON_HP, 1));
            return endRows(hp, hp >= 0 ? dragonFull(hp) : -1, number(sbLine(YOUR_DAMAGE, 1)), dragon.isEmpty() ? 0 : (System.currentTimeMillis() - dragonAt) / 1000, nodes, MARKET);
        }

        @Override
        public List<Row> demo() {
            return endRows(4_824_217, 15_000_000, 412_000, 102, DEMO_NODES, EXAMPLE);
        }
    };

    public static void register() {
        Features.add(new Feature("slayer_progress", "Slayer", "Slayer progress", "Kills towards the boss and the quest's progress from the sidebar and auto-slayer coins spent, on the Slayer card.", "").when(SlayerCombatCrimson::sb));
        Features.add(new Feature("slayer_spawn_soon", "Slayer", "Boss spawn warning", "A warning when the quest progress passes the percentage.", "").when(SlayerCombatCrimson::sb)
                .option(Option.integer("slayer_spawn_soon.percent", "Warn at (%)", "", 80, 50, 99))
                .option(Option.bool("slayer_spawn_soon.repeat", "Warn on every kill after that", "", false)));
        Features.add(new Feature("slayer_attunement", "Slayer", "Blaze attunement alert", "Which attunement the dagger needs, as a coloured alert.", "").when(SlayerCombatCrimson::sb));
        Features.add(new Feature("combat_session", "Combat", "Combat session", "Cocoons, ghost drops and bag-of-cash coins, and your armor stacks when you switch them on.", "").when(SlayerCombatCrimson::sb)
                .option(Option.bool("combat_session.armorStacks", "Armor stacks", "Your armor's stacks from the action bar, out of ten.", false)));
        Features.add(new Feature("end_dragon", "Combat", "Dragon fight", "Which dragon spawned and when, its HP and your damage from the sidebar; ender node drops.", "").when(SlayerCombatCrimson::end));
        ChatBus.register(ChatBus.OBSERVE + 16, "slayercombat", null, SlayerCombatCrimson::onChat);
        HudLayout.add(SLAYER);
        HudLayout.add(COMBAT);
        HudLayout.add(END);
    }
}
