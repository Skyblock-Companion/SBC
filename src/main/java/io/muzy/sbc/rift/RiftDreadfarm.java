// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.rift;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.data.DataFiles;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.mc.Interactions;
import io.muzy.sbc.mc.ParticleHooks;
import io.muzy.sbc.mc.SoundHooks;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// the dreadfarm, from skyhanni rift/area/dreadfarm: RiftAgaricusCap.kt, VoltHighlighter.kt,
// RiftWiltedBerberisHelper.kt + its respawn sequence (rift/WiltedBerberisLocations.json)
public final class RiftDreadfarm {
    public static final String ID = "rift_dreadfarm", BERBERIS = "rift_berberis";
    private static final String FARMING_TOOL = "FARMING_WAND";
    private static final Set<String> BERBERIS_SOUNDS = Set.of("entity.donkey.death", "entity.donkey.hurt");
    private static final double LIGHTNING_DISTANCE = 7;
    private static final long CHARGE_TIME = 12_000;

    private RiftDreadfarm() {}

    private static boolean on(String key, boolean def) {
        return RiftScan.on(ID, key, def);
    }

    private static boolean berberis(String key, boolean def) {
        return RiftScan.on(BERBERIS, key, def);
    }

    // --- agaricus cap ---

    private static long capStart = Long.MIN_VALUE;
    private static final long CAP_RED = Long.MAX_VALUE;
    private static BlockPos capAt;

    private static void tickCap(Minecraft mc) {
        capAt = updateCap(mc);
    }

    private static BlockPos updateCap(Minecraft mc) {
        if (!RiftScan.heldId(mc).equals(FARMING_TOOL)) return null;
        HitResult hit = mc.hitResult;
        if (!(hit instanceof BlockHitResult b) || hit.getType() != HitResult.Type.BLOCK) return null;
        BlockPos pos = b.getBlockPos();
        var block = mc.level.getBlockState(pos).getBlock();
        if (block == Blocks.BROWN_MUSHROOM) {
            if (!pos.equals(capAt) || capStart == CAP_RED) capStart = System.currentTimeMillis();
            return pos;
        }
        if (block == Blocks.RED_MUSHROOM && pos.equals(capAt)) {
            capStart = CAP_RED;
            return capAt;
        }
        return null;
    }

    private static void drawCap(WorldDraw w) {
        if (capAt == null) return;
        Vec3 at = Vec3.atLowerCornerOf(capAt).add(0, 0.6, 0);
        if (capStart == CAP_RED) w.dynamicText(at, "§cClick!", 1.5, 0);
        else w.dynamicText(at, "§b" + RiftScan.seconds(System.currentTimeMillis() - capStart), 1.5, 0);
    }

    // --- volt ---

    private enum Volt { FRIENDLY(0x8000FF00), HOSTILE(0x80FF0000), DOING_LIGHTNING(0x800000FF);
        final int color;

        Volt(int color) {
            this.color = color;
        }
    }

    private static final Map<ArmorStand, Volt> VOLTS = new HashMap<>();
    private static final Map<ArmorStand, Long> CHARGING = new HashMap<>();

    private static Volt voltState(ArmorStand st) {
        String tex = RiftScan.texture(st.getItemBySlot(EquipmentSlot.HEAD));
        if (tex == null) return null;
        if (tex.equals(RiftScan.skull("VOLT_DOING_LIGHTNING"))) return Volt.DOING_LIGHTNING;
        if (tex.equals(RiftScan.skull("VOLT_FRIENDLY"))) return Volt.FRIENDLY;
        if (tex.equals(RiftScan.skull("VOLT_HOSTILE"))) return Volt.HOSTILE;
        return null;
    }

