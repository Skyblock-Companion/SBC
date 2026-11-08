// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// secret routes: per room a list of steps, one per secret, with the path to walk, spots to etherwarp to,
// mine, blow up or interact with, and the secret itself. only the current step gets drawn
public final class Routes {
    public record Step(List<Vec3> path, List<Vec3> etherwarps, List<Vec3> mines, List<Vec3> interacts, List<Vec3> tnts, List<Vec3> pearls,
                       String type, Vec3 secret) {}

    public static final String FASTEST = "fastest";
    private static final List<String> FILES = List.of("fow", "3ppopka", "pearl", "normal");
    public static final List<String> SETS = List.of(FASTEST, "fow", "3ppopka", "pearl", "normal");
    // what a teleport costs vs walking, in blocks: aiming at the spot + the moment it takes
    private static final double TELEPORT_COST = 3;

    private static final Map<String, Map<String, List<Step>>> LOADED = new HashMap<>();
    private static final Map<String, List<Step>> BEST = new HashMap<>();

    private Routes() {}

    private static Vec3 pt(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        return new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
    }

    private static List<Vec3> pts(JsonObject o, String key) {
        List<Vec3> out = new ArrayList<>();
        if (o.has(key)) for (JsonElement e : o.getAsJsonArray(key)) out.add(pt(e));
        return out;
    }

    private static Map<String, List<Step>> set(String name) {
        return LOADED.computeIfAbsent(name, n -> {
            Map<String, List<Step>> out = new HashMap<>();
            try (var in = Routes.class.getResourceAsStream("/sbc/routes_" + n + ".json")) {
                JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
                for (var e : root.entrySet()) {
                    if (!e.getValue().isJsonArray()) continue;
                    List<Step> steps = new ArrayList<>();
                    for (JsonElement se : e.getValue().getAsJsonArray()) {
                        JsonObject o = se.getAsJsonObject();
                        JsonObject sec = o.has("secret") ? o.getAsJsonObject("secret") : null;
                        steps.add(new Step(pts(o, "locations"), pts(o, "etherwarps"), pts(o, "mines"), pts(o, "interacts"), pts(o, "tnts"), pts(o, "enderpearls"),
                                sec == null ? "" : sec.get("type").getAsString(), sec == null ? null : pt(sec.get("location"))));
                    }
                    out.put(e.getKey().toLowerCase(Locale.ROOT), steps);
                }
            } catch (Exception ex) {
                SbcMod.LOG.warn("Could not read secret routes {}", n, ex);
            }
            return out;
        });
    }

    private static boolean secretStep(Step s) {
        return s.secret() != null && !s.type().startsWith("exit") && !s.type().equals("dummy");
    }

    // route length: blocks walked + a fixed price per etherwarp or pearl
    static double cost(List<Step> steps) {
        double blocks = 0;
        for (Step s : steps) {
            for (int i = 1; i < s.path().size(); i++) blocks += s.path().get(i - 1).distanceTo(s.path().get(i));
            blocks += (s.etherwarps().size() + s.pearls().size()) * TELEPORT_COST;
        }
        return blocks;
    }

    // out of a rooms routes, the shortest of the ones getting the most secrets. a short route that skips some isnt faster
    static List<Step> fastest(List<List<Step>> routes) {
        List<Step> best = List.of();
        long mostSecrets = -1;
        double lowest = Double.MAX_VALUE;
        for (List<Step> r : routes) {
            long secrets = r.stream().filter(Routes::secretStep).count();
            double c = cost(r);
            if (secrets > mostSecrets || (secrets == mostSecrets && c < lowest)) {
                best = r;
                mostSecrets = secrets;
                lowest = c;
            }
        }
        return best;
    }

    public static List<Step> steps(String roomId) {
        String room = roomId.toLowerCase(Locale.ROOT), s = Config.I.routeSet;
        if (FASTEST.equals(s) || !SETS.contains(s)) {
            List<Step> known = BEST.get(room);
            if (known != null) return known;
            List<List<Step>> all = new ArrayList<>();
            for (String file : FILES) {
                List<Step> r = set(file).get(room);
                if (r != null) all.add(r);
            }
            List<Step> best = fastest(all);
            BEST.put(room, best);
            return best;
        }
        List<Step> r = set(s).get(roomId.toLowerCase(Locale.ROOT));
        if (r == null && !s.equals("fow")) r = set("fow").get(roomId.toLowerCase(Locale.ROOT));
        return r == null ? List.of() : r;
    }

