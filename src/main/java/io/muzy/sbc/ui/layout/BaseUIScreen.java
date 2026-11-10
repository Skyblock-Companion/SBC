// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.base.BaseOwoScreen, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 *
 * Changed: Esc. The library turns off the game's own early Esc handling with a mixin on Screen; here shouldCloseOnEsc answers
 * false while the game's keyPressed runs, which has the same effect on that one check without a mixin.
 */
package io.muzy.sbc.ui.layout;

import io.muzy.sbc.core.Safe;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// minimal screen around a component tree, override createAdapter() and build()
public abstract class BaseUIScreen<R extends ParentUIComponent> extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger("sbcompanion");

    // there after init()
    protected UIAdapter<R> uiAdapter = null;

    // tree build failed: screen closes instead of drawing half a tree
    protected boolean invalid = false;

    private boolean insideGameKeyPressed = false;

    protected BaseUIScreen(Component title) {
        super(title);
    }

    protected abstract UIAdapter<R> createAdapter();

    protected abstract void build(R rootComponent);

    @Override
    protected void init() {
        if (this.invalid) return;

        // Check whether this screen was already initialized
        if (this.uiAdapter != null) {
            // If it was, only resize the adapter instead of recreating it - this preserves UI state
            this.uiAdapter.moveAndResize(0, 0, this.width, this.height);
            // Re-add it as a child to circumvent vanilla clearing them
            this.addRenderableWidget(this.uiAdapter);
        } else {
            try {
                this.uiAdapter = this.createAdapter();
                this.build(this.uiAdapter.rootComponent);

                this.uiAdapter.inflateAndMount();
            } catch (Exception error) {
                LOGGER.warn("Could not initialize screen", error);
                UIErrorToast.report(error);
                this.invalid = true;
            }
        }

        Safe.afterExtract("BaseUIScreen.afterExtract", this, (screen, drawContext, mouseX, mouseY, tickDelta) -> {
            this.drawComponentTooltip(drawContext, mouseX, mouseY, tickDelta);
        });
    }

    protected void drawComponentTooltip(GuiGraphicsExtractor drawContext, int mouseX, int mouseY, float tickDelta) {
        if (this.uiAdapter != null) this.uiAdapter.drawTooltip(drawContext, mouseX, mouseY, tickDelta);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {}

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        if (!this.invalid) {
            super.extractRenderState(graphics, mouseX, mouseY, a);
        } else {
            this.onClose();
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !this.insideGameKeyPressed && super.shouldCloseOnEsc();
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        if (this.uiAdapter == null) return false;

        // the tree hears keys first (esc too), only what nothing wanted closes the screen
        this.insideGameKeyPressed = true;
        try {
            if (super.keyPressed(input)) {
                return true;
            }
        } finally {
            this.insideGameKeyPressed = false;
        }

        if (input.isEscape() && this.shouldCloseOnEsc()) {
            this.onClose();
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (this.uiAdapter == null) return false;

        return this.uiAdapter.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public GuiEventListener getFocused() {
        return this.uiAdapter;
    }

    @Override
    public void removed() {
        if (this.uiAdapter != null) {
            this.uiAdapter.cursorAdapter.applyStyle(CursorStyle.NONE);
        }
    }
}
