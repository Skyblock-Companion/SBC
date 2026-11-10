// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.chat;

import com.mojang.blaze3d.platform.InputConstants;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.mc.mixin.ChatComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.TextAlignment;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.renderer.state.gui.GuiTextRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;

// from skyhanni CopyChat: right click a chat line to copy it. ctrl = only the row under the mouse, alt = with
// colour codes
// skyhanni's shift (word replacements) means nothing here so shift copies like no key
public final class CopyChat {
    private CopyChat() {}

    public static void register() {
        Features.add(new Feature("chat_copy", "Chat", "Copy chat lines", "Right click a line in the open chat to copy it. Ctrl copies only that row, Alt keeps the colour codes.", "SkyHanni: Copy Chat")
                .when(() -> true));
        Safe.afterInit("CopyChat.afterInit", (client, screen, w, h) -> {
            if (!(screen instanceof ChatScreen)) return;
            Safe.allowMouseClick("CopyChat.allowMouseClick", screen, (s, event) -> {
                if (event.button() != InputConstants.MOUSE_BUTTON_RIGHT || !Features.on("chat_copy")) return true;
                copyAt(event);
                return true;
            });
        });
    }

    private static void copyAt(MouseButtonEvent event) {
        Minecraft mc = Minecraft.getInstance();
        GuiMessage.Line line = lineAt(mc, (int) event.x(), (int) event.y());
        if (line == null) return;
        String clipboard, what;
        if (event.hasControlDown()) {
            StringBuilder row = new StringBuilder();
            line.content().accept((i, style, cp) -> {
                row.appendCodePoint(cp);
                return true;
            });
            clipboard = GameState.strip(row.toString());
            what = "line";
        } else if (event.hasAltDown()) {
            clipboard = LegacyText.of(line.parent().content());
            what = "formatted message";
        } else {
            clipboard = GameState.strip(line.parent().content().getString());
            what = "message";
        }
        mc.keyboardHandler.setClipboard(clipboard);
        mc.gui.hud.getChat().addClientSystemMessage(Component.literal("§b[SBC] §7Copied " + what + " to clipboard."));
    }

    // CopyChat.getChatLine
    private static GuiMessage.Line lineAt(Minecraft mc, int mouseX, int mouseY) {
        ChatComponent chat = mc.gui.hud.getChat();
        HoveredTextFinder finder = new HoveredTextFinder(mc.font, mouseX, mouseY);
        chat.captureClickableText(finder, mc.getWindow().getGuiScaledHeight(), mc.gui.hud.getGuiTicks(), ChatComponent.DisplayMode.FOREGROUND);
        if (finder.hovered == null) return null;
        for (GuiMessage.Line l : ((ChatComponentAccessor) chat).sbc$trimmedMessages()) if (l.content() == finder.hovered) return l;
        return null;
    }

    private static final class HoveredTextFinder implements ActiveTextCollector {
        private final Font font;
        private final int mouseX, mouseY;
        private ActiveTextCollector.Parameters parameters = new ActiveTextCollector.Parameters(new Matrix3x2f());
        FormattedCharSequence hovered;

        HoveredTextFinder(Font font, int mouseX, int mouseY) {
            this.font = font;
            this.mouseX = mouseX;
            this.mouseY = mouseY;
        }

        @Override
        public ActiveTextCollector.Parameters defaultParameters() {
            return parameters;
        }

        @Override
        public void defaultParameters(ActiveTextCollector.Parameters p) {
            parameters = p;
        }

        @Override
        public void accept(TextAlignment alignment, int anchorX, int y, ActiveTextCollector.Parameters p, FormattedCharSequence text) {
            int left = alignment.calculateLeft(anchorX, font, text);
            GuiTextRenderState state = new GuiTextRenderState(font, text, p.pose(), left, y, -1, 0, true, true, p.scissor());
            ActiveTextCollector.findElementUnderCursor(state, mouseX, mouseY, style -> hovered = text);
        }

        @Override
        public void acceptScrolling(Component message, int centerX, int left, int right, int top, int bottom, ActiveTextCollector.Parameters p) {
            defaultScrollingHelper(message, centerX, left, right, top, bottom, font.width(message), 9, p);
        }
    }
}
