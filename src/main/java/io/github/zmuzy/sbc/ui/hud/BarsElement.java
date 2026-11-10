// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui.hud;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Vitals;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.BooleanSupplier;

// classic status bars: health, mana, defense as slim bars with numbers above the hotbar where the vanilla
// hearts + hunger were (those get hidden, hunger means nothing on skyblock)
public final class BarsElement extends HudElement {
    public static final BarsElement I = new BarsElement();
    private static final int BAR_W = 88, BAR_H = 6, DEF_H = 3, GAP = 6, TEXT_H = 10; // together as wide as the hotbar
    private static final int PCT_GAP = 2, ICON_GAP = 2, EXTRA_GAP = 3, EHP_GAP = 6;
    private static final int WIDTH = BAR_W * 2 + GAP;

    private static final int HP = 0xFFFF5555, HP_DARK = 0xFF5A1414, HEAL = 0xFFFFAAAA;
    private static final int MANA = 0xFF55AAFF, MANA_DARK = 0xFF143A5A, OVER = 0xFF55FFFF;
    private static final int DEF = 0xFF55FF55, DEF_DARK = 0xFF145A14;

    private static final String HP_TEXT = "bars_hp_text", HP_BAR = "bars_hp_bar", MANA_TEXT = "bars_mana_text", MANA_BAR = "bars_mana_bar",
            DEF_TEXT = "bars_def_text", EHP_TEXT = "bars_ehp", LESS_TEXT = "bars_less", DEF_BAR = "bars_def_bar";

    private static double hpShown = -1, manaShown = -1;

    private BarsElement() {
        super("bars", "Status bars", "HUD", Zone.BOTTOM_CENTER, 10, "bars");
    }

    // bars replace the vanilla hearts + hunger
    public static boolean on() {
        Minecraft mc = Minecraft.getInstance();
        if (!Features.on("bars") || mc.player == null || mc.gui.hud.isHidden()) return false;
        if ("THE_RIFT".equals(GameState.island) && !opt("rift", true)) return false;
        return GameState.inSkyBlock || SbcMod.DEV;
    }

