// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// how every card is written: all values in little room, 2 short values per row, short labels + numbers,
// meters and rules get little air
public final class Compact {
    public static final int ROW_H = 9, WIDE = 172, GAP = 7, METER_BLOCK = 3, RULE_BLOCK = 2, THICK = 2, THICK_BLOCK = 3;

    // labels every card shares, a card with a better one says so in brief()
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("Per hour", "/h"), Map.entry("Time left", "Left"), Map.entry("To next", "Next"), Map.entry("Total", "Total"),
            Map.entry("Crops a minute", "C/min"), Map.entry("Bazaar an hour", "BZ/h"), Map.entry("NPC an hour", "NPC/h"),
            Map.entry("Blocks a second", "BPS"), Map.entry("Blocks broken", "Blocks"), Map.entry("Farming XP an hour", "XP/h"),
            Map.entry("Farming Fortune", "FF"), Map.entry("Bonus Pest Chance", "Pest"), Map.entry("Sowdust earned", "Sowdust"),
            Map.entry("Yaw and pitch", "Angle"), Map.entry("Cultivating", "Cult"));
    private static final String[][] WORDS = {{" an hour", "/h"}, {" a minute", "/min"}, {" a second", "/s"}, {"Experience", "XP"}, {"Reputation", "Rep"},
            {"Fortune", "FF"}, {"Damage", "Dmg"}, {"Level", "Lvl"}, {"Collection", "Coll"}};
    // whole numbers with commas. "1.5" or "12k" are short already
    private static final Pattern LONG_NUMBER = Pattern.compile("(?<!§)(?<![.,])(?<!(?<!§)\\d)(\\d{1,3}(?:,\\d{3})+)(?![\\d.,]*\\d)");

    private Compact() {}

    // label the compact way, color codes kept
    public static String label(String label) {
        String plain = RowText.plain(label).trim();
        String mapped = LABELS.get(plain);
        if (mapped != null) {
            int at = label.indexOf(plain);
            return (at > 0 ? label.substring(0, at) : "") + mapped;
        }
        String out = label;
        for (String[] w : WORDS) out = out.replace(w[0], w[1]);
        return out;
    }

    private static String shortNumber(long n) {
        if (n >= 1_000_000_000L) return String.format(Locale.ROOT, "%.2fB", n / 1e9);
        if (n >= 1_000_000L) return String.format(Locale.ROOT, "%.2fM", n / 1e6);
        if (n >= 100_000L) return Math.round(n / 1e3) + "k";
        return String.format(Locale.ROOT, "%.1fk", n / 1e3);
    }

    // long numbers shortened: "230,499" -> "230k". under 10,000 stays exact, its short enough
    public static String value(String value) {
        if (value == null || value.indexOf(',') < 0) return value;
        Matcher m = LONG_NUMBER.matcher(value);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            long n = Long.parseLong(m.group(1).replace(",", ""));
            m.appendReplacement(out, Matcher.quoteReplacement(n < 10_000 ? m.group(1) : shortNumber(n)));
        }
        m.appendTail(out);
        return out.toString();
    }

    // lines in their order in rows of 1 or 2. neighbours share a row when both can and each fits half. nothing
    // dropped, nothing reordered
    public static <T> List<List<T>> rows(List<T> lines, Predicate<T> mayPair, ToIntFunction<T> width, int room) {
        List<List<T>> out = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            T a = lines.get(i);
            if (i + 1 < lines.size() && mayPair.test(a) && mayPair.test(lines.get(i + 1))) {
                T b = lines.get(i + 1);
                if (2 * Math.max(width.applyAsInt(a), width.applyAsInt(b)) + GAP <= room) {
                    out.add(List.of(a, b));
                    i++;
                    continue;
                }
            }
            out.add(List.of(a));
        }
        return out;
    }
}
