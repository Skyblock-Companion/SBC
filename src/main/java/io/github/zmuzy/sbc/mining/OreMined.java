// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mining;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.mc.BlockHooks;
import io.github.zmuzy.sbc.mc.Interactions;
import io.github.zmuzy.sbc.mc.SoundHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.regex.Matcher;

// ore mining half of skyhanni MiningApi: from your click, the sound the server plays and the block that goes
// -> which ore you really mined + what efficient miner took (OreMinedEvent)
// pickobulus counts by itself, from blocks breaking around its explosion up to the number it says in chat
// other players blocks dont count, they come without the sound right after your click
public final class OreMined {
    private record Mined(OreBlock ore, long time, BlockPos pos, boolean[] confirmed) {}

    private static final Set<String> SOUNDS = Set.of("block.glass.break", "block.stone.break", "block.gravel.break", "block.wool.break", "entity.experience_orb.pickup", "block.metal.place");
    private static final Patterns.P PICK_USE = Patterns.p("SH.data.miningapi.pickobulus.use");
    private static final Patterns.P PICK_FAIL = Patterns.p("SH.data.miningapi.pickobulus.fail");
    private static final Patterns.P PICK_END = Patterns.p("SH.data.miningapi.pickobulus.end");
    private static final List<BiConsumer<OreBlock, Map<OreBlock, Integer>>> LISTENERS = new ArrayList<>();

    private static final Map<BlockPos, Long> clickSpam = new HashMap<>();
    private static final List<Mined> surrounding = new ArrayList<>();
    private static BlockPos lastClickedPos, initBlockPos, explosion;
    private static long lastClicked = MiningEnv.FAR_PAST, lastInitSound = MiningEnv.FAR_PAST, lastPickUse = MiningEnv.FAR_PAST;
    private static boolean waitingInit = true, ignoreInit, waitingEffSound, waitingEffBlock, pickWaitSound, pickWaitBlock;
    private static final List<Map.Entry<BlockPos, OreBlock>> pickBlocks = new ArrayList<>();
    private static Set<OreBlock> here = Set.of();
    private static String lastPlace = "";
    private static Object level;

    private OreMined() {}

    // block broken by your click (null for pickobulus) + how many of each block went with it
    public static void listen(BiConsumer<OreBlock, Map<OreBlock, Integer>> l) {
        LISTENERS.add(l);
    }

    // from skyhanni IslandTypeTag.CUSTOM_MINING
    static boolean customMining() {
        return switch (GameState.island) {
            case "DWARVEN_MINES", "CRYSTAL_HOLLOWS", "MINESHAFT", "CRIMSON_ISLE", "THE_END", "SPIDER_DEN" -> true;
            default -> false;
        };
    }

    public static OreBlock of(BlockState s) {
        for (OreBlock o : here) if (o.check.test(s)) return o;
        return null;
    }

    private static void updateLocation() {
        OreBlock.Area.glacite = MiningApi.inGlaciteArea();
        OreBlock.Area.tunnels = MiningApi.inGlacialTunnels();
        OreBlock.Area.mineshaft = MiningApi.inMineshaft();
        OreBlock.Area.dwarven = MiningApi.inRegularDwarven();
        OreBlock.Area.hollows = MiningApi.inCrystalHollows();
        OreBlock.Area.crimson = "CRIMSON_ISLE".equals(GameState.island);
        OreBlock.Area.end = "THE_END".equals(GameState.island);
        OreBlock.Area.spiders = "SPIDER_DEN".equals(GameState.island);
        here = OreBlock.inCurrentArea();
    }

    private static void resetOre() {
        lastInitSound = MiningEnv.FAR_PAST;
        waitingInit = true;
        ignoreInit = false;
        initBlockPos = null;
        waitingEffSound = waitingEffBlock = false;
    }

    private static void resetPick() {
        lastPickUse = MiningEnv.FAR_PAST;
        explosion = null;
        pickWaitSound = pickWaitBlock = false;
    }

    private static boolean pickActive() {
        return !MiningEnv.isFarPast(lastPickUse);
    }

    private static void onClick(Vec3 pos, boolean right) {
        if (right || !customMining()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        BlockPos p = BlockPos.containing(pos);
        if (of(mc.level.getBlockState(p)) == null) return;
        long now = MiningEnv.now();
        clickSpam.put(p, now);
        lastClickedPos = p;
        lastClicked = now;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!customMining()) return ChatBus.PASS;
        String m = LegacyText.of(line.component());
        if (PICK_USE.matcher(m).matches()) {
            lastPickUse = MiningEnv.now();
            return ChatBus.PASS;
        }
        if (PICK_FAIL.matcher(m).matches()) {
            resetPick();
            pickBlocks.clear();
            return ChatBus.PASS;
        }
        Matcher end = PICK_END.matcher(m);
        if (end.matches()) {
            int amount = (int) MiningEnv.parseLong(end.group("amount"));
            resetPick();
            Map<OreBlock, Integer> blocks = new EnumMap<>(OreBlock.class);
            for (int i = 0; i < pickBlocks.size() && i < amount; i++) blocks.merge(pickBlocks.get(i).getValue(), 1, Integer::sum);
            if (!blocks.isEmpty()) post(null, blocks);
            pickBlocks.clear();
        }
        return ChatBus.PASS;
    }

