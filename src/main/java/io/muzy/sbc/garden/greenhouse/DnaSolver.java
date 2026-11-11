// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.greenhouse;

import com.google.gson.JsonParser;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.garden.GardenPack;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.mc.Interactions;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// from skyhanni DnaAnalyzerSolver: the dna analyzer's strands (each column has each colour once) have to line
// up by swapping inside a column
// a dp over the 24 orders of a column finds the fewest swaps, the 2 slots of the next swap go green. never
// clicks, you do. whether end columns can move comes from the repo (dna_solver_allows_ends)
public final class DnaSolver {
    private DnaSolver() {}

    public static final String ID = "garden_dna_solver";
    private static final int ROWS = 4, UNREACHABLE = 1000;
    private static final List<int[]> ROW_PERMUTATIONS = new ArrayList<>();
    private static Boolean allowsEnds;

    static {
        permute(new int[]{0, 1, 2, 3}, 0);
    }

    // one swap: column + two rows
    public record Swap(int column, int a, int b) {}

    private static void permute(int[] a, int l) {
        if (l == ROWS) {
            ROW_PERMUTATIONS.add(a.clone());
            return;
        }
        for (int i = l; i < ROWS; i++) {
            int t = a[l];
            a[l] = a[i];
            a[i] = t;
            permute(a, l + 1);
            t = a[l];
            a[l] = a[i];
            a[i] = t;
        }
    }

    private static int[] apply(int[] perm, int[] column) {
        int[] out = new int[ROWS];
        for (int i = 0; i < ROWS; i++) out[i] = column[perm[i]];
        return out;
    }

    // canColumnsConnect: each colour stays on its row or moves one row max
    static boolean connect(int[] a, int[] b) {
        for (int r = 0; r < ROWS; r++) {
            int v = a[r];
            if (b[r] == v) continue;
            if (r > 0 && b[r - 1] == v) continue;
            if (r < ROWS - 1 && b[r + 1] == v) continue;
            return false;
        }
        return true;
    }

    // getMinimumColumnSwaps: permutation cycles, each swapped through. returns row pairs
    static List<int[]> columnSwaps(int[] from, int[] to) {
        int[] pos = new int[ROWS];
        for (int i = 0; i < ROWS; i++) {
            int at = 0;
            for (int k = 0; k < ROWS; k++) if (from[k] == to[i]) at = k;
            pos[at] = i;
        }
        boolean[] visited = new boolean[ROWS];
        List<int[]> swaps = new ArrayList<>();
        for (int i = 0; i < ROWS; i++) {
            if (visited[i]) continue;
            int cur = i;
            List<Integer> cycle = new ArrayList<>();
            while (!visited[cur]) {
                visited[cur] = true;
                cycle.add(cur);
                cur = pos[cur];
            }
            for (int k = 1; k < cycle.size(); k++) swaps.add(new int[]{cycle.get(0), cycle.get(k)});
        }
        return swaps;
    }

