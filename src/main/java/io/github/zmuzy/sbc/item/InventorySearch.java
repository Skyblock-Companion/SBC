// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.item;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.mc.mixin.ContainerAccess;
import io.github.zmuzy.sbc.ui.ContainerValue;
import io.github.zmuzy.sbc.ui.ItemList;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

// ctrl+f in any chest or inventory: search box above the window, slots whose name or lore dont match get
// dimmed, matches get a green edge
// box says how many filled slots match. up/down = earlier searches, esc or ctrl+f again closes
// search text stays till the screen changes
public final class InventorySearch {
    private static final Ui ui = new Ui();
    private static final Deque<String> history = new ArrayDeque<>();
    private static boolean open;
    private static String query = "";
    private static int histPos = -1;
    private static Screen lastScreen;
    private static int boxX, boxY, boxW;
    private InventorySearch() {}

    // true over the search box as drawn this frame
    public static boolean over(double x, double y) {
        return boxW > 0 && x >= boxX && y >= boxY && x < boxX + boxW && y < boxY + BOX_H;
    }

    private static boolean optOn(String key) {
        var o = Features.get("inv_search").option(key);
        return o != null && o.bool();
    }

    private static boolean applies(Screen s) {
        return Features.on("inv_search") && s instanceof AbstractContainerScreen<?> && !(s instanceof io.github.zmuzy.sbc.ui.storage.StorageOverlayScreen) && (io.github.zmuzy.sbc.core.GameState.inSkyBlock || !optOn("inv_search.skyblockOnly"));
    }

    public static boolean active() {
        return open && !query.isEmpty();
    }

    private static boolean matches(ItemStack st) {
        if (st.isEmpty()) return false;
        String q = query.toLowerCase(Locale.ROOT).trim();
        if (q.isEmpty()) return true;
        StringBuilder hay = new StringBuilder(GameState.strip(st.getHoverName().getString()));
        var lore = st.get(DataComponents.LORE);
        if (lore != null) for (Component c : lore.lines()) hay.append(' ').append(GameState.strip(c.getString()));
        String h = hay.toString().toLowerCase(Locale.ROOT);
        for (String w : q.split("\\s+")) if (!h.contains(w)) return false;
        return true;
    }

    // runs after each slot is drawn, dims non matches and edges the matches
    public static void slot(GuiGraphicsExtractor g, Slot slot) {
        if (!active()) return;
        ItemStack st = slot.getItem();
        if (st.isEmpty()) return;
        mark(g, slot.x, slot.y, matches(st));
    }

    // what the search does to one slot here
    public static void mark(GuiGraphicsExtractor g, int x, int y, boolean match) {
        if (match) {
            int edge = Modern.GOOD;
            g.fill(x, y, x + 16, y + 16, 0x305FEA8C);
            g.fill(x - 1, y - 1, x + 17, y, edge);
            g.fill(x - 1, y + 16, x + 17, y + 17, edge);
            g.fill(x - 1, y, x, y + 16, edge);
            g.fill(x + 16, y, x + 17, y + 16, edge);
        } else g.fill(x, y, x + 16, y + 16, 0xC80A0F17);
    }

    // search box above the container
    public static void draw(Screen s, GuiGraphicsExtractor g, int mx, int my) {
        boxW = 0;
        if (!applies(s)) return;
        if (s != lastScreen) {
            lastScreen = s;
            open = false;
            if (io.github.zmuzy.sbc.feature.Features.get("inv_search") != null && io.github.zmuzy.sbc.feature.Features.get("inv_search").option("reset").bool()) query = "";
        }
        if (!open) return;
        ui.begin(g, mx, my);
        Theme t = Theme.current();
        int found = 0, filled = 0;
        if (!query.isEmpty() && s instanceof AbstractContainerScreen<?> cs) {
            for (Slot sl : cs.getMenu().slots) {
                if (sl.getItem().isEmpty()) continue;
                filled++;
                if (matches(sl.getItem())) found++;
            }
        }
        // right above the window and its button row. next to them when a tall chest leaves no room above
        ContainerAccess a = (ContainerAccess) s;
        int w = a.sbc$width(), x = a.sbc$left(), row = QuickNav.rowTop(s);
        int y = (row < 0 ? a.sbc$top() : row) - BOX_H - 2 - (ContainerValue.plateAbove() ? 16 : 0);
        if (y < 2) {
            y = 2;
            int room = x - 6;
            if (room >= 120) {
                w = Math.min(w, room);
                x -= 4 + w;
            }
        }
        boxX = x;
        boxY = y;
        boxW = w;
        paintBox(ui, x, y, w, query, !history.isEmpty(), found, filled);
    }

