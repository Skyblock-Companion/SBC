// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events.carnival;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.combat.Combat;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.dungeon.DungeonMarks;
import io.github.zmuzy.sbc.events.EventSheet;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.mc.BlockHooks;
import io.github.zmuzy.sbc.render.WorldDraw;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;

// fruit digging at the carnival, in the world and on the carnival card. from skyhanni CarnivalFruitDigging:
// clues you dug on their blocks, fruit left, solver's next dig. grid is theirs
public final class FruitDigging {
    public static final String ID = "carnival_panel";
    static final int GRID_LENGTH = 7, MAX_DIGS = 15;
    private static final int START_X = -112, START_Z = 19, GRID_Y = 72;

    public enum Fruit {
        UNKNOWN("Unknown", 0, 0, "", false), NO_FRUIT("No Fruit", 0, 0, "", false), BOMB("Bomb", 0, 10, "CARNIVAL_BOMB", false), RUM("Rum", 0, 5, "CARNIVAL_RUM", false),
        MANGO("Mango", 300, 10, "CARNIVAL_MANGO", true), APPLE("Apple", 100, 8, "CARNIVAL_APPLE", true), WATERMELON("Watermelon", 100, 4, "CARNIVAL_WATERMELON", true),
        POMEGRANATE("Pomegranate", 200, 4, "CARNIVAL_POMEGRANATE", true), COCONUT("Coconut", 200, 3, "CARNIVAL_COCONUT", true), CHERRY("Cherry", 200, 2, "CARNIVAL_CHERRY", true),
        DURIAN("Durian", 800, 2, "CARNIVAL_DURIAN", true), DRAGON_FRUIT("Dragonfruit", 1200, 1, "CARNIVAL_DRAGON_FRUIT", true);

        public final String inGameName, textureKey;
        public final int points, count;
        public final boolean edible;

        Fruit(String inGameName, int points, int count, String textureKey, boolean edible) {
            this.inGameName = inGameName;
            this.points = points;
            this.count = count;
            this.textureKey = textureKey;
            this.edible = edible;
        }

        static Fruit fromName(String name) {
            for (Fruit f : values()) if (f.inGameName.equalsIgnoreCase(name)) return f;
            return null;
        }

        static Fruit fromTexture(String texture) {
            if (texture == null || texture.isEmpty()) return null;
            for (Fruit f : values()) if (!f.textureKey.isEmpty() && DungeonMarks.skull(f.textureKey).equals(texture)) return f;
            return null;
        }
    }

    static final class Cell {
        Integer adjacentMines;
        Fruit treasure = Fruit.UNKNOWN, solved = Fruit.UNKNOWN, anchored = Fruit.UNKNOWN, uncovered = Fruit.UNKNOWN;
        boolean diggable = true, removed;

        Fruit found() {
            return uncovered != Fruit.UNKNOWN ? uncovered : solved;
        }

        FruitSolver.CellInput input() {
            Fruit content = uncovered != Fruit.UNKNOWN ? uncovered : solved != Fruit.UNKNOWN ? solved : null;
            return new FruitSolver.CellInput(content, diggable, !diggable && content == null, false, adjacentMines, treasure == Fruit.UNKNOWN ? null : treasure, anchored == Fruit.UNKNOWN ? null : anchored);
        }
    }

    // one game without the game, tests drive it with the same events the world gives
    static final class Game {
        final Cell[][] cells = new Cell[GRID_LENGTH][GRID_LENGTH];
        final Map<Fruit, Integer> remaining = new EnumMap<>(Fruit.class);
        final FruitSolver solver;
        FruitSolver.Recommendation recommendation;
        // floating fruit already read, an item gets shown once when the anchor shows it
        final java.util.Set<Integer> shownItems = new java.util.HashSet<>();
        boolean dirty, playing;
        int digsUsed, lastRow = -1, lastCol = -1;

        Game(Random random) {
            solver = new FruitSolver(GRID_LENGTH, random);
            reset();
        }

        void reset() {
            for (int r = 0; r < GRID_LENGTH; r++) for (int c = 0; c < GRID_LENGTH; c++) cells[r][c] = new Cell();
            remaining.clear();
            shownItems.clear();
            for (Fruit f : Fruit.values()) remaining.put(f, f.count);
            recommendation = null;
            dirty = playing = false;
            digsUsed = 0;
            lastRow = lastCol = -1;
        }

        void start() {
            reset();
            playing = true;
            dirty = true;
        }

