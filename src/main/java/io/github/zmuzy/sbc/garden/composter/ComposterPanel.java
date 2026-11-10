// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.composter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.ItemIcons;
import io.github.zmuzy.sbc.core.ItemRepo;
import io.github.zmuzy.sbc.core.LegacyItems;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.core.Store;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.garden.GardenData;
import io.github.zmuzy.sbc.garden.GardenItems;
import io.github.zmuzy.sbc.item.menus.MenuMarks;
import io.github.zmuzy.sbc.item.menus.MenuView;
import io.github.zmuzy.sbc.item.sacks.Sacks;
import io.github.zmuzy.sbc.ui.SideHelper;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

// from skyhanni ComposterOverlay, next to the menu: cheapest way to fill organic matter + fuel, what a
// compost costs and earns with your picks
// in Composter Upgrades, what hovering an upgrade would change. numbers from Garden.json + your upgrades
public final class ComposterPanel implements SideHelper.Content {
    public static final String FEATURE = "composter_overlay";
    public static final ComposterPanel I = new ComposterPanel();
    private static final int ROW = 12;
    // blockedItems: their recipes are crops but theyre not worth their crops
    private static final Set<String> BLOCKED = Set.of("POTION_AFFINITY_TALISMAN", "CROPIE_TALISMAN", "SPEED_TALISMAN", "SIMPLE_CARROT_CANDY");

    // TimeType
    private enum TimeType {
        COMPOST("Compost", 1), HOUR("Hour", 3600), DAY("Day", 86400);

        final String display;
        final int seconds;

        TimeType(String display, int seconds) {
            this.display = display;
            this.seconds = seconds;
        }
    }

    public record Choice(String id, double factor, double price, int needed) {}

    private static Map<String, Double> baseOrganic = new HashMap<>(), fuelFactors = new HashMap<>();
    private static Map<String, Double> organicFactors = new HashMap<>();
    private static double factorsFor = -1;

    private TimeType timeType = TimeType.HOUR;
    private List<Choice> organic = List.of(), fuel = List.of();
    private long clickedAt;

    private ComposterPanel() {}

    public static SideHelper.Content of(Screen s) {
        if (!Features.on(FEATURE) || !"GARDEN".equals(GameState.island)) return null;
        String t = GameState.strip(s.getTitle().getString()).trim();
        return inComposter(t) || inUpgrades(t) ? I : null;
    }

    private static boolean inComposter(String title) {
        return Patterns.get("SH.composter.inventory").matcher(title).matches();
    }

    private static boolean inUpgrades(String title) {
        return Patterns.get("SH.composter.upgrades.inventory").matcher(title).matches();
    }

    private static Option option(String key) {
        Feature f = Features.get(FEATURE);
        return f == null ? null : f.option(key);
    }

    private static boolean bool(String key, boolean otherwise) {
        Option o = option(key);
        return o == null ? otherwise : o.bool();
    }

