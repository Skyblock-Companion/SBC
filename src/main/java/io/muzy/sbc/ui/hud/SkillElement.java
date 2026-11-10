// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui.hud;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Profile;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.core.Vitals;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.Locale;

// skill progress instead of hypixels xp bar: the skill youre leveling (from the action bars "+12.5 Farming
// (96.7%)" or "(1,234/5,678)"), level from tab, bar, percent, xp into the level
public final class SkillElement extends HudElement {
    public static final SkillElement I = new SkillElement();
    private static final int BAR_W = 182, BAR_H = 6, LINE = 10, MID_GAP = 6, MIN_GAP = 4;
    // gap to the status bars under it
    private static final int BELOW = 5;
    private static final String P_NAME = "skill_name", P_GAIN = "skill_gain", P_PERCENT = "skill_percent", P_BAR = "skill_bar", P_NUMBERS = "skill_numbers";
    private static final long POP_MS = 450, RISE_MS = 800;
    private static final int XP = 0xFF7FE33A, XP_DARK = 0xFF234A10, MAX = 0xFFFFC94D, MAX_DARK = 0xFF4A3A10;
    private static final int NAME = 0xFFB6F27F, XP_NOW = 0xFFC4F58C, XP_NEED = Modern.GOLD, GAIN = Modern.AQUA, RATE = 0xFF7DF0B0;

    // how far back Vitals.xpPerHour looks
    private static final long PACE_WINDOW = 300_000;

    private static double shown = -1;
    private static String gainKey = "";
    private static long gainAt;
    private static String paceSkill = "";
    private static long paceGainAt, paceSince;

    private SkillElement() {
        super("skill_progress", "Skill progress", "HUD", Zone.BOTTOM_CENTER, 12, "skill_progress");
    }

