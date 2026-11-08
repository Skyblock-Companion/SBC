// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.events.hoppity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import net.minecraft.world.phys.Vec3;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

// skyhanni's constants/HoppityEggLocations.json: factory menu slot indexes, employee slots + chocolate per
// sec, milestones, hitman costs, resident rabbits per island, special rabbit textures, every known egg spot
// per island (apiEggLocations, "x:y:z")
public final class HoppityData {
    public record Milestone(long amount, String rabbit) {}

    private static boolean loaded;
    private static Map<Integer, Integer> rabbitSlots = Map.of();
    private static Set<Integer> otherUpgradeSlots = Set.of(), noPickblockSlots = Set.of(), destructiveSlots = Set.of();
    public static int barnIndex = 35, infoIndex = 13, productionInfoIndex = 45, prestigeIndex = 27, milestoneIndex = 53, leaderboardIndex = 52,
            handCookieIndex = 37, timeTowerIndex = 38, shrineIndex = 39, coachRabbitIndex = 41, rabbitHitmanIndex = 51, maxRabbits = 577, maxPrestige = 6, cfShortcutIndex = 16;
    private static TreeSet<Long> chocolateMilestones = new TreeSet<>(), hitmanCosts = new TreeSet<>();
    private static List<Milestone> factoryMilestones = List.of(), shopMilestones = List.of();
    private static Map<String, List<String>> residentLocations = Map.of();
    private static Map<String, Map<String, Vec3>> apiEggLocations = Map.of();
    private static List<String> specialRabbits = List.of();

    private HoppityData() {}

    private static synchronized void load() {
        if (loaded) return;
        loaded = true;
        dev.zmuzy.sbc.core.data.DataFiles.read(dev.zmuzy.sbc.core.data.DataFiles.Source.SKYHANNI, "HoppityEggLocations.json", HoppityData::parse);
    }

