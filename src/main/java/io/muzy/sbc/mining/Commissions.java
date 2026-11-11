// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mining;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// commissions tab widget the way skyhanni reads it ("Commissions:" then " <name>: <progress>", open while the
// line has no DONE)
// plus the "Commission Complete!" chat line
// plus the commissions menu like skyblocker CommissionHighlight (lore has COMPLETED = done) and skyhanni
// TunnelsMaps (collector type from lore)
// tab list gives progress as a percent or DONE (skyblocker COMM_PATTERN). if a tab or lore line has an "x/y"
// instead, keep it and show it as is
public final class Commissions {
    static final Patterns.P COMPLETE = Patterns.p("SH.mining.commissions.complete");
    // collector commissions: "<what> Collector" (skyhanni collectorCommissionPattern, colorless)
    static final Patterns.P COLLECTOR = Patterns.p("SH.mining.commisson.collector.colorless");
    // from skyblocker CommsWidget.COMM_PATTERN
    static final Pattern COMM = Pattern.compile("(?<name>.*): (?<progress>.*)%?");
    // "x/y" progress wherever hypixel writes one
    static final Pattern FRACTION = Pattern.compile("(?<have>\\d[\\d,]*)\\s*/\\s*(?<need>\\d[\\d,]*)");

    // progress = what the tab says ("45%", "DONE", "12/50"). detail = the "x/y" from the menu lore, or null
    public record Commission(String name, String progress, boolean done, String detail) {
        public Commission(String name, String progress, boolean done) {
            this(name, progress, done, null);
        }

        // "12/50" when hypixel gives numbers, else the percent
        public String shown() {
            if (done) return "DONE";
            if (FRACTION.matcher(progress).find()) return progress;
            return detail != null ? detail + " §7(" + progress + ")" : progress;
        }
    }

    private static List<Commission> current = List.of();
    private static final List<String> recentlyCompleted = new ArrayList<>();
    private static long lastCompleted = MiningEnv.FAR_PAST;
    // lowercase commission name -> "x/y" from the menu lore
    private static final Map<String, String> menuDetails = new HashMap<>();
    private static final List<String> menuCompleted = new ArrayList<>();

    private Commissions() {}

    public static List<Commission> current() {
        return current;
    }

    // skyhanni TabListUpdateEvent, plain tab lines
    public static void onTab(List<String> tab) {
        List<String> lines = MiningEvents.widgetLines(tab, "COMMISSIONS");
        if (lines == null) {
            current = List.of();
            return;
        }
        List<Commission> out = new ArrayList<>();
        for (String raw : lines) {
            String l = raw.trim();
            Matcher m = COMM.matcher(l);
            if (!m.matches()) continue;
            String name = m.group("name").trim();
            String progress = m.group("progress").trim();
            boolean done = progress.contains("DONE");
            // chat said its done a moment ago, tab list lags behind (ignoredTabListCommissions, 5 s)
            if (!done && MiningEnv.passedSince(lastCompleted) < 5000 && recentlyCompleted.stream().anyMatch(name::equalsIgnoreCase)) done = true;
            if (!done && menuCompleted.stream().anyMatch(name::equalsIgnoreCase)) done = true;
            out.add(new Commission(name, progress, done, menuDetails.get(name.toLowerCase(java.util.Locale.ROOT))));
        }
        current = out;
    }

    // commissions menu opened: every item name + clean lore. done when a lore line has COMPLETED (skyblocker), an
    // "x/y" lore line is kept as detail
    // returns how many commissions were in the menu
    public static int onMenu(String title, Map<String, List<String>> itemNameToLore) {
        if (!GameState.strip(title).trim().equals("Commissions")) return 0;
        menuDetails.clear();
        menuCompleted.clear();
        int found = 0;
        for (var e : itemNameToLore.entrySet()) {
            String name = GameState.strip(e.getKey()).trim();
            if (name.isEmpty()) continue;
            boolean completed = false, commission = false;
            String detail = null;
            for (String raw : e.getValue()) {
                String l = GameState.strip(raw).trim();
                if (l.contains("COMPLETED")) completed = true;
                if (COLLECTOR.matcher(l).matches() || l.contains("Commission")) commission = true;
                Matcher f = FRACTION.matcher(l);
                if (detail == null && f.find()) detail = f.group("have") + "/" + f.group("need");
            }
            if (!completed && detail == null && !commission) continue;
            found++;
            String key = name.toLowerCase(java.util.Locale.ROOT);
            if (detail != null) menuDetails.put(key, detail);
            if (completed) menuCompleted.add(name);
        }
        // refresh the list with the new details
        List<Commission> out = new ArrayList<>();
        for (Commission c : current) {
            boolean done = c.done() || menuCompleted.stream().anyMatch(c.name()::equalsIgnoreCase);
            out.add(new Commission(c.name(), c.progress(), done, menuDetails.get(c.name().toLowerCase(java.util.Locale.ROOT))));
        }
        current = out;
        return found;
    }

    // formatted chat line in. returns the done commissions name or null
    public static String onChat(String message) {
        Matcher m = COMPLETE.matcher(message);
        if (!m.matches()) return null;
        String name = m.group("name");
        recentlyCompleted.clear();
        recentlyCompleted.add(name);
        lastCompleted = MiningEnv.now();
        return name;
    }

    // display rows: name -> shown progress, done ones marked
    public static List<String[]> rows() {
        List<String[]> out = new ArrayList<>();
        for (Commission c : current) out.add(new String[]{c.name(), c.done() ? "§aDONE" : "§e" + c.shown()});
        return out;
    }

    public static void reset() {
        current = List.of();
        recentlyCompleted.clear();
        menuDetails.clear();
        menuCompleted.clear();
        lastCompleted = MiningEnv.FAR_PAST;
    }
}
