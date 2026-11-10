// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events.hoppity;

import io.github.zmuzy.sbc.core.SbTime;

// skyblock calendar helpers from skyhanni HoppityApi / SkyBlockTime / SkyblockSeason
// the hunt runs through spring (early spring, spring, late spring) and ends at the start of early summer.
// eggs alternate between 2 sets on odd and even days
public final class HoppityTime {
    private HoppityTime() {}

    // SkyBlockTime.now() as our SbDate (month 0-based here, 0 = early spring)
    public static SbTime.SbDate now() {
        return SbTime.sbDate(HoppityEnv.now());
    }

    // SkyblockSeason.SPRING.isSeason()
    public static boolean isHoppityEvent() {
        return now().month() <= 2;
    }

    // first event was year 346 -> #1, 20th was year 365 etc
    public static int eventNumber(int year) {
        return year - 345;
    }

    // HoppityApi.getEventEndMark(year): start of early summer (month 3) of that year
    public static long eventEnd(int year) {
        return SbTime.sbToReal(year, 3, 1);
    }

    public static long eventStart(int year) {
        return SbTime.sbToReal(year, 0, 1);
    }

    // HoppityApi.getEventEndMark(): null outside the event
    public static Long eventEndMark() {
        return isHoppityEvent() ? eventEnd(now().year()) : null;
    }

    // SkyBlockTime.isAlternateDay: spring 1st normal, spring 2nd alt and so on. every month has 31 days so it
    // flips each month
    // skyhanni uses 1-based months ((month % 2 == 1) == (day % 2 == 0))
    public static boolean isAlternateDay(SbTime.SbDate d) {
        if (!isHoppityEvent()) return false;
        int month1 = d.month() + 1;
        return (month1 % 2 == 1) == (d.day() % 2 == 0);
    }

    public static boolean isAlternateDay() {
        return isAlternateDay(now());
    }

    // real time of (year, month0, day, hour), SkyBlockTime.copy(...).toTimeMark(). days can overflow into the next month
    public static long toReal(int year, int month0, int day, int hour) {
        return SbTime.sbToReal(year, month0, 1) + (day - 1) * SbTime.DAY + hour * (SbTime.DAY / 24);
    }

    public static long dayMillis() {
        return SbTime.DAY;
    }
}
