// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.util.UIErrorToast, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 *
 * Changed: the title names this mod, as the screens never name another one.
 */
package io.github.zmuzy.sbc.ui.layout;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

// corner message when building a screen failed
public class UIErrorToast implements Toast {

    private final List<FormattedCharSequence> errorMessage;
    private final Font textRenderer;
    private final int width;

    public UIErrorToast(Throwable error) {
        this.textRenderer = GameLink.font();

        var stackTop = error.getStackTrace()[0];
        var errorLocation = stackTop.getClassName().split("\\.");

        final var texts = new ArrayList<Component>();
        texts.add(Component.literal("SkyBlock Companion UI error").withStyle(ChatFormatting.RED));

        texts.add(Component.literal(" "));
        texts.add(Component.literal("Type: ").withStyle(ChatFormatting.RED)
                .append(Component.literal(error.getClass().getSimpleName()).withStyle(ChatFormatting.GRAY)));
        texts.add(Component.literal("Thrown by: ").withStyle(ChatFormatting.RED)
                .append(Component.literal(errorLocation[errorLocation.length - 1] + ":" + stackTop.getLineNumber()).withStyle(ChatFormatting.GRAY)));
        texts.add(Component.literal(" "));

        texts.add(Component.literal(String.valueOf(error.getMessage())));

        texts.add(Component.literal(" "));
        texts.add(Component.literal("Check your log for details").withStyle(ChatFormatting.GRAY));

        int widest = 0;
        for (var text : texts) widest = Math.max(widest, this.textRenderer.width(text));
        this.width = Math.min(240, widest + 8);

        this.errorMessage = new ArrayList<>();
        for (var text : texts) this.errorMessage.addAll(this.textRenderer.split(text, this.width() - 8));
    }

    public static void report(Throwable error) {
        GameLink.addToast(new UIErrorToast(error));
    }

    private Visibility visibility = Visibility.HIDE;

    @Override
    public void update(ToastManager manager, long time) {
        this.visibility = time > 10000 ? Visibility.HIDE : Visibility.SHOW;
    }

    @Override
    public Visibility getWantedVisibility() {
        return this.visibility;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        var ui = UIGraphics.of(graphics);

        ui.fill(0, 0, this.width(), this.height(), 0x77000000);
        ui.drawRectOutline(0, 0, this.width(), this.height(), 0xA7FF0000);

        int xOffset = this.width() / 2 - this.textRenderer.width(this.errorMessage.get(0)) / 2;
        ui.text(this.textRenderer, this.errorMessage.get(0), 4 + xOffset, 4, 0xFFFFFFFF);

        for (int i = 1; i < this.errorMessage.size(); i++) {
            ui.text(this.textRenderer, this.errorMessage.get(i), 4, 4 + i * 11, 0xFFFFFFFF, false);
        }
    }

    @Override
    public int height() {
        return 6 + this.errorMessage.size() * 11;
    }

    @Override
    public int width() {
        return this.width;
    }

    @Override
    public Object getToken() {
        return Type.VERY_TYPE;
    }

    enum Type {
        VERY_TYPE
    }
}
