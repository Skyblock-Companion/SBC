// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui.layout;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.sdl.SDLMouse;

import java.util.List;

// everything the layout asks the game for, in one place so a new mc version only needs one file checked. same
// calls owo-lib makes
final class GameLink {
    static final int KEY_UP = InputConstants.KEY_UP, KEY_DOWN = InputConstants.KEY_DOWN, KEY_LEFT = InputConstants.KEY_LEFT, KEY_RIGHT = InputConstants.KEY_RIGHT,
            KEY_PAGE_UP = InputConstants.KEY_PAGEUP, KEY_PAGE_DOWN = InputConstants.KEY_PAGEDOWN;
    // sdl system cursor ids, the same ones the game's CursorTypes uses
    static final int ARROW_CURSOR = SDLMouse.SDL_SYSTEM_CURSOR_DEFAULT, IBEAM_CURSOR = SDLMouse.SDL_SYSTEM_CURSOR_TEXT,
            HAND_CURSOR = SDLMouse.SDL_SYSTEM_CURSOR_POINTER, MOVE_CURSOR = SDLMouse.SDL_SYSTEM_CURSOR_MOVE;

    private GameLink() {}

    static Font font() {
        return Minecraft.getInstance().font;
    }

    static int lineHeight() {
        return font().lineHeight;
    }

    static int textWidth(FormattedCharSequence text) {
        return font().width(text);
    }

    static List<FormattedCharSequence> splitText(Component text, int width) {
        return font().split(text, width);
    }

    static ClientTooltipComponent tooltipLine(FormattedCharSequence line) {
        return ClientTooltipComponent.create(line);
    }

    // same click somewhere else: how a parent hands it to a child in the childs coords
    static MouseButtonEvent clickAt(MouseButtonEvent click, double x, double y) {
        return new MouseButtonEvent(x, y, click.buttonInfo());
    }

    // one real window pixel in gui units, labels sit that far down so text lands on whole pixels
    static float onePixel() {
        return 1f / Minecraft.getInstance().getWindow().getGuiScale();
    }

    // last frame in partial ticks, scroll smoothing runs on it
    static float frameDelta() {
        return Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaTicks();
    }

    static int windowWidth() {
        return Minecraft.getInstance().getWindow().getWidth();
    }

    static int windowHeight() {
        return Minecraft.getInstance().getWindow().getHeight();
    }

    static long windowHandle() {
        return Minecraft.getInstance().getWindow().handle();
    }

    // false with no sound system, like in tests
    static boolean hasSounds() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getSoundManager() != null;
    }

    static void playButtonClick() {
        playUiSound(SoundEvents.UI_BUTTON_CLICK.value());
    }

    static void playInteraction() {
        playUiSound(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("sbcompanion", "ui.interaction")));
    }

    private static void playUiSound(SoundEvent event) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, 1));
    }

    static long createCursor(int shape) {
        return SDLMouse.SDL_CreateSystemCursor(shape);
    }

    static void setCursor(long cursor) {
        SDLMouse.SDL_SetCursor(cursor == 0 ? SDLMouse.SDL_GetDefaultCursor() : cursor);
    }

    static void addToast(Toast toast) {
        Minecraft.getInstance().gui.toastManager().addToast(toast);
    }
}
