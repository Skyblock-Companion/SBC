// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.events.hoppity;

import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyItems;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.item.enchants.EnchantParser;
import io.github.zmuzy.sbc.item.market.LoreCosts;
import io.github.zmuzy.sbc.ui.SideHelper;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

// panels next to the factory menus, from skyhanni CFShopPrice (what 1M chocolate is worth in coins per shop
// item), HitmanSlots (what hitman slots cost, paid and to come) + HitmanApi (when slots open and fill)
public final class CFPanels {
    private CFPanels() {}

    public static final String SHOP = "cf_shop_price", HITMAN = "cf_hitman_costs";
    private static final long DAY = io.github.zmuzy.sbc.core.SbTime.DAY, HOUR = DAY / 24;
    private static boolean inShop;
    private static long spent;

    // one shop item
    record Product(String name, long chocolate, double itemPrice, double otherPrice, boolean canBuy) {
        double perMillion() {
            return (itemPrice - otherPrice) / chocolate * 1_000_000;
        }
    }

    static boolean inShop() {
        return inShop;
    }

    // --- the shop ---

    // updateProducts on one item, null if not for sale or no price
    static Product product(String name, List<String> lore, double itemPrice, java.util.function.ToDoubleFunction<String> priceOf) {
        Long chocolate = ChocolateFactory.getChocolateBuyCost(lore);
        if (chocolate == null || chocolate <= 0 || itemPrice <= 0) return null;
        double other = 0;
        for (LoreCosts.Entry e : LoreCosts.read(lore)) if (!e.id().equals("SKYBLOCK_CHOCOLATE")) other += priceOf.applyAsDouble(e.id()) * e.amount();
        return new Product(name, chocolate, itemPrice, other, LoreCosts.hasTradeLine(lore));
    }

    private static final class ShopPanel implements SideHelper.Content {
        private List<Product> products = List.of();

        @Override public String title() { return "Chocolate Shop"; }
        @Override public ItemStack icon() { return LegacyItems.stack("cocoa_beans"); }
        @Override public int accent() { return 0xFFC48A5A; }
        @Override public String[] tabs() { return new String[]{"Coins per million"}; }

        @Override public void refresh(AbstractContainerScreen<?> screen) {
            inShop = true;
            List<Product> out = new ArrayList<>();
            var mc = Minecraft.getInstance();
            for (Slot s : screen.getMenu().slots) {
                if (s.container == mc.player.getInventory() || s.getItem().isEmpty()) continue;
                List<String> lore = LegacyText.lore(s.getItem());
                if (s.index == 50) for (String l : lore) {
                    Matcher m = Patterns.get("SH.misc.chocolatefactory.shop.spent").matcher(l);
                    if (m.matches()) spent = HoppityEnv.parseLong(m.group("amount"));
                }
                Product p = product(LegacyText.name(s.getItem()), lore, Gain.ofId(Gain.sbId(s.getItem())), Gain::ofId);
                if (p != null) out.add(p);
            }
            out.sort(Comparator.comparingDouble(Product::perMillion).reversed());
            products = out;
        }

        @Override public int summary(Ui ui, int x, int y, int w) {
            Modern.row(ui, "Available", "§6" + ChocolateFactory.Amount.CURRENT.formatted(), x, y, w, Modern.LABEL, Modern.GOLD);
            Modern.row(ui, "Spent", "§6" + HoppityEnv.sep(spent), x, y + 10, w, Modern.LABEL, Modern.GOLD);
            return 22;
        }

        @Override public int list(Ui ui, int tab, int x, int y, int w) {
            if (products.isEmpty()) return SideHelper.emptyText(ui, "Nothing here has a price.", x, y, w);
            int at = y;
            for (Product p : products) {
                Modern.row(ui, (p.canBuy() ? "" : "§m") + p.name(), "§6" + EnchantParser.shortFormat(p.perMillion()), x, at, w, Modern.LABEL, Modern.GOLD);
                at += 10;
                Modern.row(ui, "§7" + EnchantParser.shortFormat(p.chocolate()) + " choc", ChocolateFactory.Amount.CURRENT.formattedTimeUntilGoal(p.chocolate()), x + 6, at, w - 6, Modern.DIM, Modern.AQUA);
                at += 11;
            }
            return at - y;
        }
    }

    private static final ShopPanel SHOP_PANEL = new ShopPanel();

    private static SideHelper.Content shopPanel(Screen s) {
        if (!Features.on(SHOP) || !Patterns.get("SH.misc.chocolatefactory.shop.title").matcher(GameState.strip(s.getTitle().getString()).trim()).matches()) {
            inShop = false;
            return null;
        }
        return SHOP_PANEL;
    }

