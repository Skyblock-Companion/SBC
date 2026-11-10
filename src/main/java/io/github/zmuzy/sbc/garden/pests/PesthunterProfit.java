// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.pests;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.LegacyItems;
import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.garden.GardenPack;
import io.github.zmuzy.sbc.item.market.NpcTrade;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.item.menus.MenuView;
import io.github.zmuzy.sbc.ui.SideHelper;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyhanni PesthunterProfit: next to pesthunter's wares, what each trade is worth per pest: market price
// minus other costs, divided by its price in pests
// skyhanni's table in our side panel, best first
public final class PesthunterProfit {
    public static final String ID = "garden_pesthunter";
    private static final String TITLE = "Pesthunter's Wares", PESTS_ID = "PESTS";
    // ItemUtils.readItemAmount
    private static final Pattern AMOUNT = Pattern.compile("(?<name>(?:§.)*(?:[^§] ?)+)(?:§8x(?<amount>[\\d,]+))?");
    private static final Panel PANEL = new Panel();

    private PesthunterProfit() {}

    record Row(String name, double price, double cost, long pests) {
        double profit() {
            return price - cost;
        }

        double perPest() {
            return pests > 0 ? profit() / pests : 0;
        }
    }

    // readItem: null if its not a trade or has no price
    static Row read(MenuView.Item it) {
        if (it.empty() || !NpcTrade.hasTradeLine(it.lore)) return null;
        var costs = NpcTrade.readLoreCosts(it.lore);
        double other = 0;
        long pests = 0;
        for (var c : costs) {
            if (c.id().equals(PESTS_ID)) pests += c.amount();
            else {
                double p = NpcTrade.priceOf(c.id());
                if (p < 0) return null;
                other += p * c.amount();
            }
        }
        Matcher m = AMOUNT.matcher(it.name);
        long amount = m.matches() && m.group("amount") != null ? Long.parseLong(m.group("amount").replace(",", "")) : 1;
        String name = m.matches() ? GameState.strip(m.group("name")).trim() : it.clean;
        String id = ItemRepo.idForName(name.replace("[Lvl 100]", "[Lvl {LVL}]"));
        if (id == null) id = it.id;
        if (id == null || id.isEmpty()) return null;
        double price = NpcTrade.priceOf(id);
        if (price < 0) return null;
        // show level 1 hedgehog instead of level 100
        return new Row(name.replace("[Lvl 100]", "[Lvl 1]"), price * amount, other, pests);
    }

    private static final class Panel implements SideHelper.Content {
        private List<Row> rows = List.of();

        @Override
        public String title() {
            return "Pesthunter profit";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("silverfish_spawn_egg");
        }

        @Override
        public int accent() {
            return 0xFFFF7A59;
        }

        @Override
        public String[] tabs() {
            return new String[]{"Per pest"};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
            MenuView v = MenuMarks.current();
            if (v == null) return;
            List<Row> out = new ArrayList<>();
            for (int slot = 0; slot < v.top; slot++) {
                Row r = read(v.item(slot));
                if (r != null) out.add(r);
            }
            out.sort((a, b) -> Double.compare(b.perPest(), a.perPest()));
            rows = out;
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            return 0;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            for (Row r : rows) {
                String right = (r.perPest() > 0 ? "§6" : "§c") + Prices.coins(r.perPest());
                int rw = ui.width(right);
                ui.textShadow(ui.ellipsize("§f" + r.name(), w - rw - 6), x + 2, y + 1, Modern.LABEL);
                ui.textShadow(right, x + w - rw - 2, y + 1, Modern.INK);
                y += 11;
            }
            if (rows.isEmpty()) y += SideHelper.emptyText(ui, "No trades with a known price.", x, y, w);
            return y - y0;
        }
    }

    private static SideHelper.Content panel(Screen s) {
        if (!Features.on(ID) || !GardenPack.garden()) return null;
        return TITLE.equals(GameState.strip(s.getTitle().getString()).trim()) ? PANEL : null;
    }

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Pesthunter profit", "Beside Pesthunter's Wares: what each trade is worth per pest you give for it, best first.", "SkyHanni: Pesthunter Profit Display")
                .when(GardenPack::garden).defaultOff());
        SideHelper.add(PesthunterProfit::panel);
    }
}
