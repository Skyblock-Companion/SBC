// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.ui.panels;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.mc.mixin.ContainerAccess;
import io.github.zmuzy.sbc.mc.mixin.SlotAccessor;
import io.github.zmuzy.sbc.ui.ItemList;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

// 4 slots next to the player in the inventory: necklace, cloak, belt, gloves. port of skyblocker
// SkyblockInventoryScreen minus its screen class, same spots (x 77, y 8 + 18 per slot)
public final class EquipmentSlots {
    private static final Ui ui = new Ui();
    private static final int OFFHAND = 45, OFFHAND_MOVE = 21, X = 77, Y = 8, STEP = 18, SIZE = 16;
    private static final Identifier SLOT = Identifier.withDefaultNamespace("container/slot");
    private static final Identifier[] EMPTY = {sprite("empty_necklace"), sprite("empty_cloak"), sprite("empty_belt"), sprite("empty_hand")};
    private static final String[] NAMES = {"Necklace", "Cloak", "Belt", "Gloves"};
    private static final String NOTE = "§8Last seen in your menus, not live";

    private static Slot moved;
    private static boolean placed;
    private static ItemStack hovered;

    private EquipmentSlots() {}

    private static Identifier sprite(String name) {
        return Identifier.fromNamespaceAndPath("sbcompanion", "equipment/" + name);
    }

    static void register() {
        Features.add(new Feature("equipment_slots", "Menus", "Equipment in the inventory", "Necklace, cloak, belt and gloves beside the player in your inventory, as your menus last showed them: open /equipment or a loadout and they catch up. Click a piece to open the menu. The offhand slot and the recipe book button move right to make room.", "Skyblocker: show equipment in inventory")
                .when(() -> GameState.inSkyBlock || io.github.zmuzy.sbc.SbcMod.DEV)
                .tooltip((st, lines) -> {
                    if (st == hovered) {
                        lines.add(Component.empty());
                        lines.add(Component.literal(NOTE));
                    }
                })
                .option(Option.bool("equipment_slots.statsPage", "Open /stats instead of /equipment", "What a click on a piece opens. In the Rift and the Safari it is always /stats: the Equipment Wardrobe is not there.", false))
                .option(Option.bool("equipment_slots.customizeButton", "Customize armor button", "A small paint brush by the player that starts /sbc dye in the chat. Needs Armor dye on.", true)));
    }

    // slots are there. for the mixin that moves the recipe book button + draws their backs
    public static boolean on() {
        return Features.on("equipment_slots");
    }

    private static boolean option(String key) {
        Feature f = Features.get("equipment_slots");
        Option o = f == null ? null : f.option(key);
        return o != null && o.bool();
    }

    // same menu while the player lives, so the slot goes back on close
    static void opened(InventoryScreen s) {
        if (!on() || moved != null) return;
        Slot slot = s.getMenu().slots.get(OFFHAND);
        ((SlotAccessor) slot).sbc$setX(slot.x + OFFHAND_MOVE);
        moved = slot;
    }

    static void closed() {
        if (moved == null) return;
        ((SlotAccessor) moved).sbc$setX(moved.x - OFFHAND_MOVE);
        moved = null;
    }

    // extractBackground slots: 3 above the old offhand spot + the offhands new one
    public static void background(GuiGraphicsExtractor g, ContainerAccess a) {
        if (!on()) return;
        int left = a.sbc$left(), top = a.sbc$top();
        for (int i = 0; i < 3; i++) g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, left + X - 1, top + Y - 1 + i * STEP, STEP, STEP);
        if (moved != null) g.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT, left + moved.x - 1, top + moved.y - 1, STEP, STEP);
    }

    public static void draw(Screen s, GuiGraphicsExtractor g, int mx, int my) {
        placed = false;
        hovered = null;
        if (!(s instanceof InventoryScreen inv) || !on()) return;
        ContainerAccess a = (ContainerAccess) s;
        ui.begin(g, mx, my);
        paint(ui, g, a.sbc$left(), a.sbc$top(), EquipmentCache.current(), inv.getMenu().getCarried().isEmpty() && a.sbc$hovered() == null);
        if (customize()) paintCustomize(ui, a.sbc$left(), a.sbc$top());
        placed = true;
        ui.flushTooltip();
    }

    private static boolean customize() {
        return option("customizeButton") && Features.on("armor_dye");
    }

    // pieces at this spot, tips = can the mouse ask about them
    public static void paint(Ui ui, GuiGraphicsExtractor g, int left, int top, ItemStack[] set, boolean tips) {
        for (int i = 0; i < 4; i++) {
            int x = left + X, y = top + Y + i * STEP;
            boolean hv = tips && ui.hover(x, y, SIZE, SIZE);
            ItemStack st = set[i];
            if (st.isEmpty()) g.blitSprite(RenderPipelines.GUI_TEXTURED, EMPTY[i], x, y, SIZE, SIZE);
            else ui.icon(st, x, y, 1f);
            if (!hv) continue;
            ui.rect(x, y, SIZE, SIZE, 0x80FFFFFF);
            String open = "§eClick: /" + command();
            if (st.isEmpty()) {
                ui.tooltip("§f" + NAMES[i], "§7Nothing, as far as your menus last showed.", NOTE, "", open);
            } else {
                hovered = st;
                g.setTooltipForNextFrame(Minecraft.getInstance().font, st, ui.mx, ui.my);
            }
        }
    }

    private static void paintCustomize(Ui ui, int left, int top) {
        int x = left + 63, y = top + 10, w = 10;
        boolean hv = ui.hover(x, y, w, w);
        ui.rect(x, y, w, w, hv ? Ui.alpha(Modern.AQUA, 0.45f) : 0x60000000);
        ui.icon("minecraft:brush", x, y, Modern.ICON_SCALE);
        if (hv) ui.tooltip("§fCustomize armor", "§7Starts §f/sbc dye §7in the chat");
    }

    // stays out of the equipment wardrobe where theres none
    private static String command() {
        return option("statsPage") || "THE_RIFT".equals(GameState.island) || EquipmentCache.inSafari() ? "stats" : "equipment";
    }

    // false = swallow the click
    static boolean click(Screen s, double x, double y) {
        if (!placed || !(s instanceof InventoryScreen)) return true;
        ContainerAccess a = (ContainerAccess) s;
        int left = a.sbc$left(), top = a.sbc$top();
        if (customize() && x >= left + 63 && y >= top + 10 && x < left + 73 && y < top + 20) {
            ItemList.pressTaken();
            Minecraft.getInstance().gui.setScreen(new net.minecraft.client.gui.screens.ChatScreen("/sbc dye ", false));
            return false;
        }
        if (x < left + X || x >= left + X + SIZE) return true;
        for (int i = 0; i < 4; i++) {
            int py = top + Y + i * STEP;
            if (y < py || y >= py + SIZE) continue;
            ItemList.pressTaken();
            Panels.send(command());
            return false;
        }
        return true;
    }

    // wiki + price keys over a piece
    static boolean key(AbstractContainerScreen<?> s, KeyEvent event) {
        return hovered != null && io.github.zmuzy.sbc.ui.UiFeatures.keyOver(s, hovered, event);
    }

    static List<String> names() {
        return List.of(NAMES);
    }
}
