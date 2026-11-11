// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.ui.panels;

import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemInfo;
import io.muzy.sbc.core.LegacyItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

// the 25 garden plots, learned from configure plots when it closes: name + icon each. port of skyblocker
// GardenPlots with GardenPlotsWidgets plot number table
public final class GardenPlots {
    // icon = a game item id when sbId is empty, like skyblockers Either of Item or String
    public static final class Plot {
        public String icon = "", sbId = "", name = "";
        // skyblock id you picked, null = none
        public String customIcon;
    }

    // from skyblocker GARDEN_PLOT_TO_SLOT: number in the tab Plots line -> spot in the grid
    static final int[] PLOT_TO_SLOT = {12, 7, 11, 13, 17, 6, 8, 16, 18, 2, 10, 14, 22, 1, 3, 5, 9, 15, 19, 21, 23, 0, 4, 20, 24};
    // grid middle
    static final int BARN = 12;
    // from skyblocker CUSTOM_ICON_OPTIONS in hypixel ids: crops, null = none
    static final String[] CUSTOM_ICONS = {null, "WHEAT", "CARROT_ITEM", "POTATO_ITEM", "SUGAR_CANE", "DOUBLE_PLANT", "MOONFLOWER", "WILD_ROSE", "NETHER_STALK", "RED_MUSHROOM", "CACTUS", "MELON", "PUMPKIN", "INK_SACK:3"};

    private static final ItemStack[] stacks = new ItemStack[25];
    private static boolean stale = true;

    private GardenPlots() {}

    private static String path(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    // reads the open configure plots menu, slotAt = chest slot item
    public static void read(IntFunction<ItemStack> slotAt) {
        Plot[] plots = PanelStore.data().plots;
        boolean any = false;
        for (int row = 0; row < 5; row++) {
            for (int i = row * 9 + 2; i < row * 9 + 7; i++) {
                if (i == 22) continue; // barn icon
                ItemStack stack = slotAt.apply(i);
                String kind = stack.isEmpty() ? "" : path(stack);
                if (stack.isEmpty() || kind.equals("red_stained_glass_pane") || kind.equals("oak_button") || kind.equals("black_stained_glass_pane")) continue;
                // other mods color the plot names when they change icons
                String name = ChatFormatting.stripFormatting(stack.getHoverName().getString());
                String[] parts = name.split("-", 2);
                if (parts.length < 2) continue;
                int index = (i / 9) * 5 + (i % 9 - 2);
                Plot plot = plots[index] == null ? new Plot() : plots[index];
                String sb = Gain.sbId(stack);
                plot.sbId = sb;
                plot.icon = sb.isBlank() ? BuiltInRegistries.ITEM.getKey(stack.getItem()).toString() : "";
                plot.name = parts[1].trim();
                plots[index] = plot;
                any = true;
            }
        }
        if (any) {
            changed();
            PanelStore.save();
        }
    }

    // from skyhanni PlotIconConfig.hardReset: every plot back to what the menu shows
    public static void clearCustomIcons() {
        for (Plot p : PanelStore.data().plots) if (p != null) p.customIcon = null;
        changed();
        PanelStore.save();
    }

    // look the icon up again
    static void changed() {
        stale = true;
    }

    // custom icon first, then what the menu showed. barn is always a lodestone
    static ItemStack[] stacks() {
        if (!stale) return stacks;
        stale = false;
        Plot[] plots = PanelStore.data().plots;
        for (int i = 0; i < 25; i++) {
            Plot p = plots[i];
            if (p == null) stacks[i] = null;
            else if (p.customIcon != null) stacks[i] = ItemInfo.stack(p.customIcon);
            else stacks[i] = p.sbId.isBlank() ? LegacyItems.stack(p.icon) : ItemInfo.stack(p.sbId);
        }
        stacks[BARN] = LegacyItems.stack("minecraft:lodestone");
        return stacks;
    }

    static ItemStack customIcon(int option) {
        String id = CUSTOM_ICONS[option];
        return id == null ? LegacyItems.stack("minecraft:barrier") : ItemInfo.stack(id);
    }

    static void setCustomIcon(int plot, String id) {
        Plot p = PanelStore.data().plots[plot];
        if (p == null) return;
        p.customIcon = id;
        changed();
        PanelStore.save();
    }

    // what /plottp wants: barn by that word, a plot by name. null = not learned yet
    static String command(int plot) {
        if (plot == BARN) return "plottp barn";
        Plot p = PanelStore.data().plots[plot];
        return p == null ? null : "plottp " + p.name;
    }

    // /plottp by the tab number, no slash
    public static String commandForNumber(int number) {
        String by = number >= 0 && number < PLOT_TO_SLOT.length ? command(PLOT_TO_SLOT[number]) : null;
        return by != null ? by : "plottp " + number;
    }

    // sets what the menu would have taught, for pictures
    public static void preview(Plot[] plots) {
        PanelStore.useForTests(new PanelStore.Data());
        System.arraycopy(plots, 0, PanelStore.data().plots, 0, 25);
        changed();
    }

    // from skyblocker updateInfestedFromTab: grid spots the tabs "Plots: 1, 2" line names
    static List<Integer> infested(List<String> tab) {
        List<Integer> out = new ArrayList<>(8);
        for (String line : tab) {
            String string = GameState.strip(line).trim();
            if (!string.startsWith("Plots:")) continue;
            String[] split = string.split(":", 2);
            if (split.length < 2) break;
            for (String s : split[1].split(",")) {
                try {
                    int n = Integer.parseInt(s.strip());
                    if (n >= 0 && n < PLOT_TO_SLOT.length) out.add(PLOT_TO_SLOT[n]);
                } catch (NumberFormatException ignored) {
                }
            }
            break;
        }
        return out;
    }
}
