// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyCubed; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.map;

import com.mojang.blaze3d.platform.NativeImage;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.nav.Graph;
import dev.zmuzy.sbc.nav.Navigator;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.hud.ElementStyle;
import dev.zmuzy.sbc.ui.hud.Looks;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// maps. the minimap only shows the islands prepared image, never anything read from the world around you.
// hypixels minimap rules are about reading the world, so an island without an image shows nothing
public final class MapRender {
    private MapRender() {}

    public static final Identifier ISLAND = Identifier.fromNamespaceAndPath("sbcompanion", "island_map");
    public static final int ISLAND_N = 512;

    private static DynamicTexture islandTex;
    private static String islandFor = "";
    // world bounds of the island map texture: minX, minZ, blocks per pixel
    public static double islMinX, islMinZ, islBpp = 1;

    public record Marker(String name, Vec3 pos, int color, String kind, String texture) {
        public Marker(String name, Vec3 pos, int color, String kind) {
            this(name, pos, color, kind, null);
        }
    }

    // minimap hud widget, top left at x, y
    public static void drawMinimap(Ui ui, int x, int y, int size) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        SkyMaps.IslandMap sky = SkyMaps.ready(GameState.island);
        if (sky == null) return;
        if (Config.I.minimapRound) drawRound(ui, x, y, size, sky);
        else drawSkyMinimap(ui, x, y, size, sky);
    }

    // whats behind a square map: the themes card
    private static void squarePanel(Ui ui, int x, int y, int size) {
        Theme.current().panel(ui, x - 3, y - 3, size + 6, size + 16, ElementStyle.of("minimap"));
    }

    private static void squareLabels(Ui ui, int x, int y, int size, Minecraft mc) {
        double px = mc.player.getX(), py = mc.player.getY(), pz = mc.player.getZ();
        northPin(ui, x + size / 2, y + 7);
        String coords = coords((int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz));
        ui.textShadow(ui.ellipsize(coords, size), x + (size - Math.min(size, ui.width(coords))) / 2, y + size + 3, Modern.INK);
    }

    private static String coords(int x, int y, int z) {
        return "§c" + x + "§7, §a" + y + "§7, §b" + z;
    }

    // minimap over the islands drawn map, centered on you
    private static void drawSkyMinimap(Ui ui, int x, int y, int size, SkyMaps.IslandMap m) {
        Minecraft mc = Minecraft.getInstance();
        double px = mc.player.getX(), pz = mc.player.getZ();
        double r = Config.I.minimapRadius;
        double u0 = m.u(px - r), v0 = m.v(pz - r), u1 = m.u(px + r), v1 = m.v(pz + r);
        squarePanel(ui, x, y, size);
        ui.round(x, y, size, size, 4, 0xFF100E0B);
        ui.g.enableScissor(x, y, x + size, y + size);
        // only what the map has. a texture read past its edge wraps around and the island would show again next to itself
        double cu0 = Math.max(0, u0), cv0 = Math.max(0, v0), cu1 = Math.min(m.width, u1), cv1 = Math.min(m.height, v1);
        if (cu1 > cu0 && cv1 > cv0) {
            int sx0 = x + (int) Math.round((cu0 - u0) / (u1 - u0) * size), sx1 = x + (int) Math.round((cu1 - u0) / (u1 - u0) * size);
            int sy0 = y + (int) Math.round((cv0 - v0) / (v1 - v0) * size), sy1 = y + (int) Math.round((cv1 - v0) / (v1 - v0) * size);
            ui.g.blit(RenderPipelines.GUI_TEXTURED, m.texture, sx0, sy0, (float) cu0, (float) cv0, sx1 - sx0, sy1 - sy0,
                    (int) Math.round(cu1 - cu0), (int) Math.round(cv1 - cv0), m.width, m.height, 0xFFFFFFFF);
        }
        double scale = size / (2 * r);
        int cx = x + size / 2, cy = y + size / 2;
        drawPath(ui, x, y, size, size, p -> new double[]{cx + (p.x - px) * scale, cy + (p.z - pz) * scale});
        ui.g.disableScissor();
        pointer(ui, cx, cy, mc.player.getYRot());
        squareLabels(ui, x, y, size, mc);
    }

    // round minimap: the drawn map clipped to a circle, turning with you so ahead is always up. drawn in thin
    // horizontal strips, each clipped to the circle width at that height
    public static final Identifier ROUND = Identifier.fromNamespaceAndPath("sbcompanion", "minimap_round");
    private static final int RS = 160;
    private static DynamicTexture roundTex;
    private static double bakedX = Double.NaN, bakedZ, bakedYaw;
    private static long bakedAt;
    private static Object bakedSource;
    private static float[] rdx, rdy, rfade;
    private static byte[] rkind;
    private static int[] rframe;
    // the ring needs redrawing (its table was just built)
    private static boolean frameStale;

    // round minimap: the drawn map baked into one round texture turned so ahead is up. baked on the cpu only when
    // you move, turn or the source changes, then drawn as one image
    private static void drawRound(Ui ui, int x, int y, int size, SkyMaps.IslandMap sky) {
        Minecraft mc = Minecraft.getInstance();
        double px = mc.player.getX(), pz = mc.player.getZ();
        boolean rot = Config.I.minimapRotate;
        float yaw = mc.player.getYRot();
        double theta = rot ? Math.PI - Math.toRadians(yaw) : 0;
        double cos = Math.cos(theta), sin = Math.sin(theta);
        int R = size / 2, cx = x + R, cy = y + R;
        double r = Config.I.minimapRadius;
        double scale = R / r;

        bake(mc, sky, px, pz, rot ? yaw : 0f, theta, r);
        ui.disc(cx, cy + 2, R + 3, 0x38000000);
        ui.g.blit(RenderPipelines.GUI_TEXTURED, ROUND, x - 2, y - 2, 0f, 0f, size + 4, size + 4, RS, RS, RS, RS, 0xFFFFFFFF);

        Project proj = p -> {
            double dx = (p.x - px) * scale, dz = (p.z - pz) * scale;
            return new double[]{cx + dx * cos - dz * sin, cy + dx * sin + dz * cos};
        };
        // minimap stays simple: map, route, you. npcs (with faces) are on the big map
        drawPath(ui, x, y, size, size, proj, R - 3);
        pointer(ui, cx, cy, rot ? 180f : yaw);

        // inside the rim, at the screen edge a marker outside it gets cut off
        northPin(ui, (int) Math.round(cx + sin * (R - 5)), (int) Math.round(cy - cos * (R - 5)));
        String here = !GameState.area.isEmpty() ? GameState.area : GameState.pretty(GameState.island);
        drawPlace(ui, x, y, size, Config.I.minimapLabels ? here : "", (int) Math.floor(px), (int) Math.floor(mc.player.getY()), (int) Math.floor(pz), yaw);
        return;
    }

    // which way you look like a compass says it. yaw 0 = south, 90 = west
    public static String facing(float yaw) {
        return new String[]{"S", "SW", "W", "NW", "N", "NE", "E", "SE"}[Math.floorMod(Math.round(yaw / 45f), 8)];
    }

    // pill: the end rows step in, as round as the font pixels allow
    private static void pill(Ui ui, int x, int y, int w, int h, int color) {
        ui.rect(x + 2, y, w - 4, 1, color);
        ui.rect(x + 1, y + 1, w - 2, 1, color);
        ui.rect(x, y + 2, w, h - 4, color);
        ui.rect(x + 1, y + h - 2, w - 2, 1, color);
        ui.rect(x + 2, y + h - 1, w - 4, 1, color);
    }

    // red N on a dark disc with a thin edge, centered at x, y
    public static void northPin(Ui ui, int x, int y) {
        ui.disc(x, y, 6, 0x60FFFFFF);
        ui.disc(x, y, 5, 0xF00E141D);
        ui.textShadow("N", x - 2, y - 3, Modern.BAD);
    }

    // what the classic map says under it: place on a pill in the map color, then coords on a quieter one (each
    // axis its usual color), and facing when theres room
    public static void drawPlace(Ui ui, int x, int y, int size, String place, int bx, int by, int bz, float yaw) {
        ElementStyle st = ElementStyle.of("minimap");
        float opacity = st.alpha() != null ? st.alpha() : Config.I.classicAlpha;
        int glass = Ui.alpha(st.bg() != null ? st.bg() | 0xFF000000 : 0xFF111925, opacity), accent = st.titleColor() != null ? st.titleColor() : Looks.of("minimap").accent();
        // a pill can be as wide as the ring around the map
        int cx = x + size / 2, py = y + size + 6, room = size + 4 - 8;
        if (!place.isEmpty()) {
            String name = ui.ellipsize(place, room);
            int w = ui.width(name) + 8, left = cx - w / 2;
            pill(ui, left, py, w, 12, glass);
            ui.rect(left + 2, py, w - 4, 1, 0x28FFFFFF);
            ui.gradH(left + 2, py + 11, w / 2 - 2, 1, Ui.alpha(accent, 0.1f), Ui.alpha(accent, 0.95f));
            ui.gradH(left + w / 2, py + 11, w - w / 2 - 2, 1, Ui.alpha(accent, 0.95f), Ui.alpha(accent, 0.1f));
            ui.textShadow(name, left + 4, py + 2, Modern.INK);
        }
        py += 14;
        String coords = coords(bx, by, bz), withFacing = coords + "  §e" + facing(yaw);
        String text = ui.width(withFacing) <= room ? withFacing : ui.width(coords) <= room ? coords : ui.ellipsize(coords, room);
        int w = ui.width(text) + 8, left = cx - w / 2;
        pill(ui, left, py, w, 12, glass);
        ui.textShadow(text, left + 4, py + 2, Modern.INK);
    }

    // the classic ring for one texture pixel at dx, dy from the center: lit from above like the cards, soft dark outer edge
    public static int classicRing(double dx, double dy, double half) {
        double d = Math.sqrt(dx * dx + dy * dy), fromOutside = half - d;
        int lit = Ui.lerpColor(0xFFD6E2EE, 0xFF4A5A6C, (float) ((dy / half + 1) / 2));
        if (fromOutside < 1) return Ui.alpha(0xFF0A0F17, (float) Math.max(0, fromOutside) * 0.9f);
        return lit;
    }

    private static void bake(Minecraft mc, SkyMaps.IslandMap sky, double px, double pz, float yaw, double theta, double r) {
        long now = System.currentTimeMillis();
        Object source = sky;
        boolean moved = Double.isNaN(bakedX) || Math.abs(px - bakedX) + Math.abs(pz - bakedZ) > r / 80 || Math.abs(yaw - bakedYaw) > 2.5f;
        boolean stale = now - bakedAt > 2000;
        if (roundTex != null && source == bakedSource && !moved && !stale) return;
        if (roundTex != null && now - bakedAt < 60) return; // max ~16 bakes a sec
        if (roundTex == null) {
            roundTex = new DynamicTexture(() -> "sbcompanion round minimap", RS, RS, true);
            mc.getTextureManager().register(ROUND, roundTex);
        }
        NativeImage out = roundTex.getPixels();
        NativeImage src = sky.image;
        if (src == null || src.isClosed()) return;
        int sw = src.getWidth(), sh = src.getHeight();
        double half = RS / 2.0, rIn = half - 2.5; // map radius in texture px, the outer 2.5px is the frame
        double k = rIn / r; // texture px per block
        double c = Math.cos(-theta), s = Math.sin(-theta);
        if (rdx == null) {
            rdx = new float[RS * RS];
            rdy = new float[RS * RS];
            rkind = new byte[RS * RS];
            rfade = new float[RS * RS];
            rframe = new int[RS * RS];
            for (int j = 0; j < RS; j++)
                for (int i = 0; i < RS; i++) {
                    double dx = i + 0.5 - half, dy = j + 0.5 - half, d = Math.sqrt(dx * dx + dy * dy);
                    int n = j * RS + i;
                    rdx[n] = (float) dx;
                    rdy[n] = (float) dy;
                    if (d > half) rkind[n] = 0;
                    else if (d > rIn) rkind[n] = 1;
                    else {
                        rkind[n] = 2;
                        rfade[n] = d > rIn - 5 ? (float) ((d - (rIn - 5)) / 5 * 0.45) : 0f;
                    }
                }
            frameStale = true;
        }
        if (frameStale) {
            frameStale = false;
            for (int n = 0; n < RS * RS; n++) {
                if (rkind[n] != 1) continue;
                rframe[n] = classicRing(rdx[n], rdy[n], half);
            }
        }
        double ik = 1 / k;
        for (int n = 0; n < RS * RS; n++) {
            byte kind = rkind[n];
            if (kind == 0) {
                out.setPixel(n % RS, n / RS, 0);
                continue;
            }
            if (kind == 1) {
                out.setPixel(n % RS, n / RS, rframe[n]);
                continue;
            }
            double dx = rdx[n], dy = rdy[n];
            double wx = px + (dx * c - dy * s) * ik, wz = pz + (dx * s + dy * c) * ik;
            int u = (int) Math.floor(sky.u(wx)), v = (int) Math.floor(sky.v(wz));
            int col = u < 0 || v < 0 || u >= sw || v >= sh ? 0xFF100E0B : src.getPixel(u, v) | 0xFF000000;
            if (rfade[n] > 0f) col = darken(col, rfade[n]);
            out.setPixel(n % RS, n / RS, col);
        }
        roundTex.upload();
        bakedX = px;
        bakedZ = pz;
        bakedYaw = yaw;
        bakedAt = now;
        bakedSource = source;
    }

    private static int darken(int argb, float f) {
        float m = 1 - f;
        int r = (int) (((argb >> 16) & 0xFF) * m), g = (int) (((argb >> 8) & 0xFF) * m), b = (int) ((argb & 0xFF) * m);
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    // --- island map ---

    // builds (once per island) a texture with the islands walkable paths drawn as roads
    public static boolean ensureIslandMap(String island) {
        if (island.equals(islandFor) && islandTex != null) return true;
        Graph g = Repo.graph(island);
        if (g == null || g.isEmpty()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (islandTex == null) {
            islandTex = new DynamicTexture(() -> "sbcompanion island map", ISLAND_N, ISLAND_N, true);
            mc.getTextureManager().register(ISLAND, islandTex);
        }
        double minX = Double.MAX_VALUE, minZ = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxZ = -Double.MAX_VALUE;
        for (Graph.Node n : g.nodes) {
            minX = Math.min(minX, n.pos().x);
            maxX = Math.max(maxX, n.pos().x);
            minZ = Math.min(minZ, n.pos().z);
            maxZ = Math.max(maxZ, n.pos().z);
        }
        double span = Math.max(maxX - minX, maxZ - minZ) + 40;
        islBpp = span / ISLAND_N;
        islMinX = (minX + maxX) / 2 - span / 2;
        islMinZ = (minZ + maxZ) / 2 - span / 2;
        NativeImage img = islandTex.getPixels();
        img.fillRect(0, 0, ISLAND_N, ISLAND_N, 0x00000000);
        for (Graph.Node n : g.nodes) {
            for (int nb : n.neighbours()) {
                if (nb < n.index()) continue;
                Vec3 a = n.pos(), b = g.nodes[nb].pos();
                line(img, (a.x - islMinX) / islBpp, (a.z - islMinZ) / islBpp, (b.x - islMinX) / islBpp, (b.z - islMinZ) / islBpp, 0xFF2E3A63);
            }
        }
        for (Graph.Node n : g.nodes) {
            if (!n.tags().contains("area") && !n.tags().contains("small_area")) continue;
            int px = (int) ((n.pos().x - islMinX) / islBpp), pz = (int) ((n.pos().z - islMinZ) / islBpp);
            if (px > 2 && pz > 2 && px < ISLAND_N - 3 && pz < ISLAND_N - 3) img.fillRect(px - 1, pz - 1, 3, 3, 0xFF3D4C80);
        }
        islandTex.upload();
        islandFor = island;
        return true;
    }

    private static void line(NativeImage img, double x0, double y0, double x1, double y1, int color) {
        double len = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        int steps = (int) Math.max(1, Math.ceil(len));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            int x = (int) Math.round(x0 + (x1 - x0) * t), y = (int) Math.round(y0 + (y1 - y0) * t);
            for (int dx = 0; dx < 2; dx++)
                for (int dy = 0; dy < 2; dy++) {
                    int px = x + dx, py = y + dy;
                    if (px >= 0 && py >= 0 && px < ISLAND_N && py < ISLAND_N) img.setPixel(px, py, color);
                }
        }
    }

    // --- shared overlays ---

    // npcs + warps for an island: graph nodes tagged npc/warp + npcs weve seen
    private static final java.util.Map<String, Object[]> markerCache = new java.util.HashMap<>();

    // cached 2 sec per island bc the minimap asks every frame
    public static List<Marker> markers(String island) {
        long now = System.currentTimeMillis();
        Object[] c = markerCache.get(island);
        if (c != null && now - (long) c[0] < 2000) {
            @SuppressWarnings("unchecked") List<Marker> l = (List<Marker>) c[1];
            return l;
        }
        List<Marker> l = buildMarkers(island);
        markerCache.put(island, new Object[]{now, l});
        return l;
    }

    private static List<Marker> buildMarkers(String island) {
        List<Marker> out = new ArrayList<>();
        Graph g = Repo.graph(island);
        if (g != null && !g.isEmpty()) {
            for (Graph.Node n : g.named()) {
                if (n.tags().contains("npc")) out.add(new Marker(n.name(), n.pos(), 0xFFFFC857, "npc"));
                else if (n.tags().contains("warp")) out.add(new Marker(n.name(), n.pos(), 0xFFB98CFF, "warp"));
            }
        }
        SkyMaps.IslandMap sky = SkyMaps.get(island);
        if (sky != null) for (SkyMaps.Poi p : sky.pois) {
            if (!p.type().equalsIgnoreCase("npc") || out.stream().anyMatch(m -> m.name().equalsIgnoreCase(p.name()))) continue;
            out.add(new Marker(p.name(), p.pos(), 0xFFFFC857, "npc"));
        }
        for (Map.Entry<String, double[]> e : Store.I.npcs.entrySet()) {
            if (!e.getKey().startsWith(island + "|")) continue;
            String name = e.getKey().substring(island.length() + 1);
            if (out.stream().anyMatch(m -> m.name().equalsIgnoreCase(name))) continue;
            out.add(new Marker(name, new Vec3(e.getValue()[0], e.getValue()[1], e.getValue()[2]), 0xFF7FE3A8, "npc"));
        }
        // faces for npc markers: skycubeds skins, else the skin we saw on that npc
        java.util.Map<String, String> faces = new java.util.HashMap<>();
        if (sky != null) for (SkyMaps.Poi p : sky.pois) if (p.texture() != null) faces.put(p.name().toLowerCase(), p.texture());
        for (Map.Entry<String, String> e : Store.I.npcTextures.entrySet())
            if (e.getKey().startsWith(island + "|")) faces.putIfAbsent(e.getKey().substring(island.length() + 1).toLowerCase(), e.getValue());
        List<Marker> withFaces = new ArrayList<>(out.size());
        for (Marker m : out)
            withFaces.add(m.kind().equals("npc") ? new Marker(m.name(), m.pos(), m.color(), m.kind(), faces.get(m.name().toLowerCase())) : m);
        return withFaces;
    }

    // area names (Royal Mines, Village...) at the middle of their graph nodes, for map labels
    public static List<Marker> areas(String island) {
        Graph g = Repo.graph(island);
        List<Marker> out = new ArrayList<>();
        if (g == null || g.isEmpty()) return out;
        java.util.Map<String, double[]> sum = new java.util.HashMap<>();
        for (Graph.Node n : g.named()) {
            if (!n.tags().contains("area") || n.name().equals("no_area")) continue;
            double[] s = sum.computeIfAbsent(n.name(), k -> new double[4]);
            s[0] += n.pos().x;
            s[1] += n.pos().y;
            s[2] += n.pos().z;
            s[3]++;
        }
        for (java.util.Map.Entry<String, double[]> e : sum.entrySet()) {
            double[] s = e.getValue();
            out.add(new Marker(e.getKey(), new Vec3(s[0] / s[3], s[1] / s[3], s[2] / s[3]), 0xFFFFFFFF, "area"));
        }
        return out;
    }

    public interface Project {
        double[] at(Vec3 world);
    }

    // active nav route as a dotted line clipped to the rect
    public static void drawPath(Ui ui, int x, int y, int w, int h, Project proj) {
        drawPath(ui, x, y, w, h, proj, 0);
    }

    // same, optionally kept inside a circle of this radius around the rect center
    public static void drawPath(Ui ui, int x, int y, int w, int h, Project proj, int circle) {
        int ccx = x + w / 2, ccy = y + h / 2;
        long c2 = (long) circle * circle;
        List<Vec3> path = Navigator.path();
        if (path.isEmpty() || Navigator.target() == null || !Navigator.target().island().equals(GameState.island)) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 prev = mc.player == null ? path.get(0) : mc.player.position();
        int color = Config.I.pathColor;
        for (Vec3 p : path) {
            double[] a = proj.at(prev), b = proj.at(p);
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            int steps = (int) Math.max(1, len / 2);
            for (int i = 0; i <= steps; i++) {
                int px = (int) Math.round(a[0] + (b[0] - a[0]) * i / steps), py = (int) Math.round(a[1] + (b[1] - a[1]) * i / steps);
                if (px >= x && py >= y && px < x + w - 1 && py < y + h - 1
                        && (circle <= 0 || (long) (px - ccx) * (px - ccx) + (long) (py - ccy) * (py - ccy) <= c2)) ui.rect(px, py, 2, 2, color);
            }
            prev = p;
        }
        double[] t = proj.at(Navigator.target().pos());
        int tx = (int) t[0], ty = (int) t[1];
        if (tx >= x && ty >= y && tx < x + w && ty < y + h && (circle <= 0 || (long) (tx - ccx) * (tx - ccx) + (long) (ty - ccy) * (ty - ccy) <= c2)) {

            ui.round(tx - 4, ty - 4, 9, 9, 4, 0xFF000000);
            ui.round(tx - 3, ty - 3, 7, 7, 3, color);
        }
    }

    // npc face centered at x, y, size px wide: flat from its skin with the hat layer like the player list, just a
    // soft shadow under it. false = no known skin
    public static boolean drawHead(Ui ui, Marker m, int x, int y, int size) {
        if (m.texture() == null) return false;
        int left = x - size / 2, top = y - size / 2;
        if (ui.g != null) {
            ui.rect(left + 1, top + 1, size, size, 0x50000000);
            if (dev.zmuzy.sbc.ui.Faces.draw(ui.g, m.texture(), left, top, size)) return true;
        }
        ui.icon(dev.zmuzy.sbc.core.ItemIcons.of("", "player_head", m.texture(), -1, false), left, top, size / 16f);
        return true;
    }

    // inside the classic pointer: a dart along u with a notch at its tail, grown by the outline width
    private static boolean onPointer(double u, double v, double grow) {
        double side = Math.abs(v);
        return side <= 4 + grow && u <= 6 + grow - 2.5 * side && u >= -1.5 - grow - 0.625 * side;
    }

    // classic player marker, white on a dark outline, yaw like arrow(). drawn in runs per row, a few dozen rects max
    public static void pointer(Ui ui, int cx, int cy, float yaw) {
        double rad = Math.toRadians(yaw), fx = -Math.sin(rad), fy = Math.cos(rad);
        for (int pass = 0; pass < 2; pass++) {
            double grow = pass == 0 ? 1.15 : 0;
            int color = pass == 0 ? 0xD8000000 : 0xFFFFFFFF;
            for (int j = -8; j <= 8; j++) {
                int run = Integer.MIN_VALUE;
                for (int i = -8; i <= 9; i++) {
                    boolean on = i <= 8 && onPointer(i * fx + j * fy, i * fy - j * fx, grow);
                    if (on && run == Integer.MIN_VALUE) run = i;
                    else if (!on && run != Integer.MIN_VALUE) {
                        ui.rect(cx + run, cy + j, i - run, 1, color);
                        run = Integer.MIN_VALUE;
                    }
                }
            }
        }
    }

    // small arrow where you look (yaw 0 = south / down on a north up map)
    public static void arrow(Ui ui, int cx, int cy, float yaw) {
        double rad = Math.toRadians(yaw);
        double dx = -Math.sin(rad), dz = Math.cos(rad);
        for (int i = -1; i <= 5; i++) {
            int px = (int) Math.round(cx + dx * i), py = (int) Math.round(cy + dz * i);
            ui.rect(px - 1, py - 1, 3, 3, i >= 4 ? 0xFFFFFFFF : 0xFF000000);
            ui.rect(px, py, 1, 1, 0xFFFFFFFF);
        }
        ui.round(cx - 3, cy - 3, 7, 7, 3, 0xFF000000);
        ui.round(cx - 2, cy - 2, 5, 5, 2, 0xFF39D0FF);
    }
}