    private static boolean opt(String key, boolean def) {
        var f = Features.get("skill_progress");
        var o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    // on at all (feature on, skyblock, hud shown)
    public static boolean on() {
        Minecraft mc = Minecraft.getInstance();
        if (!Features.on("skill_progress") || mc.player == null || mc.gui.hud.isHidden()) return false;
        return GameState.inSkyBlock || SbcMod.DEV;
    }

    // hypixels xp bar (vanilla xp bar + level) shows your level and stays, unless you choose to hide it
    public static boolean hidesXpBar() {
        return on() && opt("hideXpBar", false);
    }

    // "+12.5 Farming (96.7%)" leaves the action bar, this bar shows the same
    public static boolean hidesActionBar() {
        return on() && opt("hideActionBar", true);
    }

    private static boolean fresh() {
        return !Vitals.skill.isEmpty() && System.currentTimeMillis() - Vitals.skillAt < 60_000;
    }

    @Override
    public boolean visible() {
        return on() && (fresh() || !Vitals.skill.isEmpty() && opt("always", false));
    }

    @Override
    public String heading() {
        return "";
    }

    @Override
    public boolean stretchable() {
        return false;
    }

    @Override
    public List<Row> demo() {
        return List.of();
    }

    // --- numbers ---

    // "5,678k" -> 5678000, "" or junk -> -1
    static double num(String s) {
        if (s == null || s.isEmpty()) return -1;
        String t = s.replace(",", "").trim();
        double mul = 1;
        char c = Character.toLowerCase(t.charAt(t.length() - 1));
        if (c == 'k') mul = 1e3;
        else if (c == 'm') mul = 1e6;
        else if (c == 'b') mul = 1e9;
        if (mul != 1) t = t.substring(0, t.length() - 1);
        try {
            return Double.parseDouble(t) * mul;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // what the bar shows: own numbers, or your goal / levels past the cap when those apply
    static Object[] state(boolean dm) {
        Object[] base = baseState(dm);
        return dm ? base : io.muzy.sbc.skills.SkillCards.forBar(base);
    }

    // skill, level (-1 ?), percent (-1 ?), xp into level, xp needed (-1 ?)
    static Object[] baseState(boolean dm) {
        if (dm) return new Object[]{"Combat", 50, 84.01, 3_612_500.0, 4_300_000.0};
        String skill = Vitals.skill;
        double[] tab = GameState.tabSkills.get(skill);
        int level = tab == null ? rememberedLevel(skill) : (int) tab[0];
        double pct = Vitals.skillPct >= 0 ? Vitals.skillPct : tab == null ? -1 : tab[1];
        double cur = -1, need = -1;
        String nums = Vitals.skillNumbers;
        int slash = nums.indexOf('/');
        if (slash > 0) {
            cur = num(nums.substring(0, slash));
            need = num(nums.substring(slash + 1));
            if (cur >= 0 && need > 0) pct = 100.0 * cur / need; // fresher than the tab list
        }
        if (need < 0 && level >= 0 && level + 1 < Profile.SKILL_XP.length && !ownTable(skill)) {
            need = Profile.SKILL_XP[level + 1] - Profile.SKILL_XP[level];
            if (pct >= 0) cur = Math.round(need * pct / 100.0);
        }
        return new Object[]{skill, level, pct, cur, need};
    }

    // runecrafting + social use a way smaller table than Profile.SKILL_XP, only hypixels own numbers mean anything
    private static boolean ownTable(String skill) {
        return skill.equals("Runecrafting") || skill.equals("Social");
    }

    // level while tab doesnt show this skill: last thing it said vs the api, higher one wins bc levels never go
    // down. Profile.current can be someone you looked up or another profile of yours
    private static int rememberedLevel(String skill) {
        Profile.Data d = Profile.current;
        if (d != null && d.fromGame) {
            // already this profiles own, and that list has every profile
            Profile.Level own = d.skills.get(skill);
            return own == null ? -1 : own.level();
        }
        double[] seen = Store.I.observedSkills.get(skill);
        int level = seen == null ? -1 : (int) seen[0];
        var player = Minecraft.getInstance().player;
        if (d == null || player == null || ownTable(skill) || !player.getUUID().toString().replace("-", "").equalsIgnoreCase(d.uuid)) return level;
        if (!GameState.profileName.isEmpty() && !GameState.profileName.equalsIgnoreCase(d.profileName)) return level;
        Profile.Level known = d.skills.get(skill);
        return known == null ? level : Math.max(level, known.level());
    }

    // Vitals mixes every skill from the last 5 min, so right after a switch it says nothing about this one yet
    private static boolean paceIsThisSkills() {
        long now = System.currentTimeMillis();
        if (!Vitals.skill.equals(paceSkill)) {
            if (!paceSkill.isEmpty() && now - paceGainAt < PACE_WINDOW) paceSince = now;
            paceSkill = Vitals.skill;
        }
        paceGainAt = Vitals.skillAt;
        return now - paceSince >= PACE_WINDOW;
    }

    private static String xp(double v) {
        return String.format(Locale.ROOT, "%,d", Math.round(v));
    }

    // "688k", "1.2M", "12.5k": short but still says what it is
    private static String brief(double v) {
        if (v >= 1e9) return trimZero(String.format(Locale.ROOT, "%.1f", v / 1e9)) + "B";
        if (v >= 999_500) return trimZero(String.format(Locale.ROOT, "%.1f", v / 1e6)) + "M";
        if (v >= 100_000) return Math.round(v / 1e3) + "k";
        if (v >= 1_000) return trimZero(String.format(Locale.ROOT, "%.1f", v / 1e3)) + "k";
        return String.valueOf(Math.round(v));
    }

    private static String trimZero(String s) {
        return s.endsWith(".0") ? s.substring(0, s.length() - 2) : s;
    }

    // "+1,234.5" -> "+1.2k" when the line above the bar is crowded
    private static String briefGain(String gain) {
        double v = num(gain.startsWith("+") ? gain.substring(1) : gain);
        return v < 0 ? gain : "+" + (v >= 1000 ? brief(v) : trimZero(String.format(Locale.ROOT, "%.0f", v)));
    }

    // how long the rest of the level takes at this pace
    private static String eta(double left, double perHour) {
        long m = Math.max(1, Math.round(left / perHour * 60));
        if (m < 60) return "~" + m + "m";
        if (m < 1440) return "~" + m / 60 + "h " + m % 60 + "m";
        return "~" + m / 1440 + "d " + m % 1440 / 60 + "h";
    }

    private static boolean maxed(int level, double pct, double need) {
        // a level past the table only has an xp needed when levels past the cap get counted
        return need == 0 || pct >= 100 || level + 1 >= Profile.SKILL_XP.length && need < 0;
    }

    // --- layout ---

    private boolean oneLine() {
        return opt("oneLine", false);
    }

    private static boolean boxed() {
        return opt("background", false);
    }

    // no box = bar runs full hotbar width, level with the status bars
    private int inset(Theme t) {
        return boxed() ? pad(t, style()) : 0;
    }

    @Override
    public int width() {
        return BAR_W; // hotbar width so the stack above lines up
    }

    private static boolean topHome() {
        return PartElement.home(P_NAME) || PartElement.home(P_GAIN) || PartElement.home(P_PERCENT);
    }

    private static boolean barHome() {
        return opt("bar", true) && PartElement.home(P_BAR);
    }

    private boolean numbersHome() {
        return !oneLine() && opt("numbers", true) && PartElement.home(P_NUMBERS);
    }

    @Override
    public int height() {
        int p = inset(Theme.current());
        int h = (topHome() ? LINE : 0) + (barHome() ? BAR_H + 2 : 0);
        if (numbersHome()) h += LINE;
        return Math.max(1, h + 2 * p + BELOW);
    }

    private static long readingAt;

    // once a sec, where the skill xp is. xp/h comes from how it grows
    public static void rateTick(net.minecraft.client.Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - readingAt < 1000 || Vitals.skill.isEmpty()) return;
        readingAt = now;
        Vitals.sessionTick(now);
        Object[] s = baseState(false);
        Vitals.xpReading(Vitals.skill, (Double) s[3]);
    }

    // a real gain moved one of these, the same text again doesnt
    private static void noteGain() {
        String key = Vitals.skill + "|" + Vitals.skillGain + "|" + Vitals.skillPct + "|" + Vitals.skillNumbers;
        if (key.equals(gainKey)) return;
        gainKey = key;
        gainAt = System.currentTimeMillis();
    }

    // last gain: swells + flashes white when xp comes in, a copy floats up
    private static int gainPiece(Ui ui, String gain, int x, int y, boolean sample) {
        if (gain.isEmpty()) return x;
        int w = ui.width(gain);
        long age = sample ? Long.MAX_VALUE : System.currentTimeMillis() - gainAt;
        if (age >= POP_MS && age >= RISE_MS) return BarsElement.piece(ui, gain, x, y, GAIN);
        float pop = Math.min(1f, age / (float) POP_MS), rise = Math.min(1f, age / (float) RISE_MS);
        float scale = 1f + 0.4f * (1 - pop) * (1 - pop);
        int color = Ui.lerpColor(0xFFFFFFFF, GAIN | 0xFF000000, pop);
        var pose = ui.g.pose();
        if (rise < 1) ui.g.text(ui.font, gain, x, Math.round(y - 9 * rise), Ui.alpha(GAIN | 0xFF000000, (1 - rise) * 0.7f), true);
        pose.pushMatrix();
        pose.translate(x + w / 2f, y + 4f);
        pose.scale(scale, scale);
        ui.g.text(ui.font, gain, -w / 2, -4, color, true);
        pose.popMatrix();
        return x + w;
    }

    private static String barStyle() {
        var f = Features.get("skill_progress");
        var o = f == null ? null : f.option("style");
        return o == null ? "Glass" : o.choice();
    }

    private static void barPiece(Ui ui, int x, int y, int w, double pct, boolean maxed) {
        double target = pct >= 0 ? pct / 100.0 : 0;
        if (shown < 0 || Math.abs(target - shown) > 0.5) shown = target;
        shown += (target - shown) * 0.25;
        int fill = maxed ? MAX : XP;
        BarsElement.bar(ui, x, y, w, BAR_H, shown, 0, fill, maxed ? MAX_DARK : XP_DARK, fill, barStyle());
    }

    @Override
    public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
        Theme t = Theme.current();
        var st = style();
        boolean dm = demo && !fresh();
        Object[] s = state(dm);
        String skill = (String) s[0];
        int level = (Integer) s[1];
        double pct = (Double) s[2], cur = (Double) s[3], need = (Double) s[4];
        boolean maxed = maxed(level, pct, need);
        if (maxed) pct = 100;
        int w = width(), h = height(), p = inset(t);
        if (boxed()) t.panel(ui, x, y, w, h - BELOW, st);
        int cx = x + p, cy = y + p, cw = w - 2 * p;
        if (!dm) noteGain();
        int name = st.titleColor() == null ? NAME : st.titleColor();
        int number = st.textColor() == null ? Modern.INK : st.textColor();
        boolean nameHome = PartElement.home(P_NAME), gainHome = PartElement.home(P_GAIN);
        String gain = !gainHome ? "" : dm ? "+600" : Vitals.skillGain;
        double xph = dm ? 1_240_000 : Vitals.xpPerHour();
        String rate = gainHome && xph > 0 && opt("xpPerHour", true) ? brief(xph) + "/h" : "";
        if (!nameHome) skill = "";
        String lv = nameHome && level >= 0 ? " " + level : "";
        boolean showPct = pct >= 0 && opt("percent", true) && PartElement.home(P_PERCENT), numbers = opt("numbers", true);
        String pct2 = !showPct ? "" : maxed ? "100%" : String.format(Locale.ROOT, "%.2f%%", pct);
        String pct1 = !showPct ? "" : maxed ? "100%" : String.format(Locale.ROOT, "%.1f%%", pct);
        int percent = maxed ? Modern.GOLD : number;

        // one line: numbers join the percent on the right, middle gets the rest
        boolean inline = oneLine() && numbers && !maxed && cur >= 0 && need > 0;
        String shortNow = inline ? brief(cur) : "", shortNeed = inline ? brief(need) : "";
        int inlineW = inline ? ui.width(shortNow + "/" + shortNeed) + (showPct ? MID_GAP : 0) : 0;
        Top top = Top.fit(ui, cw - ui.width(skill + lv) - inlineW, pct2, pct1, gain, rate);
        int rightW = inlineW + ui.width(top.pct), midW = top.middle(ui);
        String shownSkill = ui.ellipsize(skill, Math.max(12, cw - rightW - MIN_GAP - ui.width(lv)));
        int leftEnd = BarsElement.piece(ui, shownSkill, cx, cy, name);
        leftEnd = BarsElement.piece(ui, lv, leftEnd, cy, maxed ? Modern.GOLD : number);
        int rx = cx + cw - rightW;
        if (inline) {
            rx = BarsElement.piece(ui, shortNow, rx, cy, XP_NOW);
            rx = BarsElement.piece(ui, "/", rx, cy, Modern.DIM);
            rx = BarsElement.piece(ui, shortNeed, rx, cy, XP_NEED) + (showPct ? MID_GAP : 0);
        }
        BarsElement.piece(ui, top.pct, rx, cy, percent);
        if (midW > 0) {
            // the seam between gain and rate holds the bar middle so different lengths dont shove things around, as long
            // as the ends leave room
            int centre = cx + cw / 2;
            int start = top.gain.isEmpty() || top.rate.isEmpty() ? centre - midW / 2 : centre - top.gap / 2 - ui.width(top.gain);
            int mx = Math.max(leftEnd + MIN_GAP, Math.min(cx + cw - rightW - MIN_GAP - midW, start));
            mx = gainPiece(ui, top.gain, mx, cy, dm);
            BarsElement.piece(ui, top.rate, mx + (top.gain.isEmpty() ? 0 : top.gap), cy, RATE);
        }
        if (topHome()) cy += LINE;
        if (barHome()) {
            barPiece(ui, cx, cy, cw, pct, maxed);
            cy += BAR_H + 2;
        }
        boolean steady = dm || paceIsThisSkills();
        if (numbersHome()) numbersLine(ui, cx, cy, cw, cur, need, steady && opt("xpPerHour", true) ? xph : 0, maxed);
    }

    // what the line above says right + middle: the richest form that still leaves the skill room
    private record Top(String pct, String gain, String rate, int gap) {
        int middle(Ui ui) {
            return ui.width(gain) + ui.width(rate) + (gain.isEmpty() || rate.isEmpty() ? 0 : gap);
        }

        static Top fit(Ui ui, int room, String pct2, String pct1, String gain, String rate) {
            String brief = gain.isEmpty() ? "" : briefGain(gain);
            Top[] forms = {new Top(pct2, gain, rate, MID_GAP), new Top(pct1, gain, rate, MID_GAP), new Top(pct1, gain, rate, MIN_GAP),
                    new Top(pct1, brief, rate, MIN_GAP), new Top(pct1, brief, "", 0), new Top(pct1, "", "", 0)};
            for (Top t : forms) {
                int mid = t.middle(ui);
                if (ui.width(t.pct) + mid + (mid > 0 ? 2 * MIN_GAP : MIN_GAP) <= room) return t;
            }
            return forms[forms.length - 1];
        }
    }

    // under the bar: xp into the level / needed, then whats left + how long if theres room
    private static void numbersLine(Ui ui, int x, int y, int w, double cur, double need, double xph, boolean maxed) {
        if (maxed) {
            String label = "MAX LEVEL";
            ui.textShadow(label, x + w - ui.width(label), y, Modern.GOLD);
            if (need == 0 && cur > 0) {
                int nx = BarsElement.piece(ui, xp(cur), x, y, XP_NOW);
                BarsElement.piece(ui, " XP", nx, y, Modern.LABEL);
            }
            return;
        }
        if (cur < 0 || need <= 0) {
            ui.textShadow("-", x, y, Modern.DIM);
            return;
        }
        String now = xp(cur), of = xp(need);
        int numbersW = ui.width(now) + ui.width("/") + 4 + ui.width(of), unitW = ui.width(" XP");
        double left = Math.max(0, need - cur);
        String amount = brief(left), eta = xph > 0 && left > 0 ? eta(left, xph) : "", word = " left";
        // from skyhanni SkillProgress.addActionsLeft: how many more of the last gain
        double each = num(Vitals.skillGain.startsWith("+") ? Vitals.skillGain.substring(1) : Vitals.skillGain);
        if (opt("actions_left", false) && each > 0) {
            amount = brief(Math.ceil(left / each) + 1);
            word = " to go";
        }
        // what fits, most first. the word XP comes before anything else, an amount never goes without its word. each:
        // amount, word, time, time word
        String[][] options = eta.isEmpty()
                ? new String[][]{{amount, word, "", ""}}
                : new String[][]{{amount, word, " " + eta, ""}, {amount, word, "", ""}, {"", "", eta, " left"}};
        boolean unit = true;
        String[] tail = null;
        for (String[] o : options) {
            int tailW = ui.width(o[0]) + ui.width(o[1]) + ui.width(o[2]) + ui.width(o[3]);
            if (numbersW + MID_GAP + tailW > w) continue;
            unit = numbersW + unitW + MID_GAP + tailW <= w;
            tail = o;
            break;
        }
        int nx = BarsElement.piece(ui, now, x, y, XP_NOW);
        nx = BarsElement.piece(ui, "/", nx + 2, y, Modern.DIM);
        nx = BarsElement.piece(ui, of, nx + 2, y, XP_NEED);
        if (unit) BarsElement.piece(ui, " XP", nx, y, Modern.LABEL);
        if (tail == null) return;
        int rx = x + w - ui.width(tail[0]) - ui.width(tail[1]) - ui.width(tail[2]) - ui.width(tail[3]);
        rx = BarsElement.piece(ui, tail[0], rx, y, XP_NOW);
        rx = BarsElement.piece(ui, tail[1], rx, y, Modern.LABEL);
        rx = BarsElement.piece(ui, tail[2], rx, y, GAIN);
        BarsElement.piece(ui, tail[3], rx, y, Modern.LABEL);
    }

    private interface PartPaint {
        void draw(Ui ui, int x, int y, Object[] state);
    }

    private static void part(String id, String title, int w, int h, java.util.function.Supplier<int[]> at, PartPaint paint) {
        Features.add(new Feature(id, "Bar parts", title, "A piece of the skill bar: switch it off, or drag it anywhere on its own in the HUD editor.", "")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV));
        HudLayout.add(new PartElement(id, title, "HUD", I, 13, w, h, at, () -> (on() && fresh() || HudLayout.pinned(id)) && Features.on(id),
                (ui, x, y, demo) -> paint.draw(ui, x, y, state(demo && !fresh()))));
    }

