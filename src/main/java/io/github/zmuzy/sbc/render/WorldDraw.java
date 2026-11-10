// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.render;

import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// drawing in the world for features: lines, boxes, beacon beams, floating labels, each with an optional
// through walls flag + culling (stuff past range or behind you gets skipped)
public final class WorldDraw {
    public final Minecraft mc;
    public final Vec3 eye;
    private final Vec3 look;
    // nothing farther than this many blocks gets drawn
    public double range = 400;

    public WorldDraw(Minecraft mc) {
        this.mc = mc;
        this.eye = mc.player == null ? Vec3.ZERO : mc.player.getEyePosition();
        this.look = mc.player == null ? new Vec3(0, 0, 1) : mc.player.getLookAngle();
    }

    // skyhanni exactPlayerCrosshairLocation: 2 blocks in front of the eye where you look, lines start there
    public Vec3 crosshair() {
        return eye.add(look.scale(2));
    }

    // in range and not clearly behind the camera
    public boolean visible(Vec3 p) {
        Vec3 d = p.subtract(eye);
        double len = d.length();
        if (len > range) return false;
        return len < 4 || d.scale(1 / len).dot(look) > -0.35;
    }

    // 1 up to near blocks, fading to 0.25 at far
    public float fade(Vec3 p, double near, double far) {
        double d = p.distanceTo(eye);
        if (d <= near) return 1f;
        if (d >= far) return 0.25f;
        return (float) (1 - 0.75 * (d - near) / (far - near));
    }

