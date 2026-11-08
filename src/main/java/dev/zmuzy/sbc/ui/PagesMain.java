// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Mayor;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Profile;
import dev.zmuzy.sbc.core.SbTime;
import dev.zmuzy.sbc.core.SbTime.Period;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.nav.Navigator;
import dev.zmuzy.sbc.plan.Planner;
import dev.zmuzy.sbc.tasks.Guide;
import dev.zmuzy.sbc.tasks.Routine;
import dev.zmuzy.sbc.tasks.RoutinePreset;
import dev.zmuzy.sbc.tasks.TaskDef;
import dev.zmuzy.sbc.ui.theme.Theme;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// dashboard, routine and calendar pages
final class PagesMain {
    private PagesMain() {}

    private static final MenuUi.Field newTask = new MenuUi.Field("New task name (e.g. Kill 10 Ghosts)", false);
    private static final String[] PERIODS = {"DAILY", "WEEKLY", "MONTHLY", "SB_DAY"};
    private static int newTaskPeriod;
    private static int newTaskTarget = 1;
    private static int newTaskPriority = 3;
    private static boolean newTaskIsland;
    private static final MenuUi.Field newTrigger = new MenuUi.Field("Chat text that means it's done (optional)", false);

    // every card has its own color
    static final int ROUTINE = 0xFFFFD166, WALLET = 0xFFFFB627, SKY = 0xFF7FD4FF, PROFILE = MenuUi.PURPLE, EVENTS = 0xFFFF9F45, MAYOR = 0xFF5FEA8C, PLAN = 0xFF5CD8FF,
            CALENDAR = 0xFF7FD4FF, CUSTOM = 0xFFB388FF;

    private static int labelButton(MenuUi ui, String id, int x, int y, String label, Runnable click) {
        int w = ui.width(label) + 12;
        ui.button(id, x, y, w, 16, label, Ui.SOFT, click);
        return w;
    }

    private static int monthOffset;
    private static LocalDate selectedDay;

    // multi hour events (not the hourly ones) that hit a new york calendar day
    private static List<SbTime.Event> monthEvents = List.of();
    private static LocalDate monthEventsFor;

    private static List<SbTime.Event> eventsOn(LocalDate day) {
        LocalDate first = day.withDayOfMonth(1);
        if (!first.equals(monthEventsFor)) {
            monthEventsFor = first;
            long from = first.minusDays(1).atStartOfDay(SbTime.NY).toInstant().toEpochMilli();
            long to = first.plusMonths(1).plusDays(1).atStartOfDay(SbTime.NY).toInstant().toEpochMilli();
            monthEvents = SbTime.longEvents(from, to);
        }
        long start = day.atStartOfDay(SbTime.NY).toInstant().toEpochMilli();
        long end = start + 86_400_000L;
        List<SbTime.Event> out = new ArrayList<>();
        for (SbTime.Event e : monthEvents)
            if (e.start() < end && e.end() > start && out.stream().noneMatch(o -> o.name().equals(e.name()))) out.add(e);
        return out;
    }

    static void go(TaskDef t) {
        Navigator.Target loc = Guide.locationOf(t);
        if (loc != null) {
            Navigator.navigate(loc);
            return;
        }
        if (!t.guideNode().isEmpty() && Navigator.navigateTo(t.island(), t.guideNode())) return;
        if (!t.island().isEmpty()) {
            var g = dev.zmuzy.sbc.core.Repo.graph(t.island());
            if (g != null && !g.isEmpty() && !g.named().isEmpty()) {
                var w = g.warpNear(g.named().get(0).pos());
                if (w != null) {
                    Navigator.navigate(new Navigator.Target(t.island(), GameState.pretty(t.island()), w.pos()));
                    return;
                }
            }
        }
        Toasts.push("No location", "This task has no fixed place - see its hint", Ui.ORANGE);
    }

    // wide strip for the pages one main button
    private static void banner(MenuUi ui, int x, int y, int w, int h, int accent, boolean running) {
        ui.edgeCard(x, y, w, h, accent);
        ui.gradH(x + 2, y + 1, w - 3, h - 2, Ui.alpha(accent | 0xFF000000, 0.24f), Ui.alpha(accent | 0xFF000000, 0.02f));
    }

    // === DASHBOARD ===

    // how much of each routine group is done + whats left, from today
    private record Tally(int done, int total, int minutes, double coins, double xp) {
        static Tally of(List<Routine.Entry> list) {
            int done = 0, min = 0;
            double coins = 0, xp = 0;
            for (Routine.Entry e : list) {
                if (e.complete()) {
                    done++;
                    continue;
                }
                TaskDef t = e.def();
                double left = (t.target() - e.done()) / (double) Math.max(1, t.target());
                min += (int) Math.ceil(t.minutes() * left);
                coins += t.coins() * left;
                xp += t.sbXp() * left;
            }
            return new Tally(done, list.size(), min, coins, xp);
        }

        double share() {
            return total == 0 ? 0 : (double) done / total;
        }
    }

    private static String minutes(int m) {
        return m >= 60 ? m / 60 + "h " + m % 60 + "m" : m + "m";
    }

    private static Map<String, List<Routine.Entry>> groups() {
        Map<String, List<Routine.Entry>> groups = new LinkedHashMap<>();
        for (String g : new String[]{"Daily", "Every SkyBlock day", "Events", "Weekly", "Monthly"}) groups.put(g, new ArrayList<>());
        for (Routine.Entry e : Routine.today()) {
            String g = switch (e.def().period()) {
                case DAILY -> "Daily";
                case SB_DAY -> "Every SkyBlock day";
                case EVENT, SB_YEAR -> "Events";
                case WEEKLY -> "Weekly";
                case MONTHLY -> "Monthly";
                default -> "Other";
            };
            groups.computeIfAbsent(g, k -> new ArrayList<>()).add(e);
        }
        return groups;
    }

    private static String groupIcon(String g) {
        return switch (g) {
            case "Daily" -> "clock";
            case "Every SkyBlock day" -> "sunflower";
            case "Events" -> "firework_rocket";
            case "Weekly" -> "book";
            case "Monthly" -> "map";
            default -> "paper";
        };
    }

