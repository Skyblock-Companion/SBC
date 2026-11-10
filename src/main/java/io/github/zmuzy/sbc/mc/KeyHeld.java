// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.mc;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import org.lwjgl.sdl.SDLMouse;

import java.util.function.IntPredicate;

// from skyhanni KeyboardManager.isKeyHeld: is the key a KeyMapping is bound to down rn, also with a screen
// open where the mapping state doesnt move
// keys can be bound to mouse buttons and sdl asks for those differently
public final class KeyHeld {
    private KeyHeld() {}

    public static boolean held(KeyMapping key) {
        if (key == null || key.isUnbound()) return false;
        try {
            return held(KeyMappingHelper.getBoundKeyOf(key));
        } catch (Throwable t) {
            return false;
        }
    }

    // same for a key with no KeyMapping, like a shortcuts
    public static boolean held(InputConstants.Key key) {
        try {
            return held(key, KeyHeld::mouseDown, InputConstants::isKeyDown);
        } catch (Throwable t) {
            return false;
        }
    }

    // sdl buttons start at 1 (left), one bit each in the mouse state
    public static boolean mouseDown(int button) {
        try {
            return button >= 1 && button <= 32 && (SDLMouse.SDL_GetMouseState(null, null) & (1 << (button - 1))) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    // sdl mouse button to the container click number (0 left, 1 right, 2 middle), the way the game maps it
    public static int clickButton(int button) {
        return switch (button) {
            case InputConstants.MOUSE_BUTTON_LEFT -> 0;
            case InputConstants.MOUSE_BUTTON_RIGHT -> 1;
            default -> button;
        };
    }

    // the key a typed digit or letter sits on. sdl puts 0 after 9, so digits are not one run from KEY_0
    public static int keyOf(char c) {
        c = Character.toUpperCase(c);
        if (c == '0') return InputConstants.KEY_0;
        if (c >= '1' && c <= '9') return InputConstants.KEY_1 + (c - '1');
        if (c >= 'A' && c <= 'Z') return InputConstants.KEY_A + (c - 'A');
        return -1;
    }

    static boolean held(InputConstants.Key key, IntPredicate mouse, IntPredicate keyboard) {
        if (key.getValue() == InputConstants.UNKNOWN.getValue()) return false;
        return key.getType() == InputConstants.Type.MOUSE ? mouse.test(key.getValue()) : keyboard.test(key.getValue());
    }
}