    private static final int BOX_H = 18;

    // the box here: the words, how many filled slots match, and a meter of that share
    public static void paintBox(Ui ui, int x, int y, int w, String query, boolean hasHistory, int found, int filled) {
        Theme.current().panel(ui, x, y, w, BOX_H);
        boolean typed = !query.isEmpty();
        int accent = typed && found == 0 ? Modern.BAD : Modern.GOOD;
        ui.icon(io.github.zmuzy.sbc.core.LegacyItems.stack("minecraft:spyglass"), x + 3, y + 3, Modern.ICON_SCALE);
        int tx = x + Modern.ICON + 5, ty = y + 4;
        String right = typed
                ? "§" + (found == 0 ? 'c' : 'a') + found + "§7/§f" + filled + " §b" + ItemList.percent(filled == 0 ? 0 : (double) found / filled)
                : hasHistory && w >= 150 ? "§e↑↓§7 earlier  §eEsc§7 close" : "§eEsc§7 close";
        int rw = ui.width(right);
        if (typed) ItemList.typing(ui, query, tx, ty, x + w - Modern.PAD - rw - 4 - tx, true, accent);
        else {
            ui.textShadow("Search items...", tx, ty, Modern.DIM);
            if ((ui.now / 500) % 2 == 0) ui.rect(tx - 2, ty - 1, 1, 9, accent);
        }
        ui.textShadow(right, x + w - Modern.PAD - rw, ty, Modern.LABEL);
        Modern.meter(ui, x + 1, y + BOX_H - 3, w - 2, Modern.METER, typed && filled > 0 ? (double) found / filled : 0, accent);
    }

    // keys while a container is open. false = eat the key
    public static boolean key(Screen s, int key, int modifiers) {
        if (!applies(s)) return true;
        boolean ctrl = (modifiers & InputConstants.MOD_CONTROL) != 0;
        if (ctrl && key == (optOn("inv_search.ctrlK") ? InputConstants.KEY_K : InputConstants.KEY_F)) {
            open = !open;
            if (open) histPos = -1;
            return false;
        }
        if (!open) return true;
        switch (key) {
            case InputConstants.KEY_ESCAPE -> {
                open = false;
                return false;
            }
            case InputConstants.KEY_BACKSPACE -> {
                if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
                return false;
            }
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                if (!query.isBlank() && !query.equals(history.peekFirst())) {
                    history.addFirst(query);
                    while (history.size() > 20) history.removeLast();
                }
                return false;
            }
            case InputConstants.KEY_UP, InputConstants.KEY_DOWN -> {
                if (history.isEmpty()) return false;
                histPos = Math.max(0, Math.min(history.size() - 1, histPos + (key == InputConstants.KEY_UP ? 1 : -1)));
                query = (String) history.toArray()[histPos];
                return false;
            }
            default -> {
                return key == InputConstants.KEY_LSHIFT || key == InputConstants.KEY_RSHIFT || key == InputConstants.KEY_LCONTROL;
            }
        }
    }

    // chars typed while the box is open. false = eat it
    public static boolean typed(Screen s, int codepoint) {
        if (!applies(s) || !open) return true;
        if (codepoint >= 32 && query.length() < 40) query += new String(Character.toChars(codepoint));
        return false;
    }
}