    private static boolean onSound(SoundHooks.Sound s) {
        if (!customMining()) return false;
        if (s.name().equals("entity.generic.explode") && MiningEnv.passedSince(lastPickUse) < 5000) {
            explosion = BlockPos.containing(s.x(), s.y(), s.z());
            pickWaitSound = true;
            return false;
        }
        if (!SOUNDS.contains(s.name())) return false;
        if (pickActive() && pickWaitSound) {
            pickWaitSound = false;
            pickWaitBlock = true;
            return false;
        }
        if (waitingInit && (s.name().equals("entity.experience_orb.pickup") ? orbSound() : noOrbSound(s))) return false;
        if (waitingEffSound && !surrounding.isEmpty()) {
            Mined last = surrounding.get(surrounding.size() - 1);
            if (last.confirmed[0]) return false;
            waitingEffSound = false;
            last.confirmed[0] = true;
            waitingEffBlock = true;
        }
        return false;
    }

    // true when this wasnt the first sound of a mined block
    private static boolean orbSound() {
        if (MiningEnv.passedSince(lastClicked) > 1000 || lastClickedPos == null) return true;
        Minecraft mc = Minecraft.getInstance();
        OreBlock ore = mc.level == null ? null : of(mc.level.getBlockState(lastClickedPos));
        if (ore == null || ore.hasInitSound) return true;
        ignoreInit = true;
        waitingInit = false;
        waitingEffBlock = true;
        lastInitSound = MiningEnv.now();
        return false;
    }

    private static boolean noOrbSound(SoundHooks.Sound s) {
        if (s.pitch() != 0.7936508f) return true;
        BlockPos p = BlockPos.containing(s.x(), s.y(), s.z());
        if (!clickSpam.containsKey(p)) return true;
        waitingInit = false;
        waitingEffBlock = true;
        initBlockPos = p;
        lastInitSound = MiningEnv.now();
        return false;
    }

    private static void onBlock(BlockPos pos, BlockState old, BlockState now) {
        if (!customMining()) return;
        if (old.is(Blocks.AIR) || old.is(Blocks.BEDROCK)) return;
        if (!now.is(Blocks.AIR) && !now.is(Blocks.BEDROCK) && !now.is(Blocks.POLISHED_DIORITE)) return;
        BlockPos at = pos.immutable();
        if (handlePick(at, old)) return;
        if (MiningEnv.passedSince(lastInitSound) > 100) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || Math.sqrt(at.distToCenterSqr(mc.player.position())) > 7) return;
        OreBlock ore = of(old);
        if (ore == null) return;
        if (at.equals(initBlockPos)) {
            surrounding.add(new Mined(ore, MiningEnv.now(), at, new boolean[]{true}));
            runEvent();
            return;
        }
        if (!waitingEffBlock || (ignoreInit && ore.hasInitSound)) return;
        for (Mined m : surrounding) if (m.pos.equals(at)) return;
        waitingEffBlock = false;
        surrounding.add(new Mined(ore, MiningEnv.now(), at, new boolean[]{false}));
        waitingEffSound = true;
    }

    private static boolean handlePick(BlockPos pos, BlockState old) {
        if (!pickActive() || !pickWaitBlock) return false;
        if (explosion == null || Math.sqrt(explosion.distSqr(pos)) > 15) return true;
        OreBlock ore = of(old);
        if (ore == null) return true;
        for (var e : pickBlocks) if (e.getKey().equals(pos)) return true;
        pickBlocks.add(Map.entry(pos, ore));
        pickWaitBlock = false;
        pickWaitSound = true;
        return true;
    }

    private static void runEvent() {
        boolean ignoreFilter = ignoreInit;
        resetOre();
        if (surrounding.isEmpty()) return;
        Mined original = null;
        for (Mined m : surrounding) {
            if (m.confirmed[0]) {
                original = m;
                break;
            }
        }
        if (original == null) {
            surrounding.clear();
            clickSpam.clear();
            return;
        }
        Map<OreBlock, Integer> extra = new EnumMap<>(OreBlock.class);
        for (Mined m : surrounding) if (ignoreFilter ? m.ore == original.ore : m.confirmed[0]) extra.merge(m.ore, 1, Integer::sum);
        post(original.ore, extra);
        surrounding.clear();
        long passed = MiningEnv.passedSince(original.time);
        clickSpam.values().removeIf(t -> MiningEnv.passedSince(t) >= passed);
        lastClickedPos = null;
    }

    private static void post(OreBlock original, Map<OreBlock, Integer> extra) {
        for (var l : LISTENERS) {
            try {
                l.accept(original, extra);
            } catch (Throwable t) {
                io.github.zmuzy.sbc.SbcMod.LOG.warn("ore listener failed", t);
            }
        }
    }

    private static void tick(Minecraft mc) {
        if (mc.level != level) {
            level = mc.level;
            clickSpam.clear();
            surrounding.clear();
            pickBlocks.clear();
            lastClickedPos = null;
            resetOre();
            resetPick();
        }
        String place = GameState.island + "|" + GameState.area;
        if (!place.equals(lastPlace)) {
            lastPlace = place;
            updateLocation();
        }
        if (!customMining() || here.isEmpty()) return;
        clickSpam.values().removeIf(t -> MiningEnv.passedSince(t) >= 10_000);
        surrounding.removeIf(m -> MiningEnv.passedSince(m.time) >= 5000);
        if (!waitingInit && MiningEnv.passedSince(lastInitSound) > 200) {
            if (ignoreInit) runEvent();
            else resetOre();
        }
        if (pickActive() && MiningEnv.passedSince(lastPickUse) > 5000) {
            resetPick();
            pickBlocks.clear();
        }
    }

    public static void register() {
        Safe.endTick("OreMined.tick", OreMined::tick);
        ChatBus.register(ChatBus.OBSERVE + 25, "mining_ore", null, OreMined::onChat);
        Interactions.onBlockClick(OreMined::onClick);
        SoundHooks.listen(OreMined::onSound);
        BlockHooks.listen(OreMined::onBlock);
    }
}
