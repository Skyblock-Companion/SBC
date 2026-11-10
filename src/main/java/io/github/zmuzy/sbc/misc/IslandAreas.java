// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.misc;

import io.github.zmuzy.sbc.areas.FishHooks;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Repo;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.nav.Graph;
import io.github.zmuzy.sbc.nav.Navigator;
import io.github.zmuzy.sbc.render.WorldDraw;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.SideHelper;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// from skyhanni misc/pathfind IslandAreaBackend, IslandAreaFeatures, AreaNode: island areas from its graph
// (nodes tagged area or small_area), nearest first, next to your inventory
public final class IslandAreas {
    public static final String ID = "island_areas";
    private static final int ROW = 11;

    record Area(String name, Vec3 pos, boolean small, int distance) {}

    private static List<Area> areas = List.of();
    private static long updatedAt, lastTitle;
    private static String lastArea = "";

    private IslandAreas() {}

    // from skyhanni updateNodes: each named area once by its nearest border node, nearest first
    static List<Area> of(Graph g, Vec3 me, boolean small) {
        Map<String, Area> best = new HashMap<>();
        for (Graph.Node n : g.nodes) {
            boolean isSmall = n.tags().contains("small_area");
            if (n.name() == null || (!n.tags().contains("area") && !(small && isSmall))) continue;
            int d = (int) Math.round(n.pos().distanceTo(me));
            Area old = best.get(n.name());
            if (old == null || d < old.distance()) best.put(n.name(), new Area(n.name(), n.pos(), isSmall, d));
        }
        List<Area> out = new ArrayList<>(best.values());
        out.sort(Comparator.comparingInt(Area::distance));
        return out;
    }

    private static boolean opt(String key) {
        return Features.get(ID).option(key).bool();
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        long now = System.currentTimeMillis();
        String area = GameState.area == null ? "" : GameState.area.trim();
        if (!area.equals(lastArea)) {
            lastArea = area;
            // from skyhanni onAreaChange: a small area only counts when small areas are shown
            boolean small = false;
            for (Area a : areas) if (a.name().equals(area)) small = a.small();
            if (!area.isEmpty() && opt("island_areas.title") && now - lastTitle >= 2000 && (!small || opt("island_areas.small"))) {
                FishHooks.title("§aEntered " + area + "!");
                lastTitle = now;
            }
        }
        if (now - updatedAt < 1000) return;
        updatedAt = now;
        Graph g = Repo.graph(GameState.island);
        areas = g == null || g.isEmpty() ? List.of() : of(g, mc.player.position(), opt("island_areas.small"));
    }

    private static void world(WorldDraw w) {
        if (!opt("island_areas.world")) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        for (Area a : areas) {
            if (a.distance() > 40) break;
            if (a.name().equals(GameState.area.trim()) || !w.seen(a.pos())) continue;
            w.dynamicText(a.pos(), "§e" + a.name(), 1.5, 0);
        }
    }

    private static boolean isTarget(Area a) {
        Navigator.Target t = Navigator.target();
        return t != null && a.name().equals(t.name());
    }

    private static void click(Area a) {
        if (isTarget(a)) Navigator.stop();
        else Navigator.navigate(new Navigator.Target(GameState.island, a.name(), a.pos()));
    }