    private static Vec3 block(Dungeon.Placed r, Vec3 rel) {
        Vec3 c = r.toActual(rel.x, rel.y, rel.z);
        return new Vec3(Math.floor(c.x), Math.floor(c.y), Math.floor(c.z));
    }

    // next step once you reach the current ones secret
    public static void advance(Dungeon.Placed r, Vec3 me) {
        List<Step> steps = steps(r.def.id());
        if (r.step >= steps.size()) return;
        Step s = steps.get(r.step);
        if (s.secret() == null) return;
        Vec3 at = block(r, s.secret()).add(0.5, 0.5, 0.5);
        double reach = s.type().equals("exit") ? 2.0 : 3.2;
        if (at.distanceTo(me) < reach) r.step++;
    }

    public static String info(Dungeon.Placed r) {
        List<Step> steps = steps(r.def.id());
        if (steps.isEmpty()) return "";
        if (r.step >= steps.size()) return "Route done";
        Step s = steps.get(r.step);
        return "Route " + (r.step + 1) + "/" + steps.size() + (s.type().isEmpty() ? "" : " · " + s.type());
    }

    private static void box(WorldDraw w, Vec3 at, int col, boolean filled) {
        w.box(new AABB(at.x, at.y, at.z, at.x + 1, at.y + 1, at.z + 1), col, filled, true);
    }

    // the walk drawn like the nav line: your color + width, starting just in front of your eyes, rounded corners
    private static void walkLine(WorldDraw w, Dungeon.Placed r, Step s) {
        List<Vec3> points = new ArrayList<>();
        for (Vec3 p : s.path()) points.add(block(r, p).add(0.5, 0.5, 0.5));
        if (s.secret() != null) points.add(block(r, s.secret()).add(0.5, 0.5, 0.5));
        if (points.isEmpty()) return;
        Vec3 me = w.mc.player.position(), eye = w.mc.player.getEyePosition();
        int start = dev.zmuzy.sbc.nav.Navigator.closestIndexAhead(points, me);
        Vec3 dir = points.get(start).subtract(eye);
        List<Vec3> route = new ArrayList<>();
        route.add(dir.lengthSqr() < 1e-4 ? eye : eye.add(dir.normalize().scale(0.8)).add(0, -0.35, 0));
        route.addAll(points.subList(start, points.size()));
        List<Vec3> smooth = dev.zmuzy.sbc.nav.Navigator.rounded(route);
        for (int i = 1; i < smooth.size(); i++) w.line(smooth.get(i - 1), smooth.get(i), Config.I.pathColor | 0xFF000000, 5f, Config.I.pathThroughWalls);
    }

    public static boolean draw(WorldDraw w, Dungeon.Placed r) {
        List<Step> steps = steps(r.def.id());
        if (steps.isEmpty()) return false;
        if (r.step >= steps.size()) return true;
        Step s = steps.get(r.step);
        walkLine(w, r, s);
        for (Vec3 p : s.etherwarps()) box(w, block(r, p), 0xFFA855F7, false);
        for (Vec3 p : s.mines()) box(w, block(r, p), 0xFFFFFF00, false);
        for (Vec3 p : s.interacts()) box(w, block(r, p), 0xFF3B82F6, false);
        for (Vec3 p : s.tnts()) box(w, block(r, p), 0xFFEF4444, true);
        for (Vec3 p : s.pearls()) box(w, block(r, p), 0xFF22D3EE, false);
        if (s.secret() != null) {
            Vec3 at = block(r, s.secret());
            int col = switch (s.type()) {
                case "item" -> 0xFF4ADE80;
                case "interact" -> 0xFF60A5FA;
                case "bat" -> 0xFF2DD4BF;
                case "exit" -> 0xFFF2F2F2;
                default -> 0xFFEF9F27;
            };
            box(w, at, col, true);
            w.label((r.step + 1) + " - " + s.type(), at.add(0.5, 1.4, 0.5), true);
        }
        return true;
    }
}
