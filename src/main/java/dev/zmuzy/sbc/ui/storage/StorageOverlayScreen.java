// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui.storage;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.item.SlotInfo;
import dev.zmuzy.sbc.item.StorageCache;
import dev.zmuzy.sbc.ui.Bling;
import dev.zmuzy.sbc.ui.Ui;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2d;
import org.joml.Vector2dc;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

// port of skyblocker StorageOverlayScreen in our look. replaces hypixels storage, ender chest and backpack
// menus: every page on one screen with a search box, inventory under it
public class StorageOverlayScreen extends AbstractContainerScreen<StorageOverlayMenu> {
    private static final Pattern CHANGING_BACKPACK_PATTERN = Pattern.compile("(Placing backpack)|(Removed backpack)");
    private static final int HEADER_H = 17, SLOT_SIZE = 18, EDGE_PADDING = 7;
    private static final int NOT_MATCHED_COLOR = ARGB.black(0.6f);
    private static final int CARD = 0xC8101010, MISSING_SLOT = 0xB0000000;
    private static final long MIN_COMMAND_DELAY = 200;

    protected static int openStorage;
    private static double savedScroll = 0;
    private static String savedSearch = "";
    private static int savedIndex = -1;
    private static boolean disableOnNextLoad = false;
    private static Vector2dc previousMousePosition;
    private static long lastCommand;
    private static String queuedCommand;

    private final Ui ui = new Ui();
    private BackpackGridWidget grid;
    private int oldHeight;
    private final StorageOverlayMenu handler;
    private final Component name;
    private final ChestMenu defaultHandler;
    private boolean saveMousePosition, pagesListed;

    public static Vector2dc getPreviousMousePosition() {
        Vector2dc screenPosition = previousMousePosition;
        previousMousePosition = null;
        return screenPosition;
    }

    public static void setup() {
        Safe.allowGame("StorageOverlayScreen.allowGame", (message, overlay) -> {
            if (GameState.inSkyBlock && !overlay && CHANGING_BACKPACK_PATTERN.matcher(message.getString()).find()) disableOnNextLoad = true;
            return true;
        });
        Safe.modifyCommand("StorageOverlayScreen.modifyCommand", command -> {
            if (!Features.on("storage_overlay") || savedIndex < 0 || Minecraft.getInstance().gui.screen() instanceof StorageOverlayScreen || !command.equals("storage")) return command;
            String c = getCommandForIndex(savedIndex);
            savedIndex = -1;
            return c.substring(1);
        });
    }

    private static boolean bool(String key, boolean def) {
        var f = Features.get("storage_overlay");
        var o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    private static int integer(String key, int def) {
        var f = Features.get("storage_overlay");
        var o = f == null ? null : f.option(key);
        return o == null ? def : o.integer();
    }

    // server commands max every 200ms like it wants, the last waiting one goes out next tick
    public static void sendAfterCooldown(String command) {
        if (System.currentTimeMillis() - lastCommand > MIN_COMMAND_DELAY) send(command);
        else queuedCommand = command;
    }

    private static void send(String command) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        lastCommand = System.currentTimeMillis();
        player.connection.sendCommand(command.startsWith("/") ? command.substring(1) : command);
    }

    // storage key = typing /storage, which opens this
    public static void open() {
        sendAfterCooldown("/storage");
    }

    public static void tick(Minecraft mc) {
        if (queuedCommand != null && System.currentTimeMillis() - lastCommand > MIN_COMMAND_DELAY) {
            String c = queuedCommand;
            queuedCommand = null;
            send(c);
        }
    }

    public StorageOverlayScreen(StorageOverlayMenu handler, ChestMenu defaultHandler, Component name, Inventory inventory, int height) {
        super(handler, inventory, name, 176, height);
        this.titleLabelY = -1000; // title stays, the page cache reads it
        this.inventoryLabelY += 2;
        this.handler = handler;
        this.defaultHandler = defaultHandler;
        this.name = name;
        this.oldHeight = height - 87;
    }

    public static boolean enabled(String title) {
        openStorage = StorageCache.indexFromTitle(title);
        boolean enabled = Features.on("storage_overlay") && (title.equals("storage") || openStorage != -1) && !disableOnNextLoad;
        disableOnNextLoad = false;
        return enabled;
    }

