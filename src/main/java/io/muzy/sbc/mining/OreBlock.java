// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mining;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

// from skyhanni OreBlock + OreType: what a block is worth to the mining helpers and where it counts
public enum OreBlock {
    LOW_TIER_MITHRIL(s -> s.is(Blocks.WOOL.gray()) || s.is(Blocks.DYED_TERRACOTTA.cyan()), () -> Area.dwarven || Area.glacite, Category.DWARVEN_METAL, true, "MITHRIL"),
    MID_TIER_MITHRIL(s -> s.is(Blocks.PRISMARINE) || s.is(Blocks.PRISMARINE_BRICKS) || s.is(Blocks.DARK_PRISMARINE), () -> Area.dwarven || Area.hollows || Area.glacite, Category.DWARVEN_METAL, true, "MITHRIL"),
    HIGH_TIER_MITHRIL(s -> s.is(Blocks.WOOL.lightBlue()), () -> Area.dwarven || Area.hollows || Area.glacite, Category.DWARVEN_METAL, true, "MITHRIL"),
    TITANIUM(s -> s.is(Blocks.POLISHED_DIORITE), () -> Area.dwarven || Area.glacite, Category.DWARVEN_METAL, true, "TITANIUM"),
    STONE(s -> s.is(Blocks.STONE), () -> Area.dwarven, Category.BLOCK, true, "COBBLESTONE"),
    COBBLESTONE(Blocks.COBBLESTONE, () -> Area.dwarven, Category.BLOCK, true, "COBBLESTONE"),
    COAL_ORE(Blocks.COAL_ORE, () -> Area.dwarven || Area.hollows, Category.ORE, true, "COAL"),
    IRON_ORE(Blocks.IRON_ORE, () -> Area.dwarven || Area.hollows, Category.ORE, true, "IRON"),
    GOLD_ORE(Blocks.GOLD_ORE, () -> Area.dwarven || Area.hollows, Category.ORE, true, "GOLD"),
    LAPIS_ORE(Blocks.LAPIS_ORE, () -> Area.dwarven || Area.hollows, Category.ORE, true, "LAPIS"),
    REDSTONE_ORE(s -> s.is(Blocks.REDSTONE_ORE), () -> Area.dwarven || Area.hollows, Category.ORE, true, "REDSTONE"),
    EMERALD_ORE(Blocks.EMERALD_ORE, () -> Area.dwarven || Area.hollows, Category.ORE, true, "EMERALD"),
    DIAMOND_ORE(Blocks.DIAMOND_ORE, () -> Area.dwarven || Area.hollows, Category.ORE, true, "DIAMOND"),
    NETHERRACK(Blocks.NETHERRACK, () -> Area.crimson, Category.BLOCK, true, "NETHERRACK"),
    QUARTZ_ORE(Blocks.NETHER_QUARTZ_ORE, () -> Area.hollows || Area.crimson, Category.ORE, true, "QUARTZ"),
    GLOWSTONE(Blocks.GLOWSTONE, () -> Area.crimson, Category.BLOCK, true, "GLOWSTONE"),
    MYCELIUM(Blocks.MYCELIUM, () -> Area.crimson, Category.BLOCK, true, "MYCELIUM"),
    RED_SAND(s -> s.is(Blocks.RED_SAND), () -> Area.crimson, Category.BLOCK, true, "RED_SAND"),
    SULPHUR(Blocks.SPONGE, () -> Area.crimson, Category.ORE, true, "SULPHUR"),
    GRAVEL(Blocks.GRAVEL, () -> Area.spiders, Category.BLOCK, true, "GRAVEL"),
    END_STONE(Blocks.END_STONE, () -> Area.end, Category.BLOCK, true, "END_STONE"),
    OBSIDIAN(Blocks.OBSIDIAN, () -> Area.hollows || Area.mineshaft || Area.end, Category.ORE, true, "OBSIDIAN"),
    HARD_STONE_HOLLOWS(s -> s.is(Blocks.WOOL.gray()) || s.is(Blocks.WOOL.green()) || s.is(Blocks.DYED_TERRACOTTA.cyan()) || s.is(Blocks.DYED_TERRACOTTA.brown()) || s.is(Blocks.DYED_TERRACOTTA.gray()) || s.is(Blocks.DYED_TERRACOTTA.black()) || s.is(Blocks.DYED_TERRACOTTA.lime()) || s.is(Blocks.DYED_TERRACOTTA.green()) || s.is(Blocks.DYED_TERRACOTTA.blue()) || s.is(Blocks.DYED_TERRACOTTA.red()) || s.is(Blocks.DYED_TERRACOTTA.lightGray()) || s.is(Blocks.CLAY) || s.is(Blocks.STONE_BRICKS) || s.is(Blocks.MOSSY_STONE_BRICKS) || s.is(Blocks.CRACKED_STONE_BRICKS) || s.is(Blocks.CHISELED_STONE_BRICKS) || s.is(Blocks.STONE) || s.is(Blocks.DIORITE) || s.is(Blocks.GRANITE) || s.is(Blocks.ANDESITE), () -> Area.hollows, Category.BLOCK, true, "HARD_STONE"),
    HARD_STONE_TUNNELS(s -> s.is(Blocks.INFESTED_STONE) || s.is(Blocks.WOOL.lightGray()), () -> Area.tunnels, Category.BLOCK, true, "HARD_STONE"),
    HARD_STONE_MINESHAFT(s -> s.is(Blocks.STONE) || s.is(Blocks.WOOL.lightGray()), () -> Area.mineshaft, Category.BLOCK, true, "HARD_STONE"),
    PURE_COAL(Blocks.COAL_BLOCK, () -> Area.dwarven || Area.hollows, Category.ORE, true, "COAL"),
    PURE_IRON(Blocks.IRON_BLOCK, () -> Area.dwarven || Area.hollows, Category.ORE, false, "IRON"),
    PURE_GOLD(Blocks.GOLD_BLOCK, () -> Area.dwarven || Area.hollows || Area.mineshaft, Category.ORE, false, "GOLD"),
    PURE_LAPIS(Blocks.LAPIS_BLOCK, () -> Area.dwarven || Area.hollows, Category.ORE, true, "LAPIS"),
    PURE_REDSTONE(Blocks.REDSTONE_BLOCK, () -> Area.dwarven || Area.hollows, Category.ORE, false, "REDSTONE"),
    PURE_EMERALD(Blocks.EMERALD_BLOCK, () -> Area.dwarven || Area.hollows, Category.ORE, false, "EMERALD"),
    PURE_DIAMOND(Blocks.DIAMOND_BLOCK, () -> Area.dwarven || Area.hollows, Category.ORE, false, "DIAMOND"),
    PURE_QUARTZ(Blocks.QUARTZ_BLOCK, () -> Area.dwarven || Area.hollows, Category.ORE, true, "QUARTZ"),
    RUBY(DyeColor.RED, () -> Area.hollows || Area.glacite),
    AMBER(DyeColor.ORANGE, () -> Area.hollows || Area.glacite),
    AMETHYST(DyeColor.PURPLE, () -> Area.hollows || Area.glacite),
    JADE(DyeColor.LIME, () -> Area.hollows || Area.glacite),
    SAPPHIRE(DyeColor.LIGHT_BLUE, () -> Area.hollows || Area.glacite),
    TOPAZ(DyeColor.YELLOW, () -> Area.hollows || Area.glacite),
    JASPER(DyeColor.MAGENTA, () -> Area.hollows || Area.mineshaft),
    OPAL(DyeColor.WHITE, () -> Area.mineshaft || Area.crimson),
    AQUAMARINE(DyeColor.BLUE, () -> Area.glacite),
    CITRINE(DyeColor.BROWN, () -> Area.glacite),
    ONYX(DyeColor.BLACK, () -> Area.glacite),
    PERIDOT(DyeColor.GREEN, () -> Area.glacite),
    LOW_TIER_UMBER(s -> s.is(Blocks.TERRACOTTA), () -> Area.glacite, Category.DWARVEN_METAL, true, "UMBER"),
    MID_TIER_UMBER(s -> s.is(Blocks.DYED_TERRACOTTA.brown()), () -> Area.glacite, Category.DWARVEN_METAL, true, "UMBER"),
    HIGH_TIER_UMBER(s -> s.is(Blocks.SMOOTH_RED_SANDSTONE), () -> Area.glacite, Category.DWARVEN_METAL, true, "UMBER"),
    LOW_TIER_TUNGSTEN_TUNNELS(s -> s.is(Blocks.INFESTED_COBBLESTONE), () -> Area.tunnels, Category.DWARVEN_METAL, true, "TUNGSTEN"),
    LOW_TIER_TUNGSTEN_MINESHAFT(s -> s.is(Blocks.COBBLESTONE_SLAB) || s.is(Blocks.COBBLESTONE) || s.is(Blocks.COBBLESTONE_STAIRS), () -> Area.mineshaft, Category.DWARVEN_METAL, true, "TUNGSTEN"),
    HIGH_TIER_TUNGSTEN(Blocks.CLAY, () -> Area.glacite, Category.DWARVEN_METAL, true, "TUNGSTEN"),
    GLACITE(Blocks.PACKED_ICE, () -> Area.glacite, Category.DWARVEN_METAL, true, "GLACITE");

