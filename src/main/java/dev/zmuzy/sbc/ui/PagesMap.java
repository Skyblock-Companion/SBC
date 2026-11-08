// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, SkyCubed; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Profile;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.map.MapRender;
import dev.zmuzy.sbc.map.SkyMaps;
import dev.zmuzy.sbc.nav.Navigator;
import dev.zmuzy.sbc.ui.hud.Looks;
import dev.zmuzy.sbc.ui.theme.Theme;
import dev.zmuzy.sbc.ui.layout.BaseUIComponent;
import dev.zmuzy.sbc.ui.layout.UIGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

// island map page (wheel zoom, drag pan, click a marker to go there) + the page before a profile is known
final class PagesMap {
    private PagesMap() {}

    static float zoom = 1f;
    static double panX, panY; // map texture px, offset from the fitted center
        private static final MenuUi.Field search = new MenuUi.Field("Search an NPC (e.g. Pomtair)", false);
    private static final int MAP = 0xFF9CE37D;

    private static String query() {
        return search.value.trim().toLowerCase(Locale.ROOT);
    }

    static void scroll(double amount) {
        zoom = (float) Math.max(1, Math.min(8, zoom * (amount > 0 ? 1.25 : 0.8)));
    }

    // === MAP ===

    // empty = overview of every island
    static String viewing = "";

    static int map(MenuUi ui, int x, int y, int w) {
        return viewing.isEmpty() ? overview(ui, x, y, w) : island(ui, x, y, w, viewing);
    }

    private static List<String> islands() {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        for (SkyMaps.IslandMap m : SkyMaps.all()) set.add(m.island);
        for (String g : Repo.graphIslands()) if (!g.equals("MAIN_LOBBY")) set.add(g);
        return new java.util.ArrayList<>(set);
    }

    private static String icon(String island) {
        return switch (island) {
            case "HUB" -> "oak_sapling";
            case "DWARVEN_MINES", "GOLD_MINES", "DEEP_CAVERNS", "GLACITE_TUNNELS" -> "iron_pickaxe";
            case "CRYSTAL_HOLLOWS" -> "amethyst_shard";
            case "CRIMSON_ISLE" -> "blaze_powder";
            case "THE_END" -> "ender_eye";
            case "THE_PARK", "GALATEA" -> "jungle_sapling";
            case "THE_FARMING_ISLANDS" -> "wheat";
            case "SPIDER_DEN" -> "string";
            case "THE_RIFT" -> "ender_pearl";
            case "WINTER" -> "snowball";
            case "DUNGEON_HUB" -> "skeleton_skull";
            case "BACKWATER_BAYOU", "LOTUS_ATOLL" -> "fishing_rod";
            default -> "map";
        };
    }

    // every island has its own color so the tile reads before its name
    private static int color(String island) {
        return switch (island) {
            case "HUB" -> 0xFF9CE37D;
            case "DWARVEN_MINES", "GOLD_MINES", "DEEP_CAVERNS" -> 0xFFFFD166;
            case "GLACITE_TUNNELS" -> 0xFF9BE7FF;
            case "CRYSTAL_HOLLOWS" -> 0xFFEC7DD8;
            case "CRIMSON_ISLE" -> 0xFFFF6B4A;
            case "THE_END" -> 0xFFC77DFF;
            case "THE_PARK", "GALATEA" -> 0xFF81C784;
            case "THE_FARMING_ISLANDS" -> 0xFFFFC857;
            case "SPIDER_DEN" -> 0xFFB388FF;
            case "THE_RIFT" -> 0xFFD070FF;
            case "WINTER" -> 0xFF7FD4FF;
            case "DUNGEON_HUB" -> 0xFFFF5D73;
            case "BACKWATER_BAYOU", "LOTUS_ATOLL" -> 0xFF4FC3F7;
            default -> Looks.colourFor(island);
        };
    }

