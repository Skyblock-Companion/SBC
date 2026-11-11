// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.world;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

// from skyblocker skyblock/GyroOverlay: how far the gyrokinetic wand pulls around where you aim
public final class GyroOverlay {
    public static final String ID = "gyro_overlay";
    private static final int GYRO_RADIUS = 10, MAX_REACH = 24;

    private static ItemStack lastStack;
    private static boolean lastWand;

    private GyroOverlay() {}

    private static boolean wand(ItemStack stack) {
        if (stack != lastStack) {
            lastStack = stack;
            var cd = stack.get(DataComponents.CUSTOM_DATA);
            lastWand = cd != null && "GYROKINETIC_WAND".equals(cd.copyTag().getStringOr("id", ""));
        }
        return lastWand;
    }

    private static void draw(WorldDraw w) {
        Minecraft mc = w.mc;
        Entity camera = mc.getCameraEntity();
        if (mc.player == null || mc.level == null || camera == null || !wand(mc.player.getMainHandItem())) return;
        HitResult hit = camera.pick(MAX_REACH, mc.getDeltaTracker().getGameTimeDeltaPartialTick(false), false);
        if (hit.getType() == HitResult.Type.MISS) return;
        Feature f = Features.get(ID);
        int color = f.option("color").color();
        Vec3 at = hit.getLocation().add(0, 0.1, 0);
        switch (f.option("look").choice()) {
            case "Circle" -> Gizmos.circle(at, GYRO_RADIUS, GizmoStyle.fill(color));
            case "Sphere" -> sphere(hit.getLocation(), color);
            default -> Gizmos.circle(at, GYRO_RADIUS, GizmoStyle.stroke(color | 0xFF000000, 2.5f));
        }
    }

    // rings at the heights of a ball, same reach seen from any side
    private static void sphere(Vec3 at, int color) {
        GizmoStyle stroke = GizmoStyle.stroke(color | 0xFF000000, 1.5f);
        for (int y = -8; y <= 8; y += 4) {
            float r = (float) Math.sqrt(GYRO_RADIUS * GYRO_RADIUS - y * y);
            Gizmos.circle(at.add(0, y, 0), r, stroke);
        }
    }

    public static void register() {
        Features.add(new Feature(ID, "World", "Gyrokinetic Wand reach", "The ten blocks the wand pulls from, around where you aim it, while you hold it.", "Skyblocker: gyro overlay")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).world(GyroOverlay::draw)
                .option(Option.choice("gyro_overlay.look", "Look", "", "Circle outline", "Circle outline", "Circle", "Sphere"))
                .option(Option.color("gyro_overlay.color", "Colour", "", 0x7F761594)));
    }
}
