// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mining;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.mc.BlockHooks;
import dev.zmuzy.sbc.mc.ParticleHooks;
import dev.zmuzy.sbc.mc.SoundHooks;
import dev.zmuzy.sbc.render.WorldDraw;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipBlockStateContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// crystal hollows treasure chests. from skyhanni PowderChestTimer (how long each lasts, colored, on a card +
// lines between them)
// and skyblocker CrystalsChestHighlighter (lock spot on the chest + how many locks picked). one thing here: a
// chest you uncovered
public final class TreasureChests {
    static final String ID = "mining_chests";
    private static final long MAX_DURATION = 60_000, PARTICLE_LIFE = 250, MINED_FOR = 5000;
    private static final int MAX_DISTANCE = 15, NEAR_PLAYER = 25;
    // chest -> when it goes away
    private static final Map<BlockPos, Long> CHESTS = new HashMap<>();
    private static final Map<BlockPos, Long> MINED = new HashMap<>();
    private static final Map<Vec3, Long> PARTICLES = new HashMap<>();
    private static long lastSound = MiningEnv.FAR_PAST, playersCheckedAt;
    private static boolean playersNear;
    private static int waitingForChest, currentLocks, neededLocks;

    private TreasureChests() {}

    private static boolean ready() {
        return MiningApi.inCrystalHollows() && Features.on(ID) && (!MiningPack.on(ID, "mining_chests.only_max") || greatExplorerMaxed());
    }

    // we dont keep the menus perk levels here, so the setting reads what the tree said last
    private static boolean greatExplorerMaxed() {
        return HotmPerks.level("Great Explorer") >= 20;
    }

    // from skyhanni getColorBasedOnTime
    static int timeColor(long left) {
        double ratio = Math.clamp(left / (double) MAX_DURATION, 0.0, 1.0);
        return 0xFF000000 | ((int) (255 * (1 - ratio)) << 16) | ((int) (255 * ratio) << 8);
    }

    // from skyhanni toChatColor
    static String chatColor(long left) {
        int rgb = timeColor(left), red = (rgb >> 16) & 0xFF, green = (rgb >> 8) & 0xFF;
        if (red <= 127 && green >= 127) return "§a";
        if (red <= 212 && green >= 42 && green <= 127) return "§6";
        if (red <= 230 && green >= 25 && green <= 42) return "§c";
        if (red >= 230 && green <= 25) return "§4";
        return "§f";
    }

    private static boolean arePlayersNearby(Minecraft mc) {
        long now = System.currentTimeMillis();
        if (now - playersCheckedAt < 5000) return playersNear;
        playersCheckedAt = now;
        playersNear = false;
        for (var p : mc.level.players()) if (p != mc.player && p.distanceTo(mc.player) < NEAR_PLAYER) playersNear = true;
        return playersNear;
    }

    private static void onBlock(BlockPos pos, BlockState old, BlockState now) {
        if (!MiningApi.inCrystalHollows() || !Features.on(ID)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        double dist = Math.sqrt(pos.distToCenterSqr(mc.player.position()));
        if (dist <= MAX_DISTANCE) {
            BlockPos at = pos.immutable();
            if (old.is(Blocks.STONE) && now.isAir()) MINED.put(at, System.currentTimeMillis());
            else if (!old.is(Blocks.CHEST) && now.is(Blocks.CHEST)) {
                boolean mined = MINED.remove(at) != null;
                boolean falsePositive = arePlayersNearby(mc) || (!mined && old.isAir());
                if (!falsePositive || MiningEnv.passedSince(lastSound) <= 200) CHESTS.put(at, System.currentTimeMillis() + MAX_DURATION);
            } else if (old.is(Blocks.CHEST) && !now.is(Blocks.CHEST)) CHESTS.remove(at);
        }
        // skyblocker CrystalsChestHighlighter: the chest you were told about + a lock count that ends with it
        if (waitingForChest > 0 && now.is(Blocks.CHEST) && dist <= 10) {
            currentLocks = 0;
            waitingForChest--;
        } else if (now.isAir() && CHESTS.containsKey(pos)) currentLocks = 0;
    }

    private static void onParticle(ParticleHooks.Particle p) {
        if (MiningApi.inCrystalHollows() && p.is("minecraft:crit")) PARTICLES.put(new Vec3(p.x(), p.y(), p.z()), System.currentTimeMillis());
    }

    // skyhanni PowderChestTimer.onPlaySound + skyblocker CrystalsChestHighlighter.onSound
    private static boolean onSound(SoundHooks.Sound s) {
        if (!MiningApi.inCrystalHollows() || !Features.on(ID)) return false;
        String n = s.name();
        if (n.equals("entity.experience_orb.pickup") && s.pitch() == 1f && !CHESTS.isEmpty()) lockPicked();
        else if (n.equals("entity.villager.no")) {
            currentLocks = 0;
            PARTICLES.clear();
        } else if (n.equals("block.chest.open")) {
            neededLocks = Math.min(currentLocks, 5);
            currentLocks = 0;
            PARTICLES.clear();
        }
        if (s.volume() != 1f) return false;
        if (n.equals("entity.player.levelup") && s.pitch() == 1f) {
            lastSound = MiningEnv.now();
            return MiningPack.on(ID, "mining_chests.mute_discover");
        }
        if (!MiningPack.on(ID, "mining_chests.mute_open") || MiningEnv.passedSince(lastSound) > MAX_DURATION) return false;
        return switch (n) {
            case "block.chest.open" -> s.pitch() == 1f;
            case "entity.experience_orb.pickup" -> s.pitch() == 1.4920635f || s.pitch() == 0f || s.pitch() == 0.4920635f || s.pitch() == 1f;
            default -> false;
        };
    }

    private static void lockPicked() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 eye = mc.player.getEyePosition(0), look = mc.player.getViewVector(0);
        double range = mc.player.blockInteractionRange();
        BlockHitResult ray = mc.player.level().isBlockInLine(new ClipBlockStateContext(eye, eye.add(look.x * range, look.y * range, look.z * range), st -> st.is(Blocks.CHEST)));
        if (ray.getType() != HitResult.Type.MISS) {
            currentLocks++;
            PARTICLES.clear();
        }
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (MiningApi.inCrystalHollows() && "You uncovered a treasure chest!".equals(line.plain())) waitingForChest++;
        return ChatBus.PASS;
    }

