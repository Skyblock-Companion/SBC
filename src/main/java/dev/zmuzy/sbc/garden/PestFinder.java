// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.garden;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

// pest finder. infested plots (from tab) get skyhanni style wall lines + the pest count in the middle
// every pest gets a red box. where the vacuum points is PestGuess. display only
public final class PestFinder {
    private static long lastHeld;

    private PestFinder() {}

    private static boolean garden() {
        return "GARDEN".equals(GameState.island) || dev.zmuzy.sbc.SbcMod.DEV;
    }

    // the pests by name, as the damage indicator knows them
    private static final java.util.Set<String> PEST_NAMES = new java.util.HashSet<>();

    static boolean pest(dev.zmuzy.sbc.combat.Mobs.Mob m) {
        if (PEST_NAMES.isEmpty())
            for (var t : dev.zmuzy.sbc.combat.BossType.values()) if (t.group == dev.zmuzy.sbc.combat.BossType.Group.PESTS) PEST_NAMES.add(GameState.strip(t.fullName));
        return m.base != null && m.base.isAlive() && PEST_NAMES.contains(m.name);
    }

    // bright red box on every pest in sight, no text
    public static void boxes(WorldDraw w) {
        if (!garden() || !Features.on("garden_pest_box")) return;
        float partial = w.mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (var m : dev.zmuzy.sbc.combat.Mobs.all()) {
            if (!pest(m)) continue;
            Vec3 at = m.base.getPosition(partial);
            var bb = m.base.getBoundingBox();
            // at least a block big, a mite is tiny
            double half = Math.max(0.5, Math.max(bb.getXsize(), bb.getZsize()) / 2 + 0.15), h = Math.max(1.0, bb.getYsize() + 0.3);
            net.minecraft.world.phys.AABB box = new net.minecraft.world.phys.AABB(at.x - half, at.y - 0.15, at.z - half, at.x + half, at.y - 0.15 + h, at.z + half);
            w.filledBox(box, 0xFF2020, 0.55f, true);
            w.box(box, 0xFFFF3030, false, false);
        }
    }

    // plot frames with pest counts
    public static void draw(WorldDraw w) {
        if (!garden()) return;
        int alive = 0;
        for (String l : GameState.tab) {
            String t = l.trim();
            if (t.startsWith("Alive:")) {
                try {
                    alive = Integer.parseInt(t.substring(6).trim());
                } catch (NumberFormatException ignored) {
                }
            }
        }
        List<Integer> plots = GardenExtras.infestedPlots();
        long now = System.currentTimeMillis();
        boolean holding = holding(w);
        if (holding) lastHeld = now;
        // PestFinder.timePassedDisabled: they stay a moment after you put the item away
        if (!always() && !holding && now - lastHeld > dev.zmuzy.sbc.garden.GardenPack.number("garden_pest_finder", "linger", 1) * 1000L) plots = List.of();
        // PestFinder.onRenderWorld's visibilityType
        String visibility = dev.zmuzy.sbc.garden.GardenPack.choice("garden_pest_finder", "visibility", "Both");
        boolean border = !visibility.equals("Name"), name = !visibility.equals("Border");
        for (int p : plots) {
            Vec3 c = GardenExtras.plotCenter(p);
            if (c == null) continue;
            boolean inside = w.mc.player != null && Math.abs(w.mc.player.getX() - c.x) <= 48 && Math.abs(w.mc.player.getZ() - c.z) <= 48;
            // from skyhanni PestFinder.onRenderWorld: red + dark red corners for the plot youre in, gold + red for the rest
            if (border) plotBorder(w, c, inside ? 0xFFFF5555 : 0xFFFFAA00, inside ? 0xFFAA0000 : 0xFFFF5555);
            // the name only for plots youre not in, you already know where you are
            if (!name || inside) continue;
            int known = dev.zmuzy.sbc.garden.plot.PlotPests.known(p);
            if (known <= 0 && plots.size() == 1 && alive > 0) known = alive;
            // from skyhanni PestFinder.drawName: a block at the plots middle at your eye height, the text on it
            Vec3 at = new Vec3(c.x, w.eye.y, c.z);
            w.waypointBox(at, 0xFFFF5555, false, false, 0);
            w.label("§e" + (known > 0 ? known : "?") + " §c" + (known == 1 ? "pest" : "pests") + " §7in §bPlot " + p, at, true);
        }
    }

