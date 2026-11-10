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
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

// the 2 ice rooms, display only. ice fill: reads the 3 boards from the world and draws a route that hits
// every free square once. ice path: the silverfish slides till it hits something, so we search the moves
public final class IcePuzzles {
    // ---- ice fill ----

    private static final int[][] ORIGIN = {{16, 70, 9}, {17, 71, 16}, {18, 72, 25}};
    private static final int[] SIZE = {3, 5, 7};

    private static final boolean[][][] fillBoard = {new boolean[3][3], new boolean[5][5], new boolean[7][7]};
    private static final List<List<int[]>> fillPath = new ArrayList<>(List.of(List.of(), List.of(), List.of()));
    private static CompletableFuture<?> fillJob;
    private static Dungeon.Placed fillRoom;

    private static long nodes;

    private static List<int[]> dfs(boolean[][] wall, int left, List<int[]> path, boolean[][] seen) {
        if (++nodes > 4_000_000) return null;
        int[] cur = path.get(path.size() - 1);
        int cols = wall[0].length;
        if (left == 0) return cur[0] == 0 && cur[1] == cols / 2 ? new ArrayList<>(path) : null;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int r = cur[0] + d[0], c = cur[1] + d[1];
            if (r < 0 || r >= wall.length || c < 0 || c >= cols || wall[r][c] || seen[r][c]) continue;
            seen[r][c] = true;
            path.add(new int[]{r, c});
            List<int[]> res = dfs(wall, left - 1, path, seen);
            path.remove(path.size() - 1);
            seen[r][c] = false;
            if (res != null) return res;
        }
        return null;
    }

    private static List<int[]> solveFill(boolean[][] wall) {
        int rows = wall.length, cols = wall[0].length;
        int free = 0;
        for (boolean[] row : wall) for (boolean b : row) if (!b) free++;
        boolean[][] seen = new boolean[rows][cols];
        int[] start = {rows - 1, cols / 2};
        if (wall[start[0]][start[1]]) return List.of();
        seen[start[0]][start[1]] = true;
        List<int[]> path = new ArrayList<>();
        path.add(start);
        nodes = 0;
        List<int[]> res = dfs(wall, free - 1, path, seen);
        return res == null ? List.of() : res;
    }

    private static BlockPos fillCell(Dungeon.Placed room, int board, int r, int c) {
        Vec3 a = room.toActual(ORIGIN[board][0] - c, ORIGIN[board][1], ORIGIN[board][2] - r);
        return BlockPos.containing(Math.floor(a.x), Math.floor(a.y), Math.floor(a.z));
    }

    // reads one board, false = its floor isnt loaded yet. sets changed[0] when something differs
    private static boolean readFill(Minecraft mc, Dungeon.Placed room, int b, boolean[] changed) {
        boolean[][] wall = fillBoard[b];
        for (int r = 0; r < SIZE[b]; r++)
            for (int c = 0; c < SIZE[b]; c++) {
                BlockPos p = fillCell(room, b, r, c);
                if (!mc.level.hasChunkAt(p)) return false;
                if (mc.level.getBlockState(p.below()).isAir()) return false;
                boolean w = !mc.level.getBlockState(p).isAir();
                if (wall[r][c] != w) {
                    wall[r][c] = w;
                    changed[0] = true;
                }
            }
        return true;
    }

    private static void tickFill(Minecraft mc) {
        var room = Dungeon.current();
        if (room == null || !room.def.id().equals("ice-path") || mc.level == null || mc.player == null) {
            fillRoom = null;
            return;
        }
        if (fillRoom != room) {
            fillRoom = room;
            for (int b = 0; b < 3; b++) {
                for (boolean[] row : fillBoard[b]) Arrays.fill(row, false);
                fillPath.set(b, List.of());
            }
            fillJob = null;
        }
        if (mc.player.tickCount % 10 != 0) return;
        if (fillJob != null && !fillJob.isDone()) return;
        for (int b = 0; b < 3; b++) {
            boolean[] changed = {false};
            if (!readFill(mc, room, b, changed) || !changed[0]) continue;
            final int board = b;
            boolean[][] copy = new boolean[SIZE[b]][];
            for (int r = 0; r < SIZE[b]; r++) copy[r] = fillBoard[b][r].clone();
            fillJob = CompletableFuture.runAsync(() -> fillPath.set(board, solveFill(copy)));
            return;
        }
    }

    private static void drawFill(WorldDraw w) {
        var room = fillRoom;
        if (room == null) return;
        for (int b = 0; b < 3; b++) {
            List<int[]> path = fillPath.get(b);
            for (int i = 0; i + 1 < path.size(); i++) {
                Vec3 p = Vec3.atCenterOf(fillCell(room, b, path.get(i)[0], path.get(i)[1]));
                Vec3 q = Vec3.atCenterOf(fillCell(room, b, path.get(i + 1)[0], path.get(i + 1)[1]));
                w.line(p, q, 0xFFFF3B3B, 5f, true);
            }
        }
    }

    // ---- ice path (silverfish) ----

    private static final int N = 17;
    private static final boolean[][] pathBoard = new boolean[N][N];
    private static List<int[]> slide = List.of();
    private static int[] fishAt;
    private static Dungeon.Placed pathRoom;

    private static BlockPos pathCell(Dungeon.Placed room, int row, int col) {
        Vec3 a = room.toActual(23 - col, 66, 24 - row);
        return BlockPos.containing(Math.floor(a.x), Math.floor(a.y), Math.floor(a.z));
    }

    private static int[] slideTo(int r, int c, int dr, int dc) {
        int rr = r, cc = c;
        while (rr >= 0 && rr < N && cc >= 0 && cc < N && !pathBoard[rr][cc]) {
            rr += dr;
            cc += dc;
        }
        return new int[]{rr - dr, cc - dc};
    }

    private static List<int[]> solvePath(int[] from) {
        ArrayDeque<List<int[]>> queue = new ArrayDeque<>();
        Set<Integer> seen = new HashSet<>();
        queue.add(List.of(from));
        seen.add(from[0] * 100 + from[1]);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty()) {
            List<int[]> cur = queue.poll();
            int[] at = cur.get(cur.size() - 1);
            if (at[0] == 0 && at[1] >= 7 && at[1] <= 9) return cur;
            for (int[] d : dirs) {
                int[] to = slideTo(at[0], at[1], d[0], d[1]);
                if (to[0] == at[0] && to[1] == at[1]) continue;
                if (!seen.add(to[0] * 100 + to[1])) continue;
                List<int[]> next = new ArrayList<>(cur);
                next.add(to);
                queue.add(next);
            }
        }
        return List.of();
    }

    private static void tickPath(Minecraft mc) {
        var room = Dungeon.current();
        if (room == null || !room.def.id().equals("ice-silverfish-room") || mc.level == null || mc.player == null) {
            pathRoom = null;
            return;
        }
        pathRoom = room;
        if (mc.player.tickCount % 5 != 0) return;
        boolean changed = false;
        for (int r = 0; r < N; r++)
            for (int c = 0; c < N; c++) {
                BlockPos p = pathCell(room, r, c).above();
                if (!mc.level.hasChunkAt(p)) continue;
                boolean w = !mc.level.getBlockState(p).isAir();
                if (pathBoard[r][c] != w) {
                    pathBoard[r][c] = w;
                    changed = true;
                }
            }
        Vec3 spot = room.toActual(15, 66, 16);
        var fish = mc.level.getEntitiesOfClass(Silverfish.class, AABB.ofSize(Vec3.atCenterOf(BlockPos.containing(Math.floor(spot.x), Math.floor(spot.y), Math.floor(spot.z))), 16, 16, 16));
        if (fish.isEmpty()) return;
        BlockPos fp = fish.get(0).blockPosition();
        int[] rel = room.toRelative(fp.getX(), fp.getZ());
        int[] pos = {24 - rel[1], 23 - rel[0]};
        if (pos[0] < 0 || pos[0] >= N || pos[1] < 0 || pos[1] >= N) return;
        if (changed || fishAt == null || fishAt[0] != pos[0] || fishAt[1] != pos[1]) {
            fishAt = pos;
            slide = solvePath(pos);
        }
    }

    private static void drawPath(WorldDraw w) {
        var room = pathRoom;
        if (room == null || slide.size() < 2) return;
        for (int i = 0; i + 1 < slide.size(); i++) {
            Vec3 p = Vec3.atCenterOf(pathCell(room, slide.get(i)[0], slide.get(i)[1]));
            Vec3 q = Vec3.atCenterOf(pathCell(room, slide.get(i + 1)[0], slide.get(i + 1)[1]));
            w.line(p, q, 0xFFFF3B3B, 5f, true);
        }
    }

    public static void register() {
        java.util.function.BooleanSupplier inDungeon = () -> "CATACOMBS".equals(GameState.island) || SbcMod.DEV;
        Features.add(new Feature("dungeon_icefill", "Dungeons", "Ice Fill", "Draws the route that fills every ice square of the three boards.", "Skyblocker: ice fill solver")
                .when(inDungeon).tick(IcePuzzles::tickFill).world(IcePuzzles::drawFill));
        Features.add(new Feature("dungeon_icepath", "Dungeons", "Ice Path", "Draws the shortest chain of slides that gets the silverfish to the exit.", "Skyblocker: ice path solver")
                .when(inDungeon).tick(IcePuzzles::tickPath).world(IcePuzzles::drawPath));
    }
}
