// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.events.carnival;

import dev.zmuzy.sbc.events.carnival.FruitDigging.Fruit;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

// fruit digging's next dig. from skyhanni FruitDiggingSolver + FruitDiggingBelief: deal the unknown fruit
// over the unknown cells, keep boards that fit every clue, score each cell by likely gain, risk and what it'd
// tell you. weights are theirs
final class FruitSolver {
    // FruitDiggingSolver.CellInput
    record CellInput(Fruit content, boolean diggable, boolean ghost, boolean mustFruit, Integer minesCount, Fruit treasure, Fruit anchor) {}

    record Recommendation(int targetRow, int targetCol, String shovel, double expectedPoints, double pBomb, double pRum, int samples) {}

    private static final double RISK_WEIGHT = 1.0, DESTRUCTION_WEIGHT = 0.10, SETUP_WEIGHT = 0.5, INFO_WEIGHT = 15.0;
    private static final int TARGET_SAMPLES = 500, MAX_ATTEMPTS = 20_000;

    private final int size;
    private final int[][] neighborsOf;
    private final Random random;

    FruitSolver(int size, Random random) {
        this.size = size;
        this.random = random;
        neighborsOf = new int[size * size][];
        for (int id = 0; id < size * size; id++) {
            int row = id / size, col = id % size;
            List<Integer> list = new ArrayList<>();
            for (int dr = -1; dr <= 1; dr++) {
                for (int dc = -1; dc <= 1; dc++) {
                    if (dr == 0 && dc == 0) continue;
                    int r = row + dr, c = col + dc;
                    if (r >= 0 && r < size && c >= 0 && c < size) list.add(r * size + c);
                }
            }
            neighborsOf[id] = list.stream().mapToInt(Integer::intValue).toArray();
        }
    }

    private static boolean inBag(Fruit f) {
        return f.edible || f == Fruit.BOMB || f == Fruit.RUM;
    }

    Recommendation recommend(CellInput[][] grid, int digsUsed, double nextMultiplier, boolean coconutProtection) {
        return new Advisor(grid, digsUsed, nextMultiplier, coconutProtection).recommend();
    }

    // --- FruitDiggingBelief ---

    // a clue over slots, kinds from FruitDiggingBelief.Clue
    private record Clue(int kind, int[] cells, int bombs, int rank, Fruit named, boolean satisfiedByKnown) {
        static final int BOMBS = 0, FLOOR = 1, CEIL = 2, NO_FRUIT = 3, MUST = 4;
    }

    static int dowsingRank(Fruit fruit) {
        return fruit == Fruit.APPLE ? 0 : fruit.points;
    }

    private static boolean satisfies(Clue clue, int[] slots, Fruit[] deal) {
        switch (clue.kind) {
            case Clue.BOMBS -> {
                int bombs = 0;
                for (int s : slots) if (deal[s] == Fruit.BOMB) bombs++;
                return bombs == clue.bombs;
            }
            case Clue.NO_FRUIT -> {
                for (int s : slots) if (deal[s].edible) return false;
                return true;
            }
            case Clue.FLOOR, Clue.CEIL -> {
                boolean found = clue.satisfiedByKnown;
                for (int s : slots) {
                    Fruit f = deal[s];
                    if (!f.edible) continue;
                    int rank = dowsingRank(f);
                    if (clue.kind == Clue.FLOOR ? rank < clue.rank : rank > clue.rank) return false;
                    if (f == clue.named) found = true;
                }
                return found;
            }
            default -> {
                return slots.length == 0 || deal[slots[0]].edible;
            }
        }
    }

    private final class Advisor {
        final int digsUsed;
        final double nextMultiplier;
        final boolean coconutProtection;
        final Fruit[] known = new Fruit[size * size];
        final boolean[] diggable = new boolean[size * size], ghost = new boolean[size * size];
        final List<Integer> candidates = new ArrayList<>(), openCells = new ArrayList<>();
        final Set<Integer> openOnBoard = new HashSet<>();
        int applesCollected, cherriesCollected, sampleCount;
        final Map<Integer, Map<Fruit, Double>> beliefs;

