// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mining;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashSet;
import java.util.Set;

// from skyblocker CarpetHighlighter: the unbreakable carpets in dwarven mines ore veins (gray, light blue or
// light gray carpet on a sea lantern)
// looked for around you every 15 ticks, kept once found
public final class Carpets {
    static final String ID = "mining_carpets";
    private static final int RADIUS = 15, EVERY = 15;
    private static final Set<BlockPos> FOUND = new HashSet<>();
    private static int ticks;

    private Carpets() {}

    private static boolean carpet(Minecraft mc, BlockPos pos) {
        BlockState st = mc.level.getBlockState(pos);
        if (!(st.is(Blocks.CARPET.gray()) || st.is(Blocks.CARPET.lightBlue()) || st.is(Blocks.CARPET.lightGray()))) return false;
        return mc.level.getBlockState(pos.below()).is(Blocks.SEA_LANTERN);
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null || ++ticks < EVERY) return;
        ticks = 0;
        for (BlockPos p : BlockPos.withinManhattan(mc.player.blockPosition(), RADIUS)) if (carpet(mc, p)) FOUND.add(p.immutable());
    }

    private static void draw(WorldDraw w) {
        int color = MiningPack.color(ID, "mining_carpets.color", 0x4CFF0000);
        float alpha = ((color >>> 24) & 0xFF) / 255f;
        for (BlockPos p : FOUND) w.filledBox(new AABB(p.getX(), p.getY(), p.getZ(), p.getX() + 1, p.getY() + 1 / 16.0, p.getZ() + 1), color & 0xFFFFFF, alpha, false);
    }

    public static void register() {
        Features.add(new Feature(ID, "Mining", "Unbreakable carpets", "Marks the carpets in ore veins of the Dwarven Mines that cannot be mined. Only what you can already see.", "Skyblocker: Unbreakable Carpet Highlighter")
                .when(() -> "DWARVEN_MINES".equals(GameState.island)).tick(Carpets::tick).world(Carpets::draw).left(FOUND::clear)
                .option(Option.color("mining_carpets.color", "Colour", "", 0x4CFF0000)));
    }
}
