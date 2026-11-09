// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.ui;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.dungeon.Dungeon;
import dev.zmuzy.sbc.dungeon.Routes;
import dev.zmuzy.sbc.dungeon.Score;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.ui.hud.ElementStyle;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.hud.Looks;
import dev.zmuzy.sbc.ui.hud.RowText;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// the floor as a 6x6 grid: rooms you were in by kind, your room and you
public final class DungeonMap {
    private static final String ID = "dungeonmap";
    private static final int CELL = 15, GAP = 2, PAD = 7, TITLE = 14;
    // even sizes so the grid lands on whole pixels at the smaller hud size
    private static final int M_CELL = 18, M_GAP = 2, M_STEP = M_CELL + M_GAP, M_GRID = Dungeon.CELLS * M_STEP - M_GAP, M_SIDE = 96, M_GUTTER = 6, CHIP_ROW = 12, STAT_ROW = 12;
    private static final int CELL_EMPTY = 0x90050911, ROOM_CHIP = 0xFFC9A66B;
    private static final int[] GRADE_AT = {100, 160, 230, 270, 300};
    private static final String[] GRADE_NAME = {"C", "B", "A", "S", "S+"};
    private static final String[] BASE_KINDS = {"Room", "Puzzle", "Trap", "Miniboss"};
    private static final String[] EXTRA_KINDS = {"Entrance", "Fairy", "Blood"};
    // same order in every room so chips are where you expect
    private static final String[] CATEGORY_ORDER = {"chest", "item", "bat", "wither", "key", "lever", "prince", "fairysoul", "superboom", "stonk", "aotv", "pearl"};

    private static final Pattern SECRETS_FOUND = Pattern.compile("Secrets Found: (\\d+)\\s*$");
    private static final Patterns.P ROOMS = Patterns.p("Score.ROOMS"), CLEARED = Patterns.p("Score.CLEARED"), SECRETS = Patterns.p("Score.SECRETS"), CRYPTS = Patterns.p("Score.CRYPTS"),
            PUZZLE_COUNT = Patterns.p("Score.PUZZLE_COUNT"), PUZZLE = Patterns.p("Score.PUZZLE");
    private DungeonMap() {}

    // from skyblocker fancy map: teammates in their class color
    private static void team(Ui ui, int gx, int gy) {
        if (!live() || !dev.zmuzy.sbc.dungeon.DungeonMarks.opt("dungeon_map", "team")) return;
        var mc = Minecraft.getInstance();
        if (mc.level == null) return;
        for (var p : mc.level.players()) {
            if (p == mc.player) continue;
            var member = dev.zmuzy.sbc.dungeon.DungeonRun.member(p.getGameProfile().name());
            if (member == null || member.dead()) continue;
            double x = (p.getX() - Dungeon.ORIGIN) / 32.0, z = (p.getZ() - Dungeon.ORIGIN) / 32.0;
            if (x < 0 || z < 0 || x >= Dungeon.CELLS || z >= Dungeon.CELLS) continue;
            int px = (gx + (int) (x * M_STEP) - 2) & ~1, py = (gy + (int) (z * M_STEP) - 2) & ~1;
            ui.rect(px, py, 4, 4, 0xFF000000);
            ui.rect(px + 1, py + 1, 2, 2, 0xFF000000 | dev.zmuzy.sbc.dungeon.DungeonMarks.color("dungeon_leap", member.dungeonClass().toLowerCase(Locale.ROOT), 0xFFFFFFFF));
        }
    }

    // --- what the card shows ---

    // cells as grid x/y, left = secrets left (-1 = none)
    private record Tile(String kind, Set<Long> cells, int left, boolean current) {
        boolean has(int i, int j) {
            return cells.contains(pack(i, j));
        }
    }

    private record Chip(String text, int color) {}

    // run numbers from sidebar + tab, -1 till known
    private record Stats(int cleared, int rooms, int roomsTotal, double secrets, int secretsFound, int crypts, int puzzlesDone, int puzzles) {}