    public enum Category { BLOCK, ORE, DWARVEN_METAL, GEMSTONE }

    // skyhanni MiningApi.updateLocation: where the player mines
    static final class Area {
        static boolean glacite, tunnels, mineshaft, dwarven, hollows, crimson, end, spiders;

        private Area() {}
    }

    final Predicate<BlockState> check;
    final BooleanSupplier area;
    public final Category category;
    final boolean hasInitSound;
    // OreType name for this
    public final String type;

    OreBlock(Predicate<BlockState> check, BooleanSupplier area, Category category, boolean hasInitSound, String type) {
        this.check = check;
        this.area = area;
        this.category = category;
        this.hasInitSound = hasInitSound;
        this.type = type;
    }

    OreBlock(Block block, BooleanSupplier area, Category category, boolean hasInitSound, String type) {
        this(s -> s.is(block), area, category, hasInitSound, type);
    }

    OreBlock(DyeColor color, BooleanSupplier area) {
        this(s -> gemstone(s, color), area, Category.GEMSTONE, true, null);
    }

    private static boolean gemstone(BlockState s, DyeColor color) {
        Block b = s.getBlock();
        return b instanceof StainedGlassBlock g && g.getColor() == color || b instanceof StainedGlassPaneBlock p && p.getColor() == color;
    }

    // a gemstones ore type is just its own name
    public String oreType() {
        return type != null ? type : name();
    }

    public static Set<OreBlock> inCurrentArea() {
        Set<OreBlock> out = EnumSet.noneOf(OreBlock.class);
        for (OreBlock o : values()) if (o.area.getAsBoolean()) out.add(o);
        return out;
    }
}
