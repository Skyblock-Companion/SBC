// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.dungeon;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

// boulder room, display only: reads the 7 wide boulder grid from the world and searches a path (pushing a
// boulder one step is ok when the square behind is free)
public final class Boulder {
    private static final int COLS = 7, ROWS = 7;

    private static final class State {
        final char[][] grid;
        int x, y, pushed;

        State(char[][] grid, int x, int y, int pushed) {
            this.grid = new char[grid.length][];
            for (int i = 0; i < grid.length; i++) this.grid[i] = grid[i].clone();
            this.x = x;
            this.y = y;
            this.pushed = pushed;
        }

        boolean valid(int px, int py) {
            return px >= 0 && py >= 0 && px < grid.length && py < grid[0].length;
        }

        boolean move(int dx, int dy) {
            int nx = x + dx, ny = y + dy;
            if (!valid(nx, ny)) return false;
            if (grid[nx][ny] != 'B') {
                x = nx;
                y = ny;
                return true;
            }
            int bx = nx + dx, by = ny + dy;
            if (valid(bx, by) && grid[bx][by] == '.') {
                grid[nx][ny] = '.';
                grid[bx][by] = 'B';
                x = nx;
                y = ny;
                pushed++;
                return true;
            }
            return false;
        }

        int cost(int pathLength) {
            return pathLength + pushed * 2 + Math.abs(x) + Math.abs(y - grid[0].length / 2);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof State s && Arrays.deepEquals(grid, s.grid) && x == s.x && y == s.y && pushed == s.pushed;
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(Arrays.deepHashCode(grid), x, y, pushed);
        }
    }

    private record Node(State state, List<int[]> path) {}

    private static List<int[]> solve(char[][] board) {
        Set<State> seen = new HashSet<>();
        PriorityQueue<Node> queue = new PriorityQueue<>(java.util.Comparator.comparingInt((Node n) -> n.state.cost(n.path.size())));
        for (int c = 0; c < COLS; c++) queue.add(new Node(new State(board, ROWS - 1, c, 0), new ArrayList<>()));
        int iterations = 0;
        int[][] dirs = {{-1, 0}, {0, -1}, {0, 1}, {1, 0}};
        while (!queue.isEmpty() && iterations < 10000) {
            Node n = queue.poll();
            if (!seen.add(n.state)) continue;
            n.path.add(new int[]{n.state.x, n.state.y});
            if (n.state.x == 0) return n.path;
            for (int[] d : dirs) {
                State next = new State(n.state.grid, n.state.x, n.state.y, n.state.pushed);
                if (next.move(d[0], d[1])) queue.add(new Node(next, new ArrayList<>(n.path)));
            }
            iterations++;
        }
        return null;
    }

    private static BlockPos actual(Dungeon.Placed r, int x, int y, int z) {
        Vec3 a = r.toActual(x, y, z);
        return BlockPos.containing(Math.floor(a.x), Math.floor(a.y), Math.floor(a.z));
    }

    private static List<Vec3> line = List.of();
    private static BlockPos button;
    private static Dungeon.Placed lastRoom;
    private static int lastHash;

    private static void tick(Minecraft mc) {
        var room = Dungeon.current();
        if (room == null || !room.def.id().startsWith("boxes-room") || mc.level == null || mc.player == null) {
            line = List.of();
            button = null;
            lastRoom = null;
            return;
        }
        if (mc.player.tickCount % 10 != 0) return;
        // rows 0..5 = boulder rows (z = 24, 21 ... 9), row 6 = where you start. columns x = 24, 21 ... 6
        char[][] board = new char[ROWS][COLS];
        for (char[] row : board) Arrays.fill(row, '.');
        int[][] cellX = new int[ROWS][COLS], cellZ = new int[ROWS][COLS];
        for (int c = 0; c < COLS; c++) {
            cellX[ROWS - 1][c] = 24 - 3 * c;
            cellZ[ROWS - 1][c] = 6;
            board[ROWS - 1][c] = 'P';
        }
        for (int r = 0; r < ROWS - 1; r++)
            for (int c = 0; c < COLS; c++) {
                int x = 24 - 3 * c, z = 24 - 3 * r;
                cellX[r][c] = x;
                cellZ[r][c] = z;
                var b = mc.level.getBlockState(actual(room, x, 65, z)).getBlock();
                board[r][c] = b == Blocks.BIRCH_PLANKS || b == Blocks.JUNGLE_PLANKS ? 'B' : '.';
            }
        int hash = Arrays.deepHashCode(board);
        if (room == lastRoom && hash == lastHash) return;
        lastRoom = room;
        lastHash = hash;
        line = List.of();
        button = null;
        List<int[]> path = solve(board);
        if (path == null) return;
        List<Vec3> pts = new ArrayList<>();
        for (int[] p : path) pts.add(Vec3.atCenterOf(actual(room, cellX[p[0]][p[1]], 64, cellZ[p[0]][p[1]])));
        for (int i = 0; i + 1 < pts.size() && button == null; i++) {
            Vec3 a = pts.get(i).add(0, 1, 0), b = pts.get(i + 1).add(0, 1, 0);
            int steps = (int) Math.max(Math.abs(b.x - a.x), Math.max(Math.abs(b.y - a.y), Math.abs(b.z - a.z)));
            for (int s = 0; s <= steps; s++) {
                double t = steps == 0 ? 0 : (double) s / steps;
                BlockPos p = BlockPos.containing(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
                if (mc.level.getBlockState(p).is(Blocks.STONE_BUTTON)) {
                    button = p;
                    break;
                }
            }
        }
        if (button != null) line = pts;
    }

    private static void draw(WorldDraw w) {
        if (line.size() < 2) return;
        for (int i = 0; i + 1 < line.size(); i++) w.line(line.get(i), line.get(i + 1), 0xFFFF9F43, 5f, true);
        if (button != null) w.box(new AABB(button), 0xFFFF3B3B, true, false);
    }

    public static void register() {
        java.util.function.BooleanSupplier inDungeon = () -> "CATACOMBS".equals(GameState.island) || SbcMod.DEV;
        Features.add(new Feature("dungeon_boulder", "Dungeons", "Boulder room", "Draws a route through the boulders and marks the button at its end.", "Skyblocker: boulder solver")
                .when(inDungeon).tick(Boulder::tick).world(Boulder::draw));
    }
}
