// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.data.DataFiles.Source;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// lists for tab completion that skyblocker downloads from its own server (WarpAutocomplete,
// RngMeterAutocomplete, JoinInstanceAutocomplete, ChaptersAutocomplete). warps come from skyhanni
public final class CommandLists {
    private static final String FILE = "/assets/sbcompanion/data/command_lists.json";
    private static JsonObject own;

    private CommandLists() {}

    private static synchronized JsonObject own() {
        if (own == null) {
            own = new JsonObject();
            try (InputStream in = CommandLists.class.getResourceAsStream(FILE)) {
                if (in != null) own = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            } catch (Exception e) {
                SbcMod.LOG.warn("Could not read the command lists", e);
            }
        }
        return own;
    }

    private static List<String> strings(JsonElement array) {
        List<String> out = new ArrayList<>();
        if (array != null && array.isJsonArray()) for (JsonElement e : array.getAsJsonArray()) out.add(e.getAsString());
        return out;
    }

    // every warp, from skyhannis Warps.json
    public static List<String> warps() {
        JsonObject o = DataFiles.object(Source.SKYHANNI, "Warps.json");
        return o == null ? List.of() : strings(o.get("warpCommands"));
    }

    // from skyblocker RngMeterAutocomplete: /rng types + subtypes
    public static Map<String, List<String>> rngMeters() {
        Map<String, List<String>> out = new LinkedHashMap<>();
        JsonElement r = own().get("rng_meters");
        if (r != null && r.isJsonObject()) for (Map.Entry<String, JsonElement> e : r.getAsJsonObject().entrySet()) out.put(e.getKey(), strings(e.getValue()));
        return out;
    }

    // from skyblocker JoinInstanceAutocomplete: every /joininstance instance + type, catacombs or kuudra
    public static Map<String, String> joinInstances() {
        Map<String, String> out = new LinkedHashMap<>();
        JsonElement r = own().get("join_instances");
        if (r != null && r.isJsonObject()) for (Map.Entry<String, JsonElement> e : r.getAsJsonObject().entrySet()) out.put(e.getKey(), e.getValue().getAsString());
        return out;
    }

    // from skyblocker buildCommand filter, for /joindungeon + /joinkuudra
    public static List<String> joinInstances(String type) {
        List<String> out = new ArrayList<>();
        joinInstances().forEach((name, t) -> {
            if (t.equalsIgnoreCase(type)) out.add(name);
        });
        out.sort(null);
        return out;
    }

    // from skyblocker ChaptersAutocomplete: /chapter spots. empty for now, see the note in the file
    public static List<String> chapters() {
        return strings(own().get("chapters"));
    }
}
