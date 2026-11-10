// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mining;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.render.WorldDraw;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Map;

// crystal hollows looks. from skyhanni CrystalHollowsNamesInCore, skyblocker NucleusWaypoints (4 quarters
// named from the nucleus)
// skyblocker NucleusBarriersBox (where a crystal goes), CrystalHollowsWalls (walls between quarters),
// CrystalsHudWidget (map with the places you know)
public final class CrystalCore {
    private CrystalCore() {}

    static final String NAMES = "mining_core_names", BARRIERS = "mining_nucleus_barriers", WALLS = "mining_area_walls", MAP = "mining_crystal_map";
    private static final Map<Vec3, String> CORE = Map.of(
            new Vec3(550, 116, 550), "§8Precursor Remnants",
            new Vec3(552, 116, 474), "§bMithril Deposits",
            new Vec3(477, 116, 476), "§aJungle",
            new Vec3(474, 116, 554), "§6Goblin Holdout");
    private static final Vec3 NUCLEUS = new Vec3(513, 106, 524);
    private static final Identifier MAP_TEXTURE = Identifier.fromNamespaceAndPath("sbcompanion", "textures/gui/crystals_map.png");
    private static final Identifier MAP_ICON = Identifier.withDefaultNamespace("textures/map/decorations/player.png");

    private enum Crystal {
        AMBER(474, 524, "amber", 0x7FCB6400), AMETHYST(474, 492, "amethyst", 0x7FA80BCB), TOPAZ(508, 473, "topaz", 0x7FCDC400), JADE(542, 492, "jade", 0x5568CB00), SAPPHIRE(542, 524, "sapphire", 0x7F2964CB);

        final AABB box;
        final String key;
        final int def;

        // skyhanni LorenzVec(x, 124, z).axisAlignedTo(x + 11, 111, z + 11).expandBlock()
        Crystal(int x, int z, String key, int def) {
            this.box = new AABB(x, 111, z, x + 12, 125, z + 12).inflate(0.002);
            this.key = key;
            this.def = def;
        }
    }

    private static boolean inNucleus() {
        return "Crystal Nucleus".equals(GameState.area);
    }

    private static void drawNames(WorldDraw w) {
        Minecraft mc = Minecraft.getInstance();
        if (!inNucleus() || mc.player == null || mc.player.getY() <= 65) return;
        for (var e : CORE.entrySet()) if (e.getKey().distanceToSqr(w.eye) > 50) w.dynamicText(e.getKey(), e.getValue(), 2.5, 0);
        if (NUCLEUS.distanceToSqr(w.eye) > 50) w.dynamicText(NUCLEUS, "§cNucleus", 2.5, 0);
    }

    private static void drawBarriers(WorldDraw w) {
        if (!inNucleus()) return;
        if (MiningPack.on(BARRIERS, "mining_nucleus_barriers.only_hoppity") && !io.github.zmuzy.sbc.events.hoppity.HoppityTime.isHoppityEvent()) return;
        boolean filled = !"Outline".equals(MiningPack.choice(BARRIERS, "mining_nucleus_barriers.style", "Filled"));
        for (Crystal c : Crystal.values()) {
            int color = MiningPack.color(BARRIERS, "mining_nucleus_barriers." + c.key, c.def);
            if (filled) w.filledBox(c.box, color & 0xFFFFFF, ((color >>> 24) & 0xFF) / 255f, false);
            else w.box(c.box, color, false, false);
        }
    }

    // area walls

    private static final double HEAT = 65, TOP = 190, MIDDLE_X = 513, MIDDLE_Z = 513, MAX = 1024, THIN = 0.02;
    private static final int GREEN = 0x3C55FF55, BLUE = 0x3C5555FF, PURPLE = 0x3CFF55FF, GOLD = 0x3CFFAA00, RED = 0x3CFF5555, WHITE = 0x3CFFFFFF;
    private static final AABB NUCLEUS_BOX = new AABB(463, HEAT, 460, 560, TOP, 563);

