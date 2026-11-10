// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.ui.storage;

import io.github.zmuzy.sbc.mc.mixin.SlotAccessor;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;

// port of skyblocker StorageOverlayScreenHandler. the servers chest menu with slots moved around: your
// inventory lines up with the bottom of the overlay and the open pages slots follow that page
public class StorageOverlayMenu extends ChestMenu {
    private final int rows;

    public StorageOverlayMenu(MenuType<?> type, int syncId, Inventory playerInventory, Container inventory, int rows, boolean isBackpack, int height) {
        super(type, syncId, playerInventory, inventory, rows);
        this.rows = rows;
        int yOffset = rows * 18;
        shiftInventory(height - yOffset - 113);

        if (!isBackpack) {
            for (int i = 0; i < rows * 9; i++) {
                Slot slot = slots.get(i);
                slots.set(i, new InactiveSlot(slot.container, slot.getContainerSlot(), slot.x, slot.y));
            }
        } else {
            for (int i = 0; i < 9; i++) {
                Slot slot = slots.get(i);
                slots.set(i, new InactiveSlot(slot.container, slot.getContainerSlot(), slot.x, slot.y));
            }
            updateBackpackSlots(height);
        }
    }

    public StorageOverlayMenu(ChestMenu handler, boolean isBackpack, int height, Inventory playerInventory) {
        this(handler.getType(), handler.containerId, playerInventory, handler.getContainer(), handler.getRowCount(), isBackpack, height);
    }

    public int containerRows() {
        return rows;
    }

    public void shiftInventory(int shift) {
        for (int i = rows * 9; i < slots.size(); i++) {
            Slot originalSlot = slots.get(i);
            Slot slot = new Slot(originalSlot.container, originalSlot.getContainerSlot(), originalSlot.x, originalSlot.y + shift);
            slot.index = i;
            slots.set(i, slot);
        }
    }

    public void updateBackpackSlots(int height) {
        for (int i = 9; i < rows * 9; ++i) {
            Slot originalSlot = slots.get(i);
            Slot slot = new Slot(originalSlot.container, originalSlot.getContainerSlot(), 0, -20) {
                @Override
                public boolean isActive() {
                    return this.y > firstActiveY() && this.y < height - 94;
                }

                @Override
                public boolean isHighlightable() {
                    return this.y > firstActiveY() && this.y < height - 94;
                }
            };
            slot.index = i;
            slots.set(i, slot);
        }
    }

    // classics grid starts higher, a slot leaving at the top must show till its fully gone
    private static int firstActiveY() {
        return 2;
    }

    // moves the container slots to a new top left. columns = how many the page shows with
    public void moveBackpackSlots(int x, int y, int columns) {
        for (int i = 9; i < rows * 9; ++i) {
            int itemX = x + (i - 9) % columns * 18;
            int itemY = y + (i - 9) / columns * 18;
            SlotAccessor slot = (SlotAccessor) slots.get(i);
            slot.sbc$setX(itemX);
            slot.sbc$setY(itemY);
        }
    }

    private static class InactiveSlot extends Slot {
        InactiveSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean isActive() {
            return false;
        }
    }
}
