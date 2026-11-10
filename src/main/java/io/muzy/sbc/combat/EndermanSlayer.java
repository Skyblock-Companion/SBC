// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.combat;

import io.muzy.sbc.dungeon.DungeonMarks;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// voidgloom seraph. from skyhanni EndermanSlayerFeatures + EndermanSlayerHideParticles, skyblocker
// BeaconHighlighter (thrown beacon path) + SlayerGlowAdder (nukekubi)
// we have no block change hook so a landed beacon is found by checking the blocks around it. laser timer
// lives in damage indicator
public final class EndermanSlayer {
    public static final String BEACONS = "void_beacons", NUKEKUBI = "void_nukekubi", PARTICLES = "void_hide_particles";
    private static final long BEACON_MS = 5000;

    private static final List<Enderman> carrying = new ArrayList<>();
    private static final Map<ArmorStand, List<Vec3>> flying = new HashMap<>();
    private static final Map<BlockPos, Long> sitting = new HashMap<>();
    private static final Set<ArmorStand> skulls = new HashSet<>();

    private EndermanSlayer() {}

    private static boolean hasBeacon(Enderman e) {
        var b = e.getCarriedBlock();
        return b != null && b.is(Blocks.BEACON);
    }

    private static boolean nearEnough(net.minecraft.world.entity.Entity e) {
        return Combat.seen(e, 15, 0.5);
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        boolean beacons = Features.on(BEACONS), nukekubi = Features.on(NUKEKUBI);
        if (!beacons && !nukekubi) return;
        long now = System.currentTimeMillis();
        AABB around = mc.player.getBoundingBox().inflate(32);
        if (mc.player.tickCount % 2 == 0) {
            if (beacons) {
                for (Enderman e : mc.level.getEntitiesOfClass(Enderman.class, around, EndermanSlayer::hasBeacon))
                    if (!carrying.contains(e) && nearEnough(e)) carrying.add(e);
                carrying.removeIf(e -> !e.isAlive() || !hasBeacon(e));
            }
            for (ArmorStand a : mc.level.getEntitiesOfClass(ArmorStand.class, around)) {
                var head = a.getItemBySlot(EquipmentSlot.HEAD);
                if (head.isEmpty()) continue;
                if (beacons && head.getHoverName().getString().equals("Beacon") && !flying.containsKey(a) && nearEnough(a)) {
                    flying.put(a, new ArrayList<>(List.of(a.getEyePosition())));
                    if (Combat.opt(BEACONS, "warning")) {
                        Combat.title("§4Beacon", 40);
                        Combat.play("block.note_block.pling", 1f, 0.1f);
                    }
                }
                if (nukekubi && !skulls.contains(a) && DungeonMarks.headTexture(head).equals(DungeonMarks.skull("MOB_NUKEKUBI"))) skulls.add(a);
            }
            skulls.removeIf(a -> !a.isAlive());
            landed(mc, now);
        }
        sitting.entrySet().removeIf(e -> now - e.getValue() > 7000 || !mc.level.getBlockState(e.getKey()).is(Blocks.BEACON));
    }

