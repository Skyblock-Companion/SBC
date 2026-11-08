// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.nav;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.core.Store;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// everything /sbnav can go to: named spots of every island graph (areas, npcs, warps), nearest of a kind
// (fairy soul, enigma soul, visitor spot...), npcs you met, any waypoint. tab completes
public final class Destinations {
    private static final Set<String> BORING_TAGS = Set.of("area", "npc", "warp", "no_area");
    private static final int MAX_SUGGESTIONS = 80;

    private record Dest(String label, String island, String name, Vec3 pos) {}

    private Destinations() {}

    private static String islandKey(String pretty) {
        for (String isl : Repo.graphIslands()) if (GameState.pretty(isl).equalsIgnoreCase(pretty) || isl.equalsIgnoreCase(pretty)) return isl;
        return null;
    }

    private static String nearestLabel(String tag) {
        return "Nearest " + tag.replace('_', ' ');
    }

    // all destinations you can see from here, current island first. labels are unique
    private static List<Dest> collect() {
        Map<String, Dest> out = new LinkedHashMap<>();
        String here = GameState.island;
        Graph hg = Repo.graph(here);
        if (hg != null && !hg.isEmpty()) {
            // nearest of each marker kind on this island
            Set<String> tags = new java.util.TreeSet<>();
            for (Graph.Node n : hg.nodes) for (String t : n.tags()) if (!BORING_TAGS.contains(t) && !t.startsWith("grind")) tags.add(t);
            for (String t : tags) out.put(nearestLabel(t).toLowerCase(Locale.ROOT), new Dest(nearestLabel(t), here, nearestLabel(t), null));
            for (Graph.Node n : hg.named()) out.putIfAbsent(n.name().toLowerCase(Locale.ROOT), new Dest(n.name(), here, n.name(), n.pos()));
        }
        for (var e : Store.I.npcs.entrySet()) {
            int bar = e.getKey().indexOf('|');
            if (bar < 0) continue;
            String isl = e.getKey().substring(0, bar), name = e.getKey().substring(bar + 1);
            double[] p = e.getValue();
            String label = isl.equals(here) ? name : name + " @ " + GameState.pretty(isl);
            out.putIfAbsent(label.toLowerCase(Locale.ROOT), new Dest(label, isl, name, new Vec3(p[0], p[1], p[2])));
        }
        for (Waypoints.Point w : Waypoints.all()) {
            String label = w.island().isEmpty() || w.island().equals(here) ? w.name() : w.name() + " @ " + GameState.pretty(w.island());
            out.putIfAbsent(label.toLowerCase(Locale.ROOT), new Dest(label, w.island(), w.name(), w.pos()));
        }
        for (String isl : Repo.graphIslands()) {
            if (isl.equals(here)) continue;
            Graph g = Repo.graph(isl);
            if (g == null) continue;
            for (Graph.Node n : g.named()) {
                String label = n.name() + " @ " + GameState.pretty(isl);
                out.putIfAbsent(label.toLowerCase(Locale.ROOT), new Dest(label, isl, n.name(), n.pos()));
            }
        }
        return new ArrayList<>(out.values());
    }

    public static void suggest(SuggestionsBuilder b) {
        String q = b.getRemaining().toLowerCase(Locale.ROOT).trim();
        int n = 0;
        if (q.isEmpty() || "stop".startsWith(q)) b.suggest("stop");
        List<Dest> all = collect();
        for (int pass = 0; pass < 2 && n < MAX_SUGGESTIONS; pass++) {
            for (Dest d : all) {
                String l = d.label().toLowerCase(Locale.ROOT);
                boolean hit = pass == 0 ? l.startsWith(q) : (!l.startsWith(q) && l.contains(q));
                if (!hit) continue;
                b.suggest(d.label());
                if (++n >= MAX_SUGGESTIONS) break;
            }
        }
    }

    // starts navigating to what was typed, returns a message to show
    public static String go(String text) {
        String q = text.trim();
        Minecraft mc = Minecraft.getInstance();
        if (q.equalsIgnoreCase("stop") || q.equalsIgnoreCase("off")) {
            Navigator.stop();
            return "Navigation stopped";
        }
        String here = GameState.island;
        if (q.regionMatches(true, 0, "nearest ", 0, 8)) {
            String tag = q.substring(8).trim().replace(' ', '_').toLowerCase(Locale.ROOT);
            Graph g = Repo.graph(here);
            if (g != null && mc.player != null) {
                Graph.Node best = null;
                double bd = Double.MAX_VALUE;
                for (Graph.Node n : g.nodes) {
                    if (!n.tags().contains(tag)) continue;
                    double d = n.pos().distanceToSqr(mc.player.position());
                    if (d < bd) {
                        bd = d;
                        best = n;
                    }
                }
                if (best != null) {
                    String label = nearestLabel(tag).substring(8);
                    Navigator.navigate(new Navigator.Target(here, label.substring(0, 1).toUpperCase(Locale.ROOT) + label.substring(1), best.pos()));
                    return "Navigating to the nearest " + label;
                }
            }
            return "No " + tag.replace('_', ' ') + " on this island";
        }
        int at = q.lastIndexOf(" @ ");
        if (at > 0) {
            String isl = islandKey(q.substring(at + 3).trim());
            String name = q.substring(0, at).trim();
            if (isl != null && Navigator.navigateTo(isl, name)) return "Navigating to " + name + " on " + GameState.pretty(isl);
        }
        if (Navigator.navigateTo(here, q)) return "Navigating to " + q;
        for (String isl : Repo.graphIslands()) if (Navigator.navigateTo(isl, q)) return "Navigating to " + q + " on " + GameState.pretty(isl);
        return null;
    }

    // /sbnav (and /sbc nav) command tree: one text arg with every destination suggested
    public static LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
        return literal(name).then(argument("place", StringArgumentType.greedyString())
                .suggests((ctx, b) -> {
                    suggest(b);
                    return b.buildFuture();
                })
                .executes(c -> {
                    String msg = go(StringArgumentType.getString(c, "place"));
                    c.getSource().sendFeedback(Component.literal(msg != null ? "§b[SBC] §f" + msg
                            : "§c[SBC] Place not found: " + StringArgumentType.getString(c, "place")));
                    return msg != null ? 1 : 0;
                }));
    }
}
