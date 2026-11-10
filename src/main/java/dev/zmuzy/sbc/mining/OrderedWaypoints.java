// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mining;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni OrderedWaypoints + ColeweightWaypointFormat: route of numbered blocks loaded from clipboard
// or saved routes
// a few shown at a time, moves on as you reach them. their other format is skytils, skipped bc nothing else
// here reads it
public final class OrderedWaypoints {
    static final String ID = "mining_ordered_waypoints";
    // eye height in skyhannis setup line, SNEAKING_EYE_HEIGHT + STANDING_EYE_HEIGHT
    private static final double SNEAKING_EYE = 1.27, STANDING_EYE = 1.62;
    private static final List<Vec3> ROUTE = new ArrayList<>();
    private static final List<Integer> RENDER = new ArrayList<>();
    private static int index, lastCloser;
    private static String lastIsland = "";

    private OrderedWaypoints() {}

    // skyhanni ColeweightWaypointFormat.load: list of x, y, z with options.name as the number. null if its something else
    static List<Vec3> parse(String text) {
        try {
            JsonArray a = JsonParser.parseString(text).getAsJsonArray();
            List<double[]> read = new ArrayList<>();
            for (JsonElement e : a) {
                JsonObject o = e.getAsJsonObject();
                read.add(new double[]{Integer.parseInt(o.getAsJsonObject("options").get("name").getAsString()), o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt()});
            }
            read.sort((x, y) -> Double.compare(x[0], y[0]));
            List<Vec3> out = new ArrayList<>();
            for (double[] r : read) out.add(new Vec3(r[1], r[2], r[3]));
            return out.isEmpty() ? null : out;
        } catch (Exception e) {
            return null;
        }
    }

    // from skyhanni ColeweightWaypointFormat.export
    static String export(List<Vec3> route) {
        JsonArray a = new JsonArray();
        for (int i = 0; i < route.size(); i++) {
            JsonObject o = new JsonObject();
            o.addProperty("x", (int) route.get(i).x);
            o.addProperty("y", (int) route.get(i).y);
            o.addProperty("z", (int) route.get(i).z);
            o.addProperty("r", 0);
            o.addProperty("g", 1);
            o.addProperty("b", 0);
            JsonObject options = new JsonObject();
            options.addProperty("name", String.valueOf(i + 1));
            o.add("options", options);
            a.add(o);
        }
        return a.toString();
    }

    private static Vec3 player() {
        return MiningEnv.world.playerPos();
    }

    private static double dist(Vec3 block) {
        return block.distanceTo(player());
    }

    private static boolean b(String key) {
        return MiningPack.on(ID, "mining_ordered_waypoints." + key);
    }

    private static int range() {
        return MiningPack.number(ID, "mining_ordered_waypoints.range", 3);
    }

    private static void load(String name) {
        List<Vec3> route;
        if (name.isEmpty()) {
            route = parse(Minecraft.getInstance().keyboardHandler.getClipboard());
            if (route == null) {
                MiningPack.chat("§cThere was an error parsing waypoints. Please make sure they are properly formatted.");
                return;
            }
        } else {
            List<double[]> saved = Store.I.orderedRoutes.get(name);
            if (saved == null) {
                MiningPack.chat("§cRoute " + name + " doesn't exist. Saved routes: " + String.join(", ", Store.I.orderedRoutes.keySet()));
                return;
            }
            route = new ArrayList<>();
            for (double[] p : saved) route.add(new Vec3(p[0], p[1], p[2]));
        }
        ROUTE.clear();
        ROUTE.addAll(route);
        index = 0;
        double best = Double.MAX_VALUE;
        for (int i = 0; i < ROUTE.size(); i++) {
            double d = dist(ROUTE.get(i));
            if (d < best) {
                best = d;
                index = i;
            }
        }
        RENDER.clear();
        MiningPack.chat("Loaded " + ROUTE.size() + " ordered waypoints!");
        if (!Features.get(ID).enabled()) Features.get(ID).setEnabled(true);
    }

    private static void unload() {
        if (!ROUTE.isEmpty()) MiningPack.chat("Unloaded ordered waypoints.");
        ROUTE.clear();
        RENDER.clear();
        index = lastCloser = 0;
    }

