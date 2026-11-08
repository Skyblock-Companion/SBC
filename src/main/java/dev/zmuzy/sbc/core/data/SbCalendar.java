// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.core.data;

import dev.zmuzy.sbc.core.SbTime;

// when sb calendar events start + how long, from SbTime with nothing downloaded. replaces what skyblocker
// reads from its server in EventNotifications
public final class SbCalendar {
    private SbCalendar() {}

    public record Occurrence(Event event, long start, long end) {
        public long duration() {
            return end - start;
        }

        public boolean active(long now) {
            return now >= start && now < end;
        }
    }

    private static final int[] EVERY_MONTH = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};

    public enum Event {
        CULT_OF_THE_FALLEN_STAR("Cult of the Fallen Star", EVERY_MONTH, new int[]{7, 14, 21, 28}, SbTime.DAY / 4),
        HARVEST_FEAST("Harvest Feast", new int[]{6}, new int[]{1}, 93 * SbTime.DAY),
        HOPPITYS_HUNT("Hoppity's Hunt", new int[]{0}, new int[]{1}, 93 * SbTime.DAY),
        JERRYS_WORKSHOP("Jerry's Workshop", new int[]{11}, new int[]{1}, 31 * SbTime.DAY),
        MAYOR_ELECTION("Mayor Election", new int[]{5}, new int[]{27}, 279 * SbTime.DAY),
        NEW_YEAR_CELEBRATION("New Year Celebration", new int[]{11}, new int[]{29}, 3 * SbTime.DAY),
        SEASON_OF_JERRY("Season of Jerry", new int[]{11}, new int[]{24}, 3 * SbTime.DAY),
        SPOOKY_FESTIVAL("Spooky Festival", new int[]{7}, new int[]{29}, 3 * SbTime.DAY),
        TRAVELING_ZOO("Traveling Zoo", new int[]{3, 9}, new int[]{1}, 3 * SbTime.DAY),
        YEAR_OF_THE_WITCH("Year of the Witch", new int[]{0}, new int[]{1}, SbTime.YEAR, SbTime.WITCH_YEAR, SbTime.WITCH_EVERY);

        public final String title;
        private final int[] months, days;
        private final long duration;
        // every n years from firstYear
        private final int firstYear, everyYears;

        Event(String title, int[] months, int[] days, long duration) {
            this(title, months, days, duration, 1, 1);
        }

        Event(String title, int[] months, int[] days, long duration, int firstYear, int everyYears) {
            this.title = title;
            this.months = months;
            this.days = days;
            this.duration = duration;
            this.firstYear = firstYear;
            this.everyYears = everyYears;
        }

        boolean inYear(int year) {
            return Math.floorMod(year - firstYear, everyYears) == 0;
        }

        // theres a rule for when it comes
        public boolean computable() {
            return months.length > 0;
        }

        public long duration() {
            return duration;
        }
    }

    // first start at or after from, null = no rule
    public static Occurrence next(Event e, long from) {
        if (!e.computable()) return null;
        for (int year = Math.max(1, SbTime.sbDate(from).year()); ; year++) {
            if (!e.inYear(year)) continue;
            for (int month : e.months) {
                for (int day : e.days) {
                    long start = SbTime.sbToReal(year, month, day);
                    if (start >= from) return new Occurrence(e, start, start + e.duration);
                }
            }
        }
    }

    // the one going on now (can be from last year), null = none or no rule
    public static Occurrence current(Event e, long now) {
        if (!e.computable()) return null;
        for (int year = SbTime.sbDate(now).year(); year >= Math.max(1, SbTime.sbDate(now).year() - 1); year--) {
            if (!e.inYear(year)) continue;
            for (int month : e.months) {
                for (int day : e.days) {
                    long start = SbTime.sbToReal(year, month, day);
                    if (now >= start && now < start + e.duration) return new Occurrence(e, start, start + e.duration);
                }
            }
        }
        return null;
    }
}
