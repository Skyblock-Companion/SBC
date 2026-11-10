// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui;

import io.github.zmuzy.sbc.ui.hud.RowText;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// the pure half of the tab list: how cards share columns (even, not fill one to the bottom first) and what
// each line says
final class TabColumns {
    // taller than this share looks crowded, as long as another column still fits
    private static final double COMFORTABLE = 0.72, COMFORTABLE_LOW = 0.62;
    private static final int MOST_COLUMNS = 6;

    private TabColumns() {}

    // biggest card first into the shortest column, evens them out best. then each column keeps the original order
    // keepOrder = cards stay in order
    static List<List<Integer>> balance(int[] heights, int gap, int columns, boolean keepOrder) {
        List<List<Integer>> cols = new ArrayList<>();
        for (int i = 0; i < columns; i++) cols.add(new ArrayList<>());
        int[] used = new int[columns];
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < heights.length; i++) order.add(i);
        if (!keepOrder) order.sort(Comparator.comparingInt((Integer i) -> -heights[i]).thenComparingInt(i -> i));
        for (int i : order) {
            int best = 0;
            for (int c = 1; c < columns; c++) if (used[c] < used[best]) best = c;
            cols.get(best).add(i);
            used[best] += heights[i] + gap;
        }
        cols.removeIf(List::isEmpty);
        if (!keepOrder) {
            even(cols, heights, gap);
            for (List<Integer> c : cols) c.sort(Comparator.naturalOrder());
            cols.sort(Comparator.comparingInt(c -> c.getFirst()));
        }
        return cols;
    }

    // move/swap cards between the tallest column and the rest while it helps
    private static void even(List<List<Integer>> cols, int[] heights, int gap) {
        if (cols.size() < 2) return;
        for (int round = 0; round < 24; round++) {
            int tall = 0;
            for (int c = 1; c < cols.size(); c++) if (used(cols.get(c), heights, gap) > used(cols.get(tall), heights, gap)) tall = c;
            int bestOther = -1, bestA = -1, bestB = -1, bestTop = used(cols.get(tall), heights, gap);
            List<Integer> high = cols.get(tall);
            int top = used(high, heights, gap);
            for (int c = 0; c < cols.size(); c++) {
                if (c == tall) continue;
                List<Integer> other = cols.get(c);
                int low = used(other, heights, gap);
                for (int a = 0; a < high.size(); a++) {
                    if (high.size() > 1) {
                        int h = heights[high.get(a)] + gap, peak = Math.max(top - h, low + h);
                        if (peak < bestTop) {
                            bestTop = peak;
                            bestOther = c;
                            bestA = a;
                            bestB = -1;
                        }
                    }
                    for (int b = 0; b < other.size(); b++) {
                        int d = heights[high.get(a)] - heights[other.get(b)], peak = Math.max(top - d, low + d);
                        if (d > 0 && peak < bestTop) {
                            bestTop = peak;
                            bestOther = c;
                            bestA = a;
                            bestB = b;
                        }
                    }
                }
            }
            if (bestOther < 0) return;
            List<Integer> other = cols.get(bestOther);
            int moved = high.remove(bestA);
            if (bestB >= 0) high.add(other.remove(bestB));
            other.add(moved);
        }
    }

    private static int used(List<Integer> column, int[] heights, int gap) {
        int h = 0;
        for (int i : column) h += heights[i] + gap;
        return h;
    }

    static int tallest(List<List<Integer>> cols, int[] heights, int gap) {
        int tallest = 0;
        for (List<Integer> c : cols) {
            int h = -gap;
            for (int i : c) h += heights[i] + gap;
            tallest = Math.max(tallest, h);
        }
        return tallest;
    }

    static int totalWidth(List<List<Integer>> cols, int[] widths, int gap) {
        int total = -gap;
        for (List<Integer> c : cols) {
            int w = 0;
            for (int i : c) w = Math.max(w, widths[i]);
            total += w + gap;
        }
        return total;
    }

    // your number, else the fewest that fit the width with nothing crowded, else whatever makes the tallest one shortest
    static List<List<Integer>> arrange(int[] heights, int[] widths, int gap, int maxW, int maxH, int want, boolean keepOrder) {
        if (heights.length == 0) return List.of();
        if (want > 0) return balance(heights, gap, want, keepOrder);
        List<List<Integer>> best = null;
        int bestTall = Integer.MAX_VALUE;
        for (int k = 1; k <= Math.min(MOST_COLUMNS, heights.length); k++) {
            List<List<Integer>> cols = balance(heights, gap, k, keepOrder);
            int tall = tallest(cols, heights, gap);
            boolean fitsWidth = totalWidth(cols, widths, gap) <= maxW;
            if (fitsWidth && tall <= maxH * COMFORTABLE) return cols;
            if (fitsWidth && tall < bestTall || best == null) {
                best = cols;
                bestTall = tall;
            }
        }
        return best;
    }

    // a wide low list beats a narrow tall one
    static boolean comfortable(int tall, int maxH) {
        return tall <= maxH * COMFORTABLE_LOW;
    }

    // same idea but for the biggest list: your number, else fewest that fit without crowding, else least scaling
    static List<List<Integer>> largest(int[] heights, int[] widths, int gap, int maxW, int maxH, int want, boolean keepOrder) {
        if (heights.length == 0) return List.of();
        if (want > 0) return balance(heights, gap, want, keepOrder);
        List<List<Integer>> best = null;
        double bestScale = -1;
        for (int k = 1; k <= Math.min(MOST_COLUMNS, heights.length); k++) {
            List<List<Integer>> cols = balance(heights, gap, k, keepOrder);
            int tall = tallest(cols, heights, gap), total = totalWidth(cols, widths, gap);
            if (total <= maxW && comfortable(tall, maxH)) return cols;
            double scale = Math.min(1, Math.min(maxW / (double) Math.max(1, total), maxH / (double) Math.max(1, tall)));
            if (scale > bestScale + 1e-6) {
                best = cols;
                bestScale = scale;
            }
        }
        return best;
    }

    // --- what a line says ---

    // name, whats on the right of the band, meter if its progress
    record Title(String name, String right, double progress) {}

    // label left, value right, meter 0..1 or -1
    record Shown(String label, String value, double progress) {}

    // "[196] Steve": level, its color code ('f' when none), rest of the line
    record Player(int level, char code, String name) {}

    // progress -1 without an xp line, toNext "" when unknown
    record Pet(int level, String name, String xp, String percent, double progress, String toNext) {}

    // numbers that arent progress, crit chance isnt a fraction of anything
    private static final Set<String> NO_METERS = Set.of("STATS", "DUNGEON_STATS_LINE", "DUNGEON_PLAYER_STATS", "BANK", "GEMS");
    // words that are a state not a reading
    private static final Set<String> STATES = Set.of("ACTIVE", "INACTIVE", "READY", "AVAILABLE", "CLAIMED", "COMPLETE", "COMPLETED", "OPEN", "CLOSED", "ON", "OFF", "MAX PESTS");
    // hypixel says maxed with a word not a number
    private static final Set<String> FULL = Set.of("MAX", "DONE", "MAX LEVEL");

    private static final Pattern COUNT = Pattern.compile("^(.+?):?\\s*\\((\\d+)\\)$");
    private static final Pattern LEVEL = Pattern.compile("^\\[(\\d+)\\]\\s+\\S.*$");
    private static final Pattern PET = Pattern.compile("^\\[Lvl (\\d+)\\]\\s*(.+)$");
    private static final Pattern PET_XP = Pattern.compile("^(.*?)\\s*\\(([\\d.]+)%\\)\\s*$");
    private static final Pattern PET_NUMBERS = Pattern.compile("([\\d,.]+[kKmMbB]?)\\s*/\\s*([\\d,.]+[kKmMbB]?)\\s*XP");
    private static final Pattern NAMED_BARS = Pattern.compile("^(.{1,30}?):\\s*\\|{3,}[\\s|]*\\(([\\d.]+)%\\)\\s*$");
    private static final Pattern TRAILING_TIME = Pattern.compile("^(.+?)\\s+(\\d+[dhms](?:\\s*\\d+[dhms])*|Ready!?)$");
    private static final Pattern BRACKETED_END = Pattern.compile("^(.+?)\\s+(\\[[^\\]]+\\])$");
    private static final Pattern CONTEST_CROP = Pattern.compile("^(.+?)\\s*◆\\s*(Top [\\d.]+%)$");
    private static final Pattern BRACKETED_FRACTION = Pattern.compile("^\\(([\\d,.]+[kKmMbB]?)/([\\d,.]+[kKmMbB]?)\\)\\s+(\\S.*)$");
    private static final Pattern BRACKETED_COUNT = Pattern.compile("^\\(\\d+\\)$");

    // color/format codes still active at the end
    static String activeCodes(String s) {
        StringBuilder codes = new StringBuilder();
        for (int i = 0; i + 1 < s.length(); i++) {
            if (s.charAt(i) != '§') continue;
            char c = Character.toLowerCase(s.charAt(i + 1));
            if ("0123456789abcdefr".indexOf(c) >= 0) codes.setLength(0);
            if (c != 'r') codes.append('§').append(s.charAt(i + 1));
            i++;
        }
        return codes.toString();
    }

    // plain text index -> index in the coded text
    private static int codedIndex(String coded, int plainIndex) {
        int n = 0;
        for (int i = 0; i < coded.length(); i++) {
            if (coded.charAt(i) == '§') {
                i++;
                continue;
            }
            if (n == plainIndex) return i;
            n++;
        }
        return coded.length();
    }

    // text before plainFrom + text from plainTo on with its color
    private static String[] split(String coded, int plainFrom, int plainTo) {
        String left = coded.substring(0, codedIndex(coded, plainFrom));
        int to = codedIndex(coded, plainTo);
        return new String[]{left, activeCodes(coded.substring(0, to)) + coded.substring(to)};
    }

    private static String trimEnd(String coded) {
        return coded.stripTrailing();
    }

    // "Players (19)", "Visitors: (5)", "Skills: 34.5", "Pet:"
    static Title title(String coded) {
        String plain = RowText.plain(coded).trim();
        Matcher count = COUNT.matcher(plain);
        if (count.matches()) return new Title(count.group(1).trim(), count.group(2), -1);
        if (plain.endsWith(":")) return new Title(plain.substring(0, plain.length() - 1).trim(), "", -1);
        int k = plain.indexOf(": ");
        if (k <= 0) return new Title(plain, "", -1);
        String[] parts = split(RowText.trimStart(coded), k, k + 2);
        RowText.Shaped s = RowText.shape(plain.substring(0, k), RowText.trimStart(parts[1]));
        return new Title(plain.substring(0, k).trim(), trimEnd(s.value()), s.progress());
    }

    // fractions + percents get a meter unless that card has no progress
    static Shown shown(String id, String coded) {
        String t = RowText.trimStart(coded);
        String plain = RowText.plain(t).stripTrailing();
        boolean meters = !NO_METERS.contains(id);
        // "Trusted       Honored": 2 ends of a line
        int gap = plain.indexOf("   ");
        if (gap > 0) {
            int to = gap;
            while (to < plain.length() && plain.charAt(to) == ' ') to++;
            String[] parts = split(t, gap, to);
            return new Shown(parts[0], parts[1], -1);
        }
        // "Diaz: |||||||| (45%)": a named bar
        Matcher named = NAMED_BARS.matcher(plain);
        if (named.matches()) {
            double p = Double.parseDouble(named.group(2)) / 100;
            String[] parts = split(t, named.end(1), named.end(1));
            return new Shown(parts[0], activeCodes(t) + RowText.percent(p), Math.min(1, p));
        }
        // "☘ Sunflower ◆ Top 12%": crop + your place in its contest
        Matcher crop = CONTEST_CROP.matcher(plain);
        if (crop.matches()) {
            String[] parts = split(t, crop.end(1), crop.start(2));
            return new Shown(trimEnd(parts[0]), parts[1], -1);
        }
        // "(1,234/1,500) Combat XP": numbers = reading, what they count = name
        Matcher goal = BRACKETED_FRACTION.matcher(plain);
        if (goal.matches() && amount(goal.group(2)) > 0) {
            double p = amount(goal.group(1)) / amount(goal.group(2));
            String[] after = split(t, goal.end(2), goal.start(3));
            String[] numbers = split(after[0], 0, 1);
            return new Shown(after[1], numbers[1] + "  §7" + RowText.percent(Math.min(1, p)), meters ? Math.min(1, p) : -1);
        }
        // the 2 numbers on Bank are 2 accounts, not x out of y
        if (plain.startsWith("Bank: ")) {
            String[] parts = split(t, 4, 6);
            return new Shown(parts[0], trimEnd(parts[1]), -1);
        }
        RowText.Shaped s = RowText.shape(t, "");
        String label = s.label(), value = trimEnd(s.value());
        double progress = meters ? s.progress() : -1;
        // a line thats just a bar says its % in the color it ended in
        if (RowText.plain(label).isBlank() && s.progress() >= 0 && value.indexOf('§') < 0) value = activeCodes(t) + value;
        if (value.isEmpty()) {
            if (isState(plain)) return new Shown("", t, -1); // "ACTIVE" alone is a state not a heading
            Matcher time = TRAILING_TIME.matcher(plain);
            if (time.matches()) {
                String[] parts = split(t, time.end(1), time.start(2));
                return new Shown(parts[0], parts[1], -1);
            }
            Matcher end = BRACKETED_END.matcher(plain);
            if (id.equals("MINION") && end.matches()) {
                String[] parts = split(t, end.end(1), end.start(2));
                return new Shown(parts[0], parts[1], -1);
            }
            return new Shown(trimEnd(label), "", progress);
        }
        if (progress < 0 && meters && FULL.contains(RowText.plain(value).trim().toUpperCase(Locale.ROOT))) progress = 1;
        // "Visitors: (5)": brackets only split the number from a name, here it already stands apart
        if (BRACKETED_COUNT.matcher(RowText.plain(value).trim()).matches()) value = value.replace("(", "").replace(")", "");
        return new Shown(trimEnd(label), value, progress);
    }

    static boolean isState(String value) {
        String p = RowText.plain(value).trim().toUpperCase(Locale.ROOT);
        if (p.endsWith("!")) p = p.substring(0, p.length() - 1);
        return STATES.contains(p);
    }

    // null for any other line
    static Player player(String coded) {
        String t = RowText.trimStart(coded);
        String plain = RowText.plain(t).trim();
        Matcher m = LEVEL.matcher(plain);
        if (!m.matches()) return null;
        int close = t.indexOf(']');
        if (close < 0) return null;
        // digit color: "§8[§b196§8]" closes its bracket in another color
        String left = t.substring(0, close);
        char code = 'f';
        for (int i = 0; i + 1 < left.length() && !Character.isDigit(left.charAt(i)); i++) {
            if (left.charAt(i) != '§') continue;
            char c = Character.toLowerCase(left.charAt(i + 1));
            if ("0123456789abcdef".indexOf(c) >= 0) code = c;
            i++;
        }
        return new Player(Integer.parseInt(m.group(1)), code, io.github.zmuzy.sbc.dungeon.FinderMarks.tabName(trimEnd(RowText.trimStart(t.substring(close + 1)))));
    }

    // null when its not a pet ("No pet selected")
    static Pet pet(String first, String second) {
        String t = RowText.trimStart(first);
        Matcher m = PET.matcher(RowText.plain(t).trim());
        if (!m.matches()) return null;
        int close = t.indexOf(']');
        String name = trimEnd(RowText.trimStart(t.substring(close + 1)));
        int level = Integer.parseInt(m.group(1));
        if (second == null) return new Pet(level, name, "", "", -1, "");
        String s = RowText.trimStart(second);
        String plain = RowText.plain(s).trim();
        if (plain.toUpperCase(Locale.ROOT).contains("MAX LEVEL")) return new Pet(level, name, trimEnd(s), "", 1, "");
        Matcher xp = PET_XP.matcher(plain);
        if (!xp.matches() || !plain.contains("XP")) return null;
        String[] parts = split(s, xp.end(1), xp.start(2) - 1);
        double p = Double.parseDouble(xp.group(2)) / 100;
        return new Pet(level, name, trimEnd(parts[0]), activeCodes(parts[1]) + xp.group(2) + "%", Math.min(1, p), toNext(xp.group(1)));
    }

    // rest of "550,162.4/1.4M XP". "~" when hypixel rounded the target, "" when unreadable
    private static String toNext(String text) {
        Matcher n = PET_NUMBERS.matcher(text);
        if (!n.find()) return "";
        double now = amount(n.group(1)), of = amount(n.group(2));
        if (now < 0 || of <= now) return "";
        boolean rounded = Character.isLetter(n.group(2).charAt(n.group(2).length() - 1));
        // rounded target = only 2 figures make sense
        return rounded ? "~" + shortAmount(new java.math.BigDecimal(of - now).round(new java.math.MathContext(2)).doubleValue()).replace(".0", "") : shortAmount(of - now);
    }

    // -1 for anything else
    static double amount(String s) {
        String t = s.replace(",", "").trim();
        if (t.isEmpty()) return -1;
        double mul = switch (Character.toLowerCase(t.charAt(t.length() - 1))) {
            case 'k' -> 1e3;
            case 'm' -> 1e6;
            case 'b' -> 1e9;
            default -> 1;
        };
        if (mul != 1) t = t.substring(0, t.length() - 1);
        try {
            return Double.parseDouble(t) * mul;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // 849838 -> "849.8k"
    static String shortAmount(double v) {
        if (v >= 1e9) return String.format(Locale.ROOT, "%.1fB", v / 1e9);
        if (v >= 1e6) return String.format(Locale.ROOT, "%.1fM", v / 1e6);
        if (v >= 1e3) return String.format(Locale.ROOT, "%.1fk", v / 1e3);
        return Long.toString(Math.round(v));
    }

    // drops the last visible char without breaking a color code
    static String withoutLast(String coded) {
        int end = coded.length();
        while (end >= 2 && coded.charAt(end - 2) == '§') end -= 2;
        return end <= 0 ? "" : coded.substring(0, end - 1);
    }
}
