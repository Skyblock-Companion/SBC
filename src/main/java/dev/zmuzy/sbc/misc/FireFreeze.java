// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.misc;

import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.dungeon.DungeonMarks;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.mc.Interactions;
import dev.zmuzy.sbc.mc.SoundHooks;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// from skyhanni features/itemabilities FireFreezeFeatures + FireVeilWandParticles. skyhannis mob list becomes
// every living thing in reach thats not a player or armor stand
public final class FireFreeze {
    public static final String FREEZE = "fire_freeze", VEIL = "fire_veil";
    private static final double RADIUS = 5, PARTICLE_OFFSET = 3.921568568330258E-4;
    private static final long FROZEN_MS = 10_000;

    // a fire freeze on the ground, its pitch says when it goes off
    static final class Area {
        final Vec3 center;
        final float firstPitch;
        long at;
        boolean known, frozen;

        Area(Vec3 center, float pitch, long now) {
            this.center = center;
            this.firstPitch = pitch;
            this.at = fromPitch(pitch, now);
        }

        boolean finished(long now) {
            // half a sec after it shouldve gone off: hypixel plays no sound when it misses
            return frozen || now - at > 500;
        }

        boolean inside(Vec3 p, double extra) {
            double dx = center.x - p.x, dz = center.z - p.z;
            return Math.sqrt(dx * dx + dz * dz) < RADIUS + extra;
        }
    }

    private static final Map<Long, Area> AREAS = new HashMap<>();
    private static final Map<LivingEntity, Long> FROZEN = new HashMap<>();
    private static long lastVeil;

    private FireFreeze() {}

    // from skyhanni timeFromPitch: starts at pitch 2 and drops 0.5 every 2 sec
    static long fromPitch(float pitch, long now) {
        return now + (long) ((2.0 * pitch + 1) * 1000);
    }

    private static long key(double x, double y, double z) {
        return (Math.round(x * 8) & 0x1FFFFFL) | (Math.round(y * 8) & 0x3FFFL) << 21 | (Math.round(z * 8) & 0x1FFFFFL) << 35;
    }

    private static boolean sound(SoundHooks.Sound s) {
        if (!GameState.inSkyBlock || !Features.on(FREEZE)) return false;
        long now = System.currentTimeMillis();
        if (s.name().equals("entity.elder_guardian.ambient") && s.volume() == 0.2f && s.pitch() >= 0 && s.pitch() <= 2 && !nearThunderSpark(s.x(), s.y(), s.z())) {
            long k = key(s.x(), s.y(), s.z());
            Area a = AREAS.get(k);
            if (a == null) AREAS.put(k, new Area(new Vec3(s.x(), s.y(), s.z()), s.pitch(), now));
            else if (!a.known && a.firstPitch != s.pitch()) {
                a.at = fromPitch(s.pitch(), now);
                a.known = true;
            }
        } else if (s.name().equals("block.anvil.land") && s.volume() == 0.6f && s.pitch() == 0.4920635f) {
            Area a = AREAS.remove(key(s.x(), s.y(), s.z()));
            if (a != null) freeze(a, now);
        }
        return false;
    }

    // a thunder spark makes the same sound
    private static boolean nearThunderSpark(double x, double y, double z) {
        var level = Minecraft.getInstance().level;
        String texture = DungeonMarks.skull("THUNDER_SPARK");
        if (level == null || texture.isEmpty()) return false;
        for (ArmorStand st : level.getEntitiesOfClass(ArmorStand.class, new net.minecraft.world.phys.AABB(x - 2, y - 2, z - 2, x + 2, y + 2, z + 2))) {
            if (texture.equals(DungeonMarks.headTexture(st.getMainHandItem()))) return true;
        }
        return false;
    }