    @Override
    public boolean visible() {
        return on();
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

    private static boolean opt(String key, boolean def) {
        var f = Features.get("bars");
        var o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    public static boolean showsHealth() {
        return on() && opt("health", true);
    }

    public static boolean showsMana() {
        return on() && opt("mana", true);
    }

    public static boolean showsDefense() {
        return on() && opt("defense", true);
    }

    // hypixels health/defense/mana text should leave the action bar, our bars show it
    public static boolean filtersActionBar() {
        return on() && opt("hideActionBar", true);
    }

    // vanilla hearts, hunger, armor, air stay hidden while our bars show health + defense
    private static boolean loggedHide;

    public static boolean hidesVanilla() {
        boolean h = on() && opt("hideVanilla", true);
        if (h && !loggedHide) {
            loggedHide = true;
            SbcMod.LOG.info("Status bars on: hiding the vanilla hearts, hunger, armor and air");
        }
        return h;
    }

    private boolean showDefense() {
        return opt("defense", true) && (Vitals.defense > 0 || demo && !on());
    }

    private static boolean numbersOn() {
        return opt("numbers", true);
    }

    // --- what stays where the game puts it ---

    private static int topTextH() {
        return numbersOn() && (opt("health", true) && PartElement.home(HP_TEXT) || opt("mana", true) && PartElement.home(MANA_TEXT)) ? TEXT_H : 0;
    }

    private static int barsH() {
        return opt("health", true) && PartElement.home(HP_BAR) || opt("mana", true) && PartElement.home(MANA_BAR) ? BAR_H : 0;
    }

    private static int defTextH() {
        return numbersOn() && (PartElement.home(DEF_TEXT) || PartElement.home(EHP_TEXT) || PartElement.home(LESS_TEXT)) ? TEXT_H : 0;
    }

    private static int defBarH() {
        return PartElement.home(DEF_BAR) ? DEF_H : 0;
    }

    @Override
    public int width() {
        return WIDTH;
    }

    @Override
    public int height() {
        int top = topTextH() + barsH();
        int def = showDefense() ? defTextH() + defBarH() : 0;
        return Math.max(1, top + (def > 0 ? (top > 0 ? 2 : 0) + def : 0));
    }

    // --- drawing a bar ---

    private static String barStyle() {
        var f = Features.get("bars");
        var o = f == null ? null : f.option("style");
        return o == null ? "Glass" : o.choice();
    }

    static void bar(Ui ui, int x, int y, int w, int h, double pct, double extra, int light, int dark, int extraColor) {
        bar(ui, x, y, w, h, pct, extra, light, dark, extraColor, "Glass");
    }

    // bar in the chosen style: groove, dark shade, fill. healing/overflow as a segment after it
    static void bar(Ui ui, int x, int y, int w, int h, double pct, double extra, int light, int dark, int extraColor, String style) {
        double p = Math.max(0, Math.min(1, pct));
        switch (style) {
            case "Flat" -> {
                ui.rect(x, y, w, h, dark);
                int f = (int) Math.round(w * p), e = (int) Math.round(w * Math.max(0, Math.min(1 - p, extra)));
                if (f > 0) ui.rect(x, y, f, h, light);
                if (e > 0) ui.rect(x + f, y, e, h, extraColor);
            }
            case "Neon" -> {
                ui.rect(x, y, w, h, 0xA0000000);
                int f = (int) Math.round(w * p), e = (int) Math.round(w * Math.max(0, Math.min(1 - p, extra)));
                if (f > 0) {
                    ui.rect(x, y, f, h, Ui.alpha(light, 0.4f));
                    if (h >= 3) ui.rect(x, y + h / 2 - (h >= 5 ? 1 : 0), f, h >= 5 ? 2 : 1, Modern.lighten(light, 0.45f));
                    if (f < w) ui.rect(x + f - 1, y, 1, h, 0xFFFFFFFF);
                }
                if (e > 0) ui.rect(x + f, y, e, h, Ui.alpha(extraColor, 0.6f));
            }
            case "Blocks" -> {
                int count = 10, gap = 1, each = (w - gap * (count - 1)) / count;
                double reach = p * count, reachExtra = Math.min(1, p + Math.max(0, extra)) * count;
                for (int i = 0; i < count; i++) {
                    int bx = x + i * (each + gap), bw = i == count - 1 ? x + w - bx : each;
                    ui.rect(bx, y, bw, h, dark);
                    int ex = (int) Math.round(bw * Math.max(0, Math.min(1, reachExtra - i)));
                    if (ex > 0) ui.rect(bx, y, ex, h, extraColor);
                    int fi = (int) Math.round(bw * Math.max(0, Math.min(1, reach - i)));
                    if (fi > 0) ui.rect(bx, y, fi, h, light);
                }
            }
            case "Thin" -> {
                int th = Math.min(h, 2), ty = y + h - th;
                ui.rect(x, ty, w, th, 0xC0050911);
                int f = (int) Math.round(w * p), e = (int) Math.round(w * Math.max(0, Math.min(1 - p, extra)));
                if (f > 0) ui.rect(x, ty, f, th, light);
                if (e > 0) ui.rect(x + f, ty, e, th, extraColor);
            }
            default -> {
                ui.rect(x, y, w, h, 0xC0050911);
                ui.rect(x + 1, y + 1, w - 2, h - 2, dark);
                int f = (int) Math.round((w - 2) * p);
                if (f > 0) {
                    ui.gradV(x + 1, y + 1, f, h - 2, Modern.lighten(light, 0.2f), Modern.darken(light, 0.22f));
                    if (h >= 5) ui.rect(x + 1, y + 1, f, 1, Modern.lighten(light, 0.6f));
                }
                int e = (int) Math.round((w - 2) * Math.max(0, Math.min(1 - p, extra)));
                if (e > 0) ui.rect(x + 1 + f, y + 1, e, h - 2, extraColor);
                if (h >= 5) for (int q = 1; q < 4; q++) ui.rect(x + (w - 2) * q / 4 + 1, y + 1, 1, h - 2, 0x38000000);
            }
        }
    }

    private static String full(double v) {
        return String.format(java.util.Locale.ROOT, "%,d", Math.round(v));
    }

    // one colored piece of a text line, returns where the next starts
    static int piece(Ui ui, String s, int x, int y, int color) {
        ui.textShadow(s, x, y, color);
        return x + ui.width(s);
    }

    // number in full (0), 1 decimal (1) or whole (2), with k/M/B
    private static String form(double v, int style) {
        if (style == 0 || v < 1000) return full(v);
        double n = v >= 1e9 ? v / 1e9 : v >= 1e6 ? v / 1e6 : v / 1e3;
        String s = String.format(java.util.Locale.ROOT, style == 1 ? "%.1f" : "%.0f", n);
        return (s.endsWith(".0") ? s.substring(0, s.length() - 2) : s) + (v >= 1e9 ? "B" : v >= 1e6 ? "M" : "k");
    }

    // whats left of the percent
    private record Numbers(String now, String max, String extra) {
        int width(Ui ui) {
            return ui.width(now) + (max.isEmpty() ? 0 : ui.width("/" + max)) + ui.width(extra);
        }
    }

    // forms to try, most exact first: current, max (-1 = not written), extra. the current number is what you came
    // for so it stays exact as long as something else can shrink
    private static final int[][] FORMS = {{0, 0, 0}, {0, 0, 1}, {0, 1, 0}, {0, 1, 1}, {0, -1, 0}, {0, -1, 1}, {0, 2, 1}, {1, 1, 1}, {1, -1, 1}, {2, 2, 2}, {2, -1, 2}};

    // whole number that still says 2 digits: 14k says 14,250, 2M doesnt say 2,345,678
    private static boolean wholeSaysEnough(double v) {
        return (v >= 1e9 ? v / 1e9 : v >= 1e6 ? v / 1e6 : v / 1e3) >= 10;
    }

    // numbers as they fit next to the percent. no "rounded" = never whole numbers, null = nothing fits
    private static Numbers numbers(Ui ui, double now, double max, double extra, int room, boolean rounded) {
        Numbers n = null;
        for (int[] f : FORMS) {
            boolean last = f == FORMS[FORMS.length - 1];
            if (!rounded && (f[0] == 2 || f[1] == 2 || f[2] == 2)) continue;
            if (f[1] < 0 && Math.round(now) != Math.round(max) && !last) continue; // max only goes when the number already says it
            if (f[0] == 0 && f[1] == 2 && !wholeSaysEnough(max)) continue;
            n = new Numbers(form(now, f[0]), f[1] < 0 ? "" : form(max, f[1]), extra > 0 ? "+" + form(extra, f[2]) : "");
            if (n.width(ui) <= room) return n;
        }
        return rounded ? n : null;
    }

    // whole percent that never says 100 too early or 0 while theres some left. only if you asked for it
    private static String percent(double now, double max) {
        if (!opt("percent", false)) return "";
        long p = Math.round(100 * now / max);
        if (now < max && p >= 100) p = 99;
        if (now > 0 && p == 0) p = 1;
        return p + "%";
    }

    private static int room(Ui ui, String pct) {
        return pct.isEmpty() ? BAR_W : BAR_W - ui.width(pct) - PCT_GAP;
    }

    private record Fit(Numbers numbers, String icon) {
        int iconWidth(Ui ui) {
            return icon.isEmpty() ? 0 : ui.width(icon) + ICON_GAP;
        }
    }

    // numbers + whether the icon goes first. the max decides: icon stays if the exact number fits beside it on a
    // full bar, so it doesnt flicker on every heal
    private static Fit fit(Ui ui, double now, double max, double extra, String icon, String pct) {
        int room = room(ui, pct), iconW = ui.width(icon) + ICON_GAP;
        if (iconW + ui.width(full(max)) + ui.width("/" + form(max, 1)) <= room(ui, pct.isEmpty() ? "" : "100%")) {
            Numbers n = numbers(ui, now, max, extra, room - iconW, false);
            if (n != null) return new Fit(n, icon);
        }
        return new Fit(numbers(ui, now, max, extra, room, true), "");
    }

    // numbers left, percent right if you want it, current number brighter than the max
    private static void column(Ui ui, int x, int y, Fit fit, String pct, int pctColor, int color, int extraColor) {
        Numbers n = fit.numbers;
        if (!pct.isEmpty()) ui.textShadow(pct, x + BAR_W - ui.width(pct), y, pctColor); // first bc text drawn later is what gets trimmed
        int left = room(ui, pct) - n.width(ui) - fit.iconWidth(ui);
        int nx = x;
        if (!fit.icon.isEmpty()) nx = piece(ui, fit.icon, nx, y, color) + ICON_GAP;
        nx = piece(ui, n.now, nx, y, Modern.lighten(color, 0.55f));
        if (!n.max.isEmpty()) {
            nx = piece(ui, "/", nx, y, Modern.DIM);
            nx = piece(ui, n.max, nx, y, color);
        }
        // no room: extra + percent share the gap or the numbers look like a sum
        if (!n.extra.isEmpty()) piece(ui, n.extra, nx + (left >= EXTRA_GAP ? EXTRA_GAP : (Math.max(0, left) + PCT_GAP) / 2), y, extraColor);
    }

    // health percent warns when low. above 100 hypixel shows absorption, which it writes in gold
    private static int healthColor(double share) {
        if (share > 1) return Modern.GOLD;
        if (share <= 0.25) return Modern.BAD;
        if (share <= 0.5) return Modern.WARN;
        return Modern.INK;
    }

    // --- the pieces ---

    private record Now(double hp, double hpMax, double mana, double manaMax, long defense, long overflow, long healing, double ehp) {
        double reduction() {
            return defense / (defense + 100.0);
        }
    }

    private static Now now(boolean dm) {
        double hpMax = dm ? 4240 : Math.max(1, Vitals.hpMax()), def = dm ? 1024 : Vitals.defense;
        double hpNow = dm ? 3840 : Vitals.hpNow();
        var player = Minecraft.getInstance().player;
        if (!dm && player != null && "THE_RIFT".equals(GameState.island)) {
            // from skyblocker FancyStatusBars: rift has hearts not hp, 2 points = a heart unless you want points
            double div = opt("rift_hp", false) ? 1 : 2;
            hpNow = Math.round(player.getHealth()) / div;
            hpMax = Math.max(1, Math.round(player.getMaxHealth()) / div);
        }
        return new Now(hpNow, hpMax, dm ? 2410 : Vitals.mana, dm ? 3560 : Vitals.manaMax, dm ? 1024 : Vitals.defense, dm ? 370 : Vitals.overflow, dm ? 0 : Vitals.healing,
                dm ? hpMax * (1 + def / 100.0) : Vitals.ehp());
    }

    private static void hpText(Ui ui, int x, int y, Now n) {
        String pct = percent(n.hp, n.hpMax);
        Fit f = fit(ui, n.hp, n.hpMax, 0, "❤", pct);
        // healing is on the bar already, so in text only when it costs the numbers nothing
        if (n.healing > 0) {
            Numbers healed = new Numbers(f.numbers.now, f.numbers.max, "+" + form(n.healing, 1));
            if (healed.width(ui) + EXTRA_GAP + f.iconWidth(ui) <= room(ui, pct)) f = new Fit(healed, f.icon);
        }
        column(ui, x, y, f, pct, healthColor(n.hp / n.hpMax), HP, HEAL);
    }

    private static void manaText(Ui ui, int x, int y, Now n) {
        String pct = percent(n.mana, n.manaMax);
        column(ui, x, y, fit(ui, n.mana, n.manaMax, n.overflow, "✎", pct), pct, Modern.INK, MANA, OVER);
    }

    private static void hpBar(Ui ui, int x, int y, Now n) {
        if (hpShown < 0) hpShown = n.hp;
        hpShown += (n.hp - hpShown) * 0.3;
        bar(ui, x, y, BAR_W, BAR_H, hpShown / n.hpMax, n.healing / n.hpMax, HP, HP_DARK, HEAL, barStyle());
    }

    private static void manaBar(Ui ui, int x, int y, Now n) {
        if (manaShown < 0) manaShown = n.mana;
        manaShown += (n.mana - manaShown) * 0.3;
        double max = Math.max(1, n.manaMax);
        bar(ui, x, y, BAR_W, BAR_H, manaShown / max, n.overflow / max, MANA, MANA_DARK, OVER, barStyle());
        // full bar has no room for the overflow segment, it runs along the bottom edge instead
        if (n.overflow / max > 1 - Math.min(1, manaShown / max)) {
            int ow = (int) Math.round((BAR_W - 2) * Math.min(1, n.overflow / max));
            ui.rect(x + BAR_W - 1 - ow, y + BAR_H - 2, ow, 1, OVER);
        }
    }

    private static int defText(Ui ui, int x, int y, Now n) {
        int end = piece(ui, "❈", x, y, DEF);
        return piece(ui, full(n.defense), end + ICON_GAP, y, Modern.lighten(DEF, 0.25f));
    }

    private static int lessWidth(Ui ui, Now n) {
        return ui.width(Math.round(n.reduction() * 100) + "% less damage");
    }

    private static void lessText(Ui ui, int x, int y, Now n) {
        String less = Math.round(n.reduction() * 100) + "%";
        int rx = piece(ui, less, x, y, Modern.lighten(DEF, 0.55f));
        piece(ui, " less damage", rx, y, Modern.LABEL);
    }

    private static void ehpText(Ui ui, int x, int y, Now n, int room) {
        if (n.ehp <= 0) return;
        String name = "EHP ", worth = full(n.ehp);
        if (ui.width(name + worth) > room) worth = form(n.ehp, 1);
        if (ui.width(name + worth) > room) return;
        int mx = piece(ui, name, x, y, Modern.LABEL);
        piece(ui, worth, mx, y, Modern.lighten(HP, 0.4f));
    }

    // defense: the number, the health it adds up to and how much dmg it takes off
    private void defenseLine(Ui ui, int x, int y, Now n) {
        int end = PartElement.home(DEF_TEXT) ? defText(ui, x, y, n) : x;
        int rw = 0;
        if (PartElement.home(LESS_TEXT)) {
            rw = lessWidth(ui, n);
            lessText(ui, x + WIDTH - rw, y, n);
        }
        if (!PartElement.home(EHP_TEXT) || n.ehp <= 0) return;
        String name = "EHP ", worth = full(n.ehp);
        int room = WIDTH - rw - (end - x) - 2 * EHP_GAP;
        if (ui.width(name + worth) > room) worth = form(n.ehp, 1);
        int mw = ui.width(name + worth);
        if (mw > room) return;
        ehpText(ui, Math.max(end + EHP_GAP, Math.min(x + WIDTH - rw - EHP_GAP - mw, x + (WIDTH - mw) / 2)), y, n, room);
    }

    @Override
    public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
        Now n = now(demo && !on());
        int lx = x, rx = x + BAR_W + GAP, th = topTextH();
        boolean numbers = numbersOn(), showHp = opt("health", true), showMana = opt("mana", true) && n.manaMax > 0;
        if (numbers) {
            if (showHp && PartElement.home(HP_TEXT)) hpText(ui, lx, y, n);
            if (showMana && PartElement.home(MANA_TEXT)) manaText(ui, rx, y, n);
        }
        int by = y + th;
        if (showHp && PartElement.home(HP_BAR)) hpBar(ui, lx, by, n);
        if (showMana && PartElement.home(MANA_BAR)) manaBar(ui, rx, by, n);
        if (showDefense()) {
            int above = th + barsH();
            int dy = y + above + (above > 0 ? 2 : 0), dth = defTextH();
            if (dth > 0) {
                defenseLine(ui, lx, dy, n);
            }
            if (PartElement.home(DEF_BAR)) bar(ui, lx, dy + dth, WIDTH, DEF_H, n.reduction(), 0, DEF, DEF_DARK, DEF, barStyle());
        }
    }

    // --- same pieces as their own elements ---

    private static boolean partOn(String id, BooleanSupplier more) {
        return (on() || HudLayout.pinned(id)) && Features.on(id) && more.getAsBoolean();
    }

    private static void part(String id, String title, int priority, int w, int h, java.util.function.Supplier<int[]> at, BooleanSupplier more, PartElement.Paint paint) {
        Features.add(new Feature(id, "Bar parts", title, "A piece of the status bars: switch it off, or drag it anywhere on its own in the HUD editor.", "")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV));
        HudLayout.add(new PartElement(id, title, "HUD", I, priority, w, h, at, () -> partOn(id, more), paint));
    }

