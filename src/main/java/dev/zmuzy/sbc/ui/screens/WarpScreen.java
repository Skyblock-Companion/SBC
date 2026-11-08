// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Modern Warp Menu; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui.screens;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.ui.layout.FlowLayout;
import dev.zmuzy.sbc.ui.layout.UIGraphics;
import dev.zmuzy.sbc.ui.layout.Sizing;
import dev.zmuzy.sbc.ui.layout.Surface;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// port of modern warp menu (MIT) instead of hypixels fast travel chest and the rifts porhtal. same layout
// data, same math for where stuff goes, same hover growth
public final class WarpScreen extends SbcScreen {
    // wait before warping again after a fail + how long the error stays
    private static final long WARP_FAIL_COOL_DOWN = 500L, WARP_FAIL_TOOLTIP_DISPLAY_TIME = 2000L;
    private static final int TEXT_GREEN = 5635925, TEXT_WHITE = 0xFFFFFF;
    private static final float UN_HOVERED_BRIGHTNESS = 0.9F;
    private static final float ISLAND_HOVERED_SCALE = 1.1F, CORNER_HOVERED_SCALE = 1.2F;
    private static final long ISLAND_TRANSITION = 400, CORNER_TRANSITION = 500;
    // chest asked for as is (hypixels own menu) so we dont replace it again
    private static int plainChestFor = -1;

    private final ChestMenu menu;
    private final boolean rift;
    private final WarpLayout layout;
    private final List<WarpLayout.MenuCheck> checks;
    private final List<ScaleButton> children = new ArrayList<>();
    private final List<IslandButton> islands = new ArrayList<>();
    private boolean menuChecked;
    private String warpFailMessage;
    private long warpFailCoolDownExpiryTime, warpFailTooltipExpiryTime;

    private WarpScreen(Screen parent, ChestMenu menu, boolean rift) {
        super(rift ? "Porhtal" : "Fast Travel", parent);
        this.menu = menu;
        this.rift = rift;
        this.layout = WarpLayout.get(rift);
        this.checks = WarpLayout.menuChecks(rift);
    }

    public static boolean active() {
        return Features.on("warp_menu") && GameState.inSkyBlock;
    }

