// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui.panels;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.Config;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

// each loadouts equipment (necklace, cloak, belt, gloves) + pet as the loadouts menu showed them while it was the
// one on, per profile. a switch copies the stored pieces into EquipmentCache before any menu shows them
final class LoadoutCache {
    static final int PET = 4, PIECES = 5;
    private record Entry(int loadout, List<ItemStack> items) {}
    private static final Codec<List<Entry>> LIST = Codec.lazyInitialized(() -> RecordCodecBuilder.<Entry>create(i -> i.group(
            Codec.INT.fieldOf("loadout").forGetter(Entry::loadout),
            EquipmentCache.STACK.listOf().fieldOf("items").forGetter(Entry::items)
    ).apply(i, Entry::new)).listOf().fieldOf("loadouts").codec());

    private static final Map<Integer, ItemStack[]> STORED = new TreeMap<>();
    private static String loaded = "";
    private static boolean dirty;

    private LoadoutCache() {}

    // what the menu shows for the loadout thats on. true = something new to save
    static boolean remember(int loadout, List<ItemStack> equipment, ItemStack pet) {
        if (loadout < 0 || equipment.size() < 4) return false;
        ItemStack[] now = new ItemStack[PIECES];
        for (int i = 0; i < 4; i++) now[i] = equipment.get(i).isEmpty() ? ItemStack.EMPTY : equipment.get(i).copy();
        now[PET] = pet.isEmpty() ? ItemStack.EMPTY : pet.copy();
        ItemStack[] old = STORED.get(loadout);
        if (old != null) {
            boolean same = true;
            for (int i = 0; i < PIECES; i++) if (!ItemStack.matches(old[i], now[i])) same = false;
            if (same) return false;
        }
        STORED.put(loadout, now);
        dirty = true;
        return true;
    }

    // 4 pieces + pet, null = never seen
    static ItemStack[] stored(int loadout) {
        return STORED.get(loadout);
    }

    static ItemStack pet(int loadout) {
        ItemStack[] s = STORED.get(loadout);
        return s == null ? ItemStack.EMPTY : s[PET];
    }

    // switching to this loadout: its pieces are what you wear now. false = nothing stored for it
    static boolean apply(int loadout) {
        ItemStack[] s = STORED.get(loadout);
        if (s == null) return false;
        EquipmentCache.put(Arrays.copyOf(s, 4));
        return true;
    }

    // every stored piece, for working out what a chat line equipped
    static List<ItemStack> pieces() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack[] s : STORED.values()) for (int i = 0; i < 4; i++) if (!s[i].isEmpty()) out.add(s[i]);
        return out;
    }

    static void tick(Minecraft mc) {
        if (mc.level == null) return;
        if (!PanelStore.key().equals(loaded)) {
            save(mc);
            loaded = PanelStore.key();
            load(mc);
        }
        if (dirty && mc.gui.screen() == null) save(mc);
    }

    private static Path file() {
        return Config.DIR.resolve("equipment").resolve(loaded + "_loadouts.nbt");
    }

    static void save(Minecraft mc) {
        if (loaded.isEmpty() || mc.level == null) return;
        dirty = false;
        Path f = file();
        try {
            RegistryOps<Tag> ops = mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            List<Entry> all = new ArrayList<>();
            for (var e : STORED.entrySet()) {
                List<ItemStack> items = new ArrayList<>(PIECES);
                for (ItemStack s : e.getValue()) items.add(EquipmentCache.forFile(s));
                all.add(new Entry(e.getKey(), items));
            }
            Tag tag = LIST.encodeStart(ops, all).getOrThrow();
            Files.createDirectories(f.getParent());
            if (tag instanceof CompoundTag c) NbtIo.write(c, f);
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not save {}", f.getFileName(), e);
        }
    }

    private static void load(Minecraft mc) {
        STORED.clear();
        dirty = false;
        if (loaded.isEmpty()) return;
        Path f = file();
        try {
            if (!Files.isRegularFile(f)) return;
            RegistryOps<Tag> ops = mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
            for (Entry e : LIST.parse(ops, NbtIo.read(f)).getOrThrow()) {
                ItemStack[] s = new ItemStack[PIECES];
                for (int i = 0; i < PIECES; i++) s[i] = i < e.items().size() && !e.items().get(i).isEmpty() ? e.items().get(i) : ItemStack.EMPTY;
                STORED.put(e.loadout(), s);
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read {}", f.getFileName(), e);
        }
    }

    // for tests
    static void clear() {
        STORED.clear();
        dirty = false;
    }
}
