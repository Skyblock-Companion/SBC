// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.mc.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

// whats already in the chat box, the way skyhanni ChatManager gets at it, to pull a line back out or edit it
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("allMessages")
    List<GuiMessage> sbc$allMessages();

    @Accessor("trimmedMessages")
    List<GuiMessage.Line> sbc$trimmedMessages();

    @Invoker("refreshTrimmedMessages")
    void sbc$refreshTrimmedMessages();
}
