// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.menus;

import io.muzy.sbc.core.AuctionPrices;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemInfo;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.tasks.Mission;
import io.muzy.sbc.ui.BrowserScreen;
import io.muzy.sbc.ui.SideHelper;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.web.WebPage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;

// from skyhanni GeorgeHelper: next to georges offer pets menu, the pets his egg still wants, cheapest of each
// + total cost
// prices from AuctionPrices. a pet with no price links the wiki, one with a price searches the ah
public final class TamingSixty {
    private TamingSixty() {}

    private static final int SPAWN_EGG_SLOT = 41;
    private static final String[] RARITIES = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC"};
    private static final String[] COLORS = {"§f", "§a", "§9", "§5", "§6", "§d"};
    private static final Patterns.P MENU = Patterns.p("SH.george.taming-sixty.offer-pets.inventory-menu");
    private static final Patterns.P NEEDED = Patterns.p("SH.george.taming-sixty.needed-pet.loreline");

    // one pet the egg wants at its cheapest tier. price 0 = none found
    record Needed(String petName, String type, int tier, double price) {
        String label() {
            return COLORS[tier] + Character.toUpperCase(RARITIES[tier].charAt(0)) + RARITIES[tier].substring(1).toLowerCase(Locale.ROOT) + " " + petName;
        }
    }

    // skyhanni constructDisplay: the eggs lore lines, each pet at the cheapest tier allowed
    static List<Needed> read(List<String> lore, boolean otherTiers, ToDoubleFunction<String> priceOf) {
        List<Needed> out = new ArrayList<>();
        for (String line : lore) {
            Matcher m = NEEDED.matcher(line);
            if (!m.matches()) continue;
            String tierName = m.group("tier").toUpperCase(Locale.ROOT);
            int tier = 0;
            for (int i = 0; i < RARITIES.length; i++) if (RARITIES[i].equals(tierName)) tier = i;
            String petName = GameState.strip(m.group("pet")).trim();
            String type = petName.toUpperCase(Locale.ROOT).replace(' ', '_');
            out.add(cheapest(petName, type, tier, otherTiers, priceOf));
        }
        return out;
    }

    // skyhanni findCheapestTier: the tier asked for, and if allowed one below + one above
    private static Needed cheapest(String petName, String type, int tier, boolean otherTiers, ToDoubleFunction<String> priceOf) {
        int bestTier = tier;
        double best = price(type, tier, priceOf);
        if (otherTiers) {
            for (int other : new int[]{tier - 1, tier + 1}) {
                if (other < 0 || other >= RARITIES.length) continue;
                double p = price(type, other, priceOf);
                if (p > 0 && (best <= 0 || p < best)) {
                    best = p;
                    bestTier = other;
                }
            }
        }
        return new Needed(petName, type, bestTier, best);
    }

    // pets are priced by level too, cheaper of low and max
    private static double price(String type, int tier, ToDoubleFunction<String> priceOf) {
        double low = priceOf.applyAsDouble(AuctionPrices.petKey(type, RARITIES[tier], false));
        double max = priceOf.applyAsDouble(AuctionPrices.petKey(type, RARITIES[tier], true));
        if (low <= 0) return Math.max(0, max);
        return max <= 0 ? low : Math.min(low, max);
    }

    static double total(List<Needed> pets) {
        double sum = 0;
        for (Needed n : pets) if (n.price() > 0) sum += n.price();
        return sum;
    }

    private static final class Panel implements SideHelper.Content {
        private List<Needed> pets = List.of();

        @Override
        public String title() {
            return "Taming 60";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("minecraft:polar_bear_spawn_egg");
        }

        @Override
        public int accent() {
            return 0xFFFF55FF;
        }

        @Override
        public String[] tabs() {
            return new String[]{"Pets " + pets.size()};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
            if (SPAWN_EGG_SLOT >= screen.getMenu().slots.size()) return;
            Slot egg = screen.getMenu().slots.get(SPAWN_EGG_SLOT);
            if (egg.getItem().isEmpty()) return;
            boolean other = MenuMarks.on("taming_sixty", "taming_sixty.other_tiers");
            pets = read(LegacyText.lore(egg.getItem()), other, AuctionPrices::lowestBin);
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            SideHelper.tile(ui, x, y, w, "Total cost", Prices.coins(total(pets)), "", Modern.GOLD);
            return 26;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            for (Needed n : pets) {
                boolean hv = ui.hover(x, y, w, 20);
                if (hv) ui.rect(x, y, w, 20, 0x22FFFFFF);
                ui.icon(ItemInfo.stack(n.type() + ";" + n.tier()), x + 2, y + 2, 1f);
                ui.textShadow(ui.ellipsize(n.label(), w - 24), x + 22, y + 1, Modern.INK);
                String right = n.price() > 0 ? "§6" + Prices.coins(n.price()) : "§cNo price found";
                ui.textShadow(right, x + 22, y + 11, Modern.INK);
                if (hv) {
                    if (n.price() > 0) ui.tooltip("§eClick to find " + n.label() + " §eon the Auction House", "§7Make sure to adjust the rarity filter.");
                    else ui.tooltip("§eClick to read about " + n.label() + " §eon the wiki");
                }
                ui.click(x, y, w, 20, () -> {
                    if (n.price() > 0) {
                        Mission.run("ahs ] " + n.petName());
                    } else {
                        Minecraft mc = Minecraft.getInstance();
                        mc.gui.setScreen(new BrowserScreen(mc.gui.screen(), WebPage.wikiSearchUrl(n.petName() + " Pet")));
                    }
                });
                y += 20;
            }
            if (y == y0) y += SideHelper.emptyText(ui, "George's egg names no pet right now.", x, y, w);
            return y - y0;
        }
    }

    private static final Panel PANEL = new Panel();

    private static SideHelper.Content panel(Screen s) {
        if (!Features.on("taming_sixty") || !"HUB".equals(GameState.island)) return null;
        return MENU.matcher(GameState.strip(s.getTitle().getString()).trim()).matches() ? PANEL : null;
    }

    static void register(BooleanSupplier skyblock) {
        Features.add(new Feature("taming_sixty", "Pets", "Taming 60 helper", "Beside George's Offer Pets menu: the pets his egg asks for, the cheapest of each and what all of them cost. Click a pet to search for it.", "SkyHanni")
                .when(skyblock)
                .option(Option.bool("taming_sixty.other_tiers", "Other tiers", "Also looks one tier below and above. Those prices leave out what Kat charges to upgrade.", false)));
        SideHelper.add(TamingSixty::panel);
    }
}
