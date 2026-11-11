// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// water board, display only, from skyblocker puzzle/waterboard: WaterboardOneFlow reads which of the 4
// layouts you got and which doors are closed, then shows the next lever + when
public final class Waterboard {
    private enum Lever {
        COAL(Blocks.COAL_BLOCK, 20, 61, 10, new int[][]{{0, -2, 0}, {2, -1, 1}, null, {5, -1, 0}}),
        GOLD(Blocks.GOLD_BLOCK, 20, 61, 15, new int[][]{{1, -1, 0}, {3, -2, 0}, {-4, -1, 1}, {1, 0, 0}}),
        QUARTZ(Blocks.QUARTZ_BLOCK, 20, 61, 20, new int[][]{{1, -4, 1}, {-1, 0, 0}, {1, 0, 0}, {-1, 0, 1}}),
        DIAMOND(Blocks.DIAMOND_BLOCK, 10, 61, 20, new int[][]{{0, -5, 1}, {-2, -1, 0}, {-1, 0, 1}, {-3, -4, 1}}),
        EMERALD(Blocks.EMERALD_BLOCK, 10, 61, 15, new int[][]{{-1, -10, 1}, {1, 0, 1}, {-6, 0, 0}, {1, -4, 0}}),
        TERRACOTTA(Blocks.TERRACOTTA, 10, 61, 10, new int[][]{{-1, -1, 1}, {0, -3, 1}, null, {-4, -5, 1}}),
        WATER(Blocks.LAVA, 15, 60, 5, null);

        final Block block;
        final int x, y, z;
        final int[][] initial;

        Lever(Block block, int x, int y, int z, int[][] initial) {
            this.block = block;
            this.x = x;
            this.y = y;
            this.z = z;
            this.initial = initial;
        }

        // skyblocker LeverType colors, the games DyeColor
        int color() {
            return switch (this) {
                case COAL -> DyeColor.RED.getTextureDiffuseColor();
                case GOLD -> DyeColor.YELLOW.getTextureDiffuseColor();
                case QUARTZ -> DyeColor.LIGHT_GRAY.getTextureDiffuseColor();
                case DIAMOND -> DyeColor.CYAN.getTextureDiffuseColor();
                case EMERALD -> DyeColor.LIME.getTextureDiffuseColor();
                case TERRACOTTA -> DyeColor.ORANGE.getTextureDiffuseColor();
                case WATER -> DyeColor.LIGHT_BLUE.getTextureDiffuseColor();
            };
        }

        static Lever ofBlock(Block b) {
            for (Lever l : values()) if (l.block == b) return l;
            return null;
        }

        static Lever at(int x, int y, int z) {
            for (Lever l : values()) if (l.x == x && l.y == y && l.z == z) return l;
            return null;
        }
    }

    private static final int[] ENTRANCE = {15, 78, 26};
    private static final int BOARD_MIN_X = 6, BOARD_MAX_X = 24, BOARD_MIN_Y = 58, BOARD_MAX_Y = 81, BOARD_Z = 26;
    // skyblocker only looks this far for a way down, and water flows this far max
    private static final int LOOK_DOWN = 5, MAX_FLOW = 7;
    private static JsonObject solutions;

    private static Dungeon.Placed room;
    private static int variant;
    private static String doors, initialDoors;
    private static EnumMap<Lever, List<Double>> solution;
    private static boolean finished, tried;
    private static long startMs, nowMs;

    private Waterboard() {}

    private static BlockPos actual(Dungeon.Placed r, int x, int y, int z) {
        Vec3 a = r.toActual(x, y, z);
        return BlockPos.containing(Math.floor(a.x), Math.floor(a.y), Math.floor(a.z));
    }

    private static void load() {
        if (solutions != null) return;
        try (var in = Waterboard.class.getResourceAsStream("/sbc/watertimes.json")) {
            solutions = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read water board solutions", e);
            solutions = new JsonObject();
        }
    }

