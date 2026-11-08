// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.Locale;
import java.util.Map;

// key as short english text ("K", "Numpad +", "Left Alt") whatever language the game is in
// hebrew or arabic key names inside an english line get drawn right to left and come out scrambled
public final class KeyNames {
    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("keypad.add", "Numpad +"), Map.entry("keypad.subtract", "Numpad -"), Map.entry("keypad.multiply", "Numpad *"),
            Map.entry("keypad.divide", "Numpad /"), Map.entry("keypad.enter", "Numpad Enter"), Map.entry("keypad.decimal", "Numpad ."),
            Map.entry("keypad.equal", "Numpad ="), Map.entry("grave.accent", "`"), Map.entry("minus", "-"), Map.entry("equal", "="),
            Map.entry("left.bracket", "["), Map.entry("right.bracket", "]"), Map.entry("backslash", "\\"), Map.entry("semicolon", ";"),
            Map.entry("apostrophe", "'"), Map.entry("comma", ","), Map.entry("period", "."), Map.entry("slash", "/"),
            Map.entry("left.alt", "Left Alt"), Map.entry("right.alt", "Right Alt"), Map.entry("left.shift", "Left Shift"),
            Map.entry("right.shift", "Right Shift"), Map.entry("left.control", "Left Ctrl"), Map.entry("right.control", "Right Ctrl"),
            Map.entry("left.win", "Left Win"), Map.entry("right.win", "Right Win"), Map.entry("page.up", "Page Up"),
            Map.entry("page.down", "Page Down"), Map.entry("caps.lock", "Caps Lock"), Map.entry("num.lock", "Num Lock"),
            Map.entry("scroll.lock", "Scroll Lock"), Map.entry("print.screen", "Print Screen"));

    private KeyNames() {}

    // null = not bound
    public static String of(String saved) {
        if (saved == null || saved.isEmpty() || saved.equals("key.keyboard.unknown")) return null;
        if (saved.startsWith("key.mouse.")) {
            String b = saved.substring("key.mouse.".length());
            return switch (b) {
                case "left" -> "Left Click";
                case "right" -> "Right Click";
                case "middle" -> "Middle Click";
                default -> "Mouse " + b;
            };
        }
        String k = saved.startsWith("key.keyboard.") ? saved.substring("key.keyboard.".length()) : saved;
        String named = NAMES.get(k);
        if (named != null) return named;
        if (k.startsWith("keypad.")) return "Numpad " + k.substring("keypad.".length()).toUpperCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (String part : k.split("\\.")) {
            if (part.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(part.length() <= 3 ? part.toUpperCase(Locale.ROOT) : Character.toUpperCase(part.charAt(0)) + part.substring(1));
        }
        return out.toString();
    }

    // fallback without a game or binding, "not bound" when it has none
    public static String of(KeyMapping k, String fallback) {
        if (k == null) return fallback;
        String s = of(k.saveString());
        return s == null ? "not bound" : s;
    }

    public static KeyMapping binding(String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.options == null) return null;
        for (KeyMapping k : mc.options.keyMappings) if (k.getName().equals(name)) return k;
        return null;
    }
}
