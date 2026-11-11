// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.menus;

import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.AuctionPrices;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.core.SbTime;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.ui.SideHelper;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.theme.Modern;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyhanni CakeTracker: which new year cakes you have and which youre missing
// learns from bags and chests you open + what you buy, colors cakes in the ah, and next to those menus lists
// the years in ranges
public final class CakeTracker {
    private static boolean inContainer, inAuction, searching, auctionHasCakes;
    private static List<Integer> known = new ArrayList<>();
    private static long openedAt;
    private static Object lastScreen;

    private CakeTracker() {}

    private static Pattern sh(String key) {
        return Patterns.get("SH." + key);
    }

    // skyhanni onSecondPassed: a year counts once its baking started, on late winter 29th
    static int currentYear(SbTime.SbDate now) {
        return now.month() == 11 && now.day() >= 29 ? now.year() : now.year() - 1;
    }

    private static int year(String name, Pattern cake) {
        Matcher m = cake.matcher(name);
        return m.matches() ? Integer.parseInt(m.group("year").replace(",", "")) : -1;
    }

    private static void add(int year) {
        if (Store.I.cakesOwned.add(year)) Store.markDirty();
    }

    private static void remove(int year) {
        if (Store.I.cakesOwned.remove(year)) Store.markDirty();
    }

    // skyhanni checkCakeContainer, checkInventoryCakes, checkAuctionCakes
    static void mark(MenuView v, Object screen) {
        Pattern cake = sh("inventory.cake-tracker.cake.name");
        Feature f = Features.get("cake_tracker");
        inContainer = sh("inventory.cake-tracker.cake.container").matcher(v.title).matches();
        inAuction = sh("inventory.cake-tracker.auction.search").matcher(v.title).matches();
        searching = sh("inventory.cake-tracker.auction.cakesearch").matcher(v.title).matches();
        if (screen != lastScreen) {
            lastScreen = screen;
            known = new ArrayList<>();
            openedAt = System.currentTimeMillis();
        }
        if (inContainer) {
            List<Integer> here = new ArrayList<>();
            for (int slot = 0; slot < v.top; slot++) {
                int y = year(v.items[slot].name, cake);
                if (y >= 0) here.add(y);
            }
            if (known.isEmpty()) {
                here.forEach(CakeTracker::add);
                known = here;
            } else if (System.currentTimeMillis() - openedAt >= 500) {
                for (int y : here) if (!known.contains(y)) add(y);
                for (int y : known) if (!here.contains(y)) remove(y);
                known = here;
            }
        }
        auctionHasCakes = false;
        if (inAuction) {
            int owned = f.option("cake_tracker.owned_color").color(), missing = f.option("cake_tracker.missing_color").color();
            for (int slot = 0; slot < v.top; slot++) {
                int y = year(v.items[slot].name, cake);
                if (y < 0) continue;
                auctionHasCakes = true;
                v.back(slot, Store.I.cakesOwned.contains(y) ? owned : missing);
            }
        }
    }

    private static ChatBus.Result chat(ChatBus.Line line) {
        String message = LegacyText.of(line.component());
        Matcher m = sh("inventory.cake-tracker.cake.purchased").matcher(message);
        if (m.matches()) add(Integer.parseInt(m.group("year").replace(",", "")));
        else if (sh("inventory.cake-tracker.cake.baker.claimed").matcher(message).matches()) add(currentYear(SbTime.sbDate(System.currentTimeMillis())));
        return ChatBus.PASS;
    }

    // skyhanni getCakeRanges: "230" or "230 - 241"
    static List<int[]> ranges(List<Integer> sorted, boolean oldestFirst) {
        List<int[]> out = new ArrayList<>();
        if (sorted.isEmpty()) return out;
        int start = sorted.get(0), end = start;
        for (int i = 1; i < sorted.size(); i++) {
            int y = sorted.get(i);
            if (oldestFirst ? y == end + 1 : y == end - 1) {
                end = y;
                continue;
            }
            out.add(new int[]{start, end});
            start = end = y;
        }
        out.add(new int[]{start, end});
        return out;
    }

    private static double price(int year) {
        return AuctionPrices.lowestBin("NEW_YEAR_CAKE+" + year);
    }

    private static final class Panel implements SideHelper.Content {
        private int total;
        private List<Integer> owned = List.of(), missing = List.of();
        // each tabs list, redone when the panel refreshes: first year, last year, price
        private List<long[]> missingRows = List.of(), ownedRows = List.of();
        private double value, cost;

        @Override
        public String title() {
            return "Cake Tracker";
        }

        @Override
        public ItemStack icon() {
            return LegacyItems.stack("cake");
        }

        @Override
        public int accent() {
            return 0xFFFF7D7D;
        }

