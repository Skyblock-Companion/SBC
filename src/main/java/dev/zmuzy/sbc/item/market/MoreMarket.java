// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item.market;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.AuctionPrices;
import dev.zmuzy.sbc.core.Gain;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemInfo;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.item.sacks.SackApi;
import dev.zmuzy.sbc.item.value.EstimatedItemValue;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

// the side panel stuff next to a menu thats not the bazaar itself
// from skyhanni CraftMaterialCollector (what a recipe needs + the list to buy it), CraftableItemList (what
// you can craft rn), TradeValue (what each side of a trade is worth)
// numbers worked out once per menu change, ui.MarketPanel draws them
public final class MoreMarket {
    public static final String CRAFTABLE = "craftable_list", TRADE = "trade_value";

    private static final int[] MATERIAL_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    // from skyhanni CraftMaterialCollector.addMultipliers
    public static final int[] MULTIPLIERS = {1, 5, 16, 32, 64, 512};
    private static final Patterns.P CRAFT_ITEM = Patterns.p("SH.craftableitemlist.craftitem");
    private static final Patterns.P TRADE_MENU = Patterns.p("SH.inventory.tradevalue.menu");
    private static final Patterns.P TRADE_COINS = Patterns.p("SH.inventory.tradevalue.coins");
    private static final Set<String> HARDCODED_VANILLA = Set.of("WOOD_AXE", "WOOD_HOE", "WOOD_PICKAXE", "WOOD_SPADE", "WOOD_SWORD", "GOLD_AXE", "GOLD_HOE", "GOLD_PICKAXE", "GOLD_SPADE", "GOLD_SWORD");

    public record Material(String id, int amount) {}

    // skyhanni ChestValue.ChestItem minus the tooltip: one kind of thing in a trade or list
    public record Line(String id, String name, long amount, double total, ItemStack icon) {}

    // one craftable: how many times + its materials
    public record Craftable(String id, int times, double price, Map<String, Integer> need) {}

    // what a recipe needs

    public static boolean inRecipe, purchasing;
    // the buy list holds what the garden visitors want, and follows them
    public static boolean fromVisitors;
    public static String recipeName = "";
    public static List<Material> materials = List.of(), buying = List.of();
    public static int multiplier = 1;

    // what you can craft

    public static boolean inCraftMenu;
    public static List<Craftable> craftable = List.of();

    // trade

    public static boolean inTrade;
    public static double yourTotal, otherTotal;
    public static List<Line> yourItems = List.of(), otherItems = List.of();

    private MoreMarket() {}

    // skyhanni getPrice with the default source: bz instant buy, else lowest bin, else whatever is known
    public static double price(String id) {
        Prices.Product p = Prices.get(id);
        if (p != null && p.buy() > 0) return p.buy();
        double bin = AuctionPrices.lowestBin(id);
        return bin > 0 ? bin : AuctionPrices.value(id);
    }

    public static boolean onMarket(String id) {
        return Prices.get(id) != null || AuctionPrices.lowestBin(id) > 0;
    }

    // from skyhanni calculateTotalPrice
    public static double totalPrice(List<Material> list, int times) {
        double total = 0;
        for (Material m : list) total += price(m.id()) * m.amount() * times;
        return total;
    }

    // skyhanni calculateMaterialsNeeded: same item in several slots = one line
    static List<Material> merge(List<Material> slots) {
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (Material m : slots) merged.merge(m.id(), m.amount(), Integer::sum);
        List<Material> out = new ArrayList<>();
        for (var e : merged.entrySet()) out.add(new Material(e.getKey(), e.getValue()));
        return out;
    }

    // skyhanni addToPurchasing with what showRecipe kept, only stuff that can be bought
    public static void startBuying() {
        List<Material> needed = new ArrayList<>();
        for (Material m : materials) if (onMarket(m.id())) needed.add(m);
        recipeList(needed);
    }

    // a recipe's materials as the list
    static void recipeList(List<Material> list) {
        base = list;
        multiplier = 1;
        fromVisitors = false;
        rebuild();
        purchasing = true;
    }

    public static void stopBuying() {
        if (fromVisitors) dismissed = base;
        purchasing = false;
        fromVisitors = false;
        base = List.of();
        manual.clear();
        buying = List.of();
        removed.clear();
    }

    // taken off the list by hand. for the visitors' list they stay off while still wanted
    private static final java.util.Set<String> removed = new java.util.HashSet<>();
    // what the visitors or a recipe want + what you added with j, the list is both
    private static List<Material> base = List.of();
    private static final Map<String, Integer> manual = new LinkedHashMap<>();

