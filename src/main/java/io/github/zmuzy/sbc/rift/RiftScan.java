// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.rift;

import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.Repo;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// what this packs area features share: skyhanni SkullTextureHolder, InventoryUtils held item,
// LocationUtils.interpolateOverTime + a cylinder of lines
public final class RiftScan {
    private static final Map<String, String> SKULLS = new HashMap<>();

    private RiftScan() {}

    // from skyhanni SkullTextureHolder.texture: a Skulls.json texture, "" = unknown
    public static String skull(String key) {
        String t = SKULLS.get(key);
        if (t != null) return t;
        var o = Repo.constant("Skulls.json");
        t = o != null && o.has(key) ? o.get(key).getAsString() : "";
        if (o != null) SKULLS.put(key, t);
        return t;
    }

    // from skyhanni getSkullTexture, null = not a skull with a texture
    public static String texture(ItemStack head) {
        var profile = head.get(DataComponents.PROFILE);
        if (profile == null) return null;
        try {
            for (var pr : profile.partialProfile().properties().get("textures")) return pr.value();
        } catch (Exception ignored) {
        }
        return null;
    }

    public static String heldId(Minecraft mc) {
        return mc.player == null ? "" : Gain.sbId(mc.player.getMainHandItem());
    }

    // mob under its name stand, hypixel name tags ride a separate armor stand
    public static LivingEntity mobUnder(ArmorStand st) {
        LivingEntity base = null;
        for (var c : st.level().getEntities(st, st.getBoundingBox().inflate(0.5, 2.5, 0.5)))
            if (c instanceof LivingEntity le && !(c instanceof ArmorStand) && !(c instanceof Player)) base = le;
        return base;
    }

    // from skyhanni LocationUtils.interpolateOverTime
    public static Vec3 lerp(long start, long millis, Vec3 from, Vec3 to, long now) {
        long diff = now - start;
        if (diff >= millis) return to;
        return from.add(to.subtract(from).scale(diff / (double) millis));
    }

    // from skyhanni drawCylinderInWorld, as rings + uprights so it stays light
    public static void cylinder(WorldDraw w, Vec3 base, double radius, double height, int argb) {
        int n = 32;
        Vec3 prevLow = null, prevHigh = null;
        for (int i = 0; i <= n; i++) {
            double a = 2 * Math.PI * i / n;
            Vec3 low = base.add(radius * Math.cos(a), 0, radius * Math.sin(a)), high = low.add(0, height, 0);
            if (prevLow != null) {
                w.line(prevLow, low, argb, 2f, false);
                w.line(prevHigh, high, argb, 2f, false);
            }
            if (i % 8 == 0) w.line(low, high, argb, 2f, false);
            prevLow = low;
            prevHigh = high;
        }
    }

    public static int color(String feature, String key, int def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.color();
    }

    public static boolean on(String feature, String key, boolean def) {
        Feature f = Features.get(feature);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    // "12.3s"
    public static String seconds(long millis) {
        return String.format(Locale.ROOT, "%.1fs", millis / 1000.0);
    }

    static <T> T at(List<T> list, int i) {
        return i >= 0 && i < list.size() ? list.get(i) : null;
    }
}