    protected void switchOpenStorage(int index) {
        if (grid == null) return;
        savedScroll = grid.getGridScroll();
        sendAfterCooldown(getCommandForIndex(index));
        saveMousePosition = bool("doNotResetCursor", true);
    }

    public void refreshSearch() {
        if (grid != null) grid.refreshSearch();
    }

    private static String getCommandForIndex(int index) {
        return index <= 8 ? "/echest " + (index + 1) : "/backpack " + (index - 8);
    }

    protected static String getStorageName(int index) {
        return index <= 8 ? "Ender Chest " + (index + 1) : "Backpack " + (index - 8);
    }

    // hides the overlay for now and shows the plain menu
    private void hide() {
        if (minecraft.player == null) return;
        minecraft.player.containerMenu = defaultHandler;
        minecraft.gui.setScreen(new ContainerScreen(defaultHandler, minecraft.player.getInventory(), name));
    }

    private void home() {
        sendAfterCooldown("/storage");
        disableOnNextLoad = true;
        savedIndex = -1;
    }

    private int getMinLeftPos() {
        return (this.width - this.getMaxWidth()) / 2;
    }

    private int getLeftPos() {
        return (this.width - this.getWidth()) / 2;
    }

    private int getMaxWidth() {
        return (this.width / 8) * 7;
    }

    private int getWidth() {
        return this.grid != null ? this.grid.getWidth() + 16 : getMaxWidth();
    }

    private int getHeight() {
        return this.height - (this.height / 5) - 87;
    }

    public Rect2i getMainExclusionZone() {
        return new Rect2i(getLeftPos(), 0, getWidth(), height);
    }

    // classics title band sits above topPos so its search row starts right there
    private static int gridOffset() {
        return 0;
    }

    private StorageLook.Layout layout() {
        return StorageLook.layout(this.width, this.height, getWidth(), getHeight());
    }

    @Override
    protected void repositionElements() {
        super.repositionElements();
        // new size, shift the inventory slots + label by the difference
        this.topPos = (this.height / 10);
        if (grid != null) grid.setY(this.topPos + gridOffset());
        handler.updateBackpackSlots(height);
        int change = getHeight() - oldHeight;
        oldHeight = getHeight();
        handler.shiftInventory(change);
        this.inventoryLabelY += change;
    }

    // page slots scrolled under the search box arent clickable. the slot hover mixin asks this
    public boolean slotReachable(Slot slot, double xm, double ym) {
        return grid == null || slot.container instanceof Inventory || grid.getGridRectangle().containsPoint((int) xm, (int) ym);
    }