    private static int groupColor(String g) {
        return switch (g) {
            case "Daily" -> 0xFF5CD8FF;
            case "Every SkyBlock day" -> 0xFF5FEA8C;
            case "Events" -> 0xFFFF9EC7;
            case "Weekly" -> 0xFFC77DFF;
            case "Monthly" -> 0xFFFFD166;
            default -> 0xFF90A4AE;
        };
    }

    static int dashboard(MenuUi ui, int x, int y, int w) {
        long now = System.currentTimeMillis();
        int gap = MenuUi.GAP, cw3 = (w - 2 * gap) / 3;
        int y0 = y;

        // guided routine banner, the one button that runs the whole day
        boolean guided = Guide.active;
        banner(ui, x, y, w, 40, guided ? 0xFF5FEA8C : PLAN, guided);
        ui.textScaled(guided ? "Guided routine running" : "Auto routine · " + RoutinePreset.current().name(), x + 10, y + 5, 1.25f, Ui.TEXT, true);
        String sub = guided ? Guide.step : "Takes you task by task through today's dailies (by importance), then your main goal. Alt+K = mission card.";
        ui.wrap(ui.ellipsize(sub, (w - 190) * 2 - 20), x + 10, y + 18, w - 190, Ui.MUTED);
        ui.button("dash_guide", x + w - 166, y + 10, 158, 20, guided ? "■ Stop routine" : "▶ START AUTO ROUTINE", guided ? Ui.DANGER : Ui.PRIMARY, () -> {
            if (Guide.active) Guide.stop();
            else {
                Guide.start();
                net.minecraft.client.Minecraft.getInstance().gui.setScreen(null);
            }
        });
        y += 40 + gap;

        // 3 cards: today, money, the world
        int rowH = 96;
        todayCard(ui, x, y, cw3, rowH);
        walletCard(ui, x + cw3 + gap, y, cw3, rowH);
        skyBlockCard(ui, x + 2 * (cw3 + gap), y, w - 2 * (cw3 + gap), rowH, now);
        y += rowH + gap;

        // profile summary from the api, everything else builds on it
        Profile.Data pd = Profile.current;
        if (pd != null) {
            y += profileCard(ui, x, y, w, pd) + gap;
        } else if (!Profile.status.isEmpty()) {
            ui.edgeCard(x, y, w, 22, Ui.ORANGE);
            ui.text(ui.ellipsize(Profile.status, w - 20), x + 10, y + 7, Ui.ORANGE);
            y += 22 + gap;
        }

        // events + mayor
        int half = (w - gap) / 2;
        int evH = 94;
        eventsCard(ui, x, y, half, evH, now);
        mayorCard(ui, x + half + gap, y, w - half - gap, evH);
        y += evH + gap;

        y += planCard(ui, x, y, w) + gap;
        return y - y0;
    }

    private static void todayCard(MenuUi ui, int x, int y, int w, int h) {
        int[] c = Routine.counts();
        double pct = c[1] == 0 ? 0 : (double) c[0] / c[1];
        Map<String, List<Routine.Entry>> groups = groups();
        List<Routine.Entry> all = Routine.today();
        Tally tally = Tally.of(all);
        int top = ui.card(x, y, w, h, "Today's routine", "writable_book", c[0] + "/" + c[1], ROUTINE);
        int ix = x + 6, iw = w - 12;
        String p = MenuUi.pct(pct);
        ui.textScaled(p, ix, y + top + 1, 2f, pct >= 1 ? Ui.GREEN : Ui.TEXT, true);
        int tx = ix + ui.width(p) * 2 + 8;
        ui.text(c[0] + " of " + c[1] + " done", tx, y + top, Ui.MUTED);
        ui.text((tally.total - tally.done) + " open · ~" + minutes(tally.minutes), tx, y + top + 10, Ui.DIM);
        ui.meter(ix, y + top + 20, iw, 3, pct, ROUTINE);
        int gy = y + top + 26, col = 0, cw = iw / 2;
        for (Map.Entry<String, List<Routine.Entry>> g : groups.entrySet()) {
            if (g.getValue().isEmpty()) continue;
            long done = g.getValue().stream().filter(Routine.Entry::complete).count();
            String name = g.getKey().equals("Every SkyBlock day") ? "SB day" : g.getKey();
            ui.row(name, done + "/" + g.getValue().size(), ix + (col % 2) * cw, gy + (col / 2) * 10, cw - 6, Ui.MUTED, MenuUi.tone(groupColor(g.getKey())));
            col++;
        }
        int streak = Routine.streak(null);
        ui.text("★ " + streak + " day streak", ix, y + h - 13, streak > 0 ? Ui.ORANGE : Ui.DIM);
        ui.button("dash_r", x + w - 46, y + h - 16, 42, 14, "Open ›", Ui.SOFT, () -> ui.goTo(1));
    }

    private static void walletCard(MenuUi ui, int x, int y, int w, int h) {
        double purse = GameState.purse, bank = GameState.bank;
        String total = purse >= 0 || bank >= 0 ? Prices.coins(Math.max(0, purse) + Math.max(0, bank)) : null;
        int top = ui.card(x, y, w, h, "Wallet", "gold_ingot", total, WALLET);
        int ix = x + 6, iw = w - 12, cw = (iw - 8) / 2;
        ui.textScaled(GameState.purse >= 0 ? Prices.coins(GameState.purse) : "—", ix, y + top + 1, 2f, Ui.GOLD, true);
        ui.text("purse", ix + Math.max(30, ui.width(GameState.purse >= 0 ? Prices.coins(GameState.purse) : "—") * 2 + 4), y + top + 8, Ui.DIM);
        int ry = y + top + 20;
        ui.row("Bank", GameState.bank >= 0 ? Prices.coins(GameState.bank) : "—", ix, ry, cw, Ui.GOLD);
        ui.row("Bits", GameState.bits >= 0 ? Prices.coins(GameState.bits) : "—", ix + cw + 8, ry, cw, MenuUi.tone(0xFF5CC8FF));
        ry += 10;
        if (GameState.motes >= 0) ui.row("Motes", Prices.coins(GameState.motes), ix, ry, cw, Ui.PINK);
        if (GameState.copper >= 0) ui.row("Copper", Prices.coins(GameState.copper), ix + cw + 8, ry, cw, MenuUi.tone(0xFFE0904A));
        ry += 12;
        ui.hair(ix, ry - 1, iw);
        ry += 2;
        double rate = Planner.currentRate();
        ui.row("On this island", rate > 0 ? Prices.coins(rate) + "/h" : "—", ix, ry, iw, rate > 0 ? Ui.GOLD : Ui.DIM);
    }

