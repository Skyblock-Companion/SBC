// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.hoppity;

import io.muzy.sbc.core.SbTime;

import java.util.ArrayList;
import java.util.List;

// from skyhanni HoppityEggType: meal eggs + the skyblock hour they respawn (7, 14, 21) on normal or alt days,
// plus the other ways to get a rabbit
// claimed state and spawn times kept per profile (mealLastFound / mealNextSpawn)
public enum HoppityEggType {
    BREAKFAST("Breakfast", "§6", 7, false),
    LUNCH("Lunch", "§9", 14, false),
    DINNER("Dinner", "§a", 21, false),
    BRUNCH("Brunch", "§6", 7, true),
    DEJEUNER("Déjeuner", "§9", 14, true),
    SUPPER("Supper", "§a", 21, true),
    SIDE_DISH("Side Dish", "§6§l", -1, false),
    HITMAN("Hitman", "§c", -1, false),
    BOUGHT("Bought", "§a", -1, false),
    BOUGHT_ABIPHONE("✆ Bought", "§a", -1, false),
    CHOCOLATE_SHOP_MILESTONE("Shop Milestone", "§6§l", -1, false),
    CHOCOLATE_FACTORY_MILESTONE("Chocolate Milestone", "§6§l", -1, false),
    STRAY("Stray", "§a", -1, false),
    VISITOR("Visitor", "§d", -1, false);

    public final String mealName, mealColor;
    public final int resetsAt;
    public final boolean altDay;
    private boolean claimed;
    // skyblock date (year, month0, day) of the last reset we saw, or null
    private int[] lastReset;
    private Long nextSpawnCache;

    HoppityEggType(String mealName, String mealColor, int resetsAt, boolean altDay) {
        this.mealName = mealName;
        this.mealColor = mealColor;
        this.resetsAt = resetsAt;
        this.altDay = altDay;
    }

    public static final List<HoppityEggType> RESETTING = resetting();

    private static List<HoppityEggType> resetting() {
        List<HoppityEggType> out = new ArrayList<>();
        for (HoppityEggType t : values()) if (t.resetsAt != -1) out.add(t);
        out.sort(java.util.Comparator.comparingInt(t -> t.resetsAt));
        return List.copyOf(out);
    }

    public boolean isResetting() {
        return resetsAt != -1;
    }

    public String formattedName() {
        return (isClaimed() ? "§7§m" : mealColor) + mealName + ":" + mealColor;
    }

    public String coloredName() {
        return mealColor + mealName;
    }

    // nextSpawn.timeUntil() in ms, negative if overdue
    public long timeUntil() {
        return nextSpawn() - HoppityEnv.now();
    }

    private long nextSpawn() {
        Long c = nextSpawnCache;
        if (c != null && !HoppityEnv.isInPast(c)) return c;
        return calculateNextSpawn();
    }

    private boolean spawnsToday() {
        return altDay == HoppityTime.isAlternateDay();
    }

    public boolean spawnedToday() {
        SbTime.SbDate d = HoppityTime.now();
        return altDay == HoppityTime.isAlternateDay(d) && d.hour() >= resetsAt;
    }

    public boolean alreadyResetToday() {
        SbTime.SbDate d = HoppityTime.now();
        return lastReset != null && lastReset[0] == d.year() && lastReset[1] == d.month() && lastReset[2] == d.day();
    }

    private long calculateNextSpawn() {
        if (resetsAt == -1) return HoppityEnv.FAR_FUTURE;
        SbTime.SbDate d = HoppityTime.now();
        boolean isEggDayToday = spawnsToday();
        int daysToAdd = isEggDayToday && d.hour() < resetsAt ? 0 : isEggDayToday ? 2 : 1;
        long mark = HoppityTime.toReal(d.year(), d.month(), d.day() + daysToAdd, resetsAt);
        nextSpawnCache = mark;
        HoppityEnv.profile().mealNextSpawn.put(name(), mark);
        return mark;
    }

    public void markClaimed() {
        markClaimed(HoppityEnv.now());
    }

    public void markClaimed(long mark) {
        claimed = true;
        HoppityEnv.profile().mealLastFound.put(name(), mark);
    }

    public void markSpawned(boolean setLastReset) {
        claimed = false;
        if (setLastReset) {
            SbTime.SbDate d = HoppityTime.now();
            lastReset = new int[]{d.year(), d.month(), d.day()};
        }
    }

    public boolean isClaimed() {
        return claimed || hasNotFirstSpawnedYet();
    }

    boolean rawClaimed() {
        return claimed;
    }

    // more spawns before the event ends?
    public boolean hasRemainingSpawns() {
        Long end = HoppityTime.eventEndMark();
        if (end == null) return false;
        long untilEnd = end - HoppityEnv.now();
        // before the last 2 days of the event there are more spawns for sure
        if (untilEnd > HoppityTime.dayMillis() * 2) return true;
        // else check if the next spawn is after the event ends
        return timeUntil() < untilEnd;
    }

    // before the event's first spawn (spring 1st before resetsAt, alt eggs before spring 2nd). skyhanni's 1-based
    // month and day
    public boolean hasNotFirstSpawnedYet() {
        SbTime.SbDate d = HoppityTime.now();
        int month1 = d.month() + 1;
        if (month1 > 1 || (altDay && d.day() > 2) || (!altDay && d.day() > 1)) return false;
        return (altDay && d.day() < 2) || d.hour() < resetsAt;
    }

    public static void markAllFound() {
        for (HoppityEggType t : RESETTING) t.markClaimed();
    }

    public static boolean anyEggsUnclaimed() {
        for (HoppityEggType t : RESETTING) if (!t.claimed) return true;
        return false;
    }

    public static boolean allEggsUnclaimed() {
        for (HoppityEggType t : RESETTING) if (t.isClaimed()) return false;
        return true;
    }

    // Matcher.getEggType by the "meal" group (hypixel also writes "Déjeune" without the r lol)
    public static HoppityEggType byMeal(String meal) {
        if (meal == null) return null;
        for (HoppityEggType t : values()) if (t.mealName.equals(meal)) return t;
        if (meal.startsWith("Déjeune")) return DEJEUNER;
        return null;
    }

    // forget the in-memory claimed/reset state (tests, profile switch)
    public static void resetAll() {
        for (HoppityEggType t : values()) {
            t.claimed = false;
            t.lastReset = null;
            t.nextSpawnCache = null;
        }
    }
}
