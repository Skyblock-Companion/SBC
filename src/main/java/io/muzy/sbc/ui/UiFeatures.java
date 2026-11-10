// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.ui;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemInfo;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.mc.Interactions;
import io.muzy.sbc.mc.mixin.ContainerAccess;
import io.muzy.sbc.web.WebPage;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

// small inventory stuff: potion list hidden next to the inventory, wiki + price lookup keys over a hovered
// item (skyblocker F1 / F6), bazaar / ah search
public final class UiFeatures {
    public static KeyMapping wikiKey, priceKey;

    private UiFeatures() {}

    public static void register() {
        Features.add(new Feature("hide_potion_effects", "Items", "Hide potion effects", "The potion effect list beside your inventory and the effect icons at the top of the screen are hidden on SkyBlock (the tab list and the SkyBlock Menu show them).", "Skyblocker: hide status effect overlay")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV));
        Features.add(new Feature("wiki_lookup", "Items", "Wiki lookup key", "Press the Wiki key (F1) over an item to open its wiki page in the in-game browser.", "Skyblocker: wiki lookup")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV));
        Features.add(new Feature("price_lookup", "Items", "Item price lookup key", "Press the Price key (F6) over an item to search it in the Bazaar (/bz) or the Auction House (/auctionsearch).", "Skyblocker: item price lookup")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV));
        Interactions.onSlotClick((screen, slot, slotId, button) -> marketSearch(screen, slot, button));
    }

    public static boolean hidePotionEffects() {
        return Features.on("hide_potion_effects") && (GameState.inSkyBlock || SbcMod.DEV) || io.muzy.sbc.ui.panels.GardenPlotsPanel.hidesEffects();
    }

    // market search button wants a sign, our search lists matches while you type. only a left click: a right
    // click is hypixels own "clear the search" and goes through
    static boolean marketSearch(AbstractContainerScreen<?> screen, Slot slot, int button) {
        if (button != 0 || slot == null || screen == null || !Features.on("market_search")) return false;
        String title = screen.getTitle().getString();
        if (!MarketSearchScreen.isMarketTitle(title)) return false;
        String sn = GameState.strip(slot.getItem().getHoverName().getString()).trim();
        if (sn.equals("Search") || sn.startsWith("Search ") && !sn.contains("Settings")) {
            Minecraft.getInstance().gui.setScreen(new MarketSearchScreen(screen, MarketSearchScreen.modeFor(title)));
            return true;
        }
        return false;
    }

    // item name the way the market search commands want it (from skyblocker ItemPrice.itemPriceLookup)
    static String marketName(ItemStack st) {
        String name = LegacyText.cleanName(st).trim();
        if ("PET".equals(Gain.sbId(st))) name = name.replaceFirst("\\[Lvl \\d+(?: ➡ \\d+)?] ", "");
        if (name.equals("Enchanted Book")) {
            List<String> lore = LegacyText.lore(st);
            if (!lore.isEmpty()) name = GameState.strip(lore.get(0)).trim();
        }
        return GameState.strip(name);
    }

    // key over a hovered slot. true = a lookup key took it
    public static boolean key(AbstractContainerScreen<?> cs, KeyEvent event) {
        Slot hovered = ((ContainerAccess) cs).sbc$hovered();
        if (hovered == null || !hovered.hasItem()) return false;
        return keyOver(cs, hovered.getItem(), event);
    }

    // same keys over an item thats not in a menu slot: side panel row, equipment piece
    public static boolean keyOver(AbstractContainerScreen<?> cs, ItemStack st, KeyEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (wikiKey != null && Features.on("wiki_lookup") && wikiKey.matches(event)) {
            String name = LegacyText.cleanName(st).replaceFirst("^\\[Lvl \\d+] ", "").trim();
            if (name.isEmpty()) return false;
            mc.gui.setScreen(new BrowserScreen(cs, WebPage.WIKI + "/w/" + name.replace(' ', '_')));
            return true;
        }
        if (priceKey != null && Features.on("price_lookup") && priceKey.matches(event)) {
            String name = marketName(st);
            if (name.isEmpty() || mc.getConnection() == null || mc.player == null) return false;
            String id = Gain.sbId(st);
            if (!id.isEmpty() && ItemInfo.onBazaar(id)) mc.getConnection().sendCommand("bz " + name);
            else mc.getConnection().sendCommand("auctionsearch " + name);
            return true;
        }
        return false;
    }
}
