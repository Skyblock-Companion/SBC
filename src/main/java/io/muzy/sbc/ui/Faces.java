// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

// flat face from a skin texture like the tab list: face + hat layer, sharp at any size, nothing behind
// skin loads in the background through the games skin manager, default skin till then
public final class Faces {
    private static final Map<String, Supplier<PlayerSkin>> LOOKUPS = new HashMap<>();

    private Faces() {}

    // null without a game
    public static Identifier skin(String texture) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || texture == null || texture.isEmpty()) return null;
        if (LOOKUPS.size() > 512) LOOKUPS.clear();
        Supplier<PlayerSkin> lookup = LOOKUPS.computeIfAbsent(texture, t -> {
            PropertyMap props = new PropertyMap(ImmutableMultimap.of("textures", new Property("textures", t)));
            GameProfile profile = new GameProfile(UUID.nameUUIDFromBytes(t.getBytes(StandardCharsets.UTF_8)), "npc", props);
            return mc.getSkinManager().createLookup(profile, false);
        });
        return lookup.get().body().texturePath();
    }

    // false = no skin to draw
    public static boolean draw(GuiGraphicsExtractor g, String texture, int x, int y, int size) {
        Identifier id = skin(texture);
        if (id == null || g == null) return false;
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 8f, 8f, size, size, 8, 8, 64, 64);
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 40f, 8f, size, size, 8, 8, 64, 64);
        return true;
    }
}
