// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item.market;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.AuctionPrices;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.sacks.SackApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyhanni NpcTradeHelper + NpcTradeApi, LoreCostUtils, SkyblockCurrency
// in an npc shop each cost line says what you have of it and its worth, the head says what the whole trade
// costs, what you can afford is green
// currencies we dont count show a ? and are never marked affordable
public final class NpcTrade {
    public static final String ID = "npc_trade";
    private static final Patterns.P COST_HEADER = Patterns.p("SH.utils.lore.cost.header");
    private static final Patterns.P TRADE_LINE = Patterns.p("SH.utils.lore.trade.click");
    private static final Patterns.P AMOUNT_FIRST = Patterns.p("SH.utils.lore.cost.amount-first");
    private static final Patterns.P INVALID_NAME = Patterns.p("SH.utils.lore.cost.invalid-currency-names.0");
    private static final Pattern CURRENCY_AMOUNT = Pattern.compile("(?<amount>[\\d,.]+) (?<name>[\\w' ]+)");
    private static final Pattern AMOUNT_BEHIND = Pattern.compile("(?<name>(?:§.)*(?:[^§] ?)+)(?:§8x(?<amount>[\\d,]+))?");
    static final String MISSING_ITEM = "MISSING_ITEM";

    // skyhanni SkyblockCurrency, just the ones with a shop. owned = null for stuff we dont count
    enum Currency {
        COINS("SKYBLOCK_COIN", "§6", "Coin", "Coins", 1.0, () -> GameState.purse, "coin", "coins", "skyblock coin", "skyblock coins", "skyblock_coin", "skyblock_coins"),
        BITS("BITS", "§b", "Bit", "Bits", null, () -> GameState.bits, "bit", "bits"),
        PESTS("PESTS", "§2", "Pest", "Pests", null, null, "pest", "pests"),
        CHOCOLATE("CHOCOLATE", "§6", "Chocolate", "Chocolate", null, null, "chocolate"),
        COPPER("SKYBLOCK_COPPER", "§c", "Copper", "Copper", null, () -> GameState.copper, "copper"),
        GOLD_MEDAL("SKYBLOCK_GOLD_MEDAL", "§6", "Gold medal", "Gold medals", null, null, "gold medal", "gold medals"),
        SILVER_MEDAL("SKYBLOCK_SILVER_MEDAL", "§f", "Silver medal", "Silver medals", null, null, "silver medal", "silver medals"),
        BRONZE_MEDAL("SKYBLOCK_BRONZE_MEDAL", "§c", "Bronze medal", "Bronze medals", null, null, "bronze medal", "bronze medals"),
        PELTS("PELTS", "§5", "Pelt", "Pelts", null, null, "pelt", "pelts"),
        GEMS("GEMS", "§a", "Gem", "Gems", null, null, "gem", "gems"),
        SOWDUST("SOWDUST", "§2", "Sowdust", "Sowdust", null, null, "sowdust"),
        MOTES("SKYBLOCK_MOTE", "§d", "Mote", "Motes", null, () -> GameState.motes, "mote", "motes"),
        KUUDRA_TOKEN("KUUDRA_TOKEN", "§5", "Token", "Tokens", null, null, "token", "tokens"),
        SAFARI_TICKET_BASIC("SAFARI_TICKET_BASIC", "§2", "Basic Safari Ticket", "Basic Safari Tickets", null, null, "basic safari ticket", "basic safari tickets"),
        SAFARI_TICKET_ECONOMY("SAFARI_TICKET_ECONOMY", "§9", "Economy Safari Ticket", "Economy Safari Tickets", null, null, "economy safari ticket", "economy safari tickets"),
        SAFARI_TICKET_PREMIUM("SAFARI_TICKET_PREMIUM", "§5", "Premium Safari Ticket", "Premium Safari Tickets", null, null, "premium safari ticket", "premium safari tickets"),
        SAFARI_TICKET_FIRST_CLASS("SAFARI_TICKET_FIRST_CLASS", "§6", "First-Class Safari Ticket", "First-Class Safari Tickets", null, null, "first-class safari ticket", "first-class safari tickets"),
        CARNIVAL_TOKEN("SKYBLOCK_CARNIVAL_POINT", "§e", "Carnival Token", "Carnival Tokens", null, null, "carnival token", "carnival tokens"),
        KERNEL("SKYBLOCK_KERNEL", "§6", "Kernel", "Kernels", null, null, "kernel", "kernels");

