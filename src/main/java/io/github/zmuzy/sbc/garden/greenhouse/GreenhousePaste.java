// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.greenhouse;

import com.mojang.brigadier.arguments.StringArgumentType;
import io.github.zmuzy.sbc.core.Safe;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.garden.GardenPack;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// from skyblocker GreenhousePaste: copy a layout link from skymutations or skyshards and the layout shows in
// your greenhouse plot as a guide
// skyblocker draws see-through blocks and heads, here its a box with a name like every other waypoint we
// draw. nothing gets placed for you
// off by default, only for people planning layouts, and it only reads the clipboard when you ask
public final class GreenhousePaste {
    private GreenhousePaste() {}

    public static final String ID = "garden_greenhouse_paste";
    private static final Set<String> IGNORE_NAMES = Set.of("PlantboyRoots", "godseedPillar");
    private static final int[][] current = new int[10][10];
    private static int[][] target = new int[10][10];
    private static BlockPos corner;
    private static long nextLocate;

    static {
        for (int[] col : target) Arrays.fill(col, -1);
    }

    private static void say(String text) {
        Minecraft.getInstance().player.sendSystemMessage(Component.literal("§b[SBC] §r" + text));
    }

    private static void remove() {
        corner = null;
        for (int x = 0; x < 10; x++) {
            Arrays.fill(current[x], 0);
            Arrays.fill(target[x], -1);
        }
    }

    // lower corner of the plot you stand on, plots are 96x96 on a 5x5 grid centred on 0,0
    private static int plotStart(int v) {
        return (int) ((v + 240) / 96.0) * 96 - 240;
    }