    private static void skyBlockCard(MenuUi ui, int x, int y, int w, int h, long now) {
        SbTime.SbDate d = SbTime.sbDate(now);
        int top = ui.card(x, y, w, h, "SkyBlock", "clock", "Year " + d.year(), SKY);
        int ix = x + 6, iw = w - 12;
        ui.textScaled(d.monthName() + " " + d.day() + SbTime.suffix(d.day()), ix, y + top, 1.5f, Ui.CYAN, true);
        ui.text(d.clock(), ix, y + top + 13, Ui.MUTED);
        double monthPct = ((now - SbTime.EPOCH) % SbTime.MONTH) / (double) SbTime.MONTH;
        ui.textRight(MenuUi.pct(monthPct) + " of the month", ix + iw, y + top + 13, Ui.DIM);
        ui.meter(ix, y + top + 23, iw, 2, monthPct, SKY);
        int ry = y + top + 27;
        ui.text(ui.ellipsize("➤ " + (GameState.islandName.isEmpty() ? "Not in SkyBlock" : GameState.islandName) + (GameState.area.isEmpty() ? "" : " · " + GameState.area), iw), ix, ry, Ui.TEXT);
        ry += 10;
        ui.text(ui.ellipsize("Profile " + (GameState.profileName.isEmpty() ? "?" : GameState.profileName) + (GameState.sbLevel.isEmpty() ? "" : " · Level " + GameState.sbLevel), iw), ix, ry, Ui.MUTED);
        ry += 10;
        ui.row("Month ends in", SbTime.dur(SbTime.untilReset(Period.SB_MONTH, now)), ix, ry, iw, Ui.TEXT);
        ry += 10;
        String cookie = GameState.cookieStatus();
        boolean off = cookie.toLowerCase(Locale.ROOT).contains("not active");
        ui.text(ui.ellipsize("● " + (cookie.isEmpty() ? "Cookie: unknown (open the tab list once)" : "Cookie: " + cookie), iw), ix, ry, off ? Ui.RED : cookie.isEmpty() ? Ui.DIM : MenuUi.tone(0xFFE0B070));
    }

    // whole card opens the profile page
    private static int profileCard(MenuUi ui, int x, int y, int w, Profile.Data pd) {
        if (pd.fromGame) return ProfileFromGame.card(ui, x, y, w, pd);
        int h = 76;
        String title = pd.player + " · " + pd.profileName + (pd.gameMode.isEmpty() ? "" : " · " + Profile.cap(pd.gameMode));
        int top = ui.card(x, y, w, h, title, "player_head", "Open profile ›", PROFILE);
        int lvl = (int) (pd.sbXp / 100);
        int xpIn = (int) (pd.sbXp % 100);
        ui.textScaled("Level " + lvl, x + 6, y + top + 2, 1.5f, Ui.CYAN, true);
        ui.meter(x + 6, y + top + 17, 120, 3, xpIn / 100.0, 0xFF5CD8FF);
        ui.text(xpIn + "/100 XP · " + xpIn + "%", x + 6, y + top + 23, Ui.MUTED);
        if (pd.firstJoin > 0) ui.text("Profile age " + SbTime.dur(System.currentTimeMillis() - pd.firstJoin), x + 6, y + top + 33, Ui.DIM);
        int tx = x + 134, gap = 3, tw = (w - 134 - 6 - 3 * gap) / 4, th = 26;
        String slayers = pd.slayers.values().stream().map(l -> String.valueOf(l.level())).reduce((a, b) -> a + "/" + b).orElse("-");
        ui.tile(tx, y + top, tw, th, "Skill average", String.format(Locale.ROOT, "%.1f", pd.skillAverage), null, Ui.TEXT, -1, 0);
        ui.tile(tx + tw + gap, y + top, tw, th, "Catacombs", pd.catacombs == null ? "-" : pd.catacombs.level() + " · " + MenuUi.pct(pd.catacombs.progress()), null, MenuUi.tone(0xFFFF5D73), pd.catacombs == null ? -1 : pd.catacombs.progress(), 0xFFFF5D73);
        ui.tile(tx + 2 * (tw + gap), y + top, tw, th, pd.fromGame ? "Networth (partial)" : "Networth", (pd.fromGame ? "≈" : "") + Prices.coins(pd.networth()), pd.fromGame ? "no museum, storage, wardrobe" : null, pd.fromGame ? Ui.ORANGE : Ui.GOLD, -1, 0);
        int mp = pd.magicalPower;
        ui.tile(tx + 3 * (tw + gap), y + top, tw, th, "Magical Power", MenuUi.num(mp), null, MenuUi.tone(MenuUi.PURPLE), -1, 0);
        int y2 = y + top + th + 2;
        ui.tile(tx, y2, tw, th, "Slayers", slayers, null, MenuUi.tone(0xFFFF5252), -1, 0);
        ui.tile(tx + tw + gap, y2, tw, th, "HOTM", pd.hotm == null ? "-" : pd.hotm.level() + " · " + MenuUi.pct(pd.hotm.progress()), null, MenuUi.tone(0xFF4FD8C4), pd.hotm == null ? -1 : pd.hotm.progress(), 0xFF4FD8C4);
        ui.tile(tx + 2 * (tw + gap), y2, tw, th, "HOTF", pd.hotfXp <= 0 ? "-" : pd.hotf.level() + " · " + MenuUi.pct(pd.hotf.progress()), null, MenuUi.tone(0xFF81C784), pd.hotfXp <= 0 ? -1 : pd.hotf.progress(), 0xFF81C784);
        ui.tile(tx + 3 * (tw + gap), y2, tw, th, "Fairy souls", String.valueOf(pd.fairySouls), null, Ui.PINK, -1, 0);
        ui.click(x, y, w, h, () -> ui.goTo(7));
        return h;
    }