    private static int overview(MenuUi ui, int x, int y, int w) {
        int y0 = y, gap = MenuUi.GAP;
        ui.input(search, x, y, Math.min(260, w / 2));
        ui.text("Pick an island, or search an NPC on every island.", x + Math.min(260, w / 2) + 8, y + 5, Ui.DIM);
        y += 18 + 4;
        List<String> list = islands();
        String q = query();
        if (!q.isEmpty()) {
            List<Object[]> found = new java.util.ArrayList<>();
            for (String isl : list)
                for (MapRender.Marker m : MapRender.markers(isl)) {
                    if (!m.kind().equals("npc") || !m.name().toLowerCase(Locale.ROOT).contains(q)) continue;
                    if (found.size() < 30) found.add(new Object[]{isl, m});
                }
            int rowH = 20, h = MenuUi.TOP + Math.max(1, found.size()) * rowH;
            int top = ui.card(x, y, w, h, "NPCs matching \"" + search.value.trim() + "\"", "villager_spawn_egg", String.valueOf(found.size()), MAP);
            int ry = y + top - 2;
            for (Object[] f : found) {
                String isl = (String) f[0];
                MapRender.Marker m = (MapRender.Marker) f[1];
                ui.hoverRound(x + 2, ry, w - 4, rowH - 1, 5, 0x00000000, 0x20FFFFFF);
                ui.head(m, x + 12, ry + 9, 14, m.color());
                ui.text(ui.ellipsize(m.name(), w / 2 - 30), x + 26, ry + 5, Ui.TEXT);
                ui.icon(icon(isl), x + w / 2, ry + 3, 0.75f);
                ui.text(GameState.pretty(isl), x + w / 2 + 15, ry + 5, Ui.MUTED);
                ui.textRight("Show on map ›", x + w - 8, ry + 5, Ui.CYAN);
                ui.click(x, ry, w, rowH - 1, () -> {
                    viewing = isl;
                    zoom = 1;
                    panX = panY = 0;
                });
                ry += rowH;
            }
            if (found.isEmpty()) ui.text("No NPC called \"" + search.value.trim() + "\" on any map.", x + 6, ry + 5, Ui.MUTED);
            return y - y0 + h + gap;
        }
        int cols = Math.max(2, Math.min(4, w / 140));
        int tg = 4, tw = (w - (cols - 1) * tg) / cols, th = 44;
        int i = 0;
        int total = 0;
        for (String isl : list) {
            int tx = x + (i % cols) * (tw + tg), ty = y + (i / cols) * (th + tg);
            boolean here = isl.equals(GameState.island);
            int accent = MenuUi.tone(color(isl));
            int face = 0xE81A2431, hover = 0xF0263344;
            ui.hoverRound(tx, ty, tw, th, 8, face, hover);
            ui.rect(tx, ty, tw, 1, here ? accent : Ui.alpha(accent, 0.55f));
            ui.rect(tx, ty, 2, th, accent);
            if (here) ui.gradH(tx + 2, ty + 1, tw - 2, th - 1, Ui.alpha(accent, 0.22f), Ui.alpha(accent, 0.02f));
            ui.icon(icon(isl), tx + 6, ty + 6, 1f);
            ui.text(ui.ellipsize(GameState.pretty(isl), tw - 30), tx + 26, ty + 5, Ui.TEXT);
            int npcs = (int) MapRender.markers(isl).stream().filter(m -> m.kind().equals("npc")).count();
            total += npcs;
            ui.text(npcs + " NPCs", tx + 26, ty + 16, Ui.MUTED);
            ui.text(SkyMaps.get(isl) != null ? "drawn map" : "path map", tx + 26, ty + 27, Ui.DIM);
            if (here) ui.chip(tx + tw - ui.chipWidth("HERE") - 3, ty + th - 14, "HERE", accent);
            final String fi = isl;
            ui.click(tx, ty, tw, th, () -> {
                viewing = fi;
                zoom = 1;
                panX = panY = 0;
            });
            i++;
        }
        y += ((i + cols - 1) / cols) * (th + tg);
        if (list.isEmpty()) {
            ui.text("Island maps come from other SkyBlock mods' data (install one once to get them).", x, y, Ui.MUTED);
            y += 14;
        } else {
            ui.text(list.size() + " islands · " + total + " NPCs on the maps", x + 2, y + 2, Ui.DIM);
            y += 14;
        }
        return y - y0 + 4;
    }

