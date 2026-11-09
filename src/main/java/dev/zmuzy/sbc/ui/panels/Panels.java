// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui.panels;

import com.mojang.blaze3d.platform.InputConstants;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Safe;
import dev.zmuzy.sbc.feature.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

// panels + helpers next to the menus, and the one spot the game events reach them from so shared files only
// need one call each
public final class Panels {
    // clean title of the open menu, set on open so slot drawing doesnt ask every frame
    private static String title = "";
    private static boolean loadouts;

    private Panels() {}

    static boolean on(String feature) {
        return Features.on(feature);
    }

    // sends a command from a button press, no slash
    static void send(String command) {
        var conn = Minecraft.getInstance().getConnection();
        if (conn != null) conn.sendCommand(command);
    }

    static boolean shiftDown() {
        return InputConstants.isKeyDown(InputConstants.KEY_LSHIFT) || InputConstants.isKeyDown(InputConstants.KEY_RSHIFT);
    }

    public static void register() {
        GardenPlotsPanel.register();
        EquipmentSlots.register();
        MuseumPanel.register();
        Loadouts.register();
        Features.add(new dev.zmuzy.sbc.feature.Feature("fancy_crafting_table", "Menus", "Crafting table for Craft Item", "The Craft Item menu looks like a plain crafting table with your inventory under it, the three quick crafts at the side. It replaces Hypixel's chest, so it starts off.", "Skyblocker: fancy crafting table")
                .defaultOff().when(() -> GameState.inSkyBlock || dev.zmuzy.sbc.SbcMod.DEV));
        var keys = net.minecraft.client.KeyMapping.Category.register(net.minecraft.resources.Identifier.fromNamespaceAndPath("sbcompanion", "loadouts"));
        dev.zmuzy.sbc.item.WardrobeKeys.registerKeys(keys);
        dev.zmuzy.sbc.dungeon.DungeonKeys.registerKeys(keys);
        CustomWardrobe.register(keys);
        Safe.endTick("Panels.tick", Panels::tick);
        Safe.stopping("Panels.stopping", c -> {
            PanelStore.save();
            EquipmentCache.save(c);
        });
        Safe.afterInit("Panels.afterInit", (client, screen, w, h) -> {
            if (screen instanceof InventoryScreen inv && (GameState.inSkyBlock || dev.zmuzy.sbc.SbcMod.DEV)) {
                EquipmentSlots.opened(inv);
                GardenPlotsPanel.opened();
                Safe.remove("Panels.remove", screen, s -> EquipmentSlots.closed());
            }
            if (screen instanceof AbstractContainerScreen<?> cs) {
                MuseumPanel.notShown();
                title = GameState.strip(cs.getTitle().getString()).trim();
                loadouts = Loadouts.isLoadouts(title);
            }
            if (screen instanceof ContainerScreen cs && title.equals("Configure Plots")) {
                ChestMenu menu = cs.getMenu();
                Safe.remove("Panels.remove#2", screen, s -> GardenPlots.read(i -> menu.getSlot(i).getItem()));
            }
        });
        dev.zmuzy.sbc.mc.Interactions.onSlotClick((screen, slot, slotId, button) -> {
            if (slot == null) return false;
            String t = GameState.strip(screen.getTitle().getString()).trim();
            // from skyblocker MuseumItemCache: donation confirm button
            if (t.equals("Confirm Donation") && slotId == 20 && screen.getMenu() instanceof ChestMenu menu) MuseumData.confirmed(i -> menu.getSlot(i).getItem());
            EquipmentCache.clicked(t, slotId, slot.getItem());
            return false;
        });
        Safe.commands("Panels.commands", (dispatcher, registry) -> dispatcher.register(literal("sbc")
                .then(literal("museum").executes(c -> {
                    c.getSource().sendFeedback(Component.literal("§7/sbc museum resync reads your Museum again."));
                    return 1;
                }).then(literal("resync").executes(c -> {
                    MuseumData.tryResync(s -> Minecraft.getInstance().execute(() -> c.getSource().sendFeedback(Component.literal("§b[SBC] " + s))));
                    return 1;
                })))));
    }

    private static void tick(Minecraft mc) {
        if (!GameState.inSkyBlock && !dev.zmuzy.sbc.SbcMod.DEV) return;
        PanelStore.tick(mc);
        EquipmentCache.tick(mc);
        Loadouts.tick(mc);
    }

    public static void draw(Screen s, GuiGraphicsExtractor g, int mx, int my) {
        GardenPlotsPanel.draw(s, g, mx, my);
        EquipmentSlots.draw(s, g, mx, my);
    }

    // false = swallow the click
    public static boolean click(Screen s, MouseButtonEvent e) {
        MuseumPanel.clicked();
        if (!dev.zmuzy.sbc.item.WardrobeKeys.mouse(s, e) || !Loadouts.click(s, e)) {
            dev.zmuzy.sbc.ui.ItemList.pressTaken();
            return false;
        }
        return GardenPlotsPanel.click(s, e.x(), e.y(), dev.zmuzy.sbc.mc.KeyHeld.clickButton(e.button())) && EquipmentSlots.click(s, e.x(), e.y());
    }

    // false = swallow the key
    public static boolean key(AbstractContainerScreen<?> s, KeyEvent event) {
        if (!MuseumPanel.key(s, event)) return false;
        return !EquipmentSlots.key(s, event);
    }

    public static boolean typed(Screen s, int codepoint) {
        return MuseumPanel.typed(codepoint);
    }

    // color behind a slot item, 0 = none. asked every frame for every slot so most menus bail at the first check
    public static int slotColor(Slot slot) {
        if (loadouts) {
            int c = Loadouts.slotColor(slot);
            if (c != 0) return c;
        }
        return title.contains("Museum") ? MuseumPanel.slotColor(slot, title) : 0;
    }
}
