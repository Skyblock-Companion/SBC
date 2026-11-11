// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mc.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import io.muzy.sbc.core.Safe;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// from skyblocker CameraMixin. optional, if it fails teleports just jump
@Mixin(Camera.class)
public abstract class SmoothTeleportMixin {
    @ModifyReturnValue(method = "position", at = @At("RETURN"), require = 0)
    private Vec3 sbc$position(Vec3 original) {
        if (Safe.resting("SmoothTeleportMixin.position")) return original;
        try {
            return io.muzy.sbc.world.SmoothTeleport.camera(original);
        } catch (Throwable t) {
            Safe.caught("SmoothTeleportMixin.position", t);
            return original;
        }
    }
}