        Advisor(CellInput[][] grid, int digsUsed, double nextMultiplier, boolean coconutProtection) {
            this.digsUsed = digsUsed;
            this.nextMultiplier = nextMultiplier;
            this.coconutProtection = coconutProtection;
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    int id = row * size + col;
                    CellInput cell = grid[row][col];
                    Fruit content = cell.content() == Fruit.UNKNOWN || cell.content() == Fruit.NO_FRUIT ? null : cell.content();
                    known[id] = content;
                    diggable[id] = cell.diggable();
                    ghost[id] = cell.ghost() && content == null;
                    if (cell.diggable()) {
                        candidates.add(id);
                        if (content == null) {
                            openCells.add(id);
                            openOnBoard.add(id);
                        }
                    } else if (content == null) {
                        openCells.add(id);
                    }
                    if (content != null && !cell.diggable()) {
                        if (content == Fruit.APPLE) applesCollected++;
                        if (content == Fruit.CHERRY) cherriesCollected++;
                    }
                }
            }
            beliefs = estimate(remainingBag(), clues(grid));
        }

        private List<Fruit> remainingBag() {
            Map<Fruit, Integer> counts = new EnumMap<>(Fruit.class);
            for (Fruit f : Fruit.values()) if (inBag(f)) counts.put(f, f.count);
            for (Fruit f : known) if (f != null) counts.merge(f, -1, Integer::sum);
            List<Fruit> out = new ArrayList<>();
            for (Map.Entry<Fruit, Integer> e : counts.entrySet()) for (int i = 0; i < e.getValue(); i++) out.add(e.getKey());
            return out;
        }

        private List<Clue> clues(CellInput[][] grid) {
            List<Clue> out = new ArrayList<>();
            for (int row = 0; row < size; row++) {
                for (int col = 0; col < size; col++) {
                    int id = row * size + col;
                    CellInput cell = grid[row][col];
                    List<Integer> hidden = new ArrayList<>();
                    for (int n : neighborsOf[id]) if (openOnBoard.contains(n)) hidden.add(n);
                    int[] cells = hidden.stream().mapToInt(Integer::intValue).toArray();
                    if (cell.minesCount() != null) out.add(new Clue(Clue.BOMBS, cells, cell.minesCount(), 0, null, false));
                    if (cell.treasure() != null) out.add(orderClue(id, cells, cell.treasure(), false));
                    if (cell.anchor() != null) out.add(orderClue(id, cells, cell.anchor(), true));
                    if (ghost[id] || cell.mustFruit()) out.add(new Clue(Clue.MUST, new int[]{id}, 0, 0, null, false));
                }
            }
            return out;
        }

        private Clue orderClue(int center, int[] hidden, Fruit named, boolean isAnchor) {
            if (named == Fruit.NO_FRUIT) return new Clue(Clue.NO_FRUIT, hidden, 0, 0, null, false);
            boolean satisfied = false;
            for (int n : neighborsOf[center]) if (known[n] == named && diggable[n]) satisfied = true;
            return new Clue(isAnchor ? Clue.FLOOR : Clue.CEIL, hidden, 0, dowsingRank(named), named, satisfied);
        }

        // FruitDiggingBelief.estimate
        private Map<Integer, Map<Fruit, Double>> estimate(List<Fruit> pool, List<Clue> clues) {
            Map<Integer, Map<Fruit, Double>> out = new HashMap<>();
            if (openCells.isEmpty() || pool.size() != openCells.size()) {
                sampleCount = 0;
                return out;
            }
            if (clues.isEmpty()) {
                sampleCount = TARGET_SAMPLES;
                Map<Fruit, Double> base = baseRate(pool);
                for (int c : openCells) out.put(c, base);
                return out;
            }
            Map<Integer, Integer> slotOf = new HashMap<>();
            for (int i = 0; i < openCells.size(); i++) slotOf.put(openCells.get(i), i);
            int[][] slots = new int[clues.size()][];
            for (int i = 0; i < clues.size(); i++) {
                List<Integer> s = new ArrayList<>();
                for (int cell : clues.get(i).cells) {
                    Integer slot = slotOf.get(cell);
                    if (slot != null) s.add(slot);
                }
                slots[i] = s.stream().mapToInt(Integer::intValue).toArray();
            }
            Fruit[] deal = pool.toArray(new Fruit[0]);
            List<Map<Fruit, Integer>> hits = new ArrayList<>();
            for (int i = 0; i < openCells.size(); i++) hits.add(new EnumMap<>(Fruit.class));
            int accepted = 0, attempts = 0;
            while (accepted < TARGET_SAMPLES && attempts < MAX_ATTEMPTS) {
                attempts++;
                for (int i = deal.length - 1; i >= 0; i--) {
                    int j = random.nextInt(i + 1);
                    Fruit t = deal[i];
                    deal[i] = deal[j];
                    deal[j] = t;
                }
                boolean ok = true;
                for (int i = 0; i < clues.size() && ok; i++) ok = satisfies(clues.get(i), slots[i], deal);
                if (!ok) continue;
                accepted++;
                for (int s = 0; s < deal.length; s++) hits.get(s).merge(deal[s], 1, Integer::sum);
            }
            sampleCount = accepted;
            if (accepted == 0) {
                Map<Fruit, Double> base = baseRate(pool);
                for (int c : openCells) out.put(c, base);
                return out;
            }
            for (int s = 0; s < openCells.size(); s++) {
                Map<Fruit, Double> m = new EnumMap<>(Fruit.class);
                for (Map.Entry<Fruit, Integer> e : hits.get(s).entrySet()) m.put(e.getKey(), e.getValue() / (double) accepted);
                out.put(openCells.get(s), m);
            }
            return out;
        }

        private Map<Fruit, Double> baseRate(List<Fruit> pool) {
            Map<Fruit, Double> out = new EnumMap<>(Fruit.class);
            for (Fruit f : pool) out.merge(f, 1.0 / pool.size(), Double::sum);
            return out;
        }

        // --- FruitDiggingSolver ---

        private double pointValue(Fruit f) {
            return switch (f) {
                case APPLE -> 100.0 * (applesCollected + 1);
                case CHERRY -> 200.0 + 300.0 * (cherriesCollected + 1);
                case BOMB, RUM, UNKNOWN, NO_FRUIT -> 0;
                default -> f.points;
            };
        }

        private Map<Fruit, Double> distribution(int cell) {
            return beliefs.getOrDefault(cell, Map.of());
        }

        private double fruitOdds(int cell, Fruit f) {
            if (known[cell] != null) return known[cell] == f ? 1 : 0;
            return distribution(cell).getOrDefault(f, 0.0);
        }

        private double expected(int cell) {
            double raw = 0;
            if (known[cell] != null) raw = withWatermelon(known[cell], cell);
            else for (Map.Entry<Fruit, Double> e : distribution(cell).entrySet()) raw += e.getValue() * withWatermelon(e.getKey(), cell);
            return raw * nextMultiplier;
        }

        private double withWatermelon(Fruit f, int cell) {
            if (f != Fruit.WATERMELON) return pointValue(f);
            double nearby = averageNearby(cell);
            return pointValue(f) + 0.5 * (nearby > 0 ? nearby : averageRemaining());
        }

        private double averageNearby(int cell) {
            double value = 0, weight = 0;
            for (int nb : neighborsOf[cell]) {
                if (!diggable[nb]) continue;
                if (known[nb] != null) {
                    if (known[nb].edible) {
                        value += pointValue(known[nb]);
                        weight += 1;
                    }
                } else {
                    for (Map.Entry<Fruit, Double> e : distribution(nb).entrySet()) {
                        if (e.getKey().edible) {
                            value += e.getValue() * pointValue(e.getKey());
                            weight += e.getValue();
                        }
                    }
                }
            }
            return weight > 0 ? value / weight : 0;
        }

        private double averageRemaining() {
            int count = 0;
            double value = 0;
            for (Fruit f : Fruit.values()) {
                if (!f.edible) continue;
                int n = f.count;
                for (Fruit k : known) if (k == f) n--;
                count += n;
                value += pointValue(f) * n;
            }
            return count > 0 ? value / count : 0;
        }

        private double valueAtRisk(int cell) {
            double total = 0;
            for (int nb : neighborsOf[cell]) {
                if (!diggable[nb]) continue;
                if (known[nb] != null) {
                    if (known[nb].edible) total += pointValue(known[nb]);
                } else {
                    for (Map.Entry<Fruit, Double> e : distribution(nb).entrySet()) if (e.getKey().edible) total += e.getValue() * pointValue(e.getKey());
                }
            }
            return total;
        }

        private double opportunityCost() {
            double best = 0;
            for (int c : candidates) if (fruitOdds(c, Fruit.BOMB) < 0.05 && fruitOdds(c, Fruit.RUM) < 0.05) best = Math.max(best, expected(c));
            return best > 0 ? best : averageRemaining();
        }

        private double score(int cell, double cost) {
            double risk = (fruitOdds(cell, Fruit.BOMB) + fruitOdds(cell, Fruit.RUM)) * cost * RISK_WEIGHT;
            if (!coconutProtection) risk += fruitOdds(cell, Fruit.BOMB) * DESTRUCTION_WEIGHT * valueAtRisk(cell);
            double setup = (fruitOdds(cell, Fruit.POMEGRANATE) - fruitOdds(cell, Fruit.DURIAN)) * SETUP_WEIGHT * cost;
            int hidden = 0;
            for (int n : neighborsOf[cell]) if (openOnBoard.contains(n)) hidden++;
            return expected(cell) - risk + setup + hidden * INFO_WEIGHT;
        }

        private String shovelFor(int cell) {
            double nearby = 0;
            for (int nb : neighborsOf[cell]) {
                if (!diggable[nb]) continue;
                if (known[nb] != null) {
                    if (known[nb].edible) nearby += 1;
                } else {
                    for (Map.Entry<Fruit, Double> e : distribution(nb).entrySet()) if (e.getKey().edible) nearby += e.getValue();
                }
            }
            return nearby >= 0.25 ? "Anchor" : "Mines";
        }

        Recommendation recommend() {
            if (candidates.isEmpty()) return null;
            double cost = opportunityCost();
            int best = -1;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (int c : candidates) {
                double s = score(c, cost);
                if (s > bestScore) {
                    bestScore = s;
                    best = c;
                }
            }
            // a durian halved the next dig, spend it on the cheapest safe cell while digs are left
            if (nextMultiplier < 1.0 && FruitDigging.MAX_DIGS - digsUsed > 1) {
                int absorber = -1;
                double least = Double.POSITIVE_INFINITY;
                for (int c : candidates) {
                    if (fruitOdds(c, Fruit.BOMB) >= 0.15 || fruitOdds(c, Fruit.RUM) >= 0.15) continue;
                    double e = expected(c);
                    if (e < least) {
                        least = e;
                        absorber = c;
                    }
                }
                if (absorber >= 0) best = absorber;
            }
            return new Recommendation(best / size, best % size, shovelFor(best), expected(best), fruitOdds(best, Fruit.BOMB), fruitOdds(best, Fruit.RUM), sampleCount);
        }
    }
}
