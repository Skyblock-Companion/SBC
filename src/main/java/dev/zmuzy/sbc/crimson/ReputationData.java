// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, NotEnoughUpdates; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.crimson;

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

// skyhanni's CrimsonIsleReputation.json (FISHING, RESCUE, FETCH, DOJO, MINIBOSS, KUUDRA: name -> item +
// location) and RescueMissionWaypoints.json (barb/mage -> tier -> parkour), copied to
// assets/sbcompanion/data/sh/
public final class ReputationData {
    // ReputationQuest: display item (neu internal name) + location (empty = none)
    public record Quest(String item, Vec3 location) {}

    // category -> quest name -> quest
    public static Map<String, Map<String, Quest>> categories = Map.of();
    // faction ("barb"/"mage") -> tier ("S1","S2","A".."D") -> parkour spots
    public static Map<String, Map<String, List<Vec3>>> rescue = Map.of();

    private static boolean loaded;

    private ReputationData() {}

    private static JsonObject read(String file) {
        try (var in = dev.zmuzy.sbc.core.data.DataFiles.sh(file)) {
            if (in == null) return null;
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            SbcMod.LOG.warn("Bad Crimson data {}", file, e);
            return null;
        }
    }

    // CrimsonIsleReputationHelper.readLocationData: [x, y, z] -> pos minus (1, 0, 1), empty -> null
    static Vec3 readLocationData(JsonArray a) {
        if (a == null || a.isEmpty()) return null;
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble()).add(-1, 0, -1);
    }

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        JsonObject o = read("CrimsonIsleReputation.json");
        if (o != null) {
            Map<String, Map<String, Quest>> cats = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> cat : o.entrySet()) {
                Map<String, Quest> m = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> q : cat.getValue().getAsJsonObject().entrySet()) {
                    JsonObject qo = q.getValue().getAsJsonObject();
                    m.put(q.getKey(), new Quest(qo.get("item").getAsString(), readLocationData(qo.getAsJsonArray("location"))));
                }
                cats.put(cat.getKey(), m);
            }
            categories = cats;
        }
        o = read("RescueMissionWaypoints.json");
        if (o != null) {
            Map<String, Map<String, List<Vec3>>> r = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> faction : o.entrySet()) {
                Map<String, List<Vec3>> tiers = new LinkedHashMap<>();
                for (Map.Entry<String, JsonElement> tier : faction.getValue().getAsJsonObject().entrySet()) {
                    List<Vec3> locs = new ArrayList<>();
                    for (JsonElement e : tier.getValue().getAsJsonArray()) locs.add(CrimsonEnv.vec(e.getAsString()));
                    tiers.put(tier.getKey(), locs);
                }
                r.put(faction.getKey(), tiers);
            }
            rescue = r;
        }
    }

    public static Map<String, Quest> category(String name) {
        load();
        return categories.getOrDefault(name, Map.of());
    }

    public static ParkourHelper.Data rescueParkour(String faction, String tier) {
        load();
        Map<String, List<Vec3>> f = rescue.get(faction);
        if (f == null) return null;
        List<Vec3> l = f.get(tier);
        return l == null ? null : new ParkourHelper.Data(l, List.of());
    }
}
