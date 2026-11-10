// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.core.data;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.Config;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// turns a broken feature off for everyone without a release: remote.json at the root of our repo, read
// through DataUpdater and applied next start. shape is skyhannis
public final class RemoteSwitch {
    private RemoteSwitch() {}

    static final String FILE = "remote.json";
    private static volatile Set<String> off;
    private static final Map<String, Held> HELD = new ConcurrentHashMap<>();

    // a bound feature kept off: its json field in the config + what the player chose
    private static final class Held {
        final Feature feature;
        final String field;
        volatile boolean choice;

        Held(Feature feature, String field, boolean choice) {
            this.feature = feature;
            this.field = field;
            this.choice = choice;
        }
    }

    // ids a file turns off, only ones set to true
    static Set<String> parse(String text) {
        Set<String> out = new HashSet<>();
        try {
            JsonObject features = JsonParser.parseString(text).getAsJsonObject().getAsJsonObject("features");
            if (features == null) return out;
            for (Map.Entry<String, JsonElement> e : features.entrySet()) {
                JsonElement v = e.getValue();
                if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean() && v.getAsBoolean()) out.add(e.getKey());
            }
        } catch (Exception e) {
            return new HashSet<>();
        }
        return out;
    }

    // feature is off remotely. cheap, called wherever a switch is read
    public static boolean isOff(String id) {
        Set<String> s = off;
        if (s == null) s = load();
        return !s.isEmpty() && s.contains(id);
    }

    private static synchronized Set<String> load() {
        if (off != null) return off;
        Set<String> s = Set.of();
        try {
            DataFiles.ensure();
            Path f = DataFiles.root().resolve("own").resolve(FILE);
            if (GameData.autoUpdate() && Files.exists(f)) s = Set.copyOf(parse(Files.readString(f)));
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read the remote switch file, nothing is turned off: {}", e.toString());
        }
        if (!s.isEmpty()) SbcMod.LOG.info("Turned off remotely until a fix: {}", s);
        off = s;
        return s;
    }

    // once all features exist: every bound feature thats off remotely gets its field held false. true = any
    static synchronized boolean holdBound() {
        for (Feature f : Features.all()) {
            if (!f.bound() || !isOff(f.id) || HELD.containsKey(f.id)) continue;
            boolean choice = f.field();
            String field = jsonField(f);
            f.field(false);
            if (field == null) SbcMod.LOG.warn("The remote switch of {} can not keep its stored value", f.id);
            HELD.put(f.id, new Held(f, field, choice));
        }
        return !HELD.isEmpty();
    }

    // which config key a bound feature writes: the one that differs when its field does
    private static String jsonField(Feature f) {
        Gson g = new Gson();
        boolean was = f.field();
        f.field(true);
        JsonObject on = g.toJsonTree(Config.I).getAsJsonObject();
        f.field(false);
        JsonObject out = g.toJsonTree(Config.I).getAsJsonObject();
        f.field(was);
        for (Map.Entry<String, JsonElement> e : on.entrySet()) if (!e.getValue().equals(out.get(e.getKey()))) return e.getKey();
        return null;
    }

    // a settings click while held off gets kept for later. true = this took the choice
    public static boolean keepChoice(String id, boolean v) {
        Held h = HELD.get(id);
        if (h == null) return false;
        h.choice = v;
        return true;
    }

    // every tick while something is held: whatever wrote the field true (a settings reset) only changed the choice
    static void keepOff() {
        boolean changed = false;
        for (Held h : HELD.values()) {
            if (!h.feature.field()) continue;
            h.choice = true;
            h.feature.field(false);
            changed = true;
        }
        if (changed) Config.save();
    }

    // config file text: what the player chose goes in, not the session false
    public static String savedText(Gson gson, Config config) {
        if (HELD.isEmpty()) return gson.toJson(config);
        JsonObject tree = gson.toJsonTree(config).getAsJsonObject();
        for (Held h : HELD.values()) if (h.field != null) tree.addProperty(h.field, h.choice);
        return gson.toJson(tree);
    }

    // tests: read again next ask + let go of whats held
    static synchronized void forget() {
        for (Held h : HELD.values()) h.feature.field(h.choice);
        HELD.clear();
        off = null;
    }
}
