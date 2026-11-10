// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.crimson;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.render.WorldDraw;
import io.github.zmuzy.sbc.rift.RiftScan;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.skeleton.WitherSkeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// the dojo's 7 tests, from skyblocker dojo (DojoManager + the test helpers) and CrimsonGlowAdder
// skyblocker sends a ping packet for latency, here we use the tab list's latency so no packet goes out. shows
// what to do, clicks nothing
public final class Dojo {
    public static final String ID = "crimson_dojo";
    private static final String START_MESSAGE = "[NPC] Master Tao: Ahhh, here we go! Let's get you into the Arena.";
    private static final Pattern TEST_OF = Pattern.compile("\\s+Test of (\\w+) OBJECTIVES");
    private static final Pattern FINISHED = Pattern.compile("\\s+CHALLENGE ((COMPLETED)|(FAILED))");

    enum Challenge {
        NONE(""), FORCE("Force"), STAMINA("Stamina"), MASTERY("Mastery"), DISCIPLINE("Discipline"), SWIFTNESS("Swiftness"), CONTROL("Control"), TENACITY("Tenacity");
        final String name;

        Challenge(String name) {
            this.name = name;
        }

        static Challenge from(String name) {
            for (Challenge c : values()) if (c.name.equals(name)) return c;
            return NONE;
        }

        String option() {
            return "crimson_dojo." + name.toLowerCase(Locale.ROOT);
        }
    }

    static Challenge current = Challenge.NONE;
    static boolean inArena;
    private static long ping;

    private Dojo() {}

    static void reset() {
        inArena = false;
        current = Challenge.NONE;
        ZOMBIES.clear();
        controlVictim = null;
        controlLast = null;
        controlOffset = controlLastOffset = null;
        controlUpdate = -1;
        ORDER.clear();
        END_TIMES.clear();
        swiftnessBlock = null;
        FIREBALLS.clear();
        FIRE_OFFSETS.clear();
        HOLES.clear();
        LAST_HOLES.clear();
        DIRECTIONS.clear();
        middleBase = null;
    }

    // what a chat line does to the state, text is without colour codes
    static void onLine(String text) {
        if (text.equals(START_MESSAGE)) {
            inArena = true;
            var mc = Minecraft.getInstance();
            if (mc != null && mc.getConnection() != null && mc.player != null) {
                var info = mc.getConnection().getPlayerInfo(mc.player.getUUID());
                ping = info == null ? 0 : info.getLatency();
            }
            return;
        }
        if (!inArena) return;
        if (FINISHED.matcher(text).matches()) {
            reset();
            return;
        }
        if (current != Challenge.NONE) return;
        Matcher m = TEST_OF.matcher(text);
        if (m.matches()) {
            current = Challenge.from(m.group(1));
            if (current != Challenge.NONE && !RiftScan.on(ID, current.option(), true)) current = Challenge.NONE;
        }
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (Crimson.crimson()) onLine(GameState.strip(line.component().getString()));
        return ChatBus.PASS;
    }

    // --- force ---

    private static final int ZOMBIE_LIFE_TIME = 10100;
    private static final Map<Zombie, Long> ZOMBIES = new HashMap<>();

    private static void drawForce(WorldDraw w) {
        long now = System.currentTimeMillis();
        float partial = w.mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        for (var e : ZOMBIES.entrySet()) {
            float seconds = Math.max((e.getValue() - now) / 1000f, 0);
            String code = seconds > 1 ? "§a" : seconds > 0 ? "§e" : "§c";
            w.dynamicText(e.getKey().getEyePosition(partial), code + String.format(Locale.ROOT, "%.1f", seconds), 1.5, 0);
        }
    }

    // --- discipline ---

    private static final Map<String, String> SWORD_NAMES = Map.of("WOOD_SWORD", " Wood", "IRON_SWORD", " Iron", "GOLD_SWORD", " Gold", "DIAMOND_SWORD", " Diamond");
    private static final Map<String, Integer> SWORD_COLORS = Map.of("WOOD_SWORD", 0xA52A2A, "IRON_SWORD", 0xC0C0C0, "GOLD_SWORD", 0xFFD700, "DIAMOND_SWORD", 0x00FFFF);

