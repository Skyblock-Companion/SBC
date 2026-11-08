// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.item.menus;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

// is a key down rn, also with a screen open where KeyMapping state doesnt move
final class Keys {
    private Keys() {}

    static boolean down(int glfwKey) {
        try {
            return glfwKey > 0 && InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), glfwKey);
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean down(KeyMapping key) {
        return dev.zmuzy.sbc.mc.KeyHeld.held(key);
    }

    // a setting that names a modifier
    static boolean modifier(String name) {
        return switch (name) {
            case "Ctrl" -> down(GLFW.GLFW_KEY_LEFT_CONTROL) || down(GLFW.GLFW_KEY_RIGHT_CONTROL);
            case "Alt" -> down(GLFW.GLFW_KEY_LEFT_ALT) || down(GLFW.GLFW_KEY_RIGHT_ALT);
            default -> down(GLFW.GLFW_KEY_LEFT_SHIFT) || down(GLFW.GLFW_KEY_RIGHT_SHIFT);
        };
    }
}