    private static boolean option(String key, boolean def) {
        var f = Features.get("warp_menu");
        var o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    // which layout a title wants: 0 none, 1 overworld, 2 rift
    public static int kind(String title) {
        String l = GameState.strip(title).trim().toLowerCase(Locale.ROOT);
        if (l.equals("fast travel")) return 1;
        if (l.equals("porhtal")) return 2;
        return 0;
    }

    // swaps a just opened fast travel chest for the picture menu. false = not a warp menu
    public static boolean openFor(ContainerScreen chest) {
        if (!active() || chest.getMenu().containerId == plainChestFor) return false;
        int k = kind(chest.getTitle().getString());
        if (k == 0) return false;
        Minecraft.getInstance().gui.setScreen(new WarpScreen(chest, chest.getMenu(), k == 2));
        return true;
    }

    // from modern warp menu ChatListener: warp failed line while this is open
    public static void onChat(String line) {
        if (Minecraft.getInstance().gui.screen() instanceof WarpScreen s) {
            String fail = WarpLayout.failMessage(line);
            if (fail != null) s.onWarpFail(fail);
        }
    }

    private void onWarpFail(String message) {
        long now = Util.getMillis();
        warpFailCoolDownExpiryTime = now + WARP_FAIL_COOL_DOWN;
        warpFailTooltipExpiryTime = now + WARP_FAIL_TOOLTIP_DISPLAY_TIME;
        warpFailMessage = message;
    }

    private void showChest() {
        plainChestFor = menu.containerId;
        minecraft.gui.setScreen(parent);
    }

    // from modern warp menu menuItemsMatch, once the last slot it checks arrived
    private void checkMenu() {
        if (menuChecked || checks.isEmpty()) return;
        int last = checks.getLast().slot();
        if (last >= menu.slots.size()) {
            menuChecked = true;
            showChest();
            return;
        }
        if (!menu.slots.get(last).hasItem()) return;
        menuChecked = true;
        for (WarpLayout.MenuCheck c : checks) {
            ItemStack st = menu.slots.get(c.slot()).getItem();
            boolean ok = !st.isEmpty() && BuiltInRegistries.ITEM.getKey(st.getItem()).toString().equals(c.itemId())
                    && GameState.strip(st.getHoverName().getString()).trim().equals(c.itemName());
            if (!ok) {
                showChest();
                return;
            }
        }
    }

    // --- ScaleTransition ---

    private static final class ScaleTransition {
        private final long startTime = Util.getMillis(), duration, endTime;
        private final float startScale, endScale;
        private float progress, currentScale;
        private boolean finished;

        ScaleTransition(long duration, float startScale, float endScale) {
            this.duration = duration;
            this.endTime = startTime + duration;
            this.startScale = startScale;
            this.endScale = endScale;
            this.currentScale = startScale;
        }

        void step() {
            if (!finished) {
                long now = Util.getMillis();
                progress = (float) (now - startTime) / duration;
                if (now >= endTime) {
                    progress = 1;
                    finished = true;
                }
            }
            currentScale = startScale + (endScale - startScale) * progress;
        }
    }

    // --- the buttons ---

    // from modern warp menu ScaleTransitionButton
    private abstract class ScaleButton {
        final String message;
        final WarpLayout.Tex background, foreground;
        ScaleTransition transition;
        float scaledX, scaledY, scaledWidth, scaledHeight;
        boolean hovered, visible = true;

        ScaleButton(String message, WarpLayout.Tex background, WarpLayout.Tex foreground) {
            this.message = message;
            this.background = background;
            this.foreground = foreground;
        }

        void calculateHoverState(int mouseX, int mouseY) {
            hovered = mouseX >= scaledX && mouseY >= scaledY && mouseX <= scaledX + scaledWidth && mouseY <= scaledY + scaledHeight;
        }

        void transitionStep(long duration, float hoveredScale) {
            transition.step();
            if (hovered) {
                if (transition.endScale == 1) transition = new ScaleTransition((long) (transition.progress * duration), transition.currentScale, hoveredScale);
            } else if (transition.endScale == hoveredScale) {
                transition = new ScaleTransition((long) (transition.progress * duration), transition.currentScale, 1);
            }
        }

        void renderTexture(GuiGraphicsExtractor g, WarpLayout.Tex tex) {
            if (tex == null) return;
            if (WarpImages.ready(tex.id())) WarpBlit.blit(g, tex.id(), scaledX, scaledY, scaledWidth, scaledHeight, hovered ? 0xFFFFFFFF : 0xFFE6E6E6);
            // not loaded yet: plain card same size, the island name gets drawn over it like normal
            else g.fill((int) scaledX, (int) scaledY, (int) (scaledX + scaledWidth), (int) (scaledY + scaledHeight), hovered ? 0xA0304050 : 0x80202830);
        }

        void renderMessage(GuiGraphicsExtractor g, float xOffset, float yOffset, int rgb) {
            float b = hovered ? 1f : UN_HOVERED_BRIGHTNESS;
            int color = 0xFF000000 | (int) (((rgb >> 16) & 0xFF) * b) << 16 | (int) (((rgb >> 8) & 0xFF) * b) << 8 | (int) ((rgb & 0xFF) * b);
            Matrix3x2fStack stack = g.pose();
            stack.pushMatrix();
            stack.translate(scaledX + xOffset, scaledY + yOffset);
            stack.scale(transition.currentScale, transition.currentScale);
            String[] lines = message.split("\n");
            for (int i = 0; i < lines.length; i++) g.centeredText(font, lines[i], 0, font.lineHeight * i, color);
            stack.popMatrix();
        }

        boolean isMouseOver(double x, double y) {
            return visible && x >= scaledX && y >= scaledY && x <= scaledX + scaledWidth && y <= scaledY + scaledHeight;
        }

        abstract void render(GuiGraphicsExtractor g, int mouseX, int mouseY);

        abstract void press();
    }

    // from modern warp menu IslandButton + ScaledGrid: island grows from its center and its 40x40 grid grows with it
    private final class IslandButton extends ScaleButton {
        final WarpLayout.Island island;
        final float originalX, originalY;
        final int width, height;
        float gridStartX, gridStartY, gridUnitWidth, gridUnitHeight, scaleFactor = 1;

        IslandButton(WarpLayout.Island island, float x, float y, int width, int height) {
            super(island.name(), island.texture(), island.hover());
            this.island = island;
            this.originalX = x;
            this.originalY = y;
            this.width = width;
            this.height = height;
            this.scaledX = x;
            this.scaledY = y;
            this.transition = new ScaleTransition(0, 1, 1);
            setScaleFactor(1);
        }

        void setScaleFactor(float f) {
            scaleFactor = f;
            float gridWidth = width * f, gridHeight = height * f;
            gridUnitWidth = width / (float) WarpLayout.ISLAND_GRID * f;
            gridUnitHeight = height / (float) WarpLayout.ISLAND_GRID * f;
            gridStartX = originalX - Math.abs(gridWidth - width) / 2;
            gridStartY = originalY - Math.abs(gridHeight - height) / 2;
        }

        @Override
        void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            transitionStep(ISLAND_TRANSITION, ISLAND_HOVERED_SCALE);
            setScaleFactor(transition.currentScale);
            scaledX = gridStartX;
            scaledY = gridStartY;
            scaledWidth = width * scaleFactor;
            scaledHeight = height * scaleFactor;
            renderTexture(g, background);
            if (hovered) renderTexture(g, foreground);
            if (option("showIslandLabels", true)) renderMessage(g, scaledWidth / 2f, scaledHeight, TEXT_GREEN);
        }

        @Override
        void press() {
            warp(island.warps().getFirst());
        }
    }

