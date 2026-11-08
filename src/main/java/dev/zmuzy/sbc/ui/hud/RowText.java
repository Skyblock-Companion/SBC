// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui.hud;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// what a line says -> how it shows. "Pity Counter: 124/2000" = label, value, meter wherever it came from (our
// panels, sidebar, tab widgets). fractions always show their percent
public final class RowText {
    public record Shaped(String label, String value, double progress) {}

    private static final String NUM = "\\d[\\d,]*(?:\\.\\d+)?[kKmMbB]?";
    private static final Pattern FRACTION = Pattern.compile("(?<![\\d/.,:])(" + NUM + ")\\s*/\\s*(" + NUM + ")(?![\\d/:])");
    private static final Pattern PERCENT = Pattern.compile("(?<![+\\-\\d.,])(\\d+(?:\\.\\d+)?)%");
    private static final Pattern BARS = Pattern.compile("^[\\s|]*\\|{6,}[\\s|]*\\(([\\d.]+)%\\)\\s*$");
    private static final int MAX_KEY = 30, MAX_REMEMBERED = 4096;

    private static final Map<String, Shaped> REMEMBERED = new HashMap<>();

    private RowText() {}

    public static String plain(String s) {
        return s.replaceAll("§.", "");
    }

    // "5,678k" -> 5678000, -1 for anything else
    static double number(String s) {
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

    public static String percent(double fraction) {
        double p = fraction * 100;
        if (Math.abs(p - Math.rint(p)) < 0.05) return Math.round(p) + "%";
        return String.format(Locale.ROOT, "%.1f%%", p);
    }

    // where "Key: value" splits in a coded line, or -1
    private static int keyEnd(String s) {
        int plainLength = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            if (c == ':') {
                String rest = plain(s.substring(i + 1));
                if (plainLength == 0 || plainLength > MAX_KEY || rest.isBlank() || !rest.startsWith(" ")) return -1;
                return i;
            }
            plainLength++;
        }
        return -1;
    }

    // color codes active at the end so the next piece can stand alone
    private static String activeCodes(String s) {
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

    // trims leading spaces, keeps the color codes in front
    public static String trimStart(String s) {
        StringBuilder codes = new StringBuilder();
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '§' && i + 1 < s.length()) {
                codes.append(c).append(s.charAt(i + 1));
                i += 2;
            } else if (c == ' ') i++;
            else break;
        }
        return codes + s.substring(i);
    }

    public static Shaped shape(String label, String value) {
        String key = label + '\0' + value;
        Shaped known = REMEMBERED.get(key);
        if (known != null) return known;
        Shaped made = make(label, value);
        if (REMEMBERED.size() >= MAX_REMEMBERED) REMEMBERED.clear();
        REMEMBERED.put(key, made);
        return made;
    }

    private static Shaped make(String label, String value) {
        String l = label, v = value == null ? "" : value;
        if (v.isEmpty()) {
            int k = keyEnd(l);
            if (k > 0) {
                String left = l.substring(0, k), right = trimStart(l.substring(k + 1));
                String codes = activeCodes(left);
                l = left;
                v = right.startsWith("§") ? right : codes + right;
            }
        }
        String text = plain(v.isEmpty() ? l : v);
        Matcher bars = BARS.matcher(text);
        if (bars.matches()) {
            double p = Double.parseDouble(bars.group(1)) / 100;
            return new Shaped("", percent(p), Math.min(1, p));
        }
        Matcher f = FRACTION.matcher(text);
        if (f.find()) {
            double a = number(f.group(1)), b = number(f.group(2));
            if (a >= 0 && b > 0 && a <= b * 1.0001) {
                double p = Math.min(1, a / b);
                boolean said = text.indexOf('%') >= 0;
                if (v.isEmpty() && plain(l).trim().equals(f.group())) return new Shaped("§7Progress", l.trim() + "  §7" + percent(p), p);
                if (said) return new Shaped(l, v, p);
                if (v.isEmpty()) return new Shaped(l, "§7" + percent(p), p);
                return new Shaped(l, v + "  §7" + percent(p), p);
            }
        }
        Matcher pc = PERCENT.matcher(text);
        if (!v.isEmpty() && pc.find()) {
            double p = Double.parseDouble(pc.group(1)) / 100;
            if (p <= 1) return new Shaped(l, v, p);
        }
        return new Shaped(l, v, -1);
    }
}
