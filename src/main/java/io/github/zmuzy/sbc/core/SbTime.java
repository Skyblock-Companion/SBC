// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.core;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// real resets (hypixel dailies reset at midnight new york) + the sb calendar: 1 sb day = 20 real min, a month
// = 31 days, a year = 12 months (~124 real hours)
public final class SbTime {
    public static final ZoneId NY = ZoneId.of("America/New_York");
    public static final long EPOCH = 1560275700000L;
    public static final long DAY = 20 * 60_000L;
    public static final long MONTH = DAY * 31;
    public static final long YEAR = MONTH * 12;
    public static final String[] MONTHS = {"Early Spring", "Spring", "Late Spring", "Early Summer", "Summer", "Late Summer",
            "Early Autumn", "Autumn", "Late Autumn", "Early Winter", "Winter", "Late Winter"};

    private SbTime() {}

    public enum Period { DAILY, WEEKLY, MONTHLY, SB_DAY, SB_MONTH, SB_YEAR, EVENT, ONCE }

    // --- real resets ---

    public static ZonedDateTime nowNy() {
        return ZonedDateTime.now(NY);
    }

    public static String periodKey(Period p, long now) {
        ZonedDateTime t = Instant.ofEpochMilli(now).atZone(NY);
        return switch (p) {
            case DAILY -> "D:" + t.toLocalDate();
            case WEEKLY -> {
                LocalDate start = weekStart(t.toLocalDate());
                yield "W:" + start;
            }
            case MONTHLY -> "M:" + t.getYear() + "-" + t.getMonthValue();
            case SB_DAY -> "S:" + (now - EPOCH) / DAY;
            case SB_MONTH -> "SM:" + (now - EPOCH) / MONTH;
            case SB_YEAR, EVENT -> "SY:" + (now - EPOCH) / YEAR;
            case ONCE -> "ONCE";
        };
    }

    private static LocalDate weekStart(LocalDate d) {
        DayOfWeek reset = DayOfWeek.of(Math.max(1, Math.min(7, Config.I.weeklyResetDay)));
        return d.with(TemporalAdjusters.previousOrSame(reset));
    }

    // ms till the period ends (next reset)
    public static long untilReset(Period p, long now) {
        ZonedDateTime t = Instant.ofEpochMilli(now).atZone(NY);
        ZonedDateTime next = switch (p) {
            case DAILY -> t.toLocalDate().plusDays(1).atStartOfDay(NY);
            case WEEKLY -> weekStart(t.toLocalDate()).plusWeeks(1).atStartOfDay(NY);
            case MONTHLY -> t.toLocalDate().withDayOfMonth(1).plusMonths(1).atStartOfDay(NY);
            case SB_DAY -> Instant.ofEpochMilli(EPOCH + ((now - EPOCH) / DAY + 1) * DAY).atZone(NY);
            case SB_MONTH -> Instant.ofEpochMilli(EPOCH + ((now - EPOCH) / MONTH + 1) * MONTH).atZone(NY);
            case SB_YEAR, EVENT -> Instant.ofEpochMilli(EPOCH + ((now - EPOCH) / YEAR + 1) * YEAR).atZone(NY);
            case ONCE -> t.plusYears(100);
        };
        return Math.max(0, next.toInstant().toEpochMilli() - now);
    }

    public static int isoWeek(long now) {
        return Instant.ofEpochMilli(now).atZone(NY).get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
    }

    // --- sb calendar ---

    public record SbDate(int year, int month, int day, int hour, int minute) {
        public String monthName() { return MONTHS[month]; }

        public String pretty() {
            return monthName() + " " + day + suffix(day) + ", Year " + year;
        }

        public String clock() {
            int h12 = hour % 12 == 0 ? 12 : hour % 12;
            return String.format("%d:%02d%s", h12, minute, hour < 12 ? "am" : "pm");
        }
    }

    public static SbDate sbDate(long now) {
        long t = now - EPOCH;
        int year = (int) (t / YEAR) + 1;
        long inYear = t % YEAR;
        int month = (int) (inYear / MONTH);
        long inMonth = inYear % MONTH;
        int day = (int) (inMonth / DAY) + 1;
        long inDay = inMonth % DAY;
        int hour = (int) (inDay * 24 / DAY);
        int minute = (int) ((inDay * 24 * 60 / DAY) % 60);
        return new SbDate(year, month, day, hour, minute);
    }

    // real time when sb (year, month 0-11, day 1-31) starts
    public static long sbToReal(int year, int month, int day) {
        return EPOCH + (year - 1) * YEAR + month * MONTH + (day - 1) * DAY;
    }

    public static String suffix(int d) {
        if (d >= 11 && d <= 13) return "th";
        return switch (d % 10) { case 1 -> "st"; case 2 -> "nd"; case 3 -> "rd"; default -> "th"; };
    }

    // --- events ---

    public record Event(String id, String name, long start, long end, int color, String island, String hint) {
        public boolean active(long now) { return now >= start && now < end; }
    }

    private static final java.util.Map<Long, Object[]> eventCache = new java.util.concurrent.ConcurrentHashMap<>();

    // upcoming + active events in the next horizonMs, soonest first (cached a sec per horizon)
    @SuppressWarnings("unchecked")
    public static List<Event> events(long now, long horizonMs) {
        Object[] c = eventCache.get(horizonMs);
        if (c != null && Math.abs(now - (long) c[0]) < 1000) return (List<Event>) c[1];
        List<Event> out = compute(now, horizonMs);
        eventCache.put(horizonMs, new Object[]{now, out});
        return out;
    }