    // levers, stonks, superbooms etc are ways to get to a secret, they dont count
    private static final Set<String> SECRET_CATEGORIES = Set.of("chest", "item", "bat", "wither");

    private record View(String title, String floor, String status, List<Tile> tiles, boolean inRoom, List<Chip> chips, int found, int total, boolean routed, int routeStep, int routeSteps, String routeType,
                        Stats stats, int score, String grade, double playerX, double playerZ) {
        boolean routeDone() {
            return routeStep >= routeSteps;
        }

        // meter line above the chips: route if theres one, else secrets
        boolean hasProgress() {
            return inRoom && (routed || total > 0);
        }
    }

    private static long pack(int i, int j) {
        return ((long) i << 32) | (j & 0xffffffffL);
    }

    private static Tile tile(String kind, int left, boolean current, int... cells) {
        Set<Long> set = new HashSet<>();
        for (int c = 0; c + 1 < cells.length; c += 2) set.add(pack(cells[c], cells[c + 1]));
        return new Tile(kind, set, left, current);
    }

    private static final View SAMPLE = new View("Cobble Wall Pillar", "F7", "",
            List.of(tile("Room", 4, false, 0, 4, 1, 4, 0, 5, 1, 5), tile("Room", 2, false, 2, 5), tile("Room", 0, false, 3, 5, 4, 5), tile("Puzzle", -1, false, 5, 5),
                    tile("Room", 5, false, 2, 4, 3, 4, 4, 4), tile("Room", 3, false, 0, 3, 0, 2, 1, 2), tile("Trap", 2, false, 1, 3), tile("Room", 3, true, 2, 3),
                    tile("Miniboss", -1, false, 3, 3), tile("Room", 1, false, 4, 3), tile("Room", 9, false, 5, 1, 5, 2, 5, 3, 5, 4), tile("Puzzle", -1, false, 2, 2), tile("Room", 0, false, 3, 2)),
            true, List.of(new Chip("1x1", ROOM_CHIP), new Chip("Chest 2", categoryColor("chest")), new Chip("Lever 1", categoryColor("lever"))), 0, 3, true, 1, 3, "interact",
            new Stats(64, 9, 14, 45.2, 38, 3, 1, 2), 287, "S", 2.44, 3.44);

    private static boolean live() {
        return "CATACOMBS".equals(GameState.island);
    }

    private static View view() {
        return live() ? liveView() : SAMPLE;
    }

    private static String kindOf(Dungeon.Def d) {
        String n = d.name().toLowerCase(Locale.ROOT);
        if (n.contains("entrance")) return "Entrance";
        if (n.contains("fairy")) return "Fairy";
        if (n.contains("blood")) return "Blood";
        return switch (d.kind()) {
            case "puzzle" -> "Puzzle";
            case "trap" -> "Trap";
            case "miniboss" -> "Miniboss";
            default -> "Room";
        };
    }

    private static int kindColor(String kind) {
        return switch (kind) {
            case "Entrance" -> 0xFF3FA34D;
            case "Fairy" -> 0xFFFF7ACD;
            case "Blood" -> 0xFFC0392B;
            case "Puzzle" -> 0xFFA66CE0;
            case "Trap" -> 0xFFEF9F27;
            case "Miniboss" -> 0xFFFFD36B;
            // a door seen on the map, the room behind it not opened yet
            case "Unknown" -> 0xFF4A4A4A;
            default -> 0xFF8A6A3C;
        };
    }

    private static int categoryColor(String category) {
        return switch (category) {
            case "chest" -> 0xFFEF9F27;
            case "item" -> 0xFF39D0FF;
            case "bat" -> 0xFF97C459;
            case "wither" -> 0xFFB0B0B8;
            case "lever" -> 0xFFF2F2F2;
            case "superboom", "stonk" -> 0xFFFF6B6B;
            case "fairysoul" -> 0xFFFF7ACD;
            case "key" -> 0xFFFFD36B;
            default -> 0xFFB070FF;
        };
    }

    private static String categoryName(String category) {
        return switch (category) {
            case "wither" -> "Wither";
            case "aotv" -> "Teleport";
            case "fairysoul" -> "Fairy soul";
            default -> category.isEmpty() ? "Secret" : Character.toUpperCase(category.charAt(0)) + category.substring(1);
        };
    }