    // solveBoard: board[column][row] = colour. swaps in skyhanni's build order, last one is the one to do now.
    // null = no solution
    public static List<Swap> solve(int[][] board, boolean allowEnds) {
        int firstMutable = allowEnds ? 0 : 1, lastMutable = allowEnds ? board.length - 1 : board.length - 2;
        int count = lastMutable - firstMutable + 1, perms = ROW_PERMUTATIONS.size();
        int[][] dp = new int[count][perms], parent = new int[count][perms];
        List<List<List<int[]>>> swapMap = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            java.util.Arrays.fill(dp[i], UNREACHABLE);
            java.util.Arrays.fill(parent[i], -1);
            int[] column = board[firstMutable + i];
            List<List<int[]>> row = new ArrayList<>();
            for (int[] perm : ROW_PERMUTATIONS) row.add(columnSwaps(column, apply(perm, column)));
            swapMap.add(row);
        }
        for (int p = 0; p < perms; p++) {
            int[] perm = apply(ROW_PERMUTATIONS.get(p), board[firstMutable]);
            if (!allowEnds && !connect(board[0], perm)) continue;
            dp[0][p] = swapMap.get(0).get(p).size();
        }
        for (int i = 1; i < count; i++) {
            for (int p = 0; p < perms; p++) {
                int[] cur = apply(ROW_PERMUTATIONS.get(p), board[firstMutable + i]);
                for (int q = 0; q < perms; q++) {
                    if (dp[i - 1][q] == UNREACHABLE) continue;
                    int[] prev = apply(ROW_PERMUTATIONS.get(q), board[firstMutable + i - 1]);
                    if (!connect(prev, cur)) continue;
                    int cost = dp[i - 1][q] + swapMap.get(i).get(p).size();
                    if (cost < dp[i][p]) {
                        dp[i][p] = cost;
                        parent[i][p] = q;
                    }
                }
            }
        }
        int best = UNREACHABLE, last = -1;
        for (int p = 0; p < perms; p++) {
            int[] perm = apply(ROW_PERMUTATIONS.get(p), board[lastMutable]);
            if (!allowEnds && !connect(perm, board[board.length - 1])) continue;
            if (dp[count - 1][p] < best) {
                best = dp[count - 1][p];
                last = p;
            }
        }
        if (last == -1) return null;
        List<Swap> result = new ArrayList<>();
        int cur = last;
        for (int i = count - 1; i >= 0 && cur >= 0; i--) {
            for (int[] s : swapMap.get(i).get(cur)) result.add(new Swap(firstMutable + i, s[0], s[1]));
            cur = parent[i][cur];
        }
        return result;
    }

    // Colors.toColor: 0-3 by the name's colour, -1 if its not a strand
    static int colour(String name) {
        if (name.startsWith("§cDNA")) return 0;
        if (name.startsWith("§eDNA")) return 1;
        if (name.startsWith("§9DNA")) return 2;
        if (name.startsWith("§aDNA")) return 3;
        return -1;
    }

    private static boolean allowsEnds() {
        if (allowsEnds != null) return allowsEnds;
        boolean out = false;
        try (var in = io.muzy.sbc.core.data.DataFiles.sh("Garden.json")) {
            if (in != null) out = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject().get("dna_solver_allows_ends").getAsBoolean();
        } catch (Exception e) {
            io.muzy.sbc.SbcMod.LOG.warn("No dna_solver_allows_ends in Garden.json", e);
        }
        allowsEnds = out;
        return out;
    }

    // the 2 slots of the next swap, null if the menu isnt a whole board yet
    static int[] next(MenuView v, boolean ends) {
        int[][] board = new int[9][4];
        for (int slot = 9; slot <= 44; slot++) {
            int c = colour(v.item(slot).name);
            if (c < 0) return null;
            board[slot % 9][slot / 9 - 1] = c;
        }
        for (int[] column : board) {
            boolean[] seen = new boolean[4];
            for (int c : column) {
                if (seen[c]) return null; // "did not find every colour in every column": a partial update
                seen[c] = true;
            }
        }
        List<Swap> swaps = solve(board, ends);
        if (swaps == null || swaps.isEmpty()) return null;
        Swap s = swaps.get(swaps.size() - 1);
        return new int[]{s.column() + (s.a() + 1) * 9, s.column() + (s.b() + 1) * 9};
    }

    private static void mark(MenuView v) {
        int[] slots = next(v, allowsEnds());
        if (slots == null) return;
        v.back(slots[0], MenuView.GREEN);
        v.back(slots[1], MenuView.GREEN);
    }

    private static boolean inMenu(String title) {
        return title.endsWith(" DNA");
    }

    public static void register() {
        Features.add(new Feature(ID, "Garden", "DNA Analyzer solver", "In the DNA Analyzer, the two strands to swap next are green, for the fewest swaps. You do the clicking.", "SkyHanni: DNA Analyzer Solver")
                .when(GardenPack::skyblock)
                .option(Option.bool("garden_dna_solver.block_close", "Block the close button", "Stops an accidental click on it during the puzzle.", false))
                .option(Option.bool("garden_dna_solver.hide_tips", "Hide the strands' tooltips", "They cover the board while you work.", true)));
        MenuMarks.add(ID, DnaSolver::inMenu, DnaSolver::mark);
        Interactions.onSlotClick((screen, slot, id, button) -> {
            if (!Features.on(ID) || !GardenPack.on(ID, "block_close") || id != 49) return false;
            return inMenu(GameState.strip(screen.getTitle().getString()).trim());
        });
    }

    // ToolTipTextEvent: does a board slot want no tooltip, for the tooltip hook
    public static boolean hidesTip(String title, int slot) {
        return Features.on(ID) && GardenPack.on(ID, "hide_tips") && inMenu(title) && slot >= 9 && slot <= 44;
    }
}
