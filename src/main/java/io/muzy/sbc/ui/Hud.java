// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.Config;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.SbTime;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.nav.Navigator;
import io.muzy.sbc.tasks.Guide;
import io.muzy.sbc.tasks.Routine;
import io.muzy.sbc.track.Tracker;
import io.muzy.sbc.ui.hud.HudElement.Row;
import io.muzy.sbc.ui.hud.RowText;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.List;

// the routine card on screen: ring for today, guided step, next open tasks, next event, nav and earnings.
// plus minimap and toasts
public final class Hud {
    private static final Ui ui = new Ui();

    static {
        // no enchant shimmer on small hud icons, a shimmering item gets redrawn every frame
        ui.plainIcons = true;
        // + a new layer every 64 pieces (see Ui.strataEvery), so a piece gets compared with 63 max
        ui.strataEvery = 64;
    }
    private static long briefingUntil;
    private Hud() {}

    public static void showBriefing() {
        briefingUntil = System.currentTimeMillis() + 14_000;
    }

    private static boolean hideForDebug(Minecraft mc) {
        if (!mc.gui.hud.getDebugOverlay().showDebugScreen()) return false;
        var f = io.muzy.sbc.feature.Features.get("appearance");
        var o = f == null ? null : f.option("appearance.hide_in_debug");
        return o != null && o.bool();
    }

    private static boolean replayOn() {
        var f = io.muzy.sbc.feature.Features.get("appearance");
        var o = f == null ? null : f.option("appearance.hud_replay");
        return o == null || o.bool();
    }

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        io.muzy.sbc.events.hoppity.Hoppity.flash(g, g.guiWidth(), g.guiHeight());
        Minecraft mc = Minecraft.getInstance();
        io.muzy.sbc.core.Perf.enabled = Config.I.perfOverlay || io.muzy.sbc.core.FrameProbe.on;
        io.muzy.sbc.core.Perf.frame();
        io.muzy.sbc.core.PerfSampler.frame(Config.I.perfOverlay);
        int elementsLastFrame = Ui.elements;
        Ui.elements = 0;
        if (mc.gui.hud.isHidden() || mc.player == null) return;
        // skyhanni hideGuiInDebugMenu
        if (hideForDebug(mc)) return;
        ui.begin(g, -1, -1);
        int sw = g.guiWidth();
        if (Config.I.perfOverlay) {
            int els = elementsLastFrame, sh = g.guiHeight();
            if (!HudTexture.show("perf", g, ui, sw + "x" + sh + " perf", sw, sh, false, mine -> drawPerf(sw, els))) drawPerf(sw, els);
        }
        var screen = mc.gui.screen();
        if (io.muzy.sbc.ui.screens.LegacyHost.unwrap(screen) instanceof CompanionScreen) return; // the menu draws its own toasts
        // drawn after menus now so stay out of inventories and other screens (chat is fine)
        boolean clear = screen == null || screen instanceof net.minecraft.client.gui.screens.ChatScreen;
        boolean skyblock = GameState.inSkyBlock || SbcMod.DEV;
        // the layout engine places everything on the left and right edges
        long t0 = io.muzy.sbc.core.Perf.begin();
        if (clear && skyblock) {
            // drawn every 50ms, the frames between put the same finished pieces back (HudReplay)
            String key = sw + "x" + g.guiHeight() + (screen == null ? "" : " chat") + " " + Config.I.hudAll + " " + Config.I.hudScale;
            // as a picture first, then the replay of pieces, else live
            if (!HudTexture.show(g, ui, key, sw, g.guiHeight(), HudLayout.moving, mine -> HudLayout.render(ui, mine, sw)) && (!replayOn() || !HudReplay.replay(g, key))) {
                int drawn = Ui.elements;
                HudReplay.begin();
                try {
                    HudLayout.render(ui, g, sw);
                } finally {
                    HudReplay.end(key, HudLayout.moving, Ui.elements - drawn);
                }
            }
        } else if (skyblock && screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> && !(screen instanceof io.muzy.sbc.ui.storage.StorageOverlayScreen)) {
            int sh = g.guiHeight();
            String key = sw + "x" + sh + " menu " + Config.I.hudAll + " " + Config.I.hudScale;
            if (!HudTexture.show(g, ui, key, sw, sh, HudLayout.moving, mine -> HudLayout.render(ui, mine, sw, sh, false, true))) HudLayout.render(ui, g, sw, sh, false, true);
        }
        io.muzy.sbc.core.Perf.end("hud:layout total", t0);
        int tr = HudLayout.bottom(HudLayout.Zone.TOP_RIGHT);
        t0 = io.muzy.sbc.core.Perf.begin();
        // under the right column, but a toast below the screen is useless
        g.nextStratum();
        Toasts.render(ui, sw, tr > 0 ? Math.max(8, Math.min(tr + 4, g.guiHeight() - 100)) : 8);
        if (clear) Dialogue.draw(ui, sw, g.guiHeight(), false);
        io.muzy.sbc.core.Perf.end("hud:toasts+dialogue", t0);
        t0 = io.muzy.sbc.core.Perf.begin();
        io.muzy.sbc.core.Perf.end("hud:vitals", t0);
        if (!clear || !skyblock) return;

