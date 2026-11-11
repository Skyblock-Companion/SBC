// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mining;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// from skyhanni fossilexcavator/solver (FossilSolver, FossilType, FossilShape, FossilMutation, FossilTile)
// which excavator tile is most likely part of the fossil, from what the board shows
// skyblockers fossil classes do the same with the same fossils so this one covers both. board is 9x6, slot = x + 9y
public final class FossilSolver {
    public enum Type {
        TUSK("Tusk", "12.5%", "0,2 0,3 0,4 1,1 2,0 3,1 3,3 4,2", ALL()),
        WEBBED("Webbed", "10%", "0,2 1,1 2,0 3,0 3,1 3,2 3,3 4,0 5,1 6,2", new int[]{0, 4}),
        CLUB("Club", "9.1%", "0,2 0,3 1,2 1,3 2,1 3,0 4,0 5,0 6,0 6,2 7,1", new int[]{0, 2, 4, 6}),
        SPINE("Spine", "8.3%", "0,2 1,1 1,2 2,0 2,1 2,2 3,0 3,1 3,2 4,1 4,2 5,2", ROTATIONS()),
        CLAW("Claw", "7.7%", "0,3 1,2 1,4 2,1 2,3 3,1 3,2 3,4 4,0 4,1 4,2 4,3 5,1", ALL()),
        FOOTPRINT("Footprint", "7.7%", "0,2 1,1 1,2 1,3 2,1 2,2 2,3 3,0 3,2 3,4 4,0 4,2 4,4", ROTATIONS()),
        HELIX("Helix", "7.1%", "0,0 0,1 0,2 0,4 1,0 1,2 1,4 2,0 2,4 3,0 3,1 3,2 3,3 3,4", ALL()),
        UGLY("Ugly", "6.2%", "0,1 1,0 1,1 1,2 2,0 2,1 2,2 2,3 3,0 3,1 3,2 3,3 4,0 4,1 4,2 5,1", ROTATIONS());

        public final String displayName, firstPercentage;
        final int[][] tiles;
        // skyhanni FossilMutation: 0..3 = 90 deg rotations, 4..7 = same but flipped
        final int[] mutations;

        Type(String displayName, String firstPercentage, String tiles, int[] mutations) {
            this.displayName = displayName;
            this.firstPercentage = firstPercentage;
            String[] p = tiles.split(" ");
            this.tiles = new int[p.length][];
            for (int i = 0; i < p.length; i++) {
                String[] xy = p[i].split(",");
                this.tiles[i] = new int[]{Integer.parseInt(xy[0]), Integer.parseInt(xy[1])};
            }
            this.mutations = mutations;
        }
    }

    private static int[] ALL() {
        return new int[]{0, 1, 2, 3, 4, 5, 6, 7};
    }

    private static int[] ROTATIONS() {
        return new int[]{0, 1, 2, 3};
    }

    // slot -1 = nothing to click. percent = share of the remaining fossils that use it
    public record Move(int slot, double percent, int remaining, boolean impossible, boolean completed, List<Type> possible) {}

    // {x, y, chance, how many remain} for when nothing is found yet
    private static final double[][] RISKY = {{4, 2, 0.515, 404}, {5, 3, 0.393, 196}, {3, 2, 0.513, 119}, {7, 2, 0.345, 58}, {1, 3, 0.342, 38}, {3, 4, 0.6, 25}, {5, 1, 0.8, 10}, {4, 3, 1.0, 2}};
    private static final double[][] SAFE = {{4, 2, 0.515, 404}, {5, 4, 0.413, 196}, {3, 3, 0.461, 115}, {5, 2, 0.387, 62}, {3, 1, 0.342, 38}, {7, 3, 0.48, 25}, {1, 2, 0.846, 13}, {3, 4, 1.0, 2}};

    private FossilSolver() {}

