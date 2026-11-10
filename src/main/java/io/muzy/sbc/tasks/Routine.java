// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.tasks;

import io.muzy.sbc.core.Config;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.core.SbTime;
import io.muzy.sbc.core.SbTime.Period;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.ui.Toasts;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// builds todays routine on its own (unlocked islands, active events, your custom tasks), tracks progress from
// chat, keeps streaks + history, warns before resets
public final class Routine {
    public record Entry(TaskDef def, int done, long resetIn) {
        public boolean complete() { return done >= def.target(); }
    }

    private static final Map<String, List<Pattern>> compiled = new HashMap<>();
    private static String learning;
    private static long learningUntil;
    private static final Set<String> alerted = new HashSet<>();
    private static long lastAlertCheck;

    private Routine() {}

    // --- building the list ---

    public static List<TaskDef> allTasks() {
        List<TaskDef> all = new ArrayList<>(Catalog.TASKS);
        for (Store.CustomTask c : Store.I.customTasks) {
            Period p;
            try {
                p = Period.valueOf(c.period);
            } catch (Exception e) {
                p = Period.DAILY;
            }
            all.add(new TaskDef(c.id, c.name, "Custom", "writable_book", p, Math.max(1, c.target), c.minutes, 0, 0, true,
                    c.island == null ? "" : c.island, "", "Your own task", List.of(), c.regex == null || c.regex.isBlank() ? List.of() : List.of(c.regex), ""));
        }
        return all;
    }

    public static boolean unlocked(TaskDef t, long now) {
        if (Config.I.hiddenTasks.contains(t.id())) return false;
        Long back = Store.I.timers.get("repeat|" + t.id());
        if (back != null && now < back && progress(t, now) == 0) return false; // still on cooldown
        if (!t.event().isEmpty()) {
            boolean active = false;
            for (SbTime.Event e : SbTime.events(now, 0)) if (e.id().startsWith(t.event().replace("_summer", "")) && e.active(now)) active = true;
            if (!active) return false;
        }
        return true;
    }

    // how bad missing a task today is. 3 = critical (bonus gone for good after the reset and worth a lot), 2 =
    // important, 1 = optional. custom tasks count as important
    private static final Map<String, Integer> PRIORITY = Map.ofEntries(
            Map.entry("commissions", 3), Map.entry("crimson_quests", 3), Map.entry("experiments", 3), Map.entry("visitors", 3),
            Map.entry("matriarch", 3), Map.entry("fetchur", 3), Map.entry("puzzler", 3), Map.entry("hoppity_eggs", 3),
            Map.entry("vote", 3), Map.entry("new_year", 3), Map.entry("pomtair", 3), Map.entry("sirih", 3), Map.entry("suus", 3),
            Map.entry("jacob", 2), Map.entry("nucleus", 2), Map.entry("kuudra", 2), Map.entry("composter", 2), Map.entry("pests", 2),
            Map.entry("forge_collect", 2), Map.entry("minions", 2), Map.entry("bazaar_orders", 2), Map.entry("dojo", 2),
            Map.entry("chocolate", 2), Map.entry("rift", 2), Map.entry("museum", 2));

    // fixed order for the top dailies: commissions, then crimson rep, then the rest
    private static final List<String> TOP = List.of("commissions", "crimson_quests", "pomtair", "sirih", "suus", "visitors", "experiments", "matriarch",
            "fetchur", "puzzler", "kuudra", "dojo", "jacob", "nucleus");

    // tasks that come back every few days not every day
    public static final Map<String, Integer> REPEAT_DAYS = Map.of("suus", 2);

    public static int priority(TaskDef t) {
        if (t.id().startsWith("custom_")) {
            for (Store.CustomTask c : Store.I.customTasks) if (c.id.equals(t.id())) return Math.max(1, Math.min(3, c.priority));
            return 2;
        }
        return PRIORITY.getOrDefault(t.id(), 1);
    }

    private static int topIndex(TaskDef t) {
        int i = TOP.indexOf(t.id());
        return i < 0 ? 99 : i;
    }

    public static String priorityLabel(TaskDef t) {
        return switch (priority(t)) { case 3 -> "CRITICAL"; case 2 -> "IMPORTANT"; default -> "OPTIONAL"; };
    }

