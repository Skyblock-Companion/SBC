// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// hud editor presets + undo. a preset = positions, sizes, styles as one json in
// config/sbcompanion/hud_presets/ so you can share it. built in ones get generated
public final class HudPresets {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Deque<String> UNDO = new ArrayDeque<>(), REDO = new ArrayDeque<>();
    private static final int LIMIT = 50;
    private HudPresets() {}

    // everything the editor changes, one json
    public static final class Snapshot {
        public Map<String, float[]> prefs = new HashMap<>();
        public Map<String, Map<String, Object>> styles = new HashMap<>();
        public Map<String, int[]> nudge = new HashMap<>();
        public float hudAll = 1, hudScale = 1;
        public int hudX = 6;
        public float classicAlpha = dev.zmuzy.sbc.core.Config.DEFAULT_ALPHA;
    }

    public static Snapshot capture() {
        Snapshot s = new Snapshot();
        for (var e : dev.zmuzy.sbc.ui.HudLayout.prefs().entrySet()) s.prefs.put(e.getKey(), e.getValue().clone());
        for (var e : Config.I.hudStyles.entrySet()) s.styles.put(e.getKey(), new HashMap<>(e.getValue()));
        for (var e : dev.zmuzy.sbc.ui.HudLayout.nudges().entrySet()) s.nudge.put(e.getKey(), e.getValue().clone());
        s.hudAll = Config.I.hudAll;
        s.hudScale = Config.I.hudScale;
        s.hudX = Config.I.hudX;
        s.classicAlpha = Config.I.classicAlpha;
        return s;
    }

    public static void apply(Snapshot s) {
        dev.zmuzy.sbc.ui.HudLayout.prefs().clear();
        dev.zmuzy.sbc.ui.HudLayout.prefs().putAll(s.prefs);
        Config.I.hudStyles.clear();
        Config.I.hudStyles.putAll(s.styles);
        dev.zmuzy.sbc.ui.HudLayout.nudges().clear();
        dev.zmuzy.sbc.ui.HudLayout.nudges().putAll(s.nudge);
        Config.I.hudAll = s.hudAll;
        Config.I.hudScale = s.hudScale;
        Config.I.hudX = s.hudX;
        Config.I.classicAlpha = s.classicAlpha;
        Config.save();
    }

    // --- undo ---

    // call before a change
    public static void mark() {
        UNDO.push(GSON.toJson(capture()));
        while (UNDO.size() > LIMIT) UNDO.removeLast();
        REDO.clear();
    }

    public static boolean canUndo() {
        return !UNDO.isEmpty();
    }

    public static boolean canRedo() {
        return !REDO.isEmpty();
    }

    public static void undo() {
        if (UNDO.isEmpty()) return;
        REDO.push(GSON.toJson(capture()));
        apply(GSON.fromJson(UNDO.pop(), Snapshot.class));
    }

    public static void redo() {
        if (REDO.isEmpty()) return;
        UNDO.push(GSON.toJson(capture()));
        apply(GSON.fromJson(REDO.pop(), Snapshot.class));
    }

    // --- presets ---

    private static Path dir() {
        return Config.DIR.resolve("hud_presets");
    }

    public static List<String> names() {
        List<String> out = new ArrayList<>(List.of("Default"));
        try {
            if (Files.isDirectory(dir())) try (Stream<Path> s = Files.list(dir())) {
                s.filter(p -> p.toString().endsWith(".json")).map(p -> p.getFileName().toString().replace(".json", "")).sorted().forEach(out::add);
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    public static void save(String name) {
        try {
            Files.createDirectories(dir());
            Files.writeString(dir().resolve(safe(name) + ".json"), GSON.toJson(capture()));
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not save preset {}", name, e);
        }
    }

    public static void delete(String name) {
        try {
            Files.deleteIfExists(dir().resolve(safe(name) + ".json"));
        } catch (Exception ignored) {
        }
    }

    private static String safe(String name) {
        return name.replaceAll("[^A-Za-z0-9 _-]", "").trim();
    }

    public static void load(String name) {
        mark();
        switch (name) {
            case "Default" -> {
                apply(new Snapshot());
            }
            default -> {
                try {
                    apply(GSON.fromJson(Files.readString(dir().resolve(safe(name) + ".json")), Snapshot.class));
                } catch (Exception e) {
                    SbcMod.LOG.warn("Could not load preset {}", name, e);
                }
            }
        }
    }

    // layout as json text for sharing, importJson reads it back
    public static String exportJson() {
        return GSON.toJson(capture());
    }

    public static boolean importJson(String json) {
        try {
            Snapshot s = GSON.fromJson(json, Snapshot.class);
            if (s == null) return false;
            mark();
            apply(s);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @SuppressWarnings("unused")
    private static final java.lang.reflect.Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();
}