    // every field gets written again when the jar copy is read after a downloaded one failed halfway
    private static void parse(JsonObject d) {
        Map<Integer, Integer> rs = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : d.getAsJsonObject("rabbitSlots").entrySet()) rs.put(Integer.parseInt(e.getKey()), e.getValue().getAsInt());
        rabbitSlots = Collections.unmodifiableMap(rs);
        otherUpgradeSlots = ints(d, "otherUpgradeSlots");
        noPickblockSlots = ints(d, "noPickblockSlots");
        destructiveSlots = ints(d, "destructiveSlots");
        barnIndex = d.get("barnIndex").getAsInt();
        infoIndex = d.get("infoIndex").getAsInt();
        productionInfoIndex = d.get("productionInfoIndex").getAsInt();
        prestigeIndex = d.get("prestigeIndex").getAsInt();
        milestoneIndex = d.get("milestoneIndex").getAsInt();
        leaderboardIndex = d.get("leaderboardIndex").getAsInt();
        handCookieIndex = d.get("handCookieIndex").getAsInt();
        timeTowerIndex = d.get("timeTowerIndex").getAsInt();
        shrineIndex = d.get("shrineIndex").getAsInt();
        coachRabbitIndex = d.get("coachRabbitIndex").getAsInt();
        rabbitHitmanIndex = d.get("rabbitHitmanIndex").getAsInt();
        maxRabbits = d.get("maxRabbits").getAsInt();
        maxPrestige = d.get("maxPrestige").getAsInt();
        cfShortcutIndex = d.get("cf_shortcut_index").getAsInt();
        TreeSet<Long> cm = new TreeSet<>();
        for (JsonElement e : d.getAsJsonArray("chocolateMilestones")) cm.add(e.getAsLong());
        chocolateMilestones = cm;
        TreeSet<Long> hc = new TreeSet<>();
        if (d.has("hitman_costs")) for (JsonElement e : d.getAsJsonArray("hitman_costs")) hc.add(e.getAsLong());
        hitmanCosts = hc;
        factoryMilestones = milestones(d, "chocolateFactoryMilestones");
        shopMilestones = milestones(d, "chocolateShopMilestones");
        Map<String, List<String>> res = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : d.getAsJsonObject("residentLocations").entrySet()) {
            List<String> names = new ArrayList<>();
            for (JsonElement n : e.getValue().getAsJsonArray()) names.add(n.getAsString());
            res.put(e.getKey(), names);
        }
        residentLocations = Collections.unmodifiableMap(res);
        Map<String, Map<String, Vec3>> eggs = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> e : d.getAsJsonObject("apiEggLocations").entrySet()) {
            Map<String, Vec3> m = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> l : e.getValue().getAsJsonObject().entrySet()) {
                String[] p = l.getValue().getAsString().split(":");
                m.put(l.getKey(), new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])));
            }
            eggs.put(e.getKey(), Collections.unmodifiableMap(m));
        }
        apiEggLocations = Collections.unmodifiableMap(eggs);
        List<String> sp = new ArrayList<>();
        if (d.has("specialRabbits")) for (JsonElement e : d.getAsJsonArray("specialRabbits")) sp.add(e.getAsString());
        specialRabbits = sp;
    }

    private static Set<Integer> ints(JsonObject d, String key) {
        Set<Integer> out = new java.util.LinkedHashSet<>();
        if (d.has(key)) for (JsonElement e : d.getAsJsonArray(key)) out.add(e.getAsInt());
        return Collections.unmodifiableSet(out);
    }

    private static List<Milestone> milestones(JsonObject d, String key) {
        List<Milestone> out = new ArrayList<>();
        if (d.has(key)) for (JsonElement e : d.getAsJsonArray(key)) {
            JsonObject o = e.getAsJsonObject();
            out.add(new Milestone(o.get("amount").getAsLong(), o.get("rabbit").getAsString()));
        }
        return Collections.unmodifiableList(out);
    }

    // employee slot -> raw chocolate per sec one level adds
    public static Map<Integer, Integer> rabbitSlots() {
        load();
        return rabbitSlots;
    }

    public static Set<Integer> otherUpgradeSlots() {
        load();
        return otherUpgradeSlots;
    }

    // factory slots that take you out of it
    public static Set<Integer> destructiveSlots() {
        load();
        return destructiveSlots;
    }

    // WarpMenuUniques: island name on the fast travel menu -> id, null if no eggs
    static String islandByName(String name) {
        String wanted = switch (name) {
            case "SkyBlock Hub" -> "HUB";
            case "The Barn" -> "THE_FARMING_ISLANDS";
            default -> {
                com.google.gson.JsonObject islands = dev.zmuzy.sbc.core.Repo.constant("IslandType.json");
                String id = null;
                if (islands != null && islands.has("islands")) for (Map.Entry<String, JsonElement> e : islands.getAsJsonObject("islands").entrySet()) {
                    var o = e.getValue().getAsJsonObject();
                    if (o.has("name") && o.get("name").getAsString().equals(name)) id = e.getKey();
                }
                yield id;
            }
        };
        load();
        return wanted != null && apiEggLocations.containsKey(wanted) ? wanted : null;
    }

    public static Set<Integer> noPickblockSlots() {
        load();
        return noPickblockSlots;
    }

    public static TreeSet<Long> chocolateMilestones() {
        load();
        return chocolateMilestones;
    }

    // CFApi.getNextMilestoneChocolate
    public static long nextMilestone(long allTime) {
        Long h = chocolateMilestones().higher(allTime);
        return h == null ? 0 : h;
    }

    public static TreeSet<Long> hitmanCosts() {
        load();
        return hitmanCosts;
    }

    public static List<Milestone> factoryMilestones() {
        load();
        return factoryMilestones;
    }

    public static List<Milestone> shopMilestones() {
        load();
        return shopMilestones;
    }

    // CFApi.milestoneByRabbit
    public static Milestone milestoneByRabbit(String rabbitName) {
        String clean = dev.zmuzy.sbc.core.GameState.strip(rabbitName);
        for (Milestone m : factoryMilestones()) if (dev.zmuzy.sbc.core.GameState.strip(m.rabbit()).equals(clean)) return m;
        for (Milestone m : shopMilestones()) if (dev.zmuzy.sbc.core.GameState.strip(m.rabbit()).equals(clean)) return m;
        return null;
    }

    public static Map<String, List<String>> residentLocations() {
        load();
        return residentLocations;
    }

    // island -> api name -> block pos
    public static Map<String, Map<String, Vec3>> apiEggLocations() {
        load();
        return apiEggLocations;
    }

    public static List<String> specialRabbits() {
        load();
        return specialRabbits;
    }

    // islands with eggs (HoppityApi.allowedHoppityIslands)
    public static Set<String> eggIslands() {
        return apiEggLocations().keySet();
    }

    static void indexesLoaded() {
        load();
    }
}
