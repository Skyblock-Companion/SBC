// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Profile;
import dev.zmuzy.sbc.core.SbTime;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.minion.Minions;
import dev.zmuzy.sbc.plan.MaxPlan;
import dev.zmuzy.sbc.plan.MoneyMethods;
import dev.zmuzy.sbc.tasks.Guide;
import dev.zmuzy.sbc.ui.theme.Theme;

import dev.zmuzy.sbc.plan.Planner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// planner, money, level and minions pages
final class PagesPlan {
    private static final MenuUi.Field capital = new MenuUi.Field("Flip budget (empty = purse + bank)", false);
    private static long planDirtyAt;

    private static final int PLAN = 0xFF5CD8FF, MAX = 0xFFC77DFF, TODAY = 0xFFFFD166, RANK = 0xFF4FD8C4, FORAGE = 0xFF81C784, COLLECT = 0xFFFFB454, MONEY = 0xFFFFD166,
            FLIPS = 0xFF5FEA8C, METHODS = 0xFFFFB627, PASSIVE = 0xFFB5D65F, SHARDS = 0xFFEC7DD8, LEVEL = 0xFF5CD8FF, SKILLS = 0xFF9CE37D, TASKS = 0xFFFF9EC7, MINIONS = 0xFFFFB454,
            FORGE = 0xFFFF8F6B;
    private PagesPlan() {}

    private static int tagColor(String tag) {
        return tag.equals("MEASURED") ? Ui.GREEN : tag.equals("DAILY") ? MenuUi.tone(MenuUi.PURPLE) : Ui.DIM;
    }

    private static String hours(double h) {
        return String.format(Locale.ROOT, "%.0f h", h);
    }

    // === PLANNER ===

    static int planner(MenuUi ui, int x, int y, int w) {
        int y0 = y, gap = MenuUi.GAP;
        y += ProfileFromGame.notice(ui, x, y, w, Profile.current, dev.zmuzy.sbc.core.game.GameProfile.SKILLS, dev.zmuzy.sbc.core.game.GameProfile.SLAYERS, dev.zmuzy.sbc.core.game.GameProfile.CATACOMBS, dev.zmuzy.sbc.core.game.GameProfile.HOTM, dev.zmuzy.sbc.core.game.GameProfile.HOTF, dev.zmuzy.sbc.core.game.GameProfile.COLLECTIONS);
        int half = (w - 10) / 2;
        int top = ui.card(x, y, w, 70, "Plan my day", "compass", PagesMain.goalName(), PLAN);
        double hoursNorm = (Config.I.planHours - 0.5) / 9.5;
        ui.slider("hours", x + 8, y + top, half - 12, hoursNorm, String.format(Locale.ROOT, "Time I have today:  %.1f h", Config.I.planHours), v -> {
            Config.I.planHours = Math.round((0.5 + v * 9.5) * 2) / 2.0;
            planDirtyAt = System.currentTimeMillis();
        });
        ui.slider("goal", x + half + 8, y + top, half - 12, Config.I.moneyWeight / 100.0,
                "Goal:  " + PagesMain.goalName() + "  (" + (100 - Config.I.moneyWeight) + "% level / " + Config.I.moneyWeight + "% money)", v -> {
                    Config.I.moneyWeight = (int) Math.round(v * 20) * 5;
                    planDirtyAt = System.currentTimeMillis();
                });
        int by = y + top + 28;
        ui.button("pmoney", x + 8, by, 84, 16, "$ Max Money", Config.I.moneyWeight >= 90 ? Ui.PRIMARY : Ui.SOFT, () -> {
            Config.I.moneyWeight = 100;
            planDirtyAt = System.currentTimeMillis();
        });
        ui.button("pbal", x + 96, by, 64, 16, "Balanced", Config.I.moneyWeight == 50 ? Ui.PRIMARY : Ui.SOFT, () -> {
            Config.I.moneyWeight = 50;
            planDirtyAt = System.currentTimeMillis();
        });
        ui.button("plevel", x + 164, by, 84, 16, "★ Max Level", Config.I.moneyWeight <= 10 ? Ui.PRIMARY : Ui.SOFT, () -> {
            Config.I.moneyWeight = 0;
            planDirtyAt = System.currentTimeMillis();
        });
        ui.button("pgen", x + w - 104, by, 96, 16, "↻ Build my plan", Ui.PRIMARY, () -> {
            Planner.plan();
            Toasts.push("Plan ready", Planner.lastPlan.size() + " steps for " + Config.I.planHours + "h", Ui.CYAN);
        });
        if (planDirtyAt > 0 && System.currentTimeMillis() - planDirtyAt > 400) {
            planDirtyAt = 0;
            Planner.plan();
        }
        y += 70 + gap;

        List<Planner.Step> plan = Planner.lastPlan.isEmpty() ? Planner.plan() : Planner.lastPlan;
        double totalCoins = plan.stream().mapToDouble(Planner.Step::coins).sum();
        double totalXp = plan.stream().mapToDouble(Planner.Step::sbXp).sum();
        int totalMin = plan.stream().mapToInt(Planner.Step::minutes).sum();
        String sum = "§f" + totalMin + " min  §6~" + Prices.coins(totalCoins) + " coins  §b~" + (int) totalXp + " SB XP";
        int stepH = 28, planH = MenuUi.TOP + Math.max(1, plan.size()) * stepH + 2;
        int ptop = ui.card(x, y, w, planH, "Your plan", "writable_book", sum, TODAY);
        int ry = y + ptop;
        int lineX = x + 11;
        if (plan.size() > 1) ui.gradV(lineX, ry + 12, 2, (plan.size() - 1) * stepH, Ui.alpha(MenuUi.tone(PLAN), 0.9f), Ui.alpha(MenuUi.tone(MAX), 0.5f));
        int i = 0;
        for (Planner.Step st : plan) {
            int h = stepH;
            ui.hoverRound(x + 20, ry - 1, w - 22, h - 1, 7, 0x10FFFFFF, 0x20FFFFFF);
            ui.rect(lineX - 5, ry + 6, 12, 12, i == 0 ? MenuUi.tone(PLAN) : MenuUi.tone(MAX));
            String number = String.valueOf(i + 1);
            ui.textScaled(number, lineX + 1 - ui.width(number) / 2f, ry + 8, 1f, 0xFF0A0F17, false);
            ui.icon(st.icon(), x + 24, ry + 4, 1f);
            int rx = x + w - 6;
            if (!st.island().isEmpty()) {
                rx -= 26;
                final Planner.Step fs = st;
                ui.button("sgo" + i, rx, ry + 5, 26, 14, "Go", Ui.PRIMARY, () -> PagesMain.goStep(fs));
                rx -= 5;
            }
            String m = st.minutes() + " min";
            ui.textRight(m, rx, ry + 2, Ui.TEXT);
            String val = (st.coins() > 0 ? "§6+" + Prices.coins(st.coins()) : "") + (st.sbXp() >= 1 ? "  §b+" + (int) st.sbXp() + " XP" : "");
            if (!val.isEmpty()) ui.textRight(val, rx, ry + 13, Ui.GOLD);
            int block = Math.max(ui.width(m), ui.width(val));
            String tag = st.taskId().isEmpty() ? (st.measured() ? "MEASURED" : "NOT MEASURED") : "DAILY";
            int cw = ui.chipWidth(tag);
            ui.chip(rx - block - 5 - cw, ry + 6, tag, tagColor(tag));
            int room = rx - block - 5 - cw - 4 - (x + 44);
            ui.text(ui.ellipsize(st.name(), room), x + 44, ry + 2, Ui.TEXT);
            ui.text(ui.ellipsize(st.why(), room), x + 44, ry + 13, Ui.DIM);
            ry += h;
            i++;
        }
        if (plan.isEmpty()) ui.text("Nothing left to plan - everything is done or the time is too short.", x + 6, ry + 8, Ui.MUTED);
        y += planH + gap;

        y += maxSection(ui, x, y, w);

        List<Planner.Scored> ranked = Planner.rank(Config.I.moneyWeight / 100.0);
        double maxScore = ranked.isEmpty() ? 1 : ranked.get(0).score();
        List<Planner.Scored> all = new ArrayList<>(ranked);
        for (Planner.Activity a : Planner.ACTIVITIES)
            if (Config.I.excludedActivities.contains(a.id())) all.add(new Planner.Scored(a, 0, 0, false, -1));
        int rowH = 19, rankH = MenuUi.TOP + 12 + all.size() * rowH + 2;
        int rtop = ui.card(x, y, w, rankH, "Every activity, ranked for " + PagesMain.goalName(), "iron_sword", all.size() + " activities", RANK);
        int ay = y + rtop;
        ui.text(ui.ellipsize("Click an activity to exclude/include it in plans. MEASURED = your real coins an hour (10+ active minutes); XP is an estimate.", w - 12), x + 6, ay, Ui.DIM);
        ay += 12;
        int rankN = 0;
        for (Planner.Scored sc : all) {
            boolean excluded = sc.score() < 0;
            int h = rowH;
            ui.hoverRound(x + 2, ay, w - 4, h - 1, 5, excluded ? (0x28000000) : rankN % 2 == 0 ? (0x0CFFFFFF) : 0x00000000, 0x20FFFFFF);
            ui.text(excluded ? "–" : String.valueOf(rankN + 1), x + 5, ay + 5, Ui.DIM);
            ui.icon(sc.a().icon(), x + 19, ay + 2, 0.875f);
            ui.text(ui.ellipsize(sc.a().name(), 150), x + 36, ay + 5, excluded ? Ui.DIM : Ui.TEXT);
            if (!excluded) {
                double share = sc.score() / Math.max(0.0001, maxScore);
                ui.meter(x + 190, ay + 7, 84, 3, share, RANK);
                ui.text(MenuUi.pct(share), x + 278, ay + 5, Ui.MUTED);
                String tag = sc.measured() ? "MEASURED" : "ESTIMATE";
                int cw = ui.chipWidth(tag);
                ui.chip(x + w - 5 - cw, ay + 4, tag, sc.measured() ? Ui.GREEN : Ui.DIM);
                String r = (sc.measured() ? "§6" + Prices.coins(sc.coinsPerHour()) + "/h" : "§7not measured") + "  §b" + String.format(Locale.ROOT, "%.1f", sc.sbXpPerHour()) + " XP/h";
                ui.textRight(r, x + w - 9 - cw, ay + 5, Ui.MUTED);
            } else {
                ui.textRight("excluded", x + w - 8, ay + 5, Ui.RED);
            }
            final String id = sc.a().id();
            ui.click(x, ay, w, h - 1, () -> {
                if (!Config.I.excludedActivities.remove(id)) Config.I.excludedActivities.add(id);
                planDirtyAt = System.currentTimeMillis();
            });
            ui.tip(x, ay, w, h - 1, sc.a().name(), "§7" + sc.a().how(), "§8Click to " + (excluded ? "include" : "exclude"));
            ay += h;
            rankN++;
        }
        y += rankH + gap;
        return y - y0;
    }

