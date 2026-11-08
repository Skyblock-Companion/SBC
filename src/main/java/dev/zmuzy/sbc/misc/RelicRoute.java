// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.misc;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.nav.Graph;
import dev.zmuzy.sbc.nav.Navigator;
import dev.zmuzy.sbc.render.WorldDraw;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// from skyhanni misc/SpiderDenRelicPathfinder.kt, same way nav/FairySouls follows the fairy soul one: a route
// through the spiders den relics you didnt find, nearest first
public final class RelicRoute {
    public static final String ID = "spider_relic_route";
    private static final Patterns.P FOUND = Patterns.p("SH.misc.spider-relic.chat.found"), DUPLICATE = Patterns.p("SH.misc.spider-relic.chat.duplicate");
    private static final String TAG = "SPIDER_RELIC";

    private static List<Vec3> route = new ArrayList<>();
    private static List<Vec3> all = List.of();
    private static int found;
    private static boolean loaded;
    private static long lastCheck;
    private static Vec3 foundButNotClicked;
    private static Navigator.Target lastTarget;

    private RelicRoute() {}

    private static boolean here() {
        return "SPIDER_DEN".equals(GameState.island);
    }

    private static Set<String> store() {
        String key = GameState.profileName == null || GameState.profileName.isEmpty() ? "default" : GameState.profileName;
        Map<String, Set<String>> map = MiscStore.get().relics;
        return map.computeIfAbsent(key, k -> new HashSet<>());
    }

    static String key(Vec3 v) {
        return (int) Math.round(v.x) + ":" + (int) Math.round(v.y) + ":" + (int) Math.round(v.z);
    }

    // greedy order, nearest next
    static List<Vec3> order(List<Vec3> targets, Vec3 start) {
        List<Vec3> left = new ArrayList<>(targets);
        List<Vec3> out = new ArrayList<>();
        Vec3 cur = start;
        while (!left.isEmpty()) {
            Vec3 best = left.get(0);
            for (Vec3 v : left) if (v.distanceToSqr(cur) < best.distanceToSqr(cur)) best = v;
            out.add(best);
            left.remove(best);
            cur = best;
        }
        return out;
    }

    private static void reload(Minecraft mc) {
        loaded = true;
        route = new ArrayList<>();
        all = List.of();
        found = 0;
        Graph g = Repo.graph("SPIDER_DEN");
        if (g == null || g.isEmpty() || mc.player == null) return;
        List<Vec3> relics = new ArrayList<>();
        for (Graph.Node n : g.nodes) if (n.tags().contains(TAG)) relics.add(n.pos());
        all = relics;
        Set<String> done = store();
        List<Vec3> missing = new ArrayList<>();
        for (Vec3 v : relics) if (!done.contains(key(v))) missing.add(v);
        found = relics.size() - missing.size();
        route = order(missing, mc.player.position());
        pathToNext();
    }

    private static void pathTo(Vec3 v) {
        double percent = all.isEmpty() ? 0 : found * 100.0 / all.size();
        Navigator.navigate(new Navigator.Target("SPIDER_DEN", "§b" + found + "/" + all.size() + " §5Relics §8(§b" + String.format(Locale.ROOT, "%.1f", percent) + "%§8)", v));
    }

    private static void pathToNext() {
        if (all.isEmpty()) return;
        if (route.isEmpty()) {
            SmallMisc.say("§eFound all §5" + found + " Relics §ein Spider's Den!");
            Navigator.stop();
        } else pathTo(route.get(0));
    }

    // from skyhanni foundNearby: the relic within 10 blocks is the one you just clicked
    private static void foundNearby(Vec3 me) {
        foundButNotClicked = null;
        Vec3 nearest = null;
        for (Vec3 v : all) if (v.distanceTo(me) < 10 && (nearest == null || v.distanceToSqr(me) < nearest.distanceToSqr(me))) nearest = v;
        if (nearest == null) return;
        if (route.remove(nearest)) found++;
        store().add(key(nearest));
        MiscStore.save();
        pathToNext();
    }

    private static ChatBus.Result chat(ChatBus.Line line) {
        String t = line.plain().trim();
        var p = Minecraft.getInstance().player;
        if (p != null && (FOUND.matcher(t).matches() || DUPLICATE.matcher(t).matches())) foundNearby(p.position());
        return ChatBus.PASS;
    }

    private static void tick(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - lastCheck < 1000) return;
        lastCheck = now;
        if (!loaded) {
            reload(mc);
            return;
        }
        Navigator.Target t = Navigator.target();
        if (t == null && lastTarget != null && !route.isEmpty() && foundButNotClicked == null) foundButNotClicked = lastTarget.pos();
        lastTarget = t;
        if (foundButNotClicked != null && mc.player != null && foundButNotClicked.distanceTo(mc.player.position()) > 5) {
            pathTo(foundButNotClicked);
            foundButNotClicked = null;
        }
    }

    private static void world(WorldDraw w) {
        if (!route.isEmpty()) w.waypoint(route.get(0), 0xFFAA00AA, "§5Relic §8(" + found + "/" + all.size() + ")", true, true);
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Navigation", "Relic route", "Leads you through the relics of Spider's Den you have not found, one after another. /sbc relics reset forgets the ones found.", "SkyHanni: Spider Den Relic Pathfinder")
                .when(() -> GameState.inSkyBlock && here()).defaultOff().tick(RelicRoute::tick).world(RelicRoute::world).left(() -> {
                    loaded = false;
                    route = new ArrayList<>();
                    foundButNotClicked = null;
                    lastTarget = null;
                }));
        ChatBus.register(ChatBus.OBSERVE + 21, ID, f, RelicRoute::chat);
        Safe.commands("RelicRoute.commands", (d, b) -> d.register(ClientCommands.literal("sbc")
                .then(ClientCommands.literal("relicsreset").executes(c -> dev.zmuzy.sbc.commands.MoreCommands.relics(false)))
                .then(ClientCommands.literal("relicsfoundall").executes(c -> dev.zmuzy.sbc.commands.MoreCommands.relics(true)))));
        dev.zmuzy.sbc.core.Commands.describe("relicsreset", "The same as /sbc relics reset");
        dev.zmuzy.sbc.core.Commands.describe("relicsfoundall", "The same as /sbc relics foundall");
    }

    // what /sbc relics reset + foundall do to the routes own list
    public static void setAll(boolean found) {
        if (found) for (Vec3 v : all) store().add(key(v));
        else store().clear();
        MiscStore.save();
        loaded = false;
    }

    // for the test: found count from a store of keys
    static int foundOf(List<Vec3> relics, Set<String> keys) {
        int n = 0;
        for (Vec3 v : relics) if (keys.contains(key(v))) n++;
        return n;
    }
}
