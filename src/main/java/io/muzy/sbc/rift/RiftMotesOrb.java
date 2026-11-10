// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.rift;

import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// port of skyhanni rift/everywhere/motes/RiftMotesOrb.kt: an entity_effect particle source doing 60-90
// particles a sec is a motes orb, marked till picked up (chat, or 300 ms)
public final class RiftMotesOrb {
    static final Patterns.P MOTES = Patterns.p("SH.rift.everywhere.motesorb");

    public static final class MotesOrb {
        public Vec3 location;
        public int counter;
        public long startTime, lastTime;
        public boolean isOrb, pickedUp;

        MotesOrb(Vec3 location, long now) {
            this.location = location;
            startTime = lastTime = now;
        }
    }

    static List<MotesOrb> motesOrbs = new ArrayList<>();
    public static boolean hideParticles;
    public static int size = 5;
    static int orbsPicked, motesPicked;

    private RiftMotesOrb() {}

    // from skyhanni onParticle for entity_effect. true = hide it
    public static boolean onParticle(Vec3 particlePos) {
        Vec3 location = particlePos.add(-0.5, 0, -0.5);
        long now = RiftEnv.now();
        MotesOrb orb = null;
        for (MotesOrb o : motesOrbs) if (o.location.distanceTo(location) < 3) {
            orb = o;
            break;
        }
        if (orb == null) {
            orb = new MotesOrb(location, now);
            motesOrbs.add(orb);
        }
        orb.location = location;
        orb.lastTime = now;
        orb.counter++;
        orb.pickedUp = false;
        return hideParticles && orb.isOrb;
    }

    // box shows through blocks too. off by default bc it shows stuff you couldnt see otherwise
    public static boolean throughWalls;

    // skyhanni cancels the particle event, our listener cant so the packet hook asks here
    public static boolean hides(String type, double x, double y, double z) {
        if (!hideParticles || !type.equals("entity_effect") || !io.muzy.sbc.feature.Features.on(Rift.MOTES)) return false;
        Vec3 at = new Vec3(x - 0.5, y, z - 0.5);
        for (MotesOrb o : motesOrbs) if (o.isOrb && o.location.distanceTo(at) < 3) return true;
        return false;
    }

    // from skyhanni onChat with the formatted line
    public static boolean onChat(String formatted) {
        if (!MOTES.matcher(formatted).matches()) return false;
        orbsPicked++;
        Vec3 p = RiftEnv.world.playerPos();
        MotesOrb best = null;
        for (MotesOrb o : motesOrbs) if (best == null || o.location.distanceTo(p) < best.location.distanceTo(p)) best = o;
        if (best != null) best.pickedUp = true;
        return true;
    }

    public static int orbsPicked() {
        return orbsPicked;
    }

    // orbs that count rn (counted per render)
    static List<MotesOrb> activeOrbs() {
        long now = RiftEnv.now();
        motesOrbs.removeIf(o -> now > o.lastTime + 2000);
        List<MotesOrb> out = new ArrayList<>();
        for (MotesOrb orb : motesOrbs) {
            double ageInSeconds = (now - orb.startTime) / 1000.0;
            if (ageInSeconds < 0.5) continue;
            double particlesPerSecond = Math.round(orb.counter / ageInSeconds * 10) / 10.0;
            if (particlesPerSecond < 60.0 || particlesPerSecond > 90.0) continue;
            orb.isOrb = true;
            if (now > orb.lastTime + 300) orb.pickedUp = true;
            out.add(orb);
        }
        return out;
    }

    public static void render(WorldDraw w) {
        if (!RiftApi.inRift()) return;
        for (MotesOrb orb : activeOrbs()) {
            Vec3 location = orb.location.add(0, 0.5, 0);
            double sizeOffset = (5 - size) * -0.1;
            int color = orb.pickedUp ? 0xFFAAAAAA : 0xFFFF55FF;
            w.label((orb.pickedUp ? "§7" : "§d") + "Motes Orb", location.add(0.5, 1.2, 0.5), false);
            w.box(new AABB(location, location.add(1, 1, 1)).inflate(sizeOffset), color, true, throughWalls);
        }
    }

    public static void reset() {
        motesOrbs.clear();
        orbsPicked = 0;
    }
}