    // CrimsonGlowAdder with DojoManager.shouldGlow and getColor
    public static int glow(Entity e) {
        if (!inArena || !(e instanceof Zombie) || (current != Challenge.FORCE && current != Challenge.DISCIPLINE) || !Features.on(ID)) return -1;
        var mc = Minecraft.getInstance();
        List<ArmorStand> stands = e.level().getEntitiesOfClass(ArmorStand.class, e.getBoundingBox().inflate(0, 2, 0), EntitySelector.ENTITY_NOT_BEING_RIDDEN);
        String name = stands.isEmpty() ? "" : stands.get(0).getName().getString();
        return glowColor(current, name, RiftScan.heldId(mc));
    }

    // ForceTestHelper: a zombie worth less than nothing. DisciplineTestHelper: the zombie for the sword in your hand
    static int glowColor(Challenge c, String name, String held) {
        if (c == Challenge.FORCE) return name.contains("-") ? 0xFF0000 : -1;
        if (c == Challenge.DISCIPLINE && name.equals(SWORD_NAMES.get(held))) return SWORD_COLORS.getOrDefault(held, -1);
        return -1;
    }

    // --- control ---

    private static WitherSkeleton controlVictim;
    private static Vec3 controlLast, controlOffset, controlLastOffset;
    private static long controlUpdate = -1;

    private static void updateControl() {
        if (controlVictim == null) return;
        if (controlLast != null) {
            controlLastOffset = controlOffset;
            Vec3 movement = controlVictim.position().subtract(controlLast).multiply(1, 0.1, 1);
            controlOffset = movement.scale(1 + 3 / 20d + ping / 1000d);
        }
        controlLast = controlVictim.position();
        controlUpdate = System.currentTimeMillis();
    }

    private static void drawControl(WorldDraw w) {
        if (controlVictim == null || controlOffset == null || controlLastOffset == null) return;
        float partial = w.mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double percent = (System.currentTimeMillis() - controlUpdate) / 150d;
        Vec3 aim = controlVictim.getEyePosition(partial).add(controlOffset.scale(percent)).add(controlLastOffset.scale(1 - percent));
        AABB box = new AABB(aim.add(-0.5, -0.5, -0.5), aim.add(0.5, 0.5, 0.5));
        Vec3 eye = w.mc.player.getEyePosition(partial);
        boolean looking = box.clip(eye, eye.add(w.mc.player.getViewVector(partial).scale(30))).isPresent();
        w.box(box, looking ? 0xFF00FF00 : 0xFFC0C0C0, false, true);
    }

    // --- mastery ---

    private static final int BLOCK_LIFE_TIME = 6550;
    private static final List<BlockPos> ORDER = new ArrayList<>();
    private static final Map<BlockPos, Long> END_TIMES = new HashMap<>();

