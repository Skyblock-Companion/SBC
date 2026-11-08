// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.greenhouse;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.garden.GardenPack;
import dev.zmuzy.sbc.mc.SoundHooks;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// from skyhanni PhantomleafSolver: a note at pitch 0.61-0.62 while you look for a phantomleaf has volume 1 -
// distance / 30 from its hiding spot
// which blocks are that far gets worked out every time you stand still, one left = thats the spot. nothing
// sent or clicked. needs 2 sounds from the same place so stand still
public final class PhantomleafSolver {
    public static final String ID = "garden_phantomleaf";
    private static final int SCALE = 30;
    private static final double MUTATION_Y = 74.0;
    private static boolean searching;
    private static double lastX = Double.NaN, lastZ;
    private static final List<Vec3> CANDIDATES = new ArrayList<>();

    private PhantomleafSolver() {}

    static boolean searching() {
        return searching;
    }

    static void searchingForTest() {
        searching = true;
    }

    private static void reset() {
        searching = false;
        CANDIDATES.clear();
        lastX = Double.NaN;
    }

    // updateCandidates: blocks around the center at exactly that distance, within 0.001
    static List<Vec3> candidates(double x, double z, double radius) {
        List<Vec3> out = new ArrayList<>();
        double rx = Math.floor(x) + 0.5, rz = Math.floor(z) + 0.5;
        for (int dx = -SCALE - 1; dx <= SCALE + 1; dx++) {
            for (int dz = -SCALE - 1; dz <= SCALE + 1; dz++) {
                double d = Math.hypot(rx + dx - x, rz + dz - z);
                if (Math.abs(d - radius) < 0.001) out.add(new Vec3(Math.floor(rx + dx), MUTATION_Y, Math.floor(rz + dz)));
            }
        }
        return out;
    }

    private static boolean onSound(SoundHooks.Sound s) {
        if (!searching || !Features.on(ID) || !GardenPack.garden()) return false;
        if (s.pitch() < 0.61f || s.pitch() > 0.62f || !s.name().equals("block.note_block.basedrum")) return false;
        var mc = Minecraft.getInstance();
        if (mc.player == null) return false;
        double x = mc.player.getX(), z = mc.player.getZ();
        if (x == lastX && z == lastZ) {
            CANDIDATES.clear();
            CANDIDATES.addAll(candidates(x, z, SCALE * (1.0 - s.volume())));
            if (CANDIDATES.isEmpty()) GardenPack.chat("§7No solutions found. Try moving a little.");
            else if (CANDIDATES.size() > 1) GardenPack.chat("§7Multiple candidates found. Move a little to recalculate.");
        }
        lastX = x;
        lastZ = z;
        return false;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!GardenPack.garden()) return ChatBus.PASS;
        String clean = GameState.strip(line.plain());
        if (Patterns.get("SH.garden.greenhouse.phantomleaf.poof").matcher(clean).find()) searching = true;
        else if (Patterns.get("SH.garden.greenhouse.phantomleaf.failure").matcher(clean).find() || Patterns.get("SH.garden.greenhouse.phantomleaf.found").matcher(clean).find()) reset();
        return ChatBus.PASS;
    }

    private static void draw(WorldDraw w) {
        if (!searching) return;
        int color = CANDIDATES.size() > 1 ? 0xFFFFFF55 : 0xFF55FF55;
        boolean walls = GardenPack.on(ID, "through_walls");
        for (Vec3 pos : CANDIDATES) w.waypoint(pos, color, "§aPhantomleaf", true, walls, walls);
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Garden", "Phantomleaf solver", "While you look for a Phantomleaf, its hiding spot in green, worked out from the sounds it makes. Stand still for best results.", "SkyHanni: Phantomleaf Solver")
                .when(GardenPack::garden).world(PhantomleafSolver::draw).left(PhantomleafSolver::reset)
                .option(Option.bool("garden_phantomleaf.through_walls", "Through walls", "Also draws its hiding spot behind blocks.", false)));
        ChatBus.register(ChatBus.OBSERVE + 17, ID, f, PhantomleafSolver::onChat);
        SoundHooks.listen(PhantomleafSolver::onSound);
    }
}
