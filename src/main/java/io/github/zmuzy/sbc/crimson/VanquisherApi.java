// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.crimson;

import io.github.zmuzy.sbc.core.Patterns;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// from skyhanni VanquisherApi: a vanquisher is yours when within 2s the "A Vanquisher is spawning nearby!"
// line, the wither spawn sound (pitch 1, volume 2) and a stand with a wither skull all show up within 3
// blocks of each other
// and the mob spawned within 4 blocks of that stand within 7s
public final class VanquisherApi {
    static final Patterns.P SPAWN = Patterns.p("SH.combat.crimsonisle.vanquisherapi.spawnpattern");

    // one vanquisher, key = the entity
    public static final class VanquisherData {
        public final boolean isOwn;
        public final int key;
        public Vec3 position;
        public final long spawnTime;
        boolean despawnSent;

        VanquisherData(boolean isOwn, int key, Vec3 position, long spawnTime) {
            this.isOwn = isOwn;
            this.key = key;
            this.position = position;
            this.spawnTime = spawnTime;
        }
    }

    static long lastOwnTime = CrimsonEnv.FAR_PAST, lastSpawnEntityTime = CrimsonEnv.FAR_PAST, lastSoundTime = CrimsonEnv.FAR_PAST;
    static Vec3 spawnEntityPos, lastSpawnEntityPos, lastSoundPos;
    static final List<VanquisherData> vanquishers = new ArrayList<>();

    static final long SHORT_TIMEOUT = 2_000, LONG_TIMEOUT = 5_000;

    static final List<Consumer<VanquisherData>> onSpawn = new ArrayList<>(), onDeath = new ArrayList<>(), onDespawn = new ArrayList<>();

    private VanquisherApi() {}

    public static boolean onChat(String clean) {
        if (!CrimsonEnv.inCrimson() || !SPAWN.matcher(clean.trim()).matches()) return false;
        lastOwnTime = CrimsonEnv.now();
        handleOwnVanquisher();
        return true;
    }

    // onPlaySound
    public static void onSound(String name, Vec3 pos, float volume, float pitch) {
        if (!CrimsonEnv.inCrimson() || !name.equals("entity.wither.spawn") || pitch != 1f || volume != 2f) return;
        lastSoundPos = pos;
        lastSoundTime = CrimsonEnv.now();
        handleOwnVanquisher();
    }

    // onEntityHealthUpdate: a stand wearing a wither skull showed up
    public static void onSkullStand(Vec3 pos) {
        if (!CrimsonEnv.inCrimson()) return;
        lastSpawnEntityPos = pos;
        lastSpawnEntityTime = CrimsonEnv.now();
        handleOwnVanquisher();
    }

    private static void handleOwnVanquisher() {
        if (lastSoundPos == null || lastSpawnEntityPos == null) return;
        long now = CrimsonEnv.now();
        if (now - lastSoundTime > SHORT_TIMEOUT || now - lastSpawnEntityTime > SHORT_TIMEOUT || now - lastOwnTime > SHORT_TIMEOUT) return;
        if (lastSoundPos.distanceTo(lastSpawnEntityPos) > 3) return;
        spawnEntityPos = lastSpawnEntityPos;
        lastSpawnEntityPos = null;
        lastSoundPos = null;
    }

    // onMobSpawn for a mob named Vanquisher
    public static VanquisherData onMobSpawn(int key, Vec3 pos) {
        for (VanquisherData d : vanquishers) if (d.key == key) return d;
        boolean isOwn = spawnEntityPos != null && pos.distanceTo(spawnEntityPos) <= 4 && CrimsonEnv.now() - lastOwnTime <= 7_000;
        VanquisherData data = new VanquisherData(isOwn, key, pos, CrimsonEnv.now());
        vanquishers.add(data);
        for (var l : onSpawn) l.accept(data);
        return data;
    }

    // onMobDeSpawn: mob gone, alive = was it still alive (false = died)
    public static void onMobDeSpawn(int key, boolean alive) {
        VanquisherData data = null;
        for (VanquisherData d : vanquishers) if (d.key == key) data = d;
        if (data == null) return;
        vanquishers.remove(data);
        postDespawn(data);
        if (!alive) for (var l : onDeath) l.accept(data);
    }

    private static void postDespawn(VanquisherData d) {
        if (d.despawnSent) return;
        d.despawnSent = true;
        for (var l : onDespawn) l.accept(d);
    }

    public static void onSecondPassed() {
        long now = CrimsonEnv.now();
        if ((lastSpawnEntityPos != null || lastSoundPos != null) && now - lastOwnTime > LONG_TIMEOUT) {
            lastSpawnEntityPos = null;
            lastSoundPos = null;
        }
        if (spawnEntityPos != null && now - lastOwnTime > 8_000) spawnEntityPos = null;
        // TimeLimitedCache 6 min
        for (VanquisherData d : new ArrayList<>(vanquishers)) if (now - d.spawnTime > 6 * 60_000) {
            vanquishers.remove(d);
            postDespawn(d);
        }
    }

    public static List<VanquisherData> vanquishers() {
        return vanquishers;
    }

    public static void onWorldChange() {
        for (VanquisherData d : vanquishers) postDespawn(d);
        vanquishers.clear();
        lastSpawnEntityPos = lastSoundPos = spawnEntityPos = null;
    }
}