    private static void eventsCard(MenuUi ui, int x, int y, int w, int h, long now) {
        List<SbTime.Event> evs = SbTime.events(now, 48 * 3_600_000L).stream().limit(6).toList();
        long live = evs.stream().filter(e -> e.active(now)).count();
        int top = ui.card(x, y, w, h, "Next up", "clock", live > 0 ? live + " live" : null, EVENTS);
        int ey = y + top - 1;
        for (SbTime.Event e : evs) {
            boolean act = e.active(now);
            String t = act ? SbTime.dur(e.end() - now) + " left" : "in " + SbTime.dur(e.start() - now);
            int right = x + w - 6;
            ui.textRight(t, right, ey, act ? Ui.GREEN : Ui.MUTED);
            int tl = right - ui.width(t) - 4;
            if (act) tl -= ui.chip(tl - ui.chipWidth("LIVE"), ey - 1, "LIVE", Ui.GREEN) + 4;
            ui.rect(x + 6, ey + 2, 4, 4, e.color());
            ui.text(ui.ellipsize(e.name(), tl - x - 16), x + 14, ey, Ui.TEXT);
            if (act) ui.meter(x + 14, ey + 9, w - 20, 1, (now - e.start()) / (double) Math.max(1, e.end() - e.start()), e.color());
            ui.tip(x, ey - 1, w, 12, e.name(), e.hint());
            ey += 12;
        }
    }

    private static void mayorCard(MenuUi ui, int x, int y, int w, int h) {
        int top = ui.card(x, y, w, h, "Mayor", "emerald", Mayor.minister.isEmpty() ? null : "Minister " + Mayor.minister, MAYOR);
        ui.textScaled(Mayor.name.isEmpty() ? "Loading…" : Mayor.name, x + 6, y + top, 1.5f, Ui.GOLD, true);
        int py = y + top + 14;
        for (Mayor.Perk p : Mayor.perks) {
            if (py > y + h - 11) break;
            String name = p.name();
            ui.text(ui.ellipsize(name, w - 12), x + 6, py, Ui.TEXT);
            int nw = ui.width(name);
            if (nw < w - 60) ui.text(ui.ellipsize(p.description(), w - 12 - nw - 6), x + 6 + nw + 6, py, Ui.DIM);
            ui.tip(x, py - 1, w, 11, p.name(), p.description());
            py += 10;
        }
    }

    private static int planCard(MenuUi ui, int x, int y, int w) {
        List<Planner.Step> plan = Planner.lastPlan.isEmpty() ? Planner.plan() : Planner.lastPlan;
        int shown = Math.min(4, plan.size());
        int h = MenuUi.TOP + shown * 18 + (plan.isEmpty() ? 12 : 12);
        int top = ui.card(x, y, w, h, "Suggested next  ·  " + Config.I.planHours + "h, " + goalName(), "compass", null, PLAN);
        ui.button("dash_p", x + w - 60, y + 1, 58, 12, "Planner ›", Ui.SOFT, () -> ui.goTo(3));
        int sy = y + top - 1;
        for (Planner.Step st : plan.stream().limit(4).toList()) {
            ui.icon(st.icon(), x + 6, sy + 2, 0.75f);
            String info = "§f" + st.minutes() + "m" + (st.coins() > 0 ? "  §6+" + Prices.coins(st.coins()) : "") + (st.sbXp() > 0 ? "  §b+" + (int) st.sbXp() + " XP" : "");
            int right = x + w - 6;
            if (!st.island().isEmpty()) {
                right -= 32;
                final Planner.Step fs = st;
                ui.button("dgo" + st.name(), right + 2, sy + 1, 30, 14, "Go", Ui.PRIMARY, () -> goStep(fs));
                right -= 2;
            }
            ui.textRight(info, right, sy + 4, Ui.TEXT);
            ui.text(ui.ellipsize(st.name(), right - ui.width(info) - x - 28), x + 22, sy + 4, Ui.TEXT);
            ui.tip(x, sy, w - 36, 17, st.name(), "§7" + st.why());
            sy += 18;
        }
        double coins = plan.stream().mapToDouble(Planner.Step::coins).sum(), xp = plan.stream().mapToDouble(Planner.Step::sbXp).sum();
        int min = plan.stream().mapToInt(Planner.Step::minutes).sum();
        ui.hair(x + 6, sy - 1, w - 12);
        ui.text(plan.isEmpty() ? "Nothing left to plan today." : shown + " of " + plan.size() + " steps shown", x + 6, sy + 1, Ui.DIM);
        if (!plan.isEmpty()) ui.textRight("§7All: §f" + minutes(min) + "  §6+" + Prices.coins(coins) + "  §b+" + (int) xp + " XP", x + w - 6, sy + 1, Ui.TEXT);
        return h;
    }

    static void goStep(Planner.Step st) {
        if (!st.taskId().isEmpty()) {
            TaskDef t = Routine.find(st.taskId());
            if (t != null) {
                go(t);
                return;
            }
        }
        if (!st.node().isEmpty() && Navigator.navigateTo(st.island(), st.node())) return;
        var g = dev.zmuzy.sbc.core.Repo.graph(st.island());
        if (g != null && !g.isEmpty()) {
            var w = g.warpNear(g.nodes[0].pos());
            if (w != null) Navigator.navigate(new Navigator.Target(st.island(), GameState.pretty(st.island()), w.pos()));
        }
    }

    static String goalName() {
        int m = Config.I.moneyWeight;
        return m >= 70 ? "Max Money" : m <= 30 ? "Max Level" : "Balanced";
    }

    // === ROUTINE ===