    private static void increment(int by) {
        index = ROUTE.isEmpty() ? 0 : Math.floorMod(index + by, ROUTE.size());
    }

    // from skyhanni decideWaypoints
    private static void decide() {
        RENDER.clear();
        if (ROUTE.isEmpty()) return;
        int size = ROUTE.size();
        if (b("auto_skip")) {
            int closest = -1;
            double best = range();
            for (int i = 0; i < size; i++) {
                double d = dist(ROUTE.get(i));
                if (d < best) {
                    best = d;
                    closest = i;
                }
            }
            if (closest > index) {
                index = lastCloser = closest;
                return;
            }
        }
        Set<Integer> order = new LinkedHashSet<>();
        order.add(Math.floorMod(index - 1, size));
        double to1 = Double.POSITIVE_INFINITY;
        if (index < size) {
            to1 = dist(ROUTE.get(index));
            order.add(index);
        }
        int next = (index + 1) % size;
        double to2 = dist(ROUTE.get(next));
        order.add(next);
        int count = MiningPack.number(ID, "mining_ordered_waypoints.next_count", 2);
        for (int i = 1; i <= count - 1; i++) order.add((next + i) % size);
        if (lastCloser == index && to1 > to2 && to2 < range()) {
            increment(1);
            return;
        }
        if (to1 < range()) lastCloser = index;
        if (to2 < range()) increment(1);
        boolean setup = b("setup_mode"), all = b("show_all");
        int setupRange = MiningPack.number(ID, "mining_ordered_waypoints.setup_range", 16);
        for (int i = 0; i < size; i++) if (all || (setup && dist(ROUTE.get(i)) < setupRange)) order.add(i);
        RENDER.addAll(order);
    }

    private static void draw(WorldDraw w) {
        boolean all = b("show_all");
        int count = MiningPack.number(ID, "mining_ordered_waypoints.next_count", 2);
        for (int i = 0; i < RENDER.size(); i++) {
            int r = RENDER.get(i);
            if (r >= ROUTE.size()) continue;
            int color = all ? MiningPack.color(ID, "mining_ordered_waypoints.all_color", 0x6600FF00)
                    : i == 0 ? MiningPack.color(ID, "mining_ordered_waypoints.previous_color", 0x995555FF)
                    : i == 1 ? MiningPack.color(ID, "mining_ordered_waypoints.current_color", 0x9955FF55)
                    : i <= 1 + count ? MiningPack.color(ID, "mining_ordered_waypoints.next_color", 0x99FFFF55)
                    : MiningPack.color(ID, "mining_ordered_waypoints.setup_color", 0x66FF0000);
            Vec3 at = ROUTE.get(r);
            AABB box = new AABB(at.x, at.y, at.z, at.x + 1, at.y + 1, at.z + 1);
            if (b("fill")) w.filledBox(box, color & 0xFFFFFF, ((color >>> 24) & 0xFF) / 255f, true);
            else w.box(box, color, false, false);
            Vec3 text = at.add(0.5, 2.5, 0.5);
            if (b("show_name") && (b("setup_mode") || all || i <= 1 + count)) w.dynamicText(text, "§e" + (r + 1), 1.5, 0);
            if (b("show_distance")) w.dynamicText(text, "§e" + Math.round(dist(at)) + "m", 1.0, -1);
        }
        if (RENDER.size() <= 1) return;
        int traceIndex = RENDER.size() == 2 ? RENDER.get(0) : RENDER.get(2);
        Vec3 trace = ROUTE.get(traceIndex);
        if (b("trace_line") && !all && !b("setup_mode")) {
            w.line(w.eye.add(w.mc.player.getLookAngle()).add(0, -0.2, 0), trace.add(0.5, 0.25, 0.5), MiningPack.color(ID, "mining_ordered_waypoints.trace_color", 0xFF55FF55), MiningPack.number(ID, "mining_ordered_waypoints.trace_width", 1), false);
        }
        if (b("setup_mode") && !all) {
            double eye = b("sneaking") ? SNEAKING_EYE : STANDING_EYE;
            Vec3 cur = ROUTE.get(RENDER.get(1));
            w.line(cur.add(0.5, 1 + eye, 0.5), trace.add(0.5, 0.5, 0.5), MiningPack.color(ID, "mining_ordered_waypoints.setup_line_color", 0xFFFF0000), MiningPack.number(ID, "mining_ordered_waypoints.setup_line_width", 1), false);
        }
    }