    private static int secretCount(Dungeon.Def d) {
        return (int) d.secrets().stream().filter(s -> SECRET_CATEGORIES.contains(s.category())).count();
    }

    // "done" also has the helper spots + each ones place in the list (from 1)
    private static int secretsFound(Dungeon.Placed r) {
        int found = 0;
        List<Dungeon.Secret> secrets = r.def.secrets();
        for (int i : r.done) if (i >= 1 && i <= secrets.size() && SECRET_CATEGORIES.contains(secrets.get(i - 1).category())) found++;
        return found;
    }

    private static boolean secretStep(Routes.Step step) {
        return !step.type().startsWith("exit") && !step.type().equals("dummy");
    }

    private static Tile tileOf(Dungeon.Placed r, Dungeon.Placed cur, boolean routes) {
        Set<Long> cells = new HashSet<>();
        for (long c : r.cells) cells.add(pack(Dungeon.index((int) (c >> 32)), Dungeon.index((int) c)));
        int total = secretCount(r.def), secretSteps = 0, stepsLeft = 0;
        List<Routes.Step> steps = routes ? Routes.steps(r.def.id()) : List.of();
        for (int i = 0; i < steps.size(); i++) {
            if (!secretStep(steps.get(i))) continue;
            secretSteps++;
            if (i >= r.step) stepsLeft++;
        }
        // with a route we follow steps, the walk-up count stays put
        int left = secretSteps > 0 ? stepsLeft : total > 0 ? Math.max(0, total - secretsFound(r)) : -1;
        return new Tile(kindOf(r.def), cells, left, r == cur);
    }

    // whats left in the room by kind, after a tag for what room it is, then the helper spots
    // with a route the count comes from the route steps
    private static List<Chip> chipsOf(Dungeon.Placed r, List<Routes.Step> steps) {
        List<Chip> out = new ArrayList<>();
        String kind = kindOf(r.def);
        out.add(new Chip(kind.equals("Room") ? r.def.kind() : kind, kind.equals("Room") ? ROOM_CHIP : kindColor(kind)));
        Map<String, Integer> left = new HashMap<>(), helpers = new HashMap<>();
        List<Dungeon.Secret> secrets = r.def.secrets();
        int total = 0;
        for (int i = 0; i < secrets.size(); i++) {
            String c = secrets.get(i).category();
            if (c.equals("entrance")) continue;
            if (!SECRET_CATEGORIES.contains(c)) {
                helpers.merge(c, 1, Integer::sum);
                continue;
            }
            total++;
            if (steps.isEmpty() && !r.done.contains(i + 1)) left.merge(c, 1, Integer::sum);
        }
        int stepsTotal = 0;
        for (int i = 0; i < steps.size(); i++) {
            if (!secretStep(steps.get(i))) continue;
            stepsTotal++;
            if (i >= r.step) left.merge(steps.get(i).type(), 1, Integer::sum);
        }
        if (total == 0 && stepsTotal == 0) out.add(new Chip("No secrets", Modern.DIM));
        else if (left.isEmpty()) out.add(new Chip("All found", Modern.GOOD));
        for (String c : CATEGORY_ORDER) {
            Integer n = left.remove(c);
            if (n != null) out.add(new Chip(categoryName(c) + " " + n, categoryColor(c)));
        }
        for (Map.Entry<String, Integer> e : left.entrySet()) out.add(new Chip(categoryName(e.getKey()) + " " + e.getValue(), categoryColor(e.getKey())));
        for (Map.Entry<String, Integer> e : helpers.entrySet()) out.add(new Chip(categoryName(e.getKey()) + " " + e.getValue(), Modern.DIM));
        return out;
    }

