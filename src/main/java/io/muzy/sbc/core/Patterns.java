// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.SbcMod;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// every regex + keyword list we match hypixel text with, loaded from json not hardcoded:
// assets/sbcompanion/patterns/<area>.json in the jar, a file with the same name in the config folder wins
public final class Patterns {
    // handle that always uses the current pattern, so reloads work without touching callers
    public static final class P {
        public final String key;
        private Pattern cached;
        private int gen = -1;

        P(String key) {
            this.key = key;
        }

        public Pattern pattern() {
            if (gen != generation) {
                cached = get(key);
                gen = generation;
            }
            return cached;
        }

        public Matcher matcher(CharSequence s) {
            return pattern().matcher(s);
        }

        @Override
        public String toString() {
            return pattern().pattern();
        }
    }

    private static final String[] AREAS = {"core", "chat", "nav", "dungeon", "item", "track", "ui", "map", "minion", "tasks", "feature", "misc", "web", "skyhanni_scoreboard", "skyhanni_regexes"};
    private static final Pattern NOTHING = Pattern.compile("(?!)");
    private static final Map<String, Pattern> compiled = new ConcurrentHashMap<>();
    private static final Map<String, JsonElement> raw = new ConcurrentHashMap<>();
    private static final Map<String, P> handles = new ConcurrentHashMap<>();
    private static volatile boolean loaded;
    private static volatile int generation;

    private Patterns() {}

    public static P p(String key) {
        return handles.computeIfAbsent(key, P::new);
    }

    private static synchronized void ensure() {
        if (loaded) return;
        loaded = true;
        for (String area : AREAS) {
            try (InputStream in = Patterns.class.getResourceAsStream("/assets/sbcompanion/patterns/" + area + ".json")) {
                if (in != null) merge(JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject());
            } catch (Exception e) {
                SbcMod.LOG.warn("Bad pattern file {}", area, e);
            }
        }
        io.muzy.sbc.core.data.RegexCheck.overlay(raw);
        Path dir;
        try {
            dir = Config.DIR.resolve("patterns");
        } catch (Throwable t) {
            return; // no game dir (unit tests) = built in patterns only
        }
        if (Files.isDirectory(dir)) {
            try (Stream<Path> files = Files.list(dir)) {
                for (Path f : files.filter(x -> x.toString().endsWith(".json")).toList()) {
                    try {
                        merge(JsonParser.parseString(Files.readString(f)).getAsJsonObject());
                        SbcMod.LOG.info("Pattern overrides from {}", f.getFileName());
                    } catch (Exception e) {
                        SbcMod.LOG.warn("Bad pattern override {}", f, e);
                    }
                }
            } catch (Exception ignored) {
            }
        }
    }

    private static void merge(JsonObject o) {
        for (Map.Entry<String, JsonElement> e : o.entrySet()) if (!e.getKey().startsWith("_")) raw.put(e.getKey(), e.getValue());
    }

    // compiled pattern for a key. unknown or broken key = a pattern that matches nothing
    public static Pattern get(String key) {
        ensure();
        Pattern p = compiled.get(key);
        if (p != null) return p;
        JsonElement e = raw.get(key);
        if (e == null) {
            SbcMod.LOG.warn("No pattern for {}", key);
            compiled.put(key, NOTHING);
            return NOTHING;
        }
        try {
            String regex;
            int flags = 0;
            if (e.isJsonObject()) {
                JsonObject o = e.getAsJsonObject();
                regex = o.get("regex").getAsString();
                if (o.has("flags")) for (String f : o.get("flags").getAsString().split("\\|")) flags |= flag(f.trim());
            } else regex = e.getAsString();
            p = Pattern.compile(regex, flags);
        } catch (Exception ex) {
            SbcMod.LOG.warn("Bad pattern {}: {}", key, ex.getMessage());
            p = NOTHING;
        }
        compiled.put(key, p);
        return p;
    }

    private static int flag(String name) {
        return switch (name) {
            case "CASE_INSENSITIVE" -> Pattern.CASE_INSENSITIVE;
            case "MULTILINE" -> Pattern.MULTILINE;
            case "DOTALL" -> Pattern.DOTALL;
            case "UNICODE_CASE" -> Pattern.UNICODE_CASE;
            case "COMMENTS" -> Pattern.COMMENTS;
            default -> 0;
        };
    }

    // plain strings (keywords, names, titles) under a key, empty = unknown
    public static List<String> list(String key) {
        ensure();
        JsonElement e = raw.get(key);
        List<String> out = new ArrayList<>();
        if (e != null && e.isJsonArray()) for (JsonElement x : e.getAsJsonArray()) out.add(x.getAsString());
        else if (e != null && e.isJsonPrimitive()) out.add(e.getAsString());
        return out;
    }

    // plain string under a key or the fallback
    public static String text(String key, String fallback) {
        ensure();
        JsonElement e = raw.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    public static boolean has(String key) {
        ensure();
        return raw.containsKey(key);
    }

    public static int size() {
        ensure();
        return raw.size();
    }

    // drop everything + reread the files, handles pick the new ones up next use
    public static synchronized void reload() {
        loaded = false;
        raw.clear();
        compiled.clear();
        generation++;
        ensure();
        SbcMod.LOG.info("Patterns reloaded: {} keys", raw.size());
    }
}
