// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.pests;

import dev.zmuzy.sbc.events.diana.BezierFitter;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.garden.GardenPack;
import dev.zmuzy.sbc.mc.Interactions;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.world.phys.Vec3;

// from skyhanni PestParticleWaypoint: fit a curve to the particle path of a vacuum left click (same fitter as
// diana's spade), its end is the guess
// PestFinder's line follows the particles as far as they go, this says where theyre heading way before they
// get there. only reads what hypixel sends after you used the vacuum
public final class PestGuess {
    public static final String ID = "garden_pest_waypoint";
    private static final BezierFitter FITTER = new BezierFitter(3);
    private static long lastUse;
    private static Vec3 guess;
    private static boolean guessIsMiddle;

    private PestGuess() {}

    // PestParticleWaypoint.isGuessPlotMiddle: particles point at the plot middle when we have no better spot
    static boolean plotMiddle(Vec3 solved) {
        Vec3 middle = dev.zmuzy.sbc.garden.GardenExtras.plotCenter(dev.zmuzy.sbc.garden.plot.GardenPlotInfo.plotAt(solved.x, solved.z));
        return middle != null && Math.ceil(solved.x) == Math.floor(middle.x) && Math.ceil(solved.z) == Math.floor(middle.z);
    }

    private static void reset() {
        lastUse = 0;
        guess = null;
        guessIsMiddle = false;
        FITTER.reset();
    }

    private static boolean onItemClick(boolean right) {
        var mc = Minecraft.getInstance();
        if (right || !Features.on(ID) || !GardenPack.garden() || !GardenPack.vacuumInHand() || mc.player == null || mc.player.isShiftKeyDown()) return false;
        reset();
        lastUse = System.currentTimeMillis();
        return false;
    }

    // onParticle for each packet on the game thread, true = the option hides it
    public static boolean hides(ClientboundLevelParticlesPacket p) {
        try {
            var mc = Minecraft.getInstance();
            if (mc == null || !mc.isSameThread() || mc.player == null || !Features.on(ID) || !GardenPack.garden()) return false;
            boolean hide = GardenPack.on(ID, "hide_particles");
            var type = p.particle().getType();
            boolean firework = type == ParticleTypes.FIREWORK;
            if (System.currentTimeMillis() - lastUse > 5000) return hide && firework;
            boolean noOffset = p.xDist() == 0f && p.yDist() == 0f && p.zDist() == 0f;
            if (type == ParticleTypes.ENCHANT && p.count() == 10 && p.xMaxSpeed() == -2f && noOffset) return hide;
            if (!(type == ParticleTypes.ANGRY_VILLAGER && p.count() == 1 && p.xMaxSpeed() == 0f && noOffset)) return hide && firework;
            Vec3 at = new Vec3(p.x(), p.y(), p.z());
            // emptyCondition: a path starts close to you, one further away is someone elses
            if (FITTER.isEmpty() && at.distanceTo(mc.player.position()) > 5) return hide;
            if (FITTER.tryAdd(at, 3.0)) {
                Vec3 solved = FITTER.solve();
                if (solved != null) {
                    guess = solved;
                    guessIsMiddle = plotMiddle(solved);
                }
            }
            return hide;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void tick(Minecraft mc) {
        if (guess == null || mc.player == null) return;
        long since = System.currentTimeMillis() - lastUse;
        double dx = guess.x - mc.player.getX(), dz = guess.z - mc.player.getZ();
        // onTick: you got there
        if (dx * dx + dz * dz <= 64 && since >= 1000 && since <= GardenPack.number(ID, "seconds", 15) * 1000L) reset();
    }

    private static void draw(WorldDraw w) {
        if (FITTER.isEmpty()) return;
        if (System.currentTimeMillis() - lastUse > GardenPack.number(ID, "seconds", 15) * 1000L) {
            reset();
            return;
        }
        if (guess == null) return;
        // same as skyhanni: the block + its beam (behind blocks unless the option is on), "Pest Guess" on top at the guess
        // "(plot middle)" a line under it, the line from the crosshair always on top
        boolean walls = GardenPack.on(ID, "through_walls");
        boolean middle = guessIsMiddle && GardenPack.on(ID, "middle");
        int colour = middle ? 0xFFFFFF55 : 0xFFFF5555;
        w.waypointBox(guess, colour, true, walls, 0);
        if (middle) w.dynamicText(guess, " §r§e(plot middle)", 1.0, -1);
        w.dynamicText(guess, "§aPest Guess", 1.3, 0);
        if (GardenPack.on(ID, "line") && w.mc.player != null) w.line(w.crosshair(), guess.add(0.5, 0.5, 0.5), colour, 3f, true);
    }

    public static void register() {
        Feature f = new Feature(ID, "Garden", "Pest guess", "When you use a vacuum, a waypoint at where its particles are heading, worked out from their path.", "SkyHanni: Pest Waypoint")
                .when(GardenPack::garden).tick(PestGuess::tick).world(PestGuess::draw).left(PestGuess::reset)
                .option(Option.bool("garden_pest_waypoint.line", "Draw a line to it", "", true))
                .option(Option.bool("garden_pest_waypoint.through_walls", "Box through walls", "The box shows behind blocks too. Its name and line always do.", false))
                .option(Option.bool("garden_pest_waypoint.middle", "Tell the plot's middle apart", "Yellow and marked when the guess is only the middle of the plot, not a pest.", true))
                .option(Option.bool("garden_pest_waypoint.hide_particles", "Hide the particles", "The ones of the vacuum's ability.", true))
                .option(Option.integer("garden_pest_waypoint.seconds", "Show for (s)", "", 15, 1, 60));
        Features.add(f);
        Interactions.onItemClick(PestGuess::onItemClick);
    }
}
