// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui.panels;

import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.feature.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Locale;

// craft item menu as a plain crafting table. starts off bc it replaces hypixels menu. port of skyblocker
// SkyblockCraftingTableScreen + its handler
public final class CraftingTable {
    private static final int[] NORMAL_SLOTS = {10, 11, 12, 16, 19, 20, 21, 23, 25, 28, 29, 30, 34};
    private static final int[] RIFT_SLOTS = {11, 12, 13, 20, 21, 22, 24, 29, 30, 31};
    private static final Identifier TEXTURE = Identifier.withDefaultNamespace("textures/gui/container/crafting_table.png");
    private static final Identifier QUICK_CRAFT = Identifier.fromNamespaceAndPath("sbcompanion", "textures/gui/sprites/quick_craft/quick_craft_overlay.png");
    private static final WidgetSprites MORE_CRAFTS = new WidgetSprites(
            Identifier.fromNamespaceAndPath("sbcompanion", "quick_craft/more_button"),
            Identifier.fromNamespaceAndPath("sbcompanion", "quick_craft/more_button_disabled"),
            Identifier.fromNamespaceAndPath("sbcompanion", "quick_craft/more_button_highlighted"));

    private CraftingTable() {}

    // MenuScreensConstructorMixin case: true when the menu opened as this
    public static boolean open(Minecraft client, ChestMenu chest, Component name) {
        if (!Features.on("fancy_crafting_table") || !name.getString().toLowerCase(Locale.ENGLISH).contains("craft item") || client.player == null) return false;
        Inventory inventory = client.player.getInventory();
        Menu menu = new Menu(chest.getType(), chest.containerId, inventory, chest.getContainer(), chest.getRowCount(), inMirrorverse());
        client.player.containerMenu = menu;
        client.gui.setScreen(new Screen(menu, inventory, Component.literal("Craft Item")));
        return true;
    }

    private static boolean inMirrorverse() {
        return dev.zmuzy.sbc.rift.RiftApi.inMirrorVerse();
    }

    // from skyblocker getCoords: where a chest slot goes on the table
    public static int[] coords(int slot, boolean mirrorverse) {
        if (mirrorverse) {
            if (slot == 24) return new int[]{124, 35};
            return new int[]{30 + (slot % 9 - 2) * 18, 17 + (slot / 9 - 1) * 18};
        }
        if (slot == 23) return new int[]{124, 35};
        if (slot == 16 || slot == 25 || slot == 34) return new int[]{152, (slot / 9 - 1) * 18 + 8};
        return new int[]{30 + (slot % 9 - 1) * 18, 17 + (slot / 9 - 1) * 18};
    }

    public static final class Menu extends ChestMenu {
        final boolean mirrorverse;

        Menu(MenuType<?> type, int syncId, Inventory inventory, Container container, int rows, boolean mirrorverse) {
            super(type, syncId, inventory, container, rows);
            this.mirrorverse = mirrorverse;
            int[] active = mirrorverse ? RIFT_SLOTS : NORMAL_SLOTS;
            for (int i = 0; i < rows * 9; i++) {
                Slot original = slots.get(i);
                Slot slot;
                if (Arrays.binarySearch(active, i) >= 0) {
                    int[] at = coords(i, mirrorverse);
                    slot = new Slot(original.container, original.getContainerSlot(), at[0], at[1]);
                } else {
                    slot = new Disabled(original.container, original.getContainerSlot());
                }
                slot.index = i;
                slots.set(i, slot);
            }
            int yOffset = (rows - 4) * 18 + 19;
            for (int i = rows * 9; i < slots.size(); i++) {
                Slot original = slots.get(i);
                Slot slot = new Slot(original.container, original.getContainerSlot(), original.x, original.y - yOffset);
                slot.index = i;
                slots.set(i, slot);
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    }

    private static final class Disabled extends Slot {
        Disabled(Container container, int index) {
            super(container, index, -20, -20);
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }

    private static String path(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    public static final class Screen extends AbstractContainerScreen<Menu> {
        private ImageButton moreCrafts;

        Screen(Menu menu, Inventory inventory, Component title) {
            super(menu, inventory, title);
        }

        @Override
        protected void init() {
            super.init();
            if (!menu.mirrorverse) {
                moreCrafts = new ImageButton(leftPos + 152, topPos + 63, 16, 16, MORE_CRAFTS, b -> slotClicked(menu.slots.get(26), menu.slots.get(26).index, 0, ContainerInput.PICKUP));
                moreCrafts.setTooltip(Tooltip.create(Component.literal("More Crafts")));
                addRenderableWidget(moreCrafts);
            }
            titleLabelX = 29;
        }

        @Override
        public void containerTick() {
            super.containerTick();
            if (moreCrafts == null) return;
            ItemStack stack = menu.slots.get(26).getItem();
            moreCrafts.active = stack.isEmpty() || path(stack).equals("player_head");
        }

        @Override
        protected void extractSlot(GuiGraphicsExtractor g, Slot slot, int mouseX, int mouseY) {
            ItemStack stack = slot.getItem();
            if (slot.index == 23 && path(stack).equals("barrier")) return;
            if (path(stack).equals("gray_stained_glass_pane") && Gain.sbId(stack).isEmpty()) return;
            super.extractSlot(g, slot, mouseX, mouseY);
        }

        @Override
        public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            super.extractBackground(g, mouseX, mouseY, a);
            int j = (height - imageHeight) / 2;
            g.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, j, 0, 0, imageWidth, imageHeight, 256, 256);
            // their picture has 4px margin, for packs with other edges
            if (!menu.mirrorverse) g.blit(RenderPipelines.GUI_TEXTURED, QUICK_CRAFT, leftPos + 143, j - 3, 0, 0, 37, 90, 37, 90);
        }
    }
}
