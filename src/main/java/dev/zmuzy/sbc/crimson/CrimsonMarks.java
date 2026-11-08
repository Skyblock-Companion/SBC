// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.crimson;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.render.WorldDraw;
import dev.zmuzy.sbc.rift.RiftScan;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// small crimson stuff: from skyhanni AtomHitBox, the magma boss phase from
// DamageIndicatorManager.checkMagmaCube (there its before the boss health, here it floats over the boss), and
// skyblocker FogRendererMixin
public final class CrimsonMarks {
    public static final String ATOMS = "crimson_atoms", MAGMA = "crimson_magma_phase", FOG = "crimson_nether_fog";

    private record Atom(ArmorStand stand, String label, int color) {}

    private static final List<Atom> FOUND = new ArrayList<>();
    private static final Map<MagmaCube, String> BOSSES = new HashMap<>();

    private CrimsonMarks() {}

    private static void tickAtoms(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.player.tickCount % 5 != 0) return;
        FOUND.clear();
        String x = RiftScan.skull("CRIMSON_ATOM_X"), y = RiftScan.skull("CRIMSON_ATOM_Y"), z = RiftScan.skull("CRIMSON_ATOM_Z");
        for (ArmorStand st : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(50))) {
            String tex = RiftScan.texture(st.getItemBySlot(EquipmentSlot.HEAD));
            if (tex == null) continue;
            if (tex.equals(x) && RiftScan.on(ATOMS, "crimson_atoms.exe", true)) FOUND.add(new Atom(st, "§aExe", 0xFF00FF00));
            else if (tex.equals(y) && RiftScan.on(ATOMS, "crimson_atoms.wai", true)) FOUND.add(new Atom(st, "§6Wai", 0xFFFFC800));
            else if (tex.equals(z) && RiftScan.on(ATOMS, "crimson_atoms.zee", true)) FOUND.add(new Atom(st, "§5Zee", 0xFFFF00FF));
        }
    }

    private static void drawAtoms(WorldDraw w) {
        for (Atom a : FOUND) {
            if (a.stand().isRemoved() || !a.stand().isAlive()) continue;
            w.box(a.stand().getBoundingBox(), a.color(), false, false);
            w.dynamicText(a.stand().position().add(0, -1, 0), a.label(), 1, 0);
        }
    }

    // checkMagmaCube's prefix, the cube shrinks every phase
    static String magmaPhase(int size) {
        return switch (size) {
            case 24 -> "§c1/6";
            case 22 -> "§e2/6";
            case 20 -> "§e3/6";
            case 18 -> "§e4/6";
            case 16 -> "§e5/6";
            default -> "§a6/6";
        };
    }

    private static void tickMagma(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.player.tickCount % 10 != 0) return;
        BOSSES.clear();
        for (ArmorStand st : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(96), Entity::hasCustomName)) {
            if (!GameState.strip(st.getName().getString()).contains("Magma Boss")) continue;
            MagmaCube best = null;
            for (MagmaCube c : mc.level.getEntitiesOfClass(MagmaCube.class, st.getBoundingBox().inflate(16))) if (best == null || c.getSize() > best.getSize()) best = c;
            if (best != null) BOSSES.put(best, magmaPhase(best.getSize()));
        }
    }

    private static void drawMagma(WorldDraw w) {
        for (var e : BOSSES.entrySet()) {
            if (e.getKey().isRemoved()) continue;
            var b = e.getKey().getBoundingBox();
            w.dynamicText(new Vec3(b.getCenter().x, b.maxY + 1, b.getCenter().z), "§cMagma Boss " + e.getValue(), 1.5, 0);
        }
    }

    // from skyblocker FogRendererMixin
    public static boolean extendFog() {
        return "CRIMSON_ISLE".equals(GameState.island) && Features.on(FOG);
    }

    public static void register() {
        Features.add(new Feature(ATOMS, "Crimson Isle", "Atom hit boxes", "The hit boxes of Exes, Wais and Zees.", "SkyHanni: Atoms HitBox")
                .when(Crimson::crimson).defaultOff().tick(CrimsonMarks::tickAtoms).world(CrimsonMarks::drawAtoms).left(FOUND::clear)
                .option(Option.bool("crimson_atoms.exe", "Exe", "", true))
                .option(Option.bool("crimson_atoms.wai", "Wai", "", true))
                .option(Option.bool("crimson_atoms.zee", "Zee", "", true)));
        Features.add(new Feature(MAGMA, "Crimson Isle", "Magma Boss phase", "Which of its six phases the Magma Boss is in, above it.", "SkyHanni: Magma Boss Phase")
                .when(Crimson::crimson).tick(CrimsonMarks::tickMagma).world(CrimsonMarks::drawMagma).left(BOSSES::clear));
        Features.add(new Feature(FOG, "Crimson Isle", "Clearer fog", "On the Crimson Isle, keeps the fog as far away as it is in the overworld instead of close like in the Nether.", "Skyblocker: Extend Nether Fog")
                .when(Crimson::crimson).defaultOff());
    }
}