        final String id, color, singular, plural;
        final Double coinValue;
        final java.util.function.LongSupplier owned;
        final List<String> loreNames;

        Currency(String id, String color, String singular, String plural, Double coinValue, java.util.function.LongSupplier owned, String... loreNames) {
            this.id = id;
            this.color = color;
            this.singular = singular;
            this.plural = plural;
            this.coinValue = coinValue;
            this.owned = owned;
            this.loreNames = List.of(loreNames);
        }

        static Currency byLoreName(String name) {
            String clean = GameState.strip(name).toLowerCase(Locale.ROOT);
            for (Currency c : values()) if (c.loreNames.contains(clean)) return c;
            return null;
        }

        static Currency byId(String id) {
            for (Currency c : values()) if (c.id.equals(id)) return c;
            return null;
        }

        // skyhanni formatAmount: "§b5,000 Bits"
        String format(long amount) {
            return color + String.format(Locale.ROOT, "%,d", amount) + " " + (amount == 1 ? singular : plural);
        }

        Long ownedOrNull() {
            if (owned == null) return null;
            long v = owned.getAsLong();
            return v < 0 ? null : v;
        }
    }

    // from skyhanni LoreCostEntry
    public record Cost(String id, long amount, String rawLine) {}

    // what the trade helper counts an item as worth
    public static double priceOf(String id) {
        return price.applyAsDouble(id);
    }

    // from skyhanni NpcTradeHelper.CostLine
    record CostLine(String rawLine, String text, boolean covered, boolean evaluable) {
        String cleanLine() {
            return GameState.strip(rawLine);
        }
    }

    // from skyhanni NpcTradeHelper.TradeItem
    record TradeItem(List<CostLine> lines, String headerSuffix) {
        boolean canAfford() {
            for (CostLine l : lines) if (!l.evaluable() || !l.covered()) return false;
            return true;
        }
    }

    private static Map<String, TradeItem> trades = Map.of();
    // ItemNameResolver + the prices, swappable in tests
    static java.util.function.Function<String, String> idOfName = ItemRepo::idForName;
    static java.util.function.ToDoubleFunction<String> price = id -> {
        Prices.Product p = Prices.get(id);
        return p != null ? p.buy() : AuctionPrices.value(id);
    };
    static ToLongFunction<String> inInventory = NpcTrade::countInInventory;

    private NpcTrade() {}

    private static long parseLong(String s) {
        return (long) Double.parseDouble(s.replace(",", ""));
    }

    // skyhanni readCurrencyOrNull: "5,000 Bits"
    private static Cost readCurrency(String line, String rawLine) {
        Matcher m = CURRENCY_AMOUNT.matcher(GameState.strip(line));
        if (!m.matches()) return null;
        Currency c = Currency.byLoreName(m.group("name"));
        return c == null ? null : new Cost(c.id, parseLong(m.group("amount")), rawLine);
    }

    // skyhanni readAmountFirstOrNull: "50,000 Undead Essence"
    private static Cost readAmountFirst(String line, String rawLine) {
        Matcher m = AMOUNT_FIRST.matcher(GameState.strip(line));
        if (!m.matches()) return null;
        String id = idOfName.apply(m.group("name"));
        if (id == null) id = essenceId(m.group("name"));
        return id == null ? null : new Cost(id, parseLong(m.group("amount")), rawLine);
    }

    // "Undead Essence" is ESSENCE_UNDEAD on the bazaar
    private static String essenceId(String name) {
        if (!name.endsWith(" Essence")) return null;
        return "ESSENCE_" + name.substring(0, name.length() - " Essence".length()).toUpperCase(Locale.ROOT);
    }

