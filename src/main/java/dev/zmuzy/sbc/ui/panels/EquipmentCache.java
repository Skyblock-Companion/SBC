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
    static final Codec<ItemStack> STACK = Codec.lazyInitialized(() -> RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("id").forGetter(ItemStack::typeHolder),
            Codec.INT.orElse(1).fieldOf("count").forGetter(ItemStack::getCount),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ItemStack::getComponentsPatch)
    ).apply(i, ItemStack::new)));
    private static final Codec<List<ItemStack>> LIST = STACK.listOf().fieldOf("items").codec();

    // skyhanni EquipmentApi: the chat line when a piece goes on, from the menu or a right click
    private static final Pattern EQUIP = dev.zmuzy.sbc.core.Patterns.get("SH.data.equipment.chat.equip");
    private static final Pattern EQUIP_PLAIN = Pattern.compile("^You equipped an? (.+)!$");
    // rarity line at the end of the lore: "LEGENDARY NECKLACE", "EPIC DUNGEON GLOVES"
    private static final Pattern KIND = Pattern.compile("\\b(?:COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|ULTIMATE|ADMIN)\\b[A-Z ]*?\\b(NECKLACE|CLOAK|BELT|GLOVES|BRACELET)\\b");

    private static final ItemStack[] NORMAL = empty(), RIFT = empty(), SAFARI = empty();
    private static String loaded = "";
    private static boolean dirty;
    private static int wait, hold;
    // the held item a tick ago: right clicking a piece takes it from the hand before or after the chat line
    private static ItemStack lastHeld = ItemStack.EMPTY;

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
        if (hold > 0) hold--;
        if (mc.player != null) {
            ItemStack held = mc.player.getMainHandItem();
            if (!held.isEmpty() && !ItemStack.matches(held, lastHeld)) lastHeld = held.copy();
        }
        if (--wait > 0) return;
        wait = 10;
        // just switched loadout: the menu still shows the old pieces till hypixel sends the new ones
        if (hold <= 0 && mc.gui.screen() instanceof ContainerScreen cs && Panels.on("equipment_slots")) {
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
            for (ItemStack s : all) written.add(forFile(s));
            Tag tag = LIST.encodeStart(ops, written).getOrThrow();
            Files.createDirectories(f.getParent());
            if (tag instanceof CompoundTag c) NbtIo.write(c, f);
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not save {}", f.getFileName(), e);
        }
    }

    // the codec wants an item, so nothing is written as one air
    static ItemStack forFile(ItemStack s) {
        return s.isEmpty() ? new ItemStack(net.minecraft.world.item.Items.AIR.builtInRegistryHolder(), 1, DataComponentPatch.EMPTY) : s;
    }

    static ItemStack[] normal() {
        return NORMAL;
    }

    // a loadout switch: its stored pieces are worn now. the menus correct it once they show them
    static void put(ItemStack[] pieces) {
        for (int i = 0; i < 4; i++) NORMAL[i] = pieces[i].isEmpty() ? ItemStack.EMPTY : pieces[i].copy();
        dirty = true;
        hold = 20;
    }

    // which of the 4 slots a piece goes in by its rarity line, -1 = not equipment
    static int slotOf(ItemStack st) {
        if (st.isEmpty()) return -1;
        List<String> lore = dev.zmuzy.sbc.core.LegacyText.lore(st);
        for (int i = lore.size() - 1; i >= 0; i--) {
            java.util.regex.Matcher m = KIND.matcher(GameState.strip(lore.get(i)));
            if (!m.find()) continue;
            return switch (m.group(1)) {
                case "NECKLACE" -> 0;
                case "CLOAK" -> 1;
                case "BELT" -> 2;
                default -> 3;
            };
        }
        return -1;
    }

    // the item name in "You equipped a X!", null = some other line
    static String equippedName(String legacy, String plain) {
        java.util.regex.Matcher m = EQUIP.matcher(legacy);
        if (m.matches()) return GameState.strip(m.group("item")).trim();
        m = EQUIP_PLAIN.matcher(plain.trim());
        return m.matches() ? m.group(1).trim() : null;
    }

    // puts the piece with this name in its slot of the set that counts here. the chat line only has the name, so
    // the piece comes from what was seen: the inventory, the hand, the stored loadouts. true = set changed
    static boolean equipped(String name, List<ItemStack> seen) {
        if (name == null || name.isEmpty()) return false;
        for (ItemStack st : seen) {
            if (st.isEmpty() || !GameState.strip(st.getHoverName().getString()).trim().equals(name)) continue;
            int slot = slotOf(st);
            if (slot < 0) continue;
            ItemStack[] set = current();
            if (ItemStack.matches(set[slot], st)) return false;
            set[slot] = st.copy();
            dirty = true;
            return true;
        }
        return false;
    }

    static void chat(Minecraft mc, net.minecraft.network.chat.Component message) {
        if (!Panels.on("equipment_slots")) return;
        String name = equippedName(dev.zmuzy.sbc.core.LegacyText.of(message), GameState.strip(message.getString()));
        if (name == null) return;
        List<ItemStack> seen = new ArrayList<>();
        if (mc.player != null) {
            seen.add(mc.player.containerMenu.getCarried());
            for (int i = 0; i < mc.player.getInventory().getContainerSize(); i++) seen.add(mc.player.getInventory().getItem(i));
        }
        seen.add(lastHeld);
        seen.addAll(LoadoutCache.pieces());
        equipped(name, seen);
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
