// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;

// the item in your hand, smaller and floating, with its own swing speed. the bare hand stays as the game draws it
public final class HeldItem {
    public static final String ID = "held_item";
    // vanilla swing is 6 ticks, the slowest we allow is 20x that
    static final int LONGEST = 120;

    private HeldItem() {}

    private static double n(String key, double def) {
        Feature f = Features.get(ID);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.number();
    }

    private static boolean b(String key) {
        Feature f = Features.get(ID);
        Option o = f == null ? null : f.option(key);
        return o != null && o.bool();
    }

    public static boolean on() {
        return Features.on(ID);
    }

    // runs before the game moves the arm, so the swing keeps vanillas arc from the new spot
    // doing it after the swing makes the offset spin with the arc and the item circles. left hand is mirrored
    public static void place(PoseStack pose, boolean right) {
        float side = right ? 1 : -1;
        pose.translate(side * (float) n("held_item.x", 0), (float) n("held_item.y", 0), (float) n("held_item.z", 0));
        pose.mulPose(Axis.XP.rotationDegrees((float) n("held_item.pitch", 0)));
        pose.mulPose(Axis.YP.rotationDegrees(side * (float) n("held_item.yaw", 0)));
        pose.mulPose(Axis.ZP.rotationDegrees(side * (float) n("held_item.roll", 0)));
    }

    // right before the item is drawn, so it shrinks around itself
    public static void size(PoseStack pose) {
        float s = (float) n("held_item.scale", 1);
        pose.scale(s, s, s);
    }

    // 0 = no swing at all
    public static boolean swings() {
        return n("held_item.swing_speed", 1) > 0;
    }

    public static boolean noEquip() {
        return b("held_item.no_equip");
    }

    public static boolean ignoreHaste() {
        return b("held_item.ignore_haste");
    }

    // ticks for one swing: faster is shorter
    static int duration(int base, double speed) {
        if (speed <= 0) return base;
        return (int) Math.max(1, Math.min(LONGEST, Math.round(base / speed)));
    }

    public static int duration(int base) {
        return duration(base, n("held_item.swing_speed", 1));
    }

    public static void register() {
        Features.add(new Feature(ID, "Items", "Viewmodel", "Held item animation: the item in your hand smaller and floating, moved and turned how you like, with a slow or fast swing that plays smoothly however fast you click. Your bare hand is not changed. Only how it looks to you: hits and abilities stay as fast as they are.", "")
                .option(Option.number("held_item.scale", "Size", "1 is the game's own size.", 0.45, 0.1, 2, 0.05))
                .option(Option.number("held_item.x", "Left / right", "", 0, -1.5, 1.5, 0.02))
                .option(Option.number("held_item.y", "Down / up", "", 0.06, -1.5, 1.5, 0.02))
                .option(Option.number("held_item.z", "Near / far", "Lower is further away.", -0.28, -1.5, 1.5, 0.02))
                .option(Option.number("held_item.yaw", "Turn sideways", "", 0, -180, 180, 1))
                .option(Option.number("held_item.pitch", "Tilt forward", "", 0, -180, 180, 1))
                .option(Option.number("held_item.roll", "Roll", "", 0, -180, 180, 1))
                .option(Option.number("held_item.swing_speed", "Swing speed", "1 is the game's speed, under 1 slower, over 1 faster, 0 no swing.", 0.5, 0, 3, 0.05))
                .option(Option.bool("held_item.ignore_haste", "Haste doesn't change the swing", "Haste and Mining Fatigue keep their hands off the swing speed.", true))
                .option(Option.bool("held_item.no_equip", "No equip animation", "The item doesn't drop and rise when you switch slots.", false)));
    }
}
