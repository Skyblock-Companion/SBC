// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.nav;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

// waypoints in groups: yours (saved, shareable as text) + live ones other features publish in their own color
// (fairy souls, visitors, secrets...). all drawn the same way
public final class Waypoints {
    public record Point(String name, String island, Vec3 pos, int color, String group, double radius) {}

    private static final Map<String, Supplier<List<Point>>> PROVIDERS = new LinkedHashMap<>();
    private static final com.google.gson.Gson GSON = new com.google.gson.Gson();

    private Waypoints() {}

    // a feature publishes waypoints it works out (not saved), drawn + navigable like saved ones
    public static void provider(String id, Supplier<List<Point>> points) {
        PROVIDERS.put(id, points);
    }

    public static Store.WpGroup group(String name) {
        return Store.I.waypoints.computeIfAbsent(name, k -> {
            Store.WpGroup g = new Store.WpGroup();
            g.name = k;
            return g;
        });
    }

    public static void add(String group, String name, String island, Vec3 pos) {
        Store.WpGroup g = group(group);
        g.points.removeIf(p -> p.name.equalsIgnoreCase(name) && p.island.equals(island));
        Store.WpPoint p = new Store.WpPoint();
        p.name = name;
        p.island = island;
        p.x = pos.x;
        p.y = pos.y;
        p.z = pos.z;
        g.points.add(p);
        Store.markDirty();
    }

    public static boolean remove(String name) {
        boolean any = false;
        for (Store.WpGroup g : Store.I.waypoints.values()) any |= g.points.removeIf(p -> p.name.equalsIgnoreCase(name));
        if (any) Store.markDirty();
        return any;
    }

    public static void clear(String group) {
        if (group == null || group.isEmpty()) Store.I.waypoints.clear();
        else Store.I.waypoints.remove(group);
        Store.markDirty();
    }

    // every waypoint on every island, saved first then live
    public static List<Point> all() {
        List<Point> out = new ArrayList<>();
        for (Store.WpGroup g : Store.I.waypoints.values()) {
            if (!g.visible || g.ordered) continue;
            for (Store.WpPoint p : g.points) out.add(new Point(p.name, p.island, new Vec3(p.x, p.y, p.z), p.color != 0 ? p.color : g.color, g.name, g.radius));
        }
        for (var e : PROVIDERS.entrySet()) {
            try {
                out.addAll(e.getValue().get());
            } catch (Throwable t) {
                io.github.zmuzy.sbc.SbcMod.LOG.warn("waypoint provider {} failed", e.getKey(), t);
            }
        }
        return out;
    }

    public static Point find(String island, String name) {
        Point any = null;
        for (Point p : all()) {
            if (!p.name().equalsIgnoreCase(name)) continue;
            if (island == null || island.isEmpty() || p.island().isEmpty() || p.island().equals(island)) return p;
            any = p;
        }
        return any;
    }

    public static boolean go(String name) {
        Point p = find(GameState.island, name);
        if (p == null) return false;
        Navigator.navigate(new Navigator.Target(p.island(), p.name(), p.pos()));
        return true;
    }

    // draws waypoints on your island, the ones youre standing at are hidden
    public static void draw(WorldDraw w) {
        String look = choice("look", "Box and beam");
        for (Point p : all()) {
            if (!p.island().isEmpty() && !p.island().equals(GameState.island)) continue;
            double d = p.pos().distanceTo(w.eye);
            if (d < p.radius()) continue;
            if (!w.visible(p.pos())) continue;
            shown(w, p.pos(), p.color(), p.name(), look);
        }
        for (Store.WpGroup g : Store.I.waypoints.values()) if (g.visible && g.ordered) drawOrdered(w, g, look);
    }

    // skyhanni drawWaypointFilled + drawDynamicText, skyblocker NamedWaypoint (name, distance under it) +
    // Waypoint.Type for the other looks
    private static void shown(WorldDraw w, Vec3 pos, int color, String name, String look) {
        Vec3 corner = pos.add(-0.5, 0, -0.5);
        switch (look) {
            case "Box" -> w.waypoint(corner, color, name, false, true);
            case "Outline" -> {
                Vec3 c = new Vec3(Math.floor(corner.x), Math.floor(corner.y), Math.floor(corner.z));
                w.box(new AABB(c.x, c.y, c.z, c.x + 1, c.y + 1, c.z + 1), color, false, true);
                w.dynamicText(c.add(0.5, 1, 0.5), name, 1.0, 0);
            }
            default -> w.waypoint(corner, color, name, true, true);
        }
    }

    // --- ordered groups: skyblocker WaypointGroup.tick + OrderedNamedWaypoint ---

    private static final int PREVIOUS = 0xFFFF0000, CURRENT = 0xFFFFFFFF, NEXT = 0xFF00FF00;

    private static List<Store.WpPoint> here(Store.WpGroup g) {
        List<Store.WpPoint> out = new ArrayList<>();
        for (Store.WpPoint p : g.points) if (p.island.isEmpty() || p.island.equals(GameState.island)) out.add(p);
        return out;
    }

    // which waypoint a group is at after standing at x, y, z. pure
    static int reached(int current, double[][] points, double x, double y, double z, double radius, boolean backwards, boolean skipping) {
        int size = points.length, now = current;
        for (int i = 0; i < size; i++) {
            int next = (now + 1) % size, previous = (now - 1 + size) % size;
            boolean notBackwards = backwards || i == next, notSkipping = skipping || i == next || i == previous;
            double dx = points[i][0] - x, dy = points[i][1] - y, dz = points[i][2] - z;
            if (notBackwards && notSkipping && dx * dx + dy * dy + dz * dz < radius * radius) now = i;
        }
        return now;
    }