    // every routine as a card, what it does + how long. click to pick
    static int presetPicker(MenuUi ui, int x, int y, int w) {
        var cur = RoutinePreset.current();
        int cols = Math.max(2, Math.min(3, w / 190)), gap = 3, tileH = 47;
        int rows = (RoutinePreset.ALL.size() + cols - 1) / cols;
        int h = MenuUi.TOP + rows * (tileH + gap) + 2;
        int top = ui.card(x, y, w, h, "Pick your routine", "nether_star", cur.name() + " ≈ " + RoutinePreset.totalMinutes() + " min today", CUSTOM);
        int cw = (w - 8 - (cols - 1) * gap) / cols;
        int i = 0;
        int accent = MenuUi.tone(PLAN);
        int face = 0x14FFFFFF, faceHover = 0x26FFFFFF;
        for (var p : RoutinePreset.ALL) {
            int cx = x + 4 + (i % cols) * (cw + gap), cy = y + top + (i / cols) * (tileH + gap);
            boolean sel = p == cur;
            int f = sel ? Ui.alpha(accent, 0.20f) : face;
            ui.hoverRound(cx, cy, cw, tileH, 7, f, sel ? f : faceHover);
            if (sel) {
                ui.rect(cx, cy, cw, 1, accent);
                ui.chip(cx + cw - ui.chipWidth("ON") - 3, cy + 3, "ON", accent);
            }
            ui.icon(p.icon(), cx + 3, cy + 3, 0.75f);
            ui.text(p.name(), cx + 18, cy + 5, sel ? Ui.CYAN : Ui.TEXT);
            ui.wrap(p.about(), cx + 4, cy + 16, cw - 8, Ui.DIM);
            final String id = p.id();
            ui.click(cx, cy, cw, tileH, () -> {
                Config.I.routinePreset = id;
                Config.save();
                if (Guide.active) Guide.start();
            });
            i++;
        }
        return h;
    }

    static int routine(MenuUi ui, int x, int y, int w) {
        int y0 = y, gap = MenuUi.GAP;
        long now = System.currentTimeMillis();
        Map<String, List<Routine.Entry>> groups = groups();

        int[] c = Routine.counts();
        double pct = c[1] == 0 ? 0 : (double) c[0] / c[1];
        Tally open = Tally.of(Routine.today());
        int tg = 4, tw = (w - 4 * tg) / 5, th = 42;
        long resetIn = SbTime.untilReset(Period.DAILY, now);
        ui.tile(x, y, tw, th, "Done today", c[0] + "/" + c[1], MenuUi.pct(pct) + " of the dailies", pct >= 1 ? Ui.GREEN : Ui.TEXT, pct, ROUTINE);
        int streak = Routine.streak(null);
        ui.tile(x + tw + tg, y, tw, th, "Streak", "★ " + streak, streak == 1 ? "day" : "days in a row", streak > 0 ? Ui.ORANGE : Ui.DIM, -1, 0);
        ui.tile(x + 2 * (tw + tg), y, tw, th, "Time left", "~" + minutes(open.minutes), (open.total - open.done) + " tasks open", Ui.TEXT, -1, 0);
        ui.tile(x + 3 * (tw + tg), y, tw, th, "Still to earn", "~" + Prices.coins(open.coins), "+" + (int) open.xp + " SkyBlock XP", Ui.GOLD, -1, 0);
        ui.tile(x + 4 * (tw + tg), y, w - 4 * (tw + tg), th, "Daily reset", SbTime.dur(resetIn), MenuUi.pct(1 - resetIn / 86_400_000.0) + " of the day gone", Ui.ORANGE, 1 - resetIn / 86_400_000.0, Ui.ORANGE);
        y += th + gap;

        boolean guided = Guide.active;
        banner(ui, x, y, w, 30, guided ? 0xFF5FEA8C : PLAN, guided);
        ui.text(guided ? "Guided routine running" : "Guided routine", x + 10, y + 6, guided ? Ui.CYAN : Ui.TEXT);
        ui.text(ui.ellipsize(guided ? Guide.step : "Path line to each daily in order of importance, task after task.", w - 150), x + 10, y + 17, Ui.MUTED);
        ui.button("guide", x + w - 128, y + 7, 120, 16, guided ? "■ Stop" : "▶ Start routine", guided ? Ui.DANGER : Ui.PRIMARY,
                () -> { if (Guide.active) Guide.stop(); else Guide.start(); });
        y += 30 + gap;

        y += presetPicker(ui, x, y, w) + gap;

        String learning = Routine.learning();
        if (learning != null) {
            ui.edgeCard(x, y, w, 20, PLAN);
            ui.text(ui.ellipsize("Teaching \"" + Routine.find(learning).name() + "\": do the task now - the next game message becomes its trigger.", w - 16), x + 8, y + 6, Ui.CYAN);
            y += 20 + gap;
        }
        for (Map.Entry<String, List<Routine.Entry>> g : groups.entrySet()) {
            List<Routine.Entry> list = g.getValue();
            if (list.isEmpty()) continue;
            Tally t = Tally.of(list);
            Period p = list.get(0).def().period();
            String reset = p == Period.EVENT || p == Period.SB_YEAR ? "" : "  ·  resets in " + SbTime.dur(SbTime.untilReset(p, now));
            int accent = groupColor(g.getKey());
            int rowH = 24, h = MenuUi.TOP + list.size() * rowH + 2;
            int top = ui.card(x, y, w, h, g.getKey(), groupIcon(g.getKey()), t.done + "/" + t.total + "  " + MenuUi.pct(t.share()) + reset, accent);
            ui.meter(x + 1, y + 14, w - 2, 2, t.share(), accent);
            int ry = y + top;
            int n = 0;
            for (Routine.Entry e : list) {
                if (n++ > 0) ui.hair(x + 4, ry - 1, w - 8);
                taskRow(ui, e, x + 2, ry, w - 4, g.getKey());
                ry += rowH;
            }
            y += h + gap;
        }

        // your own task, tracked like the built in ones
        int addH = 104;
        int top = ui.card(x, y, w, addH, "Add your own task", "writable_book", null, CUSTOM);
        int fy = y + top;
        ui.text("Name", x + 6, fy + 5, Ui.MUTED);
        ui.input(newTask, x + 66, fy, w - 72);
        fy += 20;
        ui.text("Auto-check", x + 6, fy + 5, Ui.MUTED);
        ui.input(newTrigger, x + 66, fy, w - 72);
        fy += 21;
        int bx = x + 6;
        String[] prNames = {"", "OPTIONAL", "IMPORTANT", "CRITICAL"};
        bx += 3 + labelButton(ui, "np", bx, fy, "Repeats: " + PERIODS[newTaskPeriod].replace("SB_DAY", "SkyBlock day").toLowerCase(), () -> newTaskPeriod = (newTaskPeriod + 1) % PERIODS.length);
        bx += 3 + labelButton(ui, "nt", bx, fy, "Times: " + newTaskTarget, () -> newTaskTarget = newTaskTarget % 20 + 1);
        bx += 3 + labelButton(ui, "npr", bx, fy, prNames[newTaskPriority], () -> newTaskPriority = newTaskPriority % 3 + 1);
        String isl = newTaskIsland ? "Only on " + (GameState.islandName.isEmpty() ? "this island" : GameState.islandName) : "Any island";
        bx += 3 + labelButton(ui, "nis", bx, fy, isl, () -> newTaskIsland = !newTaskIsland);
        ui.button("na", x + w - 76, fy, 70, 16, "+ Add task", Ui.PRIMARY, () -> {
            if (newTask.value.isBlank()) return;
            Store.CustomTask task = new Store.CustomTask();
            task.id = "custom_" + System.currentTimeMillis();
            task.name = newTask.value.trim();
            task.period = PERIODS[newTaskPeriod];
            task.target = newTaskTarget;
            task.priority = newTaskPriority;
            task.island = newTaskIsland ? GameState.island : "";
            // plain text matched anywhere in a chat line (no case), no regex needed
            task.regex = newTrigger.value.isBlank() ? "" : "(?i)" + java.util.regex.Pattern.quote(newTrigger.value.trim());
            Store.I.customTasks.add(task);
            newTask.value = "";
            newTrigger.value = "";
            Toasts.push("Task added", task.name + (task.regex.isEmpty() ? " (press Teach or check it by hand)" : " (auto-tracked)"), Ui.GREEN);
        });
        fy += 19;
        ui.text(ui.ellipsize("Auto-check: text from the chat message you get when it's done (e.g. \"SLAYER QUEST COMPLETE\").", w - 12), x + 6, fy, Ui.DIM);
        ui.text(ui.ellipsize("Tasks without auto-check can learn it: press Teach, then do the task once; the next game message becomes its trigger.", w - 12), x + 6, fy + 10, Ui.DIM);
        y += addH + gap;
        if (!Config.I.hiddenTasks.isEmpty()) {
            ui.button("unhide", x, y, 150, 16, "Show " + Config.I.hiddenTasks.size() + " hidden task(s)", Ui.GHOST, () -> Config.I.hiddenTasks.clear());
            y += 22;
        }
        return y - y0;
    }

