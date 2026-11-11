// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.nav;

import io.muzy.sbc.core.Config;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.ui.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// guides you to a target with a line along the islands walkable graph (like skyhannis nav), a pillar + label
// at the end, and a "warp first" hint when its on another island
public final class Navigator {
    private Navigator() {}

    public record Target(String island, String name, Vec3 pos) {}

    // nodes the path must avoid, null = none. skyhanni IslandGraphs.disableNodes, for the rifts temporal pillars
    public static java.util.function.Predicate<Graph.Node> avoid;

    private static Target target;
    private static List<Vec3> path = List.of();
    private static long lastPath;
    private static Vec3 lastFrom;
    public static String status = "";
    public static String warpHint = "";
    private static Target prompted;

    // islands with no warp in their graph, by their usual warp
    private static final java.util.Map<String, String> WARPS = java.util.Map.ofEntries(
            java.util.Map.entry("HUB", "/warp hub"), java.util.Map.entry("CRIMSON_ISLE", "/warp crimson"), java.util.Map.entry("DWARVEN_MINES", "/warp mines"),
            java.util.Map.entry("GARDEN", "/warp garden"), java.util.Map.entry("PRIVATE_ISLAND", "/warp island"), java.util.Map.entry("THE_PARK", "/warp park"),
            java.util.Map.entry("GALATEA", "/warp galatea"), java.util.Map.entry("DUNGEON_HUB", "/warp dungeon_hub"), java.util.Map.entry("SPIDER_DEN", "/warp spider"),
            java.util.Map.entry("THE_END", "/warp end"), java.util.Map.entry("CRYSTAL_HOLLOWS", "/warp crystals"), java.util.Map.entry("THE_RIFT", "/warp rift"),
            java.util.Map.entry("GOLD_MINES", "/warp gold"), java.util.Map.entry("DEEP_CAVERNS", "/warp deep"), java.util.Map.entry("THE_FARMING_ISLANDS", "/warp barn"),
            java.util.Map.entry("WINTER", "/warp jerry"), java.util.Map.entry("BACKWATER_BAYOU", "/warp backwater"), java.util.Map.entry("GLACITE_TUNNELS", "/warp base"));

    // closest warp to a target on another island ("/warp crimson"), or empty
    public static String warpFor(Target t) {
        Graph g = Repo.graph(t.island());
        Graph.Node w = g == null ? null : g.warpNear(t.pos());
        if (w != null && w.name().startsWith("/warp")) return w.name();
        String fixed = WARPS.get(t.island());
        if (fixed != null) return fixed;
        // skyhanni Warps.json (warpLocation): closest warp point on that island
        String data = io.muzy.sbc.events.diana.DianaData.closestWarpCommand(t.island(), t.pos());
        return data == null ? "" : data;
    }

    // runs the warp to the targets island (J key)
    public static boolean warpNow() {
        Minecraft mc = Minecraft.getInstance();
        if (target == null || warpHint.isEmpty() || mc.getConnection() == null) return false;
        mc.getConnection().sendCommand(warpHint.substring(1));
        return true;
    }

    public static List<Vec3> path() {
        return path;
    }

    public static Target target() {
        return target;
    }

    public static void navigate(Target t) {
        target = t;
        path = List.of();
        lastPath = 0;
        Toasts.push("Navigating", t.name() + (t.island().isEmpty() ? "" : " - " + GameState.pretty(t.island())), 0xFF39D0FF);
    }

    // same guide with a better idea of where it ends, no new toast. a guess thats still being worked out moves
    // with every particle
    public static void retarget(Target t) {
        if (target == null) {
            navigate(t);
            return;
        }
        target = t;
        lastPath = 0;
    }

    public static void stop() {
        target = null;
        path = List.of();
        status = "";
        warpHint = "";
    }

    // navigate to a named graph node or learned npc on any island
    public static boolean navigateTo(String island, String name) {
        Graph g = Repo.graph(island);
        if (g != null && !g.isEmpty()) {
            Graph.Node n = g.byName(name);
            if (n != null) {
                navigate(new Target(island, n.name(), n.pos()));
                return true;
            }
        }
        double[] p = Store.I.npcs.get(island + "|" + name);
        if (p != null) {
            navigate(new Target(island, name, new Vec3(p[0], p[1], p[2])));
            return true;
        }
        Waypoints.Point wp = Waypoints.find(island, name);
        if (wp != null && (wp.island().isEmpty() || wp.island().equals(island))) {
            navigate(new Target(wp.island(), wp.name(), wp.pos()));
            return true;
        }
        return false;
    }