    private static int withAlpha(int argb, float mul) {
        int a = Math.max(0, Math.min(255, Math.round((argb >>> 24) * mul)));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int alpha(int rgb, float alpha) {
        int a = Math.max(0, Math.min(255, Math.round(255 * alpha)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }

    public void line(Vec3 a, Vec3 b, int argb, float width, boolean throughWalls) {
        if (!visible(a) && !visible(b)) return;
        var p = Gizmos.line(a, b, argb | 0xFF000000, width);
        if (throughWalls) p.setAlwaysOnTop();
    }

    public void box(AABB box, int argb, boolean filled, boolean throughWalls) {
        if (!visible(box.getCenter())) return;
        var st = filled ? GizmoStyle.strokeAndFill(argb | 0xFF000000, 2.5f, withAlpha(argb, 0.35f)) : GizmoStyle.stroke(argb | 0xFF000000, 2.5f);
        var p = Gizmos.cuboid(box, st);
        if (throughWalls) p.setAlwaysOnTop();
    }

    // skyhanni drawFilledBoundingBox with its own alpha (0..1), no outline
    public void filledBox(AABB box, int rgb, float alphaValue, boolean throughWalls) {
        if (!visible(box.getCenter())) return;
        var p = Gizmos.cuboid(box, GizmoStyle.fill(alpha(rgb, alphaValue)));
        if (throughWalls) p.setAlwaysOnTop();
    }

    // beacon beam from the spot to the top of the world (skyhanni renderBeaconBeam / skyblocker
    // submitFilledBoxWithBeaconBeam): bright inner core + wider see through outer column
    public void beacon(Vec3 pos, int argb, boolean throughWalls) {
        if (!visible(pos)) return;
        double top = 319;
        var outer = Gizmos.cuboid(new AABB(pos.x - 0.25, pos.y, pos.z - 0.25, pos.x + 0.25, top, pos.z + 0.25), GizmoStyle.fill(alpha(argb, 0.25f)));
        var core = Gizmos.cuboid(new AABB(pos.x - 0.1, pos.y, pos.z - 0.1, pos.x + 0.1, top, pos.z + 0.1), GizmoStyle.fill(alpha(argb, 0.8f)));
        if (throughWalls) {
            outer.setAlwaysOnTop();
            core.setAlwaysOnTop();
        }
    }

    // skyhanni drawWaypointFilled: the block as a filled box with alpha 0.1 + 0.005 * dist², clamped to 0.2..1
    // (far ones solid, close ones faint)
    public void waypointBox(Vec3 block, int argb, boolean beacon, boolean throughWalls, double extraSize) {
        Vec3 corner = new Vec3(Math.floor(block.x), Math.floor(block.y), Math.floor(block.z));
        double distSq = corner.add(0.5, 0.5, 0.5).distanceToSqr(eye);
        float a = (float) Math.max(0.2, Math.min(1.0, 0.1 + 0.005 * distSq));
        AABB box = new AABB(corner.x - extraSize, corner.y - extraSize, corner.z - extraSize, corner.x + 1 + extraSize, corner.y + 1 + extraSize, corner.z + 1 + extraSize).inflate(0.001);
        filledBox(box, argb, a, throughWalls);
        if (beacon && distSq > 25) beacon(new Vec3(corner.x + 0.5, corner.y + 1, corner.z + 0.5), argb, throughWalls);
    }

    // full waypoint: filled block (+ beam), name above, distance under the name in yellow (skyblocker
    // NamedWaypoint says "12m", here "12 blocks")
    public void waypoint(Vec3 block, int argb, String name, boolean beacon, boolean throughWalls) {
        waypoint(block, argb, name, beacon, throughWalls, true);
    }

    // textThroughWalls off = name + distance stop at a wall too, nothing of the waypoint shows where its hidden
    public void waypoint(Vec3 block, int argb, String name, boolean beacon, boolean throughWalls, boolean textThroughWalls) {
        if (!visible(block)) return;
        waypointBox(block, argb, beacon, throughWalls, 0);
        Vec3 corner = new Vec3(Math.floor(block.x), Math.floor(block.y), Math.floor(block.z));
        Vec3 textAt = corner.add(0.5, 1.7, 0.5);
        if (!textThroughWalls && !seen(textAt)) return;
        double d = corner.add(0.5, 0.5, 0.5).distanceTo(eye);
        if (name != null && !name.isEmpty()) dynamicText(textAt, name, 1.5, 0);
        dynamicText(textAt, "§e" + Math.round(d) + " blocks", 1.0, -1);
    }

    // skyhanni drawDynamicText: text gets moved along the line to the viewer so its drawn max 50 blocks away and
    // scaled with that distance, keeps the same size on screen
    public void dynamicText(Vec3 location, String text, double scaleMultiplier, double lineOffset) {
        if (mc.player == null) return;
        double distToPlayer = Math.max(5.0, location.distanceTo(eye));
        if (distToPlayer < 4.5) return;
        double distRender = Math.min(distToPlayer, 50.0);
        double factor = distRender / distToPlayer;
        // skyhanni resultY lifts the text by 20 * dist / 300 blocks before projecting
        Vec3 lifted = new Vec3(location.x, location.y + 20 * distToPlayer / 300, location.z);
        Vec3 at = eye.add(lifted.subtract(eye).scale(factor));
        if (!visible(at)) return;
        float scale = (float) (0.28 * (distRender / 12.0) * scaleMultiplier);
        // one text line is about 0.75 * scale world units in the gizmo renderer
        at = at.add(0, lineOffset * 0.8 * scale, 0);
        var p = Gizmos.billboardText(text, at, TextGizmo.Style.whiteAndCentered().withScale(scale));
        p.setAlwaysOnTop();
    }

    // nothing solid between the eye and this point. a hit within a block of it counts as seeing it
    public boolean seen(Vec3 p) {
        if (mc.level == null || mc.player == null) return false;
        var hit = mc.level.clip(new net.minecraft.world.level.ClipContext(eye, p, net.minecraft.world.level.ClipContext.Block.VISUAL, net.minecraft.world.level.ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS || hit.getLocation().distanceToSqr(p) <= 1;
    }

    // floating text at a point, skyhanni drawDynamicText with multiplier 1.5 (what its waypoints use). behind a
    // wall only with throughWalls
    public void label(String text, Vec3 pos, boolean throughWalls) {
        if (!throughWalls && !seen(pos)) return;
        dynamicText(pos, text, 1.5, 0);
    }

    // 2nd line under a label
    public void labelBelow(String text, Vec3 pos) {
        dynamicText(pos, text, 1.2, -1);
    }
}