    private static int taskRow(MenuUi ui, Routine.Entry e, int x, int y, int w, String group) {
        TaskDef t = e.def();
        int h = 24;
        boolean done = e.complete();
        ui.hoverRound(x, y - 1, w, h, 6, done ? (0x20000000) : 0x00000000, 0x14FFFFFF);
        int pr = Routine.priority(t);
        int prColor = pr == 3 ? Ui.RED : pr == 2 ? Ui.GOLD : Ui.DIM;
        ui.rect(x, y - 1, 2, h - 1, done ? Ui.GREEN : prColor);
        ui.checkbox(x + 6, y + 5, done, 0);
        ui.click(x + 2, y - 1, 20, h - 1, () -> Routine.toggle(t));
        ui.icon(t.icon(), x + 22, y + 3, 1f);
        int nx = x + 42;

        // right side: buttons, then state chips over what its worth
        boolean custom = t.id().startsWith("custom_");
        int rx = x + w - 2;
        rx -= 14;
        ui.button("hide" + t.id(), rx, y + 4, 14, 14, "✕", Ui.GHOST, () -> {
            if (custom) Store.I.customTasks.removeIf(c -> c.id.equals(t.id()));
            else Config.I.hiddenTasks.add(t.id());
        });
        ui.tip(rx, y + 4, 14, 14, custom ? "Delete this task" : "Hide this task (Show hidden tasks brings it back)");
        rx -= 3;
        if (!t.island().isEmpty() || !t.guideNode().isEmpty()) {
            rx -= 24;
            ui.button("go" + t.id(), rx, y + 4, 24, 14, "Go", Ui.PRIMARY, () -> go(t));
            rx -= 3;
        }
        if (!t.auto() && !Store.I.learned.containsKey(t.id())) {
            rx -= 34;
            ui.button("teach" + t.id(), rx, y + 4, 34, 14, "Teach", Ui.SOFT, () -> {
                Routine.learn(t.id());
                Toasts.push("Teaching", "Do \"" + t.name() + "\" now", Ui.CYAN);
            });
            rx -= 3;
        }
        int blockRight = rx;
        int cx = rx;
        String mode = Store.I.learned.containsKey(t.id()) ? "LEARNED" : t.auto() ? "AUTO" : "MANUAL";
        cx -= ui.chipWidth(mode);
        ui.chip(cx, y + 1, mode, mode.equals("MANUAL") ? Ui.DIM : Ui.CYAN);
        cx -= 2;
        if (!done) {
            String pl = Routine.priorityLabel(t);
            cx -= ui.chipWidth(pl);
            ui.chip(cx, y + 1, pl, prColor);
            cx -= 2;
        }
        if (t.target() > 1) {
            String p = e.done() + "/" + t.target() + " " + MenuUi.pct((double) e.done() / t.target());
            cx -= ui.chipWidth(p);
            ui.chip(cx, y + 1, p, done ? Ui.GREEN : Ui.GOLD);
            cx -= 2;
        }
        int st = Routine.streak(t.id());
        if (st > 1) {
            String sv = "★" + st;
            cx -= ui.chipWidth(sv);
            ui.chip(cx, y + 1, sv, Ui.ORANGE);
            cx -= 2;
        }
        String worth = "§7~" + t.minutes() + " min" + (t.coins() > 0 ? "  §6" + Prices.coins(t.coins()) : "") + (t.sbXp() > 0 ? "  §b+" + (int) t.sbXp() + " XP" : "");
        ui.textRight(worth, blockRight, y + 13, Ui.DIM);

        boolean same = t.periodLabel().equals(group) || group.endsWith(t.periodLabel());
        String name = same ? t.name() : t.displayName();
        ui.text(ui.ellipsize(name, cx - nx - 4), nx, y + 2, done ? Ui.DIM : Ui.TEXT);
        ui.text(ui.ellipsize(t.hint(), blockRight - ui.width(worth) - nx - 6), nx, y + 12, Ui.DIM);
        ui.tip(nx, y, cx - nx, h - 2, () -> new String[]{t.displayName(), "§7" + t.hint(),
                "§8" + t.category() + " · ~" + t.minutes() + " min" + (t.coins() > 0 ? " · ~" + Prices.coins(t.coins()) + " coins" : ""),
                "§8Click the box to check it off manually"});
        return h;
    }