        Fruit lastDug() {
            return lastRow < 0 ? Fruit.NO_FRUIT : cells[lastRow][lastCol].uncovered;
        }

        // Fruit.getPointValue
        int pointValue(Fruit f) {
            Fruit last = lastDug();
            double multiplier = last == Fruit.POMEGRANATE ? 1.5 : last == Fruit.DURIAN ? 0.5 : 1;
            int dug = f.count - remaining.getOrDefault(f, 0);
            if (f == Fruit.APPLE) return (int) (multiplier * (f.points + 100 * dug));
            if (f == Fruit.CHERRY) return (int) (multiplier * (f.points + 300 * dug));
            return (int) (multiplier * f.points);
        }

        private void take(Cell cell, Fruit fruit) {
            if (fruit == Fruit.UNKNOWN || fruit == Fruit.NO_FRUIT) return;
            if (!cell.removed && !cell.diggable) {
                remaining.merge(fruit, -1, Integer::sum);
                cell.removed = true;
            }
        }

        // onBlockChange: sand -> sandstone where you dug, stairs where a bomb took it
        void blockChanged(int row, int col, boolean dug, boolean destroyed) {
            Cell cell = cells[row][col];
            cell.diggable = false;
            if (dug) {
                lastRow = row;
                lastCol = col;
                digsUsed++;
            } else if (destroyed) {
                take(cell, cell.solved);
            }
            dirty = true;
        }

        // handleAnchor: fruit an anchor shows, floating over its cell
        void shown(int row, int col, Fruit fruit) {
            if (lastRow < 0) return;
            Cell solved = cells[row][col], dug = cells[lastRow][lastCol];
            boolean updated = false;
            if (solved.solved == Fruit.UNKNOWN) {
                solved.solved = fruit;
                take(solved, fruit);
                updated = true;
            }
            if (solved.diggable && dug.anchored == Fruit.UNKNOWN) {
                dug.anchored = fruit;
                updated = true;
            }
            if (updated) dirty = true;
        }

        // onEntityNameUpdate: a name shows over a fruit that got dug or shown by a watermelon
        void named(int row, int col, String name) {
            Matcher m = Patterns.get("SH.event.carnival.fruitdigging.reveal").matcher(name);
            if (!m.matches()) return;
            Fruit fruit = Fruit.fromName(m.group("name"));
            if (fruit == null) return;
            Cell cell = cells[row][col];
            take(cell, fruit);
            if (cell.uncovered != fruit) {
                cell.uncovered = fruit;
                dirty = true;
            }
        }

        // onChat
        void chat(String clean) {
            if (Patterns.get("SH.event.carnival.fruitdigging.started").matcher(clean).matches()) {
                start();
                return;
            }
            if (Patterns.get("SH.event.carnival.fruitdigging.end").matcher(clean).matches()) {
                reset();
                return;
            }
            if (lastRow < 0) return;
            Cell cell = cells[lastRow][lastCol];
            Matcher m = Patterns.get("SH.event.carnival.fruitdigging.mines").matcher(clean);
            if (m.matches()) {
                cell.adjacentMines = Integer.parseInt(m.group("bombs"));
                dirty = true;
                return;
            }
            m = Patterns.get("SH.event.carnival.fruitdigging.treasure").matcher(clean);
            if (m.matches()) {
                for (Fruit f : Fruit.values()) {
                    if (f.inGameName.equals(m.group("fruit"))) {
                        cell.treasure = f;
                        dirty = true;
                    }
                }
                return;
            }
            if (Patterns.get("SH.event.carnival.fruitdigging.nofruitsnearby").matcher(clean).matches()) {
                cell.treasure = Fruit.NO_FRUIT;
                cell.anchored = Fruit.NO_FRUIT;
                dirty = true;
            }
        }

        // updateRecommendation
        void solve() {
            if (!dirty) return;
            dirty = false;
            FruitSolver.CellInput[][] grid = new FruitSolver.CellInput[GRID_LENGTH][GRID_LENGTH];
            for (int r = 0; r < GRID_LENGTH; r++) for (int c = 0; c < GRID_LENGTH; c++) grid[r][c] = cells[r][c].input();
            Fruit last = lastDug();
            recommendation = solver.recommend(grid, digsUsed, last == Fruit.POMEGRANATE ? 1.5 : last == Fruit.DURIAN ? 0.5 : 1, last == Fruit.COCONUT);
        }
    }