    public static void register() {
        Feature f = new Feature("skill_progress", "HUD", "Skill progress", "The skill you are levelling as a bar above the status bars: level, percent, XP into the level out of the XP needed, last gain and XP per hour. Replaces Hypixel's XP bar.", "")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.bool("skill_progress.hideXpBar", "Hide Hypixel's XP bar", "The vanilla experience bar and level number, which Hypixel uses for skill progress.", false))
                .option(Option.bool("skill_progress.hideActionBar", "Hide Hypixel's skill text", "The +12.5 Farming (96.7%) part of the action bar above the hotbar.", true))
                .option(Option.bool("skill_progress.bar", "Progress bar", "", true))
                .option(Option.bool("skill_progress.percent", "Percent", "", true))
                .option(Option.bool("skill_progress.numbers", "XP numbers (current / needed)", "", true))
                .option(Option.bool("skill_progress.xpPerHour", "XP per hour", "", true))
                .option(Option.bool("skill_progress.always", "Always show", "Stays after the last XP instead of going a minute later.", false))
                .option(Option.bool("skill_progress.actions_left", "Actions left", "How many more of your last gain to the next level, in place of the XP left.", false))
                .option(Option.bool("skill_progress.oneLine", "One line", "Skill, percent and numbers on a single line above the bar.", false))
                .option(Option.bool("skill_progress.background", "Background box", "", false))
                .option(Option.choice("skill_progress.style", "Bar style", "", "Glass", "Glass", "Flat", "Neon", "Blocks", "Thin"));
        Features.add(f);
        HudLayout.add(I);
        part(P_NAME, "Skill name and level", 96, LINE, () -> new int[]{0, 0}, (ui, x, y, s) -> {
            int nx = BarsElement.piece(ui, (String) s[0], x, y, NAME);
            if ((Integer) s[1] >= 0) BarsElement.piece(ui, " " + s[1], nx, y, Modern.INK);
        });
        part(P_GAIN, "Skill last gain and XP an hour", 92, LINE, () -> new int[]{45, 0}, (ui, x, y, s) -> {
            boolean sample = !fresh();
            if (!sample) noteGain();
            int nx = gainPiece(ui, sample ? "+600" : Vitals.skillGain, x, y, sample);
            double xph = sample ? 1_240_000 : Vitals.xpPerHour();
            if (xph > 0) BarsElement.piece(ui, brief(xph) + "/h", nx + MID_GAP, y, RATE);
        });
        part(P_PERCENT, "Skill percent", 40, LINE, () -> new int[]{BAR_W - 40, 0}, (ui, x, y, s) -> {
            if ((Double) s[2] >= 0) BarsElement.piece(ui, String.format(Locale.ROOT, "%.2f%%", (Double) s[2]), x, y, Modern.INK);
        });
        part(P_BAR, "Skill bar", BAR_W, BAR_H, () -> new int[]{0, topHome() ? LINE : 0}, (ui, x, y, s) -> barPiece(ui, x, y, BAR_W, (Double) s[2], maxed((Integer) s[1], (Double) s[2], (Double) s[4])));
        part(P_NUMBERS, "Skill XP numbers", BAR_W, LINE, () -> new int[]{0, (topHome() ? LINE : 0) + (barHome() ? BAR_H + 2 : 0)}, (ui, x, y, s) ->
                numbersLine(ui, x, y, BAR_W, (Double) s[3], (Double) s[4], !fresh() ? 1_240_000 : Vitals.xpPerHour(), maxed((Integer) s[1], (Double) s[2], (Double) s[4])));
    }
}
