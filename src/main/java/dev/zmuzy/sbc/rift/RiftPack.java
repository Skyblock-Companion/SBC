// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.rift;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Safe;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;

// ties the rift area files to the shared chat, menu + slot hooks in Rift so each stays its own file
public final class RiftPack {
    private RiftPack() {}

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!"THE_RIFT".equals(GameState.island) && !dev.zmuzy.sbc.SbcMod.DEV) return ChatBus.PASS;
        String formatted = dev.zmuzy.sbc.core.LegacyText.of(line.component()), clean = line.plain();
        RiftColosseum.onChat(formatted);
        RiftMountaintop.onChat(formatted);
        RiftMotes.onChat(formatted, clean);
        KloonHacking.onChat(clean);
        return ChatBus.PASS;
    }

    // chest menu opened or changed in the rift
    static void onMenu(String title, ChestMenu menu, int n) {
        KloonHacking.onMenu(title, menu, n);
        RiftMarks.onMenu(menu, n);
        RiftMountaintop.onMenu(title, menu, n);
        RiftMotes.onMenu(title, menu);
    }

    static void onMenuClosed() {
        KloonHacking.onMenuClosed();
        RiftMarks.onMenuClosed();
    }

    static void slot(GuiGraphicsExtractor g, Slot slot) {
        KloonHacking.slot(g, slot);
        RiftMarks.slot(g, slot);
    }

    public static void register() {
        RiftColosseum.register();
        RiftDreadfarm.register();
        RiftLivingCave.register();
        RiftMirrorverse.register();
        RiftMountaintop.register();
        RiftMarks.register();
        KloonHacking.register();
        RiftMotes.register();
        ChatBus.register(ChatBus.OBSERVE + 31, "rift_pack", null, RiftPack::onChat);
        Safe.endTick("RiftColosseum.readPhase", RiftColosseum::readPhase);
    }
}