    // long term plan: everything to max, sorted for your goal, spread over your daily hours with ETAs
    private static int maxSection(MenuUi ui, int x, int y, int w) {
        int y0 = y, gap = MenuUi.GAP;
        MaxPlan.Result r = MaxPlan.get();
        int h = MenuUi.TOP + 42 + 14;
        int top = ui.card(x, y, w, h, "My plan to max everything", "nether_star", Config.I.planHours + " h/day", MAX);
        String[][] heads = {
                {"Everything maxed in", r.totalDays <= 0 ? "-" : days(r.totalDays)},
                {"SkyBlock Lv 300", eta(r, 300)}, {"Lv 400", eta(r, 400)}, {"Lv 500", eta(r, 500)},
                {"SB XP / day", String.format(Locale.ROOT, "%.0f", r.sbXpPerDay)}};
        int tg = 3, tw = (w - 8 - (heads.length - 1) * tg) / heads.length;
        for (int i = 0; i < heads.length; i++) {
            String[] t = heads[i];
            ui.tile(x + 4 + i * (tw + tg), y + top, tw, 40, t[0], t[1], null, t[1].equals("✔") ? Ui.GREEN : Ui.GOLD, -1, 0);
        }
        ui.text(ui.ellipsize("Each day: " + Math.round(r.dailyMinutes) + " min of dailies, then " + String.format(Locale.ROOT, "%.1f", r.focusHoursPerDay)
                + " h on your focus goals. Change hours / goal above; the plan updates live.", w - 12), x + 6, y + top + 43, Ui.DIM);
        y += h + gap;

        int blockH = 24, todayH = MenuUi.TOP + Math.max(1, r.today.size()) * blockH + 2;
        int todayMin = r.today.stream().mapToInt(MaxPlan.Block::minutes).sum();
        int ttop = ui.card(x, y, w, todayH, "Today  ·  " + r.today.size() + " blocks  ·  " + todayMin + " min", "clock", null, TODAY);
        ui.button("dayGo", x + w - 142, y + 1, 140, 12, Guide.active ? "■ Stop guided day" : "▶ Start my day (guided)", Guide.active ? Ui.DANGER : Ui.PRIMARY,
                () -> {
                    if (Guide.active) Guide.stop();
                    else Guide.start();
                });
        int ry = y + ttop;
        int bi = 0;
        for (MaxPlan.Block b : r.today) {
            if (bi > 0) ui.hair(x + 4, ry - 1, w - 8);
            if (bi == 0) ui.rect(x + 2, ry - 1, w - 4, blockH - 1, Ui.alpha(Ui.CYAN, 0.10f));
            ui.icon(b.icon(), x + 6, ry + 3, 1f);
            ui.text(ui.ellipsize(b.name(), w - 110), x + 26, ry + 2, Ui.TEXT);
            ui.text(ui.ellipsize(b.why(), w - 110), x + 26, ry + 12, Ui.DIM);
            ui.textRight(b.minutes() + " min", x + w - (b.island().isEmpty() ? 8 : 40), ry + 7, Ui.GOLD);
            if (!b.island().isEmpty()) {
                final MaxPlan.Block fb = b;
                ui.button("tb" + bi, x + w - 34, ry + 4, 28, 14, "Go", Ui.SOFT,
                        () -> PagesMain.goStep(new Planner.Step(fb.name(), fb.icon(), fb.minutes(), 0, 0, fb.island(), fb.node(), false, fb.why(), "")));
            }
            ry += blockH;
            bi++;
        }
        y += todayH + gap;

        List<MaxPlan.Phase> phases = r.phases.stream().limit(30).toList();
        int phaseH = 24, phasesH = MenuUi.TOP + Math.max(1, phases.size()) * phaseH + 2;
        int ptop = ui.card(x, y, w, phasesH, "Phases (one focus at a time, best value first)", "compass", phases.isEmpty() ? null : phases.size() + " goals", MAX);
        int py = y + ptop;
        int pi = 0;
        for (MaxPlan.Phase ph : phases) {
            MaxPlan.Goal g = ph.goal();
            if (pi > 0) ui.hair(x + 4, py - 1, w - 8);
            ui.text(String.valueOf(pi + 1), x + 5, py + 7, Ui.DIM);
            ui.icon(g.icon(), x + 18, py + 3, 1f);
            String tag = g.measured() ? "MEASURED" : "ESTIMATE";
            int cw = ui.chipWidth(tag);
            ui.chip(x + w - 5 - cw, py + 1, tag, g.measured() ? Ui.GREEN : Ui.DIM);
            ui.textRight("§f" + hours(g.hours()) + "  §b+" + Math.round(g.sbXp()) + " SB XP  §6day " + Math.round(ph.startDay()) + " → " + Math.round(ph.endDay()), x + w - 9 - cw, py + 2, Ui.TEXT);
            ui.text(ui.ellipsize(g.name(), 180), x + 38, py + 2, Ui.TEXT);
            String nums = Prices.coins(g.have()) + "/" + Prices.coins(g.need()) + " " + g.unit() + "  " + MenuUi.pct(g.progress());
            ui.meter(x + 38, py + 15, 180, 3, g.progress(), MAX);
            ui.text(ui.ellipsize(nums, w - 230), x + 224, py + 12, Ui.MUTED);
            ui.tip(x, py, w, phaseH - 1, () -> new String[]{g.name(), "§7" + g.how(), "§7Left: " + Prices.coins(Math.max(0, g.need() - g.have())) + " " + g.unit(),
                    "§7Your rate: " + Prices.coins(g.ratePerHour()) + " " + g.unit() + "/h" + (g.measured() ? " (measured)" : " (estimate for your gear)"),
                    g.coinsPerHour() > 0 ? "§6Earns about " + Prices.coins(g.coinsPerHour()) + "/h while you do it" : "§8Costs time, earns little"});
            py += phaseH;
            pi++;
        }
        if (phases.isEmpty()) ui.text(Profile.current == null ? "Join SkyBlock so the plan knows your levels." : "Everything is maxed!", x + 6, py + 7, Ui.MUTED);
        y += phasesH + gap;

        // foraging plan + collections closest to their next tier
        MaxPlan.Goal fo = r.goals.stream().filter(g -> g.id().equals("skill_Foraging")).findFirst().orElse(null);
        MaxPlan.Goal hf = r.goals.stream().filter(g -> g.id().equals("hotf")).findFirst().orElse(null);
        int half = (w - gap) / 2;
        int cardH = MenuUi.TOP + Math.max(6, Math.min(10, r.collectionTargets.size())) * 11 + 2;
        int ftop = ui.card(x, y, half, cardH, "Foraging plan", "oak_sapling", null, FORAGE);
        int fy = y + ftop;
        if (fo != null) {
            ui.row(fo.name(), hours(fo.hours()) + " (" + days(fo.hours() / r.focusHoursPerDay) + ")", x + 6, fy, half - 12, Ui.TEXT, Ui.GOLD);
            fy += 11;
        }
        if (hf != null) {
            ui.row(hf.name(), hours(hf.hours()), x + 6, fy, half - 12, Ui.TEXT, Ui.GOLD);
            fy += 11;
        }
        MoneyMethods.Loadout lo = MoneyMethods.loadout();
        String[] tips = {
                lo.has("TREECAPITATOR_AXE", "JUNGLE_AXE") ? "✔ You have a Treecapitator" : "• Get a Treecapitator (chops whole trees)",
                lo.pet("MONKEY", "LEGENDARY") ? "✔ Legendary Monkey pet" : "• Level a Monkey pet (faster chopping, more XP)",
                "• Galatea: chop Fig / Mangrove trees, sell the logs",
                "• Spend whispers on HOTF perks for fortune"};
        for (String t : tips) {
            if (fy > y + cardH - 10) break;
            ui.text(ui.ellipsize(t, half - 12), x + 6, fy, t.startsWith("✔") ? Ui.GREEN : Ui.MUTED);
            fy += 11;
        }
        int rx = x + half + gap, rw = w - half - gap;
        int ctop = ui.card(rx, y, rw, cardH, "Collections closest to the next tier", "chest", r.collectionTiersLeft > 0 ? r.collectionTiersLeft + " left" : null, COLLECT);
        int cy = y + ctop;
        if (r.collectionTargets.isEmpty()) ui.text(Profile.current == null ? "Join SkyBlock first" : !Profile.current.knows(dev.zmuzy.sbc.core.game.GameProfile.COLLECTIONS) ? dev.zmuzy.sbc.core.game.GameProfile.hint(dev.zmuzy.sbc.core.game.GameProfile.COLLECTIONS) : "Loading collection tiers…", rx + 6, cy, Ui.DIM);
        for (String[] c : r.collectionTargets) {
            if (cy > y + cardH - 10) break;
            ui.textRight(c[2], rx + rw - 6, cy, Ui.CYAN);
            ui.text(c[1], rx + rw / 2, cy, Ui.DIM);
            ui.text(ui.ellipsize(c[0], rw / 2 - 10), rx + 6, cy, Ui.TEXT);
            cy += 11;
        }
        y += cardH + gap;
        return y - y0;
    }

