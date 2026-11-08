// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui.layout;

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
import org.lwjgl.glfw.GLFW;

import java.util.List;

// everything the layout asks the game for, in one place so a new mc version only needs one file checked. same
// calls owo-lib makes
final class GameLink {
    static final int KEY_UP = GLFW.GLFW_KEY_UP, KEY_DOWN = GLFW.GLFW_KEY_DOWN, KEY_LEFT = GLFW.GLFW_KEY_LEFT, KEY_RIGHT = GLFW.GLFW_KEY_RIGHT,
            KEY_PAGE_UP = GLFW.GLFW_KEY_PAGE_UP, KEY_PAGE_DOWN = GLFW.GLFW_KEY_PAGE_DOWN;
    static final int ARROW_CURSOR = GLFW.GLFW_ARROW_CURSOR, IBEAM_CURSOR = GLFW.GLFW_IBEAM_CURSOR, HAND_CURSOR = GLFW.GLFW_HAND_CURSOR,
            MOVE_CURSOR = GLFW.GLFW_RESIZE_ALL_CURSOR;

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

    static long createCursor(int glfwShape) {
        return GLFW.glfwCreateStandardCursor(glfwShape);
    }

    static void setCursor(long cursor) {
        GLFW.glfwSetCursor(windowHandle(), cursor);
    }

    static void addToast(Toast toast) {
        Minecraft.getInstance().gui.toastManager().addToast(toast);
    }
}
