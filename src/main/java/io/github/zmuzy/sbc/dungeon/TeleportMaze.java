// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.dungeon;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

// teleport pad maze, display only. every pad you try teaches where it goes. dead end pads go red, and when
// only one is left its the way out (green + a line to it)
public final class TeleportMaze {
    private static final int[][] ROOM_CENTERS = {{7, 68, 9}, {23, 68, 9}, {7, 68, 17}, {23, 68, 17}, {7, 68, 25}, {15, 68, 25}, {23, 68, 25}};
    private static final int[][] PADS = {
            {15, 69, 12}, {4, 69, 6}, {10, 69, 6}, {20, 69, 6}, {26, 69, 6}, {4, 69, 12}, {10, 69, 12}, {20, 69, 12}, {26, 69, 12},
            {4, 69, 14}, {10, 69, 14}, {20, 69, 14}, {26, 69, 14}, {4, 69, 20}, {10, 69, 20}, {20, 69, 20}, {26, 69, 20},
            {4, 69, 22}, {10, 69, 22}, {12, 69, 22}, {18, 69, 22}, {20, 69, 22}, {26, 69, 22},
            {4, 69, 28}, {10, 69, 28}, {12, 69, 28}, {18, 69, 28}, {20, 69, 28}, {26, 69, 28}, {15, 69, 14}};
    private static final Set<String> ORES = Set.of("barrier", "coal_ore", "iron_ore", "redstone_ore", "lapis_ore", "gold_ore", "diamond_ore", "emerald_ore");

    private static final Map<BlockPos, String> pads = new HashMap<>();
    private static BlockPos finalPad;
    private static BlockPos prev;
    private static Dungeon.Placed lastRoom;

    private TeleportMaze() {}

    private static BlockPos actual(Dungeon.Placed r, int x, int y, int z) {
        Vec3 a = r.toActual(x, y, z);
        return BlockPos.containing(Math.floor(a.x), Math.floor(a.y), Math.floor(a.z));
    }

    private static int[] rel(Dungeon.Placed r, BlockPos p) {
        int[] xz = r.toRelative(p.getX(), p.getZ());
        return new int[]{xz[0], 69, xz[1]};
    }

    private static boolean isPad(int[] r) {
        for (int[] p : PADS) if (p[0] == r[0] && p[2] == r[2]) return true;
        return false;
    }

    private static int[] nearestPad(int[] r) {
        int[] best = PADS[0];
        long bd = Long.MAX_VALUE;
        for (int[] p : PADS) {
            long d = (long) (p[0] - r[0]) * (p[0] - r[0]) + (long) (p[2] - r[2]) * (p[2] - r[2]);
            if (d < bd) {
                bd = d;
                best = p;
            }
        }
        return best;
    }

    private static String roomType(Minecraft mc, Dungeon.Placed room, int[] pos) {
        if (pos[0] == 15 && pos[2] == 12) return "barrier";
        for (int[] c : ROOM_CENTERS) {
            if (c[0] - 3 <= pos[0] && pos[0] <= c[0] + 3 && c[2] - 3 <= pos[2] && pos[2] <= c[2] + 3) {
                String id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(actual(room, c[0], c[1], c[2])).getBlock()).getPath();
                return ORES.contains(id) ? id : null;
            }
        }
        return null;
    }

    private static void learn(Minecraft mc, Dungeon.Placed room, int[] from, int[] to) {
        String type = roomType(mc, room, to);
        if (type != null) pads.put(actual(room, from[0], from[1], from[2]), type);
    }

    private static void tick(Minecraft mc) {
        var room = Dungeon.current();
        if (room == null || !room.def.id().startsWith("teleport-pad-room") || mc.level == null || mc.player == null) {
            pads.clear();
            finalPad = null;
            prev = null;
            lastRoom = null;
            return;
        }
        if (room != lastRoom) {
            pads.clear();
            finalPad = null;
            prev = null;
            lastRoom = room;
        }
        BlockPos now = mc.player.blockPosition();
        if (prev != null && prev.distSqr(now) > 25) {
            int[] from = rel(room, prev), to = rel(room, now);
            if (!(from[0] == to[0] && from[2] == to[2]) && isPad(from)) {
                learn(mc, room, from, to);
                learn(mc, room, nearestPad(to), from);
            }
        }
        prev = now;
        if (finalPad == null) {
            Set<BlockPos> left = new HashSet<>();
            for (int[] p : PADS) {
                if (p[0] == 15) continue;
                BlockPos a = actual(room, p[0], p[1], p[2]);
                if (!pads.containsKey(a)) left.add(a);
            }
            if (left.size() == 1) finalPad = left.iterator().next();
        }
    }

    private static void draw(WorldDraw w) {
        for (BlockPos p : pads.keySet()) w.box(new AABB(p), 0xFFFF3B3B, true, false);
        if (finalPad != null) {
            w.box(new AABB(finalPad), 0xFF33D17A, true, true);
            Vec3 from = w.eye.add(w.mc.player.getLookAngle().scale(1.5)).add(0, -0.3, 0);
            w.line(from, Vec3.atCenterOf(finalPad), 0xFF33D17A, 2f, true);
        }
    }

    public static void register() {
        java.util.function.BooleanSupplier inDungeon = () -> "CATACOMBS".equals(GameState.island) || SbcMod.DEV;
        Features.add(new Feature("dungeon_teleportmaze", "Dungeons", "Teleport maze", "Marks the pads that lead to dead ends in red and the way out in green once it is known.", "Skyblocker: teleport maze solver")
                .when(inDungeon).tick(TeleportMaze::tick).world(TeleportMaze::draw));
    }
}