    private static String eta(MaxPlan.Result r, int level) {
        Double d = r.levelEtaDays.get(level);
        if (d == null) return Profile.current == null ? "?" : "past plan";
        return d <= 0 ? "✔" : days(d);
    }

    private static String days(double d) {
        if (d < 1) return String.format(Locale.ROOT, "%.0f h", d * 24);
        if (d < 60) return String.format(Locale.ROOT, "%.0f days", d);
        return String.format(Locale.ROOT, "%.1f months", d / 30.4);
    }

    // === FORGE ===

    private static boolean forgeOnlyMine = true;

    static int forge(MenuUi ui, int x, int y, int w) {
        int y0 = y, gap = MenuUi.GAP;
        int tier = dev.zmuzy.sbc.plan.Forge.hotm();
        List<dev.zmuzy.sbc.plan.Forge.Row> all = dev.zmuzy.sbc.plan.Forge.available() && Prices.ready() ? dev.zmuzy.sbc.plan.Forge.rows() : List.of();
        int top = ui.card(x, y, w, 62, "The Forge", "anvil", tier < 0 ? "HOTM ?" : "HOTM " + tier, FORGE);
        ui.button("fmine", x + w - 136, y + 1, 134, 12, forgeOnlyMine ? "✔ Only what I can forge" : "Show every recipe", Ui.SOFT, () -> forgeOnlyMine = !forgeOnlyMine);
        long mine = all.stream().filter(r -> !(tier >= 0 && r.hotm() > tier)).count();
        long good = all.stream().filter(r -> !(tier >= 0 && r.hotm() > tier) && r.certain() && r.profit() > 0).count();
        var best = all.stream().filter(r -> !(tier >= 0 && r.hotm() > tier) && r.certain() && r.profit() > 0).findFirst().orElse(null);
        int quick = dev.zmuzy.sbc.plan.Forge.quickForgeLevel();
        Profile.Data d = Profile.current;
        int tg = 3, tw = (w - 8 - 3 * tg) / 4, th = 26;
        ui.tile(x + 4, y + top, tw, th, "Your tier", tier < 0 ? "?" : tier + (d != null && d.hotm != null && d.hotm.progress() >= 0 ? " · " + MenuUi.pct(d.hotm.progress()) : ""), null, Ui.CYAN, d != null && d.hotm != null ? d.hotm.progress() : -1, FORGE);
        ui.tile(x + 4 + tw + tg, y + top, tw, th, "You can forge", all.isEmpty() ? "-" : mine + "/" + all.size() + " " + MenuUi.pct((double) mine / all.size()), null, Ui.TEXT, -1, 0);
        ui.tile(x + 4 + 2 * (tw + tg), y + top, tw, th, "Profitable now", all.isEmpty() ? "-" : String.valueOf(good), null, Ui.GREEN, -1, 0);
        ui.tile(x + 4 + 3 * (tw + tg), y + top, tw, th, "Best per hour", best == null ? "-" : Prices.coins(best.perHour()) + "/h", null, Ui.GOLD, -1, 0);
        String how = "Ingredients at Bazaar buy orders, the result at a sell offer less " + dev.zmuzy.sbc.core.ItemPrice.tax() + "% tax"
                + (quick > 0 ? " · Quick Forge " + quick + ": " + MenuUi.pct(dev.zmuzy.sbc.plan.Forge.quickForgePercent(quick) / 100) + " faster" : "");
        ui.text(ui.ellipsize(tier < 0 ? dev.zmuzy.sbc.core.game.GameProfile.hint(dev.zmuzy.sbc.core.game.GameProfile.HOTM) : how, w - 12), x + 6, y + top + th + 2, Ui.DIM);
        y += 62 + gap;
        if (!dev.zmuzy.sbc.plan.Forge.available()) {
            ui.text("Forge recipes come from the SkyBlock item repo cache (installed with another SkyBlock mod).", x, y, Ui.MUTED);
            return y - y0 + 20;
        }
        if (!Prices.ready()) {
            ui.text("Waiting for Bazaar prices…", x, y, Ui.MUTED);
            return y - y0 + 20;
        }
        List<dev.zmuzy.sbc.plan.Forge.Row> rows = new ArrayList<>();
        for (dev.zmuzy.sbc.plan.Forge.Row r : all) {
            boolean locked = tier >= 0 && r.hotm() > tier;
            if (forgeOnlyMine && (locked || !r.certain() || r.profit() <= 0)) continue;
            rows.add(r);
            if (rows.size() >= 60) break;
        }
        int rowH = 28, tableH = MenuUi.TOP + 11 + Math.max(1, rows.size()) * rowH + 2;
        int ttop = ui.card(x, y, w, tableH, "Recipes", "anvil", rows.size() + " shown", FORGE);
        int[] cols = {x + 26, x + w * 40 / 100, x + w * 50 / 100, x + w * 61 / 100, x + w * 72 / 100, x + w - 6};
        int hy = y + ttop - 2;
        ui.text("Recipe", cols[0], hy, Ui.DIM);
        ui.text("Time", cols[1], hy, Ui.DIM);
        ui.text("Cost", cols[2], hy, Ui.DIM);
        ui.text("Sells", cols[3], hy, Ui.DIM);
        ui.text("Profit", cols[4], hy, Ui.DIM);
        ui.textRight("Per hour", cols[5], hy, Ui.DIM);
        int ry = hy + 11;
        int shown = 0;
        for (dev.zmuzy.sbc.plan.Forge.Row r : rows) {
            boolean locked = tier >= 0 && r.hotm() > tier;
            int h = rowH;
            int zebra = shown % 2 == 0 ? 0x0CFFFFFF : 0x00000000;
            ui.hoverRound(x + 2, ry, w - 4, h - 1, 4, shown == 0 ? Ui.alpha(Ui.GOLD, 0.14f) : zebra, 0x20FFFFFF);
            String[] pp = r.id().split(":");
            ui.icon(r.id().startsWith("PET:") ? dev.zmuzy.sbc.core.ItemIcons.pet(pp[1], pp[2]) : dev.zmuzy.sbc.core.ItemIcons.of(r.id()), x + 5, ry + 5, 1f);
            String name = r.name() + (r.count() > 1 ? " ×" + r.count() : "");
            int nameRoom = cols[1] - cols[0] - 4;
            if (r.hotm() > 0) {
                String chip = "T" + r.hotm();
                int cw = ui.chipWidth(chip);
                ui.chip(cols[1] - cw - 4, ry + 1, chip, locked ? Ui.RED : MenuUi.tone(MenuUi.PURPLE));
                nameRoom -= cw + 2;
            }
            ui.text(ui.ellipsize(name, nameRoom), cols[0], ry + 3, locked ? Ui.DIM : Ui.TEXT);
            // rows own tooltip. an ingredients tooltip (added later) wins while the mouse is on that item
            ui.tip(x, ry, w, h - 1, () -> {
                List<String> tip = new ArrayList<>();
                tip.add(r.name() + (r.count() > 1 ? " ×" + r.count() : ""));
                tip.add("§7Takes " + SbTime.dur(r.seconds() * 1000) + " in the Forge"
                        + (r.seconds() != r.baseSeconds() ? " §8(" + SbTime.dur(r.baseSeconds() * 1000) + " without Quick Forge)" : "")
                        + (r.hotm() > 0 ? " · needs HOTM " + r.hotm() : ""));
                tip.add("");
                for (dev.zmuzy.sbc.plan.Forge.Input in : r.inputs())
                    tip.add("§7" + in.count() + "× " + dev.zmuzy.sbc.core.ItemRepo.name(in.id()) + "  §6"
                            + (in.price().known() ? Prices.coins(in.unitCost() * in.count()) + " §8" + in.price().source().words + (in.price().certain() ? "" : ", §euncertain") : "§cno price"));
                if (r.coins() > 0) tip.add("§7Coins  §6" + Prices.coins(r.coins()));
                tip.add("");
                tip.add("§7Ingredients: §6" + Prices.coins(r.cost()) + "  §7· the result: §6" + Prices.coins(r.sell()) + " §8" + r.result().source().words);
                tip.add(r.profit() >= 0 ? "§aProfit " + Prices.coins(r.profit()) : "§cLoss " + Prices.coins(-r.profit()));
                tip.add("§7Per hour §6" + Prices.coins(r.perHour()) + " §7· a day in one slot §6" + Prices.coins(r.perDay()));
                if (!r.certain()) tip.add("§eSome prices are uncertain (rarely sold): not ranked.");
                if (!r.id().startsWith("PET:")) tip.add("§eClick for the item card");
                return tip.toArray(new String[0]);
            });
            // ingredients as items with counts, hover for their tooltip
            int ix = cols[0];
            for (dev.zmuzy.sbc.plan.Forge.Input in : r.inputs()) {
                if (ix > cols[1] - 16) break;
                ui.icon(dev.zmuzy.sbc.core.ItemInfo.stack(in.id()), ix, ry + 13, 0.75f);
                if (in.count() > 1) ui.textScaled(String.valueOf(in.count()), ix + 9, ry + 21, 0.6f, 0xFFFFFFFF, true);
                final String part = in.id();
                ui.tip(ix, ry + 13, 12, 12, () -> {
                    List<String> tip = new ArrayList<>(dev.zmuzy.sbc.core.ItemInfo.tooltip(part));
                    tip.add("");
                    tip.add("§7Buy: §6" + dev.zmuzy.sbc.core.ItemPrice.buy(part).basis() + " §7each");
                    return tip.toArray(new String[0]);
                });
                ix += 18;
            }
            ui.text(SbTime.dur(r.seconds() * 1000), cols[1], ry + 9, Ui.MUTED);
            ui.text(Prices.coins(r.cost()), cols[2], ry + 9, Ui.MUTED);
            ui.text(r.priced() ? Prices.coins(r.sell()) + (r.certain() ? "" : "?") : "?", cols[3], ry + 9, r.certain() ? Ui.MUTED : Ui.ORANGE);
            String margin = r.cost() > 0 && r.priced() ? " " + MenuUi.pct(Math.abs(r.profit()) / r.cost()) : "";
            ui.text((r.profit() >= 0 ? "+" : "") + Prices.coins(r.profit()) + margin, cols[4], ry + 9, r.profit() >= 0 ? Ui.GREEN : Ui.RED);
            ui.textRight(Prices.coins(r.perHour()) + "/h", cols[5], ry + 9, r.perHour() > 0 ? Ui.GOLD : Ui.DIM);
            if (!r.id().startsWith("PET:")) {
                final String rid = r.id();
                ui.click(x, ry, w, h - 1, () -> ItemScreen.open(rid));
            }
            ry += h;
            shown++;
        }
        if (shown == 0) ui.text(forgeOnlyMine ? "Nothing profitable you can forge right now - try \"Show every recipe\"." : "No recipes.", x + 6, ry + 8, Ui.MUTED);
        y += tableH + gap;
        y += ui.wrap("Per hour counts each recipe as at least one hour of Forge time (you can't restart it every minute). Times are before the Quick Forge perk. HOTM tiers marked T come from Hypixel or from opening the recipe in the Forge once; recipes the mod "
                + "hasn't seen yet show for everyone. Selling price is what you get instantly (Bazaar) or the lowest BIN.", x + 2, y, w - 4, Ui.DIM);
        return y - y0 + 6;
    }