    private static void rebuild() {
        Map<String, Integer> all = new LinkedHashMap<>();
        for (Material m : base) all.merge(m.id(), m.amount(), Integer::sum);
        for (var e : manual.entrySet()) all.merge(e.getKey(), e.getValue(), Integer::sum);
        List<Material> out = new ArrayList<>();
        for (var e : all.entrySet()) out.add(new Material(e.getKey(), e.getValue()));
        buying = out;
        purchasing = !out.isEmpty();
    }

    // one more of it by hand, from any menu. how many it holds now
    public static int add(String id, int amount) {
        manual.merge(id, amount, Integer::sum);
        removed.remove(id);
        rebuild();
        for (Material m : buying) if (m.id().equals(id)) return m.amount();
        return amount;
    }

    public static void remove(String id) {
        manual.remove(id);
        List<Material> left = new ArrayList<>();
        for (Material m : base) if (!m.id().equals(id)) left.add(m);
        base = left;
        if (fromVisitors) removed.add(id);
        rebuild();
        if (!purchasing) stopBuying();
    }

    // a visitors list you closed stays closed till they want something else
    private static List<Material> dismissed = List.of();

    // what the visitors still need. a list you made from a recipe stays yours
    public static void visitorsWant(List<Material> want) {
        if (purchasing && !fromVisitors && !base.isEmpty()) return;
        if (!fromVisitors && base.isEmpty() && want.equals(dismissed)) return;
        if (want.isEmpty()) {
            if (fromVisitors) {
                fromVisitors = false;
                base = List.of();
                rebuild();
            }
            dismissed = List.of();
            return;
        }
        List<Material> kept = new ArrayList<>();
        for (Material m : want) if (!removed.contains(m.id())) kept.add(m);
        removed.removeIf(id -> want.stream().noneMatch(m -> m.id().equals(id)));
        if (kept.isEmpty()) {
            if (fromVisitors) {
                fromVisitors = false;
                base = List.of();
                rebuild();
            }
            return;
        }
        if (!kept.equals(base)) multiplier = 1;
        base = kept;
        fromVisitors = true;
        rebuild();
    }

    private static String hover(ChestMenu menu, int slot) {
        return menu.slots.get(slot).getItem().getHoverName().getString();
    }

    // from Markets tick when the open menu changed
    static void onMenu(String title, int n, ChestMenu menu) {
        readRecipe(n, menu);
        readCraftMenu(title);
        readTrade(title, n, menu);
    }

    static void onClose() {
        inRecipe = false;
        inCraftMenu = false;
        inTrade = false;
        craftable = List.of();
        yourItems = List.of();
        otherItems = List.of();
        yourTotal = 0;
        otherTotal = 0;
    }

    // from skyhanni CraftMaterialCollector.onInventoryFullyOpened
    private static void readRecipe(int n, ChestMenu menu) {
        inRecipe = false;
        if (n <= 32 || !Market.b(Market.BAZAAR, "market_bazaar.craftMaterials", true) || !Features.on(Market.BAZAAR)) return;
        if (purchasing || !"Crafting Table".equals(hover(menu, 23)) || !"Supercraft".equals(hover(menu, 32))) return;
        String resultId = Gain.sbId(menu.slots.get(25).getItem());
        if (resultId.isEmpty()) return;
        List<Material> slots = new ArrayList<>();
        for (int s : MATERIAL_SLOTS) {
            ItemStack st = menu.slots.get(s).getItem();
            String id = Gain.sbId(st);
            if (!id.isEmpty()) slots.add(new Material(id, st.getCount()));
        }
        materials = merge(slots);
        recipeName = ItemRepo.coloredName(resultId);
        inRecipe = true;
    }

    // CraftableItemList

    private static boolean option(String key, boolean def) {
        return Market.b(CRAFTABLE, key, def);
    }

    // skyhanni isVanillaItem. our repo has no vanilla flag, so an id thats its own mc material counts
    static boolean isVanilla(String id) {
        if (HARDCODED_VANILLA.contains(id)) return true;
        ItemRepo.Entry e = ItemRepo.get(id);
        return e != null && e.material() != null && e.material().equalsIgnoreCase("minecraft:" + id);
    }

    // skyhanni canCraftAmount: whichever material runs out first
    static int canCraftAmount(Map<String, Integer> need, Map<String, Long> available) {
        int min = Integer.MAX_VALUE;
        for (var e : need.entrySet()) {
            long having = available.getOrDefault(e.getKey(), 0L);
            min = Math.min(min, (int) Math.floor((double) having / e.getValue()));
        }
        return min;
    }

