// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.ui.panels;

import com.mojang.blaze3d.platform.InputConstants;
import io.muzy.sbc.core.Config;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.mc.mixin.ContainerAccess;
import io.muzy.sbc.ui.ContainerValue;
import io.muzy.sbc.ui.ItemList;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.List;

// garden plots panel right of the inventory in the garden: 25 plots 5x5 with real icons, click = /plottp,
// infested ones flash red, 3 buttons, right click picks an icon
public final class GardenPlotsPanel {
    private static final Ui ui = new Ui();
    private static final int ACCENT = 0xFF8CDB6A, CELL = 18, GRID_X = 7, GRID_Y = Modern.BAND + 3, BUTTON = 18, SPACING = 4, TAB_H = 16;
    // same width as their widget. height = band + grid + buttons
    public static final int W = 104, H = GRID_Y + 5 * CELL + 3 + BUTTON + 3;
    private static final String FOLDED = "garden_plots.folded";

    private static boolean placed, onLeft, dragging;
    private static int px, py, pw, ph, editing = -1, grabX, grabY;
    private static long tabAt;
    private static List<Integer> infested = List.of();
    private static String bed = "minecraft:red_bed";

    private GardenPlotsPanel() {}

    static void register() {
        Features.add(new Feature("garden_plots", "Garden", "Garden Plots panel", "Beside your inventory in the Garden: all 25 plots with their icons. Click one to teleport to it, right click to pick a custom icon. Plots with pests flash red. Buttons for the Desk, the Garden spawn and setting the spawn. Drag it by its title; Shift + right click on the title puts it back.", "Skyblocker: garden plots widget")
                .when(() -> "GARDEN".equals(GameState.island) || io.muzy.sbc.SbcMod.DEV)
                .option(Option.bool("garden_plots.closeOnTeleport", "Close the inventory on teleport", "Closes your inventory when a plot in the panel is clicked.", false))
                .option(Option.integer("garden_plots.x", "Panel X", "How far from the inventory it stands; dragging it sets this.", 0, 0, 800))
                .option(Option.integer("garden_plots.y", "Panel Y", "How far below the inventory's top edge it stands; dragging it sets this.", 0, -400, 400)));
    }

    static boolean on(Screen s) {
        return s instanceof InventoryScreen && Features.on("garden_plots");
    }

    // in the garden the effects next to the inventory make room for the panel, like skyblocker
    public static boolean hidesEffects() {
        return Features.on("garden_plots");
    }

    private static Option option(String key) {
        Feature f = Features.get("garden_plots");
        return f == null ? null : f.option(key);
    }

    private static int optionInt(String key) {
        Option o = option(key);
        return o == null ? 0 : o.integer();
    }

    private static boolean folded() {
        return Boolean.TRUE.equals(Config.I.options.get(FOLDED));
    }

    private static void setFolded(boolean v) {
        if (v) Config.I.options.put(FOLDED, true);
        else Config.I.options.remove(FOLDED);
        Config.save();
    }

    // where the open panel goes: x, y, 0 right / 1 left. null = no room either side. right is home unless the
    // open item list is there
    public static int[] place(int left, int top, int cw, int sw, int sh, char list, int offX, int offY) {
        int rightX = left + cw + SPACING + Math.max(0, offX), leftX = left - SPACING - W - Math.max(0, offX);
        boolean rightFree = list != 'R' && rightX + W <= sw - 2, leftFree = list != 'L' && leftX >= 2;
        if (!rightFree && !leftFree) {
            // leftover from dragging, not worth folding for
            rightX = left + cw + SPACING;
            leftX = left - SPACING - W;
            rightFree = list != 'R' && rightX + W <= sw - 2;
            leftFree = list != 'L' && leftX >= 2;
            if (!rightFree && !leftFree) return null;
        }
        boolean right = rightFree;
        int y = top + offY;
        if (offY == 0 && list == (right ? 'r' : 'l')) y += TAB_H + 2;
        y = Math.max(2, Math.min(y, sh - H - 2));
        return new int[]{right ? rightX : leftX, y, right ? 0 : 1};
    }