    // === MONEY ===

    static int money(MenuUi ui, int x, int y, int w) {
        int y0 = y, gap = MenuUi.GAP;
        double cap = parse(capital.value);
        if (cap <= 0) cap = Config.I.flipCapital > 0 ? Config.I.flipCapital : Math.max(0, GameState.purse) + Math.max(0, GameState.bank);
        String live = Prices.ready() ? "● Bazaar live" : Prices.error.isEmpty() ? "Loading Bazaar…" : Prices.error;
        int top = ui.card(x, y, w, 46, "Budget for flips", "gold_ingot", live, MONEY);
        ui.textScaled(cap > 0 ? Prices.coins(cap) : "?", x + 6, y + top + 1, 1.5f, Ui.GOLD, true);
        ui.text(cap > 0 ? "coins" : "unknown (join SkyBlock)", x + 6 + (int) (ui.width(cap > 0 ? Prices.coins(cap) : "?") * 1.5f) + 4, y + top + 5, Ui.DIM);
        ui.text(ui.ellipsize("Type an amount (10m, 1.5b) and press Enter to keep it", w - 12), x + 6, y + top + 16, Ui.DIM);
        int fw = Math.min(240, w / 2);
        ui.input(capital, x + w - fw - 6, y + top, fw);
        capital.onEnter = v -> Config.I.flipCapital = parse(v);
        y += 46 + gap;

        List<Prices.Flip> flips = Prices.flips(cap, 15);
        int rowH = 18, tableH = MenuUi.TOP + 11 + Math.max(1, flips.size()) * rowH + 2;
        double perHourSum = flips.stream().limit(4).mapToDouble(Prices.Flip::coinsPerHour).sum();
        int ttop = ui.card(x, y, w, tableH, "Best Bazaar flips right now", "emerald", flips.isEmpty() ? null : "top 4: +" + Prices.coins(perHourSum) + "/h", FLIPS);
        int[] cols = {x + 6, x + w * 34 / 100, x + w * 46 / 100, x + w * 58 / 100, x + w * 76 / 100, x + w - 6};
        int hy = y + ttop - 2;
        ui.text("Item", cols[0], hy, Ui.DIM);
        ui.text("Buy at", cols[1], hy, Ui.DIM);
        ui.text("Sell at", cols[2], hy, Ui.DIM);
        ui.text("Margin", cols[3], hy, Ui.DIM);
        ui.text("Items/h", cols[4], hy, Ui.DIM);
        ui.textRight("Profit/h", cols[5], hy, Ui.DIM);
        int ry = hy + 11;
        int i = 0;
        for (Prices.Flip f : flips) {
            int h = rowH;
            int zebra = i % 2 == 0 ? 0x0CFFFFFF : 0x00000000;
            ui.hoverRound(x + 2, ry, w - 4, h - 1, 4, zebra, 0x20FFFFFF);
            String fid = f.product().id();
            String name = dev.zmuzy.sbc.core.ItemRepo.name(fid);
            ui.icon(dev.zmuzy.sbc.core.ItemInfo.stack(fid), cols[0] - 2, ry + 2, 0.875f);
            ui.text(ui.ellipsize(name, cols[1] - cols[0] - 20), cols[0] + 14, ry + 5, Ui.TEXT);
            ui.text(Prices.coins(f.product().sell() + 0.1), cols[1], ry + 5, Ui.MUTED);
            ui.text(Prices.coins(f.product().buy() - 0.1), cols[2], ry + 5, Ui.MUTED);
            String mg = Prices.coins(f.marginEach()) + String.format(Locale.ROOT, " (%.0f%%)", f.marginPct());
            ui.text(ui.ellipsize(mg, cols[4] - cols[3] - 4), cols[3], ry + 5, Ui.GREEN);
            ui.text(Prices.coins(f.itemsPerHour()), cols[4], ry + 5, Ui.MUTED);
            ui.textRight("+" + Prices.coins(f.coinsPerHour()), cols[5], ry + 5, Ui.GOLD);
            ui.tip(x, ry, w, h - 1, () -> {
                List<String> tip = new ArrayList<>(dev.zmuzy.sbc.core.ItemInfo.tooltip(fid));
                tip.add("");
                tip.add("§7Buy order at §6" + Prices.coins(f.product().sell() + 0.1) + " §7· sell offer at §6" + Prices.coins(f.product().buy() - 0.1));
                tip.add("§7Moves " + Prices.coins(f.product().buyWeek()) + " / " + Prices.coins(f.product().sellWeek()) + " a week · " + f.product().buyOrders() + " / " + f.product().sellOrders() + " orders");
                if (f.maxAffordable() < Long.MAX_VALUE) tip.add("§7Your budget buys " + Prices.coins(f.maxAffordable()));
                tip.add("§8Assumes you capture 5% of hourly volume, 1.25% tax.");
                tip.add("§eClick for the item card");
                return tip.toArray(new String[0]);
            });
            ui.click(x, ry, w, h - 1, () -> ItemScreen.open(fid));
            ry += h;
            i++;
        }
        if (flips.isEmpty()) ui.text(Prices.ready() ? "No good spreads right now." : "Waiting for Bazaar data…", x + 6, ry + 5, Ui.MUTED);
        y += tableH + gap;

        y += moneyMethods(ui, x, y, w);
        return y - y0;
    }

