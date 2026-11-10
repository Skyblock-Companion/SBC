// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.areas;

import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.dungeon.DungeonMarks;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.nav.Graph;
import io.muzy.sbc.nav.Navigator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// from skyhanni hunting/safari/HideyhoFinder: when hideyho says "No peeking!" it hides at another of its
// spots. spots come from the island graph, you get led through them nearest first
public final class HideyhoFinder {
    public static final String ID = "hunting_hideyho";
    private static final String TAG = "hideyho";
    private static final Patterns.P START = Patterns.p("SH.hunting.safari.hideyho-finder.first-found"), HIDING = Patterns.p("SH.hunting.safari.hideyho-finder.begin-hiding"),
            FOUND = Patterns.p("SH.hunting.safari.hideyho-finder.found-after-hiding");
    private static final long HIDE_DELAY_MS = 2000, SECOND = 1000;
    private static final double NODE_REACHED = 3, SEEN_RADIUS = 5, START_RADIUS = 10;

    private static Vec3 startLocation;
    private static long beginAt, lastCheck;
    private static List<Vec3> route = new ArrayList<>();
    private static int index;

    private HideyhoFinder() {}

    // spots sorted nearest to where it was first. the first one is where it just left so it goes
    static List<Vec3> placesWithoutStart(List<Vec3> places, Vec3 start) {
        List<Vec3> out = new ArrayList<>(places);
        Vec3 current = null;
        for (Vec3 p : out) if (current == null || p.distanceToSqr(start) < current.distanceToSqr(start)) current = p;
        out.remove(current);
        return out;
    }

    // skyhannis NavigateAllApi order: nearest next from where you stand
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

    // from skyhanni nearbyLocation: hideyho by its player entity skin, only when in sight
    private static Vec3 hideyhoNear(Minecraft mc, Vec3 pos, double radius) {
        String skin = DungeonMarks.skull("HIDEYHO");
        if (skin.isEmpty() || mc.level == null || mc.player == null) return null;
        for (RemotePlayer p : mc.level.getEntitiesOfClass(RemotePlayer.class, AABB.ofSize(pos, radius * 2, radius * 2, radius * 2))) {
            if (p.position().distanceTo(pos) > radius || !mc.player.hasLineOfSight(p)) continue;
            for (var prop : p.getGameProfile().properties().get("textures")) if (skin.equals(prop.value())) return p.position();
        }
        return null;
    }

    private static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(Component.literal("§b[SBC] " + text));
    }

    private static ChatBus.Result chat(ChatBus.Line line) {
        String clean = line.plain().trim();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ChatBus.PASS;
        if (START.matcher(clean).matches()) startLocation = hideyhoNear(mc, mc.player.position(), START_RADIUS);
        else if (FOUND.matcher(clean).matches()) stop();
        // from skyhanni beginHidingPattern: wait for it to teleport first
        else if (HIDING.matcher(clean).matches()) beginAt = System.currentTimeMillis() + HIDE_DELAY_MS;
        return ChatBus.PASS;
    }

    private static void begin(Minecraft mc) {
        if (startLocation == null || mc.player == null) return;
        List<Vec3> places = new ArrayList<>();
        for (Graph.Node n : Repo.graph(GameState.island).nodes) for (String t : n.tags()) if (t.equalsIgnoreCase(TAG)) places.add(n.pos());
        if (places.isEmpty()) return;
        route = nearestFirst(placesWithoutStart(places, startLocation), mc.player.position());
        index = 0;
        current();
    }

    private static void current() {
        if (index >= route.size()) {
            say("§eCould not find any hidden Hideyho, maybe someone else already found it.");
            stop();
            return;
        }
        Navigator.navigate(new Navigator.Target(GameState.island, "Hideyho " + (index + 1) + "/" + route.size(), route.get(index)));
    }

    private static void stop() {
        boolean going = !route.isEmpty();
        route = new ArrayList<>();
        index = 0;
        beginAt = 0;
        if (going) Navigator.stop();
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        if (beginAt != 0 && now >= beginAt) {
            beginAt = 0;
            begin(mc);
        }
        if (route.isEmpty() || index >= route.size()) return;
        Vec3 place = route.get(index);
        if (now - lastCheck >= SECOND) {
            lastCheck = now;
            if (hideyhoNear(mc, place, SEEN_RADIUS) != null) {
                say("§aFound Hideyho!");
                stop();
                return;
            }
        }
        if (mc.player.position().distanceTo(place) <= NODE_REACHED) {
            index++;
            current();
        }
    }

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Hunting", "Hideyho finder", "When Hideyho hides in the Critter Safari, you are led through its places, nearest next, until you see it.", "SkyHanni: Hideyho Finder")
                .when(() -> "SAFARI".equals(GameState.island)).tick(HideyhoFinder::tick).left(() -> {
                    stop();
                    startLocation = null;
                }));
        ChatBus.register(ChatBus.OBSERVE + 8, ID, f, HideyhoFinder::chat);
    }
}
