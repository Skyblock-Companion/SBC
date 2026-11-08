// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.misc;

import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyblocker BuildersWandPreview (see through box per block, not its model) + HealthBars (a text bar
// over the name, not a textured quad)
public final class SbHelpers {
    public static final String WAND = "builders_wand", BARS = "mob_health_bars";

    private SbHelpers() {}

    // --- skyblocker BuildersWandPreview ---

    private static final int MAX_BLOCKS = 241, PLOT_SIZE = 96, PLOT_OFFSET = 48;
    private static List<BlockPos> preview = List.of();
    private static boolean previewRemoves;

    // from skyblocker findConnectedFaces: same block with air in front, spreading sideways over the face you look at
    static Set<BlockPos> connected(Level level, BlockPos pos, Direction side, BlockState state) {
        Queue<BlockPos> q = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();
        q.add(pos);
        visited.add(pos);
        while (!q.isEmpty()) {
            BlockPos current = q.poll();
            for (Direction dir : Direction.values()) {
                if (dir == side || dir == side.getOpposite()) continue;
                BlockPos next = current.relative(dir);
                if (visited.contains(next) || !level.getBlockState(next).equals(state) || !level.getBlockState(next.relative(side)).isAir()) continue;
                q.add(next);
                visited.add(next);
            }
            if (visited.size() > MAX_BLOCKS) return Set.of();
        }
        return visited;
    }

    // from skyblocker isInPlot
    static boolean samePlot(BlockPos a, BlockPos b) {
        return Math.floorDiv(a.getX() + PLOT_OFFSET, PLOT_SIZE) == Math.floorDiv(b.getX() + PLOT_OFFSET, PLOT_SIZE) && Math.floorDiv(a.getZ() + PLOT_OFFSET, PLOT_SIZE) == Math.floorDiv(b.getZ() + PLOT_OFFSET, PLOT_SIZE);
    }

    private static List<BlockPos> wand(Level level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        Direction side = hit.getDirection();
        if (!level.getBlockState(pos.relative(side)).isAir()) return List.of();
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : connected(level, pos, side, level.getBlockState(pos))) out.add(p.relative(side));
        return out;
    }

    private static List<BlockPos> ruler(Minecraft mc, Level level, BlockHitResult hit) {
        boolean sneaking = mc.player.isShiftKeyDown();
        BlockPos start = hit.getBlockPos();
        Block startBlock = null;
        if (sneaking) startBlock = level.getBlockState(start).getBlock();
        else start = start.relative(hit.getDirection());
        Direction dir = hit.getDirection().getOpposite();
        if (dir == Direction.UP || dir == Direction.DOWN || !sneaking) dir = mc.player.getDirection();
        List<BlockPos> out = new ArrayList<>();
        BlockPos pos = start;
        for (int i = 0; i < MAX_BLOCKS; i++) {
            BlockState state = level.getBlockState(pos);
            if (!samePlot(start, pos) || !(sneaking ? state.is(startBlock) : state.isAir())) break;
            out.add(pos);
            pos = pos.relative(dir);
        }
        previewRemoves = sneaking;
        return out;
    }

    private static void wandTick(Minecraft mc) {
        preview = List.of();
        previewRemoves = false;
        if (mc.player == null || mc.level == null || !(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return;
        String id = Gain.sbId(mc.player.getMainHandItem());
        if (id.equals("BUILDERS_WAND")) preview = wand(mc.level, hit);
        else if (id.equals("BUILDERS_RULER") && "GARDEN".equals(GameState.island)) preview = ruler(mc, mc.level, hit);
    }

    private static void wandWorld(WorldDraw w) {
        if (preview.isEmpty()) return;
        float alpha = (float) Features.get(WAND).option("builders_wand.opacity").number();
        int rgb = previewRemoves ? 0xFF0000 : 0x55FFFF;
        for (BlockPos p : preview) w.filledBox(new AABB(p), rgb, alpha, false);
    }

    // --- skyblocker HealthBars ---

    static final Pattern HEALTH = Pattern.compile("(\\d{1,3}(,\\d{3})*(\\.\\d+)?[kKmMbBtT]?)/(\\d{1,3}(,\\d{3})*(\\.\\d+)?[kKmMbBtT]?)❤");
    static final Pattern HEALTH_ONLY = Pattern.compile("(\\d{1,3}(,\\d{3})*(\\.\\d+)?[kKmMbBtT]?)❤");
    // from skyblocker isDisallowedMob: names other features need in dungeons
    private static final List<String> DUNGEON_KEEP = List.of("Blaze", "The Professor", "Guardian");
    private static final Map<ArmorStand, Float> HEALTHS = new HashMap<>();
    private static final Map<ArmorStand, Long> STARTING = new HashMap<>();
    private static int barTick;

    // from skyblocker Formatters.parseNumber for 1.2k + 3,400
    static double number(String text) {
        String t = text.replace(",", "").toUpperCase(Locale.ROOT);
        double scale = 1;
        char last = t.charAt(t.length() - 1);
        switch (last) {
            case 'K' -> scale = 1e3;
            case 'M' -> scale = 1e6;
            case 'B' -> scale = 1e9;
            case 'T' -> scale = 1e12;
            default -> {}
        }
        if (scale != 1) t = t.substring(0, t.length() - 1);
        return Double.parseDouble(t) * scale;
    }

    // from skyblocker healthBar ratio from a name: current/max, or null
    static Float ratio(String name) {
        Matcher m = HEALTH.matcher(name);
        return m.find() ? (float) (number(m.group(1)) / number(m.group(4))) : null;
    }

    private static boolean keepName(String name) {
        if (!"CATACOMBS".equals(GameState.island)) return false;
        for (String k : DUNGEON_KEEP) if (name.contains(k)) return true;
        return false;
    }

    // name minus the health parts the settings take away, skyblockers sibling walk from healthBar + healthOnlyCheck
    private static Component cleaned(Component name, String match, boolean hasMax, boolean removeValue, boolean removeMax) {
        List<Component> parts = name.getSiblings();
        MutableComponent out = Component.empty();
        int span = hasMax ? 4 : 2;
        int start = -1;
        for (int i = 0; i < parts.size(); i++) {
            if (start < 0 && i < parts.size() - span) {
                StringBuilder sb = new StringBuilder();
                for (Component c : parts.subList(i + 1, i + span + 1)) sb.append(c.getString());
                if (sb.toString().equals(match)) start = i;
            }
            if (start >= 0 && i - start <= span) {
                int at = i - start;
                boolean drop;
                if (hasMax) drop = switch (at) {
                    case 0, 4 -> removeValue && removeMax;
                    case 1 -> removeValue;
                    default -> removeMax;
                };
                else drop = removeValue;
                if (drop) continue;
            }
            out.append(parts.get(i));
        }
        return start < 0 ? null : out;
    }

    private static void readBars(ArmorStand st, boolean onlyHealth, boolean removeValue, boolean removeMax) {
        if (!st.isInvisible() || !st.hasCustomName() || !st.isCustomNameVisible()) return;
        Component name = st.getCustomName();
        String text = name.getString();
        Matcher m = HEALTH.matcher(text);
        if (m.find()) {
            HEALTHS.put(st, (float) (number(m.group(1)) / number(m.group(4))));
            if ((removeValue || removeMax) && !keepName(text)) {
                Component clean = cleaned(name, m.group(0), true, removeValue, removeMax);
                if (clean != null) st.setCustomName(clean);
            }
            return;
        }
        if (!onlyHealth) return;
        m = HEALTH_ONLY.matcher(text);
        if (!m.find()) return;
        long current = (long) number(m.group(1));
        STARTING.putIfAbsent(st, current);
        HEALTHS.put(st, (float) current / STARTING.get(st));
        if (removeValue && !keepName(text)) {
            Component clean = cleaned(name, m.group(0), false, true, true);
            if (clean != null) st.setCustomName(clean);
        }
    }

    private static void barsTick(Minecraft mc) {
        if (++barTick % 5 != 0 || mc.level == null || mc.player == null) return;
        Feature f = Features.get(BARS);
        boolean only = f.option("mob_health_bars.healthOnly").bool(), value = f.option("mob_health_bars.removeValue").bool(), max = f.option("mob_health_bars.removeMax").bool();
        HEALTHS.keySet().removeIf(s -> s.isRemoved() || s.isDeadOrDying());
        STARTING.keySet().removeIf(Entity::isRemoved);
        for (ArmorStand st : mc.level.getEntitiesOfClass(ArmorStand.class, mc.player.getBoundingBox().inflate(32))) readBars(st, only, value, max);
    }

    private static String bar(float health, int cells, int full, int half, int empty) {
        int on = Math.round(Math.min(1, Math.max(0, health)) * cells);
        int colour = health > 0.5f ? full : health > 0.2f ? half : empty;
        return code(colour) + "|".repeat(on) + "§8" + "|".repeat(cells - on);
    }

    // nearest of the games 16 colors, a label only takes those
    private static String code(int rgb) {
        int[] table = {0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA, 0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF};
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255, best = 15, bestD = Integer.MAX_VALUE;
        for (int i = 0; i < 16; i++) {
            int dr = (table[i] >> 16 & 255) - r, dg = (table[i] >> 8 & 255) - g, db = (table[i] & 255) - b, d = dr * dr + dg * dg + db * db;
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return "§" + Integer.toHexString(best);
    }

    private static void barsWorld(WorldDraw w) {
        if (HEALTHS.isEmpty()) return;
        Feature f = Features.get(BARS);
        boolean hideFull = f.option("mob_health_bars.hideFull").bool();
        int cells = (int) Math.round(f.option("mob_health_bars.scale").number() * 14);
        int full = f.option("mob_health_bars.fullColor").color(), half = f.option("mob_health_bars.halfColor").color(), empty = f.option("mob_health_bars.emptyColor").color();
        for (Map.Entry<ArmorStand, Float> e : HEALTHS.entrySet()) {
            if (hideFull && e.getValue() >= 1) continue;
            ArmorStand st = e.getKey();
            if (!st.shouldShowName()) continue;
            w.label(bar(e.getValue(), cells, full, half, empty), st.getEyePosition().add(0, 0.25, 0), false);
        }
    }

    public static void register() {
        Features.add(new Feature(WAND, "World", "Builder's Wand preview", "On your Private Island and Garden: the blocks a Builder's Wand or Builder's Ruler would place, or remove while you sneak with the ruler.", "Skyblocker: Builder's Wand Preview")
                .when(() -> GameState.inSkyBlock && ("PRIVATE_ISLAND".equals(GameState.island) || "GARDEN".equals(GameState.island))).tick(SbHelpers::wandTick).world(SbHelpers::wandWorld).left(() -> preview = List.of())
                .option(Option.number("builders_wand.opacity", "Preview opacity", "", 0.5, 0.05, 1, 0.05)));
        Features.add(new Feature(BARS, "Combat", "Mob health bars", "A bar of health over the name of each mob, from the numbers in its name. Other helpers read those numbers too, so taking them out of the name starts off.", "Skyblocker: Health Bars")
                .when(() -> GameState.inSkyBlock).defaultOff().tick(SbHelpers::barsTick).world(SbHelpers::barsWorld).left(() -> {
                    HEALTHS.clear();
                    STARTING.clear();
                })
                .option(Option.number("mob_health_bars.scale", "Size", "", 1.5, 0.5, 4, 0.1))
                .option(Option.bool("mob_health_bars.hideFull", "Hide at full health", "", false))
                .option(Option.bool("mob_health_bars.healthOnly", "Also mobs that only show their current health", "The first number seen counts as full, which is wrong if the mob was already hurt.", true))
                .option(Option.bool("mob_health_bars.removeValue", "Take the current health out of the name", "", false))
                .option(Option.bool("mob_health_bars.removeMax", "Take the maximum health out of the name", "", false))
                .option(Option.color("mob_health_bars.fullColor", "Full", "", 0xFF55FF55))
                .option(Option.color("mob_health_bars.halfColor", "Half", "", 0xFFFFFF55))
                .option(Option.color("mob_health_bars.emptyColor", "Almost empty", "", 0xFFFF5555)));
    }
}