    private static void reset() {
        variant = 0;
        doors = initialDoors = null;
        solution = null;
        finished = tried = false;
        startMs = nowMs = 0;
    }

    private static int findVariant(Minecraft mc) {
        Set<Lever> found = new HashSet<>();
        BlockPos a = actual(room, ENTRANCE[0] - 1, ENTRANCE[1] - 1, ENTRANCE[2]);
        BlockPos b = actual(room, ENTRANCE[0] + 1, ENTRANCE[1], ENTRANCE[2] + 1);
        for (BlockPos p : BlockPos.betweenClosed(a, b)) {
            Lever l = Lever.ofBlock(mc.level.getBlockState(p).getBlock());
            if (l != null) found.add(l);
        }
        if (found.contains(Lever.GOLD) && found.contains(Lever.TERRACOTTA)) return 1;
        if (found.contains(Lever.EMERALD) && found.contains(Lever.QUARTZ)) return 2;
        if (found.contains(Lever.QUARTZ) && found.contains(Lever.DIAMOND)) return 3;
        if (found.contains(Lever.GOLD) && found.contains(Lever.QUARTZ)) return 4;
        return 0;
    }

    private static String findDoors(Minecraft mc) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++)
            if (!mc.level.getBlockState(actual(room, 15, 57, 19 - i)).isAir()) sb.append(i);
        return sb.toString();
    }

    private static boolean noWater(Minecraft mc) {
        for (int x = 6; x <= 24; x++)
            for (int y = 58; y <= 81; y++)
                if (mc.level.getBlockState(actual(room, x, y, 26)).is(Blocks.WATER)) return false;
        return true;
    }

    private static boolean leverActive(Minecraft mc, Lever l) {
        int[] off = l.initial == null ? null : l.initial[variant - 1];
        if (off == null) return false;
        return !mc.level.getBlockState(actual(room, ENTRANCE[0] + off[0], ENTRANCE[1] + off[1], ENTRANCE[2] + off[2])).is(l.block);
    }

    private static EnumMap<Lever, List<Double>> empty() {
        EnumMap<Lever, List<Double>> m = new EnumMap<>(Lever.class);
        for (Lever l : Lever.values()) m.put(l, new ArrayList<>());
        return m;
    }

    private static void solve(Minecraft mc) {
        tried = true;
        variant = findVariant(mc);
        if (variant == 0) {
            finished = true;
            return;
        }
        initialDoors = findDoors(mc);
        doors = initialDoors;
        if (doors.isEmpty()) {
            solution = empty();
            finished = true;
            return;
        }
        if (doors.length() != 3) {
            say("§cDoors are in an unrecognized state. Make sure exactly three doors are closed, then run §e/sbc waterreset§c.");
            finished = true;
            return;
        }
        if (!noWater(mc)) {
            say("§cWater must be toggled off or it will interfere with the solution. Turn the water off and let it drain, then run §e/sbc waterreset§c.");
            finished = true;
            return;
        }
        JsonObject all = solutions.has(String.valueOf(variant)) ? solutions.getAsJsonObject(String.valueOf(variant)) : null;
        if (all == null || !all.has(doors)) {
            finished = true;
            return;
        }
        EnumMap<Lever, List<Double>> sol = empty();
        for (var e : all.getAsJsonObject(doors).entrySet()) {
            Lever l;
            try {
                l = Lever.valueOf(e.getKey().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                continue;
            }
            JsonArray arr = e.getValue().getAsJsonArray();
            List<Double> times = sol.get(l);
            for (var t : arr) times.add(t.getAsDouble());
        }
        for (Lever l : Lever.values()) {
            List<Double> times = sol.get(l);
            if (l != Lever.WATER && leverActive(mc, l)) {
                if (!times.isEmpty() && times.get(0) == 0.0) times.remove(0);
                else times.add(0, 0.0);
            }
        }
        solution = sol;
    }

    private static void say(String text) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[SBC] " + text));
    }

    // from skyblocker WaterboardOneFlow.onServerTick: times are server ticks, not ours
    private static void serverTick() {
        if (solution != null && Features.on("dungeon_water")) nowMs += 50;
    }

    private static void tick(Minecraft mc) {
        var r = Dungeon.current();
        if (r == null || !r.def.id().startsWith("water-puzzle") || mc.level == null || mc.player == null) {
            if (room != null) reset();
            room = null;
            return;
        }
        if (room != r) {
            reset();
            room = r;
        }
        if (!tried && mc.player.tickCount % 10 == 0) {
            load();
            solve(mc);
        }
    }

    private static InteractionResult onUse(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
        try {
            if (!Features.on("dungeon_water") || solution == null || room == null) return InteractionResult.PASS;
            BlockPos p = hit.getBlockPos();
            int[] rel = room.toRelative(p.getX(), p.getZ());
            Lever l = Lever.at(rel[0], p.getY(), rel[1]);
            if (l == null) return InteractionResult.PASS;
            List<Double> times = solution.get(l);
            if (startMs != 0 || l == Lever.WATER || (!times.isEmpty() && times.get(0) == 0.0)) {
                if (!times.isEmpty()) times.remove(0);
                if (startMs == 0 && l == Lever.WATER) startMs = nowMs;
            } else {
                times.add(0, 0.0);
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("water board click", e);
        }
        return InteractionResult.PASS;
    }

    private record Step(Lever lever, double time) {}

    // --- WaterboardPreviewer ---

    // one stretch of water in room coords
    record Stretch(BlockPos from, BlockPos to) {}

    // from skyblocker findWaterPathVertical + Horizontal: where the water runs, for any idea of what lets it through
    static List<Stretch> waterPath(java.util.function.Predicate<BlockPos> open) {
        List<Stretch> path = new ArrayList<>();
        BlockPos entrance = new BlockPos(ENTRANCE[0], ENTRANCE[1], ENTRANCE[2]);
        path.add(new Stretch(entrance.above(5), entrance.above(3)));
        down(entrance.above(3), path, open);
        return path;
    }

    private static void down(BlockPos root, List<Stretch> path, java.util.function.Predicate<BlockPos> open) {
        if (!open.test(root.below())) return;
        BlockPos tail = root.below();
        while (open.test(tail.below())) tail = tail.below();
        path.add(new Stretch(root, tail));
        sideways(tail, path, open);
    }

    private static void sideways(BlockPos root, List<Stretch> path, java.util.function.Predicate<BlockPos> open) {
        if (open.test(root.below())) return;
        BlockPos left = root;
        int leftSteps = 0;
        while (open.test(left.east()) && !open.test(left.below()) && leftSteps < MAX_FLOW) {
            left = left.east();
            leftSteps++;
        }
        BlockPos right = root;
        int rightSteps = 0;
        while (open.test(right.west()) && !open.test(right.below()) && rightSteps < MAX_FLOW) {
            right = right.west();
            rightSteps++;
        }
        // if one side has a way down closer to the source the water only goes that way, else both ways
        if (open.test(left.below()) && leftSteps <= LOOK_DOWN && (leftSteps < rightSteps || !open.test(right.below()))) {
            path.add(new Stretch(root, left));
            down(left, path, open);
        } else if (open.test(right.below()) && rightSteps <= LOOK_DOWN && (rightSteps < leftSteps || !open.test(left.below()))) {
            path.add(new Stretch(root, right));
            down(right, path, open);
        } else {
            if (leftSteps > 0) {
                path.add(new Stretch(root, left));
                down(left, path, open);
            }
            if (rightSteps > 0) {
                path.add(new Stretch(root, right));
                down(right, path, open);
            }
        }
    }

    // from skyblocker isWaterPassable, with the lever you look at already pulled
    private static boolean passable(Minecraft mc, BlockPos pos, Lever prospective) {
        if (pos.getX() < BOARD_MIN_X || pos.getX() > BOARD_MAX_X || pos.getY() < BOARD_MIN_Y || pos.getY() > BOARD_MAX_Y || pos.getZ() != BOARD_Z) return false;
        var state = mc.level.getBlockState(actual(room, pos.getX(), pos.getY(), pos.getZ()));
        boolean open = state.isAir() || state.is(Blocks.WATER);
        if (prospective == null) return open;
        var behind = mc.level.getBlockState(actual(room, pos.getX(), pos.getY(), pos.getZ() + 1));
        return open && !behind.is(prospective.block) || state.is(prospective.block);
    }

    // room x, z of a world point
    private static Vec3 relative(Vec3 p) {
        double dx = p.x - room.cx, dz = p.z - room.cz;
        return switch (room.dir) {
            case 0 -> new Vec3(dx, p.y, dz);
            case 1 -> new Vec3(dz, p.y, -dx);
            case 2 -> new Vec3(-dz, p.y, dx);
            default -> new Vec3(-dx, p.y, -dz);
        };
    }

    // from skyblocker findProspective: the toggle block on the board youre looking at
    private static Lever lookedAt(Minecraft mc) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 camera = relative(eye), look = relative(eye.add(mc.player.getLookAngle())).subtract(camera);
        if (look.z == 0) return null;
        Vec3 start = camera.add(look.scale((BOARD_Z - 1.5 - camera.z) / look.z)), end = camera.add(look.scale((BOARD_Z + 1.5 - camera.z) / look.z));
        Vec3 from = room.toActual(start.x, start.y, start.z), to = room.toActual(end.x, end.y, end.z);
        return net.minecraft.world.level.BlockGetter.traverseBlocks(from, to, null, (ignored, pos) -> {
            int[] rel = room.toRelative(pos.getX(), pos.getZ());
            if (rel[1] != BOARD_Z) return null;
            Lever l = Lever.ofBlock(mc.level.getBlockState(pos).getBlock());
            if (l != null) return l;
            return Lever.ofBlock(mc.level.getBlockState(actual(room, rel[0], pos.getY(), rel[1] + 1)).getBlock());
        }, ignored -> null);
    }

    private static boolean option(String key) {
        var o = Features.get("dungeon_water").option(key);
        return o != null && o.bool();
    }

    private static void drawPreview(WorldDraw w) {
        Minecraft mc = w.mc;
        if (mc.player == null || mc.level == null) return;
        boolean levers = option("dungeon_water.preview_levers"), water = option("dungeon_water.preview_path");
        if (!levers && !water) return;
        Lever prospective = levers ? lookedAt(mc) : null;
        if (water) {
            int colour = Lever.WATER.color() | 0xFF000000;
            for (Stretch s : waterPath(p -> passable(mc, p, prospective))) {
                Vec3 head = Vec3.atCenterOf(actual(room, s.from().getX(), s.from().getY(), s.from().getZ())), tail = Vec3.atCenterOf(actual(room, s.to().getX(), s.to().getY(), s.to().getZ()));
                if (prospective == null) {
                    w.line(head, tail, colour, 3f, true);
                    continue;
                }
                // what would happen, not what will: dashed
                Vec3 forward = tail.subtract(head).normalize();
                double distance = head.distanceTo(tail);
                for (int i = 0; i < distance; i++) {
                    w.line(head, head.add(forward.scale(0.3)), colour, 3f, true);
                    w.line(head.add(forward.scale(0.7)), head.add(forward), colour, 3f, true);
                    head = head.add(forward);
                }
            }
        }
        if (prospective == null) return;
        // from skyblocker extractProspectiveChanges: filled = what would come out, outlined = what would go back
        int rgb = prospective.color() & 0xFFFFFF;
        for (int x = BOARD_MIN_X; x <= BOARD_MAX_X; x++) {
            for (int y = BOARD_MIN_Y; y <= BOARD_MAX_Y; y++) {
                BlockPos active = actual(room, x, y, BOARD_Z), inactive = actual(room, x, y, BOARD_Z + 1);
                if (mc.level.getBlockState(active).is(prospective.block)) w.box(new net.minecraft.world.phys.AABB(active), rgb | 0xFF000000, false, true);
                else if (mc.level.getBlockState(inactive).is(prospective.block)) w.filledBox(new net.minecraft.world.phys.AABB(active), rgb, 0.8f, true);
            }
        }
    }

    private static void draw(WorldDraw w) {
        if (room == null) return;
        drawPreview(w);
        if (solution == null) return;
        List<Step> steps = new ArrayList<>();
        for (var e : solution.entrySet()) for (double t : e.getValue()) steps.add(new Step(e.getKey(), t));
        steps.sort(Comparator.comparingDouble((Step s) -> s.time + (s.lever == Lever.WATER ? 0.001 : 0)).thenComparingInt(s -> s.lever.ordinal()));
        Lever next = steps.isEmpty() ? null : steps.get(0).lever;
        Lever after = steps.size() < 2 ? null : steps.get(1).lever;
        if (next != null) {
            Vec3 from = w.eye.add(w.mc.player.getLookAngle().scale(1.5)).add(0, -0.3, 0);
            w.line(from, Vec3.atCenterOf(actual(room, next.x, next.y, next.z)), 0xFF33D17A, 4f, true);
            if (after != null)
                w.line(Vec3.atCenterOf(actual(room, next.x, next.y, next.z)), Vec3.atCenterOf(actual(room, after.x, after.y, after.z)), 0xFFF2F2F2, 2f, true);
        }
        for (var e : solution.entrySet()) {
            Lever l = e.getKey();
            List<Double> times = e.getValue();
            for (int i = 0; i < times.size(); i++) {
                double t = times.get(i);
                long remaining = startMs + (long) (t * 1000) - nowMs;
                String text;
                if (l == Lever.WATER && t == 0.0 && next != Lever.WATER) text = "§c§lWAIT";
                else if ((startMs != 0 || t != 0.0) && (startMs <= 0 || remaining > 0)) text = "§e" + String.format("%.2f", startMs == 0 ? t : remaining / 1000.0);
                else text = "§a§lCLICK";
                Vec3 at = Vec3.atCenterOf(actual(room, l.x, l.y, l.z)).add(0, 0.5 * (i + 1), 0);
                w.label(text, at, true);
            }
        }
    }

    public static void register() {
        java.util.function.BooleanSupplier inDungeon = () -> "CATACOMBS".equals(GameState.island) || SbcMod.DEV;
        Features.add(new Feature("dungeon_water", "Dungeons", "Water board", "Shows which lever to pull next and a countdown on every lever, where the water will run, and what a lever you look at would change.", "Skyblocker: waterboard solver")
                .when(inDungeon).tick(Waterboard::tick).world(Waterboard::draw)
                .left(() -> {
                    reset();
                    room = null;
                })
                .option(io.muzy.sbc.feature.Option.bool("dungeon_water.preview_path", "Show the water's path", "A line where the water runs. Dashed while you look at a lever.", true))
                .option(io.muzy.sbc.feature.Option.bool("dungeon_water.preview_levers", "Show what a lever changes", "Look at a block of the board: what comes out is filled, what goes back is outlined. Shows blocks behind the board.", false)));
        Safe.useBlock("Waterboard.onUse", Waterboard::onUse);
        io.muzy.sbc.commands.ServerStats.onTick(Waterboard::serverTick);
        io.muzy.sbc.core.Commands.describe("waterreset", "Solve the water board again from how it stands now");
        Safe.commands("Waterboard.commands", (d, b) -> d.register(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("sbc")
                .then(net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal("waterreset").executes(c -> {
                    reset();
                    return 1;
                }))));
    }
}
