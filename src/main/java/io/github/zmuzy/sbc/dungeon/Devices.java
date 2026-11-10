// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.dungeon;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// f7 boss devices, display only: simon says (next buttons), lights on (levers still off), arrow align (clicks
// per frame), target practice (targets already hit)
public final class Devices {
    private Devices() {}

    private static boolean near(Minecraft mc, double x, double y, double z, double r) {
        return mc.player != null && mc.level != null && mc.player.position().distanceToSqr(x, y, z) < r * r;
    }

    // ---- simon says ----

    private static final BlockPos START_BUTTON = new BlockPos(110, 121, 91);
    private static final List<BlockPos> pattern = new ArrayList<>();
    private static final Set<BlockPos> clicked = new HashSet<>();
    private static final Map<BlockPos, Boolean> wasLantern = new HashMap<>();
    private static final Map<BlockPos, Boolean> wasSolid = new HashMap<>();

    private static void simonReset() {
        pattern.clear();
        clicked.clear();
    }

    private static void tickSimon(Minecraft mc) {
        if (!near(mc, 110, 121, 93, 40)) return;
        for (int y = 120; y <= 123; y++)
            for (int z = 92; z <= 95; z++) {
                BlockPos board = new BlockPos(111, y, z);
                var b = mc.level.getBlockState(board).getBlock();
                boolean lantern = b == Blocks.SEA_LANTERN;
                if (Boolean.TRUE.equals(wasLantern.get(board)) && b == Blocks.OBSIDIAN) pattern.add(board);
                wasLantern.put(board, lantern);
                BlockPos button = new BlockPos(110, y, z);
                boolean solid = !mc.level.getBlockState(button).isAir();
                if (Boolean.TRUE.equals(wasSolid.get(button)) && !solid) simonReset();
                wasSolid.put(button, solid);
            }
    }

    private static void drawSimon(WorldDraw w) {
        if (!near(w.mc, 110, 121, 93, 40)) return;
        int drawn = 0;
        for (BlockPos p : pattern) {
            BlockPos button = p.west();
            if (clicked.contains(button) || !w.mc.level.getBlockState(button).is(Blocks.STONE_BUTTON)) continue;
            int col = drawn == 0 ? 0xFF33D17A : 0xFFFFD36B;
            w.box(new AABB(button), col, true, true);
            if (++drawn == 2) return;
        }
    }

    // ---- lights on ----

    private static final BlockPos[] LEVERS = {new BlockPos(62, 136, 142), new BlockPos(58, 136, 142), new BlockPos(60, 135, 142),
            new BlockPos(60, 134, 142), new BlockPos(62, 133, 142), new BlockPos(58, 133, 142)};

    private static void drawLights(WorldDraw w) {
        if (!near(w.mc, 60, 135, 142, 40)) return;
        for (BlockPos p : LEVERS) {
            BlockState s = w.mc.level.getBlockState(p);
            if (s.getBlock() == Blocks.LEVER && s.hasProperty(BlockStateProperties.POWERED) && !s.getValue(BlockStateProperties.POWERED))
                w.box(new AABB(p), 0xFFFF3B3B, true, false);
        }
    }

    // ---- arrow align ----

    private static final BlockPos LEFT_TOP = new BlockPos(-2, 124, 79);
    private static final AABB FRAMES = new AABB(-3, 120, 75, -1, 125, 80);
    private static final int X = -1, S = -2, E = -3, U = 7, D = 3, L = 5, R = 1;
    private static final List<int[]> SOLUTIONS = List.of(
            new int[]{1, 1, 3, -1, -1, 7, -1, 3, -1, -1, -2, -1, 3, -1, -3, -1, -1, 3, -1, 7, -1, -1, 1, 1, 7},
            new int[]{1, 1, -3, 5, 5, 7, -1, -1, -1, 7, 7, 5, -1, 1, 7, -1, 7, -1, 7, -1, -1, -2, -1, -2, -1},
            new int[]{3, 5, 5, -1, -1, 3, -1, 7, -1, -1, -3, -1, -2, -1, -3, -1, -1, 3, -1, 7, -1, -1, 1, 1, 7},
            new int[]{-2, -1, -1, -1, -2, 3, -1, -1, -1, 3, 3, -1, -3, -1, 3, 3, -1, 7, -1, 3, 1, 1, 7, 5, 5},
            new int[]{1, 1, 1, 1, 3, 7, -1, -1, -1, 3, 7, -1, -3, -1, 3, 7, -1, 7, -1, 3, -2, -1, 7, 5, 5},
            new int[]{1, 1, 3, -1, -3, 7, -1, 3, -1, 7, 7, -1, 3, -1, 7, 7, -1, 3, -1, 7, -2, -1, 1, 1, 7},
            new int[]{-2, 1, 1, 1, -3, -1, -1, -1, -1, -1, -2, 1, 1, 1, -3, -1, -1, -1, -1, -1, -2, 1, 1, 1, -3},
            new int[]{-1, 1, 1, 3, -1, -1, 7, -1, 3, -1, -1, 7, -1, 3, -1, -1, 7, -1, 3, -1, -2, 7, -1, 1, -3},
            new int[]{-2, 1, 3, -1, -1, -1, -1, 1, 1, -3, -2, 1, 7, -1, -1, -1, -1, 1, 1, -3, -2, 1, 7, -1, -1});

    private static int[] solution;
    private static boolean noSolution;

