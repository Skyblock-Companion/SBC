// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.Config;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Safe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

// from skyblocker BackpackPreview: whats in your 9 ender chest pages and 18 backpacks, per profile
// a page gets remembered when its menu closes bc the server only sends a page while youre looking at it
// storage menu says which pages exist and how big. top row of a page is hypixels nav so items start at slot 9
public final class StorageCache {
    private static final Pattern ECHEST_PATTERN = Pattern.compile("Ender Chest.*\\((\\d+)/\\d+\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern BACKPACK_PATTERN = Pattern.compile("Backpack.*\\(Slot #(\\d+)\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern STORAGE_PATTERN = Pattern.compile("Storage", Pattern.CASE_INSENSITIVE);
    private static final Pattern BACKPACK_SIZE_PATTERN = Pattern.compile("has (\\d+) slots", Pattern.CASE_INSENSITIVE);
    private static final Pattern PROFILE_ID = Pattern.compile("^Profile ID: ([0-9a-fA-F-]+)$");
    public static final int STORAGE_SIZE = 27;

    private static final Codec<ItemStack> EMPTY_ALLOWING_ITEMSTACK_CODEC = Codec.lazyInitialized(() -> RecordCodecBuilder.create(instance -> instance.group(
            BuiltInRegistries.ITEM.holderByNameCodec().fieldOf("id").forGetter(ItemStack::typeHolder),
            Codec.INT.orElse(1).fieldOf("count").forGetter(ItemStack::getCount),
            DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(ItemStack::getComponentsPatch)
    ).apply(instance, ItemStack::new)));

    private static final Storage[] storages = new Storage[STORAGE_SIZE];
    // profile whose pages are loaded
    private static String loaded = "";
    private static volatile Path saveDir;
    private static String profileId = "";

    private StorageCache() {}

    public static void init() {
        Safe.afterInit("StorageCache.afterInit", (client, screen, w, h) -> {
            if (screen instanceof AbstractContainerScreen<?> handled) Safe.remove("StorageCache.remove", screen, s -> updateStorage(handled));
        });
    }

    // "Profile ID: ..." comes with every server join
    public static void onChat(String plainLine) {
        Matcher m = PROFILE_ID.matcher(plainLine);
        if (m.matches()) profileId = m.group(1).replace("-", "");
    }

    // profile the last "Profile ID:" line named, empty till one came
    public static String profileId() {
        return profileId;
    }

    public static void tick(Minecraft mc) {
        if (profileId.isEmpty() || mc.level == null) return;
        saveStorages(mc);
        String id = mc.getUser().getProfileId().toString().replace("-", "") + "/" + profileId;
        if (id.equals(loaded)) return;
        Path dir = Config.DIR.resolve("backpack-preview/" + id);
        saveDir = dir;
        CompletableFuture.runAsync(() -> {
            try {
                Files.createDirectories(dir);
            } catch (Exception e) {
                SbcMod.LOG.error("Could not create the storage folder {}", dir, e);
            }
        });
        loaded = id;
        loadStorages(mc);
    }

    private static RegistryOps<Tag> ops(Minecraft mc) {
        return mc.level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
    }

    private static void loadStorages(Minecraft mc) {
        RegistryOps<Tag> ops = ops(mc);
        Path dir = saveDir;
        for (int index = 0; index < STORAGE_SIZE; ++index) {
            storages[index] = null;
            int i = index;
            CompletableFuture.supplyAsync(() -> {
                Path file = dir.resolve(i + ".nbt");
                if (Files.isRegularFile(file)) {
                    try {
                        return Storage.CODEC.parse(ops, NbtIo.read(file)).getOrThrow();
                    } catch (Exception e) {
                        SbcMod.LOG.error("Could not read {}", file.getFileName(), e);
                    }
                }
                return null;
            }).thenAcceptAsync(storage -> {
                // dont overwrite a page seen in the meantime
                if (storages[i] == null) storages[i] = storage;
            }, mc);
        }
    }

    private static void saveStorages(Minecraft mc) {
        for (int index = 0; index < STORAGE_SIZE; ++index)
            if (storages[index] != null && storages[index].dirty) saveStorage(mc, index);
    }

    private static void saveStorage(Minecraft mc, int index) {
        Storage storage = storages[index];
        storage.dirty = false;
        RegistryOps<Tag> ops = ops(mc);
        Path file = saveDir.resolve(index + ".nbt");
        CompletableFuture.runAsync(() -> {
            try {
                NbtIo.write((CompoundTag) Storage.CODEC.encodeStart(ops, storage).getOrThrow(), file);
            } catch (Exception e) {
                SbcMod.LOG.error("Could not write {}", file.getFileName(), e);
            }
        });
    }

    private static void updateStorage(AbstractContainerScreen<?> screen) {
        String title = screen.getTitle().getString();
        int index = indexFromTitle(title);
        AbstractContainerMenu menu = screen.getMenu();
        if (index != -1 && index < STORAGE_SIZE) storages[index] = new Storage(menu.slots.getFirst().container, title, true);
        if (STORAGE_PATTERN.matcher(title).matches()) initializeStorage(menu);
    }

    // blank pages for what the storage menu lists but we havent seen yet. true if anything changed
    public static boolean initializeStorage(AbstractContainerMenu menu) {
        NonNullList<Slot> slots = menu.slots;
        if (slots.size() < 45) return false;
        boolean changed = false;
        // ender chest pages
        for (int i = 9; i < 18; ++i) {
            Slot slot = slots.get(i);
            int index = i - 9;
            if (slot.getItem().isEmpty() || slot.getItem().is(Items.STAINED_GLASS_PANE.red()) || storages[index] != null) continue;
            storages[index] = blank(18);
            changed = true;
        }
        // backpacks
        for (int i = 27; i < 45; ++i) {
            Slot slot = slots.get(i);
            int index = i - 18;
            if (slot.getItem().is(Items.STAINED_GLASS_PANE.brown())) {
                if (storages[index] != null) changed = true;
                storages[index] = null;
            }
            if (storages[index] != null) continue;
            Integer size = backpackSize(slot.getItem());
            if (size != null) {
                storages[index] = blank(size + 9);
                changed = true;
            }
        }
        return changed;
    }

    private static Integer backpackSize(ItemStack stack) {
        var lore = stack.get(DataComponents.LORE);
        if (lore == null) return null;
        for (Component line : lore.lines()) {
            Matcher m = BACKPACK_SIZE_PATTERN.matcher(GameState.strip(line.getString()));
            if (m.find()) return Integer.parseInt(m.group(1));
        }
        return null;
    }

    private static Storage blank(int size) {
        return new Storage(new SimpleContainer(Stream.generate(() -> ItemStack.EMPTY).limit(size).toArray(ItemStack[]::new)), "", true);
    }

    public static Storage[] getStorages() {
        return storages;
    }

    // the open pages own container stands in for a page never seen before so it shows right away
    public static Storage adopt(int index, Container container, String title) {
        storages[index] = new Storage(container, title, true);
        return storages[index];
    }

    public static int indexFromTitle(String title) {
        Matcher echest = ECHEST_PATTERN.matcher(title);
        if (echest.find()) return Integer.parseInt(echest.group(1)) - 1;
        Matcher backpack = BACKPACK_PATTERN.matcher(title);
        if (backpack.find()) return Integer.parseInt(backpack.group(1)) + 8;
        return -1;
    }

    // a pages items without hypixels nav row, for the hover preview
    public record Page(String title, List<ItemStack> items) {
        public int rows() {
            return Math.max(1, items.size() / 9);
        }

        public int count() {
            int n = 0;
            for (ItemStack st : items) if (!st.isEmpty()) n++;
            return n;
        }
    }

    // remembered page contents, null if not seen yet
    public static Page page(boolean ender, int number) {
        int index = ender ? number - 1 : number + 8;
        if (index < 0 || index >= STORAGE_SIZE || storages[index] == null) return null;
        Storage s = storages[index];
        List<ItemStack> items = new ArrayList<>();
        for (int i = 9; i < s.size(); i++) items.add(s.getStack(i));
        return new Page(s.name().isEmpty() ? (ender ? "Ender Chest " + number : "Backpack " + number) : s.name(), items);
    }

    public static final class Storage {
        private static final Codec<Storage> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(Storage::name),
                EMPTY_ALLOWING_ITEMSTACK_CODEC.listOf().fieldOf("items").forGetter(Storage::items)
        ).apply(instance, Storage::create));
        private final Container inventory;
        private final String name;
        private boolean dirty;

        private Storage(Container inventory, String name, boolean dirty) {
            this.inventory = inventory;
            this.name = name;
            this.dirty = dirty;
        }

        private String name() {
            return name;
        }

        public int size() {
            return inventory.getContainerSize();
        }

        public ItemStack getStack(int index) {
            return inventory.getItem(index);
        }

        private static Storage create(String name, List<ItemStack> items) {
            return new Storage(new SimpleContainer(items.toArray(ItemStack[]::new)), name, false);
        }

        private List<ItemStack> items() {
            List<ItemStack> items = new ArrayList<>();
            for (int i = 0; i < size(); ++i) items.add(getStack(i));
            return items;
        }
    }
}
