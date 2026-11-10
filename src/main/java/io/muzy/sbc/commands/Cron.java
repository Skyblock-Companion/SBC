// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.commands;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

// next time a cron line fires, for the cron todos. from skyhanni CronExpression.getNextValidTimeAfter
// 6 fields: sec min hour dom month dow, each *, ?, number, list, range, step. quartz L W # not supported,
// those count as invalid
final class Cron {
    private static final int[][] RANGES = {{0, 59}, {0, 59}, {0, 23}, {1, 31}, {1, 12}, {1, 7}};

    private Cron() {}

    private static boolean[] field(String text, int min, int max) {
        boolean[] on = new boolean[max + 1];
        for (String part : text.split(",")) {
            String[] step = part.split("/", 2);
            int by = step.length > 1 ? Integer.parseInt(step[1]) : 1;
            if (by < 1) throw new IllegalArgumentException(part);
            int from, to;
            if (step[0].equals("*") || step[0].equals("?")) {
                from = min;
                to = max;
            } else if (step[0].contains("-")) {
                String[] r = step[0].split("-", 2);
                from = Integer.parseInt(r[0]);
                to = Integer.parseInt(r[1]);
            } else {
                from = Integer.parseInt(step[0]);
                to = step.length > 1 ? max : from;
            }
            if (from < min || to > max) throw new IllegalArgumentException(part);
            for (int i = from; i <= to; i += by) on[i] = true;
        }
        return on;
    }

    // epoch ms of the first fire strictly after afterMs, -1 if unreadable or nothing in the next 2 years
    static long nextAfter(String expression, long afterMs, ZoneId zone) {
        boolean[][] f = new boolean[6][];
        try {
            String[] parts = expression.trim().split("\\s+");
            if (parts.length != 6) return -1;
            for (int i = 0; i < 6; i++) f[i] = field(parts[i], RANGES[i][0], RANGES[i][1]);
        } catch (IllegalArgumentException e) {
            return -1;
        }
        ZonedDateTime t = Instant.ofEpochMilli(afterMs).atZone(zone).truncatedTo(ChronoUnit.SECONDS).plusSeconds(1);
        ZonedDateTime end = t.plusYears(2);
        while (t.isBefore(end)) {
            if (!f[4][t.getMonthValue()]) {
                t = t.plusMonths(1).withDayOfMonth(1).truncatedTo(ChronoUnit.DAYS);
                continue;
            }
            // quartz counts dow from sunday = 1
            int dow = t.getDayOfWeek().getValue() % 7 + 1;
            if (!f[3][t.getDayOfMonth()] || !f[5][dow]) {
                t = t.plusDays(1).truncatedTo(ChronoUnit.DAYS);
                continue;
            }
            if (!f[2][t.getHour()]) {
                t = t.plusHours(1).truncatedTo(ChronoUnit.HOURS);
                continue;
            }
            if (!f[1][t.getMinute()]) {
                t = t.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES);
                continue;
            }
            if (!f[0][t.getSecond()]) {
                t = t.plusSeconds(1);
                continue;
            }
            return t.toInstant().toEpochMilli();
        }
        return -1;
    }

    static long nextAfter(String expression, long afterMs) {
        return nextAfter(expression, afterMs, ZoneId.systemDefault());
    }
}
