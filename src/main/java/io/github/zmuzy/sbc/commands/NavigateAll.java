// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.zmuzy.sbc.core.Commands;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Repo;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.mc.Interactions;
import io.github.zmuzy.sbc.nav.Graph;
import io.github.zmuzy.sbc.nav.Navigator;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyhanni NavigateAllApi, NavigateAllCommand, NavigateClipboardCommand. walks a list of places one by
// one on the islands path network
// /sbc navall = every node of a kind, /sbc navclipboard = x:y:z lines you copied, left click at the place marks it done
// route is just nearest first (theirs improves it more). player does all the walking
public final class NavigateAll {
    // from skyhanni NavigateAllApi
    private static final double NODE_REACHED = 3, CLICK_RANGE = 5;
    private static final int MAX_SNAP_DISTANCE = 20;
    // from skyhanni allowedMultiNavigationTags: GraphNodeTag names in the graph files -> what the player calls them
    private static final Map<String, String> TAGS = new LinkedHashMap<>();

    static {
        TAGS.put("hoppity", "Hoppity Egg");
        TAGS.put("rift_effigy", "Blood Effigies");
        TAGS.put("rift_montezuma", "Montezuma");
        TAGS.put("crimson_miniboss", "Crimson Miniboss");
        TAGS.put("spider_relic", "Relic");
        TAGS.put("end_golem", "Golem Spawn");
        TAGS.put("fishing_hotspot", "Fishing Hotspot");
        TAGS.put("fishing_wormhole", "Fishing Wormhole");
        TAGS.put("fairy_soul", "Fairy Soul");
        TAGS.put("hideonleaf", "Hideonleaf");
        TAGS.put("hideonsun", "Hideonsun");
        TAGS.put("tree_protection_order", "Tree Protection Order");
        TAGS.put("honeyhive", "Honeyhive");
        TAGS.put("safari_bell", "Safari Bell");
        TAGS.put("hideyho", "Hideyho");
        TAGS.put("pangolin_area", "Pangolin");
        TAGS.put("sanger_area", "Sanger");
        TAGS.put("floor_drops", "Floor Drops");
    }

    private static boolean tagged(Graph.Node n, String tag) {
        for (String t : n.tags()) if (t.equalsIgnoreCase(tag)) return true;
        return false;
    }

    private static List<Vec3> route = List.of();
    private static int index;
    private static String name;
    private static boolean manual;
    private static String routeIsland = "";
    private NavigateAll() {}

    private static void say(String text) {
        Minecraft.getInstance().gui.hud.getChat().addClientSystemMessage(Component.literal("§b[SBC] §f" + text));
    }

