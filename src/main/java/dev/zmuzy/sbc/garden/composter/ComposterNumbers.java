// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.composter;

// skyhanni ComposterApi by upgrade level: what the composter does at those levels
public final class ComposterNumbers {
    private ComposterNumbers() {}

    public static int maxOrganicMatter(int capLevel) {
        return 40_000 + capLevel * 30_000;
    }

    public static int maxFuel(int capLevel) {
        return 100_000 + capLevel * 30_000;
    }

    public static double multiDropChance(int level) {
        return level * 0.03;
    }

    public static long timePerCompost(int speedLevel) {
        return (long) (10 * 60_000L / (1 + speedLevel * 0.2));
    }

    public static double matterPer(int costReduction) {
        return 4_000.0 * (1.0 - costReduction / 100.0);
    }

    public static double fuelPer(int costReduction) {
        return 2_000.0 * (1.0 - costReduction / 100.0);
    }

    // getDurationUntilEndOfResource
    public static long untilEnd(long amount, double fraction, double requiredPer, long timePerCompost) {
        double remaining = amount - fraction * requiredPer;
        return (long) (timePerCompost * Math.floor(remaining / requiredPer));
    }
}
