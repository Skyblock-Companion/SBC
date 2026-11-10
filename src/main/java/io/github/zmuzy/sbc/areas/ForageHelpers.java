// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni and Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.areas;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Repo;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.item.menus.MenuView;
import io.github.zmuzy.sbc.mc.Interactions;
import io.github.zmuzy.sbc.mc.SoundHooks;
import io.github.zmuzy.sbc.nav.Graph;
import io.github.zmuzy.sbc.nav.Navigator;
import io.github.zmuzy.sbc.render.WorldDraw;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.track.Profit;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.SideHelper;
import io.github.zmuzy.sbc.ui.Toasts;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SeaPickleBlock;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// foraging. from skyhanni ClearTreeLogs, ForagingBeaconSolver (+ skyblocker TunerSolver),
// ForagingBeaconWarning, HoneyhiveReminder, the Mute classes, WhispersPerHotfPerk + HotxFeatures
public final class ForageHelpers {
    private static final BooleanSupplier SKYBLOCK = () -> GameState.inSkyBlock || SbcMod.DEV;

    private ForageHelpers() {}

    private static boolean customTrees() {
        return "GALATEA".equals(GameState.island) || "TORRHUS_CANYON".equals(GameState.island) || SbcMod.DEV;
    }

    private static boolean foragingIsland() {
        return customTrees() || "THE_PARK".equals(GameState.island);
    }

    private static boolean opt(String id, String key) {
        return Features.get(id).option(id + "." + key).bool();
    }

    // --- skyhanni ClearTreeLogs ---

    private static final String CLEAN = "foraging_clean_tree";
    private static Set<Block> treeBlocks;

    private static Set<Block> treeBlocks() {
        if (treeBlocks == null) treeBlocks = Set.of(Blocks.STRIPPED_SPRUCE_WOOD, Blocks.MANGROVE_WOOD, Blocks.MANGROVE_LEAVES, Blocks.AZALEA_LEAVES, Blocks.STRIPPED_BIRCH_WOOD, Blocks.STRIPPED_MANGROVE_WOOD, Blocks.OAK_LEAVES);
        return treeBlocks;
    }

    private static final FishHooks.Scan CLEAN_SCAN = new FishHooks.Scan() {
        @Override
        public boolean wanted() {
            return Features.on(CLEAN) && customTrees();
        }

        @Override
        public void entity(Entity e) {
            if (!(e instanceof Display.BlockDisplay d) || d.blockRenderState() == null) return;
            BlockState state = d.blockRenderState().blockState();
            if (!treeBlocks().contains(state.getBlock())) return;
            boolean blocks = opt(CLEAN, "blocks"), runes = opt(CLEAN, "runes");
            if (blocks && runes) {
                FishHooks.hide(d);
                return;
            }
            Boolean floating = floating(d);
            if (floating != null && (floating ? blocks : runes)) FishHooks.hide(d);
        }

        // from skyhanni isFloatingTreeBlock: looks flat ahead, even scale from 0.4 to 1
        private Boolean floating(Display d) {
            if (Math.abs(d.getLookAngle().y) > 1e-6) return false;
            Display.RenderState rs = d.renderState();
            if (rs == null) return null;
            var scale = rs.transformation().get(0f).scale();
            if (scale.x() != scale.y() || scale.x() != scale.z()) return false;
            return scale.x() >= 0.4f && scale.x() <= 1f;
        }
    };

    // --- mutes ---

    private static final String MUTE = "foraging_mute";

    private static boolean muted(SoundHooks.Sound s) {
        if (!Features.on(MUTE)) return false;
        boolean galatea = "GALATEA".equals(GameState.island);
        if (galatea && opt(MUTE, "fusion") && s.volume() == 20f && (s.name().equals("entity.firework_rocket.blast") || s.name().equals("entity.firework_rocket.blast_far"))) return true;
        if ((galatea || "SAFARI".equals(GameState.island)) && opt(MUTE, "phantoms") && s.name().startsWith("entity.phantom")) return true;
        if (s.name().equals("entity.creaking.death") && opt(MUTE, "tree")) return !customTrees() || opt(MUTE, "tree_galatea");
        return false;
    }

    // --- chat ---

    private static final Pattern BUFF = Pattern.compile("^(?:New day! Your (?:Lottery|Beekeeper) buff changed!|New buff: Gain (?:.+) (?:Fig Fortune|Mangrove Fortune|Helix Fortune|Sweep)\\.|New buff: .*(?:Honeyhives?|Honeycomb|Critter|Trees lathered).*\\.|You can disable this messaging by toggling (?:Lottery|BeeKeeper) in your /hotf!)$"),
            UNMINEABLE = Pattern.compile("^(?:You cannot damage a tree while it is regenerating!|The toughness of this tree is way too high!)$"),
            TIMBER = Pattern.compile("TIMBER! You felled the entire \\w+ Tree!");

    private static ChatBus.Result chat(ChatBus.Line line) {
        String t = line.plain().trim();
        if (opt("foraging_chat", "buffs") && !foragingIsland() && BUFF.matcher(t).matches()) return ChatBus.CANCEL;
        if (opt("foraging_chat", "unmineable") && customTrees() && UNMINEABLE.matcher(t).matches()) return ChatBus.CANCEL;
        return ChatBus.PASS;
    }

    private static ChatBus.Result timber(ChatBus.Line line) {
        if (foragingIsland() && TIMBER.matcher(line.plain().trim()).matches()) FishHooks.title("§b§lTree felled!");
        return ChatBus.PASS;
    }

    // --- sweep details card ---

    private static final String SWEEP_CARD = "foraging_sweep_card";

    private static RichCard.Content sweepContent(String[] s) {
        List<RichCard.Line> out = new ArrayList<>();
        out.add(RichCard.Line.row("Sweep", "§2" + s[0] + "∮").raw());
        if (!s[1].isEmpty()) out.add(RichCard.Line.row("Tree", "§f" + s[1]).raw());
        if (!s[2].isEmpty()) out.add(RichCard.Line.row("Toughness", "§6" + s[2]).raw());
        if (!s[3].isEmpty()) out.add(RichCard.Line.row("Logs", "§a" + s[3]).raw());
        if (!s[4].isEmpty()) for (String p : s[4].split(", ")) {
            int cut = p.indexOf(" -");
            out.add(RichCard.Line.row(cut > 0 ? p.substring(0, cut) : p, "§c" + (cut > 0 ? p.substring(cut + 1).replace(" Sweep", "") : "")).raw());
        }
        return new RichCard.Content("", out);
    }

    private static final RichCard SWEEP = new RichCard(SWEEP_CARD, "Sweep details", "Foraging", Zone.TOP_LEFT, 37, SWEEP_CARD) {
        @Override
        public boolean visible() {
            return FishForageHunt.lastSweep() != null || SbcMod.DEV;
        }

        @Override
        protected Content live() {
            String[] s = FishForageHunt.lastSweep();
            return s == null ? new Content("", List.of()) : sweepContent(s);
        }

        @Override
        protected Content sample() {
            return sweepContent(new String[]{"442.76", "Fig", "3.5", "9.02", "Axe throw -50% Sweep"});
        }

        @Override
        protected List<Row> classicRows() {
            return List.of();
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Sweep", "442", 0));
        }
    };

    // --- skyblocker SweepOverlay ---

