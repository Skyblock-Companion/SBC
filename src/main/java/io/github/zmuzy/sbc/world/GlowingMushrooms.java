// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.world;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.mc.ParticleHooks;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

// from skyblocker barn/GlowingMushrooms + skyhanni misc/GlowingMushroomHighlighter: the mushrooms the server
// sends effect particles from are the glowing ones
public final class GlowingMushrooms {
    public static final String ID = "glowing_mushrooms";
    private static final long KEEP_MS = 5000;
    private static final Map<BlockPos, Long> SEEN = new HashMap<>();

    private GlowingMushrooms() {}

    private static boolean mushroom(Block block) {
        return block == Blocks.RED_MUSHROOM || block == Blocks.BROWN_MUSHROOM;
    }

    private static void particle(ParticleHooks.Particle p) {
        if (!p.is("minecraft:entity_effect") || !"THE_FARMING_ISLANDS".equals(GameState.island) || !Features.on(ID)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockPos pos = BlockPos.containing(p.x(), p.y(), p.z());
        if (mushroom(mc.level.getBlockState(pos).getBlock())) SEEN.put(pos.immutable(), System.currentTimeMillis());
    }

    private static void draw(WorldDraw w) {
        Minecraft mc = w.mc;
        if (mc.level == null || SEEN.isEmpty()) return;
        Feature f = Features.get(ID);
        int color = f.option("color").color();
        boolean walls = false;
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<BlockPos, Long>> it = SEEN.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<BlockPos, Long> e = it.next();
            BlockPos pos = e.getKey();
            BlockState state = mc.level.getBlockState(pos);
            if (now - e.getValue() > KEEP_MS || !mushroom(state.getBlock())) {
                it.remove();
                continue;
            }
            AABB box = state.getShape(mc.level, pos).bounds().move(pos);
            w.box(box, color, true, walls);
        }
    }

    public static void register() {
        Features.add(new Feature(ID, "Farming Islands", "Glowing mushrooms", "Outlines the glowing mushrooms in the Mushroom Cave.", "Skyblocker: glowing mushroom helper · SkyHanni: glowing mushroom highlighter")
                .when(() -> "THE_FARMING_ISLANDS".equals(GameState.island) || SbcMod.DEV).world(GlowingMushrooms::draw)
                .left(SEEN::clear)
                .option(Option.color("glowing_mushrooms.color", "Colour", "", 0x7F55FFFF)));
        ParticleHooks.listen(GlowingMushrooms::particle);
        Safe.attackBlock("GlowingMushrooms.attackBlock", (player, level, hand, pos, direction) -> {
            if (level.isClientSide() && !SEEN.isEmpty()) SEEN.remove(pos);
            return InteractionResult.PASS;
        });
    }
}
