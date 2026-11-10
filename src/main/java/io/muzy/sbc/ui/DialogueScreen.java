// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

// dialogue while theres answers to pick: click or 1-9, esc closes
public final class DialogueScreen extends Screen {
    private final Ui ui = new Ui();

    public DialogueScreen() {
        super(Component.literal("Dialogue"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (!Dialogue.hasOptions() || Dialogue.dismissed) {
            onClose();
            return;
        }
        ui.begin(g, mouseX, mouseY);
        Dialogue.backdrop(ui, width, height);
        Dialogue.draw(ui, width, height, true);
        ui.flushTooltip();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        for (int i = ui.hits.size() - 1; i >= 0; i--) {
            Ui.Hit hit = ui.hits.get(i);
            if (e.x() < hit.x() || e.y() < hit.y() || e.x() >= hit.x() + hit.w() || e.y() >= hit.y() + hit.h()) continue;
            if (hit.click() != null) {
                hit.click().run();
                return true;
            }
        }
        Dialogue.skip();
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        int key = e.key();
        if (key >= InputConstants.KEY_1 && key <= InputConstants.KEY_9) {
            Dialogue.choose(key - InputConstants.KEY_1);
            return true;
        }
        if (key >= InputConstants.KEY_NUMPAD1 && key <= InputConstants.KEY_NUMPAD9) {
            Dialogue.choose(key - InputConstants.KEY_NUMPAD1);
            return true;
        }
        if (key == InputConstants.KEY_SPACE || key == InputConstants.KEY_RETURN) {
            Dialogue.skip();
            return true;
        }
        if (key == InputConstants.KEY_ESCAPE) {
            Dialogue.dismiss();
            onClose();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(null);
    }
}
