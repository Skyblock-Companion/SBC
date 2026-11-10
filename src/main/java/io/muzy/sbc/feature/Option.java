// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.feature;

import io.muzy.sbc.core.Config;

import java.util.Locale;
import java.util.Map;

// one feature setting: switch, number, choice, color or text, with its default. values live in Config.options
// under "feature.key" so every option is saved, searchable and resettable
public final class Option {
    public enum Kind { BOOL, INT, FLOAT, ENUM, COLOR, TEXT }

    public final String key, label, description;
    public final Kind kind;
    public final Object def;
    public final double min, max, step;
    public final String[] choices;
    private java.util.function.Supplier<Object> getter;
    private java.util.function.Consumer<Object> setter;

    private Option(String key, String label, String description, Kind kind, Object def, double min, double max, double step, String[] choices) {
        this.key = key;
        this.label = label;
        this.description = description;
        this.kind = kind;
        this.def = def;
        this.min = min;
        this.max = max;
        this.step = step;
        this.choices = choices;
    }

    public static Option bool(String key, String label, String description, boolean def) {
        return new Option(key, label, description, Kind.BOOL, def, 0, 1, 1, null);
    }

    public static Option integer(String key, String label, String description, int def, int min, int max) {
        return new Option(key, label, description, Kind.INT, def, min, max, 1, null);
    }

    public static Option number(String key, String label, String description, double def, double min, double max, double step) {
        return new Option(key, label, description, Kind.FLOAT, def, min, max, step, null);
    }

    public static Option choice(String key, String label, String description, String def, String... choices) {
        return new Option(key, label, description, Kind.ENUM, def, 0, choices.length - 1, 1, choices);
    }

    public static Option color(String key, String label, String description, int argb) {
        return new Option(key, label, description, Kind.COLOR, argb, 0, 0, 0, null);
    }

    public static Option text(String key, String label, String description, String def) {
        return new Option(key, label, description, Kind.TEXT, def, 0, 0, 0, null);
    }

    // reads + writes an existing field instead of the generic store
    public Option bind(java.util.function.Supplier<Object> get, java.util.function.Consumer<Object> set) {
        getter = get;
        setter = set;
        return this;
    }

    // --- values ---

    private static Map<String, Object> store() {
        return Config.I.options;
    }

    public Object raw() {
        if (getter != null) {
            Object g = getter.get();
            return g == null ? def : g;
        }
        Object v = store().get(key);
        return v == null ? def : v;
    }

    public void set(Object v) {
        if (setter != null) {
            setter.accept(v == null ? def : v);
            return;
        }
        if (v == null || v.equals(def)) store().remove(key);
        else store().put(key, v);
    }

    public void reset() {
        if (setter != null) setter.accept(def);
        else store().remove(key);
    }

    public boolean isDefault() {
        if (getter != null) {
            Object g = raw();
            if (g instanceof Number a && def instanceof Number b) return Math.abs(a.doubleValue() - b.doubleValue()) < 1e-9;
            return String.valueOf(g).equalsIgnoreCase(String.valueOf(def));
        }
        return !store().containsKey(key);
    }

    public boolean bool() {
        Object v = raw();
        return v instanceof Boolean b ? b : v instanceof Number n ? n.intValue() != 0 : Boolean.parseBoolean(String.valueOf(v));
    }

    public double number() {
        Object v = raw();
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (Exception e) {
            return def instanceof Number d ? d.doubleValue() : 0;
        }
    }

    public int integer() {
        return (int) Math.round(number());
    }

    public String text() {
        return String.valueOf(raw());
    }

    // for ENUM: the chosen value, always one of the choices
    public String choice() {
        String v = text();
        if (choices != null) for (String c : choices) if (c.equalsIgnoreCase(v)) return c;
        return String.valueOf(def);
    }

    public int choiceIndex() {
        String c = choice();
        if (choices != null) for (int i = 0; i < choices.length; i++) if (choices[i].equals(c)) return i;
        return 0;
    }

    public int color() {
        Object v = raw();
        if (v instanceof Number n) return (int) n.longValue();
        try {
            String s = String.valueOf(v).replace("#", "");
            long l = Long.parseLong(s, 16);
            return s.length() <= 6 ? (int) (l | 0xFF000000L) : (int) l;
        } catch (Exception e) {
            return def instanceof Number d ? d.intValue() : 0xFFFFFFFF;
        }
    }

    // short readable form of the value, for the settings rows
    public String valueText() {
        return switch (kind) {
            case BOOL -> bool() ? "On" : "Off";
            case INT -> String.valueOf(integer());
            case FLOAT -> String.format(Locale.ROOT, step >= 1 ? "%.0f" : step >= 0.1 ? "%.1f" : "%.2f", number());
            case ENUM -> choice();
            case COLOR -> String.format(Locale.ROOT, "#%08X", color());
            case TEXT -> text();
        };
    }

    // every search word shows up in the label or description
    public boolean matches(String[] words) {
        String hay = (label + " " + description + " " + key).toLowerCase(Locale.ROOT);
        for (String w : words) if (!hay.contains(w)) return false;
        return true;
    }
}
