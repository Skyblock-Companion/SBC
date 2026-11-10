// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mining;

import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// from skyhanni MiningCommissionsBlocksColor + MixinBlockStateModelSet: ores your commissions want in one
// color, the rest gray, only on your screen
// changes how the world looks and makes the game redraw it, so it starts off
public final class CommissionBlocks {
    static final String ID = "mining_commission_blocks";
    // skyhanni CommissionBlock: tab line name -> the ore
    private static final Map<String, String> COMMISSIONS = Map.ofEntries(
            Map.entry("Mithril Everywhere", "MITHRIL"), Map.entry("Amber Gemstone Collector", "AMBER"), Map.entry("Topaz Gemstone Collector", "TOPAZ"),
            Map.entry("Amethyst Gemstone Collector", "AMETHYST"), Map.entry("Ruby Gemstone Collector", "RUBY"), Map.entry("Jade Gemstone Collector", "JADE"),
            Map.entry("Sapphire Gemstone Collector", "SAPPHIRE"), Map.entry("Glacite Collector", "GLACITE"), Map.entry("Umber Collector", "UMBER"),
            Map.entry("Tungsten Collector", "TUNGSTEN"), Map.entry("Peridot Gemstone Collector", "PERIDOT"), Map.entry("Aquamarine Gemstone Collector", "AQUAMARINE"),
            Map.entry("Citrine Gemstone Collector", "CITRINE"), Map.entry("Onyx Gemstone Collector", "ONYX"));
    private static final Set<String> ORES = new HashSet<>(COMMISSIONS.values());
    private static final Map<BlockState, BlockState> CACHE = new ConcurrentHashMap<>();
    private static volatile boolean enabled, active = true;
    private static volatile Set<String> wanted = Set.of();
    private static volatile DyeColor color = DyeColor.LIME;
    private static boolean oldSneak;
    private static long nextRead;

    private CommissionBlocks() {}

    private static boolean here() {
        return MiningApi.inCrystalHollows() || MiningApi.inGlaciteArea();
    }

    private static DyeColor chosen() {
        return switch (MiningPack.choice(ID, "mining_commission_blocks.color", "Green")) {
            case "Red" -> DyeColor.RED;
            case "Yellow" -> DyeColor.YELLOW;
            case "Aqua" -> DyeColor.LIGHT_BLUE;
            case "Purple" -> DyeColor.PURPLE;
            case "White" -> DyeColor.WHITE;
            default -> DyeColor.LIME;
        };
    }

    private static void reload(Minecraft mc) {
        CACHE.clear();
        if (mc.levelExtractor != null) mc.levelExtractor.allChanged();
    }

    private static void tick(Minecraft mc) {
        boolean dirty = !enabled;
        enabled = true;
        if (MiningPack.on(ID, "mining_commission_blocks.sneak") && mc.player != null) {
            boolean sneak = mc.player.isShiftKeyDown();
            if (sneak != oldSneak) {
                oldSneak = sneak;
                if (sneak) {
                    active = !active;
                    dirty = true;
                }
            }
        } else if (!active) {
            active = true;
            dirty = true;
        }
        long now = System.currentTimeMillis();
        if (now >= nextRead) {
            nextRead = now + 1000;
            Set<String> next = new HashSet<>();
            for (Commissions.Commission c : Commissions.current()) {
                String ore = COMMISSIONS.get(c.name());
                if (ore != null && !c.done()) next.add(ore);
            }
            if (!next.equals(wanted)) {
                wanted = next;
                dirty = true;
            }
            DyeColor c = chosen();
            if (c != color) {
                color = c;
                dirty = true;
            }
        }
        if (dirty) reload(mc);
    }

    private static void leave() {
        if (!enabled) return;
        enabled = false;
        wanted = Set.of();
        reload(Minecraft.getInstance());
    }

    // runs for every block the game draws, on its drawing threads. the mixin only asks while this is on
    public static BlockState processState(BlockState state) {
        if (!enabled || !active || state == null) return state;
        BlockState done = CACHE.get(state);
        if (done != null) return done;
        BlockState out = state;
        OreBlock ore = OreMined.of(state);
        if (ore != null && ORES.contains(ore.oreType())) {
            DyeColor c = wanted.contains(ore.oreType()) ? color : DyeColor.GRAY;
            out = ore.category == OreBlock.Category.GEMSTONE ? glass(state, c) : Blocks.WOOL.pick(c).defaultBlockState();
        }
        CACHE.put(state, out);
        return out;
    }

    // skyhanni createGlassBlockState: a pane stays a pane and keeps how it connects
    private static BlockState glass(BlockState state, DyeColor c) {
        if (!(state.getBlock() instanceof StainedGlassPaneBlock)) return Blocks.STAINED_GLASS.pick(c).defaultBlockState();
        Block pane = Blocks.STAINED_GLASS_PANE.pick(c);
        return pane.withPropertiesOf(state);
    }

    public static void register() {
        // tab list says a commission is done a bit late, Commissions already keeps what chat said
        Feature f = new Feature(ID, "Mining", "Ores by commission", "Ores your commissions ask for get one colour, the others gray, only on your screen, in the Crystal Hollows and Glacite Tunnels. It changes how the world looks, so it starts off.", "SkyHanni: Commissions Blocks Color")
                .when(CommissionBlocks::here).defaultOff().tick(CommissionBlocks::tick).left(CommissionBlocks::leave)
                .option(Option.choice("mining_commission_blocks.color", "Colour", "", "Green", "Green", "Red", "Yellow", "Aqua", "Purple", "White"))
                .option(Option.bool("mining_commission_blocks.sneak", "Sneak to switch it", "", false));
        Features.add(f);
    }
}
