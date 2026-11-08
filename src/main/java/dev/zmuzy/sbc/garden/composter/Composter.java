// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.garden.composter;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.LegacyText;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.core.Store;
import dev.zmuzy.sbc.events.hoppity.HoppityEnv;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.feature.Option;
import dev.zmuzy.sbc.garden.GardenData;
import dev.zmuzy.sbc.garden.GardenItems;
import dev.zmuzy.sbc.garden.milestones.GardenCard;
import dev.zmuzy.sbc.garden.milestones.GardenCard.Block;
import dev.zmuzy.sbc.garden.milestones.GardenCard.Line;
import dev.zmuzy.sbc.item.menus.MenuMarks;
import dev.zmuzy.sbc.item.menus.MenuView;
import dev.zmuzy.sbc.ui.HudLayout;
import dev.zmuzy.sbc.ui.HudLayout.Zone;
import dev.zmuzy.sbc.ui.SideHelper;
import dev.zmuzy.sbc.ui.Toasts;
import dev.zmuzy.sbc.ui.hud.HudElement;
import dev.zmuzy.sbc.ui.theme.Modern;
import dev.zmuzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

// the composter. from skyhanni ComposterApi + ComposterUpgradesData (what your upgrades make of it),
// ComposterDisplay (when it runs out, low warnings), ComposterInventoryNumbers,
// GardenComposterInventoryFeatures, GardenInventoryNumbers upgrade levels
// the card is GardenExtras.COMPOSTER, the helper next to the menu is ComposterPanel
public final class Composter {
    private Composter() {}

    public static final String MENU = "composter_menu", ALERTS = "garden_composter_alerts", OUTSIDE = "garden_composter_outside";
    // ComposterUpgrade
    public static final String SPEED = "Composter Speed", MULTI_DROP = "Multi Drop", FUEL_CAP = "Fuel Cap", MATTER_CAP = "Organic Matter Cap", COST_REDUCTION = "Cost Reduction";

    // what the tab's Composter widget said last
    public record Tab(long organicMatter, long fuel, String timeLeft, boolean known) {
        static final Tab NONE = new Tab(0, 0, "", false);
    }

    public static Tab readTab(List<String> tab) {
        long matter = 0, fuel = 0;
        String time = "";
        boolean known = false;
        for (String line : tab) {
            String t = line.trim();
            if (t.startsWith("Organic Matter: ")) {
                matter = Math.max(0, GameState.parseShort(t.substring(16)));
                known = true;
            } else if (t.startsWith("Fuel: ")) {
                fuel = Math.max(0, GameState.parseShort(t.substring(6)));
                known = true;
            } else if (t.startsWith("Time Left: ")) {
                time = t.substring(11).trim();
                known = true;
            }
        }
        return known ? new Tab(matter, fuel, time, true) : Tab.NONE;
    }

    public static int level(String upgrade, String addOne) {
        return GardenData.get().composterUpgrades.getOrDefault(upgrade, 0) + (upgrade.equals(addOne) ? 1 : 0);
    }

    public static int maxOrganicMatter(String addOne) {
        return ComposterNumbers.maxOrganicMatter(level(MATTER_CAP, addOne));
    }

    public static int maxFuel(String addOne) {
        return ComposterNumbers.maxFuel(level(FUEL_CAP, addOne));
    }

    public static double multiDropChance(String addOne) {
        return ComposterNumbers.multiDropChance(level(MULTI_DROP, addOne));
    }

    public static long timePerCompost(String addOne) {
        return ComposterNumbers.timePerCompost(level(SPEED, addOne));
    }

    public static double organicMatterPer(String addOne) {
        return ComposterNumbers.matterPer(level(COST_REDUCTION, addOne));
    }

    public static double fuelPer(String addOne) {
        return ComposterNumbers.fuelPer(level(COST_REDUCTION, addOne));
    }

