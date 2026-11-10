// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.visitor;

import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemInfo;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.garden.GardenData;
import io.github.zmuzy.sbc.garden.GardenItems;
import io.github.zmuzy.sbc.garden.milestones.GardenCard;
import io.github.zmuzy.sbc.garden.milestones.GardenCard.Block;
import io.github.zmuzy.sbc.garden.milestones.GardenCard.Chip;
import io.github.zmuzy.sbc.garden.milestones.GardenCard.Line;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;
import io.github.zmuzy.sbc.item.sacks.Sacks;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// visitor cards, from skyhanni GardenVisitorShoppingList, GardenVisitorDropStatistics, GardenCharmedVisitors
// what all visitors want with price + what your sacks hold, what accepted offers brought in, who is charmed
public final class VisitorCards {
    public static final String SHOPPING = "garden_visitor_shopping", DROPS = "garden_visitor_drops", CHARMED = "garden_visitor_charmed";
    private static final String[] RARITIES = {"UNCOMMON", "RARE", "LEGENDARY", "MYTHIC", "SPECIAL"};
    private static final char[] RARITY_COLOR = {'a', '9', '6', 'd', 'c'};

    private static long lastAccept;

    private VisitorCards() {}

    private static boolean option(String feature, String key, boolean otherwise) {
        Feature f = Features.get(feature);
        if (f == null) return otherwise;
        Option o = f.option(key);
        return o == null ? otherwise : o.bool();
    }

    // one shopping list line
    public record Need(String id, String name, int amount, double price, Integer inSacks, int craftable) {}