    private static void tickVolts(Minecraft mc) {
        if (mc.player.tickCount % 5 != 0) return;
        Map<ArmorStand, Volt> seen = new HashMap<>();
        for (ArmorStand st : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(48))) {
            Volt v = voltState(st);
            if (v == null) continue;
            seen.put(st, v);
            // from skyhanni onArmorChange: head turned into the lightning one near you
            if (v == Volt.DOING_LIGHTNING && VOLTS.get(st) != Volt.DOING_LIGHTNING && st.distanceToSqr(mc.player) <= LIGHTNING_DISTANCE * LIGHTNING_DISTANCE)
                CHARGING.put(st, System.currentTimeMillis());
        }
        VOLTS.clear();
        VOLTS.putAll(seen);
        CHARGING.keySet().removeIf(st -> !seen.containsKey(st));
    }

    private static void drawVolts(WorldDraw w) {
        boolean mood = on("rift_dreadfarm.volt_mood", false), range = on("rift_dreadfarm.volt_range", true), warning = on("rift_dreadfarm.volt_warning", true);
        long now = System.currentTimeMillis();
        for (var e : VOLTS.entrySet()) {
            ArmorStand st = e.getKey();
            if (st.isRemoved()) continue;
            if (mood) w.box(st.getBoundingBox(), e.getValue().color, true, false);
            if (e.getValue() != Volt.DOING_LIGHTNING) continue;
            Vec3 p = st.position();
            if (range) RiftScan.cylinder(w, p.add(0, -4, 0), LIGHTNING_DISTANCE, 20, RiftScan.color(ID, "rift_dreadfarm.volt_color", 0x3C0000FF));
            Long since = CHARGING.get(st);
            if (warning && since != null && now - since < CHARGE_TIME) w.dynamicText(p.add(0, 2.5, 0), "§eLightning: " + RiftScan.seconds(CHARGE_TIME - (now - since)), 2.5, 0);
        }
    }

    // --- wilted berberis ---

    // from skyhanni WiltedBerberis: where its sparks are
    private static final class Plant {
        Vec3 particles;
        Vec3 previous;
        boolean moving = true;
        double y;
        long lastTime;

        Plant(Vec3 particles, long now) {
            this.particles = particles;
            lastTime = now;
        }
    }

    // from skyhanni BerberisSequence: the order a fields plants come back in, learned from their respawn sparks
    static final class Sequence {
        final int expected;
        final List<Vec3> blocks = new ArrayList<>();
        boolean valid, rendering, away;
        int index;
        Vec3 previousTarget;
        long lastParticle = Long.MIN_VALUE / 4;

        Sequence(int expected) {
            this.expected = expected;
        }

        void reset() {
            blocks.clear();
            valid = false;
            rendering = false;
            index = 0;
            previousTarget = null;
            lastParticle = Long.MIN_VALUE / 4;
        }

        void advance() {
            Vec3 current = current();
            index++;
            if (index >= blocks.size()) {
                boolean wasRendering = rendering, wasValid = valid;
                reset();
                if (wasValid) rendering = wasRendering;
            } else previousTarget = current;
        }

        Vec3 current() {
            return RiftScan.at(blocks, index);
        }

        boolean isAt(Vec3 block) {
            Vec3 c = current();
            return c != null && c.equals(block);
        }

        // from skyhanni handleRespawnParticle after the block checks. true = the whole order is known now
        boolean learn(Vec3 block, long now) {
            if (valid) return false;
            if (now - lastParticle > 3000) blocks.clear();
            lastParticle = now;
            if (!blocks.isEmpty() && blocks.get(blocks.size() - 1).equals(block)) return false;
            blocks.add(block);
            if (blocks.size() != expected) return false;
            valid = true;
            rendering = true;
            return true;
        }
    }

    private static final List<Plant> PLANTS = new ArrayList<>();
    private static final Map<Vec3, Sequence> FIELDS = new LinkedHashMap<>();
    private static boolean fieldsLoaded, onFarmland, hasTool;

    private static void loadFields() {
        if (fieldsLoaded) return;
        fieldsLoaded = true;
        try (var in = DataFiles.sh("rift/WiltedBerberisLocations.json")) {
            if (in == null) return;
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : o.getAsJsonArray("field_centers")) {
                JsonObject f = e.getAsJsonObject();
                FIELDS.put(RiftEnv.vec(f.get("position").getAsString()), new Sequence(f.get("count").getAsInt()));
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Bad Wilted Berberis data", e);
        }
    }

    private static Vec3 block(Vec3 v) {
        return new Vec3(Math.floor(v.x), Math.floor(v.y), Math.floor(v.z));
    }

    private static double flat(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Plant nearestPlant(Vec3 at) {
        Plant best = null;
        double bestD = 8;
        for (Plant p : PLANTS) {
            double d = p.particles.distanceToSqr(at);
            if (d < bestD) {
                best = p;
                bestD = d;
            }
        }
        return best;
    }

    private static void tickBerberis(Minecraft mc) {
        if (mc.player.tickCount % 5 != 0) return;
        long now = System.currentTimeMillis();
        PLANTS.removeIf(p -> now - p.lastTime > 500);
        hasTool = RiftScan.heldId(mc).equals(FARMING_TOOL);
        if (mc.player.onGround()) onFarmland = mc.level.getBlockState(mc.player.blockPosition().below()).is(Blocks.FARMLAND);
        if (!berberis("rift_berberis.sequence", true)) return;
        Vec3 me = mc.player.position();
        for (var e : FIELDS.entrySet()) {
            Sequence s = e.getValue();
            if (s.blocks.isEmpty()) continue;
            if (flat(e.getKey(), me) > 20) {
                s.away = true;
                continue;
            }
            if (s.away) {
                s.away = false;
                validate(mc, s);
            }
        }
    }

    // from skyhanni validateSequence + validatePartialSequence: bushes must still stand where the order says
    private static void validate(Minecraft mc, Sequence s) {
        for (int i = 0; i < s.blocks.size(); i++) {
            boolean bush = mc.level.getBlockState(BlockPos.containing(s.blocks.get(i))).is(Blocks.DEAD_BUSH);
            if (!s.valid) {
                if (!bush) {
                    s.blocks.clear();
                    return;
                }
                continue;
            }
            if (bush != (i >= s.index)) {
                s.reset();
                if (mc.player != null) RiftEnv.world.chat("§cBerberis sequence invalid, falling back to particle mode");
                return;
            }
        }
    }

    // the order is kept like skyhanni keeps it and checked against the bushes when you come back to the field
    private static void leaveBerberis() {
        PLANTS.clear();
        hasTool = onFarmland = false;
        for (Sequence s : FIELDS.values()) if (!s.blocks.isEmpty()) s.away = true;
    }

    private static void onParticle(ParticleHooks.Particle ev) {
        if (!RiftApi.inDreadfarm() || !Features.on(BERBERIS)) return;
        loadFields();
        if (!hasTool) return;
        Vec3 at = new Vec3(ev.x(), ev.y(), ev.z());
        long now = System.currentTimeMillis();
        if (!ev.is("minecraft:firework")) {
            if (ev.is("minecraft:happy_villager")) onRespawnSpark(at, now);
            return;
        }
        Plant plant = nearestPlant(at);
        if (plant == null) {
            PLANTS.add(new Plant(at, now));
            return;
        }
        boolean moved = !plant.particles.equals(at);
        if (moved) {
            if (plant.particles.distanceTo(at) > 3) {
                plant.previous = null;
                plant.moving = true;
            }
            if (!plant.moving) plant.previous = plant.particles;
        } else plant.y = at.y - 1;
        plant.moving = moved;
        plant.particles = at;
        plant.lastTime = now;
    }

    private static void onRespawnSpark(Vec3 at, long now) {
        if (!berberis("rift_berberis.sequence", true)) return;
        var mc = Minecraft.getInstance();
        Vec3 b = block(at);
        if (mc.level == null || !mc.level.getBlockState(BlockPos.containing(b).below()).is(Blocks.FARMLAND)) return;
        Vec3 center = null;
        double best = 50;
        for (Vec3 c : FIELDS.keySet()) {
            double d = flat(c, b);
            if (d < best) {
                best = d;
                center = c;
            }
        }
        Sequence s = center == null ? null : FIELDS.get(center);
        if (s != null && s.learn(b, now)) RiftEnv.world.chat("§aBerberis respawn sequence learned!");
    }

    // from skyhanni onParticle, the hiding half
    public static boolean hidesParticle(String type, double x, double y, double z) {
        if (!RiftApi.inDreadfarm() || !hasTool || !Features.on(BERBERIS) || !berberis("rift_berberis.hide_particles", false)) return false;
        return type.equals("firework") || nearestPlant(new Vec3(x, y, z)) != null;
    }

    private static boolean onSound(SoundHooks.Sound s) {
        if (!Rift.rift() || !BERBERIS_SOUNDS.contains(s.name()) || !Features.on(BERBERIS) || !berberis("rift_berberis.mute_others", false)) return false;
        return (RiftApi.inDreadfarm() || RiftApi.inWestVillage()) && !(hasTool && onFarmland);
    }

    private static void advanceAt(Vec3 b) {
        for (Sequence s : FIELDS.values()) {
            if (!s.rendering || s.away || !s.isAt(b)) continue;
            s.advance();
            return;
        }
    }

    public static void onBlockChange(BlockPos pos, String old, String now) {
        if (old.equals("dead_bush") && now.equals("air") && RiftApi.inDreadfarm() && Features.on(BERBERIS) && berberis("rift_berberis.sequence", true))
            advanceAt(Vec3.atLowerCornerOf(pos));
    }

    private static AABB plantBox(Vec3 at) {
        return new AABB(at.x + 0.1, at.y - 0.1, at.z + 0.1, at.x + 0.9, at.y + 0.9, at.z + 0.9).inflate(0.002);
    }

    private static Vec3 center(Vec3 v) {
        return v.add(0.5, 0.5, 0.5);
    }

    private static void drawSequence(WorldDraw w, Sequence s) {
        Vec3 current = s.current(), next = RiftScan.at(s.blocks, s.index + 1), third = RiftScan.at(s.blocks, s.index + 2);
        w.filledBox(plantBox(current), 0x55FF55, 0.7f, false);
        w.dynamicText(current.add(0, 1, 0), "§aWilted Berberis", 1.5, 0);
        if (s.previousTarget != null) w.line(center(s.previousTarget), center(current), 0xFF55FF55, 3f, false);
        if (next == null) return;
        w.filledBox(plantBox(next), 0xFFFF55, 0.5f, false);
        w.line(center(current), center(next), 0xFFFFFF55, 3f, false);
        if (third == null) return;
        w.filledBox(plantBox(third), 0xFF5555, 0.3f, false);
        w.line(center(next), center(third), 0xFFFF5555, 3f, false);
    }

    private static Vec3 fix(Vec3 v, double y) {
        return new Vec3(v.x - 0.5, y, v.z - 0.5);
    }

    private static void drawPlants(WorldDraw w) {
        Vec3 eye = w.eye;
        for (Plant p : PLANTS) {
            if (p.particles.distanceTo(eye) > 20 || p.y == 0) continue;
            Vec3 at = fix(p.particles, p.y);
            if (!p.moving) {
                w.filledBox(plantBox(at), 0xFFFF55, 0.7f, false);
                w.dynamicText(at.add(0, 1, 0), "§eWilted Berberis", 1.5, 0);
                continue;
            }
            w.filledBox(plantBox(at), 0xFFFFFF, 0.5f, false);
            if (p.previous == null) continue;
            Vec3 prev = fix(p.previous, p.y);
            w.filledBox(plantBox(prev), 0xAAAAAA, 0.2f, false);
            w.line(prev.add(0.5, 0, 0.5), at.add(0.5, 0, 0.5), 0xFFFFFFFF, 3f, false);
        }
    }

    private static void drawBerberis(WorldDraw w) {
        if (!RiftApi.inDreadfarm() || !hasTool) return;
        if (berberis("rift_berberis.only_farmland", false) && !onFarmland) return;
        boolean guiding = false;
        if (berberis("rift_berberis.sequence", true)) {
            for (Sequence s : FIELDS.values()) {
                if (!s.rendering || s.away || s.current() == null) continue;
                drawSequence(w, s);
                guiding = true;
            }
        }
        if (!guiding) drawPlants(w);
    }

    public static void register() {
        Features.add(new Feature(ID, "Rift", "Dreadfarm", "Counts how long an Agaricus Cap has grown while you look at it with the Wand of Farming, and shows a Volt's strike range and the time until it strikes.", "SkyHanni: Dreadfarm")
                .when(Rift::rift)
                .tick(mc -> {
                    if (mc.player == null || mc.level == null) return;
                    String area = io.muzy.sbc.core.GameState.area;
                    if (on("rift_dreadfarm.agaricus", true) && (area.equals("Dreadfarm") || area.equals("West Village"))) tickCap(mc);
                    else capAt = null;
                    tickVolts(mc);
                })
                .world(w -> {
                    if (on("rift_dreadfarm.agaricus", true)) drawCap(w);
                    drawVolts(w);
                })
                .left(() -> {
                    capAt = null;
                    VOLTS.clear();
                    CHARGING.clear();
                })
                .option(Option.bool("rift_dreadfarm.agaricus", "Agaricus Cap timer", "The time since the mushroom was brown, and Click! once it is red.", true))
                .option(Option.bool("rift_dreadfarm.volt_warning", "Volt warning", "Counts down while a Volt near you charges its lightning.", true))
                .option(Option.bool("rift_dreadfarm.volt_range", "Volt range", "The area a charging Volt can strike.", true))
                .option(Option.color("rift_dreadfarm.volt_color", "Volt range colour", "", 0x3C0000FF))
                .option(Option.bool("rift_dreadfarm.volt_mood", "Volt mood colours", "A box on each Volt: green friendly, red hostile, blue striking.", false)));
        Features.add(new Feature(BERBERIS, "Rift", "Wilted Berberis", "With the Wand of Farming in the Dreadfarm, shows where the Wilted Berberis is, and once it has seen a field come back, the order to break it in.", "SkyHanni: Wilted Berberis Helper")
                .when(Rift::rift)
                .tick(mc -> {
                    loadFields();
                    if (mc.player != null && mc.level != null && RiftApi.inDreadfarm()) tickBerberis(mc);
                })
                .world(RiftDreadfarm::drawBerberis)
                .left(RiftDreadfarm::leaveBerberis)
                .option(Option.bool("rift_berberis.sequence", "Respawn order", "Learns the order the plants of a field come back in, and marks the next three.", true))
                .option(Option.bool("rift_berberis.only_farmland", "Only on farmland", "Only while you stand on farmland.", false))
                .option(Option.bool("rift_berberis.hide_particles", "Hide its particles", "", false))
                .option(Option.bool("rift_berberis.mute_others", "Mute other players' plants", "Mutes the plant's sounds unless you hold the wand on farmland.", false)));
        ParticleHooks.listen(RiftDreadfarm::onParticle);
        SoundHooks.listen(RiftDreadfarm::onSound);
        Interactions.onBlockClick((pos, right) -> {
            if (right || !Rift.rift() || !RiftApi.inDreadfarm() || !Features.on(BERBERIS) || !berberis("rift_berberis.sequence", true)) return;
            var mc = Minecraft.getInstance();
            if (mc.level != null && mc.level.getBlockState(BlockPos.containing(pos)).is(Blocks.DEAD_BUSH)) advanceAt(pos);
        });
    }
}