    public static void draw(Screen s, GuiGraphicsExtractor g, int mx, int my) {
        placed = false;
        if (!on(s)) {
            editing = -1;
            dragging = false;
            return;
        }
        ContainerAccess a = (ContainerAccess) s;
        int left = a.sbc$left(), top = a.sbc$top(), cw = a.sbc$width();
        long now = System.currentTimeMillis();
        if (now - tabAt > 3000) {
            tabAt = now;
            infested = GardenPlots.infested(GameState.tab);
        }
        ui.begin(g, mx, my);
        if (dragging) follow(s, left, top, cw, mx, my);
        int[] at = folded() ? null : place(left, top, cw, s.width, s.height, ItemList.standing(left, cw, s.width), optionInt("x"), optionInt("y"));
        if (at == null) {
            paintTab(ui, left, top, cw, s.height, ItemList.standing(left, cw, s.width), ContainerValue.hudBoxes(s));
        } else {
            onLeft = at[2] == 1;
            paint(ui, at[0], at[1], now, infested);
            if (mx > px + 4 && my > py + 4 && mx < px + pw - 16 && my < py + Modern.BAND) g.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_ALL);
        }
        placed = true;
        ui.flushTooltip();
    }

    // held left button moves it, let go = saved
    private static void follow(Screen s, int left, int top, int cw, int mx, int my) {
        Option ox = option("x"), oy = option("y");
        if (ox == null || oy == null) {
            dragging = false;
            return;
        }
        if (!io.muzy.sbc.mc.KeyHeld.mouseDown(InputConstants.MOUSE_BUTTON_LEFT)) {
            dragging = false;
            Config.save();
            return;
        }
        int nx = onLeft ? left - SPACING - W - (mx - grabX) : mx - grabX - (left + cw + SPACING);
        ox.set(Math.max(0, nx));
        oy.set(my - grabY - top);
    }

    // closed tab on the side the item list isnt on
    public static void paintTab(Ui ui, int left, int top, int cw, int sh, char list, List<int[]> hud) {
        String label = "GARDEN PLOTS";
        int w = ui.width("‹ " + label) + 10;
        boolean right = list != 'R' && list != 'r';
        int x = right ? left + cw + 4 : Math.max(2, left - 4 - w);
        int y = ItemList.tabY(x, w, top, sh, hud);
        px = x;
        py = y;
        pw = w;
        ph = TAB_H;
        boolean hv = ui.hover(px, py, pw, ph);
        Theme.current().panel(ui, px, py, pw, ph);
        if (hv) ui.rect(px + 1, py + 1, pw - 2, ph - 2, Ui.alpha(ACCENT, 0.2f));
        ui.gradH(px + 2, py + ph - 2, pw - 3, 1, Ui.alpha(ACCENT, 0.08f), Ui.alpha(ACCENT, hv ? 1f : 0.7f));
        ui.textShadow("‹", px + 5, py + 4, ACCENT);
        ui.textShadow(label, px + 5 + ui.width("‹ "), py + 4, hv ? 0xFFFFFFFF : Modern.LABEL);
        if (hv) ui.tooltip("§fGarden Plots", "§7Teleport to any plot", "", "§eClick to open");
        ui.click(px, py, pw, ph, () -> setFolded(false));
    }

    private static boolean pane(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath().endsWith("glass_pane");
    }

    private static void border(Ui ui, int x, int y, int w, int h, int c) {
        ui.rect(x, y, w, 1, c);
        ui.rect(x, y + h - 1, w, 1, c);
        ui.rect(x, y + 1, 1, h - 2, c);
        ui.rect(x + w - 1, y + 1, 1, h - 2, c);
    }

    // open panel here. now + infested drive the red flash
    public static void paint(Ui ui, int x, int y, long now, List<Integer> infested) {
        px = x;
        py = y;
        pw = W;
        ph = H;
        Theme.current().panel(ui, x, y, W, H);
        boolean choosing = editing >= 0;
        String right = choosing || infested.isEmpty() ? "" : "§c" + infested.size() + (infested.size() == 1 ? " §7plot" : " §7plots");
        Modern.band(ui, choosing ? "Custom Icon" : "Garden Plots", LegacyItems.stack("minecraft:lodestone"), "", ACCENT, x, y, W);
        if (!right.isEmpty()) ui.textShadow(right, x + W - 16 - ui.width(right), y + 3, Modern.INK);
        // arrow folds it. while picking an icon it goes back without changing anything
        boolean closeHover = ui.hover(x + W - 15, y + 1, 14, 12);
        if (closeHover) {
            ui.rect(x + W - 15, y + 1, 14, 12, Ui.alpha(ACCENT, 0.35f));
            ui.tooltip(choosing ? "§7Back, keeping the icon" : "§7Fold to a tab");
        }
        ui.textShadow(onLeft ? "‹" : "›", x + W - 10, y + 3, closeHover ? 0xFFFFFFFF : Modern.LABEL);
        ui.click(x + W - 15, y + 1, 14, 12, () -> {
            if (editing >= 0) editing = -1;
            else setFolded(true);
        });

        ItemStack[] stacks = GardenPlots.stacks();
        int gx = x + GRID_X, gy = y + GRID_Y, count = choosing ? GardenPlots.CUSTOM_ICONS.length : 25;
        boolean flash = (now & 512) != 0;
        for (int i = 0; i < count; i++) {
            int cx = gx + (i % 5) * CELL, cy = gy + (i / 5) * CELL, plot = i;
            boolean hovered = ui.hover(cx, cy, CELL, CELL), sick = !choosing && infested.contains(i);
            ItemStack item = choosing ? GardenPlots.customIcon(i) : stacks[i];
            ui.rect(cx, cy, CELL - 1, CELL - 1, hovered ? Ui.alpha(ACCENT, 0.3f) : 0x48000000);
            ui.rect(cx, cy, CELL - 1, 1, 0x18FFFFFF);
            // hover + pest edge show on plots we dont know yet too
            if (item != null && !item.isEmpty()) {
                if (hovered && !pane(item)) ui.icon(item, cx, cy, 1.125f);
                else ui.icon(item, cx, cy, 1f);
            }
            if (sick && flash) border(ui, cx, cy, CELL - 1, CELL - 1, 0xFFFF0000);
            if (choosing) {
                if (hovered) ui.tooltip("§f" + iconName(i));
                ui.click(cx, cy, CELL, CELL, () -> {
                    GardenPlots.setCustomIcon(editing, GardenPlots.CUSTOM_ICONS[plot]);
                    editing = -1;
                });
                continue;
            }
            if (item == null) {
                if (hovered) ui.tooltip("§7Not learned yet", "§7Open the §fDesk §7and §fConfigure Plots §7once.");
                continue;
            }
            if (hovered) tip(ui, i, sick);
            ui.clickRight(cx, cy, CELL, CELL, () -> teleport(plot), () -> {
                if (plot != GardenPlots.BARN) editing = plot;
            });
        }

        int by = y + H - BUTTON - 3;
        button(ui, "minecraft:book", gx, by, "Open Desk", "desk");
        button(ui, "minecraft:ender_eye", x + W - GRID_X - 2 * BUTTON - 2, by, "TP to Garden spawn", "warp garden");
        button(ui, bed, x + W - GRID_X - BUTTON, by, "Set Garden spawn", "setspawn");
    }

    private static String iconName(int option) {
        String id = GardenPlots.CUSTOM_ICONS[option];
        return id == null ? "None" : GameState.strip(io.muzy.sbc.core.ItemRepo.name(id));
    }

    private static void tip(Ui ui, int plot, boolean sick) {
        String name = plot == GardenPlots.BARN ? "§fThe Barn" : "§7Plot - §a§l" + PanelStore.data().plots[plot].name;
        if (sick) ui.tooltip(name, "§c§lGROSS! This plot has pests!", "", "§e§lClick to TP to it!", "§7Right click: custom icon");
        else if (plot == GardenPlots.BARN) ui.tooltip(name, "", "§e§lClick to TP to it!");
        else ui.tooltip(name, "", "§e§lClick to TP to it!", "§7Right click: custom icon");
    }

    private static void button(Ui ui, String icon, int x, int y, String label, String command) {
        boolean hv = ui.hover(x, y, BUTTON, BUTTON);
        ui.rect(x, y, BUTTON, BUTTON, hv ? Ui.alpha(ACCENT, 0.3f) : 0x48000000);
        ui.rect(x, y + BUTTON - 1, BUTTON, 1, hv ? ACCENT : Ui.alpha(ACCENT, 0.5f));
        ui.icon(icon, x + 1, y + 1, 1f);
        if (hv) ui.tooltip("§f" + label, "§7/" + command);
        ui.click(x, y, BUTTON, BUTTON, () -> Panels.send(command));
    }

    private static void teleport(int plot) {
        String command = GardenPlots.command(plot);
        if (command == null) return;
        Option close = option("closeOnTeleport");
        Screen s = Minecraft.getInstance().gui.screen();
        if (close != null && close.bool() && s != null) s.onClose();
        Panels.send(command);
    }

    // false = swallow the click, clicking the panel must not throw your held item
    static boolean click(Screen s, double x, double y, int button) {
        if (!placed || !on(s) || x < px || y < py || x >= px + pw || y >= py + ph) return true;
        ItemList.pressTaken();
        boolean open = ph > TAB_H;
        if (open && y < py + Modern.BAND && x < px + pw - 15) {
            if (button == 1 && Panels.shiftDown()) {
                Option ox = option("x"), oy = option("y");
                if (ox != null) ox.reset();
                if (oy != null) oy.reset();
                Config.save();
            } else if (button == 0) {
                dragging = true;
                grabX = (int) x - px;
                grabY = (int) y - py;
            }
            return false;
        }
        for (int i = ui.hits.size() - 1; i >= 0; i--) {
            Ui.Hit hit = ui.hits.get(i);
            if (x < hit.x() || y < hit.y() || x >= hit.x() + hit.w() || y >= hit.y() + hit.h()) continue;
            Runnable r = button == 0 ? hit.click() : button == 1 ? hit.right() : null;
            if (r != null) r.run();
            break;
        }
        return false;
    }

    // every inventory open, their 1 in 1000 pink bed too lol
    static void opened() {
        bed = Math.random() < 0.001 ? "minecraft:pink_bed" : "minecraft:red_bed";
    }

    public static void previewEditing(int plot) {
        editing = plot;
    }

}