    // every money method for your loadout, passive income, best shards to hunt rn
    private static int moneyMethods(MenuUi ui, int x, int y, int w) {
        int y0 = y, gap = MenuUi.GAP;
        MoneyMethods.Loadout lo = MoneyMethods.loadout();
        List<MoneyMethods.Rated> rated = MoneyMethods.ranked();
        List<MoneyMethods.Rated> locked = MoneyMethods.locked();
        double topRate = rated.isEmpty() ? 1 : Math.max(1, rated.get(0).coinsPerHour());
        int rowH = 26, h = MenuUi.TOP + 12 + rated.size() * rowH + 2 + (locked.isEmpty() ? 0 : 12);
        int top = ui.card(x, y, w, h, "Best ways to make money with your loadout", "diamond", rated.size() + " methods", METHODS);
        int ry = y + top;
        ui.text(ui.ellipsize(lo.known ? "Coins an hour are measured from your own play, never guessed: play a method with its tracker on to see it."
                : "Join SkyBlock so methods are checked against your gear and levels.", w - 12), x + 6, ry, Ui.DIM);
        ry += 12;
        int i = 0;
        for (MoneyMethods.Rated r : rated) {
            int rh = rowH;
            int f = i == 0 ? Ui.alpha(Ui.GOLD, 0.12f) : 0x00000000;
            ui.hoverRound(x + 2, ry, w - 4, rh - 1, 6, f, 0x18FFFFFF);
            if (i > 0) ui.hair(x + 4, ry - 1, w - 8);
            ui.icon(r.m().icon(), x + 5, ry + 4, 1f);
            int bx = x + w - 6;
            if (!r.m().island().isEmpty()) {
                final MoneyMethods.Rated fr = r;
                bx -= 26;
                ui.button("mmgo" + r.m().id(), bx, ry + 5, 26, 14, "Go", Ui.SOFT,
                        () -> PagesMain.goStep(new Planner.Step(fr.m().name(), fr.m().icon(), 60, fr.coinsPerHour(), 0, fr.m().island(), fr.m().node(), fr.measured(), fr.m().how(), "")));
                bx -= 5;
            }
            String rate = r.measured() ? Prices.coins(r.coinsPerHour()) + "/h ✔" : "not measured";
            ui.textRight(rate, bx, ry + 3, r.measured() ? Ui.GREEN : Ui.MUTED);
            double share = r.coinsPerHour() / topRate;
            ui.meter(bx - 86, ry + 15, 60, 3, share, 0xFFFFB627);
            ui.textRight(MenuUi.pct(share), bx, ry + 13, Ui.MUTED);
            ui.text(r.m().name(), x + 26, ry + 3, Ui.TEXT);
            ui.chip(x + 30 + ui.width(r.m().name()), ry + 1, r.tier().label(), Ui.CYAN);
            String nxt = r.next() == null ? "Top tier for this method" : "Next: " + r.next().label() + " - needs " + r.next().needs();
            ui.text(ui.ellipsize(nxt, w - 150), x + 26, ry + 13, r.next() == null ? Ui.GREEN : Ui.DIM);
            ui.tip(x, ry, w - 40, rh - 1, () -> {
                List<String> tip = new ArrayList<>();
                tip.add(r.m().name());
                tip.add("§7" + r.m().how());
                tip.add((r.measured() ? "§a" : "§e") + Character.toUpperCase(r.basis().charAt(0)) + r.basis().substring(1));
                for (MoneyMethods.Tier t : r.m().tiers())
                    tip.add((t == r.tier() ? "§a▶ " : t.req().test(lo) ? "§7✔ " : "§8✖ ") + t.label() + (t.needs().isEmpty() ? "" : " §8(" + t.needs() + ")"));
                return tip.toArray(new String[0]);
            });
            ry += rh;
            i++;
        }
        if (!locked.isEmpty()) {
            StringBuilder b = new StringBuilder("Not yet: ");
            for (int k = 0; k < locked.size(); k++) b.append(k > 0 ? ", " : "").append(locked.get(k).m().name()).append(" (").append(locked.get(k).missing()).append(")");
            ui.text(ui.ellipsize(b.toString(), w - 12), x + 6, ry + 1, Ui.MUTED);
            ui.tip(x, ry, w, 11, b.toString());
        }
        y += h + gap;

        double passive = MoneyMethods.minionsPerHour();
        ui.edgeCard(x, y, w, 22, PASSIVE);
        ui.icon("hopper", x + 6, y + 3, 1f);
        ui.text("Passive: your minions", x + 26, y + 7, Ui.TEXT);
        ui.textRight(passive > 0 ? "§6" + Prices.coins(passive) + "/h  §7·  §6" + Prices.coins(passive * 24) + "/day" : "walk past your minions so the mod can see them", x + w - 6, y + 7, passive > 0 ? Ui.GOLD : Ui.DIM);
        y += 22 + gap;

        List<Object[]> shards = MoneyMethods.bestShards(12);
        if (!shards.isEmpty()) {
            int rows = (shards.size() + 1) / 2, sh = MenuUi.TOP + rows * 13 + 2;
            int stop = ui.card(x, y, w, sh, "Best shards to hunt right now (Bazaar)", "prismarine_shard", shards.size() + " shards", SHARDS);
            int col = (w - 12 - 10) / 2;
            for (int k = 0; k < shards.size(); k++) {
                Object[] s = shards.get(k);
                int cx = x + 6 + (k % 2) * (col + 10), cy = y + stop + (k / 2) * 13;
                ui.textRight(Prices.coins((double) s[1]) + " each", cx + col, cy, Ui.GOLD);
                ui.text("~" + Prices.coins((double) s[2]) + " sold/h", cx + col - 120, cy, Ui.DIM);
                ui.text(ui.ellipsize(dev.zmuzy.sbc.core.ItemRepo.name((String) s[0]), col - 124), cx, cy, Ui.TEXT);
            }
            y += sh + gap;
        }
        y += ui.wrap("Rates are per hour of active play for a player at that tier; Mayor perks boost matching methods. Hover a method to see every tier and what it needs.", x + 2, y, w - 4, Ui.DIM);
        return y - y0 + 6;
    }