    // from skyhanni readCostLine
    static Cost readCostLine(String rawLine) {
        String line = rawLine.trim().replace("§8 ", " §8");
        Cost c = readCurrency(line, rawLine);
        if (c != null) return c;
        c = readAmountFirst(line, rawLine);
        if (c != null) return c;
        // skyhanni ItemUtils.readItemAmount, the form with the amount after the name
        String name = line;
        int amount = 1;
        if (line.length() > 2) {
            Matcher m = AMOUNT_BEHIND.matcher(line.substring(2));
            if (m.matches()) {
                name = line.substring(0, 2) + m.group("name").trim();
                if (m.group("amount") != null) amount = Integer.parseInt(m.group("amount").replace(",", ""));
            }
        }
        String clean = GameState.strip(name).trim();
        if (INVALID_NAME.matcher(clean).matches()) return new Cost(MISSING_ITEM, amount, rawLine);
        String id = idOfName.apply(clean);
        if (id == null) {
            Currency currency = Currency.byLoreName(clean);
            id = currency != null ? currency.id : MISSING_ITEM;
        }
        return new Cost(id, amount, rawLine);
    }

    // from skyhanni readLoreCosts
    public static List<Cost> readLoreCosts(List<String> lore) {
        int header = -1;
        for (int i = 0; i < lore.size(); i++) if (COST_HEADER.matcher(lore.get(i)).matches()) header = i;
        if (header < 0) return List.of();
        Matcher m = COST_HEADER.matcher(lore.get(header));
        if (m.matches() && m.group("cost") != null) return List.of(readCostLine(m.group("cost")));
        List<Cost> out = new ArrayList<>();
        for (int i = header + 1; i < lore.size() && !GameState.strip(lore.get(i)).isEmpty(); i++) out.add(readCostLine(lore.get(i)));
        return out;
    }

    public static boolean hasTradeLine(List<String> lore) {
        for (String l : lore) if (TRADE_LINE.matcher(GameState.strip(l)).matches()) return true;
        return false;
    }

