// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.nav.Graph;
import dev.zmuzy.sbc.nav.Navigator;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// everything in the world you can look up beside items: npcs, mobs, bosses, places and warps, from the named
// nodes of skyhannis island graphs (the same ones the path line walks). an entry id starts with @ so it never
// clashes with an item id
public final class WorldIndex {
    public record Entry(String id, String kind, String name, String island, Vec3 pos, String area) {}

    private static List<Entry> all = List.of();
    private static final Map<String, Entry> BY_ID = new HashMap<>();
    private static int builtFrom = -1;

    private WorldIndex() {}

    // what a node is by its tags, null = not something to look up (dev marks, eggs, drops, hotspots...)
    static String kindOf(List<String> tags) {
        if (tags.contains("npc") || tags.contains("mines_emissary") || tags.contains("rift_montezuma")) return "NPC";
        if (tags.contains("slayer") || tags.contains("crimson_miniboss") || tags.contains("end_golem")) return "Boss";
        if (tags.contains("grind_mobs")) return "Mob";
        if (tags.contains("warp") || tags.contains("teleport_pad") || tags.contains("teleport")) return "Travel";
        if (tags.contains("area") || tags.contains("small_area") || tags.contains("poi") || tags.contains("grind_ores") || tags.contains("grind_crops")
                || tags.contains("rift_enigma") || tags.contains("rift_eye") || tags.contains("race")) return "Place";
        return null;
    }

    public static boolean is(String id) {
        return id != null && id.startsWith("@");
    }

    public static Entry get(String id) {
        all();
        return BY_ID.get(id);
    }

    // built once from the graphs we have, again when more islands show up
    public static List<Entry> all() {
        List<String> islands = Repo.graphIslands();
        if (islands.size() == builtFrom) return all;
        builtFrom = islands.size();
        List<Entry> out = new ArrayList<>();
        BY_ID.clear();
        for (String island : islands) {
            Graph g = Repo.graph(island);
            if (g == null || g.isEmpty()) continue;
            List<Graph.Node> areas = new ArrayList<>();
            for (Graph.Node n : g.named()) if (n.tags().contains("area")) areas.add(n);
            Set<String> seen = new HashSet<>();
            for (Graph.Node n : g.named()) {
                String kind = kindOf(n.tags());
                if (kind == null || n.name().isBlank() || !seen.add(kind + "|" + n.name())) continue;
                String id = "@" + kind.toLowerCase(Locale.ROOT) + ":" + island + ":" + n.name();
                Entry e = new Entry(id, kind, n.name(), island, n.pos(), kind.equals("Place") && n.tags().contains("area") ? "" : nearestArea(areas, n.pos()));
                out.add(e);
                BY_ID.put(id, e);
            }
        }
        return all = out;
    }

    static String nearestArea(List<Graph.Node> areas, Vec3 p) {
        Graph.Node best = null;
        double bestD = 120 * 120;
        for (Graph.Node a : areas) {
            double d = a.pos().distanceToSqr(p);
            if (d < bestD) {
                bestD = d;
                best = a;
            }
        }
        return best == null ? "" : best.name();
    }

    // npcs first when you scroll the world, warps last
    static int order(String kind) {
        return switch (kind) {
            case "NPC" -> 0;
            case "Boss" -> 1;
            case "Mob" -> 2;
            case "Place" -> 3;
            default -> 4;
        };
    }

    static ItemStack icon(String kind) {
        return dev.zmuzy.sbc.core.LegacyItems.stack(switch (kind) {
            case "NPC" -> "minecraft:villager_spawn_egg";
            case "Boss" -> "minecraft:wither_skeleton_skull";
            case "Mob" -> "minecraft:zombie_spawn_egg";
            case "Travel" -> "minecraft:ender_pearl";
            default -> "minecraft:filled_map";
        });
    }

    static int color(String kind) {
        return switch (kind) {
            case "NPC" -> 0xFF55FF55;
            case "Boss" -> 0xFFFF5555;
            case "Mob" -> 0xFFFFAA00;
            case "Travel" -> 0xFF55FFFF;
            default -> 0xFFAAAAAA;
        };
    }

    // one line under the list: where it is
    static String where(Entry e) {
        String island = GameState.pretty(e.island());
        return "§7" + e.kind() + " · §f" + island + (e.area().isEmpty() ? "" : "§7, " + e.area());
    }

    static List<String> tooltip(Entry e) {
        List<String> tip = new ArrayList<>();
        tip.add("§f" + e.name());
        tip.add(where(e));
        tip.add("§7At §f" + (int) Math.floor(e.pos().x) + ", " + (int) Math.floor(e.pos().y) + ", " + (int) Math.floor(e.pos().z));
        var me = Minecraft.getInstance().player;
        if (me != null && e.island().equals(GameState.island)) tip.add("§7" + (int) Math.sqrt(me.distanceToSqr(e.pos())) + " blocks away");
        else {
            String warp = Navigator.warpFor(new Navigator.Target(e.island(), e.name(), e.pos()));
            if (!warp.isEmpty()) tip.add("§7Get there with §b" + warp);
        }
        tip.add("");
        tip.add("§eClick: details  §7Right-click: guide me there");
        return tip;
    }

    static void go(Entry e) {
        Navigator.navigate(new Navigator.Target(e.island(), e.name(), e.pos()));
    }
}