    private static void loadRepo() {
        if (!baseOrganic.isEmpty()) return;
        try (var in = io.github.zmuzy.sbc.core.data.DataFiles.sh("Garden.json")) {
            if (in == null) return;
            JsonObject all = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<String, Double> matter = new HashMap<>(), fuelMap = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : all.getAsJsonObject("organic_matter").entrySet()) matter.put(e.getKey().replace('-', ':'), e.getValue().getAsDouble());
            for (Map.Entry<String, JsonElement> e : all.getAsJsonObject("fuel").entrySet()) fuelMap.put(e.getKey(), e.getValue().getAsDouble());
            baseOrganic = matter;
            fuelFactors = fuelMap;
        } catch (Exception e) {
            io.github.zmuzy.sbc.SbcMod.LOG.warn("No composter data in Garden.json", e);
        }
    }

    // isArmor: armor made from these crops would count otherwise
    private static boolean armor(String id) {
        return id.endsWith("_HELMET") || id.endsWith("_CHESTPLATE") || id.endsWith("_LEGGINGS") || id.endsWith("_BOOTS");
    }

    // updateOrganicMatterFactors
    static Map<String, Double> factors(Iterable<String> ids, Map<String, Double> base, double minimum) {
        Map<String, Double> out = new HashMap<>();
        for (String id : ids) {
            if (BLOCKED.contains(id) || armor(id)) continue;
            GardenItems.Primitive p = GardenItems.primitive(id);
            // 160 * 8 * 4 is 5120 not 5184, but hypixel got it wrong so we copy it lol
            int amount = id.equals("ENCHANTED_HUGE_MUSHROOM_1") || id.equals("ENCHANTED_HUGE_MUSHROOM_2") ? 5184 : p.amount();
            Double value = base.get(p.id());
            if (value == null) continue;
            double total = value * amount;
            if (total > minimum) out.put(id, total);
        }
        return out;
    }

    private static double price(String id) {
        Option source = option("composter_overlay.price_source");
        double price = GardenItems.price(id, GardenItems.Source.of(source == null ? "Instant buy" : source.text()));
        return id.equals("BIOFUEL") && price > 20_000 ? 20_000 : price;
    }

    // calculateFirst: the 10 cheapest for whats missing
    static List<Choice> choices(Map<String, Double> factors, double missing, boolean roundDown, java.util.function.ToDoubleFunction<String> priceOf) {
        List<Choice> out = new ArrayList<>();
        for (var e : factors.entrySet()) {
            double price = priceOf.applyAsDouble(e.getKey());
            if (price <= 0) continue;
            double amount = missing / e.getValue();
            int needed = roundDown ? (amount > .75 && amount < 1.0 ? 1 : (int) Math.floor(amount)) : (int) Math.ceil(amount);
            out.add(new Choice(e.getKey(), e.getValue(), price, needed));
        }
        out.sort(Comparator.comparingDouble((Choice c) -> c.factor() / c.price()).reversed());
        return out.size() > 10 ? new ArrayList<>(out.subList(0, 10)) : out;
    }

    @Override
    public String title() {
        return "Composter Helper";
    }

    @Override
    public ItemStack icon() {
        return LegacyItems.stack("minecraft:composter");
    }

    @Override
    public int accent() {
        return 0xFFB5D65F;
    }

    private static String menuTitle() {
        MenuView v = MenuMarks.current();
        return v == null ? "" : v.title;
    }

    @Override
    public String[] tabs() {
        return inUpgrades(menuTitle()) ? new String[]{"Upgrades"} : new String[]{"Organic Matter", "Fuel"};
    }

    @Override
    public void refresh(AbstractContainerScreen<?> screen) {
        loadRepo();
        Option min = option("composter_overlay.min_matter");
        double minimum = min == null ? 1000 : min.number();
        if (factorsFor != minimum || organicFactors.isEmpty()) {
            organicFactors = factors(ItemRepo.ids(), baseOrganic, minimum);
            factorsFor = minimum;
        }
        Composter.Tab tab = Composter.readTab(GameState.tab);
        boolean round = bool("composter_overlay.round_down", true);
        organic = choices(organicFactors, Composter.maxOrganicMatter(null) - tab.organicMatter(), round, ComposterPanel::price);
        fuel = choices(fuelFactors, Composter.maxFuel(null) - tab.fuel(), round, ComposterPanel::price);
        GardenData data = GardenData.get();
        if (!organicFactors.containsKey(data.composterOrganicItem) && !organic.isEmpty()) data.composterOrganicItem = organic.get(0).id();
        if (!fuelFactors.containsKey(data.composterFuelItem) && !fuel.isEmpty()) data.composterFuelItem = fuel.get(0).id();
    }

    // upgrade under the mouse in Composter Upgrades, null for none or maxed. its onToolTip
    private static String hovered() {
        MenuView v = MenuMarks.current();
        int slot = MenuMarks.hoveredSlot();
        if (v == null || slot < 0 || slot >= v.top || !inUpgrades(v.title)) return null;
        Matcher m = Patterns.get("SH.composter.upgrade.name").matcher(v.item(slot).clean);
        if (!m.matches()) return null;
        return m.group("level") != null && Composter.levelOf(m.group("level")) == 25 ? null : m.group("name");
    }

    private static String arrow(String now, String preview, String color) {
        return now.equals(preview) ? color + now : color + now + " §c➜ " + color + preview;
    }

    // what a compost makes: profitDisplay's numbers per what the button says
    private record Profit(String per, double perUnit, double cost, double profit) {}

    private Profit profit(String upgrade) {
        GardenData data = GardenData.get();
        long time = Composter.timePerCompost(upgrade);
        double multiplier = timeType == TimeType.COMPOST ? 1 : timeType.seconds * 1000.0 / time;
        double drops = Composter.multiDropChance(upgrade) + 1;
        double matterFactor = organicFactors.getOrDefault(data.composterOrganicItem, 1.0), fuelFactor = fuelFactors.getOrDefault(data.composterFuelItem, 1.0);
        double matterPer = price(data.composterOrganicItem) * (Composter.organicMatterPer(upgrade) / matterFactor);
        double fuelPer = price(data.composterFuelItem) * (Composter.fuelPer(upgrade) / fuelFactor);
        double cost = (fuelPer + matterPer) * multiplier;
        double profit = (GardenItems.price("COMPOST", sourceOf()) * drops - (fuelPer + matterPer)) * multiplier;
        return new Profit(timeType.display.toLowerCase(Locale.ROOT), drops * multiplier, cost, profit);
    }

    private static GardenItems.Source sourceOf() {
        Option source = option("composter_overlay.price_source");
        return GardenItems.Source.of(source == null ? "Instant buy" : source.text());
    }

    private static int line(Ui ui, String label, String value, int x, int y, int w) {
        ui.textShadow(label, x, y + 1, Modern.LABEL);
        ui.textShadow(value, x + w - ui.width(value), y + 1, Modern.INK);
        return 10;
    }

    @Override
    public int summary(Ui ui, int x, int y, int w) {
        int y0 = y;
        if (GardenData.get().composterUpgrades.isEmpty()) {
            ui.textShadow(ui.ellipsize("Open Composter Upgrades!", w), x, y + 1, Modern.BAD);
            return 12;
        }
        GardenData data = GardenData.get();
        boolean upgrades = inUpgrades(menuTitle());
        String upgrade = upgrades ? hovered() : null;
        if (upgrades) {
            String label = upgrade == null ? "§7Preview: Nothing" : "§7Preview §a" + upgrade + "§7: §a" + Composter.level(upgrade, null) + " §c➜ §a" + (Composter.level(upgrade, null) + 1);
            ui.textShadow(ui.ellipsize(label, w), x, y + 1, Modern.LABEL);
            y += 12;
        }
        // the Display button
        int cx = x;
        for (TimeType t : TimeType.values()) {
            int bw = ui.width(t.display) + 6;
            boolean on = t == timeType;
            ui.rect(cx, y, bw, 11, on ? 0x66FFFFFF : 0x44000000);
            ui.textShadow(t.display, cx + 3, y + 2, on ? Modern.INK : Modern.LABEL);
            final TimeType type = t;
            ui.click(cx, y, bw, 11, () -> timeType = type);
            cx += bw + 2;
        }
        y += 13;
        if (!data.composterOrganicItem.isEmpty() && !data.composterFuelItem.isEmpty()) {
            ui.textShadow("Using", x, y + 2, Modern.LABEL);
            int ix = x + ui.width("Using ");
            ui.icon(ItemIcons.of(data.composterOrganicItem), ix, y, 0.625f);
            ui.icon(ItemIcons.of(data.composterFuelItem), ix + 12, y, 0.625f);
            if (ui.hover(ix, y, 24, 11)) ui.tooltip("§7The numbers are worked out with", ItemRepo.coloredName(data.composterOrganicItem) + " §7and " + ItemRepo.coloredName(data.composterFuelItem));
            y += 12;
            Profit now = profit(null), then = profit(upgrade);
            y += line(ui, "Time per Compost", arrow(GardenItems.time(Composter.timePerCompost(null)), GardenItems.time(Composter.timePerCompost(upgrade)), "§b"), x, y, w);
            y += line(ui, timeType == TimeType.COMPOST ? "Compost factor" : "Composts per " + now.per(), arrow(number(now.perUnit()), number(then.perUnit()), "§e"), x, y, w);
            y += line(ui, "Cost per " + now.per(), arrow(Prices.coins(now.cost()), Prices.coins(then.cost()), "§6"), x, y, w);
            y += line(ui, "Profit per " + now.per(), arrow(Prices.coins(now.profit()), Prices.coins(then.profit()), "§6"), x, y, w);
        }
        return y - y0 + 1;
    }

    private static String number(double v) {
        return String.format(Locale.ROOT, "%,.2f", v);
    }

    // format: how long full storage lasts at your speed
    private static String lasts(int max, double per, int maxPreview, double perPreview, String upgrade) {
        long now = (long) (Composter.timePerCompost(null) * Math.floor(max / per));
        long then = (long) (Composter.timePerCompost(upgrade) * Math.floor(maxPreview / perPreview));
        return arrow(GardenItems.time(now), GardenItems.time(then), "§b");
    }

    @Override
    public int list(Ui ui, int tab, int x, int y, int w) {
        if (inUpgrades(menuTitle())) {
            String upgrade = hovered();
            int y0 = y;
            y += line(ui, "Organic Matter empty in", lasts(Composter.maxOrganicMatter(null), Composter.organicMatterPer(null), Composter.maxOrganicMatter(upgrade), Composter.organicMatterPer(upgrade), upgrade), x, y, w);
            y += line(ui, "Fuel empty in", lasts(Composter.maxFuel(null), Composter.fuelPer(null), Composter.maxFuel(upgrade), Composter.fuelPer(upgrade), upgrade), x, y, w);
            for (String name : new String[]{Composter.SPEED, Composter.MULTI_DROP, Composter.FUEL_CAP, Composter.MATTER_CAP, Composter.COST_REDUCTION})
                y += line(ui, name, "§e" + Composter.level(name, null), x, y, w);
            return y - y0;
        }
        return choiceList(ui, tab == 1 ? fuel : organic, tab == 1, x, y, w);
    }

    private int choiceList(Ui ui, List<Choice> list, boolean isFuel, int x, int y, int w) {
        int y0 = y;
        GardenData data = GardenData.get();
        String selected = isFuel ? data.composterFuelItem : data.composterOrganicItem;
        String kind = isFuel ? "§2Fuel" : "§eOrganic Matter";
        ui.textShadow(ui.ellipsize("Items needed to fill " + kind, w), x, y + 1, Modern.LABEL);
        y += 11;
        for (Choice c : list) {
            boolean hover = ui.hover(x, y, w, ROW);
            boolean chosen = c.id().equals(selected);
            if (hover) ui.rect(x, y, w, ROW, 0x22FFFFFF);
            else if (chosen) ui.rect(x, y, w, ROW, 0x14FFFFFF);
            ui.icon(ItemIcons.of(c.id()), x, y + 1, 0.625f);
            double total = c.needed() * c.price();
            String right = "§8x" + String.format(Locale.ROOT, "%,d", c.needed()) + " §6" + Prices.coins(total);
            String name = (chosen ? "§n" : "") + GameState.strip(ItemRepo.name(c.id())).replace("Enchanted ", "Ench ");
            ui.textShadow(right, x + w - ui.width(right), y + 2, Modern.INK);
            ui.textShadow(ui.ellipsize(name, w - 15 - ui.width(right)), x + 12, y + 2, Modern.INK);
            if (hover) {
                List<String> tip = new ArrayList<>(List.of(ItemRepo.coloredName(c.id()), "", "§7Price per: §6" + Prices.coins(c.price()), kind + " §7per: §e" + String.format(Locale.ROOT, "%,.0f", c.factor()),
                        "§7Need §8" + c.needed() + " §7items for max " + kind, "§7Total price: §6" + Prices.coins(total), ""));
                tip.add(chosen ? (bool("composter_overlay.retrieve", false) ? "§eCtrl + click to get them" : "§7In use for the numbers above") : "§eClick to select for profit calculations!");
                ui.tooltip(tip.toArray(new String[0]));
            }
            final Choice choice = c;
            ui.click(x, y, w, ROW, () -> pick(choice, isFuel));
            y += ROW;
        }
        if (list.isEmpty()) y += SideHelper.emptyText(ui, "No prices yet.", x, y, w);
        return y - y0;
    }

    private void pick(Choice c, boolean isFuel) {
        GardenData data = GardenData.get();
        if (isFuel) data.composterFuelItem = c.id();
        else data.composterOrganicItem = c.id();
        Store.markDirty();
        long now = System.currentTimeMillis();
        if (!bool("composter_overlay.retrieve", false) || now - clickedAt < 500 || !ctrl()) return;
        clickedAt = now;
        retrieve(c.id(), GameState.strip(ItemRepo.name(c.id())), c.needed());
    }

    private static boolean ctrl() {
        try {
            var window = Minecraft.getInstance().getWindow();
            return InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) || InputConstants.isKeyDown(InputConstants.KEY_RCONTROL);
        } catch (Throwable t) {
            return false;
        }
    }

    private static void send(String command) {
        var c = Minecraft.getInstance().getConnection();
        if (c != null) c.sendCommand(command);
    }

    private static void say(String text) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[SBC] §e" + text));
    }

    // retrieveMaterials, only ever from that ctrl + click
    private static void retrieve(String id, String name, int needed) {
        if (needed == 0) return;
        Option from = option("composter_overlay.retrieve_from");
        boolean noTrade = io.github.zmuzy.sbc.item.market.Market.noTrade();
        if (from != null && from.text().equals("Bazaar") && !noTrade && !id.equals("BIOFUEL")) {
            io.github.zmuzy.sbc.item.market.Market.searched(name);
            send("bz " + name);
            return;
        }
        int inInventory = GardenItems.inInventory(id);
        if (inInventory >= needed) {
            say(name + " §8x" + needed + " §ealready found in inventory!");
            return;
        }
        Integer inSacks = Sacks.amountInSacks(id);
        if (inSacks != null && inSacks == 0) {
            if (noTrade) {
                say("No " + name + " found in sacks. Opening recipe.");
                send("recipe " + name);
            } else {
                say("No " + name + " found in sacks. Opening Bazaar.");
                io.github.zmuzy.sbc.item.market.Market.searched(name);
                send("bz " + name);
            }
            return;
        }
        send("gfs " + id + " " + (needed - inInventory));
    }

    static void register() {
        Features.add(new Feature(FEATURE, "Garden", "Composter helper", "Beside the Composter: what fills your organic matter and fuel for the least coins, and with what you picked what a compost costs and earns. Beside Composter Upgrades: what the upgrade under your mouse would change.", "SkyHanni: Composter Overlay")
                .when(() -> "GARDEN".equals(GameState.island))
                .option(Option.choice("composter_overlay.price_source", "Price source", "", "Instant buy", "Instant buy", "Instant sell", "NPC"))
                .option(Option.integer("composter_overlay.min_matter", "Minimum organic matter of an item", "Leave out the items that hold less.", 1_000, 1, 100_000))
                .option(Option.bool("composter_overlay.round_down", "Round the amount needed down", "So that you don't overspend.", true))
                .option(Option.bool("composter_overlay.retrieve", "Ctrl + click gets the items", "Ctrl + click on the item in use fetches it from your sacks or looks it up on the Bazaar.", false))
                .option(Option.choice("composter_overlay.retrieve_from", "Get the items from", "", "Sacks", "Sacks", "Bazaar")));
    }
}
