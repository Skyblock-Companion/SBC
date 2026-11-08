// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui.panels;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.regex.Pattern;

// necklace, cloak, belt, gloves as last seen, per profile, own sets for rift + safari. hypixel doesnt send
// equipment as player items so this caches what its menus showed
public final class EquipmentCache {
    private static final Pattern STATS_OR_LOADOUTS = Pattern.compile("^Stats & Equipment|(?:\\(\\d/\\d\\) )?Loadouts$");
    private static final Pattern WARDROBE = Pattern.compile("^(?:\\(\\d/\\d\\) )?Equipment Sets$");
    private static final Codec<ItemStack> STACK = Codec.lazyInitialized(() -> RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("id").forGetter(ItemStack::typeHolder),
            Codec.INT.orElse(1).fieldOf("count").forGetter(ItemStack::getCount),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ItemStack::getComponentsPatch)
    ).apply(i, ItemStack::new)));
    private static final Codec<List<ItemStack>> LIST = STACK.listOf().fieldOf("items").codec();

    private static final ItemStack[] NORMAL = empty(), RIFT = empty(), SAFARI = empty();
    private static String loaded = "";
    private static boolean dirty;
    private static int wait;

    private EquipmentCache() {}

    private static ItemStack[] empty() {
        ItemStack[] out = new ItemStack[4];
        Arrays.fill(out, ItemStack.EMPTY);
        return out;
    }

    // the set that counts where you are
    public static ItemStack[] current() {
        if ("THE_RIFT".equals(GameState.island)) return RIFT;
        if (inSafari()) return SAFARI;
        return NORMAL;
    }

    static boolean inSafari() {
        return GameState.island != null && GameState.island.contains("SAFARI");
    }

    private static String path(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    // from skyblocker getEquipmentInColumn: column slots top to bottom minus glass. a slot named Empty or "Slot
    // ..." = nothing
    static List<ItemStack> column(IntFunction<ItemStack> slotAt, int slots, int column) {
        List<ItemStack> out = new ArrayList<>();
        for (int i = column; i < slots; i += 9) {
            ItemStack st = slotAt.apply(i);
            if (path(st).equals("black_stained_glass_pane")) continue;
            String name = st.getHoverName().getString().trim().toLowerCase(Locale.ENGLISH);
            out.add(name.startsWith("empty") || name.startsWith("slot ") ? ItemStack.EMPTY : st);
        }
        return out;
    }

    // what the open menu says if it shows equipment. true = set changed
    public static boolean read(String title, IntFunction<ItemStack> slotAt, int slots) {
        ItemStack[] destination;
        List<ItemStack> equipment;
        if (STATS_OR_LOADOUTS.matcher(title).matches()) {
            destination = current();
            equipment = column(slotAt, slots, 1);
        } else if (WARDROBE.matcher(title).matches()) {
            int selected = -1;
            for (int i = 36; i < 45 && i < slots; i++) {
                if (path(slotAt.apply(i)).equals("lime_dye")) {
                    selected = i % 9;
                    break;
                }
            }
            if (selected < 0) return false;
            destination = NORMAL;
            equipment = column(slotAt, slots, selected);
        } else return false;
        if (equipment.size() < 4) return false;
        boolean changed = false;
        for (int i = 0; i < 4; i++) {
            ItemStack now = equipment.get(i);
            if (!ItemStack.matches(destination[i], now)) {
                destination[i] = now.copy();
                changed = true;
            }
        }
        return changed;
    }

    // clicking the lime dye of the worn set takes it off, nothing worn till the menu says otherwise
    static boolean clicked(String title, int slotId, ItemStack stack) {
        if (!WARDROBE.matcher(title).matches() || slotId <= 35 || slotId >= 45 || !path(stack).equals("lime_dye")) return false;
        Arrays.fill(NORMAL, ItemStack.EMPTY);
        dirty = true;
        return true;
    }

    static void tick(Minecraft mc) {
        if (mc.level == null) return;
        if (!PanelStore.key().equals(loaded)) {
            save(mc);
            loaded = PanelStore.key();
            load(mc);
        }
        if (--wait > 0) return;
        wait = 10;
        if (mc.gui.screen() instanceof ContainerScreen cs && Panels.on("equipment_slots")) {
            ChestMenu menu = cs.getMenu();
            int rows = menu.getRowCount() * 9;
            if (read(GameState.strip(cs.getTitle().getString()).trim(), i -> menu.getSlot(i).getItem(), rows)) dirty = true;
        }
        if (dirty && mc.gui.screen() == null) save(mc);
    }

    private static Path file() {
        return Config.DIR.resolve("equipment").resolve(loaded + ".nbt");
    }

    static void save(Minecraft mc) {
        if (loaded.isEmpty() || mc.level == null) return;
        dirty = false;
        List<ItemStack> all = new ArrayList<>(12);
        all.addAll(Arrays.asList(NORMAL));
        all.addAll(Arrays.asList(RIFT));
        all.addAll(Arrays.asList(SAFARI));
        writeStacks(mc, file(), all);
    }

    private static void load(Minecraft mc) {
        Arrays.fill(NORMAL, ItemStack.EMPTY);
        Arrays.fill(RIFT, ItemStack.EMPTY);
        Arrays.fill(SAFARI, ItemStack.EMPTY);
        if (loaded.isEmpty()) return;
        List<ItemStack> all = readStacks(mc, file());
        for (int i = 0; i < 12 && i < all.size(); i++) {
            ItemStack[] set = i < 4 ? NORMAL : i < 8 ? RIFT : SAFARI;
            set[i % 4] = all.get(i).isEmpty() ? ItemStack.EMPTY : all.get(i);
        }
    }

    // writes stacks, empty ones too
    static void writeStacks(Minecraft mc, Path f, List<ItemStack> all) {
        try {
            RegistryOps<Tag> ops = mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            List<ItemStack> written = new ArrayList<>(all.size());
            for (ItemStack s : all) written.add(s.isEmpty() ? new ItemStack(net.minecraft.world.item.Items.AIR.builtInRegistryHolder(), 1, DataComponentPatch.EMPTY) : s);
            Tag tag = LIST.encodeStart(ops, written).getOrThrow();
            Files.createDirectories(f.getParent());
            if (tag instanceof CompoundTag c) NbtIo.write(c, f);
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not save {}", f.getFileName(), e);
        }
    }

    // reads what writeStacks wrote, nothing if theres no file
    static List<ItemStack> readStacks(Minecraft mc, Path f) {
        try {
            if (!Files.isRegularFile(f)) return List.of();
            RegistryOps<Tag> ops = mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            return LIST.parse(ops, NbtIo.read(f)).getOrThrow();
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read {}", f.getFileName(), e);
            return List.of();
        }
    }

    // for pictures
    public static void preview(ItemStack[] set) {
        System.arraycopy(set, 0, NORMAL, 0, 4);
    }
}
