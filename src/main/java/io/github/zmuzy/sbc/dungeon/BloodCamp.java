// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.dungeon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// from skyblocker dungeon/BloodCampHelper: where each blood mob the watcher sends out stops. draws through
// walls so it starts off
public final class BloodCamp {
    private BloodCamp() {}

    private static final Set<String> WATCHER = new HashSet<>(), MOBS = new HashSet<>();
    private static final Set<Integer> watchers = new HashSet<>();
    private static final Map<Integer, Tracked> tracked = new HashMap<>();
    private static int predicted;
    private static boolean loaded;

    // from skyblocker BloodCampHelper.TrackedMob
    static final class Tracked {
        static final int SAMPLES = 5;
        final Vec3 start;
        Vec3 last, target;
        final Deque<Vec3> deltas = new ArrayDeque<>();

        Tracked(Vec3 start) {
            this.start = start;
            this.last = start;
        }

        // true = this move gave the mob its prediction
        boolean update(Vec3 now, boolean firstWave) {
            Vec3 d = now.subtract(last);
            last = now;
            if (d.lengthSqr() > 0) {
                if (deltas.size() == SAMPLES) deltas.removeFirst();
                deltas.addLast(d);
            }
            if (deltas.size() < SAMPLES || target != null) return false;
            Vec3 total = Vec3.ZERO;
            for (Vec3 x : deltas) total = total.add(x);
            target = start.add(total.normalize().scale(firstWave ? 15.6 + 0.5 : 11.4 + 0.5));
            return true;
        }
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        try (var in = BloodCamp.class.getResourceAsStream("/sbc/dungeon_heads.json")) {
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : o.getAsJsonArray("watcher")) WATCHER.add(e.getAsString());
            for (JsonElement e : o.getAsJsonArray("blood_mobs")) MOBS.add(e.getAsString());
        } catch (Exception ex) {
            SbcMod.LOG.warn("Could not read dungeon heads", ex);
        }
    }

    // from skyblocker BloodCampHelper.reset, on join + disconnect. predicted picks the first waves distance so it
    // cant carry into the next run
    private static void reset() {
        tracked.clear();
        watchers.clear();
        predicted = 0;
    }

    static int predicted() {
        return predicted;
    }

    static void predictedForTest(int n) {
        predicted = n;
    }

    private static void tick(Minecraft mc) {
        if (!DungeonRun.inDungeon() || mc.level == null) return;
        load();
        for (Entity e : mc.level.entitiesForRendering()) {
            if (e instanceof Zombie z && !watchers.contains(z.getId()) && z.hasItemInSlot(EquipmentSlot.HEAD) && WATCHER.contains(DungeonMarks.headTexture(z.getItemBySlot(EquipmentSlot.HEAD)))) watchers.add(z.getId());
        }
        for (var it = watchers.iterator(); it.hasNext(); ) {
            Entity w = mc.level.getEntity(it.next());
            if (w == null || !w.isAlive()) {
                it.remove();
                continue;
            }
            for (ArmorStand st : mc.level.getEntitiesOfClass(ArmorStand.class, w.getBoundingBox().inflate(2), s -> s.hasItemInSlot(EquipmentSlot.HEAD) && !tracked.containsKey(s.getId()))) {
                if (MOBS.contains(DungeonMarks.headTexture(st.getItemBySlot(EquipmentSlot.HEAD)))) tracked.put(st.getId(), new Tracked(st.position()));
            }
        }
        for (var it = tracked.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            Entity st = mc.level.getEntity(en.getKey());
            if (st == null || !st.isAlive()) {
                it.remove();
                continue;
            }
            if (en.getValue().update(st.position(), predicted < 4)) predicted++;
        }
    }

    private static void draw(WorldDraw w) {
        for (var en : tracked.entrySet()) {
            Vec3 t = en.getValue().target;
            Entity st = w.mc.level.getEntity(en.getKey());
            if (t == null || st == null) continue;
            w.box(st.getBoundingBox().move(0, 2, 0), 0xFFFFFF00, false, true);
            w.line(st.position().add(0, 2, 0), t.add(0, 2, 0), 0xFFFFFF00, 2f, true);
            w.box(new AABB(t.x - 0.5, t.y + 2, t.z - 0.5, t.x + 0.5, t.y + 4, t.z + 0.5), 0xFFFF0000, false, true);
        }
    }

    public static void register() {
        Features.add(new Feature("dungeon_blood_camp", "Dungeons", "Blood camp helper", "Shows where each blood mob will stop once it starts moving, so you can wait there.", "Skyblocker: Blood Camp Helper")
                .when(() -> DungeonRun.inDungeon() || SbcMod.DEV).tick(BloodCamp::tick).world(BloodCamp::draw).left(BloodCamp::reset).defaultOff());
    }
}