    private static boolean itemIs(ItemFrame f, String path) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(f.getItem().getItem()).getPath().equals(path);
    }

    private static int index(BlockPos p) {
        return (LEFT_TOP.getY() - p.getY()) * 5 + LEFT_TOP.getZ() - p.getZ();
    }

    private static void findSolution(Minecraft mc) {
        List<ItemFrame> frames = mc.level.getEntitiesOfClass(ItemFrame.class, FRAMES, f -> true);
        Optional<int[]> found = SOLUTIONS.stream().filter(sol -> {
            for (ItemFrame f : frames) {
                int i = index(f.blockPosition());
                if (i < 0 || i >= sol.length) return false;
                switch (sol[i]) {
                    case E -> {
                        if (!itemIs(f, "red_wool")) return false;
                    }
                    case S -> {
                        if (!itemIs(f, "lime_wool")) return false;
                    }
                    case X -> {
                        return false;
                    }
                    default -> {
                        if (!itemIs(f, "arrow")) return false;
                    }
                }
            }
            return true;
        }).findAny();
        solution = found.orElse(null);
        noSolution = found.isEmpty();
    }

    private static void drawArrows(WorldDraw w) {
        var mc = w.mc;
        if (!near(mc, FRAMES.getCenter().x, FRAMES.getCenter().y, FRAMES.getCenter().z, 8)) {
            solution = null;
            noSolution = false;
            return;
        }
        if (solution == null && !noSolution) findSolution(mc);
        if (noSolution || solution == null) return;
        for (ItemFrame f : mc.level.getEntitiesOfClass(ItemFrame.class, FRAMES, x -> true)) {
            int i = index(f.blockPosition());
            if (i < 0 || i >= solution.length) continue;
            int expect = solution[i];
            if (expect < 0) continue;
            int remaining = (expect + 8 - f.getRotation()) % 8;
            if (remaining > 0) w.label((remaining <= 2 ? "§a" : remaining <= 4 ? "§e" : "§c") + remaining, f.position().add(0.3, 0, 0), false);
        }
    }

    // ---- target practice ----

    private static final BlockPos PLATE = new BlockPos(63, 127, 35);
    private static final List<BlockPos> TARGETS = new ArrayList<>();
    private static final List<BlockPos> hit = new ArrayList<>();
    private static final Map<BlockPos, Boolean> wasEmerald = new HashMap<>();
    private static int plateWas = -1;

    static {
        for (int x : new int[]{64, 66, 68}) for (int y : new int[]{126, 128, 130}) TARGETS.add(new BlockPos(x, y, 50));
    }

    private static void tickTargets(Minecraft mc) {
        if (!near(mc, 66, 128, 45, 40)) return;
        BlockState plate = mc.level.getBlockState(PLATE);
        if (plate.getBlock() == Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE) {
            int power = plate.getValue(BlockStateProperties.POWER);
            // from skyblocker TargetPractice.onBlockStateUpdate: reset when nobody stands on the plate (power < 1) or it
            // was just stepped on (old power 0)
            if (power != plateWas && (power < 1 || plateWas == 0)) hit.clear();
            plateWas = power;
        }
        for (BlockPos p : TARGETS) {
            var b = mc.level.getBlockState(p).getBlock();
            if (Boolean.TRUE.equals(wasEmerald.get(p)) && net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(b).getPath().equals("blue_terracotta")) hit.add(p);
            wasEmerald.put(p, b == Blocks.EMERALD_BLOCK);
        }
    }

    private static void drawTargets(WorldDraw w) {
        if (!near(w.mc, 66, 128, 45, 40)) return;
        for (BlockPos p : hit) w.box(new AABB(p), 0xFFFF3B3B, true, false);
    }

    public static void register() {
        java.util.function.BooleanSupplier inDungeon = () -> "CATACOMBS".equals(GameState.island) || SbcMod.DEV;
        Features.add(new Feature("dungeon_simon", "Dungeons", "Simon Says", "Marks the next two buttons of the Simon Says device.", "Skyblocker: simon says solver")
                .when(inDungeon).tick(Devices::tickSimon).world(Devices::drawSimon).left(() -> {
                    simonReset();
                    wasLantern.clear();
                    wasSolid.clear();
                }));
        Features.add(new Feature("dungeon_lightson", "Dungeons", "Lights On", "Marks the levers of the Lights On device that are still off.", "Skyblocker: lights on solver")
                .when(inDungeon).world(Devices::drawLights));
        Features.add(new Feature("dungeon_arrowalign", "Dungeons", "Arrow Align", "Shows how many clicks every frame of the Arrow Align device still needs.", "Skyblocker: arrow align solver")
                .when(inDungeon).world(Devices::drawArrows));
        Features.add(new Feature("dungeon_targets", "Dungeons", "Target Practice", "Marks the targets you have already hit.", "Skyblocker: target practice")
                .when(inDungeon).tick(Devices::tickTargets).world(Devices::drawTargets).left(() -> {
                    hit.clear();
                    wasEmerald.clear();
                }));
        Safe.useBlock("Devices.useBlock", (player, level, hand, hitResult) -> {
            try {
                if (Features.on("dungeon_simon")) {
                    BlockPos pos = hitResult.getBlockPos();
                    if (level.getBlockState(pos).getBlock() == Blocks.STONE_BUTTON) {
                        if (pos.getX() == 110 && pos.getY() >= 120 && pos.getY() <= 123 && pos.getZ() >= 92 && pos.getZ() <= 95) clicked.add(pos.immutable());
                        else if (pos.equals(START_BUTTON)) simonReset();
                    }
                }
            } catch (Exception e) {
                SbcMod.LOG.warn("simon says click", e);
            }
            return InteractionResult.PASS;
        });
    }
}