    private static List<Entry> cached = List.of();
    private static long cachedAt;

    // todays routine: core tasks first, then value per minute, done ones sink to the bottom
    public static List<Entry> today() {
        long now = System.currentTimeMillis();
        // the hud asks every frame, rebuilding twice a sec is plenty and keeps it cheap
        if (now - cachedAt < 500) return cached;
        cachedAt = now;
        cached = build(now);
        return cached;
    }

    private static List<Entry> build(long now) {
        List<Entry> out = new ArrayList<>();
        for (TaskDef t : allTasks()) {
            if (!unlocked(t, now)) continue;
            out.add(new Entry(t, progress(t, now), SbTime.untilReset(t.period(), now)));
        }
        // open tasks first. among them the ones lost at the next reset + worth the most, then soonest reset
        out.sort(Comparator.comparing(Entry::complete)
                .thenComparing(e -> -priority(e.def()))
                .thenComparingInt(e -> topIndex(e.def()))
                .thenComparing(e -> e.def().period() != Period.DAILY)
                .thenComparing(e -> -(e.def().coins() / 1e5 + e.def().sbXp() * 3) / Math.max(1, e.def().minutes())));
        return out;
    }

    public static int[] counts() {
        int done = 0, total = 0;
        for (Entry e : today()) {
            if (e.def().period() != Period.DAILY && e.def().period() != Period.SB_DAY && e.def().period() != Period.EVENT) continue;
            total++;
            if (e.complete()) done++;
        }
        return new int[]{done, total};
    }

    // --- progress ---

    public static int progress(TaskDef t, long now) {
        Map<String, Integer> m = Store.I.progress.get(SbTime.periodKey(t.period(), now));
        return m == null ? 0 : m.getOrDefault(t.id(), 0);
    }

    public static void add(TaskDef t, int amount) {
        long now = System.currentTimeMillis();
        String key = SbTime.periodKey(t.period(), now);
        Map<String, Integer> m = Store.I.progress.computeIfAbsent(key, k -> new HashMap<>());
        int before = m.getOrDefault(t.id(), 0);
        int after = Math.max(0, Math.min(t.target(), before + amount));
        m.put(t.id(), after);
        Store.markDirty();
        cachedAt = 0;
        if (before < t.target() && after >= t.target() && REPEAT_DAYS.containsKey(t.id()))
            Store.I.timers.put("repeat|" + t.id(), now + REPEAT_DAYS.get(t.id()) * 86_400_000L - 3_600_000L);
        if (before < t.target() && after >= t.target()) {
            Toasts.push("Task complete", t.name(), 0xFF4CE08A);
            int[] c = counts();
            if (c[1] > 0 && c[0] == c[1]) Toasts.push("Routine complete!", "Every daily task is done. Nice!", 0xFFF4C542);
        }
    }

    public static void toggle(TaskDef t) {
        int p = progress(t, System.currentTimeMillis());
        add(t, p >= t.target() ? -t.target() : t.target() - p);
    }

    // days in a row (ending today or yesterday) with every core daily done
    public static int streak(String taskId) {
        TaskDef t = taskId == null ? null : Catalog.byId(taskId);
        long now = System.currentTimeMillis();
        int s = 0;
        for (int d = 0; d < 365; d++) {
            long when = now - d * 86_400_000L;
            String key = SbTime.periodKey(Period.DAILY, when);
            Map<String, Integer> m = Store.I.progress.get(key);
            boolean ok;
            if (t != null) ok = m != null && m.getOrDefault(t.id(), 0) >= t.target();
            else ok = m != null && !m.isEmpty() && m.values().stream().mapToInt(Integer::intValue).sum() > 0;
            if (!ok) {
                if (d == 0) continue;
                break;
            }
            s++;
        }
        return s;
    }

    // daily task done ratio (0..1) for a ny date key "D:yyyy-mm-dd", for the calendar heatmap
    public static double dayRatio(String dayKey) {
        Map<String, Integer> m = Store.I.progress.get(dayKey);
        if (m == null || m.isEmpty()) return 0;
        int done = 0, total = 0;
        for (TaskDef t : Catalog.TASKS) {
            if (t.period() != Period.DAILY || !t.core()) continue;
            if (!t.island().isEmpty() && !Store.I.visited.contains(t.island())) continue;
            total++;
            if (m.getOrDefault(t.id(), 0) >= t.target()) done++;
        }
        return total == 0 ? 0 : (double) done / total;
    }