    private static final String OVERLAY = "foraging_sweep_overlay";
    private static final Pattern SWEEP_STAT = Pattern.compile("Sweep:\\s*[^\\d\\s]*(\\d+)");
    private static final Set<String> VALID_AXES = Set.of("JUNGLE_AXE", "TREECAPITATOR_AXE", "FIG_AXE", "FIGSTONE_AXE", "HELIX_CHOPPER", "ROOKIE_AXE", "PROMISING_AXE", "SWEET_AXE", "EFFICIENT_AXE"),
            THROWABLE_AXES = Set.of("JUNGLE_AXE", "TREECAPITATOR_AXE", "FIG_AXE", "FIGSTONE_AXE", "HELIX_CHOPPER");
    private static final int[] FIG = {7, 9, 11, 16, 21, 26, 34, 42, 50, 62, 74, 86, 102, 118, 134, 154, 174, 194, 214, 234},
            MANGROVE = {50, 55, 60, 75, 90, 105, 130, 155, 180, 230, 280, 330, 405, 480, 555, 655, 755, 855, 955, 1055},
            HELIX = {150, 160, 170, 200, 230, 260, 320, 380, 440, 540, 640, 740, 890, 1040, 1190, 1390, 1590, 1790, 1990, 2190};
    private static final int MAX_BASIC_WOOD_CUT = 36;
    private static List<BlockPos> sweepBlocks = List.of();
    private static boolean sweepThrown, sweepNoticeShown;
    private static int overlayTicks;

    private static boolean figLog(BlockState s) {
        return s.is(Blocks.STRIPPED_SPRUCE_LOG) || s.is(Blocks.STRIPPED_SPRUCE_WOOD);
    }

    private static boolean mangroveLog(BlockState s) {
        return s.is(Blocks.MANGROVE_LOG) || s.is(Blocks.MANGROVE_WOOD);
    }

    private static boolean helixLog(BlockState s) {
        return s.is(Blocks.STRIPPED_BIRCH_LOG) || s.is(Blocks.STRIPPED_BIRCH_WOOD) || s.is(Blocks.STRIPPED_MANGROVE_LOG) || s.is(Blocks.STRIPPED_MANGROVE_WOOD);
    }

    private static boolean isLog(BlockState s) {
        return switch (GameState.island) {
            case "GALATEA" -> figLog(s) || mangroveLog(s);
            case "TORRHUS_CANYON" -> helixLog(s);
            case "HUB" -> s.is(Blocks.OAK_LOG) || s.is(Blocks.OAK_WOOD);
            default -> s.is(BlockTags.LOGS);
        };
    }

    // from skyblocker shouldBeChopped: a helix tree gets cut one wood color at a time
    private static boolean sameWood(BlockState from, BlockState to) {
        if (!"TORRHUS_CANYON".equals(GameState.island)) return true;
        boolean birch = from.is(Blocks.STRIPPED_BIRCH_LOG) || from.is(Blocks.STRIPPED_BIRCH_WOOD), toBirch = to.is(Blocks.STRIPPED_BIRCH_LOG) || to.is(Blocks.STRIPPED_BIRCH_WOOD);
        return birch == toBirch;
    }

    static int maxWood(float sweep, int[] values) {
        int logs = values.length;
        for (int i = 0; i < values.length; i++) {
            if (sweep < values[i]) {
                logs -= values.length - i;
                break;
            }
        }
        return logs;
    }

    private static float sweepStat() {
        for (String line : GameState.tab) {
            Matcher m = SWEEP_STAT.matcher(GameState.strip(line));
            if (m.find()) return Float.parseFloat(m.group(1));
        }
        if (!sweepNoticeShown && customTrees()) {
            FishHooks.say("§b[SBC] §cAdd the Sweep stat to your player list widgets with /widgets for the sweep overlay.");
            sweepNoticeShown = true;
        }
        return 0;
    }

    private static int maxFor(float sweep, BlockState state) {
        if ("THE_PARK".equals(GameState.island) || "HUB".equals(GameState.island)) return (int) Math.min(1 + sweep, MAX_BASIC_WOOD_CUT);
        if ("GALATEA".equals(GameState.island)) return figLog(state) ? maxWood(sweep, FIG) : mangroveLog(state) ? maxWood(sweep, MANGROVE) : 0;
        if ("TORRHUS_CANYON".equals(GameState.island) && helixLog(state)) return maxWood(sweep, HELIX);
        return 0;
    }

    private static BlockPos thrown(Vec3 start, Vec3 look, double pitch, Minecraft mc) {
        double angle = Math.abs(pitch / 90);
        double gravity = ((1 - angle) * (2.65 + 2.51827 * angle - 2.43475 * angle * angle + 1.22748 * angle * angle * angle)) / 1000;
        for (int i = 0; i < 50; i++) {
            Vec3 pos = start.add(look.scale(i)).subtract(0, gravity * i * i, 0);
            BlockPos b = BlockPos.containing(pos);
            if (isLog(mc.level.getBlockState(b))) return b;
        }
        return null;
    }