    private static Now nowFor(boolean demo) {
        return now(demo && !on());
    }

    public static void registerParts() {
        part(HP_TEXT, "Health numbers", 11, BAR_W, TEXT_H, () -> new int[]{0, 0}, () -> opt("health", true) && numbersOn(), (ui, x, y, d) -> hpText(ui, x, y, nowFor(d)));
        part(MANA_TEXT, "Mana numbers", 11, BAR_W, TEXT_H, () -> new int[]{BAR_W + GAP, 0}, () -> opt("mana", true) && numbersOn(), (ui, x, y, d) -> manaText(ui, x, y, nowFor(d)));
        part(HP_BAR, "Health bar", 11, BAR_W, BAR_H, () -> new int[]{0, topTextH()}, () -> opt("health", true), (ui, x, y, d) -> hpBar(ui, x, y, nowFor(d)));
        part(MANA_BAR, "Mana bar", 11, BAR_W, BAR_H, () -> new int[]{BAR_W + GAP, topTextH()}, () -> opt("mana", true), (ui, x, y, d) -> manaBar(ui, x, y, nowFor(d)));
        java.util.function.IntSupplier defRow = () -> {
            int above = topTextH() + barsH();
            return above + (above > 0 ? 2 : 0);
        };
        BooleanSupplier defense = () -> opt("defense", true) && (Vitals.defense > 0 || !on());
        part(DEF_TEXT, "Defense number", 11, 56, TEXT_H, () -> new int[]{0, defRow.getAsInt()}, () -> defense.getAsBoolean() && numbersOn(), (ui, x, y, d) -> defText(ui, x, y, nowFor(d)));
        part(EHP_TEXT, "Effective HP", 11, 62, TEXT_H, () -> new int[]{58, defRow.getAsInt()}, () -> defense.getAsBoolean() && numbersOn(), (ui, x, y, d) -> ehpText(ui, x, y, nowFor(d), 62));
        part(LESS_TEXT, "Less damage", 11, 62, TEXT_H, () -> new int[]{WIDTH - 62, defRow.getAsInt()}, () -> defense.getAsBoolean() && numbersOn(), (ui, x, y, d) -> lessText(ui, x, y, nowFor(d)));
        part(DEF_BAR, "Defense bar", 11, WIDTH, DEF_H, () -> new int[]{0, defRow.getAsInt() + defTextH()}, defense, (ui, x, y, d) -> {
            Now n = nowFor(d);
            bar(ui, x, y, WIDTH, DEF_H, n.reduction(), 0, DEF, DEF_DARK, DEF, barStyle());
        });
    }
}
