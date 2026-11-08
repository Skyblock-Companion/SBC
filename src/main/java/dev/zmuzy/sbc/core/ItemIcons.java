// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.core;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.item.ItemModel;
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
            if (e != null && e.model() != null && modelLoaded(e.model())) st.set(DataComponents.ITEM_MODEL, Identifier.parse(e.model()));
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

    private static ItemModel missing;

    // hypixels item models only exist while their server pack is loaded
    private static boolean modelLoaded(String model) {
        var mm = Minecraft.getInstance().getModelManager();
        if (missing == null) missing = mm.getItemModel(Identifier.fromNamespaceAndPath("sbcompanion", "definitely_missing"));
        return mm.getItemModel(Identifier.parse(model)) != missing;
    }

    // call on resource pack reload so models get checked again
    public static void reset() {
        cache.clear();
        ItemInfo.clearCache();
        missing = null;
    }
}