    // clipboard lines, from skyhanni NavigateClipboardCommand.parseLocationOrNull
    static Vec3 parseLine(String line) {
        String[] p = line.trim().split(":");
        if (p.length != 3) return null;
        try {
            return new Vec3(Double.parseDouble(p[0].trim()), Double.parseDouble(p[1].trim()), Double.parseDouble(p[2].trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static List<Vec3> nearestFirst(List<Vec3> places, Vec3 from) {
        List<Vec3> left = new ArrayList<>(places), out = new ArrayList<>();
        Vec3 at = from;
        while (!left.isEmpty()) {
            Vec3 best = left.get(0);
            for (Vec3 p : left) if (p.distanceToSqr(at) < best.distanceToSqr(at)) best = p;
            left.remove(best);
            out.add(best);
            at = best;
        }
        return out;
    }

    private static void start(List<Vec3> places, String label, boolean clickToFinish, boolean optimize) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || places.isEmpty()) return;
        route = optimize ? nearestFirst(places, mc.player.position()) : new ArrayList<>(places);
        index = 0;
        name = label;
        manual = clickToFinish;
        routeIsland = GameState.island;
        say("§aNavigating to " + route.size() + " " + label + (route.size() == 1 ? "" : "s") + ".");
        say(clickToFinish ? "§aLeft click a waypoint to mark it as done, §e/sbc navclipboard skip §ato skip it, §e/sbc navclipboard undo §ato go back." : "§aUse §e/sbc navall skip §ato skip a target.");
        current();
    }

    private static void current() {
        if (index >= route.size()) {
            if (name != null) say("Reached all " + route.size() + " " + name + (route.size() == 1 ? "" : "s") + ".");
            stop();
            return;
        }
        Navigator.navigate(new Navigator.Target(GameState.island, name + " " + (index + 1) + "/" + route.size(), route.get(index)));
    }

    private static void stop() {
        boolean was = name != null;
        name = null;
        route = List.of();
        if (was) Navigator.stop();
    }

    private static int skip() {
        if (name == null) {
            say("§cNo current navigation to skip.");
            return 0;
        }
        say("Skipping a " + name + ".");
        index++;
        current();
        return 1;
    }

    private static int undo() {
        if (name == null) {
            say("§cNo current navigation to go back in.");
            return 0;
        }
        if (index == 0) {
            say("§cAlready at the first " + name + ".");
            return 0;
        }
        index--;
        say("Going back to " + name + " " + (index + 1) + "/" + route.size() + ".");
        current();
        return 1;
    }

    private static void tick(Minecraft mc) {
        if (name == null || mc.player == null) return;
        if (index >= route.size()) return;
        if (!GameState.island.equals(routeIsland)) {
            stop();
            return;
        }
        if (!manual && mc.player.position().distanceTo(route.get(index)) <= NODE_REACHED) {
            index++;
            current();
        }
    }

    // from skyhanni NavigateAllApi.onItemClick: left click in reach of where youre being led
    private static boolean onClick(boolean rightClick) {
        Minecraft mc = Minecraft.getInstance();
        if (rightClick || !manual || name == null || mc.player == null || index >= route.size()) return false;
        Vec3 place = route.get(index).add(0.5, 0.5, 0.5);
        if (place.distanceTo(mc.player.position()) > CLICK_RANGE) return false;
        var look = mc.player.getLookAngle();
        var eye = mc.player.getEyePosition();
        var box = new net.minecraft.world.phys.AABB(route.get(index), route.get(index).add(1, 1, 1));
        if (box.clip(eye, eye.add(look.scale(CLICK_RANGE + 2))).isEmpty()) return false;
        index++;
        current();
        return true;
    }

    private static int byTag(String tag) {
        Graph g = Repo.graph(GameState.island);
        List<Vec3> places = new ArrayList<>();
        for (Graph.Node n : g.nodes) if (tagged(n, tag)) places.add(n.pos());
        if (places.isEmpty()) {
            say("§c" + TAGS.getOrDefault(tag, tag) + " is invalid for navigation on this island!");
            return 0;
        }
        start(places, TAGS.getOrDefault(tag, tag), false, true);
        return 1;
    }

    // from skyhanni NavigateClipboardCommand.startNavigation
    private static int clipboard(boolean optimize) {
        Minecraft mc = Minecraft.getInstance();
        Graph g = Repo.graph(GameState.island);
        if (g.isEmpty()) {
            say("§cThere is no path network on this island.");
            return 0;
        }
        String text = mc.keyboardHandler.getClipboard();
        if (text == null || text.isBlank()) {
            say("§cYour clipboard is empty. Copy one x:y:z location per line first.");
            return 0;
        }
        List<Vec3> places = new ArrayList<>();
        int unreadable = 0, tooFar = 0;
        for (String line : text.split("\\R")) {
            if (line.isBlank()) continue;
            Vec3 p = parseLine(line);
            if (p == null) {
                unreadable++;
                continue;
            }
            Graph.Node near = g.nearest(p);
            if (near == null || near.pos().distanceTo(p) > MAX_SNAP_DISTANCE) tooFar++;
            else places.add(p);
        }
        if (places.isEmpty()) {
            say(unreadable > 0 && tooFar == 0 ? "§cCould not read any location from your clipboard. Expected one x:y:z per line." : "§cNo location is within " + MAX_SNAP_DISTANCE + " blocks of the path network on this island.");
            return 0;
        }
        String notes = (unreadable > 0 ? " §7(" + unreadable + " unreadable)" : "") + (tooFar > 0 ? " §7(" + tooFar + " too far away)" : "");
        say("Read " + places.size() + " locations from the clipboard." + notes);
        start(places, "Clipboard Waypoint", true, optimize);
        return 1;
    }

    // kinds this islands graph has
    private static List<String> validTags() {
        List<String> out = new ArrayList<>();
        Graph g = Repo.graph(GameState.island);
        for (String tag : TAGS.keySet()) {
            for (Graph.Node n : g.nodes) {
                if (tagged(n, tag)) {
                    out.add(tag);
                    break;
                }
            }
        }
        return out;
    }

    public static void register() {
        Commands.describe("navall", "Walk to every place of a kind on this island: /sbc navall <kind>, skip, undo, stop");
        Commands.describe("navclipboard", "Walk to the x:y:z lines you copied: /sbc navclipboard [ordered], skip, undo, stop");
        Safe.endTick("NavigateAll.tick", NavigateAll::tick);
        Interactions.onItemClick(NavigateAll::onClick);
        Safe.commands("NavigateAll.commands", (d, r) -> d.register(literal("sbc")
                .then(literal("navall")
                        .then(literal("skip").executes(c -> skip()))
                        .then(literal("undo").executes(c -> undo()))
                        .then(literal("stop").executes(c -> {
                            stop();
                            return 1;
                        }))
                        .then(argument("kind", io.github.zmuzy.sbc.core.WordArgument.except("skip", "undo", "stop")).suggests((c, b) -> {
                            for (String t : validTags()) if (t.startsWith(b.getRemainingLowerCase())) b.suggest(t);
                            return b.buildFuture();
                        }).executes(c -> byTag(StringArgumentType.getString(c, "kind").toLowerCase(Locale.ROOT)))))
                .then(literal("navclipboard").executes(c -> clipboard(true))
                        .then(literal("optimized").executes(c -> clipboard(true)))
                        .then(literal("ordered").executes(c -> clipboard(false)))
                        .then(literal("skip").executes(c -> skip()))
                        .then(literal("undo").executes(c -> undo()))
                        .then(literal("stop").executes(c -> {
                            stop();
                            return 1;
                        })))));
    }
}
