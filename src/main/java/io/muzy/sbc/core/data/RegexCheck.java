// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import io.muzy.sbc.SbcMod;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

// can a downloaded regex replace ours. skyhanni RepoPatternManager keeps its default when a remote pattern
// doesnt compile. here callers also read named groups so a pattern missing one gets refused too
public final class RegexCheck {
    // what a downloaded file changes + counts for the log line
    public record Result(Map<String, JsonElement> updates, int same, int rejected, int vanished) {}

    public static final String PREFIX = "SH.";
    static final String FILE = "regexesModern.json";

    private RegexCheck() {}

    // downloaded regexesModern.json vs whats bundled under PREFIX. bad text changes nothing
    public static Result check(String file, Map<String, JsonElement> bundled) {
        Map<String, JsonElement> updates = new HashMap<>();
        JsonObject remote;
        try {
            remote = JsonParser.parseString(file).getAsJsonObject().getAsJsonObject("regexes");
        } catch (Exception e) {
            return new Result(updates, 0, 0, 0);
        }
        if (remote == null) return new Result(updates, 0, 0, 0);
        int same = 0, rejected = 0, vanished = 0;
        for (Map.Entry<String, JsonElement> e : bundled.entrySet()) {
            String key = e.getKey();
            if (!key.startsWith(PREFIX) || !isText(e.getValue())) continue;
            JsonElement up = remote.get(key.substring(PREFIX.length()));
            if (up == null) {
                vanished++;
                continue;
            }
            String ours = e.getValue().getAsString();
            if (!isText(up)) {
                rejected++;
                continue;
            }
            String theirs = up.getAsString();
            if (theirs.equals(ours)) {
                same++;
                continue;
            }
            if (acceptable(theirs, ours)) updates.put(key, new JsonPrimitive(theirs));
            else rejected++;
        }
        return new Result(updates, same, rejected, vanished);
    }

    // lays downloaded patterns over the bundled ones, one log line says what it did
    public static void overlay(Map<String, JsonElement> raw) {
        if (DataFiles.olderThanJar(DataFiles.Source.SKYHANNI) || !DataFiles.isFresh(DataFiles.Source.SKYHANNI, FILE)) return;
        String text = DataFiles.text(DataFiles.Source.SKYHANNI, FILE);
        if (text == null) return;
        Map<String, JsonElement> open = new HashMap<>(raw);
        for (String key : DataFiles.pinnedRegexes()) open.remove(PREFIX + key);
        Result r = check(text, open);
        raw.putAll(r.updates());
        SbcMod.LOG.info("Downloaded patterns: {} new, {} the same, {} refused and {} not in that file, those keep the bundled one",
                r.updates().size(), r.same(), r.rejected(), r.vanished());
    }

    private static boolean isText(JsonElement e) {
        return e != null && e.isJsonPrimitive() && e.getAsJsonPrimitive().isString();
    }

    // compiles + has the named groups the bundled one has
    static boolean acceptable(String theirs, String ours) {
        Pattern t;
        try {
            t = Pattern.compile(theirs);
        } catch (PatternSyntaxException e) {
            return false;
        }
        try {
            return t.namedGroups().keySet().equals(Pattern.compile(ours).namedGroups().keySet());
        } catch (PatternSyntaxException e) {
            return true; // ours never matched anything so nothing to lose
        }
    }

    // repo way of matching plain text: color codes out of the pattern, null if that doesnt compile
    public static Pattern plain(String regex) {
        try {
            return Pattern.compile(regex.replaceAll("§.", ""));
        } catch (PatternSyntaxException e) {
            return null;
        }
    }
}
