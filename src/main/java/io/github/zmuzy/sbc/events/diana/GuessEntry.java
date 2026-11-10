// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events.diana;

import net.minecraft.world.phys.Vec3;

import java.util.List;

// from skyhanni GuessEntry: one guessed burrow + its other blocks + type
public final class GuessEntry {
    public final List<Vec3> guesses;
    public BurrowType burrowType;
    public int currentIndex;
    public final boolean spadeGuess;
    public long ignoreParticleCheckUntil;
    public boolean ignoreInvalidBlock;

    public GuessEntry(List<Vec3> guesses) {
        this(guesses, BurrowType.UNKNOWN, false, DianaEnv.now(), false);
    }

    public GuessEntry(List<Vec3> guesses, BurrowType type, boolean ignoreInvalidBlock) {
        this(guesses, type, false, DianaEnv.now(), ignoreInvalidBlock);
    }

    public GuessEntry(List<Vec3> guesses, BurrowType type, boolean spadeGuess, long ignoreParticleCheckUntil, boolean ignoreInvalidBlock) {
        this.guesses = List.copyOf(guesses);
        this.burrowType = type;
        this.spadeGuess = spadeGuess;
        this.ignoreParticleCheckUntil = ignoreParticleCheckUntil;
        this.ignoreInvalidBlock = ignoreInvalidBlock;
    }

    public static GuessEntry spade(Vec3 v) {
        return new GuessEntry(List.of(v), BurrowType.UNKNOWN, true, DianaEnv.now(), false);
    }

    public Vec3 getCurrent() {
        return guesses.get(currentIndex);
    }

    public boolean contains(Vec3 v) {
        return guesses.contains(v);
    }

    // GuessEntry.checkRemove: true = drop it (expired, or couldnt move to a valid block)
    public boolean checkRemove(StringBuilder reason) {
        // drop guesses older than 30 min
        Long timer = GriffinBurrowHelper.getTimer(this);
        if (timer != null && DianaEnv.passedSince(timer) > 30 * 60_000L) {
            reason.append("expired (30min active) ");
            return true;
        }

        if (shouldKeepGuess()) return false;

        boolean shouldMove = false;
        if (!GriffinBurrowHelper.isBlockValid(getCurrent()) && !spadeGuess && !ignoreInvalidBlock) {
            reason.append("invalid block ");
            shouldMove = true;
        }

        if (GriffinBurrowHelper.shouldBurrowParticlesBeVisible(1000)
                && !GriffinBurrowParticleFinder.containsBurrow(getCurrent()) // burrow not found
                && getCurrent().distanceToSqr(DianaEnv.world.playerPos()) < 900) { // within 30 blocks
            reason.append("particles not found when they should have been ");
            shouldMove = true;
        }

        if (shouldMove && !attemptMove()) return true;

        return false;
    }

    public boolean attemptMove() {
        int nextIndex = currentIndex + 1;
        if (nextIndex >= 0 && nextIndex < guesses.size()) {
            currentIndex = nextIndex;
            GriffinBurrowHelper.update();
            GriffinBurrowHelper.onBurrowGuess(this, "moving");
            return true;
        }
        return false;
    }

    private boolean shouldKeepGuess() {
        // burrows known from the previous dig even if particles dont update, or inaccurate precise guesses
        if (DianaEnv.passedSince(ignoreParticleCheckUntil) < 0) return true;
        // dont move mob burrows while a mob is alive
        return GriffinBurrowHelper.mobAlive && burrowType == BurrowType.MOB;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        Vec3 c = getCurrent();
        String typeStr = burrowType == BurrowType.UNKNOWN ? "Guess" : burrowType.name();
        sb.append(typeStr).append('[').append(c.x).append(", ").append(c.y).append(", ").append(c.z).append(']');
        if (guesses.size() > 1) {
            sb.append(", additional: ");
            boolean first = true;
            for (int i = 0; i < guesses.size(); i++) {
                if (i == currentIndex) continue;
                Vec3 v = guesses.get(i);
                if (!first) sb.append(", ");
                first = false;
                sb.append(typeStr).append('[').append(v.x).append(", ").append(v.y).append(", ").append(v.z).append(']');
            }
        }
        if (currentIndex != 0) sb.append(", index=").append(currentIndex);
        if (spadeGuess) sb.append(", inaccurate=true");
        if (ignoreInvalidBlock) sb.append(", ignoreBlock=true");
        return sb.toString();
    }
}