    private static List<Event> compute(long now, long horizonMs) {
        List<Event> out = new ArrayList<>();
        // hourly events lined up with sb days (days start at :55, :15, :35 real time)
        long dayIdx = (now - EPOCH) / DAY;
        for (long d = dayIdx - 3; d < dayIdx + horizonMs / DAY + 3; d++) {
            long s = EPOCH + d * DAY;
            if (Math.floorMod(d, 3) == 0)
                out.add(new Event("dark_auction", "Dark Auction", s, s + 5 * 60_000L, 0xFF8E44FF, "HUB", "Sirius' auction in the Hub sewers"));
            if (Math.floorMod(d, 3) == 1)
                out.add(new Event("jacob", "Jacob's Farming Contest", s, s + DAY, 0xFFF4C542, "GARDEN", "Farm the contest crops for medals"));
        }
        // yearly events
        int year = sbDate(now).year();
        for (int y = year - 1; y <= year + 1; y++) {
            yearly(out, y, "spooky", "Spooky Festival", 7, 29, 3, 0xFFFF8A1F, "HUB", "Kill Trick or Treat mobs for Green Candy");
            yearly(out, y, "jerry", "Season of Jerry", 11, 24, 3, 0xFF7FD3FF, "WINTER", "Jerry's Workshop: defend and collect gifts");
            yearly(out, y, "new_year", "New Year Celebration", 11, 29, 3, 0xFFFFE066, "HUB", "Collect your free New Year Cake");
            yearly(out, y, "zoo_summer", "Traveling Zoo", 3, 1, 3, 0xFF66E07A, "HUB", "Buy legendary pets from Oringo");
            yearly(out, y, "zoo_winter", "Traveling Zoo", 9, 1, 3, 0xFF66E07A, "HUB", "Buy legendary pets from Oringo");
            yearly(out, y, "hoppity", "Hoppity's Hunt", 0, 1, 93, 0xFFFF9ECF, "", "Find 3 chocolate eggs every SkyBlock day");
            yearly(out, y, "harvest_feast", "Harvest Feast", 6, 1, 93, 0xFFE8A33D, "", "Crops take their turn in season at the Feast");
            yearly(out, y, "jerrys_workshop", "Jerry's Workshop", 11, 1, 31, 0xFF9AD8FF, "WINTER", "The island is open: /warp jerry");
            yearly(out, y, "election", "Election open", 5, 27, 31 * 9, 0xFF5CC8FF, "HUB", "Vote at the Election Booth");
            if (witchYear(y)) yearly(out, y, "witch", "Year of the Witch", 0, 1, 12 * 31, 0xFFA66BFF, "", WITCH_HINT);
        }
        out.removeIf(e -> e.end() < now || e.start() > now + horizonMs);
        out.sort(Comparator.comparingLong(Event::start));
        // long events (election booth) can overlap next years, keep only the current/next one per event
        java.util.Set<String> seen = new java.util.HashSet<>();
        out.removeIf(e -> !e.id().equals("dark_auction") && !e.id().equals("jacob") && !seen.add(e.id().replace("_summer", "").replace("_winter", "")));
        return out;
    }

    // every multi hour event (no hourly, no dedupe) overlapping from..to, for calendar views
    public static List<Event> longEvents(long from, long to) {
        List<Event> out = new ArrayList<>();
        int y0 = sbDate(from).year(), y1 = sbDate(to).year();
        for (int y = y0 - 1; y <= y1; y++) {
            yearly(out, y, "spooky", "Spooky Festival", 7, 29, 3, 0xFFFF8A1F, "HUB", "Kill Trick or Treat mobs for Green Candy");
            yearly(out, y, "jerry", "Season of Jerry", 11, 24, 3, 0xFF7FD3FF, "WINTER", "Jerry's Workshop: defend and collect gifts");
            yearly(out, y, "new_year", "New Year Celebration", 11, 29, 3, 0xFFFFE066, "HUB", "Collect your free New Year Cake");
            yearly(out, y, "zoo_summer", "Traveling Zoo", 3, 1, 3, 0xFF66E07A, "HUB", "Buy legendary pets from Oringo");
            yearly(out, y, "zoo_winter", "Traveling Zoo", 9, 1, 3, 0xFF66E07A, "HUB", "Buy legendary pets from Oringo");
            yearly(out, y, "hoppity", "Hoppity's Hunt", 0, 1, 93, 0xFFFF9ECF, "", "Find 3 chocolate eggs every SkyBlock day");
            yearly(out, y, "harvest_feast", "Harvest Feast", 6, 1, 93, 0xFFE8A33D, "", "Crops take their turn in season at the Feast");
            yearly(out, y, "jerrys_workshop", "Jerry's Workshop", 11, 1, 31, 0xFF9AD8FF, "WINTER", "The island is open: /warp jerry");
            if (witchYear(y)) yearly(out, y, "witch", "Year of the Witch", 0, 1, 12 * 31, 0xFFA66BFF, "", WITCH_HINT);
        }
        out.removeIf(e -> e.end() <= from || e.start() >= to);
        out.sort(Comparator.comparingLong(Event::start));
        return out;
    }

    // whole year, every 12 years, 524 was one
    public static final int WITCH_YEAR = 524, WITCH_EVERY = 12;
    static final String WITCH_HINT = "Kill mobs for ingredients, trade them at Agnes and Alcina";

    public static boolean witchYear(int year) {
        return year >= 1 && Math.floorMod(year - WITCH_YEAR, WITCH_EVERY) == 0;
    }

    private static void yearly(List<Event> out, int year, String id, String name, int month, int day, int days, int color, String island, String hint) {
        if (year < 1) return;
        long s = sbToReal(year, month, day);
        out.add(new Event(id, name, s, s + days * DAY, color, island, hint));
    }

    public static String dur(long ms) {
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60, sec = s % 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + sec + "s";
        return sec + "s";
    }
}
