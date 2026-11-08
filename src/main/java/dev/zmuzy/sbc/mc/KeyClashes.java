// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.mc;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.feature.Features;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

// one of our keys on the same key as a vanilla one that opens a screen. read from the games bytecode (26.2):
// 1. KeyboardHandler.keyPress calls KeyMapping.click only with no screen open, and KeyMapping.MAP has a list
// per key so both mappings get the click
// 2. Minecraft.tick: fabric START_CLIENT_TICK at HEAD, then handleKeybinds with no screen open ->
// Gui.handleKeybinds -> keyAdvancements.consumeClick() opens advancements
// 3. fabric END_CLIENT_TICK at RETURN, our tick sees a screen there and skips the lock
// so START_CLIENT_TICK is the only spot where both still have their click. in skyblock with slot locking on,
// advancements loses its click and ours locks the slot
// outside skyblock nothing is touched, and no key setting ever gets changed
// only clash like this in the keys we ship. F1 (wiki), F6 (price), G (sack) and U (radial) are only read in a
// container screens key event
// where vanilla mappings get no click at all, so F1 still hides the hud in the world and looks stuff up in a menu
public final class KeyClashes {
    // more clicks than any tick has, so a mixin on consumeClick cant hang the game here
    private static final int MOST_CLICKS = 64;

    private KeyClashes() {}

    public static void settle(Minecraft mc, KeyMapping lock) {
        if (lock != null && lockTakes(GameState.inSkyBlock, Features.on("slotlocks"), lock.same(mc.options.keyAdvancements))) drain(mc.options.keyAdvancements);
    }

    static boolean lockTakes(boolean inSkyBlock, boolean locksOn, boolean sameKey) {
        return inSkyBlock && locksOn && sameKey;
    }

    static void drain(KeyMapping key) {
        for (int i = 0; i < MOST_CLICKS && key.consumeClick(); i++) {
            // only the clicks go, held state stays
        }
    }
}
