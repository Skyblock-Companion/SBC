// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.dungeon;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// every room on hypixels map in the last hotbar slot, so what the team opened shows before you walk in.
// from skyblocker DungeonMapUtils: getRoomType, getRoomSegments, getPhysicalPosFromMap
public final class MapRooms {
    // kind like the map card names it, cells as grid x/y (0..5) of our 6x6 floor
    public record Room(String kind, List<int[]> cells) {}

    private static int[] physicalEntrance, mapEntrance;
    private static int mapRoomSize;
    private static List<Room> rooms = List.of();
    private static int lastTick = -1;

    private MapRooms() {}

    // skyblocker Room.Type colours
    static String kindOf(byte color) {
        return switch (color) {
            case 30 -> "Entrance";
            case 63 -> "Room";
            case 66 -> "Puzzle";
            case 62 -> "Trap";
            case 74 -> "Miniboss";
            case 82 -> "Fairy";
            case 18 -> "Blood";
            case 85 -> "Unknown";
            default -> null;
        };
    }

    private static byte color(byte[] colors, int x, int z) {
        return x < 0 || z < 0 || x >= 128 || z >= 128 ? -1 : colors[x + (z << 7)];
    }

    private static long pack(int x, int z) {
        return ((long) x << 32) | (z & 0xffffffffL);
    }

    // top left corners of one rooms parts: a part next to it in the same colour with the gap between filled
    static List<int[]> segments(byte[] colors, int x, int z, int size, byte c) {
        List<int[]> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        out.add(new int[]{x, z});
        seen.add(pack(x, z));
        queue.add(new int[]{x, z});
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            int[][] next = {
                    color(colors, p[0] - 1, p[1]) == c ? new int[]{p[0] - size - 4, p[1]} : null,
                    color(colors, p[0], p[1] - 1) == c ? new int[]{p[0], p[1] - size - 4} : null,
                    color(colors, p[0] + size, p[1]) == c ? new int[]{p[0] + size + 4, p[1]} : null,
                    color(colors, p[0], p[1] + size) == c ? new int[]{p[0], p[1] + size + 4} : null};
            for (int[] n : next) {
                if (n == null || !seen.add(pack(n[0], n[1]))) continue;
                out.add(n);
                queue.add(n);
            }
        }
        return out;
    }

    // pure, for tests: every room the map shows, by grid cell
    static List<Room> read(byte[] colors, int[] mapEntrance, int size, int[] physicalEntrance) {
        List<Room> out = new ArrayList<>();
        Set<Long> done = new HashSet<>();
        int step = size + 4, nwX = Math.floorMod(mapEntrance[0], step), nwZ = Math.floorMod(mapEntrance[1], step);
        for (int x = nwX; x < 128; x += step) {
            for (int z = nwZ; z < 128; z += step) {
                if (done.contains(pack(x, z))) continue;
                byte c = color(colors, x, z);
                String kind = kindOf(c);
                if (kind == null) continue;
                List<int[]> parts = kind.equals("Room") ? segments(colors, x, z, size, c) : List.of(new int[]{x, z});
                List<int[]> cells = new ArrayList<>();
                for (int[] part : parts) {
                    done.add(pack(part[0], part[1]));
                    int px = (part[0] - mapEntrance[0]) / step * 32 + physicalEntrance[0], pz = (part[1] - mapEntrance[1]) / step * 32 + physicalEntrance[1];
                    int i = Dungeon.index(px), j = Dungeon.index(pz);
                    if (i >= 0 && i < Dungeon.CELLS && j >= 0 && j < Dungeon.CELLS) cells.add(new int[]{i, j});
                }
                if (!cells.isEmpty()) out.add(new Room(kind, cells));
            }
        }
        return out;
    }

    // where a block in the world is on hypixels map, in map pixels. from skyblocker getMapPosFromPhysical.
    // null till the entrance is found on both
    static double[] mapPos(double x, double z) {
        if (physicalEntrance == null || mapEntrance == null || mapRoomSize == 0) return null;
        double step = mapRoomSize + 4;
        return new double[]{(x - physicalEntrance[0]) / 32.0 * step + mapEntrance[0], (z - physicalEntrance[1]) / 32.0 * step + mapEntrance[1]};
    }

    // twice a second while in a run, nothing in the boss or outside
    public static List<Room> rooms() {
        Minecraft mc = Minecraft.getInstance();
        if (!DungeonRun.inDungeon() || DungeonRun.inBoss() || mc.player == null || mc.level == null) {
            physicalEntrance = mapEntrance = null;
            mapRoomSize = 0;
            return rooms = List.of();
        }
        if (mc.player.tickCount == lastTick || Math.abs(mc.player.tickCount - lastTick) < 10) return rooms;
        lastTick = mc.player.tickCount;
        if (physicalEntrance == null) {
            for (var e : mc.level.entitiesForRendering()) {
                if (e instanceof ArmorStand st && st.hasCustomName() && st.getCustomName() != null && st.getCustomName().getString().contains("Mort")) {
                    physicalEntrance = DungeonDoors.physicalRoom(st.getX(), st.getZ());
                    break;
                }
            }
            if (physicalEntrance == null) return rooms;
        }
        MapItemSavedData map = DungeonDoors.map(mc);
        if (map == null) return rooms;
        if (mapEntrance == null || mapRoomSize == 0) {
            for (MapDecoration d : map.getDecorations()) {
                if (!d.type().value().equals(MapDecorationTypes.FRAME.value())) continue;
                int[] e = DungeonDoors.findEntrance(map.colors, (d.x() >> 1) + 64, (d.y() >> 1) + 64);
                if (e != null) {
                    mapEntrance = new int[]{e[0], e[1]};
                    mapRoomSize = e[2];
                }
                break;
            }
            if (mapEntrance == null) return rooms;
        }
        return rooms = read(map.colors, mapEntrance, mapRoomSize, physicalEntrance);
    }
}