    private static boolean inGreenhouse() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        BlockPos p = mc.player.blockPosition();
        int x = plotStart(p.getX()), z = plotStart(p.getZ());
        for (Entity e : mc.level.getEntities((Entity) null, new AABB(x, 60, z, x + 96, 90, z + 96))) {
            Component custom = e.getCustomName();
            if (custom != null && custom.getString().contains("Carpenter")) return true;
        }
        say("§cYou are not in a greenhouse.");
        return false;
    }

    private static void load(String link) {
        if (!Features.on(ID)) {
            say("§cTurn on Paste Greenhouse in the settings first.");
            return;
        }
        if (!GardenPack.garden() || !inGreenhouse()) return;
        if (link.isBlank()) return;
        int[][] layout = LayoutCodes.decodeLink(link);
        if (layout == null) {
            say("§cFailed to load greenhouse layout from clipboard. Extracted content: §7" + LayoutCodes.extractCode(link));
            return;
        }
        target = layout;
        say("§aGreenhouse loaded successfully!");
        locate();
    }

    // locateGreenhouse: what stands on each cell rn
    private static void locate() {
        var mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        BlockPos p = mc.player.blockPosition();
        corner = new BlockPos(plotStart(p.getX()) + 43, 73, plotStart(p.getZ()) + 43);
        for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) current[x][z] = cropAt(mc.level, corner.offset(x, 0, z));
        for (int x = 0; x < 9; x++) {
            for (int z = 0; z < 9; z++) {
                plantBoy(mc.level, x, z);
                spread(x, z, 23, new int[][]{{-1, 0}, {1, 0}, {-1, -1}, {0, -1}, {1, -1}, {-1, -2}, {0, -2}, {1, -2}}); // Snoozling
                spread(x, z, 37, new int[][]{{-1, 0}, {1, 0}, {-1, -1}, {0, -1}, {1, -1}, {-1, 1}, {0, 1}, {1, 1}}); // Godseed
            }
        }
    }

    private static int cropAt(Level level, BlockPos pos) {
        for (Entity e : level.getEntities((Entity) null, new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 6, pos.getZ() + 1))) {
            if (!(e instanceof ArmorStand stand)) continue;
            ItemStack head = stand.getItemBySlot(EquipmentSlot.HEAD);
            if (head.isEmpty()) continue;
            String name = head.getHoverName().getString();
            if (IGNORE_NAMES.contains(name)) return 0;
            for (GreenhouseCrops.Crop c : GreenhouseCrops.ALL) if (name.contains(c.standName())) return c.id();
            String tex = texture(head);
            GreenhouseCrops.Crop by = tex == null ? null : GreenhouseCrops.BY_TEXTURE.get(tex);
            if (by != null) return by.id();
        }
        String block = BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.above()).getBlock()).getPath();
        for (GreenhouseCrops.Crop c : GreenhouseCrops.ALL) if (block.equals(c.block())) return c.id();
        return 0;
    }

    private static String texture(ItemStack head) {
        var profile = head.get(DataComponents.PROFILE);
        if (profile == null) return null;
        try {
            for (var pr : profile.partialProfile().properties().get("textures")) return pr.value();
        } catch (Exception ignored) {
        }
        return null;
    }

    // adjustForPlantBoy: it stands in the middle of its 2x2
    private static void plantBoy(Level level, int x, int z) {
        if (current[x][z] != 26) return;
        BlockPos pos = corner.offset(x, 0, z);
        for (Entity e : level.getEntities((Entity) null, new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 6, pos.getZ() + 1))) {
            if (!(e instanceof ArmorStand stand)) continue;
            String name = stand.getItemBySlot(EquipmentSlot.HEAD).getHoverName().getString();
            if (!name.contains("Plantboy") || name.contains("Advance")) continue;
            int bx = (int) Math.floor(stand.getX()) - corner.getX(), bz = (int) Math.floor(stand.getZ()) - corner.getZ();
            for (int dx = 0; dx < 2; dx++) for (int dz = 0; dz < 2; dz++) if (bx + dx >= 0 && bx + dx < 10 && bz + dz >= 0 && bz + dz < 10) current[bx + dx][bz + dz] = 26;
            return;
        }
    }

    // adjustForSnoozling, adjustForGodseed: stand on one cell and fill the ones around
    private static void spread(int x, int z, int crop, int[][] offsets) {
        if (current[x][z] != crop) return;
        for (int[] o : offsets) if (x + o[0] >= 0 && x + o[0] < 10 && z + o[1] >= 0 && z + o[1] < 10) current[x + o[0]][z + o[1]] = crop;
    }

    private static void tick(Minecraft mc) {
        if (corner == null || mc.player == null) return;
        long now = System.currentTimeMillis();
        // skyblocker listens to every block and entity change in the grid, once a sec is enough for a guide
        if (now < nextLocate) return;
        nextLocate = now + 1000;
        locate();
    }

    // renderPreview
    private static void draw(WorldDraw w) {
        if (corner == null || w.mc.player == null) return;
        if (w.mc.player.getX() < corner.getX() - 43 || w.mc.player.getX() > corner.getX() + 53 || w.mc.player.getZ() < corner.getZ() - 43 || w.mc.player.getZ() > corner.getZ() + 53) return;
        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                int want = target[x][z], have = current[x][z];
                if (want == -1 || want == have) continue;
                if ((have == 48 || have == 49) && (want == 48 || want == 49)) continue; // mushrooms are interchangeable
                int px = corner.getX() + x, py = corner.getY() + 1, pz = corner.getZ() + z;
                if (have != 0) {
                    w.box(new AABB(px, py, pz, px + 1, py + 1, pz + 1), 0xFFFF5555, false, true);
                    continue;
                }
                if (want == 0) {
                    if (GardenPack.on(ID, "mutation_slot")) w.box(new AABB(px, py - 0.05, pz, px + 1, py + 0.1, pz + 1), 0xFF55FF55, false, false);
                    continue;
                }
                GreenhouseCrops.Crop crop = GreenhouseCrops.BY_ID.get(want);
                if (crop == null) continue;
                w.box(new AABB(px + 0.2, py, pz + 0.2, px + 0.8, py + 0.6, pz + 0.8), 0xFF55FFFF, false, false);
                w.dynamicText(new Vec3(px + 0.5, py + 0.8, pz + 0.5), "§b" + crop.name(), 0.6, 0);
            }
        }
    }

    private static String presetText() {
        return GardenPack.choice(ID, "presets", "");
    }

    private static void savePreset(String name) {
        Feature f = Features.get(ID);
        Option o = f == null ? null : f.option("presets");
        if (o == null) return;
        Map<String, String> all = LayoutCodes.presets(presetText());
        all.put(name.replace("|", "").replace("=", ""), Minecraft.getInstance().keyboardHandler.getClipboard().strip());
        StringBuilder out = new StringBuilder();
        for (var e : all.entrySet()) out.append(out.isEmpty() ? "" : "|").append(e.getKey()).append('=').append(e.getValue());
        o.set(out.toString());
        say("§aSaved the layout in your clipboard as §f" + name);
    }

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Paste Greenhouse", "Copy a layout link, then /sbc greenhouse paste: the layout shows in your Greenhouse plot as a guide. /sbc greenhouse end removes it; rotate left, rotate right and mirror turn it, save <name> keeps the copied link, paste <name> brings it back.", "Skyblocker: Paste Greenhouse Plot")
                .when(GardenPack::garden).defaultOff().tick(GreenhousePaste::tick).world(GreenhousePaste::draw)
                .option(Option.bool("garden_greenhouse_paste.mutation_slot", "Show mutation spots", "A green outline on the cells a mutation should grow on.", true))
                .option(Option.text("garden_greenhouse_paste.presets", "Saved layouts", "name=link, with | between them. Add one with /sbc greenhouse save <name>.", "")));
        Safe.commands("GreenhousePaste.commands", (d, r) -> d.register(literal("sbc").then(literal("greenhouse")
                .then(literal("paste").executes(c -> {
                    load(Minecraft.getInstance().keyboardHandler.getClipboard());
                    return 1;
                }).then(argument("preset", StringArgumentType.greedyString())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(LayoutCodes.presets(presetText()).keySet(), b))
                        .executes(c -> {
                            load(LayoutCodes.presets(presetText()).getOrDefault(StringArgumentType.getString(c, "preset"), ""));
                            return 1;
                        })))
                .then(literal("end").executes(c -> {
                    remove();
                    say("§7Greenhouse preview removed.");
                    return 1;
                }))
                .then(literal("save").then(argument("name", StringArgumentType.greedyString()).executes(c -> {
                    savePreset(StringArgumentType.getString(c, "name"));
                    return 1;
                })))
                .then(literal("rotate").then(literal("left").executes(c -> {
                    target = LayoutCodes.rotate(target, true);
                    return 1;
                })).then(literal("right").executes(c -> {
                    target = LayoutCodes.rotate(target, false);
                    return 1;
                })))
                .then(literal("mirror").executes(c -> {
                    target = LayoutCodes.mirror(target);
                    return 1;
                })))));
        io.github.zmuzy.sbc.core.Commands.describe("greenhouse", "Show a copied Greenhouse layout as a guide: paste, end, save, rotate, mirror");
    }
}