    // skyhanni FossilShape.rotate + flipShape
    static int[][] mutate(int[][] tiles, int mutation) {
        int width = 0, height = 0, minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        for (int[] t : tiles) {
            width = Math.max(width, t[0]);
            height = Math.max(height, t[1]);
            minX = Math.min(minX, t[0]);
            minY = Math.min(minY, t[1]);
        }
        width -= minX;
        height -= minY;
        int[][] out = new int[tiles.length][2];
        int degree = mutation % 4;
        for (int i = 0; i < tiles.length; i++) {
            int x = tiles[i][0], y = tiles[i][1];
            switch (degree) {
                case 1 -> out[i] = new int[]{y, width - x};
                case 2 -> out[i] = new int[]{width - x, height - y};
                case 3 -> out[i] = new int[]{height - y, x};
                default -> out[i] = new int[]{x, y};
            }
        }
        if (mutation >= 4) {
            int h = 0, min = Integer.MAX_VALUE;
            for (int[] t : out) {
                h = Math.max(h, t[1]);
                min = Math.min(min, t[1]);
            }
            h -= min;
            for (int[] t : out) t[1] = h - t[1];
        }
        return out;
    }

    private static boolean valid(int[][] tiles, int dx, int dy, Set<Integer> invalid, Set<Integer> found) {
        Set<Integer> on = new HashSet<>();
        for (int[] t : tiles) {
            int x = t[0] + dx, y = t[1] + dy;
            if (x < 0 || y < 0 || x >= 9 || y >= 6) return false;
            int slot = x + y * 9;
            if (invalid.contains(slot)) return false;
            on.add(slot);
        }
        return on.containsAll(found);
    }

    // skyhanni FossilSolver.findBestTile
    // fossil/dirt = slots showing "Fossil"/"Dirt", percentage = "Fossil Excavation Progress" of a found tile or
    // null, maxCharges = chisel charges when the board opened
    public static Move solve(Set<Integer> fossil, Set<Integer> dirt, String percentage, int maxCharges) {
        Set<Integer> invalid = new HashSet<>();
        for (int i = 0; i <= 53; i++) if (!fossil.contains(i) && !dirt.contains(i)) invalid.add(i);
        double[][] sequence = maxCharges < 18 ? RISKY : SAFE;
        if (fossil.isEmpty()) {
            boolean onlySequence = true;
            for (int slot : invalid) {
                boolean in = false;
                for (double[] s : sequence) if ((int) s[0] + (int) s[1] * 9 == slot) in = true;
                if (!in) onlySequence = false;
            }
            if (onlySequence) {
                int taken = invalid.size();
                if (taken >= sequence.length) return new Move(-1, 0, 0, true, false, List.of());
                double[] next = sequence[taken];
                return new Move((int) next[0] + (int) next[1] * 9, next[2], (int) next[3], false, false, List.of());
            }
        }
        List<Type> possible = new ArrayList<>();
        for (Type t : Type.values()) if (percentage == null || t.firstPercentage.equals(percentage)) possible.add(t);
        Map<Integer, Integer> counts = new HashMap<>();
        int total = 0;
        for (Type t : possible) {
            for (int mutation : t.mutations) {
                int[][] tiles = mutate(t.tiles, mutation);
                for (int x = 0; x <= 8; x++) {
                    for (int y = 0; y <= 5; y++) {
                        if (!valid(tiles, x, y, invalid, fossil)) continue;
                        total++;
                        for (int[] tile : tiles) counts.merge(tile[0] + x + (tile[1] + y) * 9, 1, Integer::sum);
                    }
                }
            }
        }
        for (int slot : fossil) counts.remove(slot);
        int best = -1, bestCount = -1;
        for (var e : counts.entrySet()) {
            if (e.getValue() > bestCount) {
                bestCount = e.getValue();
                best = e.getKey();
            }
        }
        List<Type> shown = percentage == null ? List.of() : possible;
        if (best < 0) return new Move(-1, 0, 0, fossil.isEmpty(), !fossil.isEmpty(), shown);
        return new Move(best, bestCount / (double) total, total, false, false, shown);
    }
}