    @Override
    protected void init() {
        super.init();
        int storagesPerRow = integer("storagesPerRow", 3);
        storagesPerRow = storagesPerRow > 0 ? storagesPerRow : Integer.MAX_VALUE;
        int internalCols = integer("backpackWidth", 9);
        int gridH = StorageLook.gridHeight(getHeight());
        grid = new BackpackGridWidget(getMinLeftPos() + 8, this.topPos + gridOffset(), getMaxWidth() - 16, gridH, storagesPerRow, internalCols, true);
        grid.setSearch(savedSearch);
        grid.setGridScroll(savedScroll);
        grid.scrollToOpenBackpack();
        this.addRenderableWidget(grid);

        StorageLook.Layout look = layout();
        boolean inCard = look.side();
        int buttonW = inCard ? look.shortcutsW() - 8 : 84;
        StorageLook.Column column = StorageLook.column(ui, buttonW);
        Runnable[] actions = {() -> sendAfterCooldown("/farmingtoolkit"), () -> sendAfterCooldown("/huntingtoolkit"), this::home, this::hide};
        List<FlatButton> buttons = new ArrayList<>();
        for (int i = 0; i < actions.length; i++) buttons.add(new FlatButton(buttonW, 16, StorageLook.SHORTCUT_LIST.get(i), column, actions[i]));
        if (inCard) {
            for (int i = 0; i < buttons.size(); i++) {
                buttons.get(i).setPosition(look.shortcutsX() + 4, StorageLook.shortcutY(look, i));
                addRenderableWidget(buttons.get(i));
            }
            return;
        }
        // extra controls, out of the way
        LinearLayout extraButtons = new LinearLayout(width - 94, height - 84, LinearLayout.Orientation.VERTICAL);
        extraButtons.spacing(5);
        buttons.forEach(extraButtons::addChild);
        extraButtons.arrangeElements();
        extraButtons.visitWidgets(this::addRenderableWidget);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // on the storage menu itself: once its items are here we can show the pages it lists without another command
        if (!pagesListed && openStorage == -1 && !handler.getContainer().getItem(9).isEmpty()) {
            pagesListed = true;
            if (StorageCache.initializeStorage(handler)) rebuildWidgets();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ui.begin(graphics, mouseX, mouseY);
        super.extractRenderState(graphics, mouseX, mouseY, a);
        this.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public void onClose() {
        savedScroll = 0;
        if (bool("rememberSearch", false) && grid != null) savedScroll = grid.getGridScroll();
        else savedSearch = "";
        if (bool("rememberOpened", false) && grid != null && grid.openBackpack != null) {
            savedIndex = grid.openBackpack.index;
            savedScroll = grid.getGridScroll();
        } else savedIndex = -1;
        super.onClose();
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        if (slot.container instanceof Inventory || grid == null) {
            super.extractSlot(graphics, slot, mouseX, mouseY);
        } else {
            // keep the page slots inside the grid
            ScreenRectangle rectangle = grid.getGridRectangle();
            rectangle = new ScreenRectangle(rectangle.position().x() - leftPos, rectangle.position().y() - topPos, rectangle.width(), rectangle.height());
            graphics.enableScissor(rectangle.left(), rectangle.top(), rectangle.right(), rectangle.bottom());
            super.extractSlot(graphics, slot, mouseX, mouseY);
            if (grid.openBackpack != null) {
                if (!grid.openBackpack.matchedSlots.get(slot.getContainerSlot())) StorageLook.dim(ui, slot.x, slot.y);
                else if (grid.searching && slot.hasItem()) StorageLook.found(ui, slot.x, slot.y);
            }
            graphics.disableScissor();
        }
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int xo, int yo) {
        // a card isnt outside, clicking one must not drop the cursor item
        StorageLook.Layout look = layout();
        if (look.inFrame(mouseX, mouseY) || look.inSide(mouseX, mouseY)) return false;
        return super.hasClickedOutside(mouseX, mouseY, xo, yo) && (mouseX < getLeftPos() || mouseX > getLeftPos() + width || mouseY < this.topPos || mouseY > this.topPos + getHeight());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        // scroll works everywhere except over an item
        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) return super.mouseScrolled(x, y, scrollX, scrollY);
        return this.getChildAt(x, y).filter(child -> child.mouseScrolled(x, y, scrollX, scrollY)).isPresent();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.minecraft.options.keyInventory.matches(event) && typing()) return true;
        return super.keyPressed(event);
    }

    // search box has the keyboard
    public boolean typing() {
        var focusPath = this.getCurrentFocusPath();
        return focusPath != null && focusPath.leafComponent() instanceof EditBox;
    }

    @Override
    public void removed() {
        super.removed();
        if (saveMousePosition) previousMousePosition = new Vector2d(minecraft.mouseHandler.xpos(), minecraft.mouseHandler.ypos());
    }

