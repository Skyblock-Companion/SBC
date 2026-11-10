// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.world;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.WebBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

// from skyblocker teleport/TeleportOverlay + the PredictiveSmoothAOTE raycast: where a teleport item would
// put you, on the block youd land on. costs nothing unless you hold one
public final class TeleportOverlay {
    public static final String ID = "teleport_overlay";

    private static ItemStack lastStack;
    private static String lastId = "";
    private static CompoundTag lastData;

    private TeleportOverlay() {}

    private static boolean opt(String key) {
        Feature f = Features.get(ID);
        Option o = f == null ? null : f.option(key);
        return o != null && o.bool();
    }

    // held items id + extra data, only reread when the stack changes
    private static String held(ItemStack stack) {
        if (stack != lastStack) {
            lastStack = stack;
            var cd = stack.get(DataComponents.CUSTOM_DATA);
            lastData = cd == null ? null : cd.copyTag();
            lastId = lastData == null ? "" : lastData.getStringOr("id", "");
        }
        return lastId;
    }

    private static int tuned(int base) {
        return lastData == null ? base : base + lastData.getIntOr("tuned_transmission", 0);
    }

    private static void draw(WorldDraw w) {
        Minecraft mc = w.mc;
        if (mc.player == null || mc.level == null) return;
        String id = held(mc.player.getMainHandItem());
        if (id.isEmpty()) return;
        switch (id) {
            case "ASPECT_OF_THE_LEECH_1" -> {
                if (opt("weird")) at(w, 3, false);
            }
            case "ASPECT_OF_THE_LEECH_2" -> {
                if (opt("weird")) at(w, 4, false);
            }
            case "ASPECT_OF_THE_LEECH_3" -> {
                if (opt("weird")) at(w, 5, false);
            }
            case "ASPECT_OF_THE_END", "ASPECT_OF_THE_VOID" -> {
                if (opt("ether") && mc.options.keyShift.isDown() && lastData != null && lastData.getIntOr("ethermerge", 0) == 1) at(w, tuned(57), true);
                else if (opt("instant")) at(w, tuned(8), false);
            }
            case "ETHERWARP_CONDUIT" -> {
                if (opt("ether")) at(w, tuned(57), true);
            }
            case "SINSEEKER_SCYTHE" -> {
                if (opt("sinrecall")) at(w, tuned(4), false);
            }
            case "NECRON_BLADE", "ASTRAEA", "HYPERION", "SCYLLA", "VALKYRIE" -> {
                if (opt("wither")) at(w, 10, false);
            }
            default -> {
            }
        }
    }

    private static float eyeHeight(Player player) {
        return player.isShiftKeyDown() ? 1.27f : 1.62f;
    }

    private static void at(WorldDraw w, int range, boolean etherwarp) {
        Minecraft mc = w.mc;
        Vec3 look = mc.player.calculateViewVector(mc.player.getXRot(), mc.player.getYRot());
        Vec3 start = mc.player.position().add(0, eyeHeight(mc.player), 0);
        Vec3 ray = raycast(mc, range, look, start, etherwarp);
        if (ray == null) return;
        BlockPos target = BlockPos.containing(start.add(ray));
        if (etherwarp) {
            if (!mc.level.getBlockState(target.above()).isAir() || !mc.level.getBlockState(target.above(2)).isAir()) return;
        } else {
            target = target.below();
        }
        if (!opt("in_air") && mc.level.getBlockState(target).isAir()) return;
        Feature f = Features.get(ID);
        int color = f.option("color").color();
        String shown = f.option("look").choice();
        AABB box = new AABB(target);
        if (!shown.equals("Outline")) w.filledBox(box, color & 0xFFFFFF, (color >>> 24) / 255f, false);
        if (!shown.equals("Highlight")) w.box(box, color, false, false);
    }