    // one wall = very thin box. they reach way past the viewer so no point culling
    private static void wall(double x0, double y0, double z0, double x1, double y1, double z1, int color) {
        Gizmos.cuboid(new AABB(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1) + (x0 == x1 ? THIN : 0), Math.max(y0, y1) + (y0 == y1 ? THIN : 0), Math.max(z0, z1) + (z0 == z1 ? THIN : 0)), GizmoStyle.fill(color));
    }

    // skyblocker drawArea: the quarter youre in, with the neighbours colors on the sides facing them
    private static void quarter(boolean minX, boolean minZ, int nextX, int nextZ) {
        double nx = minX ? NUCLEUS_BOX.minX : NUCLEUS_BOX.maxX, nz = minZ ? NUCLEUS_BOX.minZ : NUCLEUS_BOX.maxZ;
        double x = minX ? 0 : MAX, z = minZ ? 0 : MAX;
        wall(nx, HEAT, MIDDLE_Z, x, TOP, MIDDLE_Z, nextX);
        wall(MIDDLE_X, HEAT, nz, MIDDLE_X, TOP, z, nextZ);
        wall(nx, HEAT, MIDDLE_Z, nx, TOP, nz, WHITE);
        wall(MIDDLE_X, HEAT, nz, nx, TOP, nz, WHITE);
        wall(x, HEAT, z, MIDDLE_X, HEAT, MIDDLE_Z, RED);
    }

    private static void drawWalls(WorldDraw w) {
        Vec3 p = w.eye;
        if (p.y < HEAT) {
            wall(NUCLEUS_BOX.minX, HEAT, NUCLEUS_BOX.minZ, NUCLEUS_BOX.maxX, HEAT, NUCLEUS_BOX.maxZ, WHITE);
            wall(MIDDLE_X, HEAT, MIDDLE_Z, MAX, HEAT, MAX, BLUE);
            wall(MIDDLE_X, HEAT, MIDDLE_Z, MAX, HEAT, 0, GREEN);
            wall(MIDDLE_X, HEAT, MIDDLE_Z, 0, HEAT, MAX, GOLD);
            wall(MIDDLE_X, HEAT, MIDDLE_Z, 0, HEAT, 0, PURPLE);
        } else if (NUCLEUS_BOX.contains(p)) {
            if (!MiningPack.on(WALLS, "mining_area_walls.nucleus")) return;
            wall(NUCLEUS_BOX.minX, HEAT, NUCLEUS_BOX.minZ, NUCLEUS_BOX.maxX, HEAT, NUCLEUS_BOX.maxZ, RED);
            wall(NUCLEUS_BOX.minX, HEAT, NUCLEUS_BOX.minZ, NUCLEUS_BOX.minX, TOP, MIDDLE_Z, PURPLE);
            wall(NUCLEUS_BOX.minX, HEAT, NUCLEUS_BOX.minZ, MIDDLE_X, TOP, NUCLEUS_BOX.minZ, PURPLE);
            wall(NUCLEUS_BOX.maxX, HEAT, NUCLEUS_BOX.maxZ, NUCLEUS_BOX.maxX, TOP, MIDDLE_Z, BLUE);
            wall(NUCLEUS_BOX.maxX, HEAT, NUCLEUS_BOX.maxZ, MIDDLE_X, TOP, NUCLEUS_BOX.maxZ, BLUE);
            wall(NUCLEUS_BOX.minX, HEAT, NUCLEUS_BOX.maxZ, NUCLEUS_BOX.minX, TOP, MIDDLE_Z, GOLD);
            wall(NUCLEUS_BOX.minX, HEAT, NUCLEUS_BOX.maxZ, MIDDLE_X, TOP, NUCLEUS_BOX.maxZ, GOLD);
            wall(NUCLEUS_BOX.maxX, HEAT, NUCLEUS_BOX.minZ, NUCLEUS_BOX.maxX, TOP, MIDDLE_Z, GREEN);
            wall(NUCLEUS_BOX.maxX, HEAT, NUCLEUS_BOX.minZ, MIDDLE_X, TOP, NUCLEUS_BOX.minZ, GREEN);
        } else if (p.x > MIDDLE_X) {
            if (p.z > MIDDLE_Z) quarter(false, false, GREEN, GOLD);
            else quarter(false, true, BLUE, PURPLE);
        } else {
            if (p.z > MIDDLE_Z) quarter(true, false, PURPLE, BLUE);
            else quarter(true, true, GOLD, GREEN);
        }
    }

    // map

    // skyblocker transformLocation: world x or z onto the 62 px map
    static int onMap(double v) {
        return Math.clamp((int) ((v - 202) / 621 * 62), 0, 62);
    }

    // skyblocker yaw2Cardinal: the 16 ways the map marker can point
    static float cardinal(float yaw) {
        yaw += 180;
        byte clipped = (byte) ((yaw + (yaw < 0 ? -8 : 8)) * 16 / 360);
        return clipped * 360f / 16f;
    }

    private static final HudElement MAP_CARD = new HudElement(MAP, "Crystal Hollows map", "Mining", Zone.TOP_LEFT, 44, MAP) {
        @Override
        public boolean visible() {
            return MiningPack.hollows();
        }

        @Override
        public List<Row> rows() {
            return List.of();
        }

        @Override
        public int width() {
            return 62;
        }

        @Override
        public int height() {
            return 62;
        }

        @Override
        public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
            g.blit(RenderPipelines.GUI_TEXTURED, MAP_TEXTURE, x, y, 0f, 0f, 62, 62, 62, 62);
            if (MiningPack.on(MAP, "mining_crystal_map.places")) {
                int size = MiningPack.number(MAP, "mining_crystal_map.place_size", 8);
                for (var e : CrystalWaypoints.ACTIVE.entrySet()) {
                    int s = e.getKey().small() ? size / 2 : size;
                    int px = x + onMap(e.getValue().x), py = y + onMap(e.getValue().z);
                    g.fill(px - s / 2, py - s / 2, px + s / 2, py + s / 2, 0xFF000000 | e.getKey().rgb);
                }
            }
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            var pose = g.pose();
            pose.pushMatrix();
            pose.translate(x + onMap(mc.player.getX()) - 2, y + onMap(mc.player.getZ()) - 3);
            pose.scale(0.75f, 0.75f);
            pose.rotateAbout(Mth.DEG_TO_RAD * cardinal(mc.player.getYRot()), 2.5f, 3.5f);
            g.blit(RenderPipelines.GUI_TEXTURED, MAP_ICON, 0, 0, 2f, 0f, 5, 7, 8, 8);
            pose.popMatrix();
        }
    };

    public static void register() {
        Features.add(new Feature(NAMES, "Mining", "Names from the nucleus", "From the Crystal Nucleus you see in which direction the four quarters and the nucleus lie.", "SkyHanni: Names in Core; Skyblocker: Nucleus Waypoints")
                .when(MiningPack::hollows).world(CrystalCore::drawNames));
        Features.add(new Feature(BARRIERS, "Mining", "Nucleus crystal boxes", "Coloured boxes in the Crystal Nucleus where each crystal goes.", "SkyHanni: Highlight Nucleus Barriers")
                .when(MiningPack::hollows).world(CrystalCore::drawBarriers)
                .option(Option.choice("mining_nucleus_barriers.style", "Look", "", "Filled", "Filled", "Outline"))
                .option(Option.bool("mining_nucleus_barriers.only_hoppity", "Only during Hoppity's Hunt", "", false))
                .option(Option.color("mining_nucleus_barriers.amber", "Amber", "", 0x7FCB6400))
                .option(Option.color("mining_nucleus_barriers.amethyst", "Amethyst", "", 0x7FA80BCB))
                .option(Option.color("mining_nucleus_barriers.jade", "Jade", "", 0x5568CB00))
                .option(Option.color("mining_nucleus_barriers.sapphire", "Sapphire", "", 0x7F2964CB))
                .option(Option.color("mining_nucleus_barriers.topaz", "Topaz", "", 0x7FCDC400)));
        Features.add(new Feature(WALLS, "Mining", "Area walls", "Coloured walls between the quarters of the Crystal Hollows and over the Magma Fields. They change how the place looks, so they start off.", "SkyHanni: Area Walls")
                .when(MiningPack::hollows).defaultOff().world(CrystalCore::drawWalls)
                .option(Option.bool("mining_area_walls.nucleus", "In the nucleus too", "", false)));
        Features.add(new Feature(MAP, "Mining", "Crystal Hollows map", "A small map of the Crystal Hollows with you on it and the places you know. It is a prepared picture, it does not read the world.", "Skyblocker: Crystal Hollows Map")
                .when(MiningPack::hollows)
                .option(Option.bool("mining_crystal_map.places", "Show places", "", true))
                .option(Option.integer("mining_crystal_map.place_size", "Place size", "", 8, 4, 16)));
        HudLayout.add(MAP_CARD);
    }
}