    private static void masteryBlock(BlockPos pos, BlockState state) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (state.is(Blocks.WOOL.lime())) {
            ORDER.add(pos);
            // arrow from a full draw flies ~60 blocks a sec
            long travel = (long) (mc.player.position().distanceTo(Vec3.atCenterOf(pos)) * 1000 / 60);
            END_TIMES.put(pos, System.currentTimeMillis() + BLOCK_LIFE_TIME - ping - travel);
        }
        if (state.isAir()) {
            ORDER.remove(pos);
            END_TIMES.remove(pos);
        }
    }

    private static void drawMastery(WorldDraw w) {
        if (ORDER.isEmpty()) return;
        w.line(w.eye.add(w.mc.player.getLookAngle()), Vec3.atCenterOf(ORDER.get(0)), 0xFFC0C0C0, 2f, true);
        if (ORDER.size() >= 2) w.line(Vec3.atCenterOf(ORDER.get(0)), Vec3.atCenterOf(ORDER.get(1)), 0xFFC0C0C0, 2f, false);
        long now = System.currentTimeMillis();
        for (BlockPos p : ORDER) {
            float seconds = Math.max((END_TIMES.getOrDefault(p, now) - now) / 1000f, 0);
            w.dynamicText(Vec3.atCenterOf(p.above()), String.format(Locale.ROOT, "%.2f", seconds), 3, 0);
        }
    }

    // --- swiftness ---

    private static BlockPos swiftnessBlock;

    private static void drawSwiftness(WorldDraw w) {
        if (swiftnessBlock != null) w.filledBox(new AABB(swiftnessBlock), 0x00FF00, 0.5f, true);
    }

    // --- tenacity ---

    private static final Map<ArmorStand, Vec3> FIREBALLS = new HashMap<>(), FIRE_OFFSETS = new HashMap<>();

    private static void drawTenacity(WorldDraw w) {
        for (var e : FIREBALLS.entrySet()) {
            ArmorStand fireball = e.getKey();
            Vec3 offset = FIRE_OFFSETS.getOrDefault(fireball, Vec3.ZERO), start = e.getValue().add(offset), at = fireball.position().add(offset);
            Vec3 distance = at.subtract(start);
            if (distance.length() <= 0.02) continue;
            Vec3 end = start.add(distance.scale(100));
            w.line(start, end, 0xFFFF0000, 3f, false);
            HitResult hit = w.mc.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, fireball));
            if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult b) w.filledBox(new AABB(b.getBlockPos()), 0xFF0000, 0.5f, false);
        }
    }

    private static void onTenacityParticle(double x, double y, double z) {
        Vec3 p = new Vec3(x, y, z);
        ArmorStand best = null;
        double closest = 50;
        for (ArmorStand f : FIREBALLS.keySet()) {
            double d = f.position().distanceTo(p);
            if (d < closest) {
                best = f;
                closest = d;
            }
        }
        if (best != null) FIRE_OFFSETS.put(best, p.subtract(best.position()));
    }

    // --- stamina ---

    private static final int WALL_THRESHOLD = 13, WALL_HEIGHT = 5;
    private static final List<AABB> HOLES = new ArrayList<>(), LAST_HOLES = new ArrayList<>();
    private static final Map<AABB, Direction> DIRECTIONS = new HashMap<>();
    private static BlockPos middleBase;

    private enum Direction { POSITIVE_X, POSITIVE_Z, NEGATIVE_X, NEGATIVE_Z, NEW, UNCHANGED }

    private static void updateStamina(Minecraft mc) {
        List<BlockPos> bottom = wallBlocks(mc);
        if (bottom == null) return;
        LAST_HOLES.clear();
        LAST_HOLES.addAll(HOLES);
        HOLES.clear();
        for (AABB wall : walls(bottom)) HOLES.addAll(holes(mc, wall));
        Map<AABB, Direction> last = new HashMap<>(DIRECTIONS);
        DIRECTIONS.clear();
        for (AABB hole : HOLES) {
            Direction d = direction(hole);
            DIRECTIONS.put(hole, d == Direction.UNCHANGED ? last.getOrDefault(hole, Direction.NEW) : d);
        }
    }

    private static List<BlockPos> wallBlocks(Minecraft mc) {
        BlockPos me = mc.player.blockPosition();
        if (middleBase == null) {
            for (int x = me.getX() - 10; x < me.getX() + 10; x++)
                for (int y = me.getY() - 5; y < me.getY(); y++)
                    for (int z = me.getZ() - 10; z < me.getZ() + 10; z++) {
                        BlockPos p = new BlockPos(x, y, z);
                        if (mc.level.getBlockState(p).is(Blocks.CHISELED_STONE_BRICKS)) {
                            middleBase = p;
                            return null;
                        }
                    }
            return null;
        }
        List<BlockPos> out = new ArrayList<>();
        for (int x = middleBase.getX() - 15; x < middleBase.getX() + 15; x++)
            for (int z = middleBase.getZ() - 15; z < middleBase.getZ() + 15; z++) {
                BlockPos p = new BlockPos(x, middleBase.getY() + 1, z);
                if (!mc.level.getBlockState(p).isAir()) out.add(p);
            }
        return out;
    }

    private static List<AABB> walls(List<BlockPos> bottom) {
        Map<Integer, List<BlockPos>> byX = new HashMap<>(), byZ = new HashMap<>();
        for (BlockPos b : bottom) {
            byX.computeIfAbsent(b.getX(), k -> new ArrayList<>()).add(b);
            byZ.computeIfAbsent(b.getZ(), k -> new ArrayList<>()).add(b);
        }
        List<List<BlockPos>> lines = new ArrayList<>();
        for (List<BlockPos> line : byX.values()) if (line.size() >= WALL_THRESHOLD) lines.add(line);
        for (List<BlockPos> line : byZ.values()) if (line.size() >= WALL_THRESHOLD) lines.add(line);
        List<AABB> out = new ArrayList<>();
        for (List<BlockPos> wall : lines) {
            int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE, y = wall.get(0).getY();
            for (BlockPos p : wall) {
                minX = Math.min(minX, p.getX());
                minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX());
                maxZ = Math.max(maxZ, p.getZ());
            }
            out.add(AABB.encapsulatingFullBlocks(new BlockPos(minX, y, minZ), new BlockPos(maxX, y + WALL_HEIGHT, maxZ)));
        }
        return out;
    }

    private static List<AABB> holes(Minecraft mc, AABB box) {
        List<AABB> out = new ArrayList<>();
        Vec3i along = box.getXsize() == 1 ? new Vec3i(0, 0, 1) : new Vec3i(1, 0, 0);
        List<BlockPos> topLeft = new ArrayList<>(), topRight = new ArrayList<>(), bottomLeft = new ArrayList<>();
        for (int z = (int) box.minZ; z < box.maxZ; z++)
            for (int x = (int) box.minX; x < box.maxX; x++)
                for (int y = (int) box.minY; y < box.maxY; y++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (!mc.level.getBlockState(p).isAir()) continue;
                    boolean top = y == box.maxY - 1 || !mc.level.getBlockState(p.above()).isAir();
                    boolean bottom = !mc.level.getBlockState(p.below()).isAir();
                    boolean left = !mc.level.getBlockState(p.offset(along)).isAir();
                    boolean right = !mc.level.getBlockState(p.subtract(along)).isAir();
                    if (top) {
                        if (left) topLeft.add(p);
                        if (right) topRight.add(p);
                    }
                    if (bottom && left) bottomLeft.add(p);
                }
        for (int i = 0; i < topLeft.size(); i++) {
            if (topRight.size() <= i || bottomLeft.size() <= i) break;
            AABB hole = AABB.encapsulatingFullBlocks(topLeft.get(i), topRight.get(i));
            out.add(hole.expandTowards(0, bottomLeft.get(i).getY() - topLeft.get(i).getY(), 0));
        }
        return out;
    }

    private static Direction direction(AABB hole) {
        if (LAST_HOLES.contains(hole)) return Direction.UNCHANGED;
        if (LAST_HOLES.contains(hole.move(1, 0, 0))) return Direction.POSITIVE_X;
        if (LAST_HOLES.contains(hole.move(-1, 0, 0))) return Direction.NEGATIVE_X;
        if (LAST_HOLES.contains(hole.move(0, 0, 1))) return Direction.POSITIVE_Z;
        if (LAST_HOLES.contains(hole.move(0, 0, -1))) return Direction.NEGATIVE_Z;
        return Direction.NEW;
    }

    private static boolean incoming(AABB hole, Direction d, BlockPos me) {
        return switch (d) {
            case POSITIVE_X -> me.getX() < hole.minX;
            case POSITIVE_Z -> me.getZ() < hole.minZ;
            case NEGATIVE_X -> me.getX() > hole.maxX;
            case NEGATIVE_Z -> me.getZ() > hole.maxZ;
            default -> true;
        };
    }

    private static void drawStamina(WorldDraw w) {
        BlockPos me = w.mc.player.blockPosition();
        for (AABB hole : HOLES) {
            boolean in = incoming(hole, DIRECTIONS.getOrDefault(hole, Direction.NEW), me);
            w.filledBox(hole, in ? 0x00FF00 : 0xFFA300, 0.3f, false);
        }
    }

    // --- hooks ---

    private static void tick(Minecraft mc) {
        if (!inArena || mc.player == null || mc.level == null || mc.player.tickCount % 3 != 0) return;
        if (current == Challenge.STAMINA) updateStamina(mc);
        else if (current == Challenge.CONTROL) updateControl();
    }

    private static void draw(WorldDraw w) {
        if (!inArena) return;
        switch (current) {
            case FORCE -> drawForce(w);
            case STAMINA -> drawStamina(w);
            case MASTERY -> drawMastery(w);
            case SWIFTNESS -> drawSwiftness(w);
            case CONTROL -> drawControl(w);
            case TENACITY -> drawTenacity(w);
            default -> {
            }
        }
    }

    public static void onBlockChange(BlockPos pos, BlockState now) {
        if (!inArena) return;
        if (current == Challenge.MASTERY) masteryBlock(pos.immutable(), now);
        else if (current == Challenge.SWIFTNESS && now.is(Blocks.WOOL.lime())) swiftnessBlock = pos.immutable();
    }

    private static void onSpawn(Entity e) {
        var mc = Minecraft.getInstance();
        if (!inArena || !Crimson.crimson() || mc.player == null) return;
        if (!e.closerThan(mc.player, 50, 5)) return;
        switch (current) {
            case FORCE -> {
                if (e instanceof Zombie z) ZOMBIES.put(z, System.currentTimeMillis() + ZOMBIE_LIFE_TIME);
            }
            case CONTROL -> {
                if (e instanceof WitherSkeleton s && controlVictim == null) controlVictim = s;
            }
            case TENACITY -> {
                if (e instanceof ArmorStand a) FIREBALLS.put(a, a.position());
            }
            default -> {
            }
        }
    }

    private static void onDespawn(Entity e) {
        if (!inArena) return;
        if (e instanceof Zombie z) ZOMBIES.remove(z);
        if (e instanceof ArmorStand a) FIREBALLS.remove(a);
    }

    public static void register() {
        Features.add(new Feature(ID, "Crimson Isle", "Dojo tests", "Help in each of the seven tests of the Dojo: which zombies to hit and how long they stay, where the gaps in the walls come, which block to shoot and where to aim.", "Skyblocker: Dojo Helper")
                .when(Crimson::crimson).tick(Dojo::tick).world(Dojo::draw).left(Dojo::reset)
                .option(Option.bool("crimson_dojo.force", "Force", "Marks the zombies with negative points and how long each one stays.", true))
                .option(Option.bool("crimson_dojo.stamina", "Stamina", "Marks the holes in the walls: green ones are coming towards you.", true))
                .option(Option.bool("crimson_dojo.mastery", "Mastery", "The next blocks to shoot and how long each one has left.", true))
                .option(Option.bool("crimson_dojo.discipline", "Discipline", "Marks the zombies the sword in your hand can hit.", true))
                .option(Option.bool("crimson_dojo.swiftness", "Swiftness", "Marks the newest block.", true))
                .option(Option.bool("crimson_dojo.control", "Control", "Where to aim for the Wither Skeleton, allowing for your latency.", true))
                .option(Option.bool("crimson_dojo.tenacity", "Tenacity", "Where each fireball is going to land.", true)));
        ChatBus.register(ChatBus.OBSERVE + 32, "crimson_dojo", Features.get(ID), Dojo::onChat);
        Safe.entityLoad("Dojo.entityLoad", (e, level) -> onSpawn(e));
        Safe.entityUnload("Dojo.entityUnload", (e, level) -> onDespawn(e));
        Safe.attackEntity("Dojo.attackEntity", (player, level, hand, entity, hit) -> {
            if (level.isClientSide() && inArena && current == Challenge.FORCE && entity instanceof Zombie z && ZOMBIES.containsKey(z)) ZOMBIES.put(z, System.currentTimeMillis() + ZOMBIE_LIFE_TIME);
            return InteractionResult.PASS;
        });
        io.github.zmuzy.sbc.mc.ParticleHooks.listen(p -> {
            if (inArena && current == Challenge.TENACITY && p.is("minecraft:flame")) onTenacityParticle(p.x(), p.y(), p.z());
        });
    }
}
