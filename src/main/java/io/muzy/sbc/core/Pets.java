// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.core;

// pet leveling: shared xp curve offset by rarity (golden dragon type pets go to 200)
public final class Pets {
    private static final int[] LEVELS = {100, 110, 120, 130, 145, 160, 175, 190, 210, 230, 250, 275, 300, 330, 360, 400, 440, 490, 540, 600,
            660, 730, 800, 880, 960, 1050, 1150, 1260, 1380, 1510, 1650, 1800, 1960, 2130, 2310, 2500, 2700, 2920, 3160, 3420, 3700, 4000, 4350,
            4750, 5200, 5700, 6300, 7000, 7800, 8700, 9700, 10800, 12000, 13300, 14700, 16200, 17800, 19500, 21300, 23200, 25200, 27400, 29800,
            32400, 35200, 38200, 41400, 44800, 48400, 52200, 56200, 60400, 64800, 69400, 74200, 79200, 84700, 90700, 97200, 104200, 111700,
            119700, 128200, 137200, 146700, 156700, 167700, 179700, 192700, 206700, 221700, 237700, 254700, 272700, 291700, 311700, 333700,
            357700, 383700, 411700, 441700, 476700, 516700, 561700, 611700, 666700, 726700, 791700, 861700, 936700, 1016700, 1101700,
            1191700, 1286700, 1386700, 1496700, 1616700, 1746700, 1886700};
    private static final int LEVEL_200_STEP = 1886700;

    private Pets() {}

    private static int offset(String tier) {
        return switch (tier) { case "UNCOMMON" -> 6; case "RARE" -> 11; case "EPIC" -> 16; case "LEGENDARY", "MYTHIC" -> 20; default -> 0; };
    }

    public static int maxLevel(String type) {
        return type != null && (type.equals("GOLDEN_DRAGON") || type.equals("JADE_DRAGON") || type.equals("ROSE_DRAGON")) ? 200 : 100;
    }

    private static int xpFor(String tier, int index) {
        int i = offset(tier) + index;
        return i < LEVELS.length ? LEVELS[i] : LEVEL_200_STEP;
    }

    public static double xpForMax(String tier, String type) {
        double s = 0;
        for (int i = 0; i < maxLevel(type) - 1; i++) s += xpFor(tier, i);
        return s;
    }

    // xp at the start of a level
    public static double xpAtLevel(String tier, int level) {
        double s = 0;
        for (int i = 0; i < level - 1 && i < 99; i++) s += xpFor(tier, i);
        return s;
    }

    public static int level(double exp, String tier, String type) {
        int max = maxLevel(type);
        int lvl = 1;
        double left = exp;
        for (int i = 0; i < max - 1; i++) {
            int need = xpFor(tier, i);
            if (left < need) break;
            left -= need;
            lvl++;
        }
        return Math.min(lvl, max);
    }
}