        if (System.currentTimeMillis() < briefingUntil) drawBriefing(sw);
        else if (Navigator.target() != null && !Navigator.warpHint.isEmpty() ) warpHintClassic(ui, sw, Navigator.target().name(), GameState.pretty(Navigator.target().island()), Navigator.warpHint);
        else if (Navigator.target() != null && !Navigator.warpHint.isEmpty()) {
            String a = "➜ " + Navigator.target().name() + " is on " + GameState.pretty(Navigator.target().island());
            String b = "Press J to " + Navigator.warpHint;
            int bw = Math.max(ui.width(a), ui.width(b)) + 24, bx = sw / 2 - bw / 2;
            ui.panel(bx, 22, bw, 30, 8, 0xE00C0F1C);
            ui.roundGradH(bx, 22, bw, 2, 1, Ui.VIOLET, Ui.CYAN);
            ui.textCenter(a, sw / 2, 28, Ui.TEXT);
            ui.textCenter(b, sw / 2, 40, Ui.GREEN);
        }
    }

    // profiler overlay, top center. plain text on a dark strip so it costs basically nothing
    private static void drawPerf(int sw, int elements) {
        var lines = io.muzy.sbc.core.Perf.report(12);
        lines.add(1, elements + " gui elements/frame from this mod");
        int w = 0;
        for (String l : lines) w = Math.max(w, ui.width(l));
        int x = sw / 2 - w / 2 - 4, y = 4;
        ui.rect(x, y, w + 8, lines.size() * 10 + 6, 0xA0000000);
        for (int i = 0; i < lines.size(); i++) ui.text(lines.get(i), x + 4, y + 3 + i * 10, i == 0 ? 0xFFFFD866 : i == 1 ? 0xFF9AD66B : 0xFFE0E0E0);
    }

    private static int periodColor(SbTime.Period p) {
        return switch (p) {
            case DAILY -> 0xFF85B7EB;
            case WEEKLY -> 0xFFAFA9EC;
            case MONTHLY -> Ui.PINK;
            case EVENT, SB_DAY, SB_MONTH, SB_YEAR -> Ui.GOLD;
            default -> Ui.MUTED;
        };
    }

    static boolean nav() {
        return Navigator.target() != null;
    }

    static int routineHeight() {
        long now = System.currentTimeMillis();
        List<Routine.Entry> today = io.muzy.sbc.tasks.RoutinePreset.tasks();
        int rows = (int) today.stream().filter(e -> !e.complete()).limit(Config.I.hudMaxTasks).count();
        boolean guided = Guide.active && !Guide.step.isEmpty();
        List<SbTime.Event> evs = Config.I.hudShowEvents ? SbTime.events(now, 3 * 3_600_000L) : List.of();
        return 36 + (guided ? 22 : 0) + Math.max(1, rows) * 13 + (evs.isEmpty() ? 0 : 15) + (nav() ? 13 : 0) + 18;
    }

    private static ItemStack item(String id) {
        return io.muzy.sbc.core.LegacyItems.stack(id);
    }

    private static String eventIcon(String id) {
        return switch (id) {
            case "dark_auction" -> "amethyst_shard";
            case "jacob" -> "wheat";
            case "spooky" -> "carved_pumpkin";
            case "jerry" -> "snowball";
            case "new_year" -> "cake";
            case "zoo_summer", "zoo_winter" -> "lead";
            case "hoppity" -> "egg";
            case "election" -> "emerald";
            default -> "clock";
        };
    }

    // "3h 20m", "20m" under an hour. its a guess, no seconds
    private static String minutes(int m) {
        return m >= 60 ? m / 60 + "h " + m % 60 + "m" : m + "m";
    }

    // max 3 lines, the step youre on matters more than a cut sentence
    private static List<String> wrap(String s, int width) {
        List<String> out = new java.util.ArrayList<>();
        String rest = s;
        while (out.size() < 2 && Ui.MEASURE.width(rest) > width) {
            int cut = -1;
            for (int i = rest.lastIndexOf(' '); i > 0 && cut < 0; i = rest.lastIndexOf(' ', i - 1))
                if (Ui.MEASURE.width(rest.substring(0, i)) <= width) cut = i;
            if (cut < 0) break;
            out.add(rest.substring(0, cut));
            rest = rest.substring(cut + 1);
        }
        out.add(rest);
        return out;
    }

    // routine as rows: until when + whats left, current step, next tasks, then whats coming
    static List<Row> routineRows() {
        long now = System.currentTimeMillis();
        List<Row> out = new java.util.ArrayList<>();
        List<Routine.Entry> open = io.muzy.sbc.tasks.RoutinePreset.tasks().stream().filter(e -> !e.complete()).toList();
        int shown = Math.min(open.size(), Math.min(Config.I.hudMaxTasks, 4));
        long reset = SbTime.untilReset(SbTime.Period.DAILY, now);
        out.add(new Row(item("clock"), "Resets in", SbTime.dur(reset), open.isEmpty() ? Modern.INK : reset < 1_800_000L ? Modern.BAD : reset < 7_200_000L ? Modern.WARN : Modern.INK));
        int left = open.stream().mapToInt(e -> e.def().minutes()).sum();
        double worth = open.stream().mapToDouble(e -> e.def().coins()).sum();
        if (left > 0 || worth > 0) {
            // catalog guesses. time goes yellow when its more than the reset leaves
            String time = left <= 0 ? "" : (left * 60_000L > reset ? "§e~" : "§f~") + minutes(left);
            out.add(new Row(item("gold_ingot"), "To finish",(worth > 0 ? "§6~" + Prices.coins(worth) + "  " : "") + time, 0));
        }
        int streak = Routine.streak(null);
        if (streak >= 2) out.add(new Row(item("blaze_powder"), "Streak", streak + " days", Modern.GOLD));
        out.add(new Row(""));
        if (Guide.active && !Guide.step.isEmpty()) {
            // a sentence not "key: value", it reads left to right over the lines
            List<String> lines = wrap(Guide.step.replace(": ", " · "), 176);
            for (int i = 0; i < lines.size(); i++) out.add(new Row(i == 0 ? item("arrow") : ItemStack.EMPTY, "§f" + lines.get(i), "", 0));
        }
        if (open.isEmpty()) out.add(new Row(item("lime_dye"), "§aAll done for today", "", 0));
        for (Routine.Entry e : open.subList(0, shown)) {
            String tag = e.def().period() == SbTime.Period.DAILY ? "" : " §7· " + e.def().periodLabel();
            String prog = e.def().target() > 1 ? e.done() + "/" + e.def().target() : "";
            out.add(new Row(item(e.def().icon()), e.def().name() + tag, prog, periodColor(e.def().period())));
        }
        if (open.size() > shown) out.add(new Row(ItemStack.EMPTY, "§7+" + (open.size() - shown) + " more", "", 0));
        List<SbTime.Event> evs = Config.I.hudShowEvents ? SbTime.events(now, 3 * 3_600_000L) : List.of();
        if (!evs.isEmpty()) out.add(new Row(""));
        for (SbTime.Event ev : evs.stream().limit(3).toList()) {
            boolean active = ev.active(now);
            double remaining = active && ev.end() > ev.start() ? (double) (ev.end() - now) / (ev.end() - ev.start()) : -1;
            out.add(new Row(item(eventIcon(ev.id())), ev.name(), active ? SbTime.dur(ev.end() - now) + " left" : "in " + SbTime.dur(ev.start() - now), active ? Modern.GOOD : ev.color(), remaining, ev.color()));
        }
        boolean nav = nav(), earning = Tracker.active();
        if (nav || earning) out.add(new Row(""));
        if (nav) {
            int gap = Navigator.status.lastIndexOf("  ");
            String where = gap > 0 ? Navigator.status.substring(0, gap) : Navigator.status, how = gap > 0 ? Navigator.status.substring(gap + 2) : "";
            out.add(new Row(item("compass"), "§f" + where, how, Modern.AQUA));
        }
        if (earning) out.add(new Row(item("gold_nugget"), "§6+" + Prices.coins(Tracker.sessionCoins) + " §7this session", Prices.coins(io.muzy.sbc.plan.Planner.currentRate()) + "/h", Modern.GOOD));
        return out;
    }

    static String routineProgress() {
        List<Routine.Entry> today = io.muzy.sbc.tasks.RoutinePreset.tasks();
        long done = today.stream().filter(Routine.Entry::complete).count();
        int all = today.size();
        String percent = all == 0 ? "" : "  " + RowText.percent((double) done / all);
        return all > 0 && done == all ? "§a" + done + "/" + all + percent : "§f" + done + "§7/§f" + all + "§7" + percent;
    }

    static void drawPanel(int x, int y, int w, int h) {
        long now = System.currentTimeMillis();
        // follows the routine you picked (smart day = every daily)
        List<Routine.Entry> today = io.muzy.sbc.tasks.RoutinePreset.tasks();
        int[] c = {(int) today.stream().filter(Routine.Entry::complete).count(), today.size()};
        List<Routine.Entry> open = today.stream().filter(e -> !e.complete()).limit(Config.I.hudMaxTasks).toList();
        int rows = open.size();
        boolean nav = Navigator.target() != null;
        boolean guided = Guide.active && !Guide.step.isEmpty();
        List<SbTime.Event> evs = Config.I.hudShowEvents ? SbTime.events(now, 3 * 3_600_000L) : List.of();
        SbTime.Event ev = evs.isEmpty() ? null : evs.get(0);

        // header: ring + title + reset countdown
        double pct = c[1] == 0 ? 0 : (double) c[0] / c[1];
        int rcx = x + 16, rcy = y + 17;
        ui.ring(rcx, rcy, 9, 2, 0, 1, 0x30FFFFFF, 0x30FFFFFF);
        if (pct > 0) ui.ring(rcx, rcy, 9, 2, 0, pct, Ui.VIOLET, Ui.CYAN);
        String cnt = String.valueOf(c[0]);
        ui.textScaled(cnt, rcx - ui.width(cnt) * 0.35f, rcy - 3, 0.7f, c[0] == c[1] && c[1] > 0 ? Ui.GREEN : Ui.TEXT, true);
        ui.textShadow(io.muzy.sbc.tasks.RoutinePreset.current().name(), x + 31, y + 8, Ui.TEXT);
        ui.text(c[0] + "/" + c[1] + " done · reset " + SbTime.dur(SbTime.untilReset(SbTime.Period.DAILY, now)), x + 31, y + 19, Ui.DIM);
        if (!Guide.active) ui.textScaled("Alt+K", x + w - 8 - ui.width("Alt+K") * 0.7f, y + 9, 0.7f, Ui.CYAN, true);
        int ly = y + 33;

        if (guided) {
            ui.round(x + 6, ly - 2, w - 12, 19, 5, Ui.alpha(Ui.GREEN, 0.16f));
            ui.rect(x + 6, ly - 2, 2, 19, Ui.GREEN);
            ui.text("▶", x + 12, ly + 4, Ui.GREEN);
            ui.text(ui.ellipsize(Guide.step, w - 32), x + 22, ly + 4, Ui.TEXT);
            ly += 22;
        }
        if (rows == 0) {
            ui.text("✔ Everything done for today!", x + 10, ly, Ui.GREEN);
            ly += 13;
        }
        for (Routine.Entry e : open) {
            int pc = periodColor(e.def().period());
            ui.rect(x + 7, ly, 2, 9, pc);
            ui.icon(e.def().icon(), x + 12, ly - 2, 0.6f);
            String prog = e.def().target() > 1 ? e.done() + "/" + e.def().target() : "";
            int nameW = w - 32 - (prog.isEmpty() ? 0 : ui.width(prog) + 4);
            ui.text(ui.ellipsize(e.def().displayName(), nameW), x + 24, ly, e.def().core() ? Ui.TEXT : Ui.MUTED);
            if (!prog.isEmpty()) ui.text(prog, x + w - 8 - ui.width(prog), ly, pc);
            ly += 13;
        }
        if (ev != null) {
            ui.rect(x + 8, ly + 1, w - 16, 1, 0x18FFFFFF);
            boolean active = ev.active(now);
            String t = active ? SbTime.dur(ev.end() - now) + " left" : "in " + SbTime.dur(ev.start() - now);
            ui.round(x + 8, ly + 6, 5, 5, 2, ev.color());
            ui.text(ui.ellipsize(ev.name(), w - 30 - ui.width(t)), x + 17, ly + 5, ev.color());
            ui.text(t, x + w - 8 - ui.width(t), ly + 5, active ? Ui.GREEN : Ui.MUTED);
            ly += 15;
        }
        if (nav) {
            ui.text("➜ " + ui.ellipsize(Navigator.status, w - 22), x + 8, ly + 2, Ui.CYAN);
            ly += 13;
        }
        // footer: session earnings
        ui.round(x + 6, y + h - 17, w - 12, 13, 6, 0x66000000);
        String rate = Tracker.active() ? Prices.coins(io.muzy.sbc.plan.Planner.currentRate()) + "/h" : "idle";
        ui.text("⛁ +" + Prices.coins(Tracker.sessionCoins) + " this session", x + 9, y + h - 14, Ui.GOLD);
        ui.text(rate, x + w - 9 - ui.width(rate), y + h - 14, Tracker.active() ? Ui.GREEN : Ui.DIM);
    }

    static void warpHintClassic(Ui ui, int sw, String name, String island, String hint) {
        String a = "§f" + name + " §7is on §b" + island, b = "§7Press §eJ §7to §a" + hint;
        int w = Math.max(ui.width(a), ui.width(b)) + 22, h = 26, x = sw / 2 - w / 2, y = 22;
        Modern.card(ui, x, y, w, h, Math.max(Config.I.classicAlpha, 0.78f));
        Modern.edge(ui, x, y, h, Modern.AQUA);
        ui.icon(item("compass"), x + 5, y + 5, 0.625f);
        ui.textShadow(a, x + 18, y + 4, Modern.INK);
        ui.textShadow(b, x + 18, y + 14, Modern.INK);
    }

    // first thing after joining: how the day looks + whats coming, same card style
    static void briefingClassic(Ui ui, int sw, long now, float fade) {
        int[] c = Routine.counts();
        List<SbTime.Event> evs = SbTime.events(now, 4 * 3_600_000L).stream().limit(2).toList();
        long ready = Store.I.timers.values().stream().filter(t -> t <= now).count();
        String title = "Welcome back" + (GameState.profileName.isEmpty() ? "" : ", " + GameState.profileName);
        String done = c[1] == 0 ? "" : "§f" + c[0] + "§7/§f" + c[1] + "  §7" + RowText.percent((double) c[0] / c[1]);
        int w = 220, h = Modern.BAND + 2 + 2 * Modern.ROW + (evs.isEmpty() ? 0 : Modern.RULE_BLOCK + evs.size() * Modern.ROW) + (ready > 0 ? Modern.ROW : 0) + Modern.RULE_BLOCK + Modern.ROW + 2;
        int x = sw / 2 - w / 2, y = 24, cx = x + Modern.PAD, cw = w - 2 * Modern.PAD, accent = io.muzy.sbc.ui.hud.Looks.of("routine").accent();
        ui.opacity = fade;
        Modern.card(ui, x, y, w, h, Math.max(Config.I.classicAlpha, 0.78f));
        int cy = y + Modern.band(ui, title, item("writable_book"), done, accent, x, y, w);
        Modern.row(ui, "Daily tasks open", String.valueOf(c[1] - c[0]), cx, cy, cw, Modern.LABEL, Modern.INK);
        Modern.row(ui, "Resets in", SbTime.dur(SbTime.untilReset(SbTime.Period.DAILY, now)), cx, cy += Modern.ROW, cw, Modern.LABEL, Modern.INK);
        cy += Modern.ROW;
        if (!evs.isEmpty()) {
            Modern.rule(ui, cx, cy + 2, cw);
            cy += Modern.RULE_BLOCK;
        }
        for (SbTime.Event e : evs) {
            ui.icon(item(eventIcon(e.id())), cx - 1, cy - 1, 0.6f);
            boolean active = e.active(now);
            Modern.row(ui, e.name(), active ? SbTime.dur(e.end() - now) + " left" : "in " + SbTime.dur(e.start() - now), cx + 12, cy, cw - 12, Modern.LABEL, active ? Modern.GOOD : e.color());
            cy += Modern.ROW;
        }
        if (ready > 0) {
            Modern.row(ui, "Timers ready to collect", String.valueOf(ready), cx, cy, cw, Modern.LABEL, Modern.GOOD);
            cy += Modern.ROW;
        }
        Modern.rule(ui, cx, cy + 2, cw);
        cy += Modern.RULE_BLOCK;
        String more = "§7Press §eK §7for the full menu";
        ui.textShadow(more, x + (w - ui.width(more)) / 2, cy, Modern.INK);
        ui.opacity = 1f;
    }

    private static void drawBriefing(int sw) {
        long now = System.currentTimeMillis();
        briefingClassic(ui, sw, now, Math.min(1, (briefingUntil - now) / 600f));
        return;
    }
}