    // prepareDrawingData: everything wanted by those not done, minus the ones you ignore
    public static Map<String, Integer> shoppingList(List<Visitors.Visitor> visitors) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Visitors.Visitor v : visitors) {
            if (v.status == Visitors.Status.ACCEPTED || v.status == Visitors.Status.REFUSED || v.ignored()) continue;
            for (var e : v.shopping().entrySet()) out.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        return out;
    }

    // addSackData: how many can be crafted from the sacks, 0 if not all of it
    static int leftToCraft(String id, int amount, int inSacks) {
        int left = amount - inSacks;
        if (left <= 0) return 0;
        for (ItemInfo.Recipe recipe : ItemInfo.recipes(id)) {
            if (recipe.parts().isEmpty() || recipe.parts().get(0).id().contains("PEST") || !recipe.type().equals("crafting")) continue;
            Map<String, Integer> required = new HashMap<>();
            for (ItemInfo.Part p : recipe.parts()) required.merge(p.id(), p.count(), Integer::sum);
            for (var e : required.entrySet()) {
                Integer have = Sacks.amountInSacks(e.getKey());
                if (have == null || have < e.getValue() * left) return 0;
            }
            return left;
        }
        return 0;
    }

    // the buy list: what is wanted minus what the sacks hold, only what the market sells
    static List<io.github.zmuzy.sbc.item.market.MoreMarket.Material> toBuy(List<Need> needs) {
        return toBuy(needs, GardenItems::inInventory);
    }

    // what is still missing: the visitors' amount minus what is in your sacks and your inventory, so what you just
    // bought comes off the list
    static List<io.github.zmuzy.sbc.item.market.MoreMarket.Material> toBuy(List<Need> needs, java.util.function.ToIntFunction<String> inInventory) {
        List<io.github.zmuzy.sbc.item.market.MoreMarket.Material> out = new ArrayList<>();
        for (Need n : needs) {
            int left = n.amount() - (n.inSacks() == null ? 0 : n.inSacks()) - inInventory.applyAsInt(n.id());
            if (left > 0) out.add(new io.github.zmuzy.sbc.item.market.MoreMarket.Material(n.id(), left));
        }
        return out;
    }

    static void syncBuyList() {
        if (!Features.on(SHOPPING) || !option(SHOPPING, "garden_visitor_shopping.buy_list", true)) {
            if (io.github.zmuzy.sbc.item.market.MoreMarket.fromVisitors) io.github.zmuzy.sbc.item.market.MoreMarket.stopBuying();
            return;
        }
        List<io.github.zmuzy.sbc.item.market.MoreMarket.Material> want = new ArrayList<>();
        for (var m : toBuy(needs())) if (io.github.zmuzy.sbc.item.market.MoreMarket.onMarket(m.id())) want.add(m);
        io.github.zmuzy.sbc.item.market.MoreMarket.visitorsWant(want);
    }

    static List<Need> needs() {
        Map<String, String> names = new HashMap<>();
        for (Visitors.Visitor v : Visitors.all()) if (v.offer != null) names.putAll(v.offer.names);
        List<Need> out = new ArrayList<>();
        for (var e : shoppingList(Visitors.all()).entrySet()) {
            String id = e.getKey();
            Integer sacks = Sacks.amountInSacks(id);
            out.add(new Need(id, names.getOrDefault(id, ItemRepo.name(id)), e.getValue(), GardenItems.price(id) * e.getValue(), sacks, leftToCraft(id, e.getValue(), sacks == null ? 0 : sacks)));
        }
        return out;
    }

    // for /sbc dump: each visitor, what it wants, and what the buy list holds
    public static String describe() {
        StringBuilder sb = new StringBuilder();
        for (Visitors.Visitor v : Visitors.all())
            sb.append(v.name).append(" status=").append(v.status).append(" wants=").append(v.offer == null ? "(menu not read)" : v.offer.shopping).append('\n');
        for (Need n : needs()) sb.append("need ").append(n.id()).append(" x").append(n.amount()).append(" sacks=").append(n.inSacks()).append('\n');
        sb.append(io.github.zmuzy.sbc.item.market.MoreMarket.describe());
        return sb.toString();
    }

    static String stacks(int amount) {
        return amount >= 64 && option(SHOPPING, "garden_visitor_shopping.stacks", false)
                ? "x" + amount / 64 + " stacks + " + amount % 64
                : "x" + String.format(Locale.ROOT, "%,d", amount);
    }

    static boolean showHere() {
        // GardenVisitorShoppingList.showGui
        if ("HUB".equals(GameState.island)) {
            if (option(SHOPPING, "garden_visitor_shopping.bazaar_alley", true) && GameState.area.contains("Bazaar Alley")) return true;
            if (option(SHOPPING, "garden_visitor_shopping.farming_areas", true) && GameState.area.equals("Farm")) return true;
        }
        if (option(SHOPPING, "garden_visitor_shopping.farming_areas", true) && "THE_FARMING_ISLANDS".equals(GameState.island)) return true;
        return Visitors.garden() && (Visitors.onBarnPlot() || !option(SHOPPING, "garden_visitor_shopping.only_close", false));
    }

    private static final List<Need> DEMO_NEEDS = List.of(
            new Need("ENCHANTED_HAY_BLOCK", "§aEnchanted Hay Bale", 64, 497_664, 40, 0),
            new Need("ENCHANTED_CARROT", "§aEnchanted Carrot", 320, 288_000, null, 0),
            new Need("FINE_FLOUR", "§aFine Flour", 3, 96_000, 3, 0));

    private static final class Shopping extends GardenCard {
        Shopping() {
            super(SHOPPING, "Visitor shopping list", Zone.TOP_LEFT, 37, SHOPPING);
        }

        private List<Need> shownNeeds() {
            return demo ? DEMO_NEEDS : needs();
        }

        @Override
        public ItemStack icon() {
            return io.github.zmuzy.sbc.core.LegacyItems.stack("minecraft:emerald");
        }

        @Override
        public boolean visible() {
            if (!showHere()) return false;
            return !Visitors.all().isEmpty();
        }

        @Override
        public List<Row> rows() {
            return rowsOf(shownNeeds());
        }

        @Override
        public List<Row> demo() {
            return rowsOf(DEMO_NEEDS);
        }

        private List<Row> rowsOf(List<Need> list) {
            List<Row> out = new ArrayList<>();
            for (Need n : list) out.add(new Row(GameState.strip(n.name()), stacks(n.amount()) + " " + Prices.coins(n.price()), Theme.current().accent()));
            return out;
        }

        @Override
        protected String bandValue() {
            if (!option(SHOPPING, "garden_visitor_shopping.show_price", true)) return "";
            double total = 0;
            for (Need n : shownNeeds()) total += n.price();
            return total > 0 ? "§6" + Prices.coins(total) : "";
        }

        @Override
        protected Chip chip() {
            return null;
        }

        @Override
        protected List<Block> blocks() {
            List<Block> out = new ArrayList<>();
            boolean price = option(SHOPPING, "garden_visitor_shopping.show_price", true), sacks = option(SHOPPING, "garden_visitor_shopping.show_sacks", true);
            for (Need n : shownNeeds()) {
                String plain = GameState.strip(n.name());
                if (!shown(plain)) continue;
                boolean inSacks = sacks && n.inSacks() != null;
                StringBuilder value = new StringBuilder("§e").append(inSacks ? stacks(n.amount()).replaceFirst("^x", "") : stacks(n.amount()));
                if (price) value.append(" §6").append(Prices.coins(n.price()));
                Line line = new Line(plain);
                if (!compact()) line.icon(io.github.zmuzy.sbc.core.ItemIcons.of(n.id()));
                if (inSacks) {
                    line.value("§" + (n.inSacks() >= n.amount() ? 'a' : 'e') + String.format(Locale.ROOT, "%,d", n.inSacks()) + "§7/" + value);
                    line.meter(Math.min(1, (double) n.inSacks() / Math.max(1, n.amount())), n.inSacks() >= n.amount() ? Modern.GOOD : accent());
                } else line.value(value.toString());
                if (n.craftable() > 0) line.chip("CRAFT", Modern.GOOD);
                out.add(line);
            }
            if (demo) return out;
            boolean preview = option(SHOPPING, "garden_visitor_shopping.item_preview", true);
            for (Visitors.Visitor v : Visitors.all()) {
                if (v.status == Visitors.Status.ACCEPTED || v.status == Visitors.Status.REFUSED) continue;
                if (!v.shopping().isEmpty() && !v.ignored()) continue;
                VisitorRepo.Info info = VisitorRepo.all().get(v.name);
                Line row = new Line(v.name).pairs().labelColor(v.ignored() ? Modern.DIM : Modern.INK);
                if (v.ignored()) row.chip("IGNORED", Modern.DIM);
                else {
                    row.chip("NEW", Modern.GOOD);
                    if (preview && info != null) {
                        if (info.needItems().isEmpty()) row.value(info.unknownRewards() ? "§7Unknown" : "§7Any");
                        else {
                            List<ItemStack> icons = new ArrayList<>();
                            for (String need : info.needItems()) {
                                ItemStack icon = VisitorRepo.icon(need);
                                if (icon != null) icons.add(icon);
                            }
                            row.tail(icons);
                        }
                    }
                }
                out.add(row);
            }
            return out;
        }
    }

    // --- drop statistics ---

    // how often each reward came + the rest of VisitorDrops
    // {visited, accepted} summed over every visitor the logbook showed, null till a page was read
    static int[] logbookTotals(GardenData d) {
        if (d.logbook.isEmpty()) return null;
        int visited = 0, accepted = 0;
        for (int[] v : d.logbook.values()) {
            visited += v[0];
            accepted += v[1];
        }
        return new int[]{visited, accepted};
    }

    // the logbooks all time numbers once its read, else what we counted, plus a line saying which
    // so you know to open the book (every page)
    static List<Line> dropLines(GardenData d, boolean compact) {
        List<Line> out = new ArrayList<>();
        int[] book = logbookTotals(d);
        if (book == null) out.add(new Line("§eOpen the Visitor's Logbook").brief("§eOpen logbook").value(""));
        else if (d.logbookPageCount > 0 && d.logbookPages.size() < d.logbookPageCount) out.add(new Line("Logbook pages").brief("Pages").value("§e" + d.logbookPages.size() + "§7/" + d.logbookPageCount + " §7read"));
        out.add(number("Total", book != null ? book[0] : d.acceptedVisitors + d.deniedVisitors, "§e"));
        out.add(number("Accepted", book != null ? book[1] : d.acceptedVisitors, "§2").brief("Accept"));
        out.add(number("Denied", book != null ? book[0] - book[1] : d.deniedVisitors, "§c"));
        out.add(number("Copper", d.copper, "§c"));
        out.add(number("Farming EXP", d.farmingExp, "§3").brief("Farm XP"));
        out.add(number("Garden EXP", d.gardenExp, "§2").brief("Gdn XP"));
        out.add(new Line("Coins Spent").brief("Spent").value("§6" + Prices.coins(d.coinsSpent)));
        StringBuilder rarities = new StringBuilder();
        for (int i = 0; i < RARITIES.length; i++) {
            if (i > 0) rarities.append("§f-");
            rarities.append('§').append(RARITY_COLOR[i]).append(String.format(Locale.ROOT, "%,d", d.acceptedRarities.getOrDefault(RARITIES[i], 0L)));
        }
        out.add(new Line("Rarities").value(rarities.toString()));
        if (d.bits > 0) out.add(number("Bits", d.bits, "§b"));
        if (d.mithrilPowder > 0) out.add(number("Mithril Powder", d.mithrilPowder, "§2").brief("Mithril"));
        if (d.gemstonePowder > 0) out.add(number("Gemstone Powder", d.gemstonePowder, "§d").brief("Gemstone"));
        for (VisitorReward r : VisitorReward.values()) {
            int count = d.rewardsCount.getOrDefault(r.name(), 0);
            if (count > 0) out.add(new Line(r.plain).value("§b" + String.format(Locale.ROOT, "%,d", count)));
        }
        return out;
    }

    private static Line number(String label, int amount, String color) {
        return new Line(label).value(color + String.format(Locale.ROOT, "%,d", amount));
    }

    private static GardenData demoDrops() {
        GardenData d = new GardenData();
        d.acceptedVisitors = 1_284;
        d.deniedVisitors = 96;
        d.copper = 164_210;
        d.farmingExp = 2_480_000;
        d.gardenExp = 41_200;
        d.coinsSpent = 182_400_000L;
        d.acceptedRarities.put("UNCOMMON", 760L);
        d.acceptedRarities.put("RARE", 402L);
        d.acceptedRarities.put("LEGENDARY", 96L);
        d.acceptedRarities.put("MYTHIC", 14L);
        d.acceptedRarities.put("SPECIAL", 12L);
        d.rewardsCount.put("OVERGROWN_GRASS", 12);
        d.rewardsCount.put("GREEN_BANDANA", 3);
        return d;
    }

    private static final class Drops extends GardenCard {
        Drops() {
            super(DROPS, "Visitor statistics", Zone.TOP_LEFT, 38, DROPS);
        }

        @Override
        public ItemStack icon() {
            return io.github.zmuzy.sbc.core.LegacyItems.stack("minecraft:copper_ingot");
        }

        private GardenData data() {
            return demo ? demoDrops() : GardenData.get();
        }

        @Override
        public boolean visible() {
            if (!Visitors.garden()) return false;
            return !option(DROPS, "garden_visitor_drops.only_barn", true) || Visitors.onBarnPlot();
        }

        private List<Row> rowsOf(GardenData d) {
            List<Row> out = new ArrayList<>();
            int[] book = logbookTotals(d);
            out.add(new Row("Total", String.format(Locale.ROOT, "%,d", book != null ? book[0] : d.acceptedVisitors + d.deniedVisitors), Theme.current().accent()));
            out.add(new Row("Accepted", String.format(Locale.ROOT, "%,d", book != null ? book[1] : d.acceptedVisitors), Theme.current().good()));
            out.add(new Row("Denied", String.format(Locale.ROOT, "%,d", book != null ? book[0] - book[1] : d.deniedVisitors), Theme.current().bad()));
            out.add(new Row("Copper", String.format(Locale.ROOT, "%,d", d.copper), Theme.current().accent()));
            out.add(new Row("Farming EXP", String.format(Locale.ROOT, "%,d", d.farmingExp), Theme.current().accent()));
            out.add(new Row("Coins Spent", Prices.coins(d.coinsSpent), Theme.current().accent()));
            return out;
        }

        @Override
        public List<Row> rows() {
            return rowsOf(data());
        }

        @Override
        public List<Row> demo() {
            return rowsOf(demoDrops());
        }

        @Override
        protected List<Block> blocks() {
            List<Block> out = new ArrayList<>();
            for (Line l : dropLines(data(), compact())) out.add(l);
            return out;
        }
    }

    // GardenVisitorDropStatistics.onChat, only in the second after accepting an offer
    static ChatBus.Result onDropChat(ChatBus.Line line) {
        if (!Visitors.onBarnPlot()) return ChatBus.PASS;
        readDropLine(line.plain().trim(), GardenData.get(), System.currentTimeMillis() - lastAccept <= 1000);
        return ChatBus.PASS;
    }

    // one clean line after an accept
    static void readDropLine(String message, GardenData d, boolean afterAccept) {
        if (!afterAccept) return;
        add(message, "SH.garden.visitor.droptracker.copper", v -> d.copper += v);
        add(message, "SH.garden.visitor.droptracker.farmingexp", v -> d.farmingExp += v);
        add(message, "SH.garden.visitor.droptracker.gardenexp", v -> d.gardenExp += v);
        add(message, "SH.garden.visitor.droptracker.bits", v -> d.bits += v);
        add(message, "SH.garden.visitor.droptracker.powder.mithril", v -> d.mithrilPowder += v);
        add(message, "SH.garden.visitor.droptracker.powder.gemstone", v -> d.gemstonePowder += v);
        Matcher m = Patterns.get("SH.garden.visitor.droptracker.accept").matcher(message);
        if (m.matches()) {
            d.acceptedVisitors++;
            d.acceptedRarities.merge(m.group("rarity").toUpperCase(Locale.ROOT), 1L, Long::sum);
            Store.markDirty();
        }
    }

    private static void add(String message, String pattern, java.util.function.IntConsumer to) {
        Matcher m = Patterns.get(pattern).matcher(message);
        if (!m.matches()) return;
        long v = GameState.parseShort(m.group("amount"));
        if (v > 0) to.accept((int) v);
        Store.markDirty();
    }

    // VisitorAcceptEvent
    static void accepted(Visitors.Visitor v) {
        lastAccept = System.currentTimeMillis();
        GardenData d = GardenData.get();
        if (v.offer != null) {
            d.coinsSpent += Math.round(v.offer.fullPrice);
            for (VisitorReward r : v.offer.rareRewards) d.rewardsCount.merge(r.name(), 1, Integer::sum);
        }
        Store.markDirty();
    }

    static void refused(Visitors.Visitor v) {
        GardenData.get().deniedVisitors++;
        Store.markDirty();
    }

    // --- charmed ---

    private static final class Charmed extends GardenCard {
        private static final List<String> DEMO = List.of("Lumber Jack", "Madame Eleanor Q. Goldsworth III");

        Charmed() {
            super(CHARMED, "Charmed visitors", Zone.TOP_LEFT, 39, CHARMED);
        }

        @Override
        public ItemStack icon() {
            return io.github.zmuzy.sbc.core.LegacyItems.stack("minecraft:pink_dye");
        }

        private List<String> names() {
            return demo ? DEMO : new ArrayList<>(GardenData.get().charmedVisitors);
        }

        @Override
        public boolean visible() {
            return Visitors.garden() && !GardenData.get().charmedVisitors.isEmpty();
        }

        @Override
        public List<Row> rows() {
            List<Row> out = new ArrayList<>();
            for (String n : names()) out.add(new Row(n));
            return out;
        }

        @Override
        public List<Row> demo() {
            List<Row> out = new ArrayList<>();
            for (String n : DEMO) out.add(new Row(n));
            return out;
        }

        @Override
        protected String bandValue() {
            return "§d" + names().size();
        }

        @Override
        protected List<Block> blocks() {
            List<Block> out = new ArrayList<>();
            for (String n : names()) {
                VisitorRepo.Info info = VisitorRepo.all().get(n);
                out.add(new Line(n).labelColor(info == null ? Modern.INK : colorOf(info.rarity())));
            }
            return out;
        }
    }

    private static int colorOf(String rarity) {
        return switch (rarity) {
            case "uncommon" -> 0xFF55FF55;
            case "rare" -> 0xFF6FA8FF;
            case "legendary" -> 0xFFFFAA00;
            case "mythic" -> 0xFFFF55FF;
            case "special" -> 0xFFFF5555;
            default -> Modern.INK;
        };
    }

    public static final HudElement SHOPPING_CARD = new Shopping(), DROPS_CARD = new Drops(), CHARMED_CARD = new Charmed();

    static Feature shoppingFeature() {
        return new Feature(SHOPPING, "Garden", "Visitor shopping list", "All the items your visitors want, with their price and how many your sacks hold, on a card and beside a visitor's menu. Click a line beside the menu to find it on the Bazaar with the amount copied; click a visitor there to leave it out of the list.", "SkyHanni: Visitor Shopping List · Skyblocker: Visitor Helper")
                .option(Option.bool("garden_visitor_shopping.only_close", "Only when close", "Only on the Barn plot in the Garden.", false))
                .option(Option.bool("garden_visitor_shopping.bazaar_alley", "In the Bazaar Alley", "Also in the Bazaar Alley of the Hub.", true))
                .option(Option.bool("garden_visitor_shopping.farming_areas", "In the farming areas", "Also on the Farming Islands and in the Farm of the Hub.", true))
                .option(Option.bool("garden_visitor_shopping.show_price", "Show price", "The coin price of each line and of all together.", true))
                .option(Option.bool("garden_visitor_shopping.buy_list", "Into the buy list", "What your visitors want, minus your sacks, waits beside the Bazaar as the buy list.", true))
                .option(Option.bool("garden_visitor_shopping.show_sacks", "Show sack count", "How many your sacks hold, and whether you can craft the rest. Needs your sacks read once.", true))
                .option(Option.bool("garden_visitor_shopping.item_preview", "Item preview", "What a new visitor usually asks for, as icons.", true))
                .option(Option.bool("garden_visitor_shopping.stacks", "Show amounts in stacks", "\"x3 stacks + 5\" from 64 on.", false))
                .option(Option.bool("garden_visitor_shopping.super_craft", "Supercraft button", "Beside a visitor's menu, a button to the recipe when the sacks hold the ingredients of what you lack.", false));
    }

    static Feature dropsFeature() {
        return new Feature(DROPS, "Garden", "Visitor statistics", "What accepted offers brought in on this profile: visitors by rarity, copper, Farming and Garden XP, coins spent and rare rewards. /sbc resetvisitordrops starts over.", "SkyHanni: Visitor Drops Statistics")
                .option(Option.bool("garden_visitor_drops.only_barn", "Only on the Barn plot", "", true));
    }

    static Feature charmedFeature() {
        return new Feature(CHARMED, "Garden", "Charmed visitors", "The visitors you charmed with the Gift Vinyl Set. Read from each visitor's menu; click a name beside the menu to take it off.", "SkyHanni: Charmed Visitors");
    }
}
