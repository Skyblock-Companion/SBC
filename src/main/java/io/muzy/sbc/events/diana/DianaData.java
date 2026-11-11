// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, NotEnoughUpdates; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.diana;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.GameState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// skyhanni data repo stuff diana reads, bundled in assets/sbcompanion/data/sh/
// events/Diana.json (spade_types, mythological_mobs, sphinx_questions), Warps.json (warpLocation, for
// BurrowWarpHelper), misc/IslandType.json (hub bounds for GriffinBurrowHelper/ArrowGuessBurrow via
// IslandType.HUB.isInBounds)
public final class DianaData {
    // skyhanni MythologicalCreatureType: name (with colour), rare, short_names. cleanName/trackerId/mobAliases
    // derived the same way
    public record Creature(String key, String name, boolean rare, List<String> shortNames) {
        public String cleanName() {
            return GameState.strip(name);
        }

        public String trackerId() {
            return cleanName().replace(" ", "_").toUpperCase(Locale.ROOT);
        }

        public List<String> mobAliases() {
            List<String> out = new ArrayList<>();
            out.add(cleanName().toLowerCase(Locale.ROOT));
            out.addAll(shortNames);
            return out;
        }
    }

    // skyhanni WarpLocationData: display_name, island, x/y/z, extra_diana_warp_blocks, commands
    public record Warp(String key, String displayName, String island, Vec3 pos, int extraDianaWarpBlocks, List<String> commands) {}

    private static Set<String> spades;
    private static Map<String, Creature> creatures;
    private static Map<String, String> sphinx;
    private static Map<String, Warp> warps;
    private static Map<String, AABB> bounds;

    private DianaData() {}

    private static JsonObject read(String file) {
        try (InputStream in = io.muzy.sbc.core.data.DataFiles.sh(file)) {
            if (in == null) return new JsonObject();
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            SbcMod.LOG.warn("No data file {}", file, e);
            return new JsonObject();
        }
    }

    private static synchronized void loadDiana() {
        if (spades != null) return;
        if (!io.muzy.sbc.core.data.DataFiles.read(io.muzy.sbc.core.data.DataFiles.Source.SKYHANNI, "Diana.json", DianaData::parseDiana)) {
            creatures = new LinkedHashMap<>();
            sphinx = new LinkedHashMap<>();
            spades = new java.util.LinkedHashSet<>();
        }
    }

    // assigns at the end so a file that dies halfway leaves nothing behind
    private static void parseDiana(JsonObject root) {
        Set<String> sp = new java.util.LinkedHashSet<>();
        if (root.has("spade_types")) for (JsonElement e : root.getAsJsonArray("spade_types")) sp.add(e.getAsString());
        Map<String, Creature> cr = new LinkedHashMap<>();
        if (root.has("mythological_mobs")) for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("mythological_mobs").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            List<String> shorts = new ArrayList<>();
            if (o.has("short_names")) for (JsonElement s : o.getAsJsonArray("short_names")) shorts.add(s.getAsString());
            cr.put(e.getKey(), new Creature(e.getKey(), o.has("name") ? o.get("name").getAsString() : e.getKey(), o.has("rare") && o.get("rare").getAsBoolean(), shorts));
        }
        Map<String, String> sq = new LinkedHashMap<>();
        if (root.has("sphinx_questions")) for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("sphinx_questions").entrySet())
            if (!e.getKey().startsWith("//")) sq.put(e.getKey(), e.getValue().getAsString());
        creatures = cr;
        sphinx = sq;
        spades = sp;
    }

    private static synchronized void loadWarps() {
        if (warps != null) return;
        JsonObject root = read("Warps.json");
        Map<String, Warp> out = new LinkedHashMap<>();
        if (root.has("warpLocation")) for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("warpLocation").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            List<String> cmds = new ArrayList<>();
            if (o.has("commands")) for (JsonElement c : o.getAsJsonArray("commands")) cmds.add(c.getAsString());
            out.put(e.getKey().toUpperCase(Locale.ROOT), new Warp(e.getKey(), o.has("display_name") ? o.get("display_name").getAsString() : e.getKey(),
                    o.has("island") ? o.get("island").getAsString() : "", new Vec3(o.get("x").getAsDouble(), o.get("y").getAsDouble(), o.get("z").getAsDouble()),
                    o.has("extra_diana_warp_blocks") ? o.get("extra_diana_warp_blocks").getAsInt() : 0, cmds));
        }
        warps = out;
    }

    private static synchronized void loadIslands() {
        if (bounds != null) return;
        JsonObject root = read("IslandType.json");
        Map<String, AABB> out = new LinkedHashMap<>();
        if (root.has("islands")) for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("islands").entrySet()) {
            JsonObject o = e.getValue().getAsJsonObject();
            if (!o.has("bounds")) continue;
            JsonObject b = o.getAsJsonObject("bounds");
            // IslandType.onRepoReload: AABB(minX, 0, minZ, maxX, 256, maxZ)
            out.put(e.getKey(), new AABB(b.get("min_x").getAsDouble(), 0, b.get("min_z").getAsDouble(), b.get("max_x").getAsDouble(), 256, b.get("max_z").getAsDouble()));
        }
        bounds = out;
    }

    // neu ids of the diana spades (ANCESTRAL_SPADE, ...)
    public static Set<String> spades() {
        loadDiana();
        return spades;
    }

    public static Map<String, Creature> creatures() {
        loadDiana();
        return creatures;
    }

    public static Map<String, String> sphinxQuestions() {
        loadDiana();
        return sphinx;
    }

    // warps by upper case repo key (HUB, CASTLE, CRYPT, DA, MUSEUM, WIZARD, STONKS, TAYLOR, ...)
    public static Map<String, Warp> warps() {
        loadWarps();
        return warps;
    }

    // "/warp <command>" of the warp on island closest to pos, null if the island has none
    public static String closestWarpCommand(String island, Vec3 pos) {
        Warp best = null;
        for (Warp w : warps().values()) {
            if (!w.island().equalsIgnoreCase(island) || w.commands().isEmpty()) continue;
            if (best == null || w.pos().distanceToSqr(pos) < best.pos().distanceToSqr(pos)) best = w;
        }
        return best == null ? null : "/warp " + best.commands().get(0);
    }

    // island box from the repo, null if none (then everything counts as inside)
    public static AABB islandBounds(String island) {
        loadIslands();
        return bounds.get(island);
    }

    // IslandType.isInBounds: inside the repo box, or true if the island has no box
    public static boolean inBounds(String island, Vec3 v) {
        AABB b = islandBounds(island);
        return b == null || b.contains(v);
    }
}
