// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.ui.panels;

import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.value.EstimatedItemValue;
import dev.zmuzy.sbc.mc.mixin.ContainerAccess;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// loadouts menu + wardrobe value. ports skyhanni features/inventory/loadout (which one is on, slot numbers,
// highlight) and features/inventory/wardrobe
public final class Loadouts {
    private static final Pattern LOADOUTS = Patterns.get("SH.inventory.loadout.inventory.name");
    private static final Pattern ONE_PAGE = Pattern.compile("^Loadouts$");
    private static final Pattern UNEQUIPPED = Patterns.get("SH.inventory.loadout.unequippedslot");
    private static final Pattern UNUSED = Patterns.get("SH.inventory.loadout.unusedslot");
    private static final Pattern LOCKED = Patterns.get("SH.inventory.loadout.lockedslot");
    private static final Pattern ARMOR_SETS = Patterns.get("SH.inventory.wardrobe.armor.name");
    private static final Pattern EQUIPMENT_SETS = Patterns.get("SH.inventory.wardrobe.equipment.name");
    // from skyhanni LoadoutApi: icons start at 14, 3 per row. pages hold 4, 4 and 1 rows
    public static final int[] ICONS = {14, 15, 16, 23, 24, 25, 32, 33, 34, 41, 42, 43};
    private static final int[] FIRST_OF_PAGE = {0, 12, 24};
    // from skyhanni AbstractWardrobeApi: a slots button is FIRST_SLOT + its column, pieces above
    private static final int FIRST_SLOT = 36, MAX_SLOT_PER_PAGE = 9;

    private static int page = 1, currentSlot = -1, wait;

    private Loadouts() {}

    static void register() {
        Features.add(new Feature("loadout_highlight", "Menus", "Loadout highlighting", "In the Loadouts menu, the loadout you are wearing stands out, and your favourites if you want them to.", "SkyHanni: loadout highlighting")
                .when(() -> GameState.inSkyBlock || dev.zmuzy.sbc.SbcMod.DEV)
                .option(Option.bool("loadout_highlight.currentlyEquipped", "Currently equipped", "Highlight the loadout you are wearing.", true))
                .option(Option.color("loadout_highlight.equippedColor", "Currently equipped colour", "", 0xC855FF55))
                .option(Option.bool("loadout_highlight.favorites", "Favourites", "Highlight your favourite loadouts. Middle click a loadout in the menu to mark it.", false))
                .option(Option.color("loadout_highlight.favoriteColor", "Favourite colour", "", 0xC8FFFF55)));
        Features.add(new Feature("wardrobe_value", "Menus", "Estimated value of a set", "On the button of a set in the Armor Sets and Equipment Sets menus: what each piece is worth, and the total.", "SkyHanni: estimated wardrobe price")
                .when(() -> GameState.inSkyBlock || dev.zmuzy.sbc.SbcMod.DEV)
                .tooltip(Loadouts::valueTooltip));
    }

    static boolean isLoadouts(String title) {
        return LOADOUTS.matcher(title).matches() || ONE_PAGE.matcher(title).matches();
    }

    private static int pageOf(String title) {
        Matcher m = LOADOUTS.matcher(title);
        return m.matches() ? Integer.parseInt(m.group("currentPage")) : 1;
    }

    private static String path(ItemStack st) {
        return BuiltInRegistries.ITEM.getKey(st.getItem()).getPath();
    }

    // from skyhanni isCurrentSelectedLoadout: the one that doesnt offer to equip is the one on
    public static boolean isCurrent(ItemStack icon, List<String> plainLore) {
        if (icon.isEmpty()) return false;
        if (LOCKED.matcher(GameState.strip(icon.getHoverName().getString())).matches()) return false;
        // menu edge or empty page buttons arent loadouts
        String kind = path(icon);
        if (kind.endsWith("glass_pane") || kind.equals("gray_dye") || kind.equals("red_dye")) return false;
        for (String line : plainLore) if (UNEQUIPPED.matcher(line).find() || UNUSED.matcher(line).find()) return false;
        return true;
    }

    private static List<String> plainLore(ItemStack st) {
        List<String> out = new ArrayList<>();
        for (String l : LegacyText.lore(st)) out.add(GameState.strip(l));
        return out;
    }

    // few times a sec the icons say which loadout is on
    static void tick(Minecraft mc) {
        if (--wait > 0) return;
        wait = 5;
        if (!(mc.gui.screen() instanceof ContainerScreen cs)) return;
        String title = GameState.strip(cs.getTitle().getString()).trim();
        if (!isLoadouts(title)) return;
        page = pageOf(title);
        ChestMenu menu = cs.getMenu();
        int found = -1, count = page >= 3 ? 3 : ICONS.length;
        for (int k = 0; k < count; k++) {
            int index = ICONS[k];
            if (index >= menu.getRowCount() * 9) break;
            ItemStack icon = menu.getSlot(index).getItem();
            if (isCurrent(icon, plainLore(icon))) {
                found = index;
                break;
            }
        }
        currentSlot = found;
    }