    // skyhanni onIslandChange + onWorldChange. runs everywhere bc a route can be on any island
    private static void global(Minecraft mc) {
        if (lastIsland.equals(GameState.island)) return;
        boolean leftShaft = "MINESHAFT".equals(lastIsland);
        lastIsland = GameState.island;
        index = 0;
        if ((leftShaft && b("auto_unload_shaft")) || b("auto_unload")) unload();
    }

    // from skyhanni onGlaciteMineshaftDetectEvent
    public static void onMineshaft(String typeName) {
        if (b("auto_load") && Store.I.orderedRoutes.containsKey(typeName)) load(typeName);
    }

    private static void save(String name) {
        List<double[]> copy = new ArrayList<>();
        for (Vec3 v : ROUTE) copy.add(new double[]{v.x, v.y, v.z});
        Store.I.orderedRoutes.put(name, copy);
        Store.markDirty();
        MiningPack.chat("Route saved as " + name + ". Do /sbc ordered load " + name + " to import it.");
    }

    public static void register() {
        Features.add(new Feature(ID, "Mining", "Ordered waypoints", "A numbered route you load with /sbc ordered load, from your clipboard or a saved route: where you were, where to go now and what comes next, moving on as you reach each block.", "SkyHanni: Ordered Waypoints")
                .when(MiningPack::skyblock).tick(m -> decide()).world(OrderedWaypoints::draw)
                .option(Option.color("mining_ordered_waypoints.current_color", "Current colour", "", 0x9955FF55))
                .option(Option.color("mining_ordered_waypoints.previous_color", "Previous colour", "", 0x995555FF))
                .option(Option.color("mining_ordered_waypoints.next_color", "Next colour", "", 0x99FFFF55))
                .option(Option.integer("mining_ordered_waypoints.next_count", "Waypoints ahead", "", 2, 1, 10))
                .option(Option.bool("mining_ordered_waypoints.fill", "Fill the block", "", false))
                .option(Option.integer("mining_ordered_waypoints.range", "Reached within", "Blocks.", 3, 1, 10))
                .option(Option.bool("mining_ordered_waypoints.trace_line", "Line to the next one", "", true))
                .option(Option.color("mining_ordered_waypoints.trace_color", "Line colour", "", 0xFF55FF55))
                .option(Option.integer("mining_ordered_waypoints.trace_width", "Line width", "", 1, 1, 10))
                .option(Option.bool("mining_ordered_waypoints.show_distance", "Show distance", "", true))
                .option(Option.bool("mining_ordered_waypoints.show_name", "Show number", "", true))
                .option(Option.bool("mining_ordered_waypoints.setup_mode", "Setup mode", "For clearing a route: what is near shows too, with the line from one to the next.", false))
                .option(Option.color("mining_ordered_waypoints.setup_color", "Setup colour", "", 0x66FF0000))
                .option(Option.color("mining_ordered_waypoints.setup_line_color", "Setup line colour", "", 0xFFFF0000))
                .option(Option.integer("mining_ordered_waypoints.setup_range", "Setup range", "", 16, 1, 64))
                .option(Option.integer("mining_ordered_waypoints.setup_line_width", "Setup line width", "", 1, 1, 10))
                .option(Option.bool("mining_ordered_waypoints.sneaking", "Sneaking on the route", "Where the setup line starts.", true))
                .option(Option.bool("mining_ordered_waypoints.show_all", "Show all", "", false))
                .option(Option.color("mining_ordered_waypoints.all_color", "Colour of all", "", 0x6600FF00))
                .option(Option.bool("mining_ordered_waypoints.auto_unload", "Unload when changing island", "", false))
                .option(Option.bool("mining_ordered_waypoints.auto_load", "Load a Mineshaft's route", "A route saved under the Mineshaft's code, like JASP_C.", false))
                .option(Option.bool("mining_ordered_waypoints.auto_unload_shaft", "Unload when leaving a Mineshaft", "", false))
                .option(Option.bool("mining_ordered_waypoints.auto_skip", "Skip forward", "When you reach a block further down the route.", false)));
        Safe.endTick("OrderedWaypoints.global", OrderedWaypoints::global);
        Safe.commands("OrderedWaypoints.commands", (d, r) -> d.register(literal("sbc").then(literal("ordered")
                .then(literal("load").executes(c -> {
                    load("");
                    return 1;
                }).then(argument("name", StringArgumentType.word()).executes(c -> {
                    load(StringArgumentType.getString(c, "name"));
                    return 1;
                })))
                .then(literal("unload").executes(c -> {
                    unload();
                    return 1;
                }))
                .then(literal("skip").executes(c -> {
                    increment(1);
                    MiningPack.chat("Skipped 1 waypoint.");
                    return 1;
                }).then(argument("amount", IntegerArgumentType.integer(1)).executes(c -> {
                    increment(IntegerArgumentType.getInteger(c, "amount"));
                    return 1;
                })))
                .then(literal("unskip").executes(c -> {
                    increment(-1);
                    return 1;
                }).then(argument("amount", IntegerArgumentType.integer(1)).executes(c -> {
                    increment(-IntegerArgumentType.getInteger(c, "amount"));
                    return 1;
                })))
                .then(literal("skipto").then(argument("number", IntegerArgumentType.integer(1)).executes(c -> {
                    int n = IntegerArgumentType.getInteger(c, "number");
                    if (n <= ROUTE.size()) index = n - 1;
                    else MiningPack.chat("§c" + n + " is not between 1 and " + ROUTE.size() + ".");
                    return 1;
                })))
                .then(literal("delete").then(argument("number", IntegerArgumentType.integer(1)).executes(c -> {
                    int n = IntegerArgumentType.getInteger(c, "number");
                    if (n <= ROUTE.size()) {
                        ROUTE.remove(n - 1);
                        RENDER.clear();
                        MiningPack.chat("Removed waypoint " + n + ".");
                    } else MiningPack.chat("§c" + n + " is not between 1 and " + ROUTE.size() + ".");
                    return 1;
                })))
                .then(literal("add").then(argument("number", IntegerArgumentType.integer(1)).executes(c -> {
                    int n = IntegerArgumentType.getInteger(c, "number");
                    if (n > ROUTE.size() + 1) MiningPack.chat("§c" + n + " is not between 1 and " + (ROUTE.size() + 1) + ".");
                    else {
                        Vec3 p = player();
                        ROUTE.add(n - 1, new Vec3(Math.floor(p.x), Math.floor(p.y) - 1, Math.floor(p.z)));
                        MiningPack.chat(String.format(Locale.ROOT, "Inserted waypoint %d.", n));
                    }
                    return 1;
                })))
                .then(literal("export").executes(c -> {
                    Minecraft.getInstance().keyboardHandler.setClipboard(export(ROUTE));
                    MiningPack.chat("Route was copied to clipboard.");
                    return 1;
                }))
                .then(literal("save").then(argument("name", StringArgumentType.word()).executes(c -> {
                    save(StringArgumentType.getString(c, "name"));
                    return 1;
                })))
                .then(literal("erase").then(argument("name", StringArgumentType.word()).executes(c -> {
                    String name = StringArgumentType.getString(c, "name");
                    if (Store.I.orderedRoutes.remove(name) != null) {
                        Store.markDirty();
                        MiningPack.chat("Route " + name + " successfully deleted.");
                    } else MiningPack.chat("§cRoute " + name + " doesn't exist.");
                    return 1;
                })))
                .then(literal("setupmode").executes(c -> {
                    Option o = Features.get(ID).option("mining_ordered_waypoints.setup_mode");
                    o.set(!o.bool());
                    MiningPack.chat("Toggled setup mode to " + o.bool());
                    return 1;
                })))));
        dev.zmuzy.sbc.core.Commands.describe("ordered", "A numbered route of waypoints: load, unload, skip, save and more");
    }
}
