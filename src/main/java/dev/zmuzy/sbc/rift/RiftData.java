// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.rift;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.render.ParkourHelper;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// skyhanni repo constants the rift features read, copied to assets/sbcompanion/data/sh/: RiftEffigies,
// EnigmaSouls, rift/RiftRace, rift/RiftWoodenButtons, lava maze parkour...
public final class RiftData {
    // from skyhanni RiftEffigiesJson.locations (6)
    public static List<Vec3> effigies = List.of();
    // from skyhanni EnigmaSoulsJson: area -> soul -> position
    public static Map<String, Map<String, Vec3>> soulLocations = Map.of();
    // from skyhanni RiftWoodenButtonsJson: "<house> House:<position>" -> buttons (key matches the graph node
    // "name:position")
    public static Map<String, List<Vec3>> buttonLocations = Map.of();
    public static ParkourHelper.Data riftRace, lavaMaze, upsideDown, tubulator;

    private static boolean loaded;

    private RiftData() {}

    private static JsonObject read(String file) {
        try (var in = dev.zmuzy.sbc.core.data.DataFiles.sh(file)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            SbcMod.LOG.warn("Bad Rift data {}", file, e);
            return null;
        }
    }

    static List<Vec3> vecs(JsonArray a) {
        List<Vec3> out = new ArrayList<>();
        if (a != null) for (JsonElement e : a) out.add(RiftEnv.vec(e.getAsString()));
        return out;
    }

    // from skyhanni ParkourJson: locations + shortCuts (from, to)
    static ParkourHelper.Data parkour(JsonObject o) {
        if (o == null) return null;
        List<Vec3> locations = vecs(o.getAsJsonArray("locations"));
        List<ParkourHelper.ShortCut> cuts = new ArrayList<>();
        if (o.has("shortCuts")) for (JsonElement e : o.getAsJsonArray("shortCuts")) {
            JsonObject c = e.getAsJsonObject();
            cuts.add(new ParkourHelper.ShortCut(c.get("from").getAsInt(), c.get("to").getAsInt()));
        }
        return new ParkourHelper.Data(locations, cuts);
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        JsonObject o = read("RiftEffigies.json");
        if (o != null) effigies = vecs(o.getAsJsonArray("locations"));
        o = read("EnigmaSouls.json");
        if (o != null) {
            Map<String, Map<String, Vec3>> souls = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> area : o.getAsJsonObject("areas").entrySet()) {
                Map<String, Vec3> m = new LinkedHashMap<>();
                for (JsonElement s : area.getValue().getAsJsonArray()) {
                    JsonObject so = s.getAsJsonObject();
                    m.put(so.get("name").getAsString(), RiftEnv.vec(so.get("position").getAsString()));
                }
                souls.put(area.getKey(), m);
            }
            soulLocations = souls;
        }
        o = read("rift/RiftWoodenButtons.json");
        if (o != null) {
            Map<String, List<Vec3>> buttons = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> house : o.getAsJsonObject("houses").entrySet())
                for (JsonElement spot : house.getValue().getAsJsonArray()) {
                    JsonObject so = spot.getAsJsonObject();
                    // from skyhanni WoodenButtonsHelper: key "$houseName House:${spot.position}", position prints as "x:y:z" of doubles
                    buttons.put(house.getKey() + " House:" + so.get("position").getAsString(), vecs(so.getAsJsonArray("buttons")));
                }
            buttonLocations = buttons;
        }
        riftRace = parkour(read("rift/RiftRace.json"));
        lavaMaze = parkour(read("RiftLavaMazeParkour.json"));
        ParkourHelper.Data ud = parkour(read("RiftUpsideDownParkour.json"));
        if (ud != null) {
            // from skyhanni RiftUpsideDownParkour: data.locations.map { it.add(-1.0, -1.0, -1.0) }
            List<Vec3> shifted = new ArrayList<>();
            for (Vec3 v : ud.locations()) shifted.add(v.add(-1, -1, -1));
            ud = new ParkourHelper.Data(shifted, ud.shortCuts());
        }
        upsideDown = ud;
        tubulator = parkour(read("RiftTubulator.json"));
    }
}
