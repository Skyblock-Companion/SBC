// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mining;

import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.TabWidgets;

import java.util.List;
import java.util.regex.Matcher;

// from skyhanni MiningEventsApi: the running dwarven / hollows event from chat ("X STARTED!" / "X ENDED!")
// or the EVENT tab widget ("Mining Event: X" + "Ends in: ...") or the boss bar. skipped the soopy event sharing api
public final class MiningEvents {
    public enum Type {
        GOBLIN_RAID("Goblin Raid", 5 * 60_000L, "goblin.raid", "§c"),
        RAFFLE("Raffle", 3 * 60_000L, "raffle", "§6"),
        MITHRIL_GOURMAND("Mithril Gourmand", 10 * 60_000L, "mithril.gourmand", "§b"),
        BETTER_TOGETHER("Better Together", 20 * 60_000L, "better.together", "§d"),
        GONE_WITH_THE_WIND("Gone with the Wind", 20 * 60_000L, "gone.with.the.wind", "§9"),
        DOUBLE_POWDER("2x Powder", 15 * 60_000L, "double.powder", "§b");

        public final String displayName, color;
        public final long duration;
        final Patterns.P chat, widget, bossbar;

        Type(String displayName, long duration, String id, String color) {
            this.displayName = displayName;
            this.duration = duration;
            this.color = color;
            this.chat = Patterns.p("SH.mining.mining.event.chat." + id);
            this.widget = Patterns.p("SH.mining.mining.event.widget.event." + id + "-no-color");
            this.bossbar = Patterns.p("SH.mining.mining.event.bossbar." + id);
        }
    }

    static final Patterns.P WIDGET_DURATION = Patterns.p("SH.mining.mining.event.widget.duration-no-color");
    static final Patterns.P WIDGET_NOT_ANNOUNCED = Patterns.p("SH.mining.mining.event.widget.not.announced-no-color");

    // skyhanni MiningEvent(type, timeLeft) + when it was set so the time left counts down
    public static final class Active {
        public final Type type;
        private long timeLeft, setAt;

        Active(Type type, long timeLeft) {
            this.type = type;
            set(timeLeft);
        }

        void set(long timeLeft) {
            this.timeLeft = timeLeft;
            this.setAt = MiningEnv.now();
        }

        public long timeLeft() {
            return timeLeft - MiningEnv.passedSince(setAt);
        }
    }

    private static Active active;

    private MiningEvents() {}

    public static Active getActiveEvent(Type type) {
        Active e = active;
        if (e == null) return null;
        return type == null || e.type == type ? e : null;
    }

    public static boolean isActive(Type type) {
        return getActiveEvent(type) != null;
    }

    private static void setActive(Type type, long timeLeft) {
        Active current = active;
        if (current == null) {
            active = new Active(type, timeLeft);
            return;
        }
        // edge case, only if you swap to a server with a different event running
        if (current.type != type) {
            active = new Active(type, timeLeft);
            return;
        }
        current.set(timeLeft);
    }

    private static void clear() {
        active = null;
    }

    private static boolean onEventIsland() {
        String i = MiningEnv.world.island();
        return i.equals("CRYSTAL_HOLLOWS") || i.equals("DWARVEN_MINES");
    }

    // skyhanni MiningEventsApi.onChat, formatted line
    public static void onChat(String message) {
        if (!onEventIsland()) return;
        for (Type t : Type.values()) {
            if (!t.chat.matcher(message).matches()) continue;
            if (message.contains("STARTED")) setActive(t, t.duration);
            else if (message.contains("ENDED")) clear();
            return;
        }
    }

    // skyhanni MiningEventsApi.onSecondPassed: the EVENT widget if its there, else the boss bar
    public static void onSecondPassed(List<String> tab, String bossbar) {
        if (!onEventIsland()) return;
        List<String> eventLines = widgetLines(tab, "EVENT");
        if (eventLines != null) {
            for (Type t : Type.values()) {
                for (int i = 0; i < eventLines.size(); i++) {
                    String line = eventLines.get(i).trim();
                    if (WIDGET_NOT_ANNOUNCED.matcher(line).matches()) {
                        clear();
                        return;
                    }
                    if (!t.widget.matcher(line).matches()) continue;
                    if (i + 1 >= eventLines.size()) return;
                    Matcher d = WIDGET_DURATION.matcher(eventLines.get(i + 1).trim());
                    if (d.matches()) {
                        Long ms = MiningEnv.duration(d.group("duration"));
                        if (ms != null) setActive(t, ms);
                    }
                    return;
                }
            }
            return;
        }
        if (bossbar == null || bossbar.isEmpty()) return;
        for (Type t : Type.values()) {
            Matcher m = t.bossbar.matcher(bossbar);
            if (!m.matches()) continue;
            Long ms = MiningEnv.duration(m.group("time"));
            if (ms != null && ms > 0) setActive(t, ms);
            return;
        }
    }

    // lines of a tab widget (plain, no title), null if the widget isnt shown
    static List<String> widgetLines(List<String> tab, String id) {
        for (TabWidgets.Widget w : TabWidgets.parse(tab)) {
            if (!w.def().id().equals(id)) continue;
            List<String> out = new java.util.ArrayList<>();
            for (int i : w.lines()) out.add(tab.get(i));
            return out;
        }
        return null;
    }

    // "§dBetter Together §e12m 3s" or null
    public static String displayLine() {
        Active e = active;
        if (e == null) return null;
        long left = e.timeLeft();
        return e.type.color + e.type.displayName + (left > 0 ? " §e" + MiningEnv.format(left) : " §7ended?");
    }

    public static void reset() {
        active = null;
    }
}
