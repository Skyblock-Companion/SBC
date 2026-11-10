// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.rift;

import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.render.ParkourHelper;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// the 4 rift parkours skyhanni draws with ParkourHelper: gunthers race in west village (RiftGunthersRace.kt,
// rift/RiftRace.json, in order), lava maze, upside down, tubulator
public final class RiftParkours {
    static final Patterns.P RACE_START = Patterns.p("SH.rift.area.westvillage.riftrace.start");
    static final Patterns.P RACE_FINISH = Patterns.p("SH.rift.area.westvillage.riftrace.finish");
    static final Patterns.P RACE_CANCEL = Patterns.p("SH.rift.area.westvillage.riftrace.cancel");
    // from skyhanni TubulatorParkour.puzzleRoom
    private static final AABB TUBULATOR_ROOM = new AABB(-309.0, 0.0, -112.0, -298.0, 63.0, -101.0);

    static ParkourHelper race, lavaMaze, upsideDown, tubulator;

    public static boolean rainbowColor = true;
    public static int monochromeColor = 0xFFFFFFFF;
    public static int lookAhead = 3;
    public static boolean outline = true;

    private RiftParkours() {}

    public static void load() {
        RiftData.load();
        if (RiftData.riftRace != null) race = new ParkourHelper(RiftData.riftRace.locations(), RiftData.riftRace.shortCuts(), 1.0, 5.0, true, null, true);
        if (RiftData.lavaMaze != null) lavaMaze = new ParkourHelper(RiftData.lavaMaze, 1.0, 1.0);
        if (RiftData.upsideDown != null) {
            upsideDown = new ParkourHelper(RiftData.upsideDown, 2.0, 2.0);
            upsideDown.outline = true;
        }
        if (RiftData.tubulator != null) {
            tubulator = new ParkourHelper(RiftData.tubulator, 1.0, 2.0);
            tubulator.outline = true;
        }
        updateConfig();
    }

    static void updateConfig() {
        for (ParkourHelper p : new ParkourHelper[]{race, lavaMaze, upsideDown, tubulator}) {
            if (p == null) continue;
            p.rainbowColor = rainbowColor;
            p.monochromeColor = monochromeColor;
            p.lookAhead = lookAhead + 1;
            if (p == upsideDown || p == tubulator) p.outline = outline;
            p.playerPos = () -> RiftEnv.world.playerPos();
            p.onGround = () -> RiftEnv.world.onGround();
        }
    }

    public static void onIslandChange() {
        if (race != null) race.reset();
    }

    // from skyhanni onChat with the formatted line. true = used a parkour line
    public static boolean onChat(String formatted) {
        if (!RiftApi.inRift()) return false;
        if (RiftApi.inWestVillage()) {
            if (RACE_START.matcher(formatted).matches()) {
                RiftApi.inRiftRace = true;
                return true;
            }
            if (RACE_CANCEL.matcher(formatted).matches() || RACE_FINISH.matcher(formatted).matches()) {
                if (race != null) race.reset();
                RiftApi.inRiftRace = false;
                return true;
            }
        }
        if (RiftApi.inMirrorVerse()) {
            if (formatted.equals("§c§lEEK! THE LAVA OOFED YOU!")) {
                if (lavaMaze != null) lavaMaze.reset();
                return true;
            }
            if (formatted.equals("§c§lOH NO! THE LAVA OOFED YOU BACK TO THE START!")) {
                if (upsideDown != null) upsideDown.reset();
                return true;
            }
        }
        return false;
    }

    // a mirrorverse parkour is going (skyhanni hides other players then)
    public static boolean inMirrorverseParkour() {
        return RiftApi.inMirrorVerse() && ((lavaMaze != null && lavaMaze.inParkour()) || (upsideDown != null && upsideDown.inParkour()) || (tubulator != null && tubulator.inParkour()));
    }

    public static void renderRace(WorldDraw w) {
        if (!RiftApi.inRift() || !RiftApi.inWestVillage() || !RiftApi.inRiftRace || race == null) return;
        race.render(w);
    }

    public static void renderMirrorverse(WorldDraw w) {
        if (!RiftApi.inRift() || !RiftApi.inMirrorVerse()) return;
        if (lavaMaze != null) lavaMaze.render(w);
        if (upsideDown != null) upsideDown.render(w);
        Vec3 p = RiftEnv.world.playerPos();
        if (tubulator != null && TUBULATOR_ROOM.contains(p)) tubulator.render(w);
    }
}
