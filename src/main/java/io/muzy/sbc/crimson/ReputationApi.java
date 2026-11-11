// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.crimson;

import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.TabWidgets;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyhanni CrimsonIsleReputationApi: faction + reputation from the REPUTATION widget
public final class ReputationApi {
    // FactionType
    public enum FactionType {
        BARBARIAN("Barbarian", "barbarians"), MAGE("Mage", "mages");

        public final String factionName, apiName;

        FactionType(String factionName, String apiName) {
            this.factionName = factionName;
            this.apiName = apiName;
        }

        public String undercoverAgentNode() {
            return "Undercover Agent (" + factionName + ")";
        }

        public String questBoardNode() {
            return "Quest Board (" + factionName + ")";
        }

        public static FactionType fromName(String name) {
            for (FactionType f : values()) if (f.factionName.equalsIgnoreCase(name)) return f;
            return null;
        }
    }

    // TabWidget.REPUTATION first line
    private static final Pattern WIDGET = Pattern.compile("(?<faction>Barbarian|Mage) Reputation:");
    static final Patterns.P REPUTATION_COUNT = Patterns.p("SH.crimson.reputationapi.tablistreputation");

    private ReputationApi() {}

    public static FactionType factionType() {
        String f = CrimsonEnv.profile().faction;
        if (f == null) return null;
        try {
            return FactionType.valueOf(f);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void setFactionType(FactionType f) {
        CrimsonEnv.profile().faction = f == null ? null : f.name();
    }

    // onWidgetUpdateEvent for REPUTATION, the widget's plain lines
    public static void onReputationWidget(List<String> lines) {
        if (lines.isEmpty()) return;
        Matcher m = WIDGET.matcher(lines.get(0).trim());
        if (m.find()) setFactionType(FactionType.fromName(m.group("faction")));
        FactionType current = factionType();
        if (current == null) return;
        for (String l : lines) {
            Matcher r = REPUTATION_COUNT.matcher(l);
            if (r.matches()) {
                CrimsonEnv.profile().reputation.put(current.name(), Integer.parseInt(r.group("rep").replace(",", "")));
                return;
            }
        }
    }

    // reads REPUTATION out of the plain tab list
    public static void onTab(List<String> tab) {
        for (TabWidgets.Widget w : TabWidgets.parse(tab)) {
            if (!w.def().id().equals("REPUTATION")) continue;
            List<String> lines = new java.util.ArrayList<>();
            lines.add(tab.get(w.first()));
            for (int i : w.lines()) lines.add(tab.get(i));
            onReputationWidget(lines);
            return;
        }
    }
}
