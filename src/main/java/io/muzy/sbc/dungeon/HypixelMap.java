// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.dungeon;

import io.muzy.sbc.mc.mixin.MapRendererAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

// hypixels map from the last hotbar slot, pixel for pixel, as the dungeon card shows it. from skyblocker
// DungeonMap + DungeonMapTexture: the game's own map drawing, everyone's markers but yours, and yours as an
// arrow at where you really stand, turning with your view (hypixels marker only moves when the map updates)
public final class HypixelMap {
    private static final MapRenderState STATE = new MapRenderState();
    private static byte[] boundsOf;
    private static int[] bounds = {0, 0, 128, 128};

    private HypixelMap() {}

    // the part of the map with anything on it, so the rooms sit in the middle of the card
    private static int[] bounds(byte[] colors) {
        if (colors == boundsOf) return bounds;
        int x0 = 128, z0 = 128, x1 = -1, z1 = -1;
        for (int z = 0; z < 128; z++)
            for (int x = 0; x < 128; x++)
                if (colors[x + (z << 7)] != 0) {
                    if (x < x0) x0 = x;
                    if (x > x1) x1 = x;
                    if (z < z0) z0 = z;
                    if (z > z1) z1 = z;
                }
        boundsOf = colors;
        return bounds = x1 < 0 ? new int[]{0, 0, 128, 128} : new int[]{x0, z0, x1 + 1, z1 + 1};
    }

    private static int cropStart(int lo, int hi, int side) {
        int start = (lo + hi - side) / 2;
        return Math.max(0, Math.min(128 - side, start));
    }

    // false = no map to show (no run, the boss, or the map not there yet), the card draws its own
    public static boolean draw(GuiGraphicsExtractor g, int x, int y, int side) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || DungeonRun.inBoss()) return false;
        MapItemSavedData data = DungeonDoors.map(mc);
        if (data == null) return false;
        MapRooms.rooms();
        mc.getMapRenderer().extractRenderState(DungeonDoors.mapId(), data, STATE);
        double[] me = MapRooms.mapPos(mc.player.getX(), mc.player.getZ());
        // the game draws only the markers an item frame would show, which leaves out every player on a held map.
        // so each marker is drawn whatever its kind. ours replaces Hypixel's own white one when we know where you are
        STATE.decorations.clear();
        var render = (MapRendererAccess) mc.getMapRenderer();
        for (MapDecoration d : data.getDecorations()) {
            if (me != null && d.type().value().equals(MapDecorationTypes.FRAME.value())) continue;
            var marker = render.sbc$decoration(d);
            marker.renderOnFrame = true;
            STATE.decorations.add(marker);
        }
        int[] b = bounds(data.colors);
        int cx = cropStart(b[0], b[2], side), cz = cropStart(b[1], b[3], side);
        g.enableScissor(x, y, x + side, y + side);
        g.pose().pushMatrix();
        g.pose().translate(x - cx, y - cz);
        g.map(STATE);
        if (me != null) arrow(g, me[0], me[1], mc.player.getYRot());
        g.pose().popMatrix();
        g.disableScissor();
        return true;
    }

    // rows of the arrow from its tip: half widths, the last row split so it has a notch
    private static final int[] HALF = {0, 1, 1, 2, 2, 3, 3};

    // pointing where you look: yaw 0 is south, which is down on the map
    private static void arrow(GuiGraphicsExtractor g, double mx, double my, float yaw) {
        var p = g.pose();
        p.pushMatrix();
        p.translate((float) mx, (float) my);
        p.rotate((float) Math.toRadians(yaw + 180));
        shape(g, 1, 0xFF000000);
        shape(g, 0, 0xFF5FEA8C);
        p.popMatrix();
    }

    private static void shape(GuiGraphicsExtractor g, int grow, int color) {
        for (int i = 0; i < HALF.length; i++) {
            int y = i - 4, h = HALF[i] + grow;
            if (i == HALF.length - 1 && grow == 0) {
                g.fill(-h, y, -1, y + 1, color);
                g.fill(2, y, h + 1, y + 1, color);
            } else g.fill(-h, y - (i == 0 ? grow : 0), h + 1, y + 1 + (i == HALF.length - 1 ? grow : 0), color);
        }
    }
}
