// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.item.menus;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

// is a key down rn, also with a screen open where KeyMapping state doesnt move
final class Keys {
    private Keys() {}

    static boolean down(int key) {
        try {
            return key > 0 && InputConstants.isKeyDown(key);
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean down(KeyMapping key) {
        return io.github.zmuzy.sbc.mc.KeyHeld.held(key);
    }

    // a setting that names a modifier
    static boolean modifier(String name) {
        return switch (name) {
            case "Ctrl" -> down(InputConstants.KEY_LCONTROL) || down(InputConstants.KEY_RCONTROL);
            case "Alt" -> down(InputConstants.KEY_LALT) || down(InputConstants.KEY_RALT);
            default -> down(InputConstants.KEY_LSHIFT) || down(InputConstants.KEY_RSHIFT);
        };
    }
}
