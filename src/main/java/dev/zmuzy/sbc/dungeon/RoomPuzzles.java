// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.dungeon;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.core.SbTime;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.mc.Interactions;
import dev.zmuzy.sbc.nav.Graph;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

// room puzzles you solve from what the room tells you, from skyblocker skyblock/dungeon/puzzle: three weirdos
// (which chest), quiz (which answer), creeper beams, tic tac toe
public final class RoomPuzzles {
    // ---- three weirdos (from skyblocker ThreeWeirdos) ----

    private static final dev.zmuzy.sbc.core.Patterns.P WEIRDO = dev.zmuzy.sbc.core.Patterns.p("RoomPuzzles.WEIRDO");

    private static BlockPos notLyingChest;
    private static String weirdoName = "";
    private static Object weirdoLevel;

    private static BlockPos actual(Dungeon.Placed room, BlockPos relative) {
        Vec3 a = room.toActual(relative.getX(), relative.getY(), relative.getZ());
        return BlockPos.containing(Math.floor(a.x), Math.floor(a.y), Math.floor(a.z));
    }

    // like odin WeirdosSolver: the speaker by name wherever it stands, so the room doesnt have to be known yet.
    // each of the three has one chest touching its block on a side (the middle one stands a block back)
    private static ChatBus.Result onWeirdo(ChatBus.Line line) {
        Matcher m = WEIRDO.matcher(line.plain().trim());
        if (!m.matches()) return ChatBus.PASS;
        var mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return ChatBus.PASS;
        String name = m.group(1);
        for (var e : mc.level.entitiesForRendering()) {
            if (!(e instanceof ArmorStand) || e.distanceToSqr(mc.player) > 40 * 40 || !GameState.strip(e.getName().getString()).equals(name)) continue;
            BlockPos chest = chestBeside(mc, BlockPos.containing(e.getX(), e.getY(), e.getZ()));
            if (chest == null) continue;
            notLyingChest = chest;
            weirdoName = name;
            weirdoLevel = mc.level;
            break;
        }
        return ChatBus.PASS;
    }

    private static BlockPos chestBeside(Minecraft mc, BlockPos npc) {
        for (int dy = 0; dy >= -1; dy--) {
            for (var dir : Direction.Plane.HORIZONTAL) {
                BlockPos p = npc.offset(0, dy, 0).relative(dir);
                if (mc.level.getBlockState(p).getBlock() instanceof net.minecraft.world.level.block.ChestBlock) return p;
            }
        }
        return null;
    }

    private static void drawWeirdo(WorldDraw w) {
        var mc = Minecraft.getInstance();
        if (notLyingChest == null) return;
        // a new run or a room away: the old mark goes
        if (mc.level != weirdoLevel || mc.player == null || mc.player.distanceToSqr(Vec3.atCenterOf(notLyingChest)) > 48 * 48) {
            notLyingChest = null;
            return;
        }
        w.box(new AABB(notLyingChest), 0x8000FF00, true, false);
        w.label("§a" + weirdoName, Vec3.atCenterOf(notLyingChest).add(0, 1.2, 0), true);
    }

    // ---- quiz (from skyblocker Trivia) ----

    private static final dev.zmuzy.sbc.core.Patterns.P QUESTION = dev.zmuzy.sbc.core.Patterns.p("RoomPuzzles.QUESTION");
    private static final dev.zmuzy.sbc.core.Patterns.P ANSWER = dev.zmuzy.sbc.core.Patterns.p("RoomPuzzles.ANSWER");
    private static final dev.zmuzy.sbc.core.Patterns.P ORUO = dev.zmuzy.sbc.core.Patterns.p("RoomPuzzles.ORUO");

    // from skyblocker Trivia CHOICE_A/B/C: the 3 answer buttons in room coords
    private static final BlockPos[] CHOICE = {new BlockPos(20, 70, 6), new BlockPos(15, 70, 9), new BlockPos(10, 70, 6)};
    private static final Direction[] DIRECTIONS = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    // from skyblocker Trivia.answers (fairy soul counts get added lazily from the island graphs fairy_soul nodes)
    static final Map<String, List<String>> QUIZ = new HashMap<>();

