// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.dungeon;

import com.mojang.blaze3d.platform.InputConstants;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.feature.Features;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.ContainerInput;

// dungeon keys in the game controls: one that lets a click through in a terminal (ours) + skyhannis 4 spirit
// leap keys (SpiritLeapKeybindConfig, DungeonSpiritLeapOverlay)
public final class DungeonKeys {
    private static KeyMapping override;
    private static final KeyMapping[] LEAP = new KeyMapping[4];

    private DungeonKeys() {}

    public static void registerKeys(KeyMapping.Category cat) {
        override = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.terminal_override", InputConstants.Type.KEYBOARD, InputConstants.KEY_LALT, cat));
        int[] keys = {InputConstants.KEY_1, InputConstants.KEY_2, InputConstants.KEY_3, InputConstants.KEY_4};
        for (int i = 0; i < LEAP.length; i++)
            LEAP[i] = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.sbcompanion.leap_" + (i + 1), InputConstants.Type.KEYBOARD, keys[i], cat));
    }

    public static boolean overrideDown() {
        return io.muzy.sbc.mc.KeyHeld.held(override);
    }

    static String overrideName() {
        return override == null ? "the key" : override.getTranslatedKeyMessage().getString();
    }

    // leap key name for the mark on the head
    static String leapName(int index) {
        return index >= 0 && index < LEAP.length && LEAP[index] != null ? LEAP[index].getTranslatedKeyMessage().getString() : "";
    }

    // false = the key did its job in the spirit leap menu
    public static boolean key(Screen s, KeyEvent event) {
        if (!(s instanceof AbstractContainerScreen<?> cs) || !DungeonRun.inDungeon() || !Features.on("dungeon_leap") || !DungeonMarks.opt("dungeon_leap", "keys")) return true;
        if (!LeapMenu.isTitle(GameState.strip(s.getTitle().getString()).trim())) return true;
        for (int i = 0; i < LEAP.length; i++) {
            if (LEAP[i] == null || !LEAP[i].matches(event)) continue;
            int slot = LeapMenu.slotOfTarget(cs, i);
            Minecraft mc = Minecraft.getInstance();
            if (slot < 0 || mc.gameMode == null || mc.player == null) return false;
            mc.gameMode.handleContainerInput(cs.getMenu().containerId, slot, 0, ContainerInput.PICKUP, mc.player);
            return false;
        }
        return true;
    }
}