    private void slotBox(int itemX, int itemY) {
        StorageLook.slot(ui, itemX, itemY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        ui.begin(graphics, mouseX, mouseY);
        StorageLook.Layout look = layout();
        boolean open = grid != null && grid.openBackpack != null;
        StorageLook.Totals totals = grid != null ? grid.totals : new StorageLook.Totals();
        StorageLook.frame(ui, look, open ? GameState.strip(name.getString()) : null, totals.pages() > 0);
        int filled = 0, total = 0;
        for (int i = handler.containerRows() * 9; i < handler.slots.size(); i++) {
            total++;
            if (handler.slots.get(i).hasItem()) filled++;
        }
        StorageLook.inventory(ui, look, this.playerInventoryTitle.getString(), filled, total);
        if (look.side()) {
            StorageLook.overview(ui, look, totals);
            StorageLook.shortcuts(ui, look);
        }
        for (int i = handler.containerRows() * 9; i < handler.slots.size(); i++) {
            Slot slot = handler.slots.get(i);
            slotBox(this.leftPos + slot.x, this.topPos + slot.y);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        // classic says it on the inventory card band
    }

    // plain button in our look
    private final class FlatButton extends AbstractWidget {
        private final Runnable action;
        private final String label, hint;
        private final ItemStack icon;
        private final int accent;
        private final boolean alone;

        // the one thing to do when theres nothing else on screen
        FlatButton(int w, int h, String label, Runnable action) {
            this(w, h, label, null, ItemStack.EMPTY, StorageLook.FRAME, true, action);
        }

        FlatButton(int w, int h, StorageLook.Shortcut shortcut, StorageLook.Column column, Runnable action) {
            this(w, h, column.label(shortcut), column.hint(shortcut), column.icon(shortcut), shortcut.accent(), false, action);
        }

        private FlatButton(int w, int h, String label, String hint, ItemStack icon, int accent, boolean alone, Runnable action) {
            super(0, 0, w, h, Component.literal(label));
            this.label = label;
            this.hint = hint;
            this.icon = icon;
            this.accent = accent;
            this.alone = alone;
            this.action = action;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            if (alone) StorageLook.loneButton(ui, getX(), getY(), getWidth(), getHeight(), label, accent, isHovered());
            else StorageLook.button(ui, getX(), getY(), getWidth(), getHeight(), label, hint, icon, accent, isHovered());
            return;
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            action.run();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }
    }

    private class BackpackGridWidget extends SearchableGrid {
        private final List<BackpackWidget> backpackWidgets = new ArrayList<>();
        private BackpackWidget openBackpack = null;
        private final FlatButton reloadButton;
        private StorageLook.Totals totals = new StorageLook.Totals();
        private boolean searching, nothingShown;

        private BackpackGridWidget(int x, int y, int width, int height, int maxStoragesPerRow, int internalCols, boolean packed) {
            // fewer columns when they dont fit at this gui size
            int expectedWidth = internalCols * SLOT_SIZE + EDGE_PADDING * 2;
            while (expectedWidth > width - AbstractScrollArea.SCROLLBAR_WIDTH) expectedWidth = --internalCols * SLOT_SIZE + EDGE_PADDING * 2;

            super(x, y, width, height, Component.literal("Backpack grid"), expectedWidth, maxStoragesPerRow, packed, true);

            boolean storageLoaded = false;
            StorageCache.Storage[] storages = StorageCache.getStorages();
            for (int i = 0; i < storages.length; i++) {
                boolean open = StorageOverlayScreen.openStorage == i;
                StorageCache.Storage storage = storages[i];
                if (storage == null && open) storage = StorageCache.adopt(i, handler.getContainer(), name.getString());
                if (storage != null) {
                    storageLoaded = true;
                    BackpackWidget widget = new BackpackWidget(internalCols, i, storage, open);
                    if (open) openBackpack = widget;
                    backpackWidgets.add(widget);
                }
            }
            reloadButton = storageLoaded ? null : newReloadButton();
        }

        private FlatButton newReloadButton() {
            Runnable reload = () -> sendAfterCooldown("/storage");
            int w = getWidth() - AbstractScrollArea.SCROLLBAR_WIDTH - 2;
            return new FlatButton(w, 30, StorageLook.reloadLabel(ui, w), reload);
        }

        // scrolls the open page into view when its header is hidden, like after /bp 15
        private void scrollToOpenBackpack() {
            if (openBackpack == null) return;
            ScreenRectangle rectangle = getGridRectangle();
            if (openBackpack.getY() < rectangle.top() || openBackpack.getY() + HEADER_H > rectangle.bottom()) {
                setGridScroll(getGridScroll() + openBackpack.getY() - rectangle.top());
            }
        }

        @Override
        protected Collection<? extends AbstractWidget> filterWidgets(String input) {
            savedSearch = input;
            searching = !input.isEmpty();
            if (reloadButton != null) return List.of(reloadButton);
            List<BackpackWidget> shown = backpackWidgets.stream().filter(backpack -> backpack.matches(input)).toList();
            nothingShown = shown.isEmpty();
            // every page just got counted, shown or not
            totals = new StorageLook.Totals();
            for (BackpackWidget b : backpackWidgets) totals.add(b.index <= 8, b.seen, b.filled, b.size() - 9, b.items, b.found, b.foundItems);
            return shown;
        }

        @Override
        protected void drawSearchRow(GuiGraphicsExtractor context, int x, int y, int w, boolean focused, boolean hovered) {
            StorageLook.searchRow(ui, x, y, w, focused, hovered, totals.results(searching));
        }

        @Override
        protected void drawScrollbar(GuiGraphicsExtractor context, int x, int y, int h, int thumbY, int thumbH, boolean hovered) {
            StorageLook.scrollbar(ui, x, y, h, thumbY, thumbH, hovered);
        }

        @Override
        public boolean isMouseOver(double mouseX, double mouseY) {
            // mouse belongs to the open pages slots while its over one
            if (hoveredSlot != null && getGridRectangle().containsPoint((int) mouseX, (int) mouseY)) return false;
            return super.isMouseOver(mouseX, mouseY);
        }

        @Override
        protected double scrollRate() {
            return 15;
        }

        @Override
        protected void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
            super.extractWidgetRenderState(context, mouseX, mouseY, deltaTicks);
            // open page scrolled away = its slots go with it
            if (openBackpack != null && (openBackpack.getBottom() < getY() + HEADER_H || openBackpack.getY() >= getBottom())) {
                handler.moveBackpackSlots(0, -1000, 9);
            }
            if (searching && nothingShown) {
                ScreenRectangle rectangle = getGridRectangle();
                StorageLook.noMatches(ui, rectangle.left(), rectangle.top(), rectangle.width(), rectangle.height(), totals);
            }
            StorageLook.endGrid(ui);
        }
    }

    private static boolean itemMatches(ItemStack stack, String filterLowerCase) {
        if (GameState.strip(stack.getDisplayName().getString()).toLowerCase(Locale.ENGLISH).contains(filterLowerCase)) return true;
        var lore = stack.get(DataComponents.LORE);
        if (lore != null) for (Component line : lore.lines()) if (GameState.strip(line.getString()).toLowerCase(Locale.ENGLISH).contains(filterLowerCase)) return true;
        return false;
    }

    private class BackpackWidget extends AbstractWidget {
        private final int rows, columns, index;
        private final String label;
        private final StorageCache.Storage storage;
        private final boolean open;
        // the name hypixel gave the pages menu. empty = never opened, contents unknown
        private final String realName;
        private final boolean seen;
        private final BitSet matchedSlots = new BitSet();
        // stand in for a real slot so remembered items get the same marks as real ones
        private final Container one = new SimpleContainer(1);
        // counted on every search + every change to the open page
        private int filled, found;
        private long items, foundItems;

        private BackpackWidget(int columns, int index, StorageCache.Storage storage, boolean open) {
            int rows = Math.ceilDiv(storage.size() - 9, columns);
            // open page size comes from the menu
            if (open) rows = Math.ceilDiv(handler.getContainer().getContainerSize() - 9, columns);
            super(0, 0, columns * SLOT_SIZE + EDGE_PADDING * 2, rows * SLOT_SIZE + HEADER_H + EDGE_PADDING, Component.literal("Backpack preview"));
            this.rows = rows;
            this.columns = columns;
            this.label = getStorageName(index);
            this.index = index;
            this.storage = storage;
            this.open = open;
            StorageCache.Page page = StorageCache.page(index <= 8, index <= 8 ? index + 1 : index - 8);
            this.realName = page == null || page.title().equals(this.label) ? "" : page.title();
            this.seen = open || !this.realName.isEmpty();
        }

        private int size() {
            return open ? handler.getContainer().getContainerSize() : storage.size();
        }

        boolean matches(String filter) {
            matchedSlots.clear();
            filled = found = 0;
            items = foundItems = 0;
            String filterLowerCase = filter.toLowerCase(Locale.ENGLISH);
            for (int i = 9; i < size(); ++i) {
                ItemStack currentStack = open ? handler.getContainer().getItem(i) : storage.getStack(i);
                boolean match = itemMatches(currentStack, filterLowerCase);
                if (match) matchedSlots.set(i);
                if (currentStack.isEmpty()) continue;
                filled++;
                items += currentStack.getCount();
                if (match && !filter.isEmpty()) {
                    found++;
                    foundItems += currentStack.getCount();
                }
            }
            // open page stays on screen even with no match
            return matchedSlots.cardinality() != 0 || open;
        }

        private StorageLook.Page look() {
            boolean ender = index <= 8;
            return new StorageLook.Page(label, ender ? "EC " + (index + 1) : "BP " + (index - 8), ender ? StorageLook.ENDER_ICON : StorageLook.BACKPACK_ICON, ender ? StorageLook.ENDER : StorageLook.BACKPACK, filled, size() - 9, seen, open, found);
        }

        @Override
        public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            int x = getX(), y = getY();
            Theme theme = Theme.current();
            StorageLook.Page look = look();
            StorageLook.page(ui, x, y, getWidth(), getHeight(), look);
            for (int i = 0; i < rows * columns; ++i) {
                if (look.placeholder()) break; // size unknown so no slots that would suggest one
                int itemX = x + i % columns * SLOT_SIZE + 8, itemY = y + i / columns * SLOT_SIZE + SLOT_SIZE;
                if (i < size() - 9) slotBox(itemX, itemY);
                else StorageLook.missingSlot(ui, itemX, itemY);
            }
            if (!open) {
                Font font = minecraft.font;
                for (int i = 9; i < size(); ++i) {
                    ItemStack currentStack = storage.getStack(i);
                    int itemX = x + (i - 9) % columns * SLOT_SIZE + 8, itemY = y + (i - 9) / columns * SLOT_SIZE + SLOT_SIZE;
                    if (!currentStack.isEmpty()) {
                        one.setItem(0, currentStack);
                        Slot slot = new Slot(one, 0, itemX, itemY);
                        SlotInfo.back(graphics, slot);
                        graphics.item(currentStack, itemX, itemY);
                        graphics.itemDecorations(font, currentStack, itemX, itemY);
                        SlotInfo.front(graphics, slot);
                        if (graphics.containsPointInScissor(mouseX, mouseY) && mouseX > itemX && mouseX <= itemX + SLOT_SIZE && mouseY > itemY && mouseY <= itemY + SLOT_SIZE && mouseY > topPos && mouseY < topPos + StorageOverlayScreen.this.getHeight()) {
                            graphics.setTooltipForNextFrame(font, currentStack, mouseX, mouseY);
                            StorageLook.slotHover(ui, itemX, itemY);
                        }
                    }
                    if (!matchedSlots.get(i)) {
                        StorageLook.dim(ui, itemX, itemY);
                    } else if (grid != null && grid.searching && !currentStack.isEmpty()) StorageLook.found(ui, itemX, itemY);
                }
            } else {
                // the real slots of the open page move here
                handler.moveBackpackSlots(getX() - leftPos + 8, getY() - topPos + SLOT_SIZE, columns);
            }
            if (!look.seen()) StorageLook.pageHint(ui, x, y, getWidth(), getHeight());
            if (isHovered() && !open) StorageLook.pageHover(ui, x, y, getWidth(), getHeight(), look.accent());
            if (isHovered() && mouseY < y + HEADER_H && handler.getCarried().isEmpty()) {
                ui.tooltip(StorageLook.pageTooltip(look, realName, index <= 8 ? "§d" : "§6", items, foundItems));
                ui.flushTooltip();
            }
        }

        @Override
        public void onClick(MouseButtonEvent event, boolean doubleClick) {
            super.onClick(event, doubleClick);
            if (!open) switchOpenStorage(index);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
        }
    }

    // one pages items as a grid for the storage menu hover preview, returns height
    public static int grid(Ui ui, Minecraft mc, StorageCache.Page p, int px, int py) {
        for (int i = 0; i < p.items().size(); i++) {
            ItemStack st = p.items().get(i);
            int ix = px + (i % 9) * SLOT_SIZE, iy = py + (i / 9) * SLOT_SIZE;
            ui.round(ix, iy, SLOT_SIZE - 1, SLOT_SIZE - 1, 2, 0xFF1E1C18);
            if (st.isEmpty()) continue;
            ui.icon(st, ix, iy, 1f);
            if (st.getCount() > 1) {
                String n = String.valueOf(st.getCount());
                ui.textScaled(n, ix + SLOT_SIZE - 2 - ui.width(n) * 0.7f, iy + SLOT_SIZE - 9, 0.7f, 0xFFFFFFFF, true);
            }
        }
        return p.rows() * SLOT_SIZE;
    }
}