    // from skyhanni GardenPlotApi.renderPlot, lines every 4 blocks on all 4 walls from y 66 to 102, thicker corners
    // drawn with depth so a wall in between hides them
    static void plotBorder(WorldDraw w, Vec3 center, int line, int corner) {
        double minX = center.x - 48, minZ = center.z - 48, lo = 66, hi = 66 + 36;
        for (int i = 0; i <= 96; i += 96)
            for (int j = 0; j <= 96; j += 96) w.line(new Vec3(minX + i, lo, minZ + j), new Vec3(minX + i, hi, minZ + j), corner, 3f, false);
        for (int k = 4; k < 96; k += 4) {
            w.line(new Vec3(minX + k, lo, minZ), new Vec3(minX + k, hi, minZ), line, 2f, false);
            w.line(new Vec3(minX + k, lo, minZ + 96), new Vec3(minX + k, hi, minZ + 96), line, 2f, false);
            w.line(new Vec3(minX, lo, minZ + k), new Vec3(minX, hi, minZ + k), line, 2f, false);
            w.line(new Vec3(minX + 96, lo, minZ + k), new Vec3(minX + 96, hi, minZ + k), line, 2f, false);
        }
        for (double y = lo; y <= hi; y += 4) {
            w.line(new Vec3(minX, y, minZ), new Vec3(minX, y, minZ + 96), line, 2f, false);
            w.line(new Vec3(minX, y, minZ + 96), new Vec3(minX + 96, y, minZ + 96), line, 2f, false);
            w.line(new Vec3(minX + 96, y, minZ + 96), new Vec3(minX + 96, y, minZ), line, 2f, false);
            w.line(new Vec3(minX + 96, y, minZ), new Vec3(minX, y, minZ), line, 2f, false);
        }
    }

    private static boolean always() {
        var f = Features.get("garden_pest_finder");
        var o = f == null ? null : f.option("always");
        return o != null && o.bool();
    }

    private static boolean holdingVacuum(WorldDraw w) {
        if (w.mc.player == null) return false;
        var st = w.mc.player.getMainHandItem();
        return !st.isEmpty() && (dev.zmuzy.sbc.core.Gain.sbId(st).contains("VACUUM") || st.getHoverName().getString().contains("Vacuum"));
    }

    // PestFinder.shouldShowBasedOnHeldItem
    private static boolean holding(WorldDraw w) {
        return switch (dev.zmuzy.sbc.garden.GardenPack.choice("garden_pest_finder", "when", "Vacuum or lasso")) {
            case "Vacuum" -> holdingVacuum(w);
            case "Lasso" -> dev.zmuzy.sbc.garden.GardenPack.lassoInHand();
            default -> holdingVacuum(w) || dev.zmuzy.sbc.garden.GardenPack.lassoInHand();
        };
    }

    public static void register() {
        Features.add(new Feature("garden_pest_box", "Garden", "Pest boxes", "A bright red box on every pest, with nothing written on it, so you spot them at once.", "")
                .when(PestFinder::garden).world(PestFinder::boxes));
        Features.add(new Feature("garden_pest_finder", "Garden", "Pest finder", "Infested plots get lines on their walls in the world with the pest count while you hold a vacuum. Where the vacuum's ability points is the Pest guess.", "")
                .when(PestFinder::garden).world(PestFinder::draw)
                .option(dev.zmuzy.sbc.feature.Option.bool("garden_pest_finder.always", "Show the plot walls always", "Off: only while a vacuum is in your hand.", false))
                .option(dev.zmuzy.sbc.feature.Option.choice("garden_pest_finder.when", "Show while holding", "When the walls are drawn, if not always.", "Vacuum or lasso", "Vacuum or lasso", "Vacuum", "Lasso"))
                .option(dev.zmuzy.sbc.feature.Option.choice("garden_pest_finder.visibility", "Plot shows", "The walls of an infested plot, its name and count, or both.", "Both", "Both", "Border", "Name"))
                .option(dev.zmuzy.sbc.feature.Option.bool("garden_pest_finder.ignore_single", "Teleport past single pests", "The teleport key skips a plot known to have exactly one pest. Useful when you leave one alive for Infiltration.", false))
                .option(dev.zmuzy.sbc.feature.Option.integer("garden_pest_finder.linger", "Keep them for (s)", "After you put the item away.", 1, 0, 60))
                .option(dev.zmuzy.sbc.feature.Option.bool("garden_pest_finder.always_tp", "Teleport from an infested plot too", "The teleport key also works when you already stand on one.", false))
                .option(dev.zmuzy.sbc.feature.Option.bool("garden_pest_finder.back_to_garden", "Back to the Garden without pests", "The teleport key warps you to the Garden when there are none.", false)));
    }
}