    // onChat: what you bought comes off the chocolate
    static void onChat(String message) {
        if (!inShop) return;
        Matcher m = Patterns.get("SH.misc.chocolatefactory.shop.bought").matcher(message);
        if (!m.matches()) return;
        int amount = m.group("amount") == null ? 1 : Integer.parseInt(m.group("amount"));
        for (Product p : SHOP_PANEL.products) {
            if (!GameState.strip(p.name()).equals(m.group("item"))) continue;
            ChocolateFactory.addToCurrent(-p.chocolate() * amount);
            return;
        }
    }

    // --- HitmanApi ---

    // getOpenSlots: hypixel only gives cooldowns
    static int openSlots(Store.HoppityProfile p) {
        int base = p.purchasedHitmanSlots - p.availableHitmanEggs;
        long left = p.hitmanAllSlotsCooldown - HoppityEnv.now();
        if (left <= 0) return base;
        return base - (int) Math.ceil(left / (double) DAY);
    }

    private static long timeToNumSlots(Store.HoppityProfile p, int slots) {
        if (openSlots(p) + p.availableHitmanEggs >= slots) return 0;
        if (HoppityEnv.isFarPast(p.hitmanSingleSlotCooldown)) return 0;
        int onCooldown = slots - openSlots(p) - p.availableHitmanEggs - 1;
        return p.hitmanSingleSlotCooldown - HoppityEnv.now() + onCooldown * DAY;
    }

    private static int extraSlotsIn(long duration) {
        return (int) Math.ceil(duration / (double) DAY);
    }

    private static HoppityEggType firstHunted() {
        HoppityEggType best = null;
        for (HoppityEggType t : HoppityEggType.RESETTING) if (!t.isClaimed() && (best == null || t.timeUntil() < best.timeUntil())) best = t;
        if (best != null) return best;
        for (HoppityEggType t : HoppityEggType.RESETTING) if (best == null || t.timeUntil() < best.timeUntil()) best = t;
        return best;
    }

    private static HoppityEggType nextHunted(HoppityEggType previous, long duration) {
        List<HoppityEggType> passing = new ArrayList<>();
        for (HoppityEggType t : HoppityEggType.RESETTING) if (t.timeUntil() < duration) passing.add(t);
        for (HoppityEggType t : passing) if (t.resetsAt > previous.resetsAt && t.altDay == previous.altDay) return t;
        for (HoppityEggType t : passing) if (t.altDay != previous.altDay) return t;
        List<HoppityEggType> all = HoppityEggType.RESETTING;
        return all.get((all.indexOf(previous) + 1) % all.size());
    }

    private static long between(HoppityEggType a, HoppityEggType another) {
        int hours;
        if (a == another) hours = 48;
        else if (a.altDay != another.altDay) hours = 24 - another.resetsAt + a.resetsAt;
        else if (a.resetsAt > another.resetsAt) hours = a.resetsAt - another.resetsAt;
        else hours = 48 - (a.resetsAt - another.resetsAt);
        return hours * HOUR;
    }

    private static long timeToHuntCount(int target) {
        if (target <= 0) return 0;
        int hunts = target;
        List<HoppityEggType> claimable = new ArrayList<>();
        for (HoppityEggType t : HoppityEggType.RESETTING) if (!t.isClaimed()) claimable.add(t);
        claimable.sort(Comparator.comparingLong(HoppityEggType::timeUntil));
        if (hunts <= claimable.size()) return claimable.get(hunts - 1).timeUntil();
        HoppityEggType next = claimable.isEmpty() ? firstHunted() : claimable.get(claimable.size() - 1);
        hunts -= Math.max(1, claimable.size());
        long until = next.isClaimed() ? next.timeUntil() + 2 * DAY : next.timeUntil();
        for (int i = 0; i < hunts; i++) {
            HoppityEggType n = nextHunted(next, until);
            until += between(n, next);
            next = n;
        }
        return until;
    }

    // getTimeToFull: {time, 1 if the hunt ends first}
    static long[] timeToFull(Store.HoppityProfile p) {
        Long end = HoppityTime.eventEndMark();
        if (end == null) return new long[]{0, 0};
        int max = p.purchasedHitmanSlots - p.availableHitmanEggs, toFill = openSlots(p);
        if (toFill <= 0) return new long[]{0, 0};
        for (int i = 0; i < 10; i++) {
            long time = timeToHuntCount(toFill);
            if (HoppityEnv.now() + time > end) return new long[]{end - HoppityEnv.now(), 1};
            int extra = extraSlotsIn(time), adjusted = Math.min(openSlots(p) + extra, max);
            if (adjusted == toFill || extra == 0) return new long[]{time, 0};
            toFill = adjusted;
        }
        return new long[]{0, 0};
    }