    // from modern warp menu WarpButton: portal corner sits on its grid point, not its center
    private final class WarpButton extends ScaleButton {
        final IslandButton parentIsland;
        final WarpLayout.Warp warp;
        final int width, height;
        boolean drawLabel = true;

        WarpButton(IslandButton parentIsland, WarpLayout.Warp warp, int width, int height) {
            super(warp.name(), layout.warpIcon, layout.warpIconHover);
            this.parentIsland = parentIsland;
            this.warp = warp;
            this.width = width;
            this.height = height;
            this.transition = new ScaleTransition(0, 0, 0);
        }

        @Override
        void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            calculateHoverState(mouseX, mouseY);
            transition.currentScale = parentIsland.scaleFactor;
            scaledWidth = width * transition.currentScale;
            scaledHeight = height * transition.currentScale;
            scaledX = parentIsland.gridStartX + parentIsland.gridUnitWidth * warp.gridX();
            scaledY = parentIsland.gridStartY + parentIsland.gridUnitHeight * warp.gridY();
            renderTexture(g, background);
            if (hovered) renderTexture(g, foreground);
            if (drawLabel && (!option("hideWarpLabelsUntilIslandHovered", false) || parentIsland.hovered))
                renderMessage(g, scaledWidth / 2f, scaledHeight, TEXT_WHITE);
        }