    private static void leave() {
        CHESTS.clear();
        MINED.clear();
        PARTICLES.clear();
        waitingForChest = currentLocks = 0;
    }

    private static void tick(Minecraft mc) {
        if (mc.level == null) return;
        long now = System.currentTimeMillis();
        CHESTS.entrySet().removeIf(e -> e.getValue() < now - 1000);
        MINED.entrySet().removeIf(e -> now - e.getValue() > MINED_FOR);
        CHESTS.keySet().removeIf(pos -> mc.level.getBlockEntity(pos) instanceof ChestBlockEntity c && c.getOpenNess(1f) > 0f);
    }

    private static void draw(WorldDraw w) {
        if (!ready() || w.mc.player == null) return;
        long now = System.currentTimeMillis();
        double y = w.mc.player.getY();
        boolean highlight = MiningPack.on(ID, "mining_chests.highlight"), timer = MiningPack.on(ID, "mining_chests.timer");
        boolean staticColor = MiningPack.on(ID, "mining_chests.static_color");
        int chosen = MiningPack.color(ID, "mining_chests.color", 0xF555FF55);
        for (var e : CHESTS.entrySet()) {
            BlockPos p = e.getKey();
            long left = e.getValue() - now;
            Vec3 corner = new Vec3(p.getX(), p.getY(), p.getZ());
            if (highlight) w.waypointBox(corner, staticColor ? chosen : timeColor(left), false, false, 0);
            if (timer) w.dynamicText(corner.add(0.5, p.getY() <= y ? 1.25 : -0.25, 0.5), chatColor(left) + seconds(left), 1.0, 0);
        }
        lines(w, now);
        lockSpot(w);
    }

    // skyhanni sortChests, drawFirstLine, drawRemainingLines
    private static void lines(WorldDraw w, long now) {
        String mode = MiningPack.choice(ID, "mining_chests.line_mode", "Oldest");
        if (mode.equals("None") || CHESTS.isEmpty()) return;
        List<Map.Entry<BlockPos, Long>> sorted = new ArrayList<>(CHESTS.entrySet());
        Vec3 me = w.eye;
        if (mode.equals("Nearest")) sorted.sort(Comparator.comparingDouble((Map.Entry<BlockPos, Long> e) -> Vec3.atCenterOf(e.getKey()).distanceToSqr(me)));
        else sorted.sort(Map.Entry.comparingByValue());
        int n = Math.min(sorted.size(), MiningPack.number(ID, "mining_chests.line_count", 5));
        if (n <= 0) return;
        Vec3 first = Vec3.atCenterOf(sorted.get(0).getKey());
        w.line(me.add(w.mc.player.getLookAngle()).add(0, -0.2, 0), first, timeColor(sorted.get(0).getValue() - now), 3f, false);
        for (int i = 0; i + 1 < n; i++) w.line(Vec3.atCenterOf(sorted.get(i).getKey()), Vec3.atCenterOf(sorted.get(i + 1).getKey()), timeColor(sorted.get(i).getValue() - now), 3f, false);
    }

