// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.tasks;

import dev.zmuzy.sbc.core.SbTime.Period;

import java.util.List;

// one recurring thing to do. repoKeys = skyhanni repo pattern names, patterns = plain regexes, both matched
// against chat to tick progress. tasks with neither get ticked by hand
public record TaskDef(
        String id, String name, String category, String icon, Period period, int target, int minutes,
        double coins, double sbXp, boolean core, String island, String guideNode, String hint,
        List<String> repoKeys, List<String> patterns, String event) {

    // "Daily", "Weekly"... shown in brackets after every task name
    public String periodLabel() {
        Integer every = Routine.REPEAT_DAYS.get(id);
        if (every != null) return "Every " + every + " days";
        return switch (period) {
            case DAILY -> "Daily";
            case WEEKLY -> "Weekly";
            case MONTHLY -> "Monthly";
            case SB_DAY -> "SkyBlock day";
            case SB_MONTH -> "SkyBlock month";
            case SB_YEAR -> "Yearly";
            case EVENT -> "Event";
            case ONCE -> "Once";
        };
    }

    public String displayName() {
        return name + " (" + periodLabel() + ")";
    }

    public boolean auto() {
        return !repoKeys.isEmpty() || !patterns.isEmpty();
    }

    static TaskDef daily(String id, String name, String cat, String icon, int target, int minutes, double coins, double xp,
                         String island, String node, String hint, List<String> repo, List<String> pats) {
        return new TaskDef(id, name, cat, icon, Period.DAILY, target, minutes, coins, xp, true, island, node, hint, repo, pats, "");
    }
}