    private static int island(MenuUi ui, int x, int y, int w, String isl) {
        int y0 = y, gap = MenuUi.GAP;
        int fw = Math.min(200, w / 3);
        ui.input(search, x + w - fw, y - 1, fw);
        String q = query();
        ui.button("mback", x, y, 84, 16, "‹ All islands", Ui.SOFT, () -> viewing = "");
        ui.icon(icon(isl), x + 92, y, 1f);
        ui.textScaled(GameState.pretty(isl), x + 112, y + 3, 1.25f, Ui.TEXT, true);
        if (isl.equals(GameState.island)) ui.chip(x + 118 + (int) (ui.width(GameState.pretty(isl)) * 1.25f), y + 3, "YOU ARE HERE", Ui.CYAN);
        y += 22;

        int sideW = w >= 520 ? 170 : 0;
        int boxW = w - (sideW > 0 ? sideW + gap : 0);
        int boxH = Math.min(380, Math.max(220, boxW * 3 / 4));
        SkyMaps.IslandMap sky = SkyMaps.ready(isl);
        if (sky == null && !MapRender.ensureIslandMap(isl)) {
            ui.round(x, y, boxW, boxH, 8, Ui.INPUT_BG);
            ui.textCenter(SkyMaps.status(isl), x + boxW / 2, y + boxH / 2, Ui.MUTED);
            return boxH + 28;
        }
        if (sky == null && SkyMaps.get(isl) != null) ui.textRight(SkyMaps.status(isl), x + w, y - 12, Ui.DIM);

        MapView view = ui.keep("map", x, y, boxW, boxH, MapView::new);
        view.show(isl, q);

        if (sideW > 0) {
            int sx = x + boxW + gap;
            int top = ui.card(sx, y, sideW, boxH, "Legend", "filled_map", null, color(isl));
            int ly = y + top;
            if (!q.isEmpty()) {
                List<MapRender.Marker> matches = MapRender.markers(isl).stream().filter(m -> m.name().toLowerCase(Locale.ROOT).contains(q)).toList();
                ui.text(matches.size() + " match" + (matches.size() == 1 ? "" : "es"), sx + 6, ly, Ui.GOLD);
                int my2 = ly + 12;
                for (MapRender.Marker m : matches) {
                    if (my2 > y + boxH - 40) break;
                    ui.text(ui.ellipsize(m.name(), sideW - 50), sx + 6, my2, Ui.TEXT);
                    ui.button("sgo" + m.name() + my2, sx + sideW - 38, my2 - 3, 32, 13, "Go", Ui.SOFT,
                            () -> Navigator.navigate(new Navigator.Target(isl, m.name(), m.pos())));
                    my2 += 15;
                }
                ly = my2 + 4;
            }
            legend(ui, sx + 6, ly, 0xFFFFC857, "NPC (face = their skin)");
            legend(ui, sx + 6, ly + 11, 0xFF5CC8FF, "Place of interest");
            legend(ui, sx + 6, ly + 22, 0xFF7FE3A8, "NPC the mod has seen");
            legend(ui, sx + 6, ly + 33, 0xFFB98CFF, "Warp");
            legend(ui, sx + 6, ly + 44, Config.I.pathColor, "Your route");
            ui.hair(sx + 6, ly + 56, sideW - 12);
            List<MapRender.Marker> all = MapRender.markers(isl);
            long npcs = all.stream().filter(m -> m.kind().equals("npc")).count(), warps = all.stream().filter(m -> m.kind().equals("warp")).count();
            ui.row("NPCs on this map", String.valueOf(npcs), sx + 6, ly + 60, sideW - 12, Ui.GOLD);
            ui.row("Warps", String.valueOf(warps), sx + 6, ly + 70, sideW - 12, MenuUi.tone(0xFFB98CFF));
            ui.row("Zoom", String.format(Locale.ROOT, "%.1fx", zoom), sx + 6, ly + 80, sideW - 12, Ui.TEXT);
            ui.text("Wheel: zoom · drag: move", sx + 6, ly + 93, Ui.DIM);
            ui.text("Click a dot to navigate", sx + 6, ly + 103, Ui.DIM);
            ui.paint(sx + 4, ly + 116, sideW - 8, 40, (u, cx, cy, cw, ch) -> {
                MapRender.Marker hovered = hoveredMarker;
                if (hovered == null) return;
                u.rect(cx, cy, cw, ch, Ui.PANEL3);
                u.text(u.ellipsize(hovered.name(), cw - 12), cx + 5, cy + 4, Ui.TEXT);
                u.text(hovered.kind().equals("warp") ? "Warp" : hovered.kind().equals("npc") ? "NPC" : "Place", cx + 5, cy + 15, Ui.MUTED);
                u.text((int) hovered.pos().x + ", " + (int) hovered.pos().y + ", " + (int) hovered.pos().z, cx + 5, cy + 26, Ui.DIM);
            });
            int by = y + boxH - 20;
            ui.button("mzin", sx + 6, by, 22, 14, "+", Ui.SOFT, () -> scroll(1));
            ui.button("mzout", sx + 31, by, 22, 14, "-", Ui.SOFT, () -> scroll(-1));
            ui.button("mreset", sx + 56, by, 48, 14, "Reset", Ui.GHOST, () -> {
                zoom = 1;
                panX = panY = 0;
            });
        }
        return y - y0 + boxH + gap;
    }

