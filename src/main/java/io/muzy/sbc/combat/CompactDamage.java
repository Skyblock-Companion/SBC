// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.combat;

import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.decoration.ArmorStand;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// short damage numbers with a gradient on crits. from skyblocker CompactDamage + OkLabColor
// changes the tag while drawing, nothing gets written on the entity
public final class CompactDamage {
    public static final String ID = "compact_damage";
    // group 1: crit, overload crit. group 2: ring of love, tara armor, end stone sword, voodoo doll, guardian pet
    private static final Pattern DAMAGE = Pattern.compile("([✧✯]?)[\\d,]+[✧✯]?([❤+⚔☄♞]?)");
    private static final long[] POWERS = new long[19];

    static {
        POWERS[0] = 1;
        for (int i = 1; i < POWERS.length; i++) POWERS[i] = POWERS[i - 1] * 10;
    }

    private record Cached(String from, Component to) {}

    private static final Map<Integer, Cached> CACHE = new HashMap<>();

    private CompactDamage() {}

    // tag to draw for this stand, same one unless its a damage number. runs per stand per frame so its cached
    public static Component tag(ArmorStand stand, Component name) {
        if (!Features.on(ID) || !stand.isInvisible()) return name;
        String s = name.getString();
        if (s.isEmpty() || (s.charAt(0) != '✧' && s.charAt(0) != '✯' && !Character.isDigit(s.charAt(0)))) return name;
        Cached c = CACHE.get(stand.getId());
        if (c != null && c.from().equals(s)) return c.to();
        Component pretty = compact(name);
        if (pretty == null) pretty = name;
        if (CACHE.size() > 128) CACHE.clear();
        CACHE.put(stand.getId(), new Cached(s, pretty));
        return pretty;
    }

    static Component compact(Component custom) {
        return compact(custom, Combat.integer(ID, "precision", 4), Combat.color(ID, "crit_start", 0xFFFFFF55) & 0xFFFFFF, Combat.color(ID, "crit_end", 0xFFFF5555) & 0xFFFFFF, Combat.color(ID, "normal", 0xFFFFFFFF) & 0xFFFFFF);
    }

    static Component compact(Component custom, int precision, int critStart, int critEnd, int normal) {
        String text = custom.getString();
        Matcher m = DAMAGE.matcher(text);
        if (!m.matches()) return null;
        List<Component> siblings = custom.getSiblings();
        if (siblings.isEmpty()) return null;
        boolean crit = !m.group(1).isEmpty();
        String digits = text.replaceAll("\\D", "");
        if (digits.isEmpty() || digits.length() > 18) return null;
        TextColor color = siblings.getFirst().getStyle().getColor();
        MutableComponent out = Component.empty();
        String pretty = pretty(Long.parseLong(digits), precision);
        if (crit) {
            String symbol = m.group(1);
            pretty = symbol + pretty + symbol;
            int length = pretty.length();
            for (int i = 0; i < length; i++) out.append(Component.literal(pretty.substring(i, i + 1)).withColor(interpolate(critStart, critEnd, i / (length - 1.0f))));
            out.setStyle(custom.getStyle());
        } else {
            int c = color == null || color == TextColor.fromLegacyFormat(ChatFormatting.GRAY) ? normal : color.getValue();
            out = Component.literal(pretty).setStyle(custom.getStyle()).withColor(c);
        }
        if (!m.group(2).isEmpty()) out.append(Component.literal(m.group(2)).setStyle(siblings.getLast().getStyle()));
        return out;
    }