    private static long countInInventory(String id) {
        var mc = Minecraft.getInstance();
        if (mc.player == null) return 0;
        long n = 0;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (id.equals(Gain.sbId(st))) n += st.getCount();
        }
        return n;
    }

    // skyhanni NeuInternalName.getPriceName: "§8x §rName §7(§6price§7)"
    private static String priceName(String id, long amount) {
        String name = ItemRepo.coloredName(id);
        if (name == null || name.isEmpty()) name = id;
        String amountText = amount == 1 ? "" : "§8" + String.format(Locale.ROOT, "%,d", amount) + "x §r";
        return amountText + name + " §7(§6" + Prices.coins(price.applyAsDouble(id) * amount) + "§7)";
    }

    // skyhanni buildCostLine. essence is a repo item you dont carry and we dont count it, so it reads as unknown
    static CostLine costLine(Cost entry, Currency currency) {
        String id = entry.id();
        long amount = entry.amount();
        Long owned;
        if (id.equals(MISSING_ITEM)) owned = null;
        else if (currency != null) owned = currency.ownedOrNull();
        else if (id.startsWith("ESSENCE_")) owned = null;
        else owned = inInventory.applyAsLong(id);
        boolean covered = owned != null && owned >= amount;
        String suffix;
        if (id.equals(MISSING_ITEM)) suffix = "§c!";
        else if (owned == null) suffix = " §8?§7/§8" + String.format(Locale.ROOT, "%,d", amount);
        else if (covered) suffix = " §a✔";
        else suffix = " §8" + String.format(Locale.ROOT, "%,d", owned) + "§7/§8" + String.format(Locale.ROOT, "%,d", amount);
        Integer sacks = currency == null ? SackApi.getAmountInSacksOrNull(id) : null;
        String sackText = sacks != null && sacks > 0 ? " §7(sacks: §a" + String.format(Locale.ROOT, "%,d", sacks) + "§7)" : "";
        String name;
        if (id.equals(MISSING_ITEM)) name = entry.rawLine().stripLeading();
        else if (currency != null && currency.coinValue == null) name = currency.format(amount);
        else name = priceName(id, amount);
        String indent = entry.rawLine().substring(0, entry.rawLine().length() - entry.rawLine().stripLeading().length());
        return new CostLine(entry.rawLine(), indent + name + suffix + sackText, covered, owned != null);
    }

    // from skyhanni buildTradeItem
    static TradeItem tradeItem(List<Cost> costs) {
        double coinTotal = 0;
        List<String> otherCosts = new ArrayList<>();
        List<CostLine> lines = new ArrayList<>();
        for (Cost entry : costs) {
            Currency currency = Currency.byId(entry.id());
            if (currency != null && currency.coinValue == null) otherCosts.add(currency.format(entry.amount()));
            else if (currency != null) coinTotal += currency.coinValue * entry.amount();
            else coinTotal += price.applyAsDouble(entry.id()) * entry.amount();
            lines.add(costLine(entry, currency));
        }
        List<String> parts = new ArrayList<>();
        if (coinTotal > 0) parts.add("§6" + Prices.coins(coinTotal));
        parts.addAll(otherCosts);
        return new TradeItem(lines, parts.isEmpty() ? "" : " §7(" + String.join(" §7+ ", parts) + "§7)");
    }

    // skyhanni NpcTradeApi.readTrades from Markets tick. a menu without trades leaves the old ones alone, same as skyhanni
    static void onMenu(String title, ChestMenu menu, int n) {
        if (!Features.on(ID) || title.startsWith("Heart of the ")) return;
        Map<String, TradeItem> found = new HashMap<>();
        for (int i = 0; i < n; i++) {
            ItemStack st = menu.slots.get(i).getItem();
            if (st.isEmpty()) continue;
            List<String> lore = LegacyText.lore(st);
            if (!hasTradeLine(lore)) continue;
            List<Cost> costs = readLoreCosts(lore);
            if (!costs.isEmpty()) found.put(GameState.strip(st.getHoverName().getString()).trim(), tradeItem(costs));
        }
        if (!found.isEmpty()) trades = found;
    }

    // from skyhanni onToolTip
    private static void tooltip(ItemStack stack, List<Component> lines) {
        if (!Market.b(ID, "npc_trade.costBreakdown", true)) return;
        TradeItem item = trades.get(GameState.strip(stack.getHoverName().getString()).trim());
        if (item == null) return;
        for (int i = 0; i < lines.size(); i++) {
            String line = LegacyText.of(lines.get(i));
            if (COST_HEADER.matcher(line).matches()) {
                String newLine = line;
                for (CostLine l : item.lines()) {
                    if (line.contains(l.rawLine())) {
                        newLine = line.replace(l.rawLine(), l.text());
                        break;
                    }
                }
                lines.set(i, Component.literal(newLine + item.headerSuffix()));
                continue;
            }
            String clean = GameState.strip(line);
            for (CostLine l : item.lines()) {
                if (l.cleanLine().equals(clean)) {
                    lines.set(i, Component.literal(l.text()));
                    break;
                }
            }
        }
    }

    // skyhanni onBackgroundDrawn, from Markets slot hook
    static void slot(GuiGraphicsExtractor g, Slot slot, ItemStack st) {
        if (!Features.on(ID) || !Market.b(ID, "npc_trade.highlightAffordable", false) || trades.isEmpty()) return;
        TradeItem item = trades.get(GameState.strip(st.getHoverName().getString()).trim());
        if (item != null && item.canAfford()) g.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x8055FF55);
    }

    static void clear() {
        trades = Map.of();
    }

    public static void register() {
        Features.add(new Feature(ID, "Menus", "NPC trade helper", "In an NPC shop each cost line says what you have of it and what it is worth, and the head what the whole trade costs. What you can afford can be marked green.", "SkyHanni: NPC trade helper")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).tooltip(NpcTrade::tooltip)
                .option(Option.bool("npc_trade.costBreakdown", "Cost breakdown", "", true))
                .option(Option.bool("npc_trade.highlightAffordable", "Mark what you can afford", "", false)));
    }
}