    // marker under the mouse, for the legend card
    private static MapRender.Marker hoveredMarker;

    // the map picture with markers, drawn by its own code. wheel zooms, drag pans, click in place on a marker =
    // navigate there
    private static final class MapView extends BaseUIComponent {
        private final Ui ui = new Ui();
        private final java.util.List<Object[]> markerSpots = new java.util.ArrayList<>();
        private String isl = "", q = "";
        private double lastDragX = Double.NaN, lastDragY, pressX, pressY, k = 1;
        private boolean moved;

        void show(String island, String query) {
            isl = island;
            q = query;
        }

        @Override
        public void draw(UIGraphics g, int mouseX, int mouseY, float partialTicks, float delta) {
            ui.begin(g, mouseX, mouseY);
            markerSpots.clear();
            hoveredMarker = null;
            int boxW = width, boxH = height;
            SkyMaps.IslandMap sky = SkyMaps.ready(isl);
            int texW, texH;
            net.minecraft.resources.Identifier tex;
            java.util.function.ToDoubleFunction<Vec3> toU, toV;
            if (sky != null) {
                tex = sky.texture;
                texW = sky.width;
                texH = sky.height;
                toU = p -> sky.u(p.x);
                toV = p -> sky.v(p.z);
            } else if (MapRender.ensureIslandMap(isl)) {
                tex = MapRender.ISLAND;
                texW = texH = MapRender.ISLAND_N;
                toU = p -> (p.x - MapRender.islMinX) / MapRender.islBpp;
                toV = p -> (p.z - MapRender.islMinZ) / MapRender.islBpp;
            } else return;

            // fit the image in the box, keep aspect ratio
            double fit = Math.min((double) boxW / texW, (double) boxH / texH);
            int dw = (int) (texW * fit), dh = (int) (texH * fit);
            int mapX = x + (boxW - dw) / 2, mapY = y + (boxH - dh) / 2;
            double vw = texW / zoom, vh = texH / zoom;
            double cx = texW / 2.0 + panX, cy = texH / 2.0 + panY;
            cx = Math.max(vw / 2, Math.min(texW - vw / 2, cx));
            cy = Math.max(vh / 2, Math.min(texH - vh / 2, cy));
            panX = cx - texW / 2.0;
            panY = cy - texH / 2.0;
            double u0 = cx - vw / 2, v0 = cy - vh / 2;
            ui.rect(x, y, boxW, boxH, Ui.INPUT_BG);
            g.enableScissor(mapX, mapY, mapX + dw, mapY + dh);
            g.blit(RenderPipelines.GUI_TEXTURED, tex, mapX, mapY, (float) u0, (float) v0, dw, dh, (int) Math.round(vw), (int) Math.round(vh), texW, texH, 0xFFFFFFFF);
            k = dw / vw;
            final double scale = k;
            final double fu0 = u0, fv0 = v0;
            MapRender.Project proj = p -> new double[]{mapX + (toU.applyAsDouble(p) - fu0) * scale, mapY + (toV.applyAsDouble(p) - fv0) * scale};

            for (MapRender.Marker a : MapRender.areas(isl)) {
                double[] p = proj.at(a.pos());
                int ax = (int) p[0], ay = (int) p[1];
                if (ax < mapX || ay < mapY || ax >= mapX + dw || ay >= mapY + dh) continue;
                float sc = zoom >= 2 ? 0.8f : 0.6f;
                int tw = (int) (ui.width(a.name()) * sc);
                ui.rect(ax - tw / 2 - 3, ay - 3, tw + 6, 10, 0x88000000);
                ui.textScaled(a.name(), ax - tw / 2f, ay - 1, sc, 0xFFFFF2C2, false);
            }
            int headPx = zoom >= 3 ? 14 : zoom >= 1.6f ? 11 : 9;
            for (MapRender.Marker m : MapRender.markers(isl)) {
                boolean match = !q.isEmpty() && m.name().toLowerCase(Locale.ROOT).contains(q);
                if (!q.isEmpty() && !match) continue; // searching: only the npc you look for stays
                double[] p = proj.at(m.pos());
                int mx = (int) p[0], my = (int) p[1];
                if (mx < mapX || my < mapY || mx >= mapX + dw || my >= mapY + dh) continue;
                int size = match ? 16 : headPx;
                boolean hv = Math.abs(mouseX - mx) <= size / 2 + 1 && Math.abs(mouseY - my) <= size / 2 + 1 && isInBoundingBox(mouseX, mouseY);
                if (hv) hoveredMarker = m;
                if (match) {
                    float pulse = (float) (0.5 + 0.5 * Math.sin(ui.now / 200.0));
                    ui.ring(mx, my, size / 2 + 4 + (int) (pulse * 3), 2, 0, 1, Ui.GOLD, Ui.ORANGE);
                }
                if (!m.kind().equals("npc") || !MapRender.drawHead(ui, m, mx, my, hv ? size + 4 : size)) {
                    ui.rect(mx - 3, my - 3, 7, 7, 0xFF000000);
                    ui.rect(mx - 2, my - 2, 5, 5, hv ? 0xFFFFFFFF : m.color());
                }
                if (match || (zoom >= 2f && m.kind().equals("npc")) || hv)
                    ui.textScaled(m.name(), mx + size / 2 + 3, my - 3, hv || match ? 0.8f : 0.6f, 0xFFFFFFFF, true);
                markerSpots.add(new Object[]{m, mx, my, size});
            }
            if (isl.equals(GameState.island)) {
                MapRender.drawPath(ui, mapX, mapY, dw, dh, proj);
                var pl = Minecraft.getInstance().player;
                if (pl != null) {
                    double[] p = proj.at(pl.position());
                    MapRender.arrow(ui, (int) p[0], (int) p[1], pl.getYRot());
                }
            }
            g.disableScissor();
        }

