// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.function.IntPredicate;

// from skyhanni KeyboardManager.isKeyHeld: is the key a KeyMapping is bound to down rn, also with a screen
// open where the mapping state doesnt move
// keys can be bound to mouse buttons and glfw asks for those differently
public final class KeyHeld {
    private KeyHeld() {}

    public static boolean held(KeyMapping key) {
        if (key == null || key.isUnbound()) return false;
        try {
            Minecraft mc = Minecraft.getInstance();
            long window = mc.getWindow().handle();
            return held(KeyMappingHelper.getBoundKeyOf(key), button -> GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS, code -> InputConstants.isKeyDown(mc.getWindow(), code));
        } catch (Throwable t) {
            return false;
        }
    }

    // same for a key with no KeyMapping, like a shortcuts
    public static boolean held(InputConstants.Key key) {
        try {
            Minecraft mc = Minecraft.getInstance();
            long window = mc.getWindow().handle();
            return held(key, button -> GLFW.glfwGetMouseButton(window, button) == GLFW.GLFW_PRESS, code -> InputConstants.isKeyDown(mc.getWindow(), code));
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean held(InputConstants.Key key, IntPredicate mouse, IntPredicate keyboard) {
        if (key.getValue() == InputConstants.UNKNOWN.getValue()) return false;
        return key.getType() == InputConstants.Type.MOUSE ? mouse.test(key.getValue()) : keyboard.test(key.getValue());
    }
}