    // --- chat detection ---

    public static void learn(String taskId) {
        learning = taskId;
        learningUntil = System.currentTimeMillis() + 5 * 60_000;
    }

    public static String learning() {
        return learning != null && System.currentTimeMillis() < learningUntil ? learning : null;
    }

    public static void onChat(String plain) {
        if (plain.isBlank()) return;
        long now = System.currentTimeMillis();
        String l = learning();
        if (l != null && !plain.contains(":") || l != null && plain.startsWith("[NPC]")) {
            // teach: the next system message after /sbc learn becomes this tasks trigger
            Store.I.learned.put(l, Pattern.quote(plain.trim()));
            compiled.remove(l);
            learning = null;
            TaskDef t = find(l);
            if (t != null) add(t, 1);
            Toasts.push("Learned", "Will detect: " + (plain.length() > 40 ? plain.substring(0, 40) + "..." : plain), 0xFF39D0FF);
            Store.markDirty();
            return;
        }
        for (TaskDef t : allTasks()) {
            if (!unlocked(t, now)) continue;
            if (progress(t, now) >= t.target()) continue;
            for (Pattern p : patterns(t)) {
                if (p.matcher(plain).find()) {
                    // mods often print a 2nd compact copy of the same message, count one per 1.5 sec
                    Long last = lastHit.get(t.id());
                    if (last != null && now - last < 1500) break;
                    lastHit.put(t.id(), now);
                    add(t, 1);
                    break;
                }
            }
        }
    }

    private static final Map<String, Long> lastHit = new java.util.HashMap<>();

    private static List<Pattern> patterns(TaskDef t) {
        return compiled.computeIfAbsent(t.id(), k -> {
            List<Pattern> ps = new ArrayList<>();
            for (String key : t.repoKeys()) {
                Pattern p = Repo.pattern(key);
                if (p != null) ps.add(p);
            }
            for (String s : t.patterns()) {
                try {
                    ps.add(Pattern.compile(s));
                } catch (Exception ignored) {
                }
            }
            String learned = Store.I.learned.get(t.id());
            if (learned != null) ps.add(Pattern.compile(learned));
            return ps;
        });
    }

    public static TaskDef find(String id) {
        for (TaskDef t : allTasks()) if (t.id().equals(id)) return t;
        return null;
    }

    // --- alerts ---

    public static void tickAlerts() {
        long now = System.currentTimeMillis();
        if (!Config.I.alerts || !GameState.inSkyBlock || now - lastAlertCheck < 15_000) return;
        lastAlertCheck = now;
        long untilDaily = SbTime.untilReset(Period.DAILY, now);
        String dayKey = SbTime.periodKey(Period.DAILY, now);
        if (untilDaily < Config.I.alertMinutesBeforeReset * 60_000L && alerted.add("reset|" + dayKey)) {
            long left = today().stream().filter(e -> !e.complete() && e.def().core() && e.def().period() == Period.DAILY).count();
            if (left > 0) Toasts.push("Reset in " + SbTime.dur(untilDaily), left + " daily task" + (left == 1 ? "" : "s") + " still open", 0xFFFF6B6B);
        }
        for (SbTime.Event e : SbTime.events(now, 6 * 60_000L)) {
            long in = e.start() - now;
            if (in > 0 && in < 5 * 60_000L && alerted.add("ev|" + e.id() + "|" + e.start()))
                Toasts.push(e.name() + " in " + SbTime.dur(in), e.hint(), e.color());
        }
        for (Map.Entry<String, Long> t : Store.I.timers.entrySet()) {
            if (t.getValue() <= now && alerted.add("timer|" + t.getKey() + "|" + t.getValue()))
                Toasts.push("Ready", timerName(t.getKey()), 0xFF4CE08A);
        }
    }

    public static String timerName(String id) {
        return switch (id) {
            case "forge" -> "A Forge item is ready to claim";
            case "cookie" -> "Booster Cookie is about to expire";
            default -> id;
        };
    }
}