        @Override
        public boolean canFocus(FocusSource source) {
            return source == FocusSource.MOUSE_CLICK; // only the focused component gets drags and releases
        }

        @Override
        public boolean onMouseDown(MouseButtonEvent e, boolean doubled) {
            var focus = focusHandler();
            if (focus != null) focus.focus(this, FocusSource.MOUSE_CLICK);
            lastDragX = pressX = e.x();
            lastDragY = pressY = e.y();
            moved = false;
            return true;
        }

        @Override
        public boolean onMouseDrag(MouseButtonEvent e, double deltaX, double deltaY) {
            if (Double.isNaN(lastDragX)) return false;
            if (Math.abs(e.x() - pressX) > 2 || Math.abs(e.y() - pressY) > 2) moved = true;
            panX -= (e.x() - lastDragX) / k;
            panY -= (e.y() - lastDragY) / k;
            lastDragX = e.x();
            lastDragY = e.y();
            return true;
        }

        @Override
        public boolean onMouseUp(MouseButtonEvent e) {
            boolean pressed = !Double.isNaN(lastDragX);
            lastDragX = Double.NaN;
            if (!pressed || moved) return pressed;
            int mx = x + (int) pressX, my = y + (int) pressY;
            for (int i = markerSpots.size() - 1; i >= 0; i--) {
                Object[] spot = markerSpots.get(i);
                int half = (int) spot[3] / 2 + 1;
                if (Math.abs(mx - (int) spot[1]) > half || Math.abs(my - (int) spot[2]) > half) continue;
                MapRender.Marker m = (MapRender.Marker) spot[0];
                Navigator.navigate(new Navigator.Target(isl, m.name(), m.pos()));
                break;
            }
            return true;
        }

        @Override
        public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
            scroll(amount);
            return true;
        }
    }

    private static void legend(MenuUi ui, int x, int y, int color, String label) {
        ui.rect(x, y + 1, 6, 6, color);
        ui.text(label, x + 10, y, Ui.MUTED);
    }

    // === FIRST LOOK ===

    static void setup(MenuUi ui, int x, int y, int w, int h) {
        int cw = Math.min(520, w);
        int cx = x + (w - cw) / 2;
        int ch = 60;
        int cy = y + Math.max(0, (h - ch) / 2 - 10);
        int top = MenuUi.TOP;
        top = ui.card(cx, cy, cw, ch, "Profile", "player_head", null, Ui.CYAN);
        ui.textScaled("Your profile", cx + 12, cy + top, 1.5f, Ui.TEXT, true);
        ui.text("It fills in from what the game shows, once you are in SkyBlock.", cx + 12, cy + top + 16, Ui.MUTED);
    }
}