    private static double parse(String v) {
        long n = GameState.parseShort(v == null ? "" : v.trim());
        return n > 0 ? n : 0;
    }

    // === LEVEL ===

    static int level(MenuUi ui, int x, int y, int w) {
        int y0 = y, gap = MenuUi.GAP;
        y += maxPlan(ui, x, y, w);

        String[] skills = {"Farming", "Mining", "Combat", "Foraging", "Fishing", "Enchanting", "Alchemy", "Taming", "Carpentry", "Hunting"};
        int rows = (skills.length + 1) / 2, cellH = 30, h = MenuUi.TOP + 12 + rows * cellH + 2;
        int top = ui.card(x, y, w, h, "SkyBlock XP from levelling your skills", "experience_bottle", null, SKILLS);
        ui.text(ui.ellipsize("For each skill: XP to your next level and the SkyBlock XP that level gives. Faster skills rank higher in Max Level plans.", w - 12), x + 6, y + top, Ui.DIM);
        int gy = y + top + 12;
        int col = (w - 8 - 3) / 2;
        int i = 0;
        for (String sk : skills) {
            int lvl = Planner.currentLevel(sk);
            double pct = progressOf(sk);
            int cx = x + 4 + (i % 2) * (col + 3), cy = gy + (i / 2) * cellH;
            int accent = PagesMore.skillColor(sk);
            int next = lvl + 1;
            int sb = next <= 10 ? 5 : next <= 25 ? 10 : next <= 50 ? 20 : 30;
            Profile.Level pl = Profile.current == null ? null : Profile.current.skills.get(sk);
            boolean maxed = lvl >= 60 || (pl != null && pl.toNext() <= 0);
            ui.rect(cx, cy, col, cellH - 2, 0x12FFFFFF);
            ui.rect(cx, cy, 2, cellH - 2, MenuUi.tone(accent));
            ui.icon(PagesMore.skillIcon(sk), cx + 4, cy + 3, 0.75f);
            ui.text(sk + " " + (lvl < 0 ? "?" : String.valueOf(lvl)), cx + 19, cy + 2, lvl < 0 ? Ui.DIM : Ui.TEXT);
            String right = lvl < 0 ? "unknown" : maxed ? "MAX" : "+" + sb + " SB XP";
            ui.textRight(right, cx + col - 4, cy + 2, lvl < 0 ? Ui.DIM : maxed ? Ui.GOLD : Ui.CYAN);
            String nums = maxed ? "Max level" : pl != null && pl.progress() < 0 ? "" : pl != null ? "§e" + MenuUi.num(PagesMore.have(pl)) + "§7/§e" + MenuUi.num(PagesMore.need(pl)) + "  §b" + MenuUi.pct(pct) + "  §7" + MenuUi.num(pl.toNext()) + " to " + next
                    : lvl < 0 ? "" : "§b" + MenuUi.pct(pct);
            ui.text(ui.ellipsize(nums, col - 24), cx + 19, cy + 12, Ui.MUTED);
            ui.meter(cx + 4, cy + 23, col - 8, 2, maxed ? 1 : pct, accent);
            ui.tip(cx, cy, col, cellH - 2, sk + (lvl < 0 ? "" : " " + lvl),
                    pl == null ? "§7" + dev.zmuzy.sbc.core.game.GameProfile.hint(dev.zmuzy.sbc.core.game.GameProfile.SKILLS) : "§7Total XP: " + Prices.coins(pl.xp()),
                    pl != null && pl.progress() < 0 ? "§7How far into this level has not been read yet." : "§7Progress to next level: " + Math.round(pct * 100) + "%");
            i++;
        }
        y += h + gap;

        List<Store.LevelTask> tasks = new ArrayList<>(Store.I.levelTasks);
        if (tasks.isEmpty()) {
            int gtop = ui.card(x, y, w, 56, "Task Guide (scanned from the game)", "writable_book", null, TASKS);
            ui.text("Open the SkyBlock Leveling menu in-game (SkyBlock Menu → SkyBlock Leveling → Task Guide)", x + 6, y + gtop, Ui.TEXT);
            ui.text("and browse the categories. The mod reads every task, its XP and whether you finished it.", x + 6, y + gtop + 11, Ui.MUTED);
            ui.text("It only reads what is shown - it never clicks for you.", x + 6, y + gtop + 22, Ui.DIM);
            return y - y0 + 62;
        }
        long open = tasks.stream().filter(t -> !t.done).count();
        int openXp = tasks.stream().filter(t -> !t.done).mapToInt(t -> t.xp).sum();
        int doneXp = tasks.stream().filter(t -> t.done).mapToInt(t -> t.xp).sum();
        Map<String, List<Store.LevelTask>> byCat = new LinkedHashMap<>();
        tasks.sort(Comparator.comparing((Store.LevelTask t) -> t.done).thenComparing(t -> -t.xp));
        for (Store.LevelTask t : tasks) byCat.computeIfAbsent(t.category, k -> new ArrayList<>()).add(t);
        int guideH = MenuUi.TOP + 16;
        for (List<Store.LevelTask> list : byCat.values()) guideH += 13 + list.size() * 12;
        int done = tasks.size() - (int) open;
        int gtop = ui.card(x, y, w, guideH, "Task Guide (scanned from the game)", "writable_book", done + "/" + tasks.size() + "  " + MenuUi.pct((double) done / tasks.size()), TASKS);
        int ty = y + gtop;
        ui.text(ui.ellipsize(open + " open tasks worth " + openXp + " SkyBlock XP · " + doneXp + " XP done · scanned " + SbTime.dur(System.currentTimeMillis() - Store.I.levelTasksScannedAt) + " ago", w - 12), x + 6, ty, Ui.GOLD);
        ty += 12;
        for (Map.Entry<String, List<Store.LevelTask>> e : byCat.entrySet()) {
            long catDone = e.getValue().stream().filter(t -> t.done).count();
            ui.section(e.getKey() + "  " + catDone + "/" + e.getValue().size(), x + 6, ty + 1, w - 12, TASKS);
            ty += 13;
            for (Store.LevelTask t : e.getValue()) {
                ui.text(t.done ? "✔" : "•", x + 8, ty, t.done ? Ui.GREEN : MenuUi.tone(MenuUi.PURPLE));
                ui.textRight("+" + t.xp + " XP", x + w - 6, ty, t.done ? Ui.DIM : Ui.CYAN);
                if (!t.progress.isEmpty()) ui.textRight(t.progress, x + w - 54, ty, Ui.MUTED);
                ui.text(ui.ellipsize(t.name, w - 140), x + 18, ty, t.done ? Ui.DIM : Ui.TEXT);
                ty += 12;
            }
        }
        y += guideH + gap;
        return y - y0;
    }

