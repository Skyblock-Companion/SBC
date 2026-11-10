// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.core;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.item.component.ResolvableProfile;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

// icons that look like the real sb item. each stack carries its sb id (custom_data) so packs that retexture
// sb items (hypixels own, FurfSky, Defrosted...) draw it their way
public final class ItemIcons {
    private static final Map<String, ItemStack> cache = new HashMap<>();
    // the model hypixel itself puts on an item, by sb id, from every item seen in your inventory or an open
    // menu. it beats our data, which can be older than the item
    private static final Map<String, String> seen = new java.util.concurrent.ConcurrentHashMap<>();

    private ItemIcons() {}

    // stack for an sb id. texture/color/glint from the item itself beat the repo (pass null/-1/false)
    public static ItemStack of(String sbId, String fallbackVanilla, String texture, int color, boolean glint) {
        String key = sbId + "|" + fallbackVanilla + "|" + (texture == null ? "" : Integer.toHexString(texture.hashCode())) + "|" + color + "|" + glint;
        ItemStack c = cache.get(key);
        if (c != null) return c;
        try {
            ItemRepo.Entry e = sbId == null || sbId.isEmpty() ? null : ItemRepo.get(sbId);
            String material = e != null ? e.material() : "minecraft:" + (fallbackVanilla == null || fallbackVanilla.isEmpty() ? "paper" : fallbackVanilla);
            Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(material));
            if (item == null || item == Items.AIR) item = Items.PAPER;
            ItemStack st = new ItemStack(item);
            if (sbId != null && !sbId.isEmpty()) {
                CompoundTag tag = new CompoundTag();
                tag.putString("id", sbId);
                st.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            }
            String model = sbId == null ? null : seen.getOrDefault(sbId, e != null ? e.model() : null);
            if (model != null && modelLoaded(model)) st.set(DataComponents.ITEM_MODEL, Identifier.parse(model));
            String tex = texture != null ? texture : e != null ? e.texture() : null;
            if (tex != null && item == Items.PLAYER_HEAD) st.set(DataComponents.PROFILE, head(tex));
            if (glint || (e != null && e.glint())) st.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
            if (color >= 0) st.set(DataComponents.DYED_COLOR, new DyedItemColor(color));
            cache.put(key, st);
            if (cache.size() > 3000) cache.clear();
            return st;
        } catch (Exception ex) {
            return LegacyItems.stack(fallbackVanilla == null ? "paper" : fallbackVanilla);
        }
    }

    public static ItemStack of(String sbId) {
        String[] pet = PetCard.parse(sbId);
        if (pet != null) return pet(pet[0], pet[1]);
        return of(sbId, "paper", null, -1, false);
    }

    // pet head in its rarity skin
    public static ItemStack pet(String type, String tier) {
        String tex = ItemRepo.petTexture(type, tier);
        return of("PET_" + type, "player_head", tex, -1, false);
    }

    public static ResolvableProfile head(String base64) {
        UUID id = UUID.nameUUIDFromBytes(base64.getBytes(StandardCharsets.UTF_8));
        PropertyMap props = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", base64)));
        return ResolvableProfile.createResolved(new GameProfile(id, "Pet", props));
    }

    private static final Identifier NOT_A_MODEL = Identifier.fromNamespaceAndPath("sbcompanion", "definitely_missing");

    // hypixels item models only exist while their server pack is loaded. the game's "missing" model is a new
    // one after every pack reload, so it is asked for each time
    private static boolean modelLoaded(String model) {
        var mm = Minecraft.getInstance().getModelManager();
        try {
            return mm.getItemModel(Identifier.parse(model)) != mm.getItemModel(NOT_A_MODEL);
        } catch (Exception e) {
            return false;
        }
    }

    // once a second: what hypixel put on the items you can see right now
    public static void noteSeen(Minecraft mc) {
        if (mc.player == null) return;
        boolean added = false;
        for (ItemStack st : mc.player.getInventory().getNonEquipmentItems()) added |= note(st);
        if (mc.player.containerMenu != null) for (var slot : mc.player.containerMenu.slots) added |= note(slot.getItem());
        if (added) {
            cache.clear();
            ItemInfo.clearCache();
        }
    }

    private static boolean note(ItemStack st) {
        if (st == null || st.isEmpty()) return false;
        Identifier model = st.get(DataComponents.ITEM_MODEL);
        if (model == null || model.getNamespace().equals("minecraft")) return false;
        String id = Gain.sbId(st);
        if (id.isEmpty()) return false;
        return !model.toString().equals(seen.put(id, model.toString()));
    }

    // call on resource pack reload so models get checked again
    public static void reset() {
        cache.clear();
        ItemInfo.clearCache();
    }
}
