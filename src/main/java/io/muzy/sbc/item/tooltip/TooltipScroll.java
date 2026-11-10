// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.item.tooltip;

import io.muzy.sbc.feature.Features;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

// scrolling for tooltips taller than the screen, wheel moves it and shift + wheel goes sideways
// only kicks in when it doesnt fit so short ones ignore the wheel. resets on another slot or item, not on lore updates
public final class TooltipScroll {
    private static float offsetY, offsetX, targetY, targetX;
    private static ItemStack last = ItemStack.EMPTY;
    private static String lastKey = "";
    private static long shownAt;
    // size of the tooltip under the mouse this frame (set by the render hook)
    public static int lastHeight, lastWidth;
    private TooltipScroll() {}

    public static boolean active() {
        return Features.on("tooltip_scroll");
    }

    // from the tooltip hook: resets when the item changes, returns the y offset to use
    public static int offsetY(ItemStack hovered, int slot) {
        String key = slot + ":" + hovered.getItem() + ":" + hovered.getHoverName().getString();
        if (!key.equals(lastKey)) {
            lastKey = key;
            offsetY = offsetX = targetY = targetX = 0;
        }
        last = hovered;
        shownAt = System.currentTimeMillis();
        offsetY += (targetY - offsetY) * 0.35f;
        offsetX += (targetX - offsetX) * 0.35f;
        if (Math.abs(targetY - offsetY) < 0.5f) offsetY = targetY;
        if (Math.abs(targetX - offsetX) < 0.5f) offsetX = targetX;
        return Math.round(offsetY);
    }

    public static int offsetX() {
        return Math.round(offsetX);
    }

    // mouse wheel on a container screen, false when the wheel went to the tooltip
    public static boolean onScroll(Screen screen, double vertical, boolean shift) {
        if (!active() || !(screen instanceof AbstractContainerScreen<?>) || last.isEmpty() || System.currentTimeMillis() - shownAt > 250) return true;
        int sh = screen.height, sw = screen.width;
        if (shift) {
            if (lastWidth <= sw - 24) return true;
            targetX = (float) Math.max(-(lastWidth - sw + 24), Math.min(0, targetX + vertical * 24));
            return false;
        }
        // too tall: scroll to its end. fits: anywhere on screen, the positioner keeps it on
        int min = tall(sh) ? -(lastHeight - sh + 8) : -sh, max = tall(sh) ? 0 : sh;
        targetY = (float) Math.max(min, Math.min(max, targetY + vertical * 24));
        return false;
    }

    // is the tooltip under the mouse taller than the screen, then the wheel scrolls it
    // same check as the positioner below (vanilla's h + 8 > height)
    public static boolean tall(int screenHeight) {
        return lastHeight > screenHeight;
    }

    // vanilla placing, except for a tooltip taller than the screen
    // vanilla pins its bottom to the screen bottom, cuts off the top and undoes any scroll. ours starts with the
    // top on screen and the wheel moves it
    public static net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner positioner(int dy) {
        return (sw, sh, mouseX, mouseY, w, h) -> {
            // the size the game really draws, so the wheel's range ends where the tooltip does
            lastHeight = h + 8;
            lastWidth = w + 8;
            org.joml.Vector2i v = new org.joml.Vector2i(mouseX, mouseY).add(12, -12);
            if (v.x + w > sw) v.x = Math.max(v.x - 24 - w, 4);
            if (h + 8 > sh) v.y = 4 + dy;
            else {
                if (v.y + h + 3 > sh) v.y = sh - h - 3;
                int base = Math.max(4, v.y), to = Math.max(4, Math.min(sh - h - 3, base + dy));
                // the wheel cannot push it past the screen edge
                if (to != base + dy) snap(to - base);
                v.y = to;
            }
            return v;
        };
    }

    private static void snap(int dy) {
        targetY = offsetY = dy;
    }

    public static void reset() {
        offsetY = offsetX = targetY = targetX = 0;
        last = ItemStack.EMPTY;
        lastKey = "";
    }
}
