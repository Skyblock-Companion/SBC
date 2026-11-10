// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.skills;

import io.muzy.sbc.core.Profile;
import io.muzy.sbc.core.game.Seen;

// from skyhanni skillprogress/SkillUtil.kt + SkillLevel.kt: what a level costs + which level some xp is, past 60 too
public final class SkillLevels {
    // from skyhanni SkillLevel
    public record Level(int level, long current, long forNext, long overflow) {}

    private SkillLevels() {}

    private static long cost(int level) {
        return level >= 1 && level < Profile.SKILL_XP.length ? Profile.SKILL_XP[level] - Profile.SKILL_XP[level - 1] : 0;
    }

    // from skyhanni SkillUtil.xpRequiredForLevel
    public static long xpRequiredForLevel(int desired) {
        if (desired <= 60) return Profile.SKILL_XP[Math.max(0, desired)];
        long total = Profile.SKILL_XP[60], forNext = 7_000_000L + 600_000L, slope = 600_000L;
        for (int level = 60; level < desired; ) {
            total += forNext;
            level++;
            forNext += slope;
            if (level % 10 == 0) slope *= 2;
        }
        return total;
    }

    // from skyhanni calculateSkillLevel: level isnt capped, only the overflow counts from the cap
    public static Level calculate(long xp, int cap) {
        long current = xp;
        int level = 0;
        while (level < 60) {
            long forNext = cost(level + 1);
            if (current < forNext) break;
            current -= forNext;
            level++;
        }
        long forNext = cost(level + 1);
        if (level >= 60) {
            long slope = 600_000L;
            forNext = 7_000_000L + slope;
            while (current >= forNext) {
                level++;
                current -= forNext;
                forNext += slope;
                if (level % 10 == 0) slope *= 2;
            }
        }
        return new Level(level, current, forNext, Math.max(0, xp - xpRequiredForLevel(Math.min(cap, 60))));
    }

    // from skyhanni SkillInfo.totalXp as far as known, 0 = only a level was read
    public static long total(Seen.Skill s) {
        return s == null ? 0 : Math.max(s.totalXp, s.level > 0 ? xpRequiredForLevel(s.level) : 0);
    }

    // from skyhanni SkillInfo.overflowLevel: level from all the xp, or the read level when the xp says less
    public static int overflowLevel(Seen.Skill s, int cap) {
        if (s == null) return -1;
        return Math.max(s.level, calculate(total(s), cap).level());
    }

    // "Your goal" like skyhannis customGoalProgress reads it: all xp so far out of all the xp that level takes
    public static double goalPercent(long have, int goal) {
        long need = xpRequiredForLevel(goal);
        return need <= 0 ? 100 : Math.min(100, 100.0 * have / need);
    }
}
