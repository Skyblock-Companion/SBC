// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.core.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

// a data files layout taken from the jars copy, to tell a changed layout from a file with just new or removed
// entries. skyhanni finds out by parsing into its data classes (AbstractRepoManager)
final class Shape {
    private enum Kind { NULL, BOOL, NUMBER, TEXT, ARRAY, OBJECT }

    private static final Pattern FIELD = Pattern.compile("[a-z][A-Za-z0-9_]*");
    // same kind of value this many times = a data list, whatever the keys look like
    private static final int BIG_MAP = 12;

    private final EnumSet<Kind> kinds = EnumSet.noneOf(Kind.class);
    private final Map<String, Shape> fields = new HashMap<>();
    // fields a record needs, null till an object was seen
    private Set<String> required;
    // what array elements / map values look like
    private Shape entry;
    // one maps own keys, gone once entries get read together. only the whole files are held to
    private Set<String> mapKeys;

    private Shape() {}

    static Shape of(JsonElement e) {
        Shape s = new Shape();
        s.kinds.add(kind(e));
        if (e.isJsonArray()) {
            for (JsonElement x : e.getAsJsonArray()) s.entry = merged(s.entry, of(x));
        } else if (e.isJsonObject()) {
            JsonObject o = e.getAsJsonObject();
            if (isMap(o)) {
                s.required = new HashSet<>();
                s.mapKeys = new HashSet<>();
                for (Map.Entry<String, JsonElement> x : o.entrySet()) {
                    if (comment(x.getKey())) continue;
                    s.mapKeys.add(x.getKey());
                    s.entry = merged(s.entry, of(x.getValue()));
                }
            } else {
                s.required = new HashSet<>();
                for (Map.Entry<String, JsonElement> x : o.entrySet()) {
                    if (comment(x.getKey())) continue;
                    s.required.add(x.getKey());
                    s.fields.put(x.getKey(), of(x.getValue()));
                }
            }
        }
        return s;
    }

    private static boolean isMap(JsonObject o) {
        EnumSet<Kind> values = EnumSet.noneOf(Kind.class);
        boolean codeNames = true;
        int size = 0;
        for (Map.Entry<String, JsonElement> x : o.entrySet()) {
            if (comment(x.getKey())) continue;
            size++;
            values.add(kind(x.getValue()));
            codeNames &= FIELD.matcher(x.getKey()).matches();
        }
        return values.size() <= 1 && (!codeNames || size >= BIG_MAP || size == 0);
    }

    private static boolean comment(String key) {
        return key.startsWith("//");
    }

    private static Shape merged(Shape a, Shape b) {
        if (a == null) return b;
        a.kinds.addAll(b.kinds);
        for (Map.Entry<String, Shape> f : b.fields.entrySet()) a.fields.merge(f.getKey(), f.getValue(), Shape::merged);
        if (a.required == null) a.required = b.required;
        else if (b.required != null) a.required.retainAll(b.required);
        a.entry = b.entry == null ? a.entry : merged(a.entry, b.entry);
        a.mapKeys = null;
        return a;
    }

    private static Kind kind(JsonElement e) {
        if (e.isJsonNull()) return Kind.NULL;
        if (e.isJsonArray()) return Kind.ARRAY;
        if (e.isJsonObject()) return Kind.OBJECT;
        var p = e.getAsJsonPrimitive();
        return p.isBoolean() ? Kind.BOOL : p.isNumber() ? Kind.NUMBER : Kind.TEXT;
    }

    // where this file stops matching the layout, null = fits
    String misfit(JsonElement fresh) {
        if (mapKeys != null && fresh.isJsonObject()) {
            int kept = 0;
            for (String key : mapKeys) if (fresh.getAsJsonObject().has(key)) kept++;
            if (kept * 2 < mapKeys.size()) return "it kept only " + kept + " of " + mapKeys.size() + " entries";
        }
        return misfit(fresh, "");
    }

    private String misfit(JsonElement fresh, String at) {
        Kind k = kind(fresh);
        if (!kinds.contains(k)) return at + " was " + kinds + ", is " + k;
        if (fresh.isJsonArray()) {
            JsonArray a = fresh.getAsJsonArray();
            if (entry != null) for (int i = 0; i < a.size(); i++) {
                String m = entry.misfit(a.get(i), at + "[" + i + "]");
                if (m != null) return m;
            }
        } else if (fresh.isJsonObject()) {
            JsonObject o = fresh.getAsJsonObject();
            if (required != null) for (String r : required) if (!o.has(r)) return at + "." + r + " is gone";
            for (Map.Entry<String, JsonElement> x : o.entrySet()) {
                if (comment(x.getKey())) continue;
                Shape s = fields.get(x.getKey());
                if (s == null) s = entry;
                if (s == null) continue;
                String m = s.misfit(x.getValue(), at + "." + x.getKey());
                if (m != null) return m;
            }
        }
        return null;
    }
}
