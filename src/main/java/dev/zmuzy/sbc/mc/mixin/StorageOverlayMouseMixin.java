// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.ui.storage.StorageOverlayScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.joml.Vector2dc;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// like skyblocker: switching pages closes and reopens the menu, cursor stays put
@Mixin(MouseHandler.class)
public class StorageOverlayMouseMixin {
    @Final
    @Shadow
    private Minecraft minecraft;

    @Shadow
    private double xpos;

    @Shadow
    private double ypos;

    @Inject(method = "releaseMouse", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/InputConstants;grabOrReleaseMouse(Lcom/mojang/blaze3d/platform/Window;IDD)V", shift = At.Shift.AFTER))
    private void sbc$keepMouseInStorageOverlay(CallbackInfo ci) {
        if (Safe.resting("StorageOverlayMouseMixin.keepMouseInStorageOverlay")) return;
        try {
            if (minecraft.gui.screen() instanceof StorageOverlayScreen) {
                Vector2dc position = StorageOverlayScreen.getPreviousMousePosition();
                if (position != null) {
                    this.xpos = position.x();
                    this.ypos = position.y();
                }
                GLFW.glfwSetCursorPos(minecraft.getWindow().handle(), xpos, ypos);
            }
        } catch (Throwable t) {
            Safe.caught("StorageOverlayMouseMixin.keepMouseInStorageOverlay", t);
        }
    }
}