    // === CALENDAR ===

    static int calendar(MenuUi ui, int x, int y, int w) {
        int y0 = y, gap = MenuUi.GAP;
        long now = System.currentTimeMillis();
        ZoneId ny = SbTime.NY;
        LocalDate today = Instant.ofEpochMilli(now).atZone(ny).toLocalDate();
        LocalDate month = today.withDayOfMonth(1).plusMonths(monthOffset);
        boolean side = w >= 500;
        int calW = side ? Math.min(w - 196 - gap, 400) : w;
        int startCol = month.getDayOfWeek().getValue() - 1;
        int days = month.lengthOfMonth();
        int weeks = (startCol + days + 6) / 7;
        int calH = MenuUi.TOP + 12 + weeks * 26 + 4;
        calH = Math.max(calH, 180);
        ui.card(x, y, calW, calH, month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)), "clock", null, CALENDAR);
        ui.button("cprev", x + calW - 44, y + 1, 20, 12, "‹", Ui.SOFT, () -> monthOffset--);
        ui.button("cnext", x + calW - 22, y + 1, 20, 12, "›", Ui.SOFT, () -> monthOffset = Math.min(0, monthOffset + 1));
        String[] dn = {"Mo", "Tu", "We", "Th", "Fr", "Sa", "Su"};
        int cell = (calW - 8) / 7, gx = x + 4;
        for (int i = 0; i < 7; i++) ui.textCenter(dn[i], gx + i * cell + cell / 2, y + 17, Ui.DIM);
        int gridTop = y + 28;
        int doneDays = 0, pastDays = 0;
        double sumRatio = 0;
        int low = 0xFF1B2533;
        for (int d = 1; d <= days; d++) {
            int idx = startCol + d - 1;
            int cxp = gx + (idx % 7) * cell, cyp = gridTop + (idx / 7) * 26;
            LocalDate date = month.withDayOfMonth(d);
            double r = Routine.dayRatio("D:" + date);
            boolean isToday = date.equals(today), future = date.isAfter(today);
            if (!future) {
                pastDays++;
                sumRatio += r;
                if (r >= 1) doneDays++;
            }
            int base = future ? Ui.INPUT_BG : Ui.lerpColor(low, 0xFF1F7A4E, (float) r);
            ui.round(cxp + 1, cyp, cell - 2, 24, 5, base);
            if (isToday) {
                int mark = Ui.CYAN;
                ui.rect(cxp + 1, cyp, cell - 2, 1, mark);
                ui.rect(cxp + 1, cyp + 23, cell - 2, 1, mark);
                ui.rect(cxp + 1, cyp + 1, 1, 22, mark);
                ui.rect(cxp + cell - 2, cyp + 1, 1, 22, mark);
            }
            if (date.equals(selectedDay)) ui.round(cxp + 1, cyp, cell - 2, 24, 5, Ui.alpha(Ui.CYAN, 0.28f));
            ui.text(String.valueOf(d), cxp + 4, cyp + 3, future ? Ui.DIM : Ui.TEXT);
            if (!future && r > 0) ui.text((int) Math.round(r * 100) + "%", cxp + 4, cyp + 13, r >= 1 ? 0xFFB8FFD6 : Ui.MUTED);
            List<SbTime.Event> dayEvents = eventsOn(date);
            for (int k = 0; k < Math.min(3, dayEvents.size()); k++) ui.rect(cxp + cell - 7 - k * 5, cyp + 3, 4, 4, dayEvents.get(k).color());
            ui.tip(cxp + 1, cyp, cell - 2, 24, () -> {
                List<String> tip = new ArrayList<>();
                tip.add(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)));
                tip.add(future ? "§7Upcoming" : "§7" + (int) Math.round(r * 100) + "% of core dailies done");
                for (SbTime.Event ev : dayEvents) tip.add("§e● " + ev.name());
                tip.add("§8Click for details");
                return tip.toArray(new String[0]);
            });
            final LocalDate fd = date;
            ui.click(cxp + 1, cyp, cell - 2, 24, () -> selectedDay = fd);
        }

        if (side) {
            int sx = x + calW + gap, sw = w - calW - gap;
            SbTime.SbDate d = SbTime.sbDate(now);
            int top = ui.card(sx, y, sw, calH, "SkyBlock calendar", "clock", null, SKY);
            int ix = sx + 6, iw = sw - 12, ry = y + top;
            ui.textScaled(d.monthName() + " " + d.day() + SbTime.suffix(d.day()), ix, ry, 1.5f, Ui.CYAN, true);
            ry += 14;
            ui.text("Year " + d.year() + " · " + d.clock(), ix, ry, Ui.MUTED);
            ry += 13;
            double monthPct = ((now - SbTime.EPOCH) % SbTime.MONTH) / (double) SbTime.MONTH;
            ry += ui.progress("Month " + MenuUi.pct(monthPct), "ends in " + SbTime.dur(SbTime.untilReset(Period.SB_MONTH, now)), ix, ry, iw, monthPct, SKY, Ui.TEXT);
            double yearPct = ((now - SbTime.EPOCH) % SbTime.YEAR) / (double) SbTime.YEAR;
            ry += ui.progress("Year " + MenuUi.pct(yearPct), "ends in " + SbTime.dur(SbTime.untilReset(Period.SB_YEAR, now)), ix, ry, iw, yearPct, 0xFFC77DFF, Ui.TEXT);
            ry += 2;
            ui.section("This month", ix, ry, iw, ROUTINE);
            ry += 11;
            ui.row("Days fully done", doneDays + "/" + pastDays + "  " + MenuUi.pct(pastDays == 0 ? 0 : (double) doneDays / pastDays), ix, ry, iw, Ui.GREEN);
            ry += 10;
            ui.row("Average per day", MenuUi.pct(pastDays == 0 ? 0 : sumRatio / pastDays), ix, ry, iw, Ui.TEXT);
            ry += 12;
            if (!Mayor.candidates.isEmpty() && ry < y + calH - 30) {
                ui.section("Election candidates", ix, ry, iw, MAYOR);
                ry += 11;
                ui.wrap(String.join(", ", Mayor.candidates), ix, ry, iw, Ui.MUTED);
            }
        }
        y += calH + gap;

        // selected day details
        LocalDate sel = selectedDay == null ? today : selectedDay;
        Map<String, Integer> done = Store.I.progress.getOrDefault("D:" + sel, Map.of());
        List<SbTime.Event> selEvents = eventsOn(sel);
        int detailH = MenuUi.TOP + Math.max(1, (done.size() + 1) / 2) * 11 + 4 + (selEvents.isEmpty() ? 0 : 13 + selEvents.size() * 11);
        double ratio = Routine.dayRatio("D:" + sel);
        int top = ui.card(x, y, w, detailH, sel.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.ENGLISH)) + (sel.equals(today) ? "  (today)" : ""), "writable_book",
                sel.isAfter(today) ? null : MenuUi.pct(ratio) + " of core dailies", CALENDAR);
        int dy = y + top;
        if (done.isEmpty()) ui.text(sel.isAfter(today) ? "Upcoming day." : "Nothing recorded on this day.", x + 6, dy, Ui.DIM);
        int k = 0;
        for (Map.Entry<String, Integer> en : done.entrySet()) {
            TaskDef t = Routine.find(en.getKey());
            String name = t == null ? en.getKey() : t.displayName();
            boolean full = t != null && en.getValue() >= t.target();
            String line = (full ? "✔ " : "◐ ") + name + (t != null && t.target() > 1 ? "  " + en.getValue() + "/" + t.target() + " " + MenuUi.pct((double) en.getValue() / t.target()) : "");
            ui.text(ui.ellipsize(line, w / 2 - 12), x + 6 + (k % 2) * (w / 2), dy + (k / 2) * 11, full ? Ui.GREEN : Ui.GOLD);
            k++;
        }
        dy += Math.max(1, (k + 1) / 2) * 11 + 2;
        if (!selEvents.isEmpty()) {
            ui.section("SkyBlock events this day", x + 6, dy, w - 12, EVENTS);
            dy += 12;
            for (SbTime.Event ev : selEvents) {
                ui.text(ui.ellipsize("● " + ev.name() + "  §7" + ev.hint(), w - 12), x + 6, dy, ev.color());
                dy += 11;
            }
        }
        y += detailH + gap;

        // everything coming up
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("EEE HH:mm", Locale.ENGLISH);
        ZoneId local = ZoneId.systemDefault();
        List<SbTime.Event> up = new ArrayList<>();
        for (SbTime.Event e : SbTime.events(now, 5 * 86_400_000L)) {
            if (e.id().equals("dark_auction") || e.id().equals("jacob")) continue; // hourly ones are on the dashboard / hud
            up.add(e);
            if (up.size() >= 12) break;
        }
        int upH = MenuUi.TOP + Math.max(1, up.size()) * 20 + 14;
        int upTop = ui.card(x, y, w, upH, "Upcoming events (next 5 days)", "clock", up.size() + " events", EVENTS);
        int uy = y + upTop;
        if (up.isEmpty()) ui.text("Nothing in the next 5 days.", x + 6, uy + 5, Ui.DIM);
        int shown = 0;
        for (SbTime.Event e : up) {
            boolean act = e.active(now);
            if (shown++ > 0) ui.hair(x + 4, uy - 1, w - 8);
            ui.rect(x + 2, uy, 2, 18, e.color());
            String when = act ? SbTime.dur(e.end() - now) + " left" : Instant.ofEpochMilli(e.start()).atZone(local).format(fmt) + "  ·  in " + SbTime.dur(e.start() - now);
            ui.textRight(when, x + w - 6, uy + 1, act ? Ui.GREEN : Ui.MUTED);
            int right = x + w - 6 - ui.width(when) - 4;
            if (act) right -= ui.chip(right - ui.chipWidth("LIVE"), uy, "LIVE", Ui.GREEN) + 4;
            ui.text(ui.ellipsize(e.name(), right - x - 12), x + 8, uy + 1, Ui.TEXT);
            ui.text(ui.ellipsize(e.hint(), act ? w / 2 : w - 14), x + 8, uy + 10, Ui.DIM);
            if (act) {
                double frac = (now - e.start()) / (double) Math.max(1, e.end() - e.start());
                ui.textRight(MenuUi.pct(frac) + " through", x + w - 6, uy + 10, Ui.DIM);
                ui.meter(x + w / 2 + 16, uy + 13, w / 2 - 90, 2, frac, e.color());
            }
            uy += 20;
        }
        ui.text(ui.ellipsize("Jacob's Contest starts every hour at :15 and the Dark Auction at :55 (shown on the HUD).", w - 12), x + 6, y + upH - 12, Ui.DIM);
        y += upH + gap;
        return y - y0;
    }
}