    // getHitmanTimeToAll: {time, 1 if the hunt ends first}
    static long[] timeToAll(Store.HoppityProfile p) {
        Long end = HoppityTime.eventEndMark();
        if (end == null) return new long[]{0, 0};
        long slots = timeToNumSlots(p, p.purchasedHitmanSlots), hunt = timeToHuntCount(openSlots(p)), longer = Math.max(slots, hunt);
        if (longer == 0) return new long[]{0, 0};
        if (HoppityEnv.now() + longer > end) return new long[]{end - HoppityEnv.now(), 1};
        if (hunt > slots) return new long[]{hunt, 0};
        var at = io.github.zmuzy.sbc.core.SbTime.sbDate(HoppityEnv.now() + slots);
        boolean alt = HoppityTime.isAlternateDay(at);
        HoppityEggType next = null;
        for (HoppityEggType t : HoppityEggType.RESETTING) if (next == null && t.resetsAt > at.hour() && t.altDay == alt) next = t;
        if (next == null) for (HoppityEggType t : HoppityEggType.RESETTING) if (t.altDay != alt && (next == null || t.resetsAt < next.resetsAt)) next = t;
        if (next == null) return new long[]{slots, 0};
        int hours = next.resetsAt - at.hour() + (next.altDay != alt ? 24 : 0);
        return new long[]{slots + hours * HOUR, 0};
    }

    // --- HitmanSlots ---

    // handleSlotStorageUpdate: {paid, left} of the costs by how many slots the menu still offers
    static List<List<Long>> slotPrices(List<Long> costs, int leftToPurchase) {
        int owned = Math.max(0, costs.size() - leftToPurchase);
        return List.of(new ArrayList<>(costs.subList(0, Math.min(owned, costs.size()))), new ArrayList<>(costs.subList(Math.min(owned, costs.size()), costs.size())));
    }

    private static long sum(List<Long> l) {
        long n = 0;
        for (long v : l) n += v;
        return n;
    }

    private static String time(long[] t, String none) {
        return t[0] <= 0 ? none : (t[1] == 1 ? "§c" : "§b") + HoppityEnv.format(t[0]);
    }

    private static final class HitmanPanel implements SideHelper.Content {
        private List<Long> paid = List.of(), left = List.of();

        @Override public String title() { return "Rabbit Hitman"; }
        @Override public ItemStack icon() { return LegacyItems.stack("iron_sword"); }
        @Override public int accent() { return Modern.BAD; }
        @Override public String[] tabs() { return new String[]{"Slots to buy"}; }

        @Override public void refresh(AbstractContainerScreen<?> screen) {
            var mc = Minecraft.getInstance();
            int count = 0;
            for (Slot s : screen.getMenu().slots) {
                if (s.container == mc.player.getInventory() || s.getItem().isEmpty()) continue;
                int i = s.index;
                if (i <= 8 || i >= 45 || i % 9 == 0 || (i + 1) % 9 == 0) continue;
                List<String> lore = LegacyText.lore(s.getItem());
                if (!lore.isEmpty() && Patterns.get("SH.misc.chocolatefactory.hitman.slotcost").matcher(String.join(" ", lore)).matches()) count++;
            }
            List<List<Long>> prices = slotPrices(new ArrayList<>(HoppityData.hitmanCosts()), count);
            paid = prices.get(0);
            left = prices.get(1);
        }

        @Override public int summary(Ui ui, int x, int y, int w) {
            Store.HoppityProfile p = HoppityEnv.profile();
            int at = y;
            Modern.row(ui, "Purchased", "§a" + paid.size() + " §7paid §6" + EnchantParser.shortFormat(sum(paid)), x, at, w, Modern.LABEL, Modern.INK);
            at += 10;
            Modern.row(ui, "To buy", "§c" + left.size() + " §7cost §6" + EnchantParser.shortFormat(sum(left)), x, at, w, Modern.LABEL, Modern.INK);
            at += 10;
            Modern.row(ui, "Open slots", "§6" + openSlots(p), x, at, w, Modern.LABEL, Modern.INK);
            at += 10;
            if (HoppityTime.isHoppityEvent()) {
                Modern.row(ui, "Slots full", openSlots(p) == 0 ? "§7Cooldown" : time(timeToFull(p), "§cNow"), x, at, w, Modern.LABEL, Modern.AQUA);
                at += 10;
                Modern.row(ui, p.purchasedHitmanSlots + " claims", time(timeToAll(p), "§aAll ready"), x, at, w, Modern.LABEL, Modern.AQUA);
                at += 10;
            }
            return at - y + 2;
        }

        @Override public int list(Ui ui, int tab, int x, int y, int w) {
            if (left.isEmpty()) return SideHelper.emptyText(ui, "Every slot is bought.", x, y, w);
            int at = y;
            for (int i = 0; i < left.size(); i++) {
                Modern.row(ui, "Slot " + (paid.size() + i + 1), "§6" + String.format(Locale.ROOT, "%,d", left.get(i)), x, at, w, Modern.LABEL, Modern.GOLD);
                at += 10;
            }
            return at - y;
        }
    }