    public static void tick(net.minecraft.client.Minecraft mc) {
        if (mc.player == null) return;
        double radius = number("radius", 2);
        boolean back = bool("backwards", true), skip = bool("skip", true);
        for (Store.WpGroup g : Store.I.waypoints.values()) {
            if (!g.visible || !g.ordered) continue;
            List<Store.WpPoint> pts = here(g);
            if (pts.isEmpty()) continue;
            double[][] at = new double[pts.size()][];
            for (int i = 0; i < at.length; i++) at[i] = new double[]{pts.get(i).x, pts.get(i).y, pts.get(i).z};
            g.current = reached(Math.min(g.current, at.length - 1), at, mc.player.getX(), mc.player.getY(), mc.player.getZ(), radius, back, skip);
        }
    }

    private static void drawOrdered(WorldDraw w, Store.WpGroup g, String look) {
        List<Store.WpPoint> pts = here(g);
        int size = pts.size();
        if (size == 0) return;
        int current = Math.min(g.current, size - 1), next = (current + 1) % size, previous = (current - 1 + size) % size;
        for (int i = 0; i < size; i++) {
            if (i != current && i != next && i != previous) continue;
            Store.WpPoint p = pts.get(i);
            Vec3 pos = new Vec3(p.x, p.y, p.z);
            if (w.visible(pos)) shown(w, pos, i == next ? NEXT : i == current ? CURRENT : PREVIOUS, (i + 1) + (p.name.isEmpty() ? "" : " " + p.name), look);
            if (i == next && bool("line", true)) w.line(w.eye.add(w.mc.player.getLookAngle()), pos, lineColor(), (float) number("line_width", 5), true);
        }
    }

    public static void resetOrder() {
        for (Store.WpGroup g : Store.I.waypoints.values()) g.current = 0;
    }

    // /sbc wp next + previous: the first ordered group on this island
    public static int step(int by) {
        for (Store.WpGroup g : Store.I.waypoints.values()) {
            if (!g.visible || !g.ordered) continue;
            int size = here(g).size();
            if (size == 0) continue;
            g.current = (Math.floorMod(g.current + by, size));
            return 1;
        }
        return 0;
    }

    public static int first() {
        resetOrder();
        return 1;
    }

    private static io.github.zmuzy.sbc.feature.Option option(String key) {
        var f = io.github.zmuzy.sbc.feature.Features.get("waypoints");
        return f == null ? null : f.option(key);
    }

    private static boolean bool(String key, boolean def) {
        var o = option(key);
        return o == null ? def : o.bool();
    }

    private static double number(String key, double def) {
        var o = option(key);
        return o == null ? def : o.number();
    }

    private static String choice(String key, String def) {
        var o = option(key);
        return o == null ? def : o.choice();
    }

    private static int lineColor() {
        var o = option("line_color");
        return o == null ? 0xFF00FF00 : o.color();
    }

    // one text holding a whole group, paste it into /sbc wp import on another pc
    public static String export(String group) {
        Store.WpGroup g = Store.I.waypoints.get(group);
        if (g == null) return "";
        return "SBCWP:" + Base64.getEncoder().encodeToString(GSON.toJson(g).getBytes(StandardCharsets.UTF_8));
    }

    // the text other tools read too: skyblocker Waypoints.toSkytilsBase64, one group
    public static String exportShared(String group) {
        Store.WpGroup g = Store.I.waypoints.get(group);
        if (g == null || g.points.isEmpty()) return "";
        com.google.gson.JsonObject cat = new com.google.gson.JsonObject();
        cat.addProperty("name", g.name);
        cat.addProperty("island", modeOf(g.points.get(0).island));
        com.google.gson.JsonArray list = new com.google.gson.JsonArray();
        for (Store.WpPoint p : g.points) {
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("x", (int) Math.floor(p.x));
            o.addProperty("y", (int) Math.floor(p.y));
            o.addProperty("z", (int) Math.floor(p.z));
            o.addProperty("name", p.name);
            o.addProperty("color", p.color != 0 ? p.color : g.color);
            o.addProperty("enabled", true);
            list.add(o);
        }
        cat.add("waypoints", list);
        com.google.gson.JsonArray cats = new com.google.gson.JsonArray();
        cats.add(cat);
        com.google.gson.JsonObject all = new com.google.gson.JsonObject();
        all.add("categories", cats);
        return Base64.getEncoder().encodeToString(all.toString().getBytes(StandardCharsets.UTF_8));
    }

    // reverse of GameStates MODE_TO_ISLAND
    private static String modeOf(String island) {
        return GameState.modeOf(island);
    }

    public static int importText(String text) {
        try {
            String t = text.trim();
            if (!t.startsWith("SBCWP:")) return -1;
            Store.WpGroup g = GSON.fromJson(new String(Base64.getDecoder().decode(t.substring(6)), StandardCharsets.UTF_8), Store.WpGroup.class);
            if (g == null || g.name.isEmpty()) return -1;
            Store.I.waypoints.put(g.name, g);
            Store.markDirty();
            return g.points.size();
        } catch (Exception e) {
            return -1;
        }
    }
}
