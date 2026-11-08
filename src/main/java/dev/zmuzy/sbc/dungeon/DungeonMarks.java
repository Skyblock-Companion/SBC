// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.dungeon;

import com.google.gson.JsonObject;
import dev.zmuzy.sbc.core.Repo;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.render.WorldDraw;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

// small stuff the dungeon helpers share: skyblocker ItemUtils.getHeadTexture, skyhanni SkullTextureHolder
// (Skulls.json), the 5 looks of a skyblocker Waypoint.Type, so one marker setting works everywhere
public final class DungeonMarks {
    // from skyblocker Waypoint.Type: WAYPOINT, OUTLINED_WAYPOINT, HIGHLIGHT, OUTLINED_HIGHLIGHT, OUTLINE
    public static final String[] LOOKS = {"Beam", "Beam and outline", "Filled", "Filled and outline", "Outline"};
    private static final Map<String, String> SKULLS = new HashMap<>();

    private DungeonMarks() {}

    public static String headTexture(ItemStack stack) {
        if (stack == null || !stack.is(Items.PLAYER_HEAD)) return "";
        var profile = stack.get(DataComponents.PROFILE);
        if (profile == null) return "";
        for (var p : profile.partialProfile().properties().get("textures")) if (p != null) return p.value();
        return "";
    }

    // from skyhanni SkullTextureHolder.texture: a names texture in Skulls.json, empty = none
    public static String skull(String name) {
        return SKULLS.computeIfAbsent(name, n -> {
            JsonObject o = Repo.constant("Skulls.json");
            return o != null && o.has(n) ? o.get(n).getAsString() : "";
        });
    }

    private static Option option(String feature, String key) {
        Feature f = Features.get(feature);
        return f == null ? null : f.option(key);
    }

    public static boolean opt(String feature, String key) {
        Option o = option(feature, key);
        return o == null || o.bool();
    }

    public static String choice(String feature, String key, String fallback) {
        Option o = option(feature, key);
        return o == null ? fallback : o.choice();
    }

    public static int color(String feature, String key, int fallback) {
        Option o = option(feature, key);
        return o == null ? fallback : o.color();
    }

    public static int integer(String feature, String key, int fallback) {
        Option o = option(feature, key);
        return o == null ? fallback : o.integer();
    }

    public static String text(String feature, String key, String fallback) {
        Option o = option(feature, key);
        return o == null ? fallback : o.text();
    }

    public static double number(String feature, String key, double fallback) {
        Option o = option(feature, key);
        return o == null ? fallback : o.number();
    }

    // one marker in the look a setting picks
    public static void draw(WorldDraw w, AABB box, int argb, String look, boolean walls) {
        Vec3 c = box.getCenter();
        switch (look) {
            case "Beam" -> {
                w.filledBox(box, argb, 0.35f, walls);
                w.beacon(new Vec3(c.x, box.maxY, c.z), argb, walls);
            }
            case "Beam and outline" -> {
                w.box(box, argb, true, walls);
                w.beacon(new Vec3(c.x, box.maxY, c.z), argb, walls);
            }
            case "Filled" -> w.filledBox(box, argb, 0.35f, walls);
            case "Outline" -> w.box(box, argb, false, walls);
            default -> w.box(box, argb, true, walls);
        }
    }
}