    // estimateEmptyTimeFromTab in ms, null until upgrades are known
    public static Long estimateEmptyTime(Tab tab) {
        if (GardenData.get().composterUpgrades.isEmpty()) return null;
        Long next = tab.timeLeft().isEmpty() || tab.timeLeft().equals("INACTIVE") ? Long.valueOf(0L) : HoppityEnv.duration(tab.timeLeft());
        long nextCompost = next == null ? 0 : next;
        long per = timePerCompost(null);
        double fraction = (double) nextCompost / per;
        long byMatter = ComposterNumbers.untilEnd(tab.organicMatter(), fraction, organicMatterPer(null), per);
        long byFuel = ComposterNumbers.untilEnd(tab.fuel(), fraction, fuelPer(null), per);
        return nextCompost + Math.min(byMatter, byFuel);
    }

    // ComposterDisplay's line under the card, empty until the composter is known
    public static String emptyText() {
        Tab tab = readTab(GameState.tab);
        if (!tab.known()) return "";
        Long left = estimateEmptyTime(tab);
        return left == null ? "§cOpen Composter Upgrades!" : "§b" + GardenItems.time(Math.max(0, left));
    }

    // --- menus ---

    // ComposterUpgradesData.onInventoryFullyOpened, GardenInventoryNumbers, onBackgroundDrawn
    static void upgrades(MenuView v) {
        boolean numbers = MenuMarks.setting(MENU, "composter_menu.upgrade_numbers"), highlight = MenuMarks.setting(MENU, "composter_menu.highlight_upgrade");
        var pattern = Patterns.get("SH.composter.upgrade.name");
        GardenData data = GardenData.get();
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            if (it.empty()) continue;
            Matcher m = pattern.matcher(it.clean);
            if (m.matches()) {
                int level = m.group("level") == null ? 0 : levelOf(m.group("level"));
                if (!Integer.valueOf(level).equals(data.composterUpgrades.put(m.group("name"), level))) Store.markDirty();
                if (numbers) v.number(slot, String.valueOf(level));
            }
            if (highlight && it.lore.contains("§eClick to upgrade!")) v.back(slot, MenuView.GOLD);
        }
    }

    // romanToDecimalIfNecessary
    static int levelOf(String text) {
        return text.matches("\\d+") ? Integer.parseInt(text) : GameState.roman(text);
    }

    // ComposterInventoryNumbers: numbers on the icon where they fit, above the bar's button where they dont
    static void numbers(MenuView v) {
        if (!MenuMarks.setting(MENU, "composter_menu.inventory_numbers")) return;
        Matcher amount = v.item(13).first(Patterns.get("SH.garden.composter.inventory.numbers.amount"));
        if (amount != null) {
            long total = GameState.parseShort(GameState.strip(amount.group("amount")));
            if (total >= 0) v.number(13, "§6" + String.format(Locale.ROOT, "%,d", total));
        }
        for (int slot : new int[]{46, 52}) {
            Matcher value = v.item(slot).first(Patterns.get("SH.garden.composter.inventory.numbers.value"));
            if (value == null) continue;
            long having = GameState.parseShort(GameState.strip(value.group("having")));
            String text = (slot == 46 ? "§e" : "§a") + Prices.coins(Math.max(0, having)) + "/" + GameState.strip(value.group("total"));
            v.label(slot, text, -4, -10, 0.75f);
        }
    }

    // GardenComposterInventoryFeatures.onToolTip
    static void upgradeTooltip(ItemStack stack, List<Component> lines) {
        MenuView v = MenuMarks.current();
        if (v == null || !Patterns.get("SH.composter.upgrades.inventory").matcher(v.title).matches()) return;
        if (!MenuMarks.setting(MENU, "composter_menu.upgrade_price")) return;
        boolean next = false;
        int header = 0, items = 0;
        double full = 0;
        for (int i = 0; i < lines.size(); i++) {
            String plain = lines.get(i).getString();
            if (plain.equals("Upgrade Cost:")) {
                next = true;
                header = i;
                continue;
            }
            if (!next) continue;
            if (plain.endsWith(" Copper")) continue;
            if (plain.isEmpty()) break;
            GardenItems.Amount read = GardenItems.readItemAmount(LegacyText.of(lines.get(i), true, false));
            String id = read == null ? null : GardenItems.idOf(read.name());
            if (id == null) continue;
            double price = GardenItems.price(id) * read.amount();
            full += price;
            lines.set(i, lines.get(i).copy().append(" §7(§6" + Prices.coins(price) + "§7)"));
            items++;
        }
        if (items > 1) lines.set(header, lines.get(header).copy().append(" §7(§6" + Prices.coins(full) + "§7)"));
    }

    // --- warnings ---

    private static Option alertOption(String key) {
        Feature f = Features.get(ALERTS);
        return f == null ? null : f.option(key);
    }

    private static boolean alert(String key, boolean otherwise) {
        Option o = alertOption(key);
        return o == null ? otherwise : o.bool();
    }

    // ReminderUtils.isBusy
    private static boolean busy() {
        return dev.zmuzy.sbc.garden.contest.Contests.inContest() || switch (GameState.island) {
            case "CATACOMBS", "KUUDRA", "THE_RIFT" -> true;
            default -> false;
        };
    }

    // composter tab widget read once a sec here, outside the garden we only know the saved time
    public static void second(Minecraft mc) {
        GardenData data = GardenData.get();
        long now = System.currentTimeMillis();
        if ("GARDEN".equals(GameState.island)) {
            Tab tab = readTab(GameState.tab);
            if (tab.known()) {
                Long left = estimateEmptyTime(tab);
                if (left != null) {
                    data.composterEmptyTime = now + left;
                    if (left > 0) data.composterEmptyWarned = false;
                }
                notifyLow(data, tab, now);
            }
        }
        checkWarnings(mc, data, now);
    }

    // sendNotify
    private static void notifyLow(GardenData data, Tab tab, long now) {
        if (!Features.on(ALERTS) || !alert("garden_composter_alerts.notify_low", true) || busy()) return;
        Option matter = alertOption("garden_composter_alerts.min_matter"), fuel = alertOption("garden_composter_alerts.min_fuel");
        if (matter == null || fuel == null) return;
        if (tab.organicMatter() <= matter.number() && data.informedAboutLowMatter < now) {
            low("§cYour Organic Matter is low");
            data.informedAboutLowMatter = now + 5 * 60_000L;
        }
        if (tab.fuel() <= fuel.number() && data.informedAboutLowFuel < now) {
            low("§cYour Fuel is low");
            data.informedAboutLowFuel = now + 5 * 60_000L;
        }
    }

    private static void low(String text) {
        var mc = Minecraft.getInstance();
        if (alert("garden_composter_alerts.title", false) && mc.gui != null && mc.gui.hud != null) mc.gui.hud.setTitle(Component.literal(text));
        Toasts.push("Composter", GameState.strip(text) + "!", 0xFFEF9F27);
    }

    // checkWarningsAndOutsideGarden. says the empty composter thing only once or it'd nag people who never fill it
    private static void checkWarnings(Minecraft mc, GardenData data, long now) {
        if (!Features.on(ALERTS) || !alert("garden_composter_alerts.warn_empty", true) || data.composterEmptyTime == 0 || busy()) return;
        long left = data.composterEmptyTime - now;
        if (left > 20 * 60_000L) return;
        if (left <= 0 && data.composterEmptyWarned) return;
        if (now - data.lastComposterEmptyWarningTime < 2 * 60_000L) return;
        data.lastComposterEmptyWarningTime = now;
        if (left <= 0) data.composterEmptyWarned = true;
        Store.markDirty();
        String text = left > 0 ? "Your composter in the garden is almost empty!" : "Your composter is empty!";
        if (mc.gui != null && mc.gui.hud != null) mc.gui.hud.setTitle(Component.literal("§eComposter Warning!"));
        if (mc.player == null) return;
        if ("GARDEN".equals(GameState.island)) mc.player.sendSystemMessage(Component.literal("§b[SBC] §e" + text));
        else mc.player.sendSystemMessage(Component.literal("§b[SBC] §e" + text + " §7Click to warp to the Garden.").withStyle(s -> s
                .withClickEvent(new ClickEvent.RunCommand("/warp garden"))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to warp to the Garden")))));
    }

    // time left when you're not in the garden
    private static final class Outside extends GardenCard {
        Outside() {
            super(OUTSIDE, "Composter away", Zone.TOP_LEFT, 33, OUTSIDE);
        }

        @Override
        public ItemStack icon() {
            return dev.zmuzy.sbc.core.LegacyItems.stack("minecraft:composter");
        }

        private String text() {
            if (demo) return "§b1h 12m";
            long at = GardenData.get().composterEmptyTime;
            if (at == 0) return "§7?";
            long left = at - System.currentTimeMillis();
            return left > 0 ? "§b" + GardenItems.time(left, 3) : "§cEmpty";
        }

        @Override
        public boolean visible() {
            return GameState.inSkyBlock && !"GARDEN".equals(GameState.island);
        }

        @Override
        public List<Row> rows() {
            return List.of(new Row("Empty in", GameState.strip(text()), Theme.current().accent()));
        }

        @Override
        public List<Row> demo() {
            return List.of(new Row("Empty in", "1h 12m", Theme.current().accent()));
        }

        @Override
        protected List<Block> blocks() {
            List<Block> out = new ArrayList<>();
            out.add(new Line("Empty in").value(text(), Modern.AQUA));
            return out;
        }
    }

    public static final HudElement OUTSIDE_CARD = new Outside();

    public static void register() {
        Features.add(new Feature(MENU, "Garden", "Composter menus", "In the Composter: how much organic matter, fuel and compost there is, on the buttons. In Composter Upgrades: each upgrade's level on its icon, what it costs in coins in its tooltip, and what you can buy now in gold.", "SkyHanni: Composter")
                .when(() -> "GARDEN".equals(GameState.island))
                .tooltip(Composter::upgradeTooltip)
                .option(Option.bool("composter_menu.inventory_numbers", "Numbers in the Composter", "Organic matter, fuel and compost available.", true))
                .option(Option.bool("composter_menu.upgrade_numbers", "Upgrade levels", "The level on the icon of each upgrade.", true))
                .option(Option.bool("composter_menu.upgrade_price", "Upgrade price", "The coin price in an upgrade's tooltip.", true))
                .option(Option.bool("composter_menu.highlight_upgrade", "Highlight upgrades you can buy", "", true)));
        MenuMarks.add(MENU, Patterns.get("SH.composter.upgrades.inventory"), Composter::upgrades);
        MenuMarks.add(MENU, Patterns.get("SH.composter.inventory"), Composter::numbers);
        Features.add(new Feature(ALERTS, "Garden", "Composter warnings", "A warning when your composter is about to run out or is empty, on every island, and when its organic matter or fuel is low.", "SkyHanni: Composter Warning")
                .when(() -> GameState.inSkyBlock)
                .option(Option.bool("garden_composter_alerts.warn_empty", "Warn when almost empty", "Under 20 minutes, every 2 minutes; when it is empty, once. Needs Composter Upgrades opened once.", true))
                .option(Option.bool("garden_composter_alerts.notify_low", "Notify when low", "In the Garden, at most every 5 minutes.", true))
                .option(Option.integer("garden_composter_alerts.min_matter", "Min organic matter", "", 20_000, 0, 200_000))
                .option(Option.integer("garden_composter_alerts.min_fuel", "Min fuel", "", 10_000, 0, 200_000))
                .option(Option.bool("garden_composter_alerts.title", "Show a title when low", "", false)));
        Features.add(new Feature(OUTSIDE, "Garden", "Composter outside the Garden", "A card on the other islands with the time until your composter is empty.", "SkyHanni: Composter Display")
                .defaultOff());
        HudLayout.add(OUTSIDE_CARD);
        ComposterPanel.register();
        SideHelper.add(ComposterPanel::of);
    }
}
