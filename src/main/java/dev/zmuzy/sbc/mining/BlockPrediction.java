// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mining;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.core.Vitals;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyblocker BlockBreakPrediction: cracks on the block you mine run on your own clock from its strength
// + your mining speed, not on what the server last sent
// their strengths come from another repo, ours are skyhanni Mining.json by OreBlock, same numbers as the
// block strength table
// that data has no breaking power per block, so the only check is that the tool has any. on a block too hard
// for it the cracks run but the block stays
public final class BlockPrediction {
    public static final String ID = "mining_block_prediction";
    private static final Pattern BREAKING_POWER = Pattern.compile("Breaking Power (\\d+)");

    private static boolean newBlock, soundPlayed, warned;
    private static long breakTime = -1, startedAt;

    private BlockPrediction() {}

    // skyblocker getBreakTime: ticks = (strength x 30) / mining speed, max 20/3 of the strength. returned in ms
    static long breakMillis(int strength, double speed) {
        return (long) (50 * Math.min((strength * 30f) / speed, (20f / 3) * strength));
    }

    // 10 crack steps, 0 to 9
    static int step(long elapsed, long total) {
        return Math.min((int) ((elapsed * 10) / total), 9);
    }

    private static boolean hasBreakingPower(net.minecraft.world.item.ItemStack tool) {
        for (String l : LegacyText.lore(tool)) {
            Matcher m = BREAKING_POWER.matcher(GameState.strip(l));
            if (m.find()) return true;
        }
        return false;
    }

    private static long findBreakTime(Minecraft mc, BlockState state) {
        if (!hasBreakingPower(mc.player.getMainHandItem())) return -1;
        Integer strength = BlockStrength.strengthOf(state);
        if (strength == null || strength <= 0) return -1;
        double speed = Vitals.stat("Mining Speed");
        if (speed <= 0) {
            if (!warned) {
                warned = true;
                mc.player.sendSystemMessage(Component.literal("§b[SBC] §cUnable to find the Mining Speed stat in the player list. Please make sure it's enabled in the Stats Widget in /tab for the block break animation to work."));
            }
            return -1;
        }
        return breakMillis(strength, speed);
    }

    // skyblocker getBlockBreakPrediction. asked per breaking block per frame, when off its just one lookup
    public static int progress(BlockPos pos, BlockState state, int progress) {
        if (!Features.on(ID)) return progress;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gameMode == null) return progress;
        if (!(mc.hitResult instanceof BlockHitResult hit) || !hit.getBlockPos().equals(pos)) return progress;
        if (newBlock) {
            newBlock = false;
            breakTime = findBreakTime(mc, state);
        }
        if (!mc.gameMode.isDestroying()) breakTime = -1;
        if (breakTime <= 0) return progress;
        long elapsed = System.currentTimeMillis() - startedAt;
        if (!soundPlayed && elapsed >= breakTime && Features.get(ID).option("mining_block_prediction.sound").bool()) {
            soundPlayed = true;
            mc.player.playSound(SoundEvents.BLAZE_HURT, 100f, 1f);
        }
        return step(elapsed, breakTime);
    }

    public static void register() {
        Features.add(new Feature(ID, "Mining", "Block break animation", "The cracks on the block you mine follow your Mining Speed and the block's strength, so they keep up on a slow connection. Needs Mining Speed in your /tab.", "Skyblocker: Block Break Prediction")
                .when(() -> GameState.inSkyBlock).defaultOff()
                .option(Option.bool("mining_block_prediction.sound", "Play a sound", "A Blaze sound when the block should be broken.", false)));
        Safe.attackBlock("BlockPrediction.attackBlock", (player, level, hand, pos, direction) -> {
            newBlock = true;
            startedAt = System.currentTimeMillis();
            soundPlayed = false;
            return InteractionResult.PASS;
        });
    }
}