        @Override
        void press() {
            warp(warp);
        }
    }

    // from modern warp menu ConfigButton + RegularWarpMenuButton
    private final class CornerButton extends ScaleButton {
        final float xPosition, yPosition;
        final int width, height;
        final Runnable onPress;

        CornerButton(WarpLayout.Corner corner, Runnable onPress) {
            super("", corner.texture(), null);
            this.width = (int) (WarpScreen.this.width * corner.widthPct());
            this.height = (int) (corner.texture().height() * ((float) width / corner.texture().width()));
            this.xPosition = WarpScreen.this.width / (float) WarpLayout.GRID_W * corner.gridX();
            this.yPosition = WarpScreen.this.height / (float) WarpLayout.GRID_H * corner.gridY();
            this.onPress = onPress;
            this.transition = new ScaleTransition(0, 1, 1);
        }

        @Override
        void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
            calculateHoverState(mouseX, mouseY);
            transitionStep(CORNER_TRANSITION, CORNER_HOVERED_SCALE);
            float f = transition.currentScale;
            scaledWidth = width * f;
            scaledHeight = height * f;
            scaledX = xPosition - (scaledWidth - width) / 2;
            scaledY = yPosition - (scaledHeight - height) / 2;
            renderTexture(g, background);
        }

        @Override
        void press() {
            onPress.run();
        }
    }

    private static boolean shouldShowWarp(WarpLayout.Warp warp) {
        for (String tag : warp.tags()) {
            switch (tag) {
                case "bingo" -> {
                    if (option("hideUnobtainableWarps", true)) return false;
                }
                case "jerry" -> {
                    if (!option("showJerryIsland", true)) return false;
                    List<String> board = new ArrayList<>();
                    for (String l : GameState.scoreboard) board.add(GameState.strip(l));
                    if (!WarpLayout.jerryOpen(board, java.time.LocalDate.now().getMonthValue())) return false;
                }
                default -> {
                }
            }
        }
        return true;
    }

    // from modern warp menu init() + updateButtonStates(): sizes come from the screen so this reruns after a resize
    private void buildButtons() {
        children.clear();
        islands.clear();
        if (layout.configButton != null) children.add(new CornerButton(layout.configButton, () -> {
            if (minecraft.player != null) minecraft.player.closeContainer();
            SettingsScreen.open(null, "warp_menu");
        }));
        if (layout.regularButton != null && option("showRegularWarpMenuButton", false)) children.add(new CornerButton(layout.regularButton, this::showChest));
        int warpWidth = (int) (width * layout.warpIconPct);
        int warpHeight = (int) (layout.warpIcon.height() * ((float) warpWidth / layout.warpIcon.width()));
        float unitW = width / (float) WarpLayout.GRID_W, unitH = height / (float) WarpLayout.GRID_H;
        for (WarpLayout.Island island : layout.islands) {
            if (island.warps().isEmpty()) continue;
            int w = (int) (width * island.widthPct());
            int h = (int) (island.texture().height() * ((float) w / island.texture().width()));
            IslandButton button = new IslandButton(island, unitW * island.gridX(), unitH * island.gridY(), w, h);
            if (island.warps().size() == 1) button.visible = shouldShowWarp(island.warps().getFirst());
            children.add(button);
            islands.add(button);
            for (WarpLayout.Warp warp : island.warps()) {
                WarpButton wb = new WarpButton(button, warp, warpWidth, warpHeight);
                wb.visible = shouldShowWarp(warp);
                if (island.warps().size() == 1) wb.drawLabel = !option("hideWarpLabelForIslandsWithOneWarp", true);
                children.add(wb);
            }
        }
    }

    private void warp(WarpLayout.Warp warp) {
        if (Util.getMillis() <= warpFailCoolDownExpiryTime) return;
        if (rift) {
            clickSlot(warp.slotIndex());
            return;
        }
        if (minecraft.player != null) minecraft.player.connection.sendCommand(warp.warpCommand());
    }

    // from modern warp menu clickSlot: left click on the porhtals own slot
    private void clickSlot(int slotIndex) {
        if (slotIndex < 0 || slotIndex >= menu.slots.size()) {
            onWarpFail("Invalid Slot Number (" + slotIndex + ")");
            return;
        }
        Slot slot = menu.slots.get(slotIndex);
        if (!slot.hasItem()) {
            onWarpFail("Slot " + slotIndex + " Has No Item to Click On");
            return;
        }
        if (!menu.getCarried().isEmpty()) {
            onWarpFail("Mouse is Already Holding an Item");
            return;
        }
        if (minecraft.gameMode != null && minecraft.player != null) minecraft.gameMode.handleContainerInput(menu.containerId, slotIndex, 0, ContainerInput.PICKUP, minecraft.player);
    }

    // --- drawing + input ---

    private final class Canvas extends UiComponent {
        Canvas() {
            super((ui, g, x, y, w, h, mx, my) -> paint(g, mx, my), 100, 100);
        }

        @Override
        public boolean onMouseDown(MouseButtonEvent e, boolean doubleClick) {
            if (e.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return true;
            for (ScaleButton b : children.reversed()) {
                if (b.isMouseOver(e.x(), e.y())) {
                    SbcWidgets.clickSound();
                    b.press();
                    break;
                }
            }
            return true;
        }
    }

    private void paint(UIGraphics g, int mouseX, int mouseY) {
        checkMenu();
        // several islands under the mouse = only the last one in the list counts as hovered
        IslandButton top = null;
        for (IslandButton island : islands) {
            island.calculateHoverState(mouseX, mouseY);
            if (island.hovered && island.visible) {
                if (top != null) top.hovered = false;
                top = island;
            } else island.hovered = false;
        }
        for (ScaleButton b : children) if (b.visible) b.render(g, mouseX, mouseY);
        if (Util.getMillis() <= warpFailTooltipExpiryTime && warpFailMessage != null) {
            List<ClientTooltipComponent> tooltip = List.of(ClientTooltipComponent.create(Component.literal(warpFailMessage).withStyle(ChatFormatting.RED).getVisualOrderText()));
            g.nextStratum();
            g.tooltip(font, tooltip, mouseX, mouseY, DefaultTooltipPositioner.INSTANCE, null);
        }
    }

    @Override
    protected void init() {
        buildButtons();
        super.init();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        extractTransparentBackground(g);
    }

    @Override
    protected void build(FlowLayout root) {
        root.surface(Surface.BLANK);
        Canvas c = new Canvas();
        c.sizing(Sizing.fill(100), Sizing.fill(100));
        root.child(c);
    }

    @Override
    public boolean keyPressed(KeyEvent e) {
        if (e.key() == GLFW.GLFW_KEY_ESCAPE || minecraft.options.keyInventory.matches(e)) {
            onClose();
            return true;
        }
        if (e.key() == GLFW.GLFW_KEY_TAB) {
            showChest();
            return true;
        }
        return super.keyPressed(e);
    }

    @Override
    public void onClose() {
        if (minecraft.player != null) minecraft.player.closeContainer();
        minecraft.gui.setScreen(null);
    }
}