    // loadout number over all pages, -1 = not one
    private static int loadoutNumber(int slotIndex) {
        for (int i = 0; i < ICONS.length; i++) if (ICONS[i] == slotIndex) return FIRST_OF_PAGE[Math.max(0, Math.min(page - 1, 2))] + i;
        return -1;
    }

    private static Option option(String key) {
        Feature f = Features.get("loadout_highlight");
        return f == null ? null : f.option(key);
    }

    // from skyhanni LoadoutSlotHighlight: worn first, then favourite
    static int slotColor(Slot slot) {
        if (!Features.on("loadout_highlight") || slot.container instanceof net.minecraft.world.entity.player.Inventory) return 0;
        int index = slot.index;
        if (index >= 54) return 0;
        Option current = option("currentlyEquipped");
        if (index == currentSlot && current != null && current.bool()) return option("equippedColor").color();
        Option favourites = option("favorites");
        if (favourites != null && favourites.bool() && !slot.getItem().isEmpty() && PanelStore.data().loadoutFavourites.contains(loadoutNumber(index))) return option("favoriteColor").color();
        return 0;
    }

    // middle click a loadout = favourite (when favourites are on), nothing goes to the server. false = swallow
    static boolean click(Screen s, MouseButtonEvent e) {
        if (e.button() != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || !(s instanceof ContainerScreen cs) || !isLoadouts(GameState.strip(cs.getTitle().getString()).trim())) return true;
        Option favourites = option("favorites");
        if (!Features.on("loadout_highlight") || favourites == null || !favourites.bool()) return true;
        Slot hovered = ((ContainerAccess) cs).sbc$hovered();
        if (hovered == null) return true;
        int n = loadoutNumber(hovered.index);
        if (n < 0 || hovered.getItem().isEmpty()) return true;
        var set = PanelStore.data().loadoutFavourites;
        if (!set.remove(n)) set.add(n);
        PanelStore.save();
        return false;
    }

    // --- what a set is worth ---

    // from skyhanni EstimatedItemValueCalculator.getTotalPrice for one piece, 0 = cant say
    private static double worth(ItemStack st) {
        String id = Gain.sbId(st);
        if (id.isEmpty()) return 0;
        var data = st.get(DataComponents.CUSTOM_DATA);
        if (data != null) {
            try {
                boolean dungeon = false;
                for (String line : LegacyText.lore(st)) if (GameState.strip(line).contains("DUNGEON ")) dungeon = true;
                var r = EstimatedItemValue.calculate(data.copyTag(), id, dungeon, LegacyText.lore(st));
                if (r != null && r.totalPrice() > 0) return r.totalPrice();
            } catch (Exception ignored) {
            }
        }
        double market = Gain.ofStack(st);
        return Math.max(0, market);
    }

    // from skyhanni createPriceLore: the column of pieces above this button
    public static List<String> priceLore(String valueName, List<ItemStack> pieces) {
        List<String> out = new ArrayList<>();
        boolean empty = true;
        for (ItemStack p : pieces) if (piece(p)) empty = false;
        if (empty) return out;
        out.add("§aEstimated " + valueName + " Value:");
        double total = 0;
        for (ItemStack st : pieces) {
            if (!piece(st) || Gain.sbId(st).isEmpty()) continue;
            double price = worth(st);
            if (price > 0) {
                out.add("  §7- " + LegacyText.name(st) + ": §6" + Prices.coins(price));
                total += price;
            }
        }
        if (total != 0) out.add(" §aTotal Value: §6§l" + Prices.coins(total) + " coins");
        return out;
    }

    private static boolean piece(ItemStack st) {
        return !st.isEmpty() && !path(st).endsWith("glass_pane");
    }

    private static void valueTooltip(ItemStack stack, List<Component> lines) {
        if (!(Minecraft.getInstance().gui.screen() instanceof ContainerScreen cs)) return;
        String title = GameState.strip(cs.getTitle().getString()).trim();
        String name = ARMOR_SETS.matcher(title).matches() ? "Armor" : EQUIPMENT_SETS.matcher(title).matches() ? "Equipment" : null;
        if (name == null) return;
        Slot hovered = ((ContainerAccess) cs).sbc$hovered();
        if (hovered == null || hovered.getItem() != stack || hovered.index < FIRST_SLOT || hovered.index >= FIRST_SLOT + MAX_SLOT_PER_PAGE) return;
        int column = hovered.index - FIRST_SLOT;
        List<ItemStack> pieces = new ArrayList<>(4);
        for (int row = 0; row < 4; row++) pieces.add(cs.getMenu().getSlot(row * 9 + column).getItem());
        List<String> lore = priceLore(name, pieces);
        if (lore.isEmpty()) return;
        lines.add(Component.empty());
        for (String l : lore) lines.add(Component.literal(l));
    }
}