    static {
        QUIZ.put("What is the status of The Watcher?", List.of("Stalker"));
        QUIZ.put("What is the status of Bonzo?", List.of("New Necromancer"));
        QUIZ.put("What is the status of Scarf?", List.of("Apprentice Necromancer"));
        QUIZ.put("What is the status of The Professor?", List.of("Professor"));
        QUIZ.put("What is the status of Thorn?", List.of("Shaman Necromancer"));
        QUIZ.put("What is the status of Livid?", List.of("Master Necromancer"));
        QUIZ.put("What is the status of Sadan?", List.of("Necromancer Lord"));
        QUIZ.put("What is the status of Maxor?", List.of("The Wither Lords"));
        QUIZ.put("What is the status of Goldor?", List.of("The Wither Lords"));
        QUIZ.put("What is the status of Storm?", List.of("The Wither Lords"));
        QUIZ.put("What is the status of Necron?", List.of("The Wither Lords"));
        QUIZ.put("What is the status of Maxor, Storm, Goldor, and Necron?", List.of("The Wither Lords"));
        QUIZ.put("Which brother is on the Spider's Den?", List.of("Rick"));
        QUIZ.put("What is the name of Rick's brother?", List.of("Pat"));
        // full question is "What is the name of the vendor in the Hub who sells stained glass?" but only "glass?" is
        // seen (2nd line)
        QUIZ.put("glass?", List.of("Wool Weaver"));
        QUIZ.put("What is the name of the person that upgrades pets?", List.of("Kat"));
        QUIZ.put("What is the name of the lady of the Nether?", List.of("Elle"));
        QUIZ.put("Which villager in the Village gives you a Rogue Sword?", List.of("Jamie"));
        QUIZ.put("How many unique minions are there?", List.of("61 Minions"));
        QUIZ.put("Which of these enemies does not spawn in the Spider's Den?", List.of("Zombie Spider", "Cave Spider", "Wither Skeleton", "Dashing Spooder", "Broodfather", "Night Spider"));
        QUIZ.put("Which of these monsters only spawns at night?", List.of("Zombie Villager", "Ghast"));
        QUIZ.put("Which of these is not a dragon in The End?", List.of("Zoomer Dragon", "Weak Dragon", "Stonk Dragon", "Holy Dragon", "Boomer Dragon", "Booger Dragon", "Older Dragon", "Elder Dragon", "Stable Dragon", "Professor Dragon"));
    }

    // skyblocker trivias fairy soul questions: their location id -> our island graph key (null = every island)
    private static final String[][] FAIRY_QUESTIONS = {
            {"How many total Fairy Souls are there?", null},
            {"How many Fairy Souls are there in Spider's Den?", "SPIDER_DEN"},
            {"How many Fairy Souls are there in The End?", "THE_END"},
            {"How many Fairy Souls are there in The Farming Islands?", "THE_FARMING_ISLANDS"},
            {"How many Fairy Souls are there in Crimson Isle?", "CRIMSON_ISLE"},
            {"How many Fairy Souls are there in The Park?", "THE_PARK"},
            {"How many Fairy Souls are there in Jerry's Workshop?", "WINTER"},
            {"How many Fairy Souls are there in Hub?", "HUB"},
            {"How many Fairy Souls are there in The Hub?", "HUB"},
            {"How many Fairy Souls are there in Deep Caverns?", "DEEP_CAVERNS"},
            {"How many Fairy Souls are there in Gold Mine?", "GOLD_MINES"},
            {"How many Fairy Souls are there in Dungeon Hub?", "DUNGEON_HUB"}};
    private static final String[] ALL_ISLANDS = {"HUB", "SPIDER_DEN", "THE_END", "THE_FARMING_ISLANDS", "CRIMSON_ISLE", "THE_PARK", "WINTER", "DEEP_CAVERNS", "GOLD_MINES",
            "DUNGEON_HUB", "DWARVEN_MINES", "CRYSTAL_HOLLOWS", "GLACITE_TUNNELS", "THE_RIFT", "BACKWATER_BAYOU", "GALATEA", "LOTUS_ATOLL", "SAFARI", "TORRHUS_CANYON", "MAIN_LOBBY"};
    private static boolean fairyLoaded;

    // from skyblocker FairySouls.getFairySoulsSize: fairy_soul nodes of one island graph or all of them
    static int fairySouls(String island) {
        if (island != null) return fairySouls(Repo.graph(island));
        int n = 0;
        for (String i : ALL_ISLANDS) n += fairySouls(Repo.graph(i));
        return n;
    }

    static int fairySouls(Graph g) {
        if (g == null) return 0;
        int n = 0;
        for (Graph.Node node : g.nodes) if (node.tags().contains("fairy_soul")) n++;
        return n;
    }

    private static synchronized void loadFairyAnswers() {
        if (fairyLoaded) return;
        fairyLoaded = true;
        for (String[] q : FAIRY_QUESTIONS) {
            int n = fairySouls(q[1]);
            if (n > 0) QUIZ.put(q[0], List.of(n + " Fairy Souls"));
        }
    }