    // from skyblocker PredictiveSmoothAOTE.raycast: one step per block like hypixel checks a teleport. vector to
    // the end, null = nothing happens
    static Vec3 raycast(Minecraft mc, int distance, Vec3 direction, Vec3 startPos, boolean etherwarp) {
        if (mc.level == null || direction == null || startPos == null) return null;
        BlockPos xDiagonal = direction.x() > 0 ? new BlockPos(1, 0, 0) : new BlockPos(-1, 0, 0);
        BlockPos zDiagonal = direction.z() > 0 ? new BlockPos(0, 0, 1) : new BlockPos(0, 0, -1);
        int closeFloorY = Integer.MAX_VALUE;
        for (double offset = 0; offset <= distance; offset++) {
            Vec3 pos = startPos.add(direction.scale(offset));
            BlockPos check = BlockPos.containing(pos);
            if (!through(mc, check)) {
                if (!etherwarp && offset == 0) return null;
                if (etherwarp) return direction.scale(offset - 1).add(direction);
                return direction.scale(offset - 1);
            }
            if (!through(mc, check.above()) && !etherwarp) {
                if (offset == 0) {
                    Vec3 justAhead = startPos.add(direction.scale(0.2));
                    if (justAhead.y() - Math.floor(justAhead.y()) <= 0.495) continue;
                    return null;
                }
                return direction.scale(offset - 1);
            }
            if (offset != 0 && direction.x() < 0 && floor(mc, check.east()) && floor(mc, BlockPos.containing(pos.subtract(direction)).offset(zDiagonal))) return direction.scale(offset - 1);
            if (offset != 0 && direction.z() < 0 && direction.x() < 0 && floor(mc, check.south()) && floor(mc, BlockPos.containing(pos.subtract(direction)).offset(xDiagonal))) return direction.scale(offset - 1);
            if ((floor(mc, check.below()) || floor(mc, check.below().offset(xDiagonal)) && floor(mc, check.below().offset(zDiagonal))) && pos.y() - Math.floor(pos.y()) < 0.31) closeFloorY = check.getY() - 1;
            if (closeFloorY == check.getY()) return direction.scale(offset - 1);
        }
        if (!floor(mc, BlockPos.containing(startPos.add(direction.scale(distance)).subtract(0, 1, 0)))) return direction.scale(distance).subtract(0, 1, 0);
        return direction.scale(distance);
    }

    private static boolean through(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        if (state.isAir()) return true;
        Block block = state.getBlock();
        VoxelShape shape = state.getCollisionShape(mc.level, pos);
        return shape.isEmpty() || block instanceof CarpetBlock || block instanceof FlowerPotBlock || block instanceof WebBlock || block == Blocks.SNOW && state.getValue(BlockStateProperties.LAYERS) <= 3;
    }

    private static boolean floor(Minecraft mc, BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        VoxelShape shape = state.getCollisionShape(mc.level, pos);
        if (shape.isEmpty()) return false;
        return shape.bounds().maxY >= 1 || state.getBlock() == Blocks.MUD;
    }

    public static void register() {
        Features.add(new Feature(ID, "World", "Teleport overlay", "The block a teleport item would put you on, while you hold it.", "Skyblocker: teleport overlay")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).world(TeleportOverlay::draw)
                .option(Option.bool("teleport_overlay.ether", "Etherwarp", "Etherwarp Conduit, and sneaking with an Ether Merged Aspect of the End or Void.", true))
                .option(Option.bool("teleport_overlay.instant", "Aspect of the End and Void", "", false))
                .option(Option.bool("teleport_overlay.sinrecall", "Sinseeker Scythe", "", false))
                .option(Option.bool("teleport_overlay.weird", "Aspect of the Leech", "", false))
                .option(Option.bool("teleport_overlay.wither", "Wither Impact", "Necron's Blade, Hyperion, Astraea, Scylla and Valkyrie.", false))
                .option(Option.bool("teleport_overlay.in_air", "Show it in mid air", "Also when you would land on nothing.", false))
                .option(Option.choice("teleport_overlay.look", "Look", "", "Highlight", "Highlight", "Highlight and outline", "Outline"))
                .option(Option.color("teleport_overlay.color", "Colour", "", 0x7F761594)));
    }
}
