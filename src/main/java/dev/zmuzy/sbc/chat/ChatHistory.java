// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.chat;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.mc.mixin.ChatComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

// lines already in the chat box, from skyhanni ChatManager deleteMessages/editMessage
// hypixel sends a blank line before a block that only the next line tells apart, and an item's real name
// after the line mentioning it
public final class ChatHistory {
    private ChatHistory() {}

    private static ChatComponentAccessor chat() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null || mc.gui == null || mc.gui.hud == null ? null : (ChatComponentAccessor) mc.gui.hud.getChat();
    }

    private static boolean recent(GuiMessage m, int withinTicks) {
        Minecraft mc = Minecraft.getInstance();
        return mc.gui.hud.getGuiTicks() - m.addedTime() <= withinTicks;
    }

    // removes up to max of the newest lines from the last ticks that match (no colours). newest first, stops at
    // the first miss
    public static int deleteRecent(int max, int withinTicks, Predicate<String> plain) {
        ChatComponentAccessor chat = chat();
        if (chat == null) return 0;
        List<GuiMessage> all = chat.sbc$allMessages();
        int gone = 0;
        while (gone < max && !all.isEmpty()) {
            GuiMessage m = all.get(0);
            if (!recent(m, withinTicks) || !plain.test(GameState.strip(m.content().getString()))) break;
            all.remove(0);
            gone++;
        }
        if (gone > 0) chat.sbc$refreshTrimmedMessages();
        return gone;
    }

    // removes every line that matches no matter how old
    public static int deleteOlder(Predicate<String> plain) {
        ChatComponentAccessor chat = chat();
        if (chat == null) return 0;
        List<GuiMessage> all = chat.sbc$allMessages();
        int before = all.size();
        all.removeIf(m -> plain.test(GameState.strip(m.content().getString())));
        int gone = before - all.size();
        if (gone > 0) chat.sbc$refreshTrimmedMessages();
        return gone;
    }

    // edits the newest matching line of the last ticks
    public static boolean editRecent(int withinTicks, Predicate<String> plain, Function<Component, Component> edit) {
        ChatComponentAccessor chat = chat();
        if (chat == null) return false;
        List<GuiMessage> all = chat.sbc$allMessages();
        for (int i = 0; i < all.size(); i++) {
            GuiMessage m = all.get(i);
            if (!recent(m, withinTicks)) return false;
            if (!plain.test(GameState.strip(m.content().getString()))) continue;
            all.set(i, new GuiMessage(m.addedTime(), edit.apply(m.content()), m.signature(), m.source(), m.tag()));
            chat.sbc$refreshTrimmedMessages();
            return true;
        }
        return false;
    }
}