    // road to max: ETAs for sb level milestones + every skill, from your measured pace and daily hours
    private static int maxPlan(MenuUi ui, int x, int y, int w) {
        int y0 = y, gap = MenuUi.GAP;
        Profile.Data d = Profile.current;
        double hoursPerDay = Math.max(0.5, Config.I.planHours);
        long active = 0;
        double sbXp = 0;
        java.util.Map<String, Double> skillRate = new java.util.HashMap<>();
        for (Store.Rate r : Store.I.rates.values()) {
            active += r.activeMs;
            sbXp += r.sbXp;
            for (String sk : r.skillXp.keySet()) skillRate.merge(sk, r.skillPerHour(sk), Math::max);
        }
        double sbPerHour = active < 600_000 ? 0 : sbXp * 3_600_000.0 / active;
        double cur = d == null ? -1 : d.sbXp;
        int rows = d == null ? 0 : (int) d.skills.keySet().stream().filter(sk -> !d.fromGame || !dev.zmuzy.sbc.core.game.GameRead.ownTable(sk)).count();
        int h = MenuUi.TOP + 42 + (rows > 0 ? 4 + rows * 13 : 0) + 2;
        int top = ui.card(x, y, w, h, "Road to max  ·  " + hoursPerDay + " h/day (change it in Planner)", "experience_bottle", null, LEVEL);
        int tx = x + 4, ty = y + top, tg = 3, tw = (w - 8 - 3 * tg) / 4;
        int into = cur < 0 ? 0 : (int) (cur % 100);
        ui.tile(tx, ty, tw + 40, 40, "SkyBlock Level", cur < 0 ? "?" : String.valueOf((int) (cur / 100)), cur < 0 ? "" : into + "/100 XP · " + into + "%", Ui.CYAN, cur < 0 ? -1 : into / 100.0, LEVEL);
        int ex = tx + tw + 40 + tg, ew = (w - 8 - (tw + 40) - 3 * tg - 90) / 3;
        for (int target : new int[]{300, 400, 500}) {
            String eta;
            if (cur < 0 || sbPerHour <= 0) eta = "?";
            else if (cur >= target * 100) eta = "✔";
            else eta = String.format(Locale.ROOT, "%.0fd", (target * 100 - cur) / sbPerHour / hoursPerDay);
            double frac = cur < 0 ? -1 : Math.min(1, cur / (target * 100));
            ui.tile(ex, ty, ew, 40, "Lv " + target, eta, frac < 0 ? null : MenuUi.pct(frac), eta.equals("✔") ? Ui.GREEN : Ui.GOLD, frac, 0xFFFFD166);
            ex += ew + tg;
        }
        ui.tile(ex, ty, x + w - 4 - ex, 40, "Your pace", sbPerHour > 0 ? String.format(Locale.ROOT, "%.1f", sbPerHour) : "?", sbPerHour > 0 ? "SB XP/hour" : "play ~10 min", Ui.TEXT, -1, 0);
        int ry = ty + 44;
        if (d != null) {
            for (java.util.Map.Entry<String, Profile.Level> e : d.skills.entrySet()) {
                String sk = e.getKey();
                if (d.fromGame && dev.zmuzy.sbc.core.game.GameRead.ownTable(sk)) continue;
                Profile.Level l = e.getValue();
                int cap = PagesMore.skillCap(sk);
                double toMax = Math.max(0, Profile.SKILL_XP[Math.min(cap, Profile.SKILL_XP.length - 1)] - l.xp());
                double rate = skillRate.getOrDefault(sk, 0.0);
                String eta = toMax <= 0 ? "MAXED" : rate <= 0 ? "do it once to measure" : String.format(Locale.ROOT, "%.0fh  ·  %.0f days", toMax / rate, toMax / rate / hoursPerDay);
                double frac = cap == 0 ? 0 : Math.min(1, l.xp() / Profile.SKILL_XP[Math.min(cap, Profile.SKILL_XP.length - 1)]);
                ui.icon(PagesMore.skillIcon(sk), x + 5, ry, 0.625f);
                ui.text(ui.ellipsize(sk + " " + l.level() + "/" + cap, 110), x + 17, ry + 1, Ui.TEXT);
                ui.meter(x + 130, ry + 3, 116, 3, frac, PagesMore.skillColor(sk));
                ui.text(MenuUi.pct(frac), x + 250, ry + 1, Ui.MUTED);
                ui.text(toMax <= 0 ? "" : Prices.coins(toMax) + " XP left", x + 284, ry + 1, Ui.DIM);
                ui.textRight(eta, x + w - 6, ry + 1, toMax <= 0 ? Ui.GREEN : Ui.GOLD);
                ry += 13;
            }
        }
        y += h + gap;
        return y - y0;
    }

    private static double progressOf(String skill) {
        Profile.Data d = Profile.current;
        if (d != null && d.skills.containsKey(skill)) return Math.max(0, d.skills.get(skill).progress());
        if (d != null && d.fromGame) return 0; // another profiles numbers arent this ones
        double[] o = Store.I.observedSkills.get(skill);
        return o == null ? 0 : o[1] / 100.0;
    }

    // === MINIONS ===