        @Override
        public String[] tabs() {
            return new String[]{"Missing " + missing.size(), "Owned " + owned.size()};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> screen) {
            total = currentYear(SbTime.sbDate(System.currentTimeMillis()));
            List<Integer> have = new ArrayList<>(Store.I.cakesOwned), lack = new ArrayList<>();
            Set<Integer> set = new HashSet<>(have);
            for (int y = 1; y <= total; y++) if (!set.contains(y)) lack.add(y);
            Collections.sort(have);
            value = cost = 0;
            for (int y : have) value += price(y);
            for (int y : lack) cost += price(y);
            owned = have;
            missing = lack;
            missingRows = rows(lack);
            ownedRows = rows(have);
        }

        private static List<long[]> rows(List<Integer> years) {
            List<long[]> out = new ArrayList<>();
            for (int[] r : ranges(years, true)) {
                double sum = 0;
                for (int year = r[0]; year <= r[1]; year++) sum += price(year);
                out.add(new long[]{r[0], r[1], Math.round(sum)});
            }
            return out;
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            int half = (w - 3) / 2;
            SideHelper.tile(ui, x, y, half, "Have", owned.size() + "/" + total, "", 0xFF55FF55);
            SideHelper.tile(ui, x + half + 3, y, w - half - 3, "To finish", Prices.coins(cost), "", Modern.GOLD);
            Modern.meter(ui, x, y + 26, w, 2, total == 0 ? 0 : owned.size() / (double) total, 0xFF55FF55);
            return 30;
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            boolean hover = MenuMarks.on("cake_tracker", "cake_tracker.price_hover");
            int color = (tab == 0 ? Features.get("cake_tracker").option("cake_tracker.missing_color") : Features.get("cake_tracker").option("cake_tracker.owned_color")).color();
            for (long[] r : tab == 0 ? missingRows : ownedRows) {
                String text = r[0] == r[1] ? String.valueOf(r[0]) : r[0] + " - " + r[1];
                boolean hv = ui.hover(x, y, w, 11);
                if (hv) ui.rect(x, y, w, 11, 0x22FFFFFF);
                ui.textShadow(text, x + 2, y + 1, color);
                double sum = r[2];
                if (sum > 0) {
                    String right = "§6" + Prices.coins(sum);
                    ui.textShadow(right, x + w - 2 - ui.width(right), y + 1, Modern.INK);
                }
                if (hv && hover) ui.tooltip("§cNew Year Cake", "§7Years: §f" + text, "§6" + Prices.coins(sum));
                y += 11;
            }
            if (y == y0) y += SideHelper.emptyText(ui, tab == 0 ? "All cakes owned!" : "Open a bag with cakes to read them.", x, y, w);
            return y - y0;
        }
    }

    private static final Panel PANEL = new Panel();

    static SideHelper.Content panelForTest() {
        return PANEL;
    }

    private static SideHelper.Content panel(Screen s) {
        if (!Features.on("cake_tracker")) return null;
        String t = GameState.strip(s.getTitle().getString()).trim();
        if (sh("inventory.cake-tracker.auction.search").matcher(t).matches() && (auctionHasCakes || sh("inventory.cake-tracker.auction.cakesearch").matcher(t).matches())) return PANEL;
        if (sh("inventory.cake-tracker.cake.container").matcher(t).matches() && !known.isEmpty()) return PANEL;
        return null;
    }

    static void register(BooleanSupplier skyblock) {
        Feature f = new Feature("cake_tracker", "Menus", "New Year Cake tracker", "Which New Year Cakes you have and which you lack, beside your cake bags and the Auction House, with what they cost.", "SkyHanni: New Year Cake Tracker")
                .when(skyblock).defaultOff()
                .option(Option.bool("cake_tracker.price_hover", "Price on hover", "", true))
                .option(Option.color("cake_tracker.owned_color", "Owned colour", "", 0xFF55FF55))
                .option(Option.color("cake_tracker.missing_color", "Missing colour", "", 0xFFFF5555));
        Features.add(f);
        MenuMarks.add("cake_tracker", t -> true, v -> mark(v, MenuMarks.screenNow()));
        ChatBus.register(ChatBus.OBSERVE, "cake_tracker", f, CakeTracker::chat);
        SideHelper.add(CakeTracker::panel);
        Safe.commands("CakeTracker.commands", (d, r) -> d.register(ClientCommands.literal("sbc").then(ClientCommands.literal("resetcaketracker").executes(c -> {
            Store.I.cakesOwned.clear();
            Store.markDirty();
            c.getSource().sendFeedback(Component.literal("§b[SBC] §fThe cake tracker is cleared. Open your cake bags to read them again."));
            return 1;
        }))));
    }
}