    private static final Game GAME = new Game(new Random());

    private FruitDigging() {}

    public static boolean playing() {
        return GAME.playing;
    }

    // GamePos.label: row = letter, col = number
    static String label(int row, int col) {
        return (char) ('A' + row) + String.valueOf(col + 1);
    }

    private static int[] gamePos(double x, double z) {
        int col = (int) (x - START_X), row = (int) (z - START_Z);
        return row >= 0 && row < GRID_LENGTH && col >= 0 && col < GRID_LENGTH && x >= START_X && z >= START_Z ? new int[]{row, col} : null;
    }

    private static boolean opt(String key) {
        return Combat.opt(ID, key);
    }

    private static String pct(double p) {
        return Math.round(p * 100) + "%";
    }

    // buildSolverDisplay + buildRemainingFruitDisplay, on the carnival card
    static void fill(EventSheet s, Game game, boolean bestDig, boolean remainingFruit) {
        FruitSolver.Recommendation rec = game.recommendation;
        if (bestDig && rec != null) {
            s.line("Rounds", game.digsUsed + "/" + MAX_DIGS, Modern.INK).meter(game.digsUsed / (double) MAX_DIGS);
            s.line("Dig", label(rec.targetRow(), rec.targetCol()), Modern.GOOD);
            s.line("Use", rec.shovel(), Modern.BAD);
            s.line("Bomb", pct(rec.pBomb()), Modern.WARN);
            s.line("Samples", String.valueOf(rec.samples()), Modern.DIM);
        }
        if (!remainingFruit) return;
        List<Fruit> left = new ArrayList<>();
        for (Fruit f : Fruit.values()) if (game.remaining.getOrDefault(f, 0) > 0) left.add(f);
        if (left.isEmpty()) return;
        left.sort(Comparator.<Fruit>comparingInt(game::pointValue).reversed().thenComparing(f -> f.inGameName));
        s.section("Fruit left");
        for (Fruit f : left) {
            EventSheet.Line l = s.line(f.inGameName, String.valueOf(game.remaining.get(f)), Modern.INK).brief(f.inGameName);
            if (f.edible) l.tail(String.valueOf(game.pointValue(f)));
        }
    }

    public static java.util.List<io.github.zmuzy.sbc.ui.hud.HudElement.Row> plainRows() {
        java.util.List<io.github.zmuzy.sbc.ui.hud.HudElement.Row> out = new ArrayList<>();
        if (!GAME.playing) return out;
        GAME.solve();
        FruitSolver.Recommendation rec = GAME.recommendation;
        if (rec != null && opt("best_dig")) {
            out.add(new io.github.zmuzy.sbc.ui.hud.HudElement.Row("Rounds", GAME.digsUsed + "/" + MAX_DIGS, 0));
            out.add(new io.github.zmuzy.sbc.ui.hud.HudElement.Row("Dig", label(rec.targetRow(), rec.targetCol()) + " " + rec.shovel(), 0));
            out.add(new io.github.zmuzy.sbc.ui.hud.HudElement.Row("Bomb", pct(rec.pBomb()), 0));
        }
        if (opt("remaining_fruit")) for (Fruit f : Fruit.values()) if (GAME.remaining.getOrDefault(f, 0) > 0) out.add(new io.github.zmuzy.sbc.ui.hud.HudElement.Row(f.inGameName, String.valueOf(GAME.remaining.get(f)), 0));
        return out;
    }

    public static void fillLive(EventSheet s) {
        if (!GAME.playing) return;
        GAME.solve();
        fill(s, GAME, opt("best_dig"), opt("remaining_fruit"));
    }

    public static void fillSample(EventSheet s) {
        Game g = new Game(new Random(1));
        g.start();
        g.recommendation = new FruitSolver.Recommendation(1, 2, "Anchor", 240, 0.12, 0.03, 500);
        g.digsUsed = 5;
        fill(s, g, true, true);
    }

