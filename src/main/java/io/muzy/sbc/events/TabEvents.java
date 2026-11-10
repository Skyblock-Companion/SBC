// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.SbTime;
import io.muzy.sbc.core.TabWidgets;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

// for the scoreboard's Events: what skyhanni ScoreboardEventActiveTablist, ScoreboardEventStartingSoonTablist
// and ScoreboardEventBroodmother take from the tab
// the rest of its events are sidebar lines the scoreboard already shows by pattern
public final class TabEvents {
    // sidebar says these better
    private static final Set<String> BLOCKED = Set.of("Spooky Festival", "Carnival", "th SkyBlock Anniversary", "New Year Celebration");

    private TabEvents() {}

    public static List<String> lines() {
        return lines(GameState.tab);
    }

    // from the tab's plain lines
    static List<String> lines(List<String> plain) {
        List<String> out = new ArrayList<>();
        for (TabWidgets.Widget w : TabWidgets.parse(plain)) {
            if ("EVENT".equals(w.def().id())) event(w, plain, out);
            else if ("BROODMOTHER".equals(w.def().id()) && "SPIDER_DEN".equals(GameState.island)) for (int i : w.lines()) out.add(plain.get(i).trim());
        }
        return out;
    }

    // tab only shows 1 event, the other long ones from the calendar
    static final Set<String> FROM_CALENDAR = Set.of("witch", "harvest_feast", "hoppity", "zoo_summer", "zoo_winter");

    public static List<String> fromCalendar(List<String> shown, long now) {
        List<String> out = new ArrayList<>();
        for (SbTime.Event e : SbTime.events(now, 0)) {
            if (!FROM_CALENDAR.contains(e.id()) || !e.active(now)) continue;
            if (shown.stream().anyMatch(l -> GameState.strip(l).contains(e.name()))) continue;
            out.add("§d" + e.name());
            out.add(" Ends in: §e" + SbTime.dur(e.end() - now));
        }
        return out;
    }

    private static void event(TabWidgets.Widget w, List<String> plain, List<String> out) {
        Matcher first = w.def().pattern().matcher(plain.get(w.first()).trim());
        if (!first.matches()) return;
        String name = first.group("event").trim();
        for (int i : w.lines()) {
            Matcher ends = Patterns.get("SH.sb.tablistGroup.eventTimeEnds").matcher(plain.get(i));
            if (ends.matches()) {
                if (BLOCKED.stream().anyMatch(name::contains)) return;
                out.add("§d" + name);
                out.add(" Ends in: §e" + ends.group("time"));
                return;
            }
            Matcher starts = Patterns.get("SH.sb.tablistGroup.eventTimeStarts").matcher(plain.get(i));
            if (starts.matches()) {
                out.add("§6" + name);
                out.add(" Starts in: §e" + starts.group("time"));
                return;
            }
        }
    }
}
