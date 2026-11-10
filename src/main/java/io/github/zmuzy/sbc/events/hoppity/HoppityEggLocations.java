// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events.hoppity;

import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

// from skyhanni HoppityEggLocations: known egg spots on your island + the ones this profile collected (saved
// as "x:y:z" per island)
public final class HoppityEggLocations {
    private static boolean foundAllOnThisIsland;

    private HoppityEggLocations() {}

    static String key(Vec3 v) {
        return (int) v.x + ":" + (int) v.y + ":" + (int) v.z;
    }

    // apiEggLocations[currentIsland].values
    public static Set<Vec3> islandLocations() {
        Map<String, Vec3> m = HoppityData.apiEggLocations().get(HoppityEnv.world.island());
        return m == null ? Set.of() : new LinkedHashSet<>(m.values());
    }

    public static Set<Vec3> islandCollectedLocations() {
        return getEggsIn(HoppityEnv.world.island());
    }

    public static Set<Vec3> getEggsIn(String island) {
        Set<String> keys = HoppityEnv.profile().collectedEggLocations.get(island);
        Set<Vec3> out = new HashSet<>();
        if (keys == null) return out;
        for (String k : keys) {
            String[] p = k.split(":");
            if (p.length == 3) out.add(new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2])));
        }
        return out;
    }

    public static boolean foundAllOnThisIsland() {
        return foundAllOnThisIsland;
    }

    public static void setFoundAll() {
        foundAllOnThisIsland = true;
    }

    public static void onIslandChange() {
        foundAllOnThisIsland = false;
    }

    public static boolean hasCollectedEgg(Vec3 location) {
        Set<String> keys = HoppityEnv.profile().collectedEggLocations.get(HoppityEnv.world.island());
        return keys != null && keys.contains(key(location));
    }

    // HoppityEggLocations.saveNearestEgg: nearest known spot (within 10 blocks) is collected now
    public static void saveNearestEgg() {
        Vec3 player = HoppityEnv.world.playerPos();
        Vec3 best = null;
        double bestD = Double.MAX_VALUE;
        for (Vec3 l : islandLocations()) {
            double d = l.distanceToSqr(player);
            if (d < bestD) {
                bestD = d;
                best = l;
            }
        }
        if (best == null) return;
        if (bestD > 100) {
            io.github.zmuzy.sbc.SbcMod.LOG.warn("Hoppity: player far from any known egg location (island {}, distSq {}, player {}, closest {})", HoppityEnv.world.island(), bestD, player, best);
        }
        saveEggLocation(HoppityEnv.world.island(), best);
    }

    public static void saveEggLocation(String island, Vec3 location) {
        HoppityEnv.profile().collectedEggLocations.computeIfAbsent(island, k -> new HashSet<>()).add(key(location));
    }
}