    // from skyhanni readItems
    private static Map<String, Long> availableMaterials() {
        Map<String, Long> materials = new HashMap<>();
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            var inv = mc.player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack st = inv.getItem(i);
                String id = Gain.sbId(st);
                if (!id.isEmpty()) materials.merge(id, (long) st.getCount(), Long::sum);
            }
        }
        if (option("craftable_list.includeSacks", false)) for (var e : SackApi.sackData.entrySet()) materials.merge(e.getKey(), (long) e.getValue().amount(), Long::sum);
        return materials;
    }

    private static void readCraftMenu(String title) {
        inCraftMenu = Features.on(CRAFTABLE) && CRAFT_ITEM.matcher(title).matches();
        if (!inCraftMenu) {
            craftable = List.of();
            return;
        }
        Map<String, Long> available = availableMaterials();
        boolean noVanilla = option("craftable_list.excludeVanilla", true);
        List<Craftable> out = new ArrayList<>();
        for (String id : ItemRepo.ids()) {
            if (noVanilla && isVanilla(id)) continue;
            for (ItemInfo.Recipe r : ItemInfo.recipes(id)) {
                if (!r.type().equals("crafting") || r.parts().isEmpty()) continue;
                Map<String, Integer> need = new LinkedHashMap<>();
                for (ItemInfo.Part p : r.parts()) need.merge(p.id(), p.count(), Integer::sum);
                int times = canCraftAmount(need, available);
                if (times <= 0) continue;
                double total = 0;
                for (var e : need.entrySet()) total += price(e.getKey()) * e.getValue();
                out.add(new Craftable(id, times, total, need));
                break;
            }
        }
        out.sort((a, b) -> Double.compare(b.price(), a.price()));
        craftable = out;
    }

    // TradeValue

    private static double value(ItemStack st) {
        String id = Gain.sbId(st);
        var cd = st.get(DataComponents.CUSTOM_DATA);
        if (id.isEmpty() || cd == null) return 0;
        List<String> lore = LegacyText.lore(st);
        boolean dungeon = lore.stream().anyMatch(l -> l.contains("DUNGEON "));
        return EstimatedItemValue.calculate(cd.copyTag(), id, dungeon, lore).totalPrice();
    }

    // lump sum in the trade: last line of the coins item, "§71" or "§8(1,000)"
    static Double coinValue(List<String> lore) {
        if (!lore.contains("§7Lump-sum amount")) return null;
        Matcher m = TRADE_COINS.matcher(lore.getLast().trim());
        if (!m.matches()) return 0d;
        try {
            return Double.parseDouble(m.group("number").replace(",", ""));
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    private static void readTrade(String title, int n, ChestMenu menu) {
        inTrade = Features.on(TRADE) && TRADE_MENU.matcher(title).matches();
        if (!inTrade) return;
        Map<String, Line> yours = new LinkedHashMap<>(), others = new LinkedHashMap<>();
        double[] totals = new double[2];
        for (int y = 0; y < 4; y++) {
            for (int x = 0; x < 9; x++) {
                if (x == 4) continue;
                int slot = x + 9 * y;
                if (slot >= n) continue;
                ItemStack st = menu.slots.get(slot).getItem();
                if (st.isEmpty()) continue;
                boolean mine = x < 4;
                Map<String, Line> side = mine ? yours : others;
                Double coins = coinValue(LegacyText.lore(st));
                if (coins != null) {
                    totals[mine ? 0 : 1] += coins;
                    if (coins > 0) side.put("COIN", new Line("", "§6Coins", 1, coins, st));
                    continue;
                }
                String id = Gain.sbId(st);
                if (id.isEmpty()) continue;
                double each = value(st), total = each * st.getCount();
                totals[mine ? 0 : 1] += total;
                if (each == 0) continue;
                // skyhanni ChestValue.createItems: same item at the same worth goes on one line
                String key = id + "+" + each;
                Line old = side.get(key);
                side.put(key, new Line(id, LegacyText.name(st), (old == null ? 0 : old.amount()) + st.getCount(), (old == null ? 0 : old.total()) + total, st));
            }
        }
        yourTotal = totals[0];
        otherTotal = totals[1];
        yourItems = sorted(yours);
        otherItems = sorted(others);
    }

    private static List<Line> sorted(Map<String, Line> side) {
        List<Line> out = new ArrayList<>(side.values());
        out.sort((a, b) -> Double.compare(b.total(), a.total()));
        return out;
    }

    public static void register() {
        Features.add(new Feature(CRAFTABLE, "Menus", "Craftable item list", "Beside the Craft Item menu: what you could craft from your inventory, how many times and what it costs. A click opens its recipe.", "SkyHanni: craftable item list")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.bool("craftable_list.includeSacks", "Include sacks", "Count what is in your sacks too.", false))
                .option(Option.bool("craftable_list.excludeVanilla", "Leave vanilla items out", "", true)));
        Features.add(new Feature(TRADE, "Menus", "Trade value", "Beside a trade with another player: what your side and theirs are worth, with what makes up each.", "SkyHanni: trade value")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV));
    }
}