    // skyblocker prettifyDamageNumber: precision = significant digits, not digits after the point. 999,999 at 3
    // is 1.00M not 1000k
    static String pretty(long damage, int maxPrecision) {
        long target = damage;
        int precision = maxPrecision;
        int used = digits(target);
        if (used > precision) {
            double power = POWERS[used - maxPrecision];
            target = (long) (Math.round((double) target / power) * power);
        } else if (precision > used) {
            precision = used;
        }
        if (target < 1_000L) return String.valueOf(target);
        if (target < 1_000_000L) return toPrecision(target / 1_000.0, precision) + "k";
        if (target < 1_000_000_000L) return toPrecision(target / 1_000_000.0, precision) + "M";
        if (target < 1_000_000_000_000L) return toPrecision(target / 1_000_000_000.0, precision) + "B";
        if (target < 1_000_000_000_000_000L) return toPrecision(target / 1_000_000_000_000.0, precision) + "T";
        return toPrecision(target / 1_000_000_000_000_000.0, precision) + "Q";
    }

    static String toPrecision(double number, int precision) {
        int used = digits((int) number);
        int left = precision - used;
        if (left <= 0) {
            long power = POWERS[used - precision];
            return String.valueOf(Math.round(number / power) * power);
        }
        return String.format(Locale.ROOT, "%." + left + "f", number);
    }

    static int digits(long x) {
        int n = 1;
        while (n < POWERS.length && x >= POWERS[n]) n++;
        return n;
    }

    // from skyblocker OkLabColor.interpolate

    private static float linear(float c) {
        return c <= 0.04045f ? c / 12.92f : (float) Math.pow((c + 0.055f) / 1.055f, 2.4f);
    }

    private static float gamma(float c) {
        return c <= 0.0031308f ? c * 12.92f : 1.055f * (float) Math.pow(c, 1.0f / 2.4f) - 0.055f;
    }

    private static float[] lab(int rgb) {
        float r = linear(((rgb >> 16) & 0xFF) / 255f), g = linear(((rgb >> 8) & 0xFF) / 255f), b = linear((rgb & 0xFF) / 255f);
        float l = (float) Math.cbrt(0.4122214708f * r + 0.5363325363f * g + 0.0514459929f * b);
        float m = (float) Math.cbrt(0.2119034982f * r + 0.6806995451f * g + 0.1073969566f * b);
        float s = (float) Math.cbrt(0.0883024619f * r + 0.2817188376f * g + 0.6299787005f * b);
        return new float[]{0.2104542553f * l + 0.7936177850f * m - 0.0040720468f * s, 1.9779984951f * l - 2.4285922050f * m + 0.4505937099f * s, 0.0259040371f * l + 0.7827717662f * m - 0.8086757660f * s};
    }

    static int interpolate(int first, int second, float t) {
        float[] a = lab(first), z = lab(second);
        float ll = a[0] + t * (z[0] - a[0]), aa = a[1] + t * (z[1] - a[1]), bb = a[2] + t * (z[2] - a[2]);
        float l = ll + 0.3963377774f * aa + 0.2158037573f * bb, m = ll - 0.1055613458f * aa - 0.0638541728f * bb, s = ll - 0.0894841775f * aa - 1.2914855480f * bb;
        l = l * l * l;
        m = m * m * m;
        s = s * s * s;
        float r = 4.0767416621f * l - 3.3077115913f * m + 0.2309699292f * s, g = -1.2684380046f * l + 2.6097574011f * m - 0.3413193965f * s, b = -0.0041960863f * l - 0.7034186147f * m + 1.7076147010f * s;
        return Math.round(Math.clamp(gamma(r) * 255f, 0, 255)) << 16 | Math.round(Math.clamp(gamma(g) * 255f, 0, 255)) << 8 | Math.round(Math.clamp(gamma(b) * 255f, 0, 255));
    }

    static void register() {
        Feature f = new Feature(ID, "Combat", "Compact Damage", "Damage numbers in short form, a crit in a gradient.", "Skyblocker: Compact Damage").when(Combat::sb)
                .option(Option.integer("compact_damage.precision", "Digits shown", "How many digits of the number are written.", 4, 1, 15))
                .option(Option.color("compact_damage.normal", "Normal damage", "", 0xFFFFFFFF))
                .option(Option.color("compact_damage.crit_start", "Crit gradient start", "", 0xFFFFFF55))
                .option(Option.color("compact_damage.crit_end", "Crit gradient end", "", 0xFFFF5555));
        Features.add(f);
    }
}
