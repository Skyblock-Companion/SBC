// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mining;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.render.WorldDraw;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

// from skyblocker PickobulusHelper + PickobulusHudWidget: which blocks a pickobulus from here would break and
// what theyd give
// checks 8 blocks each way around the block you aim at, only stuff open to the air goes
public final class Pickobulus {
    static final String ID = "mining_pickobulus";
    // skyblocker ItemAbility.ABILITY_NAME_PATTERN, just for this ability
    private static final Pattern ABILITY = Pattern.compile("(?:⦾ )?Ability: Pickobulus {2}.*");

    enum Drop {
        MINESHAFT_PITY("Mineshaft Pity"), HARDSTONE("Hardstone"), ICE("Ice"), TUNGSTEN("Tungsten"), UMBER("Umber"), MITHRIL("Mithril"), TITANIUM("Titanium"), GEMSTONES("Gemstones"), MITHRIL_POWDER("Mithril Powder");

        final String label;

        Drop(String label) {
            this.label = label;
        }
    }

    private static final Set<Block> TO_BEDROCK = Set.of(Blocks.STONE, Blocks.COBBLESTONE, Blocks.POLISHED_DIORITE, Blocks.PRISMARINE, Blocks.PRISMARINE_BRICKS, Blocks.DARK_PRISMARINE,
            Blocks.DYED_TERRACOTTA.cyan(), Blocks.WOOL.lightBlue(), Blocks.WOOL.gray(), Blocks.LAPIS_BLOCK, Blocks.GOLD_BLOCK, Blocks.IRON_BLOCK, Blocks.DIAMOND_BLOCK, Blocks.EMERALD_BLOCK,
            Blocks.REDSTONE_BLOCK, Blocks.COAL_BLOCK, Blocks.QUARTZ_BLOCK, Blocks.GOLD_ORE, Blocks.IRON_ORE, Blocks.COAL_ORE, Blocks.LAPIS_ORE, Blocks.REDSTONE_ORE, Blocks.DIAMOND_ORE,
            Blocks.EMERALD_ORE, Blocks.NETHER_QUARTZ_ORE, Blocks.NETHERRACK, Blocks.GLOWSTONE, Blocks.OBSIDIAN, Blocks.END_STONE);

    private static final BlockState[][][] BLOCKS = new BlockState[8][8][8];
    private static final Set<BlockPos> BREAK = new HashSet<>();
    private static final int[] DROPS = new int[Drop.values().length];
    private static String error;
    private static boolean show;

    private Pickobulus() {}

    private static boolean glass(BlockState s) {
        Block b = s.getBlock();
        return b instanceof StainedGlassBlock || b instanceof StainedGlassPaneBlock;
    }

    private static void add(Drop d, int n) {
        DROPS[d.ordinal()] += n;
    }

    private static boolean hasAbility(ItemStack st) {
        for (String l : LegacyText.lore(st)) if (ABILITY.matcher(GameState.strip(l)).matches()) return true;
        return false;
    }

