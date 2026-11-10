// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.nav;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// world markers for stuff worth finding, from the island graph tags: rift enigma souls, spiders den relics,
// event baskets, mining emissaries (fairy souls live in FairySouls)
public final class Collectibles {
    private record Spec(String id, String title, String description, Set<String> tags, String label, int color, double radius, int maxShown, boolean byChat) {}

    private static final List<Spec> SPECS = List.of(
            new Spec("col_enigma", "Enigma souls nearby", "Marks Rift enigma souls near you.", Set.of("rift_enigma"), "Enigma soul", 0xFFB070FF, 60, 8, false),
            new Spec("col_relic", "Spider relics", "Marks the relics in the Spider's Den.", Set.of("SPIDER_RELIC"), "Relic", 0xFFFFD36B, 70, 8, false),
            new Spec("col_basket", "Event baskets", "Marks event baskets near you.", Set.of("event_basket"), "Basket", 0xFF97C459, 60, 6, false),
            new Spec("col_emissary", "Mining emissaries", "The name of every emissary that gives Commissions, and how far away it is.", Set.of("mines_emissary"), "Emissary", 0xFFEF9F27, 400, 9, true));

    private static final java.util.Map<String, List<Graph.Node>> CACHE = new java.util.HashMap<>();

    private Collectibles() {}

    private static String key(String island, Vec3 p) {
        return island + "|" + Math.round(p.x) + ":" + Math.round(p.y) + ":" + Math.round(p.z);
    }

    private static List<Graph.Node> nodes(Spec s) {
        return CACHE.computeIfAbsent(GameState.island + "|" + s.id(), k -> {
            Graph g = Repo.graph(GameState.island);
            List<Graph.Node> out = new ArrayList<>();
            if (g != null) for (Graph.Node n : g.nodes) for (String t : n.tags()) if (s.tags().contains(t)) {
                out.add(n);
                break;
            }
            return out;
        });
    }

    private static void draw(Spec s, WorldDraw w) {
        Minecraft mc = w.mc;
        if (mc.player == null) return;
        if (s.id().equals("col_emissary")) {
            names(s, w);
            return;
        }
        Vec3 me = mc.player.position();
        List<Graph.Node> near = new ArrayList<>();
        for (Graph.Node n : nodes(s)) {
            double d = n.pos().distanceTo(me);
            if (d > s.radius() || Store.I.found.contains(key(GameState.island, n.pos()))) continue;
            if (!s.byChat() && d < 2.5) {
                Store.I.found.add(key(GameState.island, n.pos()));
                Store.markDirty();
                continue;
            }
            near.add(n);
        }
        near.sort((a, b) -> Double.compare(a.pos().distanceToSqr(me), b.pos().distanceToSqr(me)));
        // from skyblocker waypoint/Relics: found ones in another color
        if (s.id().equals("col_relic") && Features.get(s.id()).option("show_found").bool()) {
            for (Graph.Node n : nodes(s)) {
                if (n.pos().distanceTo(me) <= s.radius() && Store.I.found.contains(key(GameState.island, n.pos())) && w.visible(n.pos())) w.waypointBox(n.pos().add(-0.5, 0, -0.5), 0xFF55FF55, false, false, 0);
            }
        }
        for (int i = 0; i < Math.min(s.maxShown(), near.size()); i++) {
            Vec3 c = near.get(i).pos();
            if (!w.visible(c)) continue;
            // skyhanni drawWaypointFilled + drawDynamicText look (see WorldDraw.waypoint)
            w.waypoint(c.add(-0.5, 0, -0.5), s.color(), s.label(), true, true);
        }
    }

    // emissaries stay where they are and you visit them again and again: a name and the distance, nothing to tick off
    private static void names(Spec s, WorldDraw w) {
        Vec3 me = w.mc.player.position();
        for (Graph.Node n : nodes(s)) {
            double d = n.pos().distanceTo(me);
            if (d > s.radius() || d < 3) continue;
            Vec3 at = n.pos().add(0, 2.4, 0);
            String name = n.name() == null || n.name().isBlank() ? s.label() : n.name();
            w.dynamicText(at, "§6" + name, 1.2, 0);
            w.dynamicText(at, "§e" + Math.round(d) + "m", 1.0, -1);
        }
    }

    // where a player looks for it: each kind sits with its islands helpers, the rest under navigation
    private static String section(String id) {
        return switch (id) {
            case "col_enigma" -> "Rift";
            case "col_emissary" -> "Mining";
            default -> "Navigation";
        };
    }

    public static void register() {
        HotspotRadar.register();
        for (Spec s : SPECS)
            Features.add(new Feature(s.id(), section(s.id()), s.title(), s.description(), "Skyblocker / SkyHanni: waypoints")
                    .when(() -> GameState.inSkyBlock || SbcMod.DEV).world(w -> draw(s, w)));
        // relic marks start off, the relic route takes you there if you want
        Features.get("col_relic").defaultOff().option(io.muzy.sbc.feature.Option.bool("col_relic.show_found", "Also mark the ones you found", "In green.", true));
    }

    // skyhanni SpiderDenRelicPathfinder + skyblocker relics commands: mark every marker of this kind on this
    // island found, or none. returns how many
    public static int mark(String specId, boolean found) {
        for (Spec s : SPECS) {
            if (!s.id().equals(specId)) continue;
            List<Graph.Node> all = nodes(s);
            for (Graph.Node n : all) {
                if (found) Store.I.found.add(key(GameState.island, n.pos()));
                else Store.I.found.remove(key(GameState.island, n.pos()));
            }
            Store.markDirty();
            return all.size();
        }
        return 0;
    }

    // forget what you collected so every marker shows again
    public static int reset() {
        int n = Store.I.found.size();
        Store.I.found.clear();
        Store.markDirty();
        return n;
    }
}