    public static void tick(Minecraft mc) {
        if (target == null || mc.player == null) return;
        Vec3 me = mc.player.position();
        if (!target.island().isEmpty() && !target.island().equals(GameState.island)) {
            warpHint = warpFor(target);
            status = "Go to " + GameState.pretty(target.island()) + (warpHint.isEmpty() ? "" : "  (press J: " + warpHint + ")");
            path = List.of();
            // once per target: clickable [WARP] in chat
            if (!warpHint.isEmpty() && prompted != target && GameState.inSkyBlock) {
                prompted = target;
                var msg = net.minecraft.network.chat.Component.literal("§b[SkyBlock Companion] §f" + target.name() + " §7is on §e" + GameState.pretty(target.island()) + " ")
                        .append(net.minecraft.network.chat.Component.literal("§a§l[WARP]").withStyle(st -> st
                                .withClickEvent(new net.minecraft.network.chat.ClickEvent.RunCommand(warpHint))
                                .withHoverEvent(new net.minecraft.network.chat.HoverEvent.ShowText(net.minecraft.network.chat.Component.literal("Run " + warpHint)))))
                        .append(net.minecraft.network.chat.Component.literal(" §8(or press J)"));
                mc.player.sendSystemMessage(msg);
            }
            return;
        }
        warpHint = "";
        double dist = me.distanceTo(target.pos());
        status = target.name() + "  " + (int) dist + "m";
        if (dist < 3.5) {
            Toasts.push("Arrived", target.name(), 0xFF4CE08A);
            if (io.muzy.sbc.feature.Features.on("navtitle")) {
                mc.gui.hud.setTimes(5, 30, 10);
                mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("\u00a7bArrived"));
                mc.gui.hud.setSubtitle(net.minecraft.network.chat.Component.literal("\u00a7f" + target.name()));
            }
            stop();
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPath > 1500 || lastFrom == null || lastFrom.distanceToSqr(me) > 36) {
            lastPath = now;
            lastFrom = me;
            Graph g = Repo.graph(GameState.island);
            if (g != null && !g.isEmpty()) {
                List<Vec3> p = g.path(g.nearest(me), g.nearest(target.pos()), avoid);
                List<Vec3> full = new ArrayList<>(p.size() + 1);
                full.addAll(p);
                full.add(target.pos());
                path = full;
            } else {
                path = List.of(target.pos());
            }
        }
    }

    // world gizmos for this frame (called while the levels gizmo collector is active)
    public static void emitGizmos(Minecraft mc) {
        if (target == null || mc.player == null || !Config.I.pathLine) return;
        if (!target.island().isEmpty() && !target.island().equals(GameState.island)) return;
        int color = Config.I.pathColor;
        boolean onTop = false;
        Vec3 me = mc.player.position().add(0, 0.1, 0);

        // skyhanni style route: one solid line from your eyes along the path nodes, rounded at every corner (a curve
        // between the midpoints of the 2 segments), same color + width as theirs
        List<Vec3> pts = path;
        int start = Math.max(0, closestIndexAhead(pts, me));
        List<Vec3> route = new ArrayList<>();
        Vec3 eye = mc.player.getEyePosition();
        Vec3 firstAhead = start < pts.size() ? pts.get(start).add(0, 0.5, 0) : target.pos().add(0, 0.5, 0);
        Vec3 dir = firstAhead.subtract(eye);
        // start just in front of + below the eyes so you see a line, not a dot in the middle of the screen
        route.add(dir.lengthSqr() < 1e-4 ? eye : eye.add(dir.normalize().scale(0.8)).add(0, -0.35, 0));
        double len = 0;
        for (int i = start; i < pts.size() && len < 200; i++) {
            Vec3 p = pts.get(i).add(0, 0.5, 0);
            len += route.get(route.size() - 1).distanceTo(p);
            route.add(p);
        }
        if (start >= pts.size() - 1) route.add(target.pos().add(0, 0.5, 0));
        List<Vec3> smooth = rounded(route);
        for (int i = 1; i < smooth.size(); i++) {
            var line = Gizmos.line(smooth.get(i - 1), smooth.get(i), color | 0xFF000000, 5.0f);
            if (onTop) line.setAlwaysOnTop();
        }

        // destination: glowing beam into the sky (through walls from anywhere), pulsing box on the spot, label with distance
        Vec3 t = target.pos();
        int rgb = color & 0xFFFFFF;
        float pulse = (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 250.0));
        double top = Math.max(t.y + 120, 320);
        var core = Gizmos.cuboid(new AABB(t.x - 0.35, t.y, t.z - 0.35, t.x + 0.35, top, t.z + 0.35), GizmoStyle.fill((0x99 << 24) | rgb));
        var spine = Gizmos.line(t, new Vec3(t.x, top, t.z), (0xFF << 24) | 0xFFFFFF, 3.0f);
        double g = 0.6 + 0.25 * pulse;
        var spot = Gizmos.cuboid(new AABB(t.x - g, t.y, t.z - g, t.x + g, t.y + 2.2, t.z + g),
                GizmoStyle.strokeAndFill((0xFF << 24) | rgb, 3.0f, ((int) (0x40 + 0x40 * pulse) << 24) | rgb));
        if (onTop) {
            core.setAlwaysOnTop();
            spine.setAlwaysOnTop();
            spot.setAlwaysOnTop();
        }
        int dist = (int) me.distanceTo(t);
        var label = Gizmos.billboardText(target.name() + "  §7" + dist + "m", t.add(0, 2.6, 0), TextGizmo.Style.whiteAndCentered().withScale(0.32f * Math.max(1f, Math.min(3f, dist / 10f))));
        label.setAlwaysOnTop();
    }

    // rounds each polyline corner with a quadratic curve from one segment middle to the next
    public static List<Vec3> rounded(List<Vec3> p) {
        if (p.size() < 3) return p;
        List<Vec3> out = new ArrayList<>();
        out.add(p.get(0));
        for (int i = 1; i < p.size() - 1; i++) {
            Vec3 a = p.get(i - 1).add(p.get(i)).scale(0.5), c = p.get(i), b = p.get(i).add(p.get(i + 1)).scale(0.5);
            if (i == 1) a = p.get(0);
            int steps = 6;
            for (int k = 0; k <= steps; k++) {
                double t = (double) k / steps, u = 1 - t;
                out.add(a.scale(u * u).add(c.scale(2 * u * t)).add(b.scale(t * t)));
            }
        }
        out.add(p.get(p.size() - 1));
        return out;
    }

    public static int closestIndexAhead(List<Vec3> path, Vec3 me) {
        if (path.isEmpty()) return 0;
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < path.size(); i++) {
            double d = path.get(i).distanceToSqr(me);
            if (d < bd) {
                bd = d;
                best = i;
            }
        }
        // skip the point were standing on so the line always points forward
        return Math.min(path.size() - 1, best + (bd < 9 ? 1 : 0));
    }
}