    private static final HitmanPanel HITMAN_PANEL = new HitmanPanel();

    private static SideHelper.Content hitmanPanel(Screen s) {
        if (!Features.on(HITMAN) || !Patterns.get("SH.misc.chocolatefactory.hitman.inventory").matcher(s.getTitle().getString().trim()).matches()) return null;
        return HITMAN_PANEL;
    }

    // --- HoppityCollectionStats: whats missing per island ---

    private static final class CollectionPanel implements SideHelper.Content {
        @Override public String title() { return "Missing rabbits"; }
        @Override public ItemStack icon() { return LegacyItems.stack("rabbit_foot"); }
        @Override public int accent() { return 0xFFFFB454; }
        @Override public String[] tabs() { return new String[]{"Residents", "Hotspots", "Locations"}; }
        @Override public void refresh(AbstractContainerScreen<?> screen) {}
        @Override public int summary(Ui ui, int x, int y, int w) { return 0; }

        @Override public int list(Ui ui, int tab, int x, int y, int w) {
            if (tab == 2) {
                if (!Hoppity.b(COLLECTION, "locations", true)) return SideHelper.emptyText(ui, "Off in the settings.", x, y, w);
                var rabbits = HoppityCollectionStats.locationRabbits();
                if (rabbits.isEmpty()) return SideHelper.emptyText(ui, "Turn the pages to read them.", x, y, w);
                int at = y;
                for (var e : rabbits.entrySet()) {
                    var r = e.getValue();
                    Modern.row(ui, e.getKey() + " §7" + r.locationName(), (r.hasMetRequirements() ? "§a" : "§c") + r.loreFoundCount() + "§7/§a" + r.requiredCount(), x, at, w, Modern.LABEL, Modern.INK);
                    at += 10;
                }
                return at - y;
            }
            if (tab == 1 && !HoppityTime.isHoppityEvent()) return SideHelper.emptyText(ui, "Only during the hunt.", x, y, w);
            if (!Hoppity.b(COLLECTION, tab == 1 ? "hotspots" : "residents", true)) return SideHelper.emptyText(ui, "Off in the settings.", x, y, w);
            List<Object[]> rows = HoppityCollectionStats.perIsland(tab == 1);
            if (rows.isEmpty()) return SideHelper.emptyText(ui, "Turn the pages to read them.", x, y, w);
            rows.sort(Comparator.comparingInt(r -> ((int) r[1]) - ((int) r[2])));
            int at = y;
            for (Object[] r : rows) {
                int found = (int) r[1], total = (int) r[2];
                Modern.row(ui, (String) r[0], (found == total ? "§a" : "§c") + found + "§7/§a" + total, x, at, w, Modern.LABEL, Modern.INK);
                at += 10;
            }
            return at - y;
        }
    }

    public static final String COLLECTION = "hoppity_collection_panel";
    private static final CollectionPanel COLLECTION_PANEL = new CollectionPanel();

    private static SideHelper.Content collectionPanel(Screen s) {
        return Features.on(COLLECTION) && s.getTitle().getString().contains("Hoppity's Collection") ? COLLECTION_PANEL : null;
    }

    static void register() {
        Features.add(new Feature(COLLECTION, "Events", "Missing rabbits", "Beside Hoppity's Collection: per island how many resident and hotspot rabbits you have, and what the location rabbits still need. Turn the pages once to read them all.", "SkyHanni: Missing Hotspot, Resident and Location Rabbits")
                .when(() -> HoppityEnv.world.inSkyBlock())
                .option(io.github.zmuzy.sbc.feature.Option.bool("hoppity_collection_panel.residents", "Residents", "", true))
                .option(io.github.zmuzy.sbc.feature.Option.bool("hoppity_collection_panel.hotspots", "Hotspots", "", true))
                .option(io.github.zmuzy.sbc.feature.Option.bool("hoppity_collection_panel.locations", "Locations", "", true)));
        SideHelper.add(CFPanels::collectionPanel);
        Features.add(new Feature(SHOP, "Events", "Chocolate Shop prices", "Beside the Chocolate Shop: coins per million chocolate for everything in it, best first, with when you can afford each and the chocolate you spent.", "SkyHanni: Chocolate Shop Price")
                .when(() -> HoppityEnv.world.inSkyBlock()));
        Features.add(new Feature(HITMAN, "Events", "Hitman slots", "Beside Rabbit Hitman: what your slots cost and what the rest would, how many are open, when they are full.", "SkyHanni: Hitman Costs")
                .when(() -> HoppityEnv.world.inSkyBlock()));
        SideHelper.add(CFPanels::shopPanel);
        SideHelper.add(CFPanels::hitmanPanel);
    }
}