    private static void draw(WorldDraw w) {
        if (!GAME.playing || !Carnival.inArea()) return;
        GAME.solve();
        for (int row = 0; row < GRID_LENGTH; row++) {
            for (int col = 0; col < GRID_LENGTH; col++) {
                Cell cell = GAME.cells[row][col];
                int x = START_X + col, z = START_Z + row;
                if (!cell.diggable) w.box(new AABB(x, GRID_Y, z, x + 1, GRID_Y + 1.1, z + 1), 0xFF808080, true, false);
                List<String> label = new ArrayList<>();
                if (opt("found_fruit") && !cell.diggable && cell.found() != Fruit.UNKNOWN) label.add("§a" + cell.found().inGameName);
                if (opt("guesses") && cell.uncovered == Fruit.UNKNOWN && cell.diggable && cell.solved != Fruit.UNKNOWN) label.add("§b" + cell.solved.inGameName + " (" + GAME.pointValue(cell.solved) + ")");
                if (opt("clues")) {
                    if (cell.treasure != Fruit.UNKNOWN) label.add("§6≤ " + cell.treasure.inGameName);
                    if (cell.anchored != Fruit.UNKNOWN && cell.anchored != cell.treasure) label.add("§6≥ " + cell.anchored.inGameName);
                }
                if (opt("mines") && cell.adjacentMines != null) label.add("§c" + cell.adjacentMines);
                double y = GRID_Y + 1.1 + (label.size() - 1) * 0.25;
                for (String text : label) {
                    w.label(text, new Vec3(x + 0.5, y, z + 0.5), false);
                    y -= 0.25;
                }
            }
        }
        FruitSolver.Recommendation rec = GAME.recommendation;
        if (rec != null && opt("best_dig") && GAME.cells[rec.targetRow()][rec.targetCol()].diggable) {
            Vec3 at = new Vec3(START_X + rec.targetCol(), GRID_Y, START_Z + rec.targetRow());
            w.waypoint(at, 0xFF55FF55, "§aDig here §7with §c" + rec.shovel() + " §7" + pct(rec.pBomb()) + " bomb", false, false);
        }
    }

    // onDataWatcherUpdate + onEntityNameUpdate, checked each tick while playing. known stuff isnt looked at again
    private static void tick(Minecraft mc) {
        if (!GAME.playing || mc.level == null || mc.player == null) return;
        AABB grid = new AABB(START_X, GRID_Y - 2, START_Z, START_X + GRID_LENGTH, GRID_Y + 6, START_Z + GRID_LENGTH);
        for (Entity e : mc.level.getEntitiesOfClass(Entity.class, grid, e -> e instanceof ArmorStand || e instanceof ItemEntity)) {
            int[] pos = gamePos(e.getX(), e.getZ());
            if (pos == null) continue;
            if (e instanceof ArmorStand st && st.hasCustomName()) {
                String name = GameState.strip(st.getCustomName().getString()).trim();
                if (!name.isBlank()) GAME.named(pos[0], pos[1], name);
            } else if (e instanceof ItemEntity item) {
                Fruit fruit = Fruit.fromTexture(DungeonMarks.headTexture(item.getItem()));
                if (fruit != null && GAME.shownItems.add(item.getId())) GAME.shown(pos[0], pos[1], fruit);
            }
        }
    }

    // carnival card options it had before it could solve
    static void options(Feature f) {
        f.world(FruitDigging::draw).tick(FruitDigging::tick)
                .left(GAME::reset)
                .option(Option.bool("carnival_panel.best_dig", "Best dig", "Marks the dig the solver would make next, and which shovel to hold.", true))
                .option(Option.bool("carnival_panel.remaining_fruit", "Fruit left", "How much of each fruit is still in the ground, and its worth now.", true))
                .option(Option.bool("carnival_panel.guesses", "Fruit you know", "On a block an anchor or a watermelon showed.", true))
                .option(Option.bool("carnival_panel.clues", "Treasure and anchor clues", "On the block you dug them from.", true))
                .option(Option.bool("carnival_panel.mines", "Bomb counts", "On the block you dug them from.", true))
                .option(Option.bool("carnival_panel.found_fruit", "Fruit that is gone", "On blocks dug or destroyed.", false));
    }

    static void register() {
        ChatBus.register(ChatBus.OBSERVE + 24, "fruit digging", null, line -> {
            if (Features.on(ID) && Carnival.inArea()) GAME.chat(GameState.strip(line.plain()));
            return ChatBus.PASS;
        });
        BlockHooks.listen((pos, old, now) -> {
            if (!old.is(Blocks.SAND) || !Features.on(ID) || !GAME.playing || pos.getY() != GRID_Y) return;
            int[] at = gamePos(pos.getX(), pos.getZ());
            if (at != null) GAME.blockChanged(at[0], at[1], now.is(Blocks.SANDSTONE), now.is(Blocks.SANDSTONE_STAIRS));
        });
    }
}