    // skyblocker CrystalsChestHighlighter: where the lock is on the chest you look at + how many locks of how many
    private static void lockSpot(WorldDraw w) {
        if (!MiningPack.on(ID, "mining_chests.locks") || !(w.mc.hitResult instanceof BlockHitResult hit) || !CHESTS.containsKey(hit.getBlockPos())) return;
        Vec3 center = Vec3.atCenterOf(hit.getBlockPos());
        int color = MiningPack.color(ID, "mining_chests.lock_color", 0x800000FF);
        long now = System.currentTimeMillis();
        PARTICLES.entrySet().removeIf(e -> now - e.getValue() > PARTICLE_LIFE);
        Vec3 sum = Vec3.ZERO;
        int count = 0;
        for (Vec3 p : PARTICLES.keySet()) {
            if (p.closerThan(center, 0.8)) {
                sum = sum.add(p);
                count++;
            }
        }
        if (count > 0) {
            Vec3 at = sum.scale(1.0 / count);
            w.filledBox(new AABB(at.x - 0.05, at.y - 0.05, at.z - 0.05, at.x + 0.05, at.y + 0.05, at.z + 0.05), color & 0xFFFFFF, ((color >>> 24) & 0xFF) / 255f, false);
        }
        if (neededLocks > 0) w.dynamicText(center, "§9" + Math.min(currentLocks, neededLocks) + "/" + neededLocks, 1.0, 0);
    }

    private static String seconds(long ms) {
        return Math.max(0, ms / 1000) + "s";
    }

    private static RichCard.Content content(int chests, long left) {
        return new RichCard.Content(chests + (chests == 1 ? " chest" : " chests"), List.of(RichCard.Line.row("Next gone in", chatColor(left) + seconds(left)).brief("Next")));
    }

    private static final RichCard CARD = new RichCard(ID, "Treasure chests", "Mining", Zone.TOP_LEFT, 42, ID) {
        private long first() {
            long best = Long.MAX_VALUE;
            for (long v : CHESTS.values()) best = Math.min(best, v);
            return best - System.currentTimeMillis();
        }

        @Override
        public boolean visible() {
            return ready() && !CHESTS.isEmpty();
        }

        @Override
        protected Content live() {
            return content(CHESTS.size(), first());
        }

        @Override
        protected Content sample() {
            return content(3, 41_000);
        }

        @Override
        protected List<Row> classicRows() {
            return List.of(new Row("Next gone in", seconds(first()) + " (" + CHESTS.size() + ")", 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Next gone in", "41s (3)", 0));
        }
    };

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Mining", "Treasure chests", "Each chest you uncover in the Crystal Hollows: how long it lasts, in a colour that runs out, on a card and on the chest, lines from one to the next, the lock's spot and the locks picked.", "SkyHanni: Powder Chest Timer; Skyblocker: Treasure Chest Highlighter")
                .when(MiningPack::hollows).tick(TreasureChests::tick).world(TreasureChests::draw).left(TreasureChests::leave)
                .option(Option.bool("mining_chests.only_max", "Only with a maxed Great Explorer", "", false))
                .option(Option.bool("mining_chests.highlight", "Colour the chests", "From green to red as time runs out.", true))
                .option(Option.bool("mining_chests.static_color", "One colour instead", "", false))
                .option(Option.color("mining_chests.color", "That colour", "", 0xF555FF55))
                .option(Option.bool("mining_chests.timer", "Time on the chest", "", true))
                .option(Option.choice("mining_chests.line_mode", "Line to chests", "The order the lines go in.", "Oldest", "Oldest", "Nearest", "None"))
                .option(Option.integer("mining_chests.line_count", "Chests on the line", "", 5, 1, 30))
                .option(Option.bool("mining_chests.locks", "Lock spot and locks picked", "", true))
                .option(Option.color("mining_chests.lock_color", "Lock colour", "", 0x800000FF))
                .option(Option.bool("mining_chests.mute_discover", "Mute finding a chest", "", false))
                .option(Option.bool("mining_chests.mute_open", "Mute opening a chest", "", false)));
        ChatBus.register(ChatBus.OBSERVE + 24, ID, f, TreasureChests::onChat);
        BlockHooks.listen(TreasureChests::onBlock);
        ParticleHooks.listen(TreasureChests::onParticle);
        SoundHooks.listen(TreasureChests::onSound);
        HudLayout.add(CARD);
    }

    static String debug() {
        return String.format(Locale.ROOT, "%d chests, %d locks", CHESTS.size(), currentLocks) + " in " + GameState.island;
    }
}