    private static final SideHelper.Content PANEL = new SideHelper.Content() {
        @Override public String title() {
            return "Island areas";
        }

        @Override public ItemStack icon() {
            return net.minecraft.world.item.Items.MAP.getDefaultInstance();
        }

        @Override public int accent() {
            return 0xFF55FF55;
        }

        @Override public String[] tabs() {
            return new String[]{"Nearby"};
        }

        @Override public void refresh(AbstractContainerScreen<?> screen) {}

        @Override public int summary(Ui ui, int x, int y, int w) {
            if (!opt("island_areas.current")) return 0;
            String area = GameState.area == null ? "" : GameState.area.trim();
            Modern.row(ui, "Current area", area.isEmpty() ? "§7None" : "§e" + area, x, y, w, Modern.LABEL, Modern.INK);
            return ROW;
        }

        @Override public int list(Ui ui, int tab, int x, int y, int w) {
            int top = y;
            if (areas.isEmpty()) return SideHelper.emptyText(ui, "No area data for this island yet.", x, y, w);
            String here = GameState.area == null ? "" : GameState.area.trim();
            for (Area a : areas) {
                if (a.name().equals(here)) continue;
                boolean target = isTarget(a);
                boolean hover = ui.hover(x, y, w, ROW);
                if (hover) ui.rect(x, y, w, ROW, 0x30FFFFFF);
                Modern.row(ui, (target ? "§6" : "§e") + a.name(), "§e" + a.distance(), x, y, w, Modern.LABEL, Modern.INK);
                if (hover) ui.tooltip("§e" + a.name(), "§7Distance: §e" + a.distance() + " blocks", "", target ? "§eClick to stop navigating" : "§eClick to find a path");
                ui.click(x, y, w, ROW, () -> click(a));
                y += ROW;
            }
            return y - top;
        }
    };

    private static SideHelper.Content panel(Screen s) {
        return s instanceof InventoryScreen && GameState.inSkyBlock && Features.on(ID) && opt("island_areas.list") ? PANEL : null;
    }

    private static RichCard.Content card(String current, List<Area> list) {
        List<RichCard.Line> out = new ArrayList<>();
        if (current != null) out.add(RichCard.Line.row("Current area", current.isEmpty() ? "§7None" : "§e" + current).raw().brief("Here"));
        int n = 0;
        for (Area a : list) {
            if (a.name().equals(current)) continue;
            if (++n > 6) break;
            out.add(RichCard.Line.row(a.name(), "§e" + a.distance()).raw());
        }
        return new RichCard.Content("", out);
    }

    private static final HudElement CARD = new RichCard(ID, "Island areas", "Misc", Zone.TOP_LEFT, 56, ID) {
        @Override public boolean visible() {
            return GameState.inSkyBlock && opt("island_areas.always") && !areas.isEmpty();
        }

        @Override protected Content live() {
            return card(opt("island_areas.current") ? GameState.area.trim() : null, areas);
        }

        @Override protected Content sample() {
            return card("Village", List.of(new Area("Coal Mine", Vec3.ZERO, false, 42), new Area("Forest", Vec3.ZERO, false, 88)));
        }

        @Override protected List<Row> classicRows() {
            List<Row> out = new ArrayList<>();
            for (Area a : areas.subList(0, Math.min(6, areas.size()))) out.add(new Row(a.name(), String.valueOf(a.distance()), Theme.current().text()));
            return out;
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Coal Mine", "42", Theme.current().text()));
        }
    };

    public static void register() {
        Features.add(new Feature(ID, "Navigation", "Island areas", "The areas of this island, nearest first: beside your inventory, where a click finds the path, and if you like as a card, on entering, and in the world.", "SkyHanni: Areas List, Title on Enter, Show in World")
                .when(() -> GameState.inSkyBlock).tick(IslandAreas::tick).world(IslandAreas::world).left(() -> {
                    areas = List.of();
                    lastArea = "";
                })
                .option(Option.bool("island_areas.list", "List beside the inventory", "Click an area to find the path to it.", false))
                .option(Option.bool("island_areas.always", "Also as a card", "The list outside your inventory as well, without clicks.", false))
                .option(Option.bool("island_areas.current", "Show the area you are in", "", false))
                .option(Option.bool("island_areas.small", "Small areas too", "", false))
                .option(Option.bool("island_areas.title", "Title on entering an area", "", false))
                .option(Option.bool("island_areas.world", "Names in the world", "The areas around you, at their borders.", false)));
        SideHelper.add(IslandAreas::panel);
        HudLayout.add(CARD);
    }
}
