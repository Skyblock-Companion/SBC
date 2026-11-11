// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.data.DataFiles.Source;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// what the jar says about its own data (data/bundled.json): how new each sources copy is, so a download older
// than the jar doesnt hide it after a mod update, and what a release fixed by hand
record Bundled(Map<Source, Instant> dates, Set<String> regexes, Map<Source, Set<String>> files) {
    static final String RESOURCE = "/assets/sbcompanion/data/bundled.json";

    // nothing known: every download stands, nothing pinned
    static Bundled none() {
        return new Bundled(Map.of(), Set.of(), Map.of());
    }

    static Bundled read() {
        try (InputStream in = Bundled.class.getResourceAsStream(RESOURCE)) {
            if (in != null) return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read what the mod knows about its own data: {}", e.toString());
        }
        return none();
    }

    static Bundled parse(String text) {
        JsonObject o = JsonParser.parseString(text).getAsJsonObject();
        Map<Source, Instant> dates = new EnumMap<>(Source.class);
        Map<Source, Set<String>> files = new EnumMap<>(Source.class);
        JsonObject sources = o.getAsJsonObject("sources"), pinned = o.getAsJsonObject("pinned");
        for (Source s : Source.values()) {
            dates.put(s, Instant.parse(sources.getAsJsonObject(s.folder).get("date").getAsString()));
            files.put(s, strings(pinned.getAsJsonObject("files"), s.folder));
        }
        return new Bundled(dates, strings(pinned, "regexes"), files);
    }

    private static Set<String> strings(JsonObject o, String key) {
        Set<String> out = new HashSet<>();
        for (JsonElement e : o.getAsJsonArray(key)) out.add(e.getAsString());
        return out;
    }

    // download with this commit date is older than the jars copy. no date = cant say its newer
    boolean older(Source s, String date) {
        Instant jar = dates.get(s);
        if (jar == null) return false;
        try {
            return date == null || Instant.parse(date).isBefore(jar);
        } catch (Exception e) {
            return true;
        }
    }

    // a release fixed this file by hand, the jars copy stays
    boolean pinned(Source s, String path) {
        return files.getOrDefault(s, Set.of()).contains(path);
    }
}
