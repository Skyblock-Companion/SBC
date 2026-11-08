// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of owo-lib; see THIRD_PARTY_NOTICES.md.
/*
 * Ported from owo-lib (https://github.com/wisp-forest/owo-lib), ui.core.OwoUIAdapter, branch 26.2.
 * owo-lib is by glisco and contributors; its licence reads "Copyright (c) 2021" and names no holder. MIT licence, full text in licenses/owo-lib-LICENSE.txt.
 */
package dev.zmuzy.sbc.ui.layout;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.function.BiFunction;

// entry point of a tree: draws it, feeds it input, sets the cursor. to the game its one widget. doesnt draw
// the tooltip, the screen does
public class UIAdapter<R extends ParentUIComponent> implements GuiEventListener, Renderable, NarratableEntry {

    public final R rootComponent;
    public final CursorAdapter cursorAdapter;

    protected int x, y;
    protected int width, height;

    protected UIAdapter(int x, int y, int width, int height, R rootComponent) {
        this(x, y, width, height, rootComponent, new CursorAdapter());
    }

    UIAdapter(int x, int y, int width, int height, R rootComponent, CursorAdapter cursorAdapter) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        this.cursorAdapter = cursorAdapter;
        this.rootComponent = rootComponent;
    }

    // tree filling the screen, as its only widget with the screens focus
    public static <R extends ParentUIComponent> UIAdapter<R> create(Screen screen, BiFunction<Sizing, Sizing, R> rootComponentMaker) {
        var rootComponent = rootComponentMaker.apply(Sizing.fill(100), Sizing.fill(100));

        var adapter = new UIAdapter<>(0, 0, screen.width, screen.height, rootComponent);
        screen.addRenderableWidget(adapter);
        screen.setFocused(adapter);

        return adapter;
    }

    // fixed size tree, the caller draws it + sends input by hand
    public static <R extends ParentUIComponent> UIAdapter<R> createWithoutScreen(int x, int y, int width, int height, BiFunction<Sizing, Sizing, R> rootComponentMaker) {
        var rootComponent = rootComponentMaker.apply(Sizing.fill(100), Sizing.fill(100));
        return new UIAdapter<>(x, y, width, height, rootComponent);
    }

    // lay out + mount the root. without this it has no size and gets nothing
    public void inflateAndMount() {
        this.rootComponent.inflate(Size.of(this.width, this.height));
        this.rootComponent.mount(null, this.x, this.y);
    }

    public void moveAndResize(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;

        this.inflateAndMount();
    }

    public int x() {
        return this.x;
    }

    public int y() {
        return this.y;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        var ui = graphics instanceof UIGraphics already ? already : UIGraphics.of(graphics);
        this.frame(ui, mouseX, mouseY, a, GameLink.frameDelta(), GameLink.windowWidth(), GameLink.windowHeight());
    }

    // one frame: update, draw, cursor for whats under the mouse
    void frame(UIGraphics ui, int mouseX, int mouseY, float a, float delta, int windowWidth, int windowHeight) {
        this.rootComponent.update(delta, mouseX, mouseY);

        ui.enableScissor(0, 0, windowWidth, windowHeight);
        this.rootComponent.draw(ui, mouseX, mouseY, a, delta);
        ui.disableScissor();

        final var hovered = this.rootComponent.childAt(mouseX, mouseY);
        if (hovered != null) {
            this.cursorAdapter.applyStyle(hovered.cursorStyle());
        }
    }

    public void drawTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
        var ui = graphics instanceof UIGraphics already ? already : UIGraphics.of(graphics);

        this.rootComponent.drawTooltip(ui, mouseX, mouseY, partialTicks, GameLink.frameDelta());
        ui.extractDeferredElements(mouseX, mouseY, partialTicks);
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.rootComponent.isInBoundingBox(mouseX, mouseY);
    }

    @Override
    public void setFocused(boolean focused) {}

    @Override
    public boolean isFocused() {
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean doubled) {
        return this.rootComponent.onMouseDown(click, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        return this.rootComponent.onMouseUp(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return this.rootComponent.onMouseScroll(mouseX, mouseY, verticalAmount);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        return this.rootComponent.onMouseDrag(click, deltaX, deltaY);
    }

    @Override
    public boolean keyPressed(KeyEvent input) {
        return this.rootComponent.onKeyPress(input);
    }

    @Override
    public boolean charTyped(CharacterEvent input) {
        return this.rootComponent.onCharTyped(input);
    }

    @Override
    public NarrationPriority narrationPriority() {
        return NarrationPriority.NONE;
    }

    @Override
    public void updateNarration(NarrationElementOutput builder) {}
}
