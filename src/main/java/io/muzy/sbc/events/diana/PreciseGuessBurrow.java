// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.diana;

import net.minecraft.world.phys.Vec3;

// from skyhanni PreciseGuessBurrow: the ancestral spade shoots DRIPPING_LAVA particles (count 2, speed -0.5)
// along a curve
// fit a cubic through them, follow it from the start = next burrow (an inaccurate "spade guess" until the
// particle finder or chain arrow confirms)
public final class PreciseGuessBurrow {
    private static final BezierFitter bezierFitter = new BezierFitter(3);
    private static GuessEntry lastGuess;
    private static long lastDianaSpade = DianaEnv.FAR_PAST;
    private static long lastLavaParticle = DianaEnv.FAR_PAST;

    private PreciseGuessBurrow() {}

    public static int getBezierFitterCount() {
        return bezierFitter.count();
    }

    public static void onIslandChange() {
        bezierFitter.reset();
    }

    // PreciseGuessBurrow.onParticle (hub)
    public static void onParticle(String type, double x, double y, double z, int count, float speed) {
        if (!isEnabled()) return;
        if (!type.equals("minecraft:dripping_lava")) return;
        if (count != 2) return;
        if (speed != -0.5f) return;
        lastLavaParticle = DianaEnv.now();
        if (DianaEnv.passedSince(lastDianaSpade) > 3000) return;
        GriffinBurrowHelper.removeSpadeWarnTitle();

        if (!bezierFitter.tryAdd(new Vec3(x, y, z), 3.0)) return;

        if (bezierFitter.count() < 6) {
            long duration = (6 - bezierFitter.count()) * 100L;
            BurrowWarpHelper.blockWarp(duration);
        }

        Vec3 guessPosition = bezierFitter.solve();
        if (guessPosition == null) return;
        // guessPosition.down(0.5).roundToBlock()
        Vec3 block = new Vec3(Math.floor(guessPosition.x), Math.floor(guessPosition.y - 0.5), Math.floor(guessPosition.z));
        GuessEntry guessEntry = GuessEntry.spade(block);

        if (lastGuess == null || !lastGuess.getCurrent().equals(guessEntry.getCurrent())) {
            if (lastGuess != null) GriffinBurrowHelper.removeGuess(lastGuess, "moving spade guess", false);
            GriffinBurrowHelper.onBurrowGuess(guessEntry, "spade guess");
            lastGuess = guessEntry;
        }
    }

    // PreciseGuessBurrow.onItemClick (hub). true = cancel the click (2nd spade use right after the particles)
    public static boolean onItemClick(boolean rightClick) {
        if (!isEnabled()) return false;
        if (!DianaApi.hasSpadeInHand()) return false;
        if (!rightClick) {
            GriffinBurrowHelper.removeInaccurateIfLooking();
            return false;
        }
        if (DianaEnv.passedSince(lastLavaParticle) < 200) return true;
        bezierFitter.reset();
        lastGuess = null;
        lastDianaSpade = DianaEnv.now();
        return false;
    }

    public static Vec3 debugGuess() {
        return bezierFitter.solve();
    }

    public static void reset() {
        bezierFitter.reset();
        lastGuess = null;
        lastDianaSpade = lastLavaParticle = DianaEnv.FAR_PAST;
    }

    private static boolean isEnabled() {
        return DianaApi.isDoingDiana() && Diana.guess();
    }
}