    private static List<String> solutions = Collections.emptyList();
    private static String currentSolution = "";
    private static final List<AABB> boxesToHighlight = new ArrayList<>();

    // from skyblocker Trivia.updateSolutions
    static List<String> solutionsFor(String question, int skyblockYear) {
        String trimmed = question.trim();
        if (trimmed.equals("What SkyBlock year is it?")) return List.of("Year " + skyblockYear);
        return QUIZ.getOrDefault(trimmed, Collections.emptyList());
    }

    // from skyblocker Trivia.onMessage on one plain line: replacement for a wrong answer, "" = leave the line,
    // null = not a quiz line
    static String feedQuiz(String plain, int skyblockYear) {
        Matcher o = ORUO.matcher(plain);
        if (o.matches()) {
            resetQuiz();
            return "";
        }
        Matcher a = ANSWER.matcher(plain);
        if (a.matches()) {
            String choice = a.group(2);
            if (solutions.isEmpty()) return "";
            if (!solutions.contains(choice)) return "    §6 " + a.group(1) + " §c" + choice;
            currentSolution = a.group(1);
            return "";
        }
        Matcher q = QUESTION.matcher(plain);
        if (q.matches()) {
            solutions = solutionsFor(q.group(1), skyblockYear);
            resetQuiz();
            return "";
        }
        return null;
    }

    static void resetQuiz() {
        currentSolution = "";
        boxesToHighlight.clear();
    }

    static void resetQuizAll() {
        solutions = Collections.emptyList();
        resetQuiz();
    }

    private static ChatBus.Result onQuiz(ChatBus.Line line) {
        loadFairyAnswers();
        String r = feedQuiz(line.plain(), SbTime.sbDate(System.currentTimeMillis()).year());
        if (r == null || r.isEmpty()) return ChatBus.PASS;
        return new ChatBus.Replace(Component.literal(r));
    }

    static int choiceIndex(String solution) {
        return switch (solution) {
            case "ⓐ" -> 0;
            case "ⓑ" -> 1;
            case "ⓒ" -> 2;
            default -> -1;
        };
    }

    private static void tickQuiz(Minecraft mc) {
        if (currentSolution.isEmpty() || !boxesToHighlight.isEmpty() || mc.level == null) return;
        var room = Dungeon.current();
        if (room == null || !room.def.id().startsWith("trivia-room")) return;
        int i = choiceIndex(currentSolution);
        if (i < 0) return;
        BlockPos center = actual(room, CHOICE[i]);
        for (Direction d : DIRECTIONS) {
            BlockPos p = center.relative(d);
            var shape = mc.level.getBlockState(p).getShape(mc.level, p);
            if (!shape.isEmpty()) boxesToHighlight.add(shape.bounds().move(p));
        }
    }

    private static void drawQuiz(WorldDraw w) {
        if (boxesToHighlight.isEmpty()) return;
        for (AABB box : boxesToHighlight) {
            w.box(box, 0x8000FF00, true, false);
            w.box(box, 0xFF00FF00, false, false);
        }
    }

    // ---- creeper beams (from skyblocker CreeperBeams) ----

    private record Beam(BlockPos a, BlockPos b) {
        Vec3 pa() {
            return Vec3.atCenterOf(a);
        }

        Vec3 pb() {
            return Vec3.atCenterOf(b);
        }

        boolean shares(Beam o) {
            return a.equals(o.a) || a.equals(o.b) || b.equals(o.a) || b.equals(o.b);
        }
    }

    private static final int[] BEAM_COLORS = {0xFFFF3B3B, 0xFFFFD36B, 0xFF39D0FF, 0xFFFF7ACD, 0xFFFF9F43};
    private static BlockPos creeperBase;
    private static List<Beam> beams = List.of();

