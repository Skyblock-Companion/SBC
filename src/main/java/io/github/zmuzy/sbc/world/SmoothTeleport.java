// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.world;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Set;

// from skyblocker teleport/ResponsiveSmoothAOTE: when the server moves you while you hold a teleport item the
// camera slides there instead of jumping. their predictive mode isnt ported
public final class SmoothTeleport {
    public static final String ID = "smooth_teleport";
    // from skyblocker PredictiveSmoothAOTE.getItemDistance: everything that teleports
    private static final Set<String> ITEMS = Set.of("ASPECT_OF_THE_LEECH_1", "ASPECT_OF_THE_LEECH_2", "ASPECT_OF_THE_END", "ASPECT_OF_THE_VOID", "ETHERWARP_CONDUIT",
            "SINSEEKER_SCYTHE", "NECRON_BLADE", "ASTRAEA", "HYPERION", "SCYLLA", "VALKYRIE");

    private static long startTime;
    private static Vec3 lastPosition;
    private static double lastProgress;
    private static Vec3 cameraStart;

    private SmoothTeleport() {}

    private static int duration() {
        Feature f = Features.get(ID);
        return f == null ? 100 : f.option("lag").integer();
    }

    private static boolean thirdPersonAllowed() {
        Feature f = Features.get(ID);
        return f != null && f.option("third_person").bool();
    }

    // ClientPacketListener.handleMovePlayer, both passes come here, only the game thread counts
    public static void playerGoingToTeleport() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.isSameThread() || !Features.on(ID) || duration() == 0) return;
        ItemStack held = mc.player.getMainHandItem();
        var cd = held.get(DataComponents.CUSTOM_DATA);
        if (cd == null || !ITEMS.contains(cd.copyTag().getStringOr("id", ""))) return;
        if (mc.options.getCameraType() != CameraType.FIRST_PERSON && !thirdPersonAllowed()) return;
        lastPosition = mc.gameRenderer.mainCamera().position();
        lastProgress = 0;
        startTime = System.currentTimeMillis();
    }

    // Camera.position() runs a lot so the common case is one null check
    public static Vec3 camera(Vec3 original) {
        if (lastPosition == null) return original;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            lastPosition = null;
            return original;
        }
        cameraStart = original;
        double progress = (double) (System.currentTimeMillis() - startTime) / Math.max(1, duration());
        if (progress >= 1) {
            lastPosition = null;
            lastProgress = 0;
            return original;
        }
        Vec3 vector = original.subtract(lastPosition);
        double relative = (progress - lastProgress) / (1 - lastProgress);
        lastPosition = lastPosition.add(vector.scale(relative));
        lastProgress = progress;
        return lastPosition;
    }

    // from skyblocker getInterpolatedPlayerPos: in third person your model goes with the camera. null = nothing sliding
    public static Vec3 player(float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        if (lastPosition == null || mc.player == null || cameraStart == null) return null;
        return lastPosition.add(mc.player.getPosition(partialTicks).subtract(cameraStart));
    }

    public static void register() {
        Features.add(new Feature(ID, "World", "Smooth teleports", "The camera slides to where a teleport item put you instead of jumping.", "Skyblocker: smooth AOTE")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).defaultOff()
                .left(() -> lastPosition = null)
                .option(Option.integer("smooth_teleport.lag", "Slide time (ms)", "How long the slide takes; you see the new place this much later.", 100, 0, 500))
                .option(Option.bool("smooth_teleport.third_person", "Also in third person", "", true)));
    }
}
