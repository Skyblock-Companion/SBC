// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.core.game;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// from skyhanni data/OtherInventoryData.kt: knows from packets when a menu fully opened and when it changed
// after. a change after open waits a tick so a burst of slots = one update
public final class MenuPackets {
    private static final class Open {
        final int windowId, slotCount;
        final Set<Integer> items = new HashSet<>();

        Open(int windowId, int slotCount) {
            this.windowId = windowId;
            this.slotCount = slotCount;
        }
    }

    private static Open current;
    private static boolean acceptItems, late;
    private static Runnable listener = () -> {};

    private MenuPackets() {}

    public static void listen(Runnable onChange) {
        listener = onChange;
    }

    public static void close() {
        late = false;
        current = null;
    }

    // slotCount below 0 = menu type with unknown size, not followed
    public static void open(int windowId, int slotCount) {
        close();
        if (slotCount < 0) return;
        current = new Open(windowId, slotCount);
        acceptItems = true;
    }

    // which slots have something, in slot order
    public static void content(int windowId, List<Boolean> filled) {
        Open it = current;
        if (it == null || it.windowId != windowId) return;
        if (!acceptItems) it.items.clear();
        for (int slot = 0; slot < Math.min(filled.size(), it.slotCount); slot++) if (filled.get(slot)) it.items.add(slot);
        if (acceptItems) done();
        else late = true;
    }

    public static void slot(int windowId, int slot, boolean filled) {
        Open it = current;
        if (it == null || it.windowId != windowId) return;
        if (!acceptItems) {
            if (slot < it.slotCount && filled) {
                it.items.add(slot);
                late = true;
            }
            return;
        }
        if (slot >= it.slotCount) {
            done();
            return;
        }
        if (filled) it.items.add(slot);
        if (it.items.size() == it.slotCount) done();
    }

    private static void done() {
        acceptItems = false;
        listener.run();
    }

    // every client tick. closed screen = closed menu (CloseWindowEvent)
    public static void tick(boolean menuOnScreen) {
        if (current != null && !menuOnScreen && !acceptItems) close();
        if (!late) return;
        late = false;
        listener.run();
    }
}