    private static boolean isTarget(Minecraft mc, BlockPos p) {
        String id = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(p).getBlock()).getPath();
        return id.equals("sea_lantern") || id.equals("prismarine");
    }

    private static boolean isDone(Minecraft mc, Beam b) {
        return mc.level.getBlockState(b.a()).is(net.minecraft.world.level.block.Blocks.PRISMARINE) && mc.level.getBlockState(b.b()).is(net.minecraft.world.level.block.Blocks.PRISMARINE);
    }

    private static void tickCreeper(Minecraft mc) {
        var room = Dungeon.current();
        if (room == null || !room.def.id().startsWith("creeper-room") || mc.level == null || mc.player == null) {
            creeperBase = null;
            beams = List.of();
            return;
        }
        if (creeperBase == null) {
            if (mc.player.tickCount % 10 != 0) return;
            for (var c : mc.level.getEntitiesOfClass(net.minecraft.world.entity.monster.Creeper.class, mc.player.getBoundingBox().inflate(50), net.minecraft.world.entity.Entity::isAlive)) {
                BlockPos p = BlockPos.containing(c.getX(), 74, c.getZ());
                if (isTarget(mc, p)) {
                    creeperBase = p;
                    break;
                }
            }
            if (creeperBase == null) return;
            List<BlockPos> targets = new ArrayList<>();
            for (BlockPos p : BlockPos.betweenClosed(creeperBase.getX() - 15, 86, creeperBase.getZ() - 15, creeperBase.getX() + 16, 68, creeperBase.getZ() + 16))
                if (isTarget(mc, p)) targets.add(p.immutable());
            Vec3 creeper = new Vec3(creeperBase.getX() + 0.5, 75.75, creeperBase.getZ() + 0.5);
            record Scored(Beam beam, double dist) {}
            List<Scored> all = new ArrayList<>();
            for (int i = 0; i < targets.size(); i++)
                for (int j = i + 1; j < targets.size(); j++) {
                    Beam beam = new Beam(targets.get(i), targets.get(j));
                    Vec3 a = beam.pa(), ab = beam.pb().subtract(a);
                    double len = ab.length();
                    if (len < 1e-6) continue;
                    all.add(new Scored(beam, creeper.subtract(a).cross(ab).length() / len));
                }
            all.sort(java.util.Comparator.comparingDouble(Scored::dist));
            List<Beam> chosen = new ArrayList<>();
            while (chosen.size() < 5 && !all.isEmpty()) {
                Beam first = all.removeFirst().beam();
                chosen.add(first);
                all.removeIf(s -> s.beam().shares(first));
            }
            beams = chosen;
        }
        if (!isTarget(mc, creeperBase)) {
            creeperBase = null;
            beams = List.of();
        }
    }

    private static void drawCreeper(WorldDraw w) {
        if (creeperBase == null) return;
        int i = 0;
        for (Beam b : beams) {
            boolean done = isDone(w.mc, b);
            int col = done ? 0xFF33D17A : BEAM_COLORS[i % BEAM_COLORS.length];
            i++;
            w.box(new AABB(b.a()), col, false, true);
            w.box(new AABB(b.b()), col, false, true);
            if (!done) w.line(b.pa(), b.pb(), col, 3f, true);
        }
    }

    // ---- tic tac toe (from skyblocker TicTacToe + TicTacToeUtils) ----

    private static BlockPos ticMove;

    // from skyblocker TicTacToeUtils.getScore: +10 when O has 3 in a row, -10 for X
    static int ticScore(char[][] b) {
        for (int row = 0; row < 3; row++)
            if (b[row][0] == b[row][1] && b[row][0] == b[row][2]) {
                if (b[row][0] == 'X') return -10;
                if (b[row][0] == 'O') return 10;
            }
        for (int col = 0; col < 3; col++)
            if (b[0][col] == b[1][col] && b[0][col] == b[2][col]) {
                if (b[0][col] == 'X') return -10;
                if (b[0][col] == 'O') return 10;
            }
        if (b[0][0] == b[1][1] && b[0][0] == b[2][2]) {
            if (b[0][0] == 'X') return -10;
            if (b[0][0] == 'O') return 10;
        }
        if (b[0][2] == b[1][1] && b[0][2] == b[2][0]) {
            if (b[0][2] == 'X') return -10;
            if (b[0][2] == 'O') return 10;
        }
        return 0;
    }

    private static boolean hasMoves(char[][] b) {
        for (char[] row : b) for (char c : row) if (c == '\0') return true;
        return false;
    }

    // from skyblocker TicTacToeUtils.alphabeta
    static int alphabeta(char[][] board, int alpha, int beta, int depth, boolean maximizePlayer) {
        int score = ticScore(board);
        if (score == 10 || score == -10) return score;
        if (!hasMoves(board)) return 0;
        if (maximizePlayer) {
            int bestScore = Integer.MIN_VALUE;
            outer:
            for (int row = 0; row < 3; row++)
                for (int column = 0; column < 3; column++)
                    if (board[row][column] == '\0') {
                        board[row][column] = 'O';
                        bestScore = Math.max(bestScore, alphabeta(board, alpha, beta, depth + 1, false));
                        board[row][column] = '\0';
                        alpha = Math.max(alpha, bestScore);
                        if (beta <= alpha) break outer;
                    }
            return bestScore - depth;
        } else {
            int bestScore = Integer.MAX_VALUE;
            outer:
            for (int row = 0; row < 3; row++)
                for (int column = 0; column < 3; column++)
                    if (board[row][column] == '\0') {
                        board[row][column] = 'X';
                        bestScore = Math.min(bestScore, alphabeta(board, alpha, beta, depth + 1, true));
                        board[row][column] = '\0';
                        beta = Math.min(beta, bestScore);
                        if (beta <= alpha) break outer;
                    }
            return bestScore + depth;
        }
    }

    // from skyblocker getBestMove: row, column of the best O move, null = board is full
    static int[] bestMove(char[][] board) {
        int[] best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int row = 0; row < 3; row++)
            for (int column = 0; column < 3; column++) {
                if (board[row][column] != '\0') continue;
                board[row][column] = 'O';
                int score = alphabeta(board, Integer.MIN_VALUE, Integer.MAX_VALUE, 0, false);
                board[row][column] = '\0';
                if (best == null || score > bestScore) {
                    bestScore = score;
                    best = new int[]{row, column};
                }
            }
        return best;
    }

    private static void tickTic(Minecraft mc) {
        ticMove = null;
        var room = Dungeon.current();
        if (room == null || !room.def.id().startsWith("tic-tac-toe") || mc.level == null || mc.player == null) return;
        if (mc.player.tickCount % 5 != 0) return;
        var frames = mc.level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, mc.player.getBoundingBox().inflate(21), f -> f.hasFramedMap());
        // only while the puzzle isnt done and its your turn (odd number of maps)
        if (frames.size() == 9 || (frames.size() & 1) != 1) return;
        char[][] board = new char[3][3];
        for (var f : frames) {
            var data = mc.level.getMapData(f.getFramedMapId(f.getItem()));
            if (data == null) continue;
            BlockPos bp = f.blockPosition();
            int row = switch (bp.getY()) {
                case 72 -> 0;
                case 71 -> 1;
                case 70 -> 2;
                default -> -1;
            };
            int col = switch (room.toRelative(bp.getX(), bp.getZ())[1]) {
                case 17 -> 0;
                case 16 -> 1;
                case 15 -> 2;
                default -> -1;
            };
            if (row == -1 || col == -1) continue;
            int color = data.colors[8256] & 0xFF;
            if (color == 114) board[row][col] = 'X';
            else if (color == 33) board[row][col] = 'O';
        }
        int[] best = bestMove(board);
        if (best == null) return;
        ticMove = actual(room, new BlockPos(8, 72 - best[0], 17 - best[1]));
    }

    private static void drawTic(WorldDraw w) {
        if (ticMove == null) return;
        w.box(new AABB(ticMove), 0x8000FF00, true, false);
        w.box(new AABB(ticMove), 0xFF00FF00, false, false);
    }

    public static void register() {
        java.util.function.BooleanSupplier inDungeon = () -> "CATACOMBS".equals(GameState.island) || SbcMod.DEV;
        Feature weirdos = Features.add(new Feature("dungeon_weirdos", "Dungeons", "Three Weirdos", "Marks the chest that holds the reward once the right character has spoken; the mark goes when you open it.", "Skyblocker: three weirdos solver")
                .when(inDungeon).world(RoomPuzzles::drawWeirdo));
        ChatBus.register(ChatBus.OBSERVE + 6, "weirdos", weirdos, RoomPuzzles::onWeirdo);
        Interactions.onBlockClick((pos, right) -> {
            if (notLyingChest != null && right && BlockPos.containing(pos).equals(notLyingChest)) notLyingChest = null;
        });
        Feature quiz = Features.add(new Feature("dungeon_quiz", "Dungeons", "Quiz room", "Marks the buttons of the right answer in the quiz room and greys out the wrong answers in chat.", "Skyblocker: trivia solver")
                .when(inDungeon).tick(RoomPuzzles::tickQuiz).world(RoomPuzzles::drawQuiz));
        ChatBus.register(ChatBus.OBSERVE + 7, "quiz", quiz, RoomPuzzles::onQuiz);
        Features.add(new Feature("dungeon_creeper", "Dungeons", "Creeper Beams", "Pairs up the lanterns and prismarine blocks to hit, one colour per pair; finished pairs turn green.", "Skyblocker: creeper beams solver")
                .when(inDungeon).tick(RoomPuzzles::tickCreeper).world(RoomPuzzles::drawCreeper));
        Features.add(new Feature("dungeon_tictactoe", "Dungeons", "Tic Tac Toe", "Marks the best square to play in the tic tac toe room.", "Skyblocker: tic tac toe solver")
                .when(inDungeon).tick(RoomPuzzles::tickTic).world(RoomPuzzles::drawTic));
    }
}