    private static void overlayTick(Minecraft mc) {
        if (++overlayTicks % 2 != 0) return;
        sweepBlocks = List.of();
        if (mc.player == null || mc.level == null) return;
        String id = Gain.sbId(mc.player.getMainHandItem());
        boolean valid = VALID_AXES.contains(id), throwable = THROWABLE_AXES.contains(id);
        if (!valid && !throwable) return;
        BlockPos start = null;
        boolean isThrown = false;
        if (valid && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK && mc.hitResult instanceof BlockHitResult hit) start = hit.getBlockPos();
        else if (throwable && opt(OVERLAY, "thrown") && !mc.player.getCooldowns().isOnCooldown(mc.player.getMainHandItem())) {
            start = thrown(mc.player.getEyePosition(1f), mc.player.getViewVector(1f), mc.player.getXRot(), mc);
            isThrown = start != null;
        }
        if (start == null || !isLog(mc.level.getBlockState(start))) return;
        float sweep = sweepStat();
        if (sweep <= 0) return;
        int max = maxFor(sweep, mc.level.getBlockState(start));
        if (isThrown) max /= 2;
        List<BlockPos> out = new ArrayList<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        visited.add(start);
        while (out.size() < max && !queue.isEmpty()) {
            BlockPos pos = queue.poll();
            BlockState here = mc.level.getBlockState(pos);
            if (!isLog(here)) continue;
            out.add(pos);
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = pos.offset(dx, dy, dz);
                        if (visited.contains(n)) continue;
                        BlockState ns = mc.level.getBlockState(n);
                        if (isLog(ns) && sameWood(here, ns)) {
                            visited.add(n);
                            queue.add(n);
                        }
                    }
        }
        sweepBlocks = out;
        sweepThrown = isThrown;
    }

    private static void overlayDraw(WorldDraw w) {
        if (sweepBlocks.isEmpty()) return;
        int c = Features.get(OVERLAY).option(OVERLAY + ".color").color();
        float alpha = ((c >>> 24) & 0xFF) / 255f, dim = sweepThrown ? 0.7f : 1f;
        int rgb = ((int) (((c >> 16) & 0xFF) * dim)) << 16 | ((int) (((c >> 8) & 0xFF) * dim)) << 8 | (int) ((c & 0xFF) * dim);
        for (BlockPos p : sweepBlocks) w.filledBox(new AABB(p), rgb, alpha, false);
    }

    // --- block highlighters ---

    private static final String BLOCKS = "foraging_highlights";
    private static final int SCAN_RADIUS = 32, SCAN_HEIGHT = 24;
    private static final Map<BlockPos, Integer> found = new HashMap<>();
    private static final Map<BlockPos, String> foundName = new HashMap<>();
    private static int scanX;

    // skyblockers predicates. its chunk events are one slice of the area per tick here
    private static void classify(Minecraft mc, BlockPos p, BlockState s) {
        boolean galatea = "GALATEA".equals(GameState.island);
        int color = 0;
        String name = null;
        if (galatea && s.is(Blocks.FLOWERING_AZALEA) && opt(BLOCKS, "lushlilac")) {
            color = DyeColor.MAGENTA.getTextColor();
            name = "Lushlilac";
        } else if (galatea && s.is(Blocks.SEA_PICKLE) && opt(BLOCKS, "sea_lumies") && s.getValue(SeaPickleBlock.PICKLES) >= Features.get(BLOCKS).option(BLOCKS + ".sea_lumies_min").number()) {
            color = DyeColor.CYAN.getTextColor();
            name = "Sea Lumies";
        } else if (!galatea && s.is(Blocks.BEE_NEST) && opt(BLOCKS, "honeyhive") && s.getValue(BeehiveBlock.HONEY_LEVEL) == BeehiveBlock.MAX_HONEY_LEVELS && !mc.level.getBlockState(p.above()).is(Blocks.BIRCH_FENCE)) {
            color = DyeColor.BLUE.getTextColor();
            name = "Honeyhive";
        } else if (!galatea && s.is(Blocks.CRIMSON_FUNGUS) && opt(BLOCKS, "ruby_veilshroom")) {
            color = DyeColor.PURPLE.getTextColor();
            name = "Ruby Veilshroom";
        }
        BlockPos key = p.immutable();
        if (name == null) {
            found.remove(key);
            foundName.remove(key);
        } else {
            found.put(key, color);
            foundName.put(key, name);
        }
    }

    private static void blocksTick(Minecraft mc) {
        if (mc.player == null || mc.level == null) return;
        BlockPos me = mc.player.blockPosition();
        int x = me.getX() - SCAN_RADIUS + scanX;
        scanX = (scanX + 1) % (SCAN_RADIUS * 2 + 1);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int z = me.getZ() - SCAN_RADIUS; z <= me.getZ() + SCAN_RADIUS; z++) {
            for (int y = me.getY() - SCAN_HEIGHT; y <= me.getY() + SCAN_HEIGHT; y++) {
                p.set(x, y, z);
                if (found.containsKey(p)) classify(mc, p, mc.level.getBlockState(p));
                else {
                    BlockState s = mc.level.getBlockState(p);
                    if (!s.isAir()) classify(mc, p, s);
                }
            }
        }
        found.keySet().removeIf(b -> {
            boolean far = b.distSqr(me) > 4 * SCAN_RADIUS * SCAN_RADIUS;
            if (far) foundName.remove(b);
            return far;
        });
    }

    private static void blocksDraw(WorldDraw w) {
        for (var e : found.entrySet()) {
            BlockPos p = e.getKey();
            w.filledBox(new AABB(p), e.getValue(), 0.4f, false);
            Vec3 c = Vec3.atCenterOf(p);
            double d = c.distanceTo(w.eye);
            if (d < 16) w.label("§f" + foundName.get(p) + " §7" + (int) d + "m", c.add(0, 0.8, 0), false);
        }
    }

    // --- temples ---

    private static final String TEMPLES = "foraging_temples";
    private static final BlockPos WALL_TOP_LEFT = new BlockPos(-633, 66, 85), WALL_BOTTOM_RIGHT = new BlockPos(-640, 65, 85), FLOOR_TOP_LEFT = new BlockPos(-633, 59, 76), FLOOR_BOTTOM_LEFT = new BlockPos(-633, 59, 75);
    private static final Map<DyeColor, BlockPos> BUTTONS = Map.ofEntries(Map.entry(DyeColor.BROWN, new BlockPos(-611, 49, 232)), Map.entry(DyeColor.RED, new BlockPos(-612, 49, 232)), Map.entry(DyeColor.ORANGE, new BlockPos(-613, 49, 232)),
            Map.entry(DyeColor.YELLOW, new BlockPos(-614, 48, 232)), Map.entry(DyeColor.LIME, new BlockPos(-614, 47, 232)), Map.entry(DyeColor.GREEN, new BlockPos(-614, 46, 232)),
            Map.entry(DyeColor.CYAN, new BlockPos(-613, 45, 232)), Map.entry(DyeColor.LIGHT_BLUE, new BlockPos(-612, 45, 232)), Map.entry(DyeColor.BLUE, new BlockPos(-611, 45, 232)),
            Map.entry(DyeColor.PURPLE, new BlockPos(-610, 46, 232)), Map.entry(DyeColor.MAGENTA, new BlockPos(-610, 47, 232)), Map.entry(DyeColor.PINK, new BlockPos(-610, 48, 232)));
    private static final BlockPos GLASS_FLOOR = new BlockPos(-612, 42, 227), GLASS_WHEEL = new BlockPos(-612, 47, 233);
    private static final BlockPos[][] STATUES = {{new BlockPos(-618, 46, 248), new BlockPos(-616, 46, 248)}, {new BlockPos(-607, 46, 249), new BlockPos(-607, 46, 247)}, {new BlockPos(-606, 46, 240), new BlockPos(-608, 46, 240)}, {new BlockPos(-617, 46, 241), new BlockPos(-617, 46, 239)}};
    private static List<DyeColor> buttonOrder = List.of();
    private static long orderAt;

    private static Direction terracottaMap(Direction d) {
        return switch (d) {
            case NORTH -> Direction.WEST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.EAST;
            default -> Direction.NORTH;
        };
    }

    // clicks a floor block needs: right clicks go one way, left clicks (below 0) the other, the shorter way wins
    static int rotations(Direction floor, Direction wanted) {
        Direction cw = floor, ccw = floor;
        for (int i = 1; i <= 4 && floor != wanted; i++) {
            cw = cw.getClockWise();
            if (cw == wanted) return i;
            ccw = ccw.getCounterClockWise();
            if (ccw == wanted) return -i;
        }
        return 0;
    }

    private static void terracotta(WorldDraw w) {
        var level = w.mc.level;
        int index = 0;
        for (int x = WALL_TOP_LEFT.getX(); x >= WALL_BOTTOM_RIGHT.getX(); x--) {
            for (int y = WALL_TOP_LEFT.getY(); y >= WALL_BOTTOM_RIGHT.getY(); y--) {
                BlockState wall = level.getBlockState(new BlockPos(x, y, WALL_TOP_LEFT.getZ()));
                if (!wall.is(Blocks.GLAZED_TERRACOTTA.orange()) || !wall.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) continue;
                Direction wanted = terracottaMap(wall.getValue(BlockStateProperties.HORIZONTAL_FACING));
                int row = index / 2;
                BlockPos floorAt = (index % 2 == 0 ? FLOOR_TOP_LEFT : FLOOR_BOTTOM_LEFT).offset(-row, 0, 0);
                index++;
                BlockState floor = level.getBlockState(floorAt);
                if (!floor.is(Blocks.GLAZED_TERRACOTTA.orange()) || !floor.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) continue;
                int n = rotations(floor.getValue(BlockStateProperties.HORIZONTAL_FACING), wanted);
                if (n != 0) w.label("§9" + n, Vec3.atCenterOf(floorAt).add(0, 1, 0), false);
            }
        }
    }

    // from skyhanni DesertTemplePuzzle.solve: fewest glass blocks of a color to most
    private static List<DyeColor> solveFloor(Minecraft mc) {
        Map<DyeColor, Integer> counts = new HashMap<>();
        Set<BlockPos> visited = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        queue.add(GLASS_FLOOR);
        while (!queue.isEmpty()) {
            BlockPos here = queue.poll();
            if (mc.level.getBlockState(here).getBlock() instanceof StainedGlassBlock g) counts.merge(g.getColor(), 1, Integer::sum);
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockPos n = here.relative(d);
                if (!visited.contains(n) && mc.level.getBlockState(n).getBlock() instanceof StainedGlassBlock) {
                    visited.add(n);
                    queue.add(n);
                }
            }
        }
        List<Map.Entry<DyeColor, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort(Map.Entry.comparingByValue());
        List<DyeColor> out = new ArrayList<>();
        for (var e : list) out.add(e.getKey());
        return out;
    }

    private static void temples(WorldDraw w) {
        if (w.mc.level == null) return;
        String area = GameState.area;
        if ("GALATEA".equals(GameState.island) && area.equals("Forest Temple") && opt(TEMPLES, "forest")) terracotta(w);
        if (!"TORRHUS_CANYON".equals(GameState.island) || !(area.equals("Torrhus Springs") || area.equals("Desert Temple")) || !opt(TEMPLES, "desert")) return;
        long now = System.currentTimeMillis();
        if (now - orderAt > 5000) {
            orderAt = now;
            buttonOrder = solveFloor(w.mc);
        }
        if (!buttonOrder.isEmpty() && !w.mc.level.getBlockState(GLASS_WHEEL).isAir()) {
            for (int i = 0; i < buttonOrder.size(); i++) {
                BlockPos p = BUTTONS.get(buttonOrder.get(i));
                if (p != null) w.label("§f" + (i + 1), Vec3.atBottomCenterOf(p).add(0, 0.2, 0.2), false);
            }
        }
        for (BlockPos[] s : STATUES) {
            Vec3 a = Vec3.atCenterOf(s[0]), b = Vec3.atCenterOf(s[1]);
            w.line(a, b, 0xFFFFFFFF, 5f, false);
            Vec3 dir = b.subtract(a).normalize(), side = new Vec3(-dir.z, 0, dir.x).scale(0.2);
            w.line(b, b.subtract(dir.scale(0.2)).add(side), 0xFFFFFFFF, 5f, false);
            w.line(b, b.subtract(dir.scale(0.2)).subtract(side), 0xFFFFFFFF, 5f, false);
        }
    }

    // --- skyhanni ForagingBeaconSolver ---

    private static final String BEACON = "foraging_beacon";
    private static final String[] COLORS = {"white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "cyan", "purple", "blue", "brown", "green", "red"};
    private static final int[] SPEED_TICKS = {52, 42, 32, 22, 12};
    private static final String[] PITCHES = {"Low", "Normal", "High"};
    private static final float[] PITCH_VALUES = {0.0952381f, 0.7936508f, 1.4920635f};
    private static final long PITCH_MARGIN = 50;
    private static final int COLOR_SLOT = 46, SPEED_SLOT = 48, PITCH_SLOT = 50;

    // shortest way around the wheel, like skyhannis internalGetOffset
    static int offset(int ref, int ours, int size) {
        int o = ((ref - ours) + size) % size;
        return o > size / 2 ? o - size : o;
    }

    static int closestSpeed(double ticks) {
        int best = 0;
        for (int i = 1; i < SPEED_TICKS.length; i++) if (Math.abs(SPEED_TICKS[i] - (int) ticks) < Math.abs(SPEED_TICKS[best] - (int) ticks)) best = i;
        return best;
    }

    private static final class Tune {
        final boolean enchanted;
        final int slotOffset;
        int refColor = -1, ourColor = -1, refSpeed = -1, ourSpeed = -1, refPitch = -1, ourPitch = -1;
        long nextRef, nextOur;
        final List<Integer> buffer = new ArrayList<>(), recent = new ArrayList<>();
        int refSlot = 10, lastTicks;
        int seenRefSlot = -1;

        Tune(boolean enchanted, boolean upgrading) {
            this.enchanted = enchanted;
            this.slotOffset = upgrading && !enchanted ? -9 : 0;
        }

        long untilRef(long now) {
            return nextRef == 0 ? Long.MAX_VALUE : nextRef - now;
        }

        long untilOur(long now) {
            return nextOur == 0 ? Long.MAX_VALUE : nextOur - now;
        }

        void handlePitch(int pitch, long now) {
            if (enchanted && !beaconGlowUp) return;
            if (untilRef(now) > PITCH_MARGIN || untilRef(now) > untilOur(now)) return;
            if (buffer.size() >= 6) buffer.remove(0);
            buffer.add(pitch);
            if (new HashSet<>(buffer).size() > 2) buffer.clear();
            if (buffer.size() < 3) return;
            int[] c = new int[3];
            for (int p : buffer) c[p]++;
            int best = 0;
            for (int i = 1; i < 3; i++) if (c[i] > c[best]) best = i;
            refPitch = best;
        }

        void updateMatchSlot(int slot, long now) {
            if (slot == refSlot) return;
            refSlot = slot;
            int diff = beaconTicks - lastTicks;
            if (diff <= 0) return;
            recent.add(diff);
            lastTicks = beaconTicks;
            if (beaconGlowUp && recent.size() < 3) return;
            List<Integer> last = new ArrayList<>(recent.subList(Math.max(0, recent.size() - 10), recent.size()));
            List<Integer> sorted = new ArrayList<>(last);
            java.util.Collections.sort(sorted);
            double median = sorted.size() % 2 == 0 ? (sorted.get(sorted.size() / 2 - 1) + sorted.get(sorted.size() / 2)) / 2.0 : sorted.get(sorted.size() / 2);
            double sum = 0;
            int n = 0;
            for (int t : last) {
                if (t < median * 0.8 || t > median * 1.2) continue;
                sum += t;
                n++;
            }
            if (n == 0) return;
            refSpeed = closestSpeed(sum / n);
            nextRef = now + SPEED_TICKS[refSpeed] * 50L;
        }

        Integer offsetAt(int slot) {
            if (slot == COLOR_SLOT + slotOffset) return refColor < 0 || ourColor < 0 ? null : (Integer) offset(refColor, ourColor, COLORS.length);
            if (slot == SPEED_SLOT + slotOffset) return refSpeed < 0 || ourSpeed < 0 ? null : (Integer) offset(refSpeed, ourSpeed, SPEED_TICKS.length);
            if (slot == PITCH_SLOT + slotOffset) return refPitch < 0 || ourPitch < 0 ? null : (Integer) offset(refPitch, ourPitch, PITCHES.length);
            return null;
        }
    }

    private static boolean beaconOpen, beaconGlowUp;
    private static int beaconTicks;
    private static Tune tuneNormal, tuneEnchanted;

    private static final Pattern B_MENU = Patterns.get("SH.foraging.beacon.inventory");

    private static boolean beaconTitle(String title) {
        return B_MENU.matcher(title).matches();
    }

    private static void beaconRead(MenuView v) {
        if (!customTrees() || !Features.on(BEACON)) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof AbstractContainerScreen<?> screen)) return;
        boolean upgrading = v.title.startsWith("Upgrade Signal Strength");
        if (!beaconOpen || upgrading != beaconGlowUp) {
            beaconOpen = true;
            beaconGlowUp = upgrading;
            beaconTicks = 0;
            tuneNormal = new Tune(false, upgrading);
            tuneEnchanted = new Tune(true, upgrading);
            if (mc.player != null && "MUSIC_PANTS".equals(Gain.sbId(mc.player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS))))
                Toasts.push("Beacon solver", "May not work properly with Stereo Pants on", 0xFFFFD866);
        }
        long now = System.currentTimeMillis();
        var slots = screen.getMenu().slots;
        for (int i = 0; i < v.top && i < slots.size(); i++) {
            ItemStack st = slots.get(i).getItem();
            if (st.isEmpty()) continue;
            Tune t = st.hasFoil() ? tuneEnchanted : tuneNormal;
            MenuView.Item it = v.item(i);
            boolean match = i >= 10 && i <= 16, change = i >= 28 && i <= 34;
            if (match || change) {
                int c = java.util.Arrays.asList(COLORS).indexOf(it.model.replace("_stained_glass_pane", ""));
                if (c >= 0) {
                    if (match) {
                        t.refColor = c;
                        t.updateMatchSlot(i, now);
                    } else t.ourColor = c;
                }
            }
            if (t.enchanted && !upgrading) continue;
            if (i == COLOR_SLOT + t.slotOffset) {
                Matcher m = it.firstClean(Patterns.get("SH.foraging.beacon.color"));
                t.ourColor = m == null ? -1 : java.util.Arrays.asList(COLORS).indexOf(m.group("color").toLowerCase(Locale.ROOT).replace(' ', '_'));
            } else if (i == SPEED_SLOT + t.slotOffset) {
                Matcher m = it.firstClean(Patterns.get("SH.foraging.beacon.speed"));
                t.ourSpeed = m == null ? -1 : Integer.parseInt(m.group("speed")) - 1;
                t.nextOur = t.ourSpeed < 0 || t.ourSpeed >= SPEED_TICKS.length ? 0 : now + SPEED_TICKS[t.ourSpeed] * 50L;
            } else if (i == PITCH_SLOT + t.slotOffset) {
                Matcher m = it.firstClean(Patterns.get("SH.foraging.beacon.pitch"));
                t.ourPitch = m == null ? -1 : java.util.Arrays.asList(PITCHES).indexOf(m.group("pitch"));
            }
        }
        for (Tune t : new Tune[]{tuneNormal, tuneEnchanted}) {
            if (t.enchanted && !upgrading) continue;
            for (int slot : new int[]{COLOR_SLOT + t.slotOffset, SPEED_SLOT + t.slotOffset, PITCH_SLOT + t.slotOffset}) {
                Integer o = t.offsetAt(slot);
                if (o == null) continue;
                if (o == 0) v.back(slot, MenuView.GREEN);
                else v.corner(slot, MenuView.BOTTOM_RIGHT, String.valueOf(o), 0x55FF55);
            }
        }
    }

    private static void beaconTick(Minecraft mc) {
        if (beaconOpen) {
            Screen s = mc.gui.screen();
            if (s == null || !beaconTitle(GameState.strip(s.getTitle().getString()))) beaconReset();
            else beaconTicks++;
        }
        // from skyhanni ForagingBeaconWarning: the widget says so in the tab
        if (++beaconAlertTicks % 20 != 0 || !opt(BEACON, "alert") || System.currentTimeMillis() - lastBeaconAlert < 9 * 60_000L) return;
        boolean inWidget = false;
        for (String raw : GameState.tab) {
            String l = GameState.strip(raw);
            if (BEACON_WIDGET.matcher(l.trim()).matches()) inWidget = true;
            else if (inWidget && !l.startsWith(" ")) inWidget = false;
            if (inWidget && Patterns.get("SH.foraging.beacon.available").matcher(l).matches()) {
                FishHooks.title("§aBeacon Ready");
                FishHooks.pling();
                lastBeaconAlert = System.currentTimeMillis();
                break;
            }
        }
    }

    private static final Pattern BEACON_WIDGET = Pattern.compile("\\w+ Beacon: \\d+ Stacks?");
    private static int beaconAlertTicks;
    private static long lastBeaconAlert;

    private static void beaconReset() {
        beaconOpen = false;
        tuneNormal = tuneEnchanted = null;
    }

    private static boolean beaconSound(SoundHooks.Sound s) {
        if (!beaconOpen || !s.name().equals("block.note_block.bass") || tuneNormal == null) return false;
        int pitch = -1;
        for (int i = 0; i < 3; i++) if (PITCH_VALUES[i] == s.pitch()) pitch = i;
        if (pitch < 0) return false;
        long now = System.currentTimeMillis();
        if (!beaconGlowUp) {
            tuneNormal.handlePitch(pitch, now);
            return false;
        }
        Tune best = null;
        for (Tune t : new Tune[]{tuneNormal, tuneEnchanted})
            if (t.untilRef(now) <= PITCH_MARGIN && t.untilOur(now) > t.untilRef(now) && (best == null || t.untilRef(now) < best.untilRef(now))) best = t;
        if (best != null) best.handlePitch(pitch, now);
        return false;
    }

    private static boolean beaconClick(AbstractContainerScreen<?> screen, net.minecraft.world.inventory.Slot slot, int slotId, int button) {
        if (!beaconOpen || !Features.on(BEACON) || slot == null || tuneNormal == null) return false;
        Integer o = tuneNormal.offsetAt(slotId);
        if (o == null && beaconGlowUp) o = tuneEnchanted.offsetAt(slotId);
        Minecraft mc = Minecraft.getInstance();
        if (o != null && o == 0 && opt(BEACON, "no_over_click") && !mc.hasControlDown()) {
            FishHooks.pling();
            FishHooks.title("§cOver-click Prevented");
            return true;
        }
        if (opt(BEACON, "middle_click") && button == 0 && mc.gameMode != null && mc.player != null && slotId >= 0 && slotId < 54) {
            mc.gameMode.handleContainerInput(mc.player.containerMenu.containerId, slotId, 2, ContainerInput.CLONE, mc.player);
            return true;
        }
        return false;
    }

    private static List<AreaPanel.Row> beaconRows(AbstractContainerScreen<?> screen) {
        List<AreaPanel.Row> rows = new ArrayList<>();
        for (Tune t : new Tune[]{tuneNormal, tuneEnchanted}) {
            if (t == null || (t.enchanted && !beaconGlowUp)) continue;
            String h = t.enchanted ? "Enchanted " : "";
            rows.add(new AreaPanel.Row(h + "Colour", t.refColor < 0 ? "Unknown" : COLORS[t.refColor].replace('_', ' '), Modern.GOOD));
            rows.add(new AreaPanel.Row(h + "Speed", t.refSpeed < 0 ? "Calculating" : "Speed " + (t.refSpeed + 1), Modern.GOOD));
            rows.add(new AreaPanel.Row(h + "Pitch", t.refPitch < 0 ? "Unknown" : PITCHES[t.refPitch], Modern.GOOD));
        }
        return rows;
    }

    private static final AreaPanel BEACON_PANEL = new AreaPanel("Beacon solver", "Wanted", () -> new ItemStack(Items.BEACON), Modern.GOOD, ForageHelpers::beaconRows);

    private static SideHelper.Content beaconPanel(Screen s) {
        return Features.on(BEACON) && customTrees() && beaconTitle(GameState.strip(s.getTitle().getString())) ? BEACON_PANEL : null;
    }

    // --- skyhanni HoneyhiveReminder ---

    private static final List<Vec3> hivesToBonk = new ArrayList<>();
    private static long hivesUntil;

    private static ChatBus.Result hiveChat(ChatBus.Line line) {
        if (!"TORRHUS_CANYON".equals(GameState.island) || !Patterns.get("SH.foraging.honeyhive.hive-looted").matcher(line.plain().trim()).matches()) return ChatBus.PASS;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return ChatBus.PASS;
        if (hivesToBonk.isEmpty() || System.currentTimeMillis() > hivesUntil) {
            hivesToBonk.clear();
            Graph g = Repo.graph(GameState.island);
            if (g != null) for (Graph.Node n : g.nodes) if (n.tags().contains("honeyhive")) hivesToBonk.add(n.pos());
            hivesUntil = System.currentTimeMillis() + 3_600_000L;
        }
        Vec3 me = mc.player.position();
        hivesToBonk.stream().min(java.util.Comparator.comparingDouble(p -> p.distanceToSqr(me))).ifPresent(hivesToBonk::remove);
        return ChatBus.PASS;
    }

    private static void hiveDraw(WorldDraw w) {
        if (hivesToBonk.isEmpty() || !"TORRHUS_CANYON".equals(GameState.island) || !opt("foraging_hive", "marks")) return;
        if (System.currentTimeMillis() > hivesUntil) {
            hivesToBonk.clear();
            return;
        }
        for (Vec3 p : hivesToBonk) w.waypoint(p, 0xFFFFD866, "Honeyhive", false, true);
    }

    // --- skyhanni WhispersPerHotfPerk, HotxFeatures ---

    private static final String HOTF = "hotf_perks";
    // from skyhanni HotfData: max level + cost exponent. 0 = no cost, -1 = daily wishes straight line
    private static final Map<String, double[]> PERKS = new LinkedHashMap<>();

    static {
        Object[][] perks = io.github.zmuzy.sbc.core.game.HeartTrees.HOTF_PERKS;
        for (Object[] p : perks) PERKS.put((String) p[0], new double[]{(Integer) p[1], ((Number) p[2]).doubleValue()});
    }

    private static long whispers = -1;

    static int cost(double exp, int level) {
        return (int) (exp == -1 ? 200.0 + level * 18.0 : Math.pow(level + 1.0, exp));
    }

    // from skyhanni HotxData.calculateTotalCost
    public static long totalCost(double exp, int level) {
        long sum = 0;
        for (int l = 2; l <= level; l++) sum += cost(exp, l);
        return sum;
    }

    private static boolean enabled(MenuView.Item it) {
        Pattern p = Patterns.get("SH.foraging.hotf.perk.enable");
        for (String l : it.cleanLore) if (p.matcher(l.trim()).find()) return true;
        return false;
    }

    private static boolean locked(MenuView.Item it) {
        Pattern p = Patterns.get("SH.foraging.hotf.perk.notunlocked");
        for (String l : it.cleanLore) if (p.matcher(l).matches()) return true;
        return false;
    }

    private static void hotfMenu(MenuView v) {
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            if (it.empty()) continue;
            if (it.clean.equals("Heart of the Forest")) {
                Matcher w = it.firstClean(Patterns.get("SH.foraging.hotf.whisper.heart"));
                if (w != null && !w.group("whisper").isEmpty()) whispers = Long.parseLong(w.group("whisper").replace(",", ""));
                Matcher t = it.firstClean(Patterns.get("SH.foraging.hotf.inventory.heart.token"));
                if (t != null && opt(HOTF, "tokens") && !t.group("token").equals("0")) v.number(slot, "§b" + t.group("token"));
                continue;
            }
            if (!PERKS.containsKey(it.clean) || !opt(HOTF, "highlight")) continue;
            v.back(slot, locked(it) ? 0xC0555555 : enabled(it) ? MenuView.GREEN : MenuView.RED);
        }
    }

    private static void hotfTooltip(ItemStack stack, List<Component> tip) {
        Minecraft mc = Minecraft.getInstance();
        Screen s = mc.gui.screen();
        if (s == null || !Patterns.get("SH.foraging.hotf.inventory").matcher(GameState.strip(s.getTitle().getString())).matches()) return;
        String name = GameState.strip(stack.getHoverName().getString());
        double[] perk = PERKS.get(name);
        if (perk == null || perk[1] == 0 || tip.size() < 2) return;
        Matcher lm = Patterns.get("SH.foraging.hotf.perk.level").matcher(GameState.strip(tip.get(1).getString()).trim());
        int level = lm.matches() ? Integer.parseInt(lm.group("level")) : 0;
        int max = (int) perk[0];
        boolean maxed = level >= max;
        int costAt = -1;
        for (int i = 0; i < tip.size(); i++) if (Patterns.get("SH.mining.hotm.perk.cost.new").matcher(GameState.strip(tip.get(i).getString()).trim()).matches()) costAt = i;
        if (costAt >= 0 && !maxed && level > 0 && opt(HOTF, "current") && whispers >= 0) {
            tip.add(costAt + 2, Component.literal(" "));
            tip.add(costAt + 3, Component.literal("§7You have"));
            tip.add(costAt + 4, Component.literal("§3" + String.format(Locale.ROOT, "%,d", whispers) + " Forest Whispers"));
        }
        if (costAt >= 0 && opt(HOTF, "ten") && mc.hasShiftDown()) {
            int to = Math.min(level + 10, max), levels = Math.min(max - level, 10);
            tip.add(costAt + 2, Component.literal("§7Whispers for " + levels + (levels == 1 ? " level" : " levels") + " §e" + String.format(Locale.ROOT, "%,d", totalCost(perk[1], to) - totalCost(perk[1], level))));
        }
        if (opt(HOTF, "spent")) {
            long spent = totalCost(perk[1], level), all = totalCost(perk[1], max);
            String design = Features.get(HOTF).option(HOTF + ".design").choice();
            String pct = String.format(Locale.ROOT, "%.2f", spent * 100.0 / all), line;
            if (maxed) line = (design.equals("Percentage") ? "§e" + pct + "%" : "§e" + String.format(Locale.ROOT, "%,d", all)) + " §7(§aMax level§7)";
            else line = switch (design) {
                case "Number" -> "§e" + String.format(Locale.ROOT, "%,d", spent) + "§7 / §e" + String.format(Locale.ROOT, "%,d", all);
                case "Percentage" -> "§e" + pct + "%§7 of max";
                default -> "§e" + String.format(Locale.ROOT, "%,d", spent) + "§7/§e" + String.format(Locale.ROOT, "%,d", all) + "§7 (§e" + pct + "%§7)";
            };
            tip.add(Math.min(2, tip.size()), Component.literal("§7Whispers spent: " + line));
        }
    }

    // --- rotating perks ---

    private static final String ROTATING = "hotf_rotating";
    private static final String[][] LOTTERY = {{"fig.fortune", "+50 Fig Fortune"}, {"helix.fortune", "+50 Helix Fortune"}, {"mangrove.fortune", "+50 Mangrove Fortune"}, {"sweep", "+10 Sweep"}};
    private static final String[][] BEEKEEPER = {{"critter.speed", "Critters 20% faster"}, {"double.honeycomb", "2x Honeycomb"}, {"honeyhive.refill", "Refill 20% faster"}, {"second.critter", "Second Critter"}};
    private static String lotteryNow = "", beekeeperNow = "";

    private static ChatBus.Result rotatingChat(ChatBus.Line line) {
        String t = line.plain().trim();
        if (!t.startsWith("New buff: ")) return ChatBus.PASS;
        String body = t.substring(10);
        for (String[] l : LOTTERY) if (Patterns.get("SH.foraging.hotf.lottery.chat." + l[0]).matcher(body).matches()) lotteryNow = l[1];
        for (String[] b : BEEKEEPER) if (Patterns.get("SH.foraging.hotf.beekeeper.chat." + b[0]).matcher(body).matches()) beekeeperNow = b[1];
        return ChatBus.PASS;
    }

    private static RichCard.Content rotatingContent(String lottery, String beekeeper) {
        List<RichCard.Line> out = new ArrayList<>();
        if (!lottery.isEmpty()) out.add(RichCard.Line.row("Lottery", "§a" + lottery).raw());
        if (!beekeeper.isEmpty()) out.add(RichCard.Line.row("Beekeeper", "§a" + beekeeper).raw());
        return new RichCard.Content("", out);
    }

    private static final RichCard ROTATING_CARD = new RichCard(ROTATING, "Rotating perks", "Foraging", Zone.TOP_LEFT, 36, ROTATING) {
        @Override
        public boolean visible() {
            return (foragingIsland() && !(lotteryNow.isEmpty() && beekeeperNow.isEmpty())) || SbcMod.DEV;
        }

        @Override
        protected Content live() {
            return rotatingContent(lotteryNow, beekeeperNow);
        }

        @Override
        protected Content sample() {
            return rotatingContent("+10 Sweep", "2x Honeycomb");
        }

        @Override
        protected List<Row> classicRows() {
            return List.of();
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Lottery", "+10 Sweep", 0));
        }
    };

    // --- skyhanni ForagingTracker gift lines ---

    private static final String GIFTS = "foraging_gifts";
    private static boolean giftOpen, giftBonus;
    private static long giftAt;
    // the block comes all at once
    private static final long GIFT_MS = 2_000;
    private static String giftType = "", giftPercent = "";
    private static int giftRewards;
    private static HoverEvent giftHover;
    private static final List<String> rareDrops = new ArrayList<>();
    private static Set<String> bonusItems, bonusMobs;

    private static void bonusData() {
        if (bonusItems != null) return;
        Set<String> items = new HashSet<>(), mobs = new HashSet<>();
        JsonObject o = Repo.constant("foraging/TreeGiftBonusDrops.json");
        if (o != null) {
            for (String list : new String[]{"uncommon_drops", "enchanted_books", "boosters", "shards", "runes", "misc_drops"}) {
                JsonArray a = o.getAsJsonArray(list);
                if (a != null) for (JsonElement e : a) items.add(e.getAsString());
            }
            JsonArray m = o.getAsJsonArray("mobs");
            if (m != null) for (JsonElement e : m) mobs.add(e.getAsString());
        }
        bonusItems = items;
        bonusMobs = mobs;
    }

    private static void giftReset() {
        giftOpen = giftBonus = false;
        giftType = giftPercent = "";
        giftRewards = 0;
        giftHover = null;
        rareDrops.clear();
    }

    private static HoverEvent hoverOf(Component c) {
        if (c.getStyle().getHoverEvent() != null) return c.getStyle().getHoverEvent();
        for (Component s : c.getSiblings()) {
            HoverEvent h = hoverOf(s);
            if (h != null) return h;
        }
        return null;
    }

    static ChatBus.Result gifts(ChatBus.Line line) {
        String message = LegacyText.of(line.component());
        long now = System.currentTimeMillis();
        if (Patterns.get("SH.foraging.treegift.open-close-reward").matcher(message).matches()) {
            // a closing border that never came must not turn the next opening one into a closing one
            giftOpen = !giftOpen || now - giftAt > GIFT_MS;
            giftAt = now;
            if (giftOpen) giftBonus = false;
            else {
                if (!giftType.isEmpty() && Features.get(GIFTS).option(GIFTS + ".display").choice().equals("Compact")) sendGift();
                rareDrops.clear();
                giftHover = null;
            }
            return ChatBus.CANCEL;
        }
        if (!giftOpen) return ChatBus.PASS;
        if (now - giftAt > GIFT_MS) {
            giftOpen = false;
            return ChatBus.PASS;
        }
        if (Patterns.get("SH.foraging.treegift.bonus-gift.separator").matcher(message).matches()) {
            giftBonus = true;
            return ChatBus.CANCEL;
        }
        Matcher m = Patterns.get("SH.foraging.treegift.contribution-percentage").matcher(message);
        if (m.matches()) {
            giftPercent = m.group("percentColor") + m.group("percentage") + "%";
            giftType = m.group("type");
            return ChatBus.CANCEL;
        }
        m = Patterns.get("SH.foraging.treegift.rewards-gained").matcher(message);
        if (m.matches()) {
            giftRewards = Integer.parseInt(m.group("count").replace(",", ""));
            giftHover = hoverOf(line.component());
            return ChatBus.CANCEL;
        }
        bonusData();
        boolean list = opt(GIFTS, "bonus");
        m = Patterns.get("SH.foraging.treegift.bonus-gift.phantoms").matcher(message);
        if (m.matches()) {
            if (list && bonusMobs.contains(GameState.strip(m.group("phantom")))) rareDrops.add("A wild §d" + GameState.strip(m.group("phantom")) + " §fappeared!");
            return ChatBus.CANCEL;
        }
        if (giftBonus && list) {
            m = Patterns.get("SH.foraging.treegift.bonus-gift.reward").matcher(message);
            if (m.matches()) {
                String id = Profit.idForName(m.group("item"));
                if (id != null && bonusItems.contains(id.replace("◆_", "AXE_"))) rareDrops.add(m.group("item"));
            }
        }
        // rest of the block is blank or centered with spaces, a line thats neither is someone talking
        String clean = GameState.strip(message);
        if (!clean.isBlank() && !clean.startsWith(" ")) {
            giftOpen = false;
            return ChatBus.PASS;
        }
        return ChatBus.CANCEL;
    }

    private static void sendGift() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        var c = Component.literal("§9" + giftType + " Tree Gift. §7You helped cut " + giftPercent + " §7and gained §e" + giftRewards + " rewards§a!");
        mc.player.sendSystemMessage(giftHover == null ? c : c.withStyle(Style.EMPTY.withHoverEvent(giftHover)));
        for (String drop : rareDrops) mc.player.sendSystemMessage(Component.literal("§f - " + drop));
    }

    // --- skyhanni ForagingTutorialQuest ---

    private static final String TUTORIAL = "foraging_tutorial";
    private static long parkWarp;
    private static final Object[][] QUESTS = {{"Foraging Tutorial", "Lumber Jack", new Vec3(-123.5, 74, -30)}, {"Into the Woods", "Charlie", new Vec3(-275.9, 80, -17.1)}, {"A Helping Hand", "Kelly", new Vec3(-350.8, 94, 31.7)}, {"The Campfire Cult", "Ryan", new Vec3(-362.7, 102, -90.5)}, {"The Rebuild", "Melody", new Vec3(-412.3, 109, 70.2)}};
    private static final Object[][] NEXT = {
            {new Vec3(-312.1, 81, -9), "Can you maybe look for her in the Spruce Woods?"}, {new Vec3(-361.2, 90, -14.8), "Cult Meeting there RIGHT NOW!"}, {new Vec3(-397.3, 98, -37.5), "You completed each Trial of Fire! Congratulations!"},
            {new Vec3(-436.4, 110.5, -14.4), " Talk to me again if you ever want to give my Harp a try!"}, {new Vec3(-435.5, 110, -13.5), "It will take me some time to assemble them, so you should come back later."},
            {new Vec3(-466.8, 120, -41.6), "  Talk to Molbert"}, {new Vec3(-465.9, 119, -53.8), "Once you find the ideal spots, go ahead and deploy them."}, {new Vec3(-448.8, 120, -64.3), "Placed trap (1/3)"},
            {new Vec3(-466.8, 120, -43.4), "Placed trap (3/3)"}, {new Vec3(-485.3, 116.5, -40.7), " I hope you forgive me after this and we can still be friends."}};
    private static final Pattern QUEST_NEEDED = Pattern.compile("You must complete the (?<quest>.*) Quest to use this!");

    private static void tutorialGo(String island, String name, Vec3 at) {
        Navigator.navigate(new Navigator.Target(island, name, at));
    }

    private static ChatBus.Result tutorialChat(ChatBus.Line line) {
        String t = line.plain();
        if ((t.equals("You don't have the requirements to use this warp!") || t.equals("You haven't unlocked this fast travel destination!")) && System.currentTimeMillis() - parkWarp < 1000) start(0);
        if ("HUB".equals(GameState.island) || "THE_PARK".equals(GameState.island)) {
            Matcher m = QUEST_NEEDED.matcher(t);
            if (m.matches()) for (int i = 0; i < QUESTS.length; i++) if (QUESTS[i][0].equals(m.group("quest"))) start(i);
        }
        for (Object[] n : NEXT) {
            if (!t.endsWith((String) n[1])) continue;
            FishHooks.say("§b[SBC] §fGo to the next phase!");
            tutorialGo(GameState.island, "Next Quest", (Vec3) n[0]);
        }
        return ChatBus.PASS;
    }

    private static void start(int q) {
        FishHooks.say("§b[SBC] §fGo to " + QUESTS[q][1] + " and start the " + QUESTS[q][0] + " quest!");
        tutorialGo(GameState.island, (String) QUESTS[q][1], (Vec3) QUESTS[q][2]);
    }

    // skyhanni MessageSendToServerEvent. nobody calls it when the feature is off
    public static void sentCommand(String command) {
        if (command.equalsIgnoreCase("warp park")) parkWarp = System.currentTimeMillis();
    }

    // --- register ---

    public static void register() {
        Features.add(new Feature(CLEAN, "Foraging", "Clean tree view", "The blocks flying from a tree you cut are not drawn.", "SkyHanni: Clean Tree View").when(ForageHelpers::customTrees)
                .option(Option.bool("foraging_clean_tree.blocks", "Hide the flying blocks", "", true))
                .option(Option.bool("foraging_clean_tree.runes", "Hide rune effects", "", false)));
        FishHooks.scan(CLEAN_SCAN);

        Features.add(new Feature(MUTE, "Foraging", "Mute foraging sounds", "Silences the sounds below.", "SkyHanni: Mute Tree Breaking, Mute Phantoms, Mute Fusion Machine").when(SKYBLOCK).defaultOff()
                .option(Option.bool("foraging_mute.tree", "Tree breaking", "", true))
                .option(Option.bool("foraging_mute.tree_galatea", "Tree breaking on Galatea too", "", false))
                .option(Option.bool("foraging_mute.phantoms", "Phantoms", "On Galatea and in the Safari.", true))
                .option(Option.bool("foraging_mute.fusion", "The Fusion Machine", "", true)));
        SoundHooks.listen(ForageHelpers::muted);

        Feature chat = Features.add(new Feature("foraging_chat", "Foraging", "Foraging chat", "Lines of foraging that only clutter your chat.", "SkyHanni: Hide Foraging Buff Messages, Unmineable Trees").when(SKYBLOCK)
                .option(Option.bool("foraging_chat.buffs", "Lottery and Beekeeper buffs away from foraging", "", true))
                .option(Option.bool("foraging_chat.unmineable", "Trees you cannot cut", "Regenerating or too tough.", false)));
        ChatBus.register(ChatBus.FILTER + 22, "foraging_chat", chat, ForageHelpers::chat);

        Feature timber = Features.add(new Feature("foraging_tree_felled", "Foraging", "Tree felled title", "A title when you fell a whole tree.", "Skyblocker: Tree Felled Notification").when(SKYBLOCK).defaultOff());
        ChatBus.register(ChatBus.OBSERVE + 7, "foraging_tree_felled", timber, ForageHelpers::timber);

        Features.add(new Feature(SWEEP_CARD, "Foraging", "Sweep details", "Your sweep, the tree's toughness, the logs and what the last hit lost, for five seconds after it.", "Skyblocker: Sweep Details Widget").when(() -> foragingIsland() || "HUB".equals(GameState.island)));
        HudLayout.add(SWEEP);

        Features.add(new Feature(OVERLAY, "Foraging", "Sweep overlay", "Holding an axe, marks the logs your hit would cut with your Sweep. Needs Sweep in your tab list.", "Skyblocker: Sweep Overlay").when(() -> foragingIsland() || "HUB".equals(GameState.island) || "PRIVATE_ISLAND".equals(GameState.island))
                .tick(ForageHelpers::overlayTick).world(ForageHelpers::overlayDraw).left(() -> sweepBlocks = List.of())
                .option(Option.bool("foraging_sweep_overlay.thrown", "Also for a thrown axe", "Half the logs, a dimmer colour.", true))
                .option(Option.color("foraging_sweep_overlay.color", "Colour", "", 0x40FF9600)));

        Features.add(new Feature(BLOCKS, "Foraging", "Foraging highlights", "Lushlilac and Sea Lumies on Galatea, full Honeyhives and Ruby Veilshrooms in the Torrhus Canyon.", "Skyblocker: block highlighters").when(ForageHelpers::customTrees)
                .tick(ForageHelpers::blocksTick).world(ForageHelpers::blocksDraw).left(() -> {
                    found.clear();
                    foundName.clear();
                })
                .option(Option.bool("foraging_highlights.lushlilac", "Lushlilac", "", true))
                .option(Option.bool("foraging_highlights.sea_lumies", "Sea Lumies", "", true))
                .option(Option.integer("foraging_highlights.sea_lumies_min", "At least this many Sea Lumies", "", 3, 1, 4))
                .option(Option.bool("foraging_highlights.honeyhive", "Honeyhives", "", true))
                .option(Option.bool("foraging_highlights.ruby_veilshroom", "Ruby Veilshrooms", "", true)));

        Features.add(new Feature(TEMPLES, "Foraging", "Temple puzzles", "The clicks each block of the Forest Temple needs; in the Desert Temple the order of the buttons and where the statues go.", "Skyblocker: Solve Forest Temple Puzzle, Solve Desert Temple Puzzles").when(ForageHelpers::customTrees).world(ForageHelpers::temples).left(() -> buttonOrder = List.of())
                .option(Option.bool("foraging_temples.forest", "Forest Temple", "", true))
                .option(Option.bool("foraging_temples.desert", "Desert Temple", "", true)));

        Features.add(new Feature(BEACON, "Foraging", "Foraging beacon", "Solves tuning the Moonglade Beacon: the clicks each setting needs, and what it should be. Says when the beacon is ready.", "SkyHanni: Beacon Solver").when(ForageHelpers::customTrees).tick(ForageHelpers::beaconTick).left(ForageHelpers::beaconReset)
                .option(Option.bool("foraging_beacon.alert", "Alert when ready", "", true))
                .option(Option.bool("foraging_beacon.no_over_click", "Stop a click on a setting that is right", "Hold Ctrl to click anyway.", true))
                .option(Option.bool("foraging_beacon.middle_click", "Left click counts as middle click", "Does not pick items up.", true)));
        MenuMarks.add(BEACON, Patterns.get("SH.foraging.beacon.inventory"), ForageHelpers::beaconRead);
        SoundHooks.listen(ForageHelpers::beaconSound);
        Interactions.onSlotClick(ForageHelpers::beaconClick);
        SideHelper.add(ForageHelpers::beaconPanel);

        ChatBus.register(ChatBus.OBSERVE + 7, "foraging_hive_marks", Features.get("foraging_hive"), ForageHelpers::hiveChat);
        Features.get("foraging_hive").world(ForageHelpers::hiveDraw).left(hivesToBonk::clear);

        Feature hotf = Features.add(new Feature(HOTF, "Foraging", "Heart of the Forest", "Perks in green when on and red when off, Tokens on the Heart, and what each perk took and will take in Forest Whispers.", "SkyHanni: HotF features").when(SKYBLOCK).tooltip(ForageHelpers::hotfTooltip)
                .option(Option.bool("hotf_perks.highlight", "Colour the perks", "", true))
                .option(Option.bool("hotf_perks.tokens", "Tokens on the Heart", "", true))
                .option(Option.bool("hotf_perks.spent", "Whispers spent", "", true))
                .option(Option.choice("hotf_perks.design", "Whispers spent as", "", "Both", "Both", "Number", "Percentage"))
                .option(Option.bool("hotf_perks.ten", "Whispers for 10 levels", "Hold Shift.", true))
                .option(Option.bool("hotf_perks.current", "Whispers you have", "", true)));
        MenuMarks.add(HOTF, Patterns.get("SH.foraging.hotf.inventory"), ForageHelpers::hotfMenu);

        Feature rot = Features.add(new Feature(ROTATING, "Foraging", "Rotating perks", "Today's Lottery and Beekeeper buffs, once Hypixel has said them.", "SkyHanni: Rotating Perk Display").when(SKYBLOCK).defaultOff());
        ChatBus.register(ChatBus.OBSERVE + 7, ROTATING, null, ForageHelpers::rotatingChat);
        HudLayout.add(ROTATING_CARD);

        Feature g = Features.add(new Feature(GIFTS, "Foraging", "Tree gift lines", "The long Tree Gift message becomes one line with what you helped cut, the rewards on hover and the bonus drops under it.", "SkyHanni: Display Tree Gifts").when(ForageHelpers::foragingIsland).left(ForageHelpers::giftReset)
                .option(Option.choice("foraging_gifts.display", "Show", "", "Compact", "Compact", "Nothing"))
                .option(Option.bool("foraging_gifts.bonus", "List the bonus drops", "Books, boosters, shards, runes and mobs.", true)));
        ChatBus.register(ChatBus.FILTER + 23, GIFTS, g, ForageHelpers::gifts);

        Feature t = Features.add(new Feature(TUTORIAL, "Foraging", "Foraging tutorial quest", "Guides you through the quests that open the Park: where to go next.", "SkyHanni: Foraging Tutorial Quest").when(SKYBLOCK).defaultOff());
        ChatBus.register(ChatBus.OBSERVE + 7, TUTORIAL, t, ForageHelpers::tutorialChat);
    }
}