    static int minions(MenuUi ui, int x, int y, int w) {
        int y0 = y, gap = MenuUi.GAP;
        List<Store.MinionRecord> list = new ArrayList<>(Store.I.minions.values());
        list.sort(Comparator.comparingDouble(Minions::coinsPerDay).reversed());
        double total = list.stream().mapToDouble(Minions::coinsPerDay).sum();
        long measured = list.stream().filter(Minions::measured).count();
        int top = ui.card(x, y, w, 62, "Your minions earn", "hopper", list.size() + " minions tracked", MINIONS);
        int tg = 3, tw = (w - 8 - 3 * tg) / 4;
        ui.tile(x + 4, y + top, tw, 26, "Per day", Prices.coins(total), null, Ui.GOLD, -1, 0);
        ui.tile(x + 4 + tw + tg, y + top, tw, 26, "Per hour", Prices.coins(total / 24), null, Ui.GOLD, -1, 0);
        ui.tile(x + 4 + 2 * (tw + tg), y + top, tw, 26, "Measured", list.isEmpty() ? "-" : measured + "/" + list.size() + " " + MenuUi.pct((double) measured / list.size()), null, Ui.GREEN, list.isEmpty() ? -1 : (double) measured / list.size(), 0xFF5FEA8C);
        ui.tile(x + 4 + 3 * (tw + tg), y + top, tw, 26, "Best minion", list.isEmpty() || Minions.coinsPerDay(list.get(0)) <= 0 ? "-" : Prices.coins(Minions.coinsPerDay(list.get(0))) + "/day", null, Ui.TEXT, -1, 0);
        ui.text(ui.ellipsize("Estimated from speed + Bazaar; measured ones exact. Detected automatically on your island.", w - 12), x + 6, y + top + 28, Ui.DIM);
        y += 62 + gap;

        // what to place rn
        List<Object[]> best = dev.zmuzy.sbc.minion.MinionCatalog.best(11, 8);
        if (!best.isEmpty()) {
            // your slots from the crafted minions item or the island tab. not seen yet = say what to open, no guessing
            dev.zmuzy.sbc.core.game.Seen seen = dev.zmuzy.sbc.core.game.GameProfile.current();
            int slots = seen == null ? -1 : seen.minionSlots;
            int h = MenuUi.TOP + best.size() * 13 + 2 + 24;
            int btop = ui.card(x, y, w, h, "Most profitable minions right now (tier XI, no upgrades)", "emerald", slots > 0 ? "your " + slots + " slots" : null, 0xFFFFD166);
            double topRate = (double) best.get(0)[2];
            int ry = y + btop;
            for (Object[] b : best) {
                String type = (String) b[0];
                double rate = (double) b[2];
                ui.text(ui.ellipsize(GameState.pretty(type) + " " + b[1], 140), x + 6, ry, Ui.TEXT);
                ui.text(ui.ellipsize(dev.zmuzy.sbc.minion.MinionCatalog.dropName(type), 110), x + 150, ry, Ui.DIM);
                ui.meter(x + 266, ry + 3, w - 266 - 196, 3, rate / topRate, 0xFFFFD166);
                ui.textRight(Prices.coins(rate) + "/day §7each", x + w - 96, ry, Ui.GOLD);
                ui.textRight(slots > 0 ? Prices.coins(rate * slots) + "/day §7all" : "§8slots ?", x + w - 6, ry, Ui.GOLD);
                ry += 13;
            }
            ui.text(ui.ellipsize("Drops per harvest from the wiki's minion pages, speed from the item data, sold on the Bazaar or to an NPC (never the AH).", w - 12), x + 6, ry + 2, Ui.DIM);
            ui.text(ui.ellipsize(slots > 0 ? "All = one minion of that kind in each of your " + slots + " slots." : "Open Collections or Crafted Minions once (or stand on your island) so the mod knows your minion slots.", w - 12), x + 6, ry + 13, Ui.DIM);
            y += h + gap;
        }
        if (list.isEmpty()) {
            int etop = ui.card(x, y, w, 52, "Your minions", "hopper", null, MINIONS);
            ui.wrap("No minions seen yet. Just walk around your island - the mod recognises every minion by its head (type and tier) and estimates its profit. Opening a minion once adds its real fuel and upgrades.", x + 6, y + etop, w - 12, Ui.MUTED);
            return y - y0 + 58;
        }
        int col = (w - gap) / 2;
        int i = 0, rowY = y, maxH = 0;
        for (Store.MinionRecord r : list) {
            int cx = x + (i % 2) * (col + gap);
            if (i % 2 == 0 && i > 0) {
                rowY += maxH + gap;
                maxH = 0;
            }
            int h = drawMinion(ui, r, cx, rowY, col, total);
            maxH = Math.max(maxH, h);
            i++;
        }
        y = rowY + maxH + gap;
        return y - y0;
    }

    private static int drawMinion(MenuUi ui, Store.MinionRecord r, int x, int y, int w, double total) {
        List<Minions.Advice> advice = Minions.advise(r);
        int shownAdvice = Math.min(3, advice.size());
        int h = MenuUi.TOP + 38 + (shownAdvice > 0 ? 11 + shownAdvice * 10 : 0) + 2;
        double perDay = Minions.coinsPerDay(r);
        String right = perDay > 0 ? (Minions.measured(r) ? "" : "~") + Prices.coins(perDay) + "/day" : "no price";
        int top = ui.card(x, y, w, h, Minions.pretty(r.type) + " Minion " + r.tier, "hopper", right, MINIONS);
        int ix = x + 6, iw = w - 12, ry = y + top;
        ui.text(ui.ellipsize(GameState.pretty(r.island) + " · seen " + SbTime.dur(System.currentTimeMillis() - r.lastSeen) + " ago", iw - 70), ix, ry, Ui.DIM);
        double full = Minions.hoursUntilFull(r);
        if (full >= 0) ui.textRight(full < 1 ? "FULL soon" : "full in " + SbTime.dur((long) (full * 3_600_000)), ix + iw, ry, full < 2 ? Ui.RED : Ui.MUTED);
        ry += 11;
        if (perDay > 0 && total > 0) {
            ui.meter(ix, ry + 3, iw - 110, 3, perDay / total, MINIONS);
            ui.textRight(MenuUi.pct(perDay / total) + " of all minions", ix + iw, ry, Ui.MUTED);
        } else {
            ui.text(Minions.measured(r) ? "Measured" : "Estimated", ix, ry, Ui.DIM);
        }
        ry += 12;
        int cx = ix;
        if (!r.fuel.isEmpty()) cx += ui.chip(cx, ry, r.fuel, Ui.ORANGE) + 3;
        for (String u : r.upgrades) {
            if (cx + ui.chipWidth(u) > x + w - 4) break;
            cx += ui.chip(cx, ry, u, Ui.CYAN) + 3;
        }
        if (r.fuel.isEmpty() && r.upgrades.isEmpty()) ui.chip(cx, ry, "no fuel / upgrades", Ui.DIM);
        ry += 14;
        if (shownAdvice > 0) {
            ui.section("Best upgrades for this minion", ix, ry, iw, MINIONS);
            ry += 11;
            for (Minions.Advice a : advice.stream().limit(3).toList()) {
                String pay = a.extraPerDay() > 0 && a.cost() > 0 ? "  · pays back in " + String.format(Locale.ROOT, "%.1f", a.daysToPayBack()) + "d" : "";
                String line = "• " + a.text() + (a.extraPerDay() > 0 ? "  (+" + Prices.coins(a.extraPerDay()) + "/day)" : "") + pay + (a.certain() ? "" : "  ?");
                ui.text(ui.ellipsize(line, iw), ix, ry, a.certain() ? Ui.TEXT : Ui.MUTED);
                ui.tip(ix, ry - 1, iw, 10, line, "§7Cost at Bazaar buy orders; what it adds at sell offers less tax, fuel taken off.",
                        a.certain() ? "§7Every price here sells often enough to count." : "§eA price here is rarely traded, so this is not ranked.");
                ry += 10;
            }
        }
        return h;
    }
}
