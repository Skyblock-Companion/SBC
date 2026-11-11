// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.item.menus;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
//? if <26.3 {
/*import org.lwjgl.glfw.GLFW;
*///?}

// is a key down rn, also with a screen open where KeyMapping state doesnt move
final class Keys {
    private Keys() {}

    //? if >=26.3 {
    static boolean down(int key) {
    //?} else {
    /*static boolean down(int glfwKey) {
    *///?}
        try {
            //? if >=26.3 {
            return key > 0 && InputConstants.isKeyDown(key);
            //?} else {
            /*return glfwKey > 0 && InputConstants.isKeyDown(Minecraft.getInstance().getWindow(), glfwKey);
            *///?}
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean down(KeyMapping key) {
        return io.muzy.sbc.mc.KeyHeld.held(key);
    }

    // a setting that names a modifier
    static boolean modifier(String name) {
        return switch (name) {
            //? if >=26.3 {
            case "Ctrl" -> down(InputConstants.KEY_LCONTROL) || down(InputConstants.KEY_RCONTROL);
            case "Alt" -> down(InputConstants.KEY_LALT) || down(InputConstants.KEY_RALT);
            default -> down(InputConstants.KEY_LSHIFT) || down(InputConstants.KEY_RSHIFT);
            //?} else {
            /*case "Ctrl" -> down(GLFW.GLFW_KEY_LEFT_CONTROL) || down(GLFW.GLFW_KEY_RIGHT_CONTROL);
            case "Alt" -> down(GLFW.GLFW_KEY_LEFT_ALT) || down(GLFW.GLFW_KEY_RIGHT_ALT);
            default -> down(GLFW.GLFW_KEY_LEFT_SHIFT) || down(GLFW.GLFW_KEY_RIGHT_SHIFT);
            *///?}
        };
    }
}
