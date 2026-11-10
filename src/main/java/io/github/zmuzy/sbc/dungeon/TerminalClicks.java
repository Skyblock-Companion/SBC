// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.dungeon;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.mc.Interactions;
import io.github.zmuzy.sbc.ui.Toasts;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

// blocks a wrong click in an f7 terminal: onClickSlot from skyblocker ColorTerminal, OrderTerminal,
// SameColorTerminal, StartsWithTerminal, LightsOnTerminal
public final class TerminalClicks {
    private static final Patterns.P ORDER = Patterns.p("Puzzles.ORDER"), STARTS = Patterns.p("Puzzles.STARTS"), COLOR = Patterns.p("Puzzles.COLOR"),
            PANES = Patterns.p("Puzzles.PANES"), SAME = Patterns.p("Puzzles.SAME");
    private static final String[] CYCLE = {"red", "orange", "yellow", "green", "blue"};
    // from skyblocker StartsWithTerminal.clickedSlotIds
    private static final Set<Integer> clicked = new HashSet<>();
    private static Object clickedIn;
    private static long toldAt;

    private TerminalClicks() {}

    private static String path(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    // from skyblocker OrderTerminal.onClickSlot. current = how many panes are done
    static boolean orderWrong(String path, boolean empty, int count, int current) {
        return !empty && (!path.equals("red_stained_glass_pane") || count != current + 1);
    }

    // from skyblocker SameColorTerminal.onClickSlot: forward wants left click, back wants right click
    static boolean sameWrong(Integer clicks, int button) {
        if (clicks == null) return false;
        if (clicks == 0) return true;
        return clicks > 0 ? button != 0 : button != 1;
    }

    private static int orderCurrent(List<ItemStack> items) {
        int[] ordered = Puzzles.orderSlots(items);
        if (ordered == null) return -1;
        int n = 0;
        while (n < ordered.length && ordered[n] >= 0 && ordered[n] < items.size() && path(items.get(ordered[n])).equals("lime_stained_glass_pane")) n++;
        return n;
    }

    // is this click wrong for the terminal with this title. nothing counts as wrong while the solver doesnt know
    static boolean wrong(AbstractContainerScreen<?> screen, String title, Slot slot, int button) {
        var menu = screen.getMenu();
        int size = Math.max(0, menu.slots.size() - 36);
        if (slot.index >= size || slot.container instanceof Inventory) return false;
        ItemStack st = slot.getItem();
        List<ItemStack> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) items.add(menu.slots.get(i).getItem());
        Matcher m;
        if (ORDER.matcher(title).matches() && Puzzles.solves("solve_order")) {
            int current = orderCurrent(items);
            return current >= 0 && orderWrong(path(st), st.isEmpty(), st.getCount(), current);
        }
        if ((m = STARTS.matcher(title)).matches() && Puzzles.solves("solve_starts")) {
            if (clickedIn != screen) {
                clickedIn = screen;
                clicked.clear();
            }
            if (clicked.contains(slot.index) || st.isEmpty() || !st.getHoverName().getString().startsWith(m.group(1))) return true;
            clicked.add(slot.index);
            return false;
        }
        if ((m = COLOR.matcher(title)).matches() && Puzzles.solves("solve_color")) {
            String target = Puzzles.colorFromName(m.group(1));
            return target != null && (st.hasFoil() || !target.equals(Puzzles.itemColor(path(st))));
        }
        if (PANES.matcher(title).matches() && Puzzles.solves("solve_panes")) return path(st).equals("lime_stained_glass_pane");
        if (SAME.matcher(title).matches() && Puzzles.solves("solve_same")) {
            Map<Integer, Integer> at = new HashMap<>();
            for (int i = 0; i < size; i++) {
                ItemStack it = items.get(i);
                if (it.isEmpty()) continue;
                String p = path(it);
                for (int c = 0; c < CYCLE.length; c++) if (p.equals(CYCLE[c] + "_stained_glass_pane")) at.put(i, c);
            }
            return sameWrong(Puzzles.sameColorClicks(at).get(slot.index), button);
        }
        return false;
    }

    private static boolean onClick(AbstractContainerScreen<?> screen, Slot slot, int slotId, int button) {
        if (slot == null || !DungeonRun.inDungeon() || !Features.on("dungeon_gui_terminals") || !DungeonMarks.opt("dungeon_gui_terminals", "block_wrong")) return false;
        String title = GameState.strip(screen.getTitle().getString()).trim();
        if (!wrong(screen, title, slot, button) || DungeonKeys.overrideDown()) return false;
        long now = System.currentTimeMillis();
        if (now - toldAt > 3000) {
            toldAt = now;
            Toasts.push("Wrong click stopped", "Hold " + DungeonKeys.overrideName() + " to click anyway", 0xFFFF5555);
        }
        return true;
    }

    public static void register() {
        Interactions.onSlotClick(TerminalClicks::onClick);
    }
}