    private static View liveView() {
        var cur = Dungeon.current();
        boolean routes = Features.on("dungeon_routes"), score = scoreRow() > 0;
        List<Tile> tiles = new ArrayList<>();
        for (var r : Dungeon.rooms()) tiles.add(tileOf(r, cur, routes));
        // what hypixels map in slot 9 shows that we havent stood in yet (the team opened it)
        Set<Long> known = new HashSet<>();
        for (Tile t : tiles) known.addAll(t.cells());
        for (var r : dev.zmuzy.sbc.dungeon.MapRooms.rooms()) {
            Set<Long> cells = new HashSet<>();
            for (int[] c : r.cells()) cells.add(pack(c[0], c[1]));
            if (cells.stream().anyMatch(known::contains)) continue;
            tiles.add(new Tile(r.kind(), cells, -1, false));
            known.addAll(cells);
        }
        List<Routes.Step> steps = cur == null || !routes ? List.of() : Routes.steps(cur.def.id());
        int step = cur == null ? 0 : cur.step;
        String type = step < steps.size() ? steps.get(step).type() : "";
        int stepsDone = 0, secretSteps = 0;
        for (int i = 0; i < steps.size(); i++) {
            if (!secretStep(steps.get(i))) continue;
            secretSteps++;
            if (i < step) stepsDone++;
        }
        double px = -1, pz = -1;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            px = (mc.player.getX() - Dungeon.ORIGIN) / 32.0;
            pz = (mc.player.getZ() - Dungeon.ORIGIN) / 32.0;
        }
        return new View(cur != null ? cur.def.name() : "Dungeon", Score.floor, Dungeon.status, tiles, cur != null, cur != null ? chipsOf(cur, steps) : List.of(),
                cur == null ? 0 : secretsFound(cur), cur == null ? 0 : secretCount(cur.def), !steps.isEmpty(), stepsDone, secretSteps, type, stats(), score ? Score.score : -1, score ? Score.grade() : "", px, pz);
    }

    private static long statsAt;
    private static Stats statsRead;

    private static Matcher find(List<String> lines, Pattern p) {
        for (String l : lines) {
            Matcher m = p.matcher(l);
            if (m.find()) return m;
        }
        return null;
    }

    // read a few times a sec, they dont change faster
    private static Stats stats() {
        long now = System.currentTimeMillis();
        if (statsRead != null && now - statsAt < 500) return statsRead;
        statsAt = now;
        Matcher cleared = find(GameState.scoreboard, CLEARED.pattern()), rooms = find(GameState.tab, ROOMS.pattern()), secrets = find(GameState.tab, SECRETS.pattern()), secretCount = find(GameState.tab, SECRETS_FOUND), crypts = find(GameState.tab, CRYPTS.pattern()), pc = find(GameState.tab, PUZZLE_COUNT.pattern());
        int puzzles = pc == null ? -1 : Integer.parseInt(pc.group(1)), done = 0, counted = 0;
        if (puzzles > 0) {
            for (String l : GameState.tab) {
                if (counted >= puzzles) break;
                Matcher m = PUZZLE.pattern().matcher(l);
                if (m.find()) {
                    counted++;
                    if (!m.group(1).equals("✖") && !m.group(1).equals("✦")) done++;
                }
            }
        }
        int clearedPct = cleared == null ? -1 : Integer.parseInt(cleared.group(1)), done2 = rooms == null ? -1 : Integer.parseInt(rooms.group(1));
        // same guess the score makes: done / share of the map thats done
        int roomsTotal = done2 > 0 && clearedPct > 0 ? (int) Math.round(done2 / (clearedPct / 100.0)) : -1;
        return statsRead = new Stats(clearedPct, done2, roomsTotal, secrets == null ? -1 : Double.parseDouble(secrets.group(1)), secretCount == null ? -1 : Integer.parseInt(secretCount.group(1)), crypts == null ? -1 : Integer.parseInt(crypts.group(1)), done, puzzles);
    }

    // --- sizes ---

    public static int width() {
        return Modern.PAD + M_GRID + M_GUTTER + M_SIDE + Modern.PAD;
    }

    public static int height() {
        View v = view();
        ElementStyle st = ElementStyle.of(ID);
        int h = (titled(st) ? Modern.BAND + 2 : 4) + Math.max(M_GRID, sideHeight(v)) + 4;
        h += roomBlock(v);
        if (scoreKept(v)) h += Modern.RULE_BLOCK + Modern.ROW + Modern.METER_BLOCK;
        return h;
    }

    // room part keeps its size room to room, otherwise everything under the map jumps as you walk
    private static int roomBlock(View v) {
        return Modern.ROW + Modern.METER_BLOCK + Math.max(2, v.inRoom() ? chipLines(v.chips()).size() : 0) * CHIP_ROW;
    }

    private static boolean scoreKept(View v) {
        return v.score() >= 0 || Features.on("dungeon_score");
    }

    private static int scoreRow() {
        return Features.on("dungeon_score") && Score.started ? 11 : 0;
    }

    private static boolean titled(ElementStyle st) {
        return st.showTitle() == null || st.showTitle();
    }

    private static List<String> kinds(View v) {
        List<String> out = new ArrayList<>(List.of(BASE_KINDS));
        for (String k : EXTRA_KINDS) if (count(v, k) > 0) out.add(k);
        return out;
    }

    private static int count(View v, String kind) {
        int n = 0;
        for (Tile t : v.tiles()) if (t.kind().equals(kind)) n++;
        return n;
    }

    private static int sideHeight(View v) {
        return kinds(v).size() * Modern.ROW + Modern.RULE_BLOCK + 4 * STAT_ROW;
    }

    private static List<List<Chip>> chipLines(List<Chip> chips) {
        int room = width() - 2 * Modern.PAD;
        List<List<Chip>> lines = new ArrayList<>();
        List<Chip> line = new ArrayList<>();
        int used = 0;
        for (Chip c : chips) {
            int w = Ui.MEASURE.width(c.text()) + 6 + 3;
            if (used + w - 3 > room && !line.isEmpty()) {
                lines.add(line);
                line = new ArrayList<>();
                used = 0;
            }
            line.add(c);
            used += w;
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }

    // no dungeon around = editor and pictures show the sample, not an empty box
    private static boolean sampleShown() {
        HudElement e = HudLayout.element(ID);
        return e != null && e.demo;
    }

    public static boolean active() {
        return live() || sampleShown();
    }

    private static int color(Dungeon.Def d) {
        String n = d.name().toLowerCase(java.util.Locale.ROOT);
        if (n.contains("entrance")) return 0xFF3FA34D;
        if (n.contains("fairy")) return 0xFFFF7ACD;
        if (n.contains("blood")) return 0xFFC0392B;
        return switch (d.kind()) {
            case "puzzle" -> 0xFFA66CE0;
            case "trap" -> 0xFFEF9F27;
            case "miniboss" -> 0xFFFFD36B;
            default -> 0xFF8A6A3C;
        };
    }

    // --- classic card ---

    // room in one piece, cells that belong together get joined across the gap
    private static void fill(Ui ui, Tile t, int gx, int gy, int inset, int color) {
        for (int i = 0; i < Dungeon.CELLS; i++) {
            for (int j = 0; j < Dungeon.CELLS; j++) {
                if (!t.has(i, j)) continue;
                int cx = gx + i * M_STEP, cy = gy + j * M_STEP;
                boolean r = t.has(i + 1, j), d = t.has(i, j + 1), l = t.has(i - 1, j), u = t.has(i, j - 1);
                int x0 = cx + (l ? 0 : inset), y0 = cy + (u ? 0 : inset);
                ui.rect(x0, y0, cx + M_CELL - (r ? 0 : inset) - x0, cy + M_CELL - (d ? 0 : inset) - y0, color);
                if (r) {
                    int from = cy + (u && t.has(i + 1, j - 1) ? 0 : inset), to = cy + M_CELL - (d && t.has(i + 1, j + 1) ? 0 : inset);
                    ui.rect(cx + M_CELL, from, M_GAP, to - from, color);
                }
                if (d) {
                    int from = cx + (l && t.has(i - 1, j + 1) ? 0 : inset), to = cx + M_CELL - (r && t.has(i + 1, j + 1) ? 0 : inset);
                    ui.rect(from, cy + M_CELL, to - from, M_GAP, color);
                }
                if (r && d && t.has(i + 1, j + 1)) ui.rect(cx + M_CELL, cy + M_CELL, M_GAP, M_GAP, color);
            }
        }
    }

    // lit top + shaded bottom along the outer edges, no breaks at the gaps
    private static void light(Ui ui, Tile t, int gx, int gy, int inset, int color) {
        int top = Modern.lighten(color, 0.22f), bottom = Modern.darken(color, 0.28f);
        for (int i = 0; i < Dungeon.CELLS; i++) {
            for (int j = 0; j < Dungeon.CELLS; j++) {
                if (!t.has(i, j)) continue;
                int cx = gx + i * M_STEP, cy = gy + j * M_STEP;
                boolean r = t.has(i + 1, j);
                int from = cx + (t.has(i - 1, j) ? 0 : inset), to = cx + M_CELL - (r ? 0 : inset);
                if (!t.has(i, j - 1)) ui.rect(from, cy + inset, to + (r && !t.has(i + 1, j - 1) ? M_GAP : 0) - from, 1, top);
                if (!t.has(i, j + 1)) ui.rect(from, cy + M_CELL - 1 - inset, to + (r && !t.has(i + 1, j + 1) ? M_GAP : 0) - from, 1, bottom);
            }
        }
    }

    // secrets left in the middle of the room. your room says it below and your dot goes there
    private static void mark(Ui ui, Tile t, int gx, int gy) {
        if (t.left() < 0 || t.current() || t.cells().isEmpty() || !dev.zmuzy.sbc.dungeon.DungeonMarks.opt("dungeon_map", "secret_counts")) return;
        double mi = 0, mj = 0;
        for (long c : t.cells()) {
            mi += (int) (c >> 32);
            mj += (int) c;
        }
        mi /= t.cells().size();
        mj /= t.cells().size();
        int px = gx + (int) Math.round(mi * M_STEP) + M_CELL / 2, py = gy + (int) Math.round(mj * M_STEP) + M_CELL / 2;
        String s = t.left() == 0 ? "✔" : String.valueOf(t.left());
        ui.textShadow(s, px - ui.width(s) / 2 + 1, py - 4, t.left() == 0 ? Modern.GOOD : Modern.INK);
    }

    // tight enough that 4 of them line up with the map
    private static void statRow(Ui ui, String label, String value, double pct, int color, int x, int y, int w, int labelColor, int valueColor) {
        Modern.row(ui, label, value, x, y, w, labelColor, valueColor);
        Modern.meter(ui, x, y + 9, w, Modern.METER, Math.max(0, pct), pct >= 1 ? Modern.GOOD : color);
    }

    private static String fractionWithPercent(int now, int of) {
        return Modern.fraction(String.valueOf(now), String.valueOf(of), now >= of ? 'a' : 'f', 'f') + " §7" + RowText.percent((double) now / of);
    }

    private static String bandRight(View v) {
        int n = v.tiles().size();
        return "§7" + n + (n == 1 ? " room" : " rooms") + (v.floor().isEmpty() ? "" : "  §f" + v.floor());
    }

    private static int scoreColor(int score) {
        return score >= 300 ? Modern.GOOD : score >= 270 ? Modern.AQUA : score >= 230 ? Modern.GOLD : Modern.LABEL;
    }

    private static void drawClassic(Ui ui, GuiGraphicsExtractor g, int x, int y) {
        ElementStyle st = ElementStyle.of(ID);
        var look = Looks.of(ID);
        View v = view();
        int w = width(), h = height(), accent = st.titleColor() != null ? st.titleColor() : look.accent();
        int labelColor = ElementStyle.or(st.textColor(), Modern.LABEL), valueColor = ElementStyle.or(st.valueColor(), Modern.INK);
        Theme.current().panel(ui, x, y, w, h, st);
        int top;
        if (titled(st)) top = y + Modern.band(ui, v.title(), look.stack(), bandRight(v), accent, x, y, w);
        else {
            Modern.edge(ui, x, y, h, accent);
            top = y + 4;
        }

        int gx = x + Modern.PAD, gy = top;
        // in a run: hypixels own map from slot 9 with your arrow. the editor and pictures get the drawn grid
        ui.rect(gx, gy, M_GRID, M_GRID, CELL_EMPTY);
        if (sampleShown() || !dev.zmuzy.sbc.dungeon.HypixelMap.draw(g, gx, gy, M_GRID)) drawGrid(ui, v, gx, gy);
        sideAndRoom(ui, v, x, y, w, gx, gy, accent, labelColor, valueColor);
    }

    private static void drawGrid(Ui ui, View v, int gx, int gy) {
        for (int i = 0; i < Dungeon.CELLS; i++)
            for (int j = 0; j < Dungeon.CELLS; j++) ui.rect(gx + i * M_STEP, gy + j * M_STEP, M_CELL, M_CELL, CELL_EMPTY);
        for (Tile t : v.tiles()) {
            int col = kindColor(t.kind());
            if (t.current()) {
                fill(ui, t, gx, gy, 0, Modern.INK);
                fill(ui, t, gx, gy, 1, col);
                light(ui, t, gx, gy, 1, col);
            } else {
                fill(ui, t, gx, gy, 0, col);
                light(ui, t, gx, gy, 0, col);
            }
        }
        for (Tile t : v.tiles()) mark(ui, t, gx, gy);
        if (v.playerX() >= 0 && v.playerZ() >= 0 && v.playerX() < Dungeon.CELLS && v.playerZ() < Dungeon.CELLS) {
            int px = (gx + (int) (v.playerX() * M_STEP) - 2) & ~1, py = (gy + (int) (v.playerZ() * M_STEP) - 2) & ~1;
            ui.rect(px, py, 4, 4, 0xFF000000);
            ui.rect(px + 1, py + 1, 2, 2, Modern.AQUA);
        }
        team(ui, gx, gy);
    }

    private static void sideAndRoom(Ui ui, View v, int x, int y, int w, int gx, int gy, int accent, int labelColor, int valueColor) {
        // next to it: legend (= count of each kind) + run numbers
        int sx = gx + M_GRID + M_GUTTER, sy = gy;
        String here = null;
        for (Tile t : v.tiles()) if (t.current()) here = t.kind();
        for (String kind : kinds(v)) {
            int n = count(v, kind), col = kindColor(kind);
            if (kind.equals(here)) ui.gradH(sx - 2, sy - 1, M_SIDE + 2, Modern.ROW - 1, Ui.alpha(col, 0.5f), Ui.alpha(col, 0.05f));
            ui.rect(sx, sy + 1, 6, 6, col);
            ui.rect(sx, sy + 1, 6, 1, Modern.lighten(col, 0.22f));
            Modern.row(ui, kind, String.valueOf(n), sx + 8, sy, M_SIDE - 8, n > 0 ? labelColor : Modern.DIM, n > 0 ? valueColor : Modern.DIM);
            sy += Modern.ROW;
        }
        Modern.rule(ui, sx, sy + 2, M_SIDE);
        sy += Modern.RULE_BLOCK;
        Stats s = v.stats();
        statRow(ui, "Cleared", s.cleared() < 0 ? "-" : s.roomsTotal() > 0 ? s.rooms() + "/" + s.roomsTotal() + " " + s.cleared() + "%" : s.cleared() + "%", s.cleared() / 100.0, Modern.AQUA, sx, sy, M_SIDE, labelColor, valueColor);
        sy += STAT_ROW;
        statRow(ui, "Secrets", s.secrets() < 0 ? "-" : (s.secretsFound() >= 0 ? s.secretsFound() + " " : "") + RowText.percent(s.secrets() / 100.0), s.secrets() / 100.0, Modern.GOLD, sx, sy, M_SIDE, labelColor, valueColor);
        sy += STAT_ROW;
        // score only counts 5 crypts max
        statRow(ui, "Crypts", s.crypts() < 0 ? "-" : s.crypts() > 5 ? "§a" + s.crypts() : fractionWithPercent(s.crypts(), 5), s.crypts() / 5.0, 0xFFFF8F6B, sx, sy, M_SIDE, labelColor, valueColor);
        sy += STAT_ROW;
        statRow(ui, "Puzzles", s.puzzles() < 0 ? "-" : s.puzzles() == 0 ? "0" : fractionWithPercent(s.puzzlesDone(), s.puzzles()), s.puzzles() <= 0 ? -1 : (double) s.puzzlesDone() / s.puzzles(), kindColor("Puzzle"), sx, sy, M_SIDE, labelColor, valueColor);

        // under it: the room youre in
        int cx = x + Modern.PAD, cw = w - 2 * Modern.PAD, cy = gy + Math.max(M_GRID, sideHeight(v)) + 4;
        int roomEnd = cy + roomBlock(v);
        if (v.hasProgress()) {
            String value;
            double progress;
            int barColor;
            if (v.routed()) {
                progress = v.routeDone() ? 1 : (double) v.routeStep() / v.routeSteps();
                value = v.routeDone() ? "§aDone" : Modern.fraction(String.valueOf(v.routeStep()), String.valueOf(v.routeSteps()), 'a', 'f') + (v.routeType().isEmpty() ? "" : "  §b" + v.routeType()) + "  §7" + RowText.percent(progress);
                barColor = accent;
            } else {
                progress = (double) v.found() / v.total();
                value = fractionWithPercent(v.found(), v.total());
                barColor = Modern.GOLD;
            }
            Modern.row(ui, v.routed() ? "Route" : "Room secrets", value, cx, cy, cw, labelColor, valueColor);
            cy += Modern.ROW;
            Modern.meter(ui, cx, cy - 1, cw, Modern.METER, progress, progress >= 1 ? Modern.GOOD : barColor);
            cy += Modern.METER_BLOCK;
        }
        if (v.inRoom()) {
            for (List<Chip> line : chipLines(v.chips())) {
                int mx = cx;
                for (Chip c : line) mx += Modern.chip(ui, mx, cy + 1, c.text(), c.color()) + 3;
                cy += CHIP_ROW;
            }
        } else if (!v.status().isEmpty()) {
            ui.textShadow(ui.ellipsize(v.status(), cw), cx, cy, Modern.WARN);
            cy += Modern.ROW;
        }

        cy = roomEnd;

        // score vs the 300 that counts, marks at A and S
        if (v.score() < 0 && scoreKept(v)) {
            Modern.rule(ui, cx, cy + 2, cw);
            cy += Modern.RULE_BLOCK;
            Modern.row(ui, "Score", "-", cx, cy, cw, labelColor, Modern.DIM);
        } else if (v.score() >= 0) {
            Modern.rule(ui, cx, cy + 2, cw);
            cy += Modern.RULE_BLOCK;
            int sc = v.score(), col = scoreColor(sc);
            String value = sc > 300 ? "§a" + sc : Modern.fraction(String.valueOf(sc), "300", sc >= 300 ? 'a' : 'f', 'f') + "  §7" + RowText.percent(sc / 300.0);
            int vw = ui.width(value);
            ui.textShadow(value, cx + cw - vw, cy, valueColor);
            ui.textShadow("Score", cx, cy, labelColor);
            int after = cx + ui.width("Score") + 4;
            after += Modern.chip(ui, after, cy, v.grade(), col) + 4;
            for (int i = 0; i < GRADE_AT.length; i++) {
                if (sc >= GRADE_AT[i]) continue;
                String next = "§7+" + (GRADE_AT[i] - sc) + " to " + GRADE_NAME[i];
                if (after + ui.width(next) + 4 <= cx + cw - vw) ui.textShadow(next, after, cy, Modern.DIM);
                break;
            }
            cy += Modern.ROW;
            Modern.meter(ui, cx, cy - 1, cw, Modern.METER, sc / 300.0, col);
            for (int mark : new int[]{230, 270}) ui.rect(cx + cw * mark / 300, cy - 2, 1, 4, 0xCCFFFFFF);
        }
    }

    // --- both ---

    public static void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
        drawClassic(ui, g, x, y);
    }
}
