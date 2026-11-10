// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.core.data.DataFiles;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// pet stats + perks at its level for the profile tooltip. from skyblocker PetLoader.formatLore on our item
// data lore + neus petnums.json. worked out when a tooltip asks
public final class PetLore {
    // from skyblocker PetLoader STATS_NUMBER_PATTERN + OTHER_NUMBER_PATTERN
    private static final Pattern STAT = Pattern.compile("\\{(?<name>[A-Za-z_]+)\\}"), OTHER = Pattern.compile("\\{(?<index>\\d+)\\}");
    // unicode private area = glyphs from the datas own font, mc cant draw them. the stat name gets written next to each
    private static final Pattern FONT_GLYPH = Pattern.compile("[\\uE000-\\uF8FF]");
    private static final DecimalFormat NUMBER = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
    private static JsonObject pets, numbers;

    private PetLore() {}

    private static synchronized void load() {
        if (pets != null) return;
        try {
            String text = RepoData.read("pets.min.json");
            pets = text == null ? new JsonObject() : JsonParser.parseString(text).getAsJsonObject();
        } catch (Exception e) {
            pets = new JsonObject();
        }
        numbers = DataFiles.object(DataFiles.Source.NEU, "petnums.json");
        if (numbers == null) numbers = new JsonObject();
    }

    // empty = data doesnt know this pet
    public static List<String> lines(String type, String tier, int level) {
        load();
        List<String> out = new ArrayList<>();
        JsonObject pet = pets.has(type) ? pets.getAsJsonObject(type) : null;
        JsonObject tiers = pet == null ? null : pet.getAsJsonObject("tiers");
        if (tiers == null || !tiers.has(tier) || !tiers.getAsJsonObject(tier).has("lore")) return out;
        JsonObject levels = numbers.has(type) && numbers.getAsJsonObject(type).has(tier) ? numbers.getAsJsonObject(type).getAsJsonObject(tier) : new JsonObject();
        boolean skip = false;
        for (JsonElement l : tiers.getAsJsonObject(tier).getAsJsonArray("lore")) {
            String line = l.getAsString();
            // from skyblocker formatLore: garbage lines + the empty line after the first kind
            boolean rightClick = line.contains("Right-click to add this") || line.contains("pet menu!");
            if (rightClick || line.contains("Can be upgraded at") || skip) {
                skip = rightClick;
                continue;
            }
            line = fill(STAT, "name", line, name -> stat(levels, name, level));
            line = fill(OTHER, "index", line, index -> other(levels, Integer.parseInt(index), level));
            out.add(FONT_GLYPH.matcher(line).replaceAll(""));
        }
        while (!out.isEmpty() && out.get(out.size() - 1).isBlank()) out.remove(out.size() - 1);
        return out;
    }

    private interface Value {
        Double of(String key);
    }

    private static String fill(Pattern p, String group, String line, Value value) {
        Matcher m = p.matcher(line);
        StringBuilder b = new StringBuilder();
        while (m.find()) {
            Double v = value.of(m.group(group));
            m.appendReplacement(b, Matcher.quoteReplacement(v == null ? "?" : NUMBER.format(v)));
        }
        m.appendTail(b);
        return b.toString();
    }

    // data levels around this one, in order
    private static TreeMap<Integer, JsonObject> byLevel(JsonObject levels) {
        TreeMap<Integer, JsonObject> out = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : levels.entrySet()) {
            try {
                if (e.getValue().isJsonObject()) out.put(Integer.parseInt(e.getKey()), e.getValue().getAsJsonObject());
            } catch (NumberFormatException ignored) {
            }
        }
        return out;
    }

    interface Read {
        Double from(JsonObject level);
    }

    // from neu PetNumbers.interpolatedStatsAtLevel: straight line between the 2 levels the data has
    static Double between(TreeMap<Integer, JsonObject> levels, int level, Read read) {
        if (levels.isEmpty()) return null;
        Map.Entry<Integer, JsonObject> low = levels.floorEntry(level), high = levels.ceilingEntry(level);
        if (low == null) low = high;
        if (high == null) high = low;
        Double a = read.from(low.getValue()), b = read.from(high.getValue());
        if (a == null || b == null) return null;
        if (low.getKey().equals(high.getKey())) return a;
        return a + (b - a) * (level - low.getKey()) / (double) (high.getKey() - low.getKey());
    }

    private static Double stat(JsonObject levels, String name, int level) {
        return between(byLevel(levels), level, o -> {
            JsonObject stats = o.getAsJsonObject("statNums");
            return stats != null && stats.has(name) ? stats.get(name).getAsDouble() : null;
        });
    }

    private static Double other(JsonObject levels, int index, int level) {
        return between(byLevel(levels), level, o -> {
            JsonArray nums = o.getAsJsonArray("otherNums");
            return nums != null && index < nums.size() ? nums.get(index).getAsDouble() : null;
        });
    }
}