    // skyhannis ServerBlockChangeEvent use: a beacon within 3 blocks of a flying one = it landed
    private static void landed(Minecraft mc, long now) {
        Iterator<Map.Entry<ArmorStand, List<Vec3>>> it = flying.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            ArmorStand a = e.getKey();
            List<Vec3> path = e.getValue();
            if (a.isAlive() && !path.getLast().equals(a.getEyePosition())) path.add(a.getEyePosition());
            Vec3 last = path.getLast();
            BlockPos c = BlockPos.containing(last);
            BlockPos found = null;
            for (BlockPos p : BlockPos.betweenClosed(c.offset(-3, -3, -3), c.offset(3, 3, 3))) {
                if (mc.level.getBlockState(p).is(Blocks.BEACON) && !sitting.containsKey(p)) {
                    found = p.immutable();
                    break;
                }
            }
            if (found != null) {
                sitting.put(found, now);
                it.remove();
            } else if (!a.isAlive()) it.remove();
        }
    }

    private static void draw(WorldDraw w) {
        if (Features.on(BEACONS)) drawBeacons(w);
        if (Features.on(NUKEKUBI)) {
            for (ArmorStand s : skulls) {
                if (!s.isAlive() || !Combat.seen(s, 20, 1.5)) continue;
                w.label("§6Nukekubi Skull", s.position().add(0, 1.5, 0), false);
                if (Combat.opt(NUKEKUBI, "line")) w.line(Combat.fromCrosshair(w), s.position().add(0, 1, 0), 0xFFFFAA00, 3, true);
            }
        }
    }

    private static void drawBeacons(WorldDraw w) {
        boolean highlight = Combat.opt(BEACONS, "highlight"), line = Combat.opt(BEACONS, "line");
        int color = Combat.color(BEACONS, "color", 0xFFFF0058), lineColor = Combat.color(BEACONS, "line_color", 0xFFFF0058);
        float width = Combat.integer(BEACONS, "line_width", 3);
        if (highlight) for (Enderman e : carrying) w.filledBox(new AABB(BlockPos.containing(e.position().add(-0.5, 0.2, -0.5))), color & 0xFFFFFF, 0.5f, true);
        long now = System.currentTimeMillis();
        for (var e : sitting.entrySet()) {
            Vec3 at = Vec3.atLowerCornerOf(e.getKey());
            if (at.distanceTo(w.eye) > 20) continue;
            if (line) w.line(Combat.fromCrosshair(w), at.add(0.5, 1, 0.5), lineColor, width, true);
            if (highlight) {
                long left = BEACON_MS - (now - e.getValue());
                w.waypointBox(at, color, true, true, 0);
                w.label("§4Beacon §b" + String.format(Locale.ROOT, "%.1fs", Math.max(0, left) / 1000.0), at.add(0.5, 1, 0.5), true);
            }
        }
        for (var e : flying.entrySet()) {
            ArmorStand a = e.getKey();
            if (!a.isAlive()) continue;
            if (highlight) {
                w.label("§4Beacon", a.position().add(0, 1, 0), true);
                List<Vec3> path = e.getValue();
                for (int i = 1; i < path.size(); i++) w.line(path.get(i - 1), path.get(i), 0xFFFF0000, 3, true);
            }
            if (line) w.line(Combat.fromCrosshair(w), a.position().add(0.5, 1, 0.5), lineColor, width, true);
        }
    }

    // nav/Slayers leaves its own beacon marks to this when true
    public static boolean ownsBeacons() {
        return Features.on(BEACONS);
    }

    private static void left() {
        carrying.clear();
        flying.clear();
        sitting.clear();
        skulls.clear();
    }

    static void register() {
        Features.add(new Feature("void_tick", "Slayer", "Voidgloom background", "Keeps the Voidgloom helpers running.", "").alwaysOn().when(() -> Combat.island("THE_END")).tick(EndermanSlayer::tick).world(EndermanSlayer::draw).left(EndermanSlayer::left));
        Features.add(new Feature(BEACONS, "Slayer", "Yang Glyphs", "Marks a Voidgloom's beacon in its hands, in the air with its path, and where it landed with the seconds left.", "SkyHanni: Yang Glyph · Skyblocker: Beacon Highlighter").when(() -> Combat.island("THE_END"))
                .option(Option.bool("void_beacons.highlight", "Highlight", "", true))
                .option(Option.color("void_beacons.color", "Colour", "", 0xFFFF0058))
                .option(Option.bool("void_beacons.warning", "Warning", "A title and a sound when one is thrown.", true))
                .option(Option.bool("void_beacons.line", "Line to it", "From your crosshair.", false))
                .option(Option.color("void_beacons.line_color", "Line colour", "", 0xFFFF0058))
                .option(Option.integer("void_beacons.line_width", "Line width", "", 3, 1, 10)));
        Features.add(new Feature(NUKEKUBI, "Slayer", "Nukekubi skulls", "A glow and a name on the skulls a Voidgloom throws, where you can see them.", "SkyHanni: Highlight Nukekubi Skulls · Skyblocker: Highlight Nukekubi Heads").when(() -> Combat.island("THE_END"))
                .option(Option.bool("void_nukekubi.line", "Line to them", "", false)));
        CombatMarks.glowing(mark -> {
            if (Features.on(NUKEKUBI)) for (ArmorStand s : skulls) if (s.isAlive()) CombatMarks.seenGlow(mark, s, 0xFFAA00);
        });
        Features.add(new Feature(PARTICLES, "Slayer", "Hide Enderman particles", "Smoke, flames and witch sparks around Endermen in The End.", "SkyHanni: Hide Particles").when(() -> Combat.island("THE_END")).defaultOff());
        CombatMarks.particles(() -> Features.on(PARTICLES), (type, x, y, z) -> {
            if (!type.equals("large_smoke") && !type.equals("flame") && !type.equals("witch")) return false;
            var level = Minecraft.getInstance().level;
            return level != null && !level.getEntitiesOfClass(Enderman.class, new AABB(x - 3, y - 3, z - 3, x + 3, y + 3, z + 3)).isEmpty();
        });
    }
}