    private static void tick(Minecraft mc) {
        show = true;
        error = null;
        BREAK.clear();
        java.util.Arrays.fill(DROPS, 0);
        if (mc.player == null || mc.level == null) {
            error = "§cCan't find player or world?";
            return;
        }
        // cooldown first so it shows whatever youre holding
        for (String l : GameState.tab) {
            String t = GameState.strip(l).trim();
            if (t.startsWith("Pickobulus: ")) {
                if (!t.equals("Pickobulus: Available")) {
                    show = !MiningPack.on(ID, "mining_pickobulus.hide_cooldown");
                    error = "§cOn cooldown: " + t.substring(12);
                    return;
                }
                break;
            }
        }
        if (!hasAbility(mc.player.getMainHandItem())) {
            show = false;
            error = "§cNot holding a tool with Pickobulus";
            return;
        }
        // magic number is skyblockers, where the abilitys ray starts
        Vec3 start = mc.player.position().add(0, mc.player.getEyeHeight() + 0.53625, 0);
        BlockHitResult hit = mc.level.clip(new ClipContext(start, start.add(mc.player.getForward().scale(20)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (hit.getType() != HitResult.Type.BLOCK) {
            error = "§cNot looking at a block";
            return;
        }
        calculate(mc, hit.getBlockPos());
    }

    private static void calculate(Minecraft mc, BlockPos pos) {
        for (int i = 0; i < 8; i++) for (int j = 0; j < 8; j++) for (int k = 0; k < 8; k++) BLOCKS[i][j][k] = mc.level.getBlockState(pos.offset(i - 4, j - 4, k - 4));
        String area = GameState.area, island = GameState.island;
        for (int i = 1; i < 7; i++) {
            for (int j = 1; j < 7; j++) {
                for (int k = 1; k < 7; k++) {
                    BlockState s = BLOCKS[i][j][k];
                    if (s.isAir() || s.is(Blocks.BEDROCK)) continue;
                    boolean open = BLOCKS[i - 1][j][k].isAir() || BLOCKS[i + 1][j][k].isAir() || BLOCKS[i][j - 1][k].isAir() || BLOCKS[i][j + 1][k].isAir() || BLOCKS[i][j][k - 1].isAir() || BLOCKS[i][j][k + 1].isAir();
                    if (!open) continue;
                    if (area.equals("Glacite Tunnels") || area.equals("Great Glacite Lake")) tunnels(pos, s, i, j, k);
                    else if (area.equals("Glacite Mineshafts") || island.equals("MINESHAFT")) mineshaft(pos, s, i, j, k);
                    else switch (island) {
                        case "GOLD_MINES", "DEEP_CAVERNS", "DWARVEN_MINES" -> {
                            if (TO_BEDROCK.contains(s.getBlock()) || glass(s)) BREAK.add(pos.offset(i - 4, j - 4, k - 4));
                        }
                        case "CRYSTAL_HOLLOWS" -> hollows(pos, s, i, j, k);
                        default -> {
                        }
                    }
                }
            }
        }
    }

    private static void breakable(BlockPos pos, int i, int j, int k) {
        BLOCKS[i][j][k] = Blocks.AIR.defaultBlockState();
        BREAK.add(pos.offset(i - 4, j - 4, k - 4));
    }

    private static void hollows(BlockPos pos, BlockState s, int i, int j, int k) {
        if (glass(s)) add(Drop.GEMSTONES, 1);
        else if (s.is(Blocks.PRISMARINE) || s.is(Blocks.PRISMARINE_BRICKS) || s.is(Blocks.DARK_PRISMARINE)) add(Drop.MITHRIL_POWDER, 3);
        else if (s.is(Blocks.WOOL.lightBlue())) add(Drop.MITHRIL_POWDER, 5);
        else if (s.is(Blocks.WOOL.gray()) || s.is(Blocks.DYED_TERRACOTTA.cyan())) add(Drop.MITHRIL_POWDER, 1);
        breakable(pos, i, j, k);
    }

    private static void mineshaft(BlockPos pos, BlockState s, int i, int j, int k) {
        if (s.is(Blocks.STONE)) add(Drop.HARDSTONE, 1);
        breakable(pos, i, j, k);
    }

    private static void tunnels(BlockPos pos, BlockState s, int i, int j, int k) {
        if (s.is(Blocks.PACKED_ICE)) {
            add(Drop.MINESHAFT_PITY, 2);
            add(Drop.ICE, 1);
            BLOCKS[i][j][k] = Blocks.AIR.defaultBlockState();
        } else if (glass(s)) {
            add(Drop.MINESHAFT_PITY, 2);
            add(Drop.GEMSTONES, 1);
            BLOCKS[i][j][k] = Blocks.AIR.defaultBlockState();
        } else if (s.is(Blocks.POLISHED_DIORITE)) {
            add(Drop.MINESHAFT_PITY, 4);
            add(Drop.TITANIUM, 1);
        } else if (s.is(Blocks.INFESTED_STONE) || s.is(Blocks.CARPET.lightGray())) {
            add(Drop.HARDSTONE, 1);
        } else if (s.is(Blocks.PRISMARINE) || s.is(Blocks.PRISMARINE_BRICKS) || s.is(Blocks.DARK_PRISMARINE)) {
            add(Drop.MINESHAFT_PITY, 1);
            add(Drop.MITHRIL, 1);
            add(Drop.MITHRIL_POWDER, 3);
        } else if (s.is(Blocks.WOOL.lightBlue())) {
            add(Drop.MINESHAFT_PITY, 1);
            add(Drop.MITHRIL, 1);
            add(Drop.MITHRIL_POWDER, 5);
        } else if (s.is(Blocks.WOOL.gray()) || s.is(Blocks.DYED_TERRACOTTA.cyan())) {
            add(Drop.MINESHAFT_PITY, 1);
            add(Drop.MITHRIL, 1);
            add(Drop.MITHRIL_POWDER, 1);
        } else if (s.is(Blocks.DYED_TERRACOTTA.brown()) || s.is(Blocks.TERRACOTTA) || s.is(Blocks.SMOOTH_RED_SANDSTONE)) {
            add(Drop.MINESHAFT_PITY, 2);
            add(Drop.UMBER, 1);
        } else if (s.is(Blocks.INFESTED_COBBLESTONE) || s.is(Blocks.CLAY)) {
            add(Drop.MINESHAFT_PITY, 2);
            add(Drop.TUNGSTEN, 1);
        } else return;
        BREAK.add(pos.offset(i - 4, j - 4, k - 4));
    }

    private static void draw(WorldDraw w) {
        if (!MiningPack.on(ID, "mining_pickobulus.outline")) return;
        for (BlockPos p : BREAK) w.box(new AABB(p), 0xFF3AB3DA, false, false);
    }

    private static List<RichCard.Line> lines(String error, int total, int[] drops) {
        List<RichCard.Line> out = new ArrayList<>();
        if (error != null) {
            out.add(RichCard.Line.row(error, ""));
            return out;
        }
        out.add(RichCard.Line.row("Total Blocks", "§f" + total).brief("Blocks"));
        for (Drop d : Drop.values()) if (drops[d.ordinal()] > 0) out.add(RichCard.Line.row(d.label, "§f" + drops[d.ordinal()]));
        return out;
    }

    private static final int[] SAMPLE = {46, 3, 2, 0, 0, 12, 1, 4, 41};

    private static final RichCard CARD = new RichCard(ID, "Pickobulus", "Mining", Zone.TOP_LEFT, 40, ID) {
        @Override
        public boolean visible() {
            return MiningPack.mining() && show && MiningPack.on(ID, "mining_pickobulus.card");
        }

        @Override
        protected Content live() {
            return new Content("", lines(error, BREAK.size(), DROPS));
        }

        @Override
        protected Content sample() {
            return new Content("", lines(null, 23, SAMPLE));
        }

        private List<Row> rows(String error, int total, int[] drops) {
            List<Row> out = new ArrayList<>();
            if (error != null) return List.of(new Row(error));
            out.add(new Row("Total Blocks", String.format(Locale.ROOT, "%d", total), 0));
            for (Drop d : Drop.values()) if (drops[d.ordinal()] > 0) out.add(new Row(d.label, String.valueOf(drops[d.ordinal()]), 0));
            return out;
        }

        @Override
        protected List<Row> classicRows() {
            return rows(error, BREAK.size(), DROPS);
        }

        @Override
        protected List<Row> classicDemo() {
            return rows(null, 23, SAMPLE);
        }
    };

    private static void leave() {
        BREAK.clear();
        java.util.Arrays.fill(DROPS, 0);
        error = null;
        show = false;
    }

    public static void register() {
        Features.add(new Feature(ID, "Mining", "Pickobulus", "While you hold a tool with Pickobulus: an outline of what it would break from where you aim, and a card with how many blocks and what they give.", "Skyblocker: Pickobulus Helper")
                .when(MiningPack::mining).tick(Pickobulus::tick).world(Pickobulus::draw).left(Pickobulus::leave)
                .option(Option.bool("mining_pickobulus.outline", "Outline the blocks", "", true))
                .option(Option.bool("mining_pickobulus.card", "Card with blocks and drops", "", true))
                .option(Option.bool("mining_pickobulus.hide_cooldown", "Hide the card on cooldown", "", false)));
        HudLayout.add(CARD);
    }
}