    private static void freeze(Area a, long now) {
        a.frozen = true;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity mob) || e instanceof Player || e instanceof ArmorStand || !a.inside(e.position(), 0)) continue;
            Long until = FROZEN.get(mob);
            if (until == null || until < now) FROZEN.put(mob, now + FROZEN_MS);
        }
    }

    private static long circleEpoch = -1;
    private static boolean circleOn;

    private static boolean customCircle() {
        long ep = Features.epoch;
        if (ep != circleEpoch) {
            circleOn = GameState.inSkyBlock && Features.on(FREEZE) && Features.get(FREEZE).option("fire_freeze.customCircle").bool();
            circleEpoch = ep;
        }
        return circleOn;
    }

    private static boolean veilLine() {
        return Features.on(VEIL) && !Features.get(VEIL).option("fire_veil.display").choice().equals("Particles");
    }

    // ParticleMixin calls this: hypixels dust ring under a custom circle + the fire veil flames
    public static boolean hidesParticle(ClientboundLevelParticlesPacket p) {
        if (!GameState.inSkyBlock) return false;
        if (veilLine() && System.currentTimeMillis() - lastVeil <= 5500 && p.particle().getType() == ParticleTypes.FLAME && p.xMaxSpeed() == 0.55f) return true;
        if (AREAS.isEmpty() || !customCircle()) return false;
        if (p.particle().getType() != ParticleTypes.DUST || p.count() != 0 || p.xMaxSpeed() != 1.0f) return false;
        if (p.xDist() != (float) PARTICLE_OFFSET || p.yDist() != (float) PARTICLE_OFFSET || p.zDist() != (float) PARTICLE_OFFSET) return false;
        long now = System.currentTimeMillis();
        Vec3 at = new Vec3(p.x(), p.y(), p.z());
        for (Area a : AREAS.values()) if (!a.finished(now) && a.inside(at, 0.5)) return true;
        return false;
    }

    // EntityHideMixin calls this: the skull stands making hypixels own ring
    public static boolean hidden(Entity e) {
        if (!(e instanceof ArmorStand st) || !st.isInvisible() || !customCircle()) return false;
        String texture = DungeonMarks.skull("FIRE_FREEZE_SKULLS");
        return !texture.isEmpty() && texture.equals(DungeonMarks.headTexture(st.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD)));
    }

    private static void circle(WorldDraw w, Vec3 c, double r, int argb) {
        int steps = 48;
        Vec3 last = null;
        for (int i = 0; i <= steps; i++) {
            double a = Math.PI * 2 * i / steps;
            Vec3 p = new Vec3(c.x + Math.cos(a) * r, c.y, c.z + Math.sin(a) * r);
            if (last != null) w.line(last, p, argb, 2f, false);
            last = p;
        }
    }

    private static String seconds(long ms) {
        return String.format(Locale.ROOT, "%.1fs", Math.max(0, ms) / 1000d);
    }

    private static int blend(double t) {
        // yellow to red like skyhannis ColorUtils.blendRGB on YELLOW + RED
        int g = (int) (0xFF + (0x55 - 0xFF) * t);
        return 0xFFFF0000 | g << 8 | 0x55;
    }

    private static void world(WorldDraw w) {
        Feature f = Features.get(FREEZE);
        long now = System.currentTimeMillis();
        AREAS.values().removeIf(a -> a.frozen || now - a.at > 2000);
        boolean circle = f.option("fire_freeze.customCircle").bool(), timer = f.option("fire_freeze.freezeTimer").bool();
        if (circle || timer) {
            for (Area a : AREAS.values()) {
                if (a.finished(now)) continue;
                Vec3 up = a.center.add(0, 1, 0);
                if (circle) circle(w, up, RADIUS, f.option("fire_freeze.displayColor").color());
                if (timer) w.dynamicText(up, "§b❄ §b" + seconds(a.at - now), 1.0, 0);
            }
        }
        boolean mobTimer = f.option("fire_freeze.mobTimer").bool(), highlight = f.option("fire_freeze.mobHighlight").bool();
        Iterator<Map.Entry<LivingEntity, Long>> it = FROZEN.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LivingEntity, Long> e = it.next();
            LivingEntity mob = e.getKey();
            long left = e.getValue() - now;
            if (left <= 0 || !mob.isAlive() || mob.isRemoved()) {
                it.remove();
                continue;
            }
            double percent = 1 - Math.min(1, Math.max(0, (left / 1000d - 5) / 5));
            int color = blend(percent);
            if (mobTimer) w.dynamicText(mob.position(), "§b❄ " + seconds(left), 1.0, 0);
            if (highlight) w.filledBox(mob.getBoundingBox().inflate(0.1), color & 0xFFFFFF, 0.5f, false);
        }
    }

    private static void veilWorld(WorldDraw w) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !veilLine() || !Features.get(VEIL).option("fire_veil.display").choice().equals("Line")) return;
        if (System.currentTimeMillis() - lastVeil > 5500) return;
        circle(w, mc.player.position(), 3.5, Features.get(VEIL).option("fire_veil.displayColor").color());
    }

    private static boolean veilClick(boolean right) {
        if (!right || !GameState.inSkyBlock) return false;
        var p = Minecraft.getInstance().player;
        if (p != null && "FIRE_VEIL_WAND".equals(Gain.sbId(p.getMainHandItem()))) lastVeil = System.currentTimeMillis();
        return false;
    }

    public static void register() {
        Features.add(new Feature(FREEZE, "Items", "Fire Freeze", "A Fire Freeze Staff's timer: how long until a circle goes off, and how long each mob in it stays frozen.", "SkyHanni: Fire Freeze")
                .when(() -> GameState.inSkyBlock).world(FireFreeze::world).left(() -> {
                    AREAS.clear();
                    FROZEN.clear();
                })
                .option(Option.bool("fire_freeze.freezeTimer", "Timer above a Fire Freeze", "Until it freezes the mobs.", true))
                .option(Option.bool("fire_freeze.mobTimer", "Timer above frozen mobs", "Until they thaw.", true))
                .option(Option.bool("fire_freeze.mobHighlight", "Box around frozen mobs", "Yellow turning red as they thaw.", false))
                .option(Option.bool("fire_freeze.customCircle", "Own circle", "A thin ring in place of Hypixel's particles.", false))
                .option(Option.color("fire_freeze.displayColor", "Circle colour", "", 0xF5000000)));
        SoundHooks.listen(FireFreeze::sound);
        Features.add(new Feature(VEIL, "Items", "Fire Veil look", "What the Fire Veil Wand's ring looks like: Hypixel's flames, one thin line, or nothing.", "SkyHanni: Fire Veil")
                .when(() -> GameState.inSkyBlock).world(FireFreeze::veilWorld)
                .option(Option.choice("fire_veil.display", "Design", "", "Particles", "Particles", "Line", "Off"))
                .option(Option.color("fire_veil.displayColor", "Line colour", "", 0xF5FF5555)));
        Interactions.onItemClick(FireFreeze::veilClick);
    }
}
