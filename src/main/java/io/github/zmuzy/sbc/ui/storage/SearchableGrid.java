// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.ui.storage;

import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractContainerWidget;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// port of skyblocker SearchableGridWidget: search box over a scrolling grid that fills from a filter
abstract class SearchableGrid extends AbstractContainerWidget {
    private static final int TEXT_FIELD_HEIGHT = 20;

    // classic draws the search row + scrollbar itself through the 2 hooks below
    private final boolean modern = true;
    private final List<AbstractWidget> filteredWidgets = new ArrayList<>();
    private final LinearLayout layoutWidget = LinearLayout.vertical();
    private final EditBox searchField;
    private final WidgetsContainer widgetsContainer;
    private final int expectedWidgetWidth, maxPerRow;
    private final boolean packed, spaceElementsOut;

    SearchableGrid(int x, int y, int width, int height, Component message, int expectedWidgetWidth, int maxPerRow, boolean packed, boolean spaceElementsOut) {
        super(x, y, width, height, message, AbstractScrollArea.defaultSettings(8));
        Font font = Minecraft.getInstance().font;
        if (modern) {
            searchField = new SearchBox(font, searchTextWidth(width));
            searchField.setBordered(false);
            searchField.setTextColor(Modern.INK);
            searchField.setHint(Component.literal("Search by name or lore").withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GRAY));
        } else {
            searchField = new EditBox(font, width, TEXT_FIELD_HEIGHT, Component.translatable("gui.recipebook.search_hint"));
            searchField.setHint(Component.translatable("gui.recipebook.search_hint").withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GRAY));
        }
        searchField.setResponder(this::filterInternal);
        this.expectedWidgetWidth = expectedWidgetWidth;
        this.maxPerRow = maxPerRow;
        this.packed = packed;
        this.spaceElementsOut = spaceElementsOut;

        widgetsContainer = new WidgetsContainer();
        if (modern) {
            // only the text line is the box, the row around it comes from drawSearchRow and takes clicks too
            int rest = StorageLook.SEARCH_BLOCK - StorageLook.SEARCH_TEXT_Y - searchField.getHeight();
            layoutWidget.addChild(searchField, settings -> settings.padding(StorageLook.SEARCH_TEXT_X, StorageLook.SEARCH_TEXT_Y, 0, rest));
        } else layoutWidget.addChild(searchField);
        layoutWidget.addChild(widgetsContainer);
        layoutWidget.arrangeElements();
        layoutWidget.setPosition(x, y);

        setWidth(getWidth()); // works out the width of a packed grid
    }

    private int searchBlock() {
        return modern ? StorageLook.SEARCH_BLOCK : TEXT_FIELD_HEIGHT;
    }

    private static int searchTextWidth(int width) {
        return Math.max(20, width - StorageLook.SEARCH_TEXT_X - StorageLook.resultsRoom(width) - 4);
    }

    @Override
    public void setX(int x) {
        super.setX(x);
        layoutWidget.setX(x);
    }

    @Override
    public void setY(int y) {
        super.setY(y);
        layoutWidget.setY(y);
    }

    @Override
    public void setWidth(int width) {
        if (packed) {
            int perRow = Math.min((width - AbstractScrollArea.SCROLLBAR_WIDTH) / expectedWidgetWidth, maxPerRow);
            int newWidth = perRow * expectedWidgetWidth + AbstractScrollArea.SCROLLBAR_WIDTH;
            setX(getX() + (width - newWidth) / 2);
            width = newWidth;
        }
        super.setWidth(width);
        searchField.setWidth(modern ? searchTextWidth(width) : width);
        widgetsContainer.setWidth(width);
        layoutWidget.arrangeElements();
    }

    @Override
    public void setHeight(int height) {
        super.setHeight(height);
        widgetsContainer.setHeight(height - searchBlock());
        layoutWidget.arrangeElements();
    }

    void setSearch(String search) {
        searchField.setValue(search);
    }

    void refreshSearch() {
        searchField.setValue(searchField.getValue());
    }

    void setGridScroll(double amount) {
        widgetsContainer.setScrollAmount(amount);
    }

    double getGridScroll() {
        return widgetsContainer.scrollAmount();
    }

    // grid rect minus the search bar
    ScreenRectangle getGridRectangle() {
        return widgetsContainer.getRectangle();
    }

    private void filterInternal(String input) {
        Collection<? extends AbstractWidget> widgets = filterWidgets(input);
        filteredWidgets.clear();
        filteredWidgets.addAll(widgets);
        widgetsContainer.recreateGrid();
    }

    protected abstract Collection<? extends AbstractWidget> filterWidgets(String input);

    // classic: whats under the search text
    protected void drawSearchRow(GuiGraphicsExtractor context, int x, int y, int w, boolean focused, boolean hovered) {
    }

    // classic: the grid scrollbar
    protected void drawScrollbar(GuiGraphicsExtractor context, int x, int y, int h, int thumbY, int thumbH, boolean hovered) {
    }

    private boolean overSearchRow(double mouseX, double mouseY) {
        return mouseX >= getX() && mouseX < getRight() && mouseY >= getY() && mouseY < getY() + StorageLook.SEARCH_ROW;
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return List.of(searchField, widgetsContainer);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        if (modern) drawSearchRow(context, getX() + 1, getY(), getWidth() - 1, searchField.isFocused(), overSearchRow(mouseX, mouseY));
        searchField.extractRenderState(context, mouseX, mouseY, deltaTicks);
        widgetsContainer.extractRenderState(context, mouseX, mouseY, deltaTicks);
    }

    // search text on the row classic draws, a click anywhere on that row counts
    private final class SearchBox extends EditBox {
        SearchBox(Font font, int width) {
            super(font, width, 9, Component.translatable("gui.recipebook.search_hint"));
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            return isActive() && overSearchRow(mouseX, mouseY);
        }
    }

    private class WidgetsContainer extends AbstractContainerWidget {
        protected GridLayout grid = new GridLayout();

        private WidgetsContainer() {
            super(0, 0, SearchableGrid.this.getWidth(), SearchableGrid.this.getHeight() - searchBlock(), Component.literal("Grid"), AbstractScrollArea.defaultSettings(8));
        }

        @Override
        public void setX(int x) {
            super.setX(x);
            grid.setX(x);
        }

        @Override
        public void setY(int y) {
            super.setY(y);
            grid.setY(y);
        }

        @Override
        public void setWidth(int width) {
            super.setWidth(width);
            recreateGrid();
        }

        @Override
        public void setHeight(int height) {
            super.setHeight(height);
            recreateGrid();
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return filteredWidgets;
        }

        @Override
        protected int contentHeight() {
            return grid.getHeight();
        }

        @Override
        protected double scrollRate() {
            return SearchableGrid.this.scrollRate();
        }

        @Override
        public void setScrollAmount(double scrollY) {
            super.setScrollAmount(scrollY);
            grid.setY(getY() - (int) scrollAmount());
        }

        private boolean isVisible(AbstractWidget widget) {
            return widget.getBottom() >= getY() && widget.getY() < getBottom();
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            context.enableScissor(getX(), getY(), getRight(), getBottom());
            for (AbstractWidget widget : filteredWidgets) {
                if (isVisible(widget)) widget.extractRenderState(context, mouseX, mouseY, deltaTicks);
            }
            extractScrollbar(context, mouseX, mouseY);
            context.disableScissor();
        }

        @Override
        protected void extractScrollbar(GuiGraphicsExtractor context, int mouseX, int mouseY) {
            if (!modern) {
                super.extractScrollbar(context, mouseX, mouseY);
                return;
            }
            if (!scrollable()) return;
            boolean over = isOverScrollbar(mouseX, mouseY);
            drawScrollbar(context, scrollBarX(), getY(), getHeight(), scrollBarY(), scrollerHeight(), over);
            if (over) context.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
        }

        protected void recreateGrid() {
            GridLayout newGrid = new GridLayout();
            int columns = (getWidth() - AbstractScrollArea.SCROLLBAR_WIDTH) / expectedWidgetWidth;
            GridLayout.RowHelper adder = newGrid.createRowHelper(columns);
            filteredWidgets.forEach(adder::addChild);
            if (spaceElementsOut) {
                newGrid.columnSpacing(((getWidth() - AbstractScrollArea.SCROLLBAR_WIDTH) - columns * expectedWidgetWidth) / columns);
            }
            newGrid.arrangeElements();
            newGrid.setPosition(grid.getX(), grid.getY());
            grid = newGrid;

            refreshScrollAmount();
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return getChildAt(mouseX, mouseY).filter(element -> element.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)).isPresent();
    }

    @Override
    protected int contentHeight() {
        return 0;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput builder) {
    }
}
