// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.dungeon;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemInfo;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.crimson.KuudraApi;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.item.sacks.Sacks;
import io.muzy.sbc.rift.RichCard;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.SideHelper;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.hud.HudElement;
import io.muzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// dungeon + kuudra reward chests. from skyblocker ChestValue (one chest), CroesusProfit (a runs chests),
// CroesusHelper (the runs) + skyhanni InstanceChestProfit
public final class DungeonChests {
    private DungeonChests() {}

    private static final String PROFIT = "dungeon_chest_profit", CROESUS = "dungeon_croesus";
    // from skyblocker ChestValue DUNGEON_CHESTS + KUUDRA_CHESTS
    private static final Set<String> CHEST_TITLES = Set.of("Wood Chest", "Wood", "Gold Chest", "Gold", "Diamond Chest", "Diamond", "Emerald Chest", "Emerald", "Obsidian Chest", "Obsidian", "Bedrock Chest", "Bedrock",
            "Free Chest", "Free Chest Chest", "Paid Chest", "Paid Chest Chest");
    private static final Map<String, String> KUUDRA_KEYS = Map.of("Kuudra Key", "KUUDRA_TIER_KEY", "Hot Kuudra Key", "KUUDRA_HOT_TIER_KEY", "Burning Kuudra Key", "KUUDRA_BURNING_TIER_KEY",
            "Fiery Kuudra Key", "KUUDRA_FIERY_TIER_KEY", "Infernal Kuudra Key", "KUUDRA_INFERNAL_TIER_KEY");
    // from skyblocker WORTHLESS_ITEMS: nobody buys these so no price means nothing
    private static final Set<String> WORTHLESS = Set.of("DUNGEON_DISC_1", "DUNGEON_DISC_2", "DUNGEON_DISC_3", "DUNGEON_DISC_4", "DUNGEON_DISC_5", "MAXOR_THE_FISH", "STORM_THE_FISH", "GOLDOR_THE_FISH",
            "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_1", "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_2", "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_3", "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_4", "ENCHANTMENT_ULTIMATE_NO_PAIN_NO_GAIN_5",
            "ENCHANTMENT_ULTIMATE_COMBO_1", "ENCHANTMENT_ULTIMATE_COMBO_2", "ENCHANTMENT_ULTIMATE_COMBO_3", "ENCHANTMENT_ULTIMATE_COMBO_4", "ENCHANTMENT_ULTIMATE_COMBO_5",
            "ENCHANTMENT_ULTIMATE_BANK_1", "ENCHANTMENT_ULTIMATE_BANK_2", "ENCHANTMENT_ULTIMATE_BANK_3", "ENCHANTMENT_ULTIMATE_BANK_4", "ENCHANTMENT_ULTIMATE_BANK_5",
            "ENCHANTMENT_ULTIMATE_JERRY_1", "ENCHANTMENT_ULTIMATE_JERRY_2", "ENCHANTMENT_ULTIMATE_JERRY_3", "ENCHANTMENT_ULTIMATE_JERRY_4", "ENCHANTMENT_ULTIMATE_JERRY_5",
            "ENCHANTMENT_FEATHER_FALLING_6", "ENCHANTMENT_FEATHER_FALLING_7", "ENCHANTMENT_FEATHER_FALLING_8", "ENCHANTMENT_FEATHER_FALLING_9", "ENCHANTMENT_FEATHER_FALLING_10",
            "ENCHANTMENT_INFINITE_QUIVER_6", "ENCHANTMENT_INFINITE_QUIVER_7", "ENCHANTMENT_INFINITE_QUIVER_8", "ENCHANTMENT_INFINITE_QUIVER_9", "ENCHANTMENT_INFINITE_QUIVER_10");
    private static final Pattern ESSENCE = Pattern.compile("(?<type>[A-Za-z]+) Essence(?: x(?<amount>\\d+))?");
    private static final Pattern SHARD = Pattern.compile("[A-Za-z ]+ Shard(?: x(?<amount>\\d+))?");
    private static final Pattern COIN_COST = Pattern.compile("^([0-9,]+) Coins$");
    // from skyblocker CroesusProfit: dungeon chests there dont have "Chest" in the name
    private static final Pattern RUN_CHEST = Pattern.compile("^(?:Wood|Gold|Diamond|Emerald|Obsidian|Bedrock)$|^(?:Free|Paid) Chest$");
    private static final Pattern RUN_MENU = Pattern.compile("(?:(?:Master )?Catacombs - Flo.*)|(?:Kuudra - .*)");
    private static final Patterns.P CROESUS_MENU = Patterns.p("SH.dungeon.croesus.inventory"), KISMET_NAME = Patterns.p("SH.dungeon.croesus.kismet.reroll.colorless"),
            KISMET_USED = Patterns.p("SH.dungeon.croesus.kismet.used.colorless"), EMPTY = Patterns.p("SH.dungeon.croesus.empty.colorless"), KEY_USED = Patterns.p("SH.dungeon.croesus.chest.state.keyused.colorless"),
            OPENED = Patterns.p("SH.dungeon.croesus.chest.state.opened.colorless"), UNOPENED = Patterns.p("SH.dungeon.croesus.chest.state.unopened.colorless"), KUUDRA_TIER = Patterns.p("SH.dungeon.croesus.chest.kuudra.colorless");
    // CroesusHelpers title, only croesus itself goes in the count
    private static final Pattern HELPER_MENU = Pattern.compile("^(?:\\(\\d+/\\d+\\) )?(?:Croesus|Vesuvius)$");
    private static final Pattern PAGE = Pattern.compile("^\\((\\d+)/\\d+\\) .*$");
    private static final int MAX_CHESTS = 60;
    private static final long THREE_DAYS = 3 * 24 * 3600_000L;

    public static net.minecraft.client.KeyMapping favoriteKey;

    // from skyhanni InstanceChestProfit.onGuiKeyPress: hovered item goes on/off the list. false = key did its job
    public static boolean favoriteKey(Screen s, net.minecraft.client.input.KeyEvent event) {
        if (favoriteKey == null || !favoriteKey.matches(event) || !Features.on(PROFIT) || !(s instanceof io.muzy.sbc.mc.mixin.ContainerAccess a)) return true;
        var slot = a.sbc$hovered();
        if (slot == null || slot.getItem().isEmpty()) return true;
        String id = Gain.sbId(slot.getItem());
        if (id.isEmpty()) return true;
        boolean added = Store.I.chestFavorites.add(id);
        if (!added) Store.I.chestFavorites.remove(id);
        Store.markDirty();
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[SBC] §f" + (added ? "Added " : "Removed ") + slot.getItem().getHoverName().getString() + (added ? " to" : " from") + " your chest favourites."));
        return false;
    }

    // star on a runs chest that has one + on the item in the chest itself
    private static void markFavorites(MenuView v, boolean run) {
        if (Store.I.chestFavorites.isEmpty()) return;
        var favourites = new java.util.HashSet<String>();
        for (String id : Store.I.chestFavorites) favourites.add(GameState.strip(io.muzy.sbc.core.ItemRepo.name(id)));
        for (int i = 0; i < v.top; i++) {
            MenuView.Item it = v.items[i];
            if (it == null || it.empty()) continue;
            if (run) {
                if (!RUN_CHEST.matcher(it.clean).matches()) continue;
                for (String l : it.cleanLore) {
                    if (favourites.contains(l.trim())) {
                        v.number(i, "§6✯");
                        break;
                    }
                }
            } else if (favourites.contains(it.clean)) {
                v.number(i, "§6✯");
            }
        }
    }

    // one line next to a chest
    public record Entry(String name, double value, boolean priced) {}

    // from skyblocker ChestValue: what a chest has + costs
    public record Value(List<Entry> lines, double cost, boolean incomplete, boolean kismet) {
        public double total() {
            double t = -cost;
            for (Entry e : lines) if (e.priced()) t += e.value();
            return t;
        }
    }

    // --- working it out ---

    // enchanted book market id like skyblockers getSkyblockApiId
    private static String apiId(MenuView.Item it) {
        if (!"ENCHANTED_BOOK".equals(it.id)) return it.id;
        CompoundTag en = it.tag.getCompoundOrEmpty("enchantments");
        var keys = en.keySet();
        if (keys.size() != 1) return "";
        String k = keys.iterator().next();
        return "ENCHANTMENT_" + k.toUpperCase(Locale.ROOT) + "_" + en.getIntOr(k, 1);
    }

    private static int amount(Matcher m) {
        String a = m.groupCount() >= 1 ? m.group("amount") : null;
        return a == null ? 1 : Integer.parseInt(a);
    }

    // from skyblocker getRewardChestProfit over the menu slots
    static Value rewardChest(List<MenuView.Item> items, boolean withEssence, boolean withKismet, java.util.function.ToDoubleFunction<String> price) {
        List<Entry> lines = new ArrayList<>();
        double cost = 0;
        boolean incomplete = false, usedKismet = false;
        for (MenuView.Item it : items) {
            if (it == null || it.clean.isEmpty()) continue;
            String name = it.clean;
            if (name.contains("Open Reward Chest")) {
                for (String l : it.cleanLore) {
                    String t = l.trim();
                    Matcher c = COIN_COST.matcher(t);
                    if (c.matches()) cost += Double.parseDouble(c.group(1).replace(",", ""));
                    if (t.equals("Dungeon Chest Key")) {
                        double p = price.applyAsDouble("DUNGEON_CHEST_KEY");
                        if (p < 0) incomplete = true;
                        else cost += p;
                    }
                    if (KUUDRA_KEYS.containsKey(t)) {
                        double p = io.muzy.sbc.crimson.KuudraKeys.cost(KUUDRA_KEYS.get(t), price);
                        if (p < 0) incomplete = true;
                        else cost += p;
                    }
                }
                continue;
            }
            if (KISMET_NAME.matcher(name).matches()) {
                for (String l : it.cleanLore) if (KISMET_USED.matcher(l.trim()).matches()) usedKismet = true;
                continue;
            }
            boolean essence = name.contains("Essence"), shard = name.contains("Shard");
            String id = apiId(it);
            if (!id.isEmpty() && !essence && !shard) {
                if (WORTHLESS.contains(id)) continue;
                double p = price.applyAsDouble(id);
                if (p < 0) incomplete = true;
                lines.add(new Entry(name + (it.count > 1 ? " x" + it.count : ""), p < 0 ? 0 : p * it.count, p >= 0));
                continue;
            }
            if (essence && withEssence) {
                Matcher m = ESSENCE.matcher(name);
                if (m.matches()) {
                    String essenceType = m.group("type").toUpperCase(Locale.ENGLISH);
                    double p = price.applyAsDouble("ESSENCE_" + essenceType);
                    if (p < 0) incomplete = true;
                    int essenceAmount = essenceType.equals("CRIMSON") ? (int) (amount(m) * io.muzy.sbc.crimson.KuudraKeys.essenceMultiplier()) : amount(m);
                    lines.add(new Entry(name, p < 0 ? 0 : p * essenceAmount, p >= 0));
                }
                continue;
            }
            if (shard && !name.contains("Reroll")) {
                Matcher m = SHARD.matcher(name);
                if (m.matches()) {
                    double total = Gain.ofLoot(name);
                    if (total < 0) incomplete = true;
                    lines.add(new Entry(name, Math.max(0, total), total >= 0));
                }
            }
        }
        if (usedKismet && withKismet) {
            double p = price.applyAsDouble("KISMET_FEATHER");
            if (p < 0) incomplete = true;
            else cost += p;
        }
        lines.sort(Comparator.comparingDouble(Entry::value).reversed());
        return new Value(lines, cost, incomplete, usedKismet);
    }

    // from skyblocker CroesusProfit.getChestValue over a run chests lore
    static Value runChest(List<String> lore, boolean withEssence, java.util.function.ToDoubleFunction<String> price, java.util.function.ToDoubleFunction<String> loot) {
        List<Entry> lines = new ArrayList<>();
        double cost = 0;
        boolean incomplete = false, contents = false;
        for (String raw : lore) {
            String l = raw.trim();
            if (l.contains("Contents")) {
                contents = true;
                continue;
            }
            if (l.isEmpty()) {
                contents = false;
                continue;
            }
            if (!contents) {
                if (l.endsWith("Coins")) {
                    String n = l.replace(",", "").replaceAll("[^\\d.]", "");
                    if (!n.isEmpty()) cost = Double.parseDouble(n);
                } else if (l.equals("Dungeon Chest Key")) {
                    double p = price.applyAsDouble("DUNGEON_CHEST_KEY");
                    if (p < 0) incomplete = true;
                    else cost += p;
                } else if (KUUDRA_KEYS.containsKey(l)) {
                    double p = io.muzy.sbc.crimson.KuudraKeys.cost(KUUDRA_KEYS.get(l), price);
                    if (p < 0) incomplete = true;
                    else cost += p;
                }
                continue;
            }
            if (l.contains("Essence") && !withEssence) continue;
            String item = l.replace("✪", "").trim();
            double v = loot.applyAsDouble(item);
            if (v < 0) incomplete = true;
            lines.add(new Entry(item, Math.max(0, v), v >= 0));
        }
        lines.sort(Comparator.comparingDouble(Entry::value).reversed());
        return new Value(lines, cost, incomplete, false);
    }

    private static double priceOf(String id) {
        return Gain.ofId(id);
    }

    // --- next to the chest ---

    private static String coins(double v) {
        return (v < 0 ? "-" : "") + Prices.coins(Math.abs(v));
    }

    private static int profitColor(double v, boolean incomplete) {
        if (incomplete) return DungeonMarks.color(PROFIT, "incomplete", 0xFF5555FF);
        if (Math.abs(v) <= DungeonMarks.integer(PROFIT, "neutral", 1000)) return DungeonMarks.color(PROFIT, "neutral_color", 0xFFAAAAAA);
        return v > 0 ? DungeonMarks.color(PROFIT, "profit_color", 0xFF55FF55) : DungeonMarks.color(PROFIT, "loss_color", 0xFFFF5555);
    }

    private static final class Panel implements SideHelper.Content {
        boolean run;
        Value one = new Value(List.of(), 0, false, false);
        final List<Object[]> chests = new ArrayList<>();

        @Override public String title() {
            return run ? "Chests of this run" : "Chest profit";
        }

        @Override public ItemStack icon() {
            return ItemInfo.stack("DUNGEON_CHEST_KEY");
        }

        @Override public int accent() {
            return Modern.GOLD;
        }

        @Override public String[] tabs() {
            return new String[]{run ? "Chests " + chests.size() : "Contents " + one.lines().size()};
        }

        @Override public void refresh(AbstractContainerScreen<?> screen) {
            String title = GameState.strip(screen.getTitle().getString()).trim();
            run = RUN_MENU.matcher(title).matches();
            var slots = screen.getMenu().slots;
            int size = Math.max(0, slots.size() - 36);
            boolean essence = DungeonMarks.opt(PROFIT, "essence");
            if (run) {
                chests.clear();
                for (int i = 0; i < size; i++) {
                    MenuView.Item it = MenuView.Item.of(slots.get(i).getItem());
                    if (!RUN_CHEST.matcher(it.clean).matches()) continue;
                    Value v = runChest(it.cleanLore, essence, DungeonChests::priceOf, Gain::ofLoot);
                    boolean opened = it.cleanLore.stream().anyMatch(l -> l.trim().equals("Already opened!"));
                    chests.add(new Object[]{it.clean, v, opened});
                }
                return;
            }
            List<MenuView.Item> items = new ArrayList<>();
            for (int i = 0; i < size; i++) items.add(MenuView.Item.of(slots.get(i).getItem()));
            one = rewardChest(items, essence, DungeonMarks.opt(PROFIT, "kismet"), DungeonChests::priceOf);
        }

        @Override public int summary(Ui ui, int x, int y, int w) {
            if (run) {
                double best = Double.NEGATIVE_INFINITY;
                String name = "";
                for (Object[] c : chests) {
                    double t = ((Value) c[1]).total();
                    if (!(Boolean) c[2] && t > best) {
                        best = t;
                        name = (String) c[0];
                    }
                }
                SideHelper.tile(ui, x, y, w, "Best chest", name.isEmpty() ? "-" : name, name.isEmpty() ? "" : coins(best), name.isEmpty() ? Modern.DIM : profitColor(best, false));
                return 26;
            }
            int half = (w - 3) / 2;
            SideHelper.tile(ui, x, y, half, "Profit", coins(one.total()), one.incomplete() ? "not all priced" : "", profitColor(one.total(), one.incomplete()));
            SideHelper.tile(ui, x + half + 3, y, w - half - 3, "Cost", coins(one.cost()), one.kismet() ? "rerolled" : "", Modern.LABEL);
            return 26;
        }

        private int line(Ui ui, String name, String value, int color, int x, int y, int w) {
            int vw = ui.width(value);
            ui.textShadow(ui.ellipsize(name, w - vw - 6), x + 2, y, Modern.INK);
            ui.textShadow(value, x + w - 2 - vw, y, color);
            return 11;
        }

        @Override public int list(Ui ui, int tab, int x, int y, int w) {
            int y0 = y;
            if (run) {
                chests.sort(Comparator.comparingDouble(c -> -((Value) c[1]).total()));
                for (Object[] c : chests) {
                    Value v = (Value) c[1];
                    boolean opened = (Boolean) c[2];
                    y += line(ui, (String) c[0] + (opened ? " (opened)" : ""), coins(v.total()), opened ? Modern.DIM : profitColor(v.total(), v.incomplete()), x, y, w);
                    y += line(ui, "  Cost", coins(v.cost()), Modern.LABEL, x, y, w);
                    for (Entry e : v.lines()) y += line(ui, "  " + e.name(), e.priced() ? coins(e.value()) : "no price", e.priced() ? Modern.GOLD : Modern.DIM, x, y, w);
                    y += 3;
                }
            } else {
                for (Entry e : one.lines()) y += line(ui, e.name(), e.priced() ? coins(e.value()) : "no price", e.priced() ? Modern.GOLD : Modern.DIM, x, y, w);
            }
            if (y == y0) y += SideHelper.emptyText(ui, "Nothing here has a price.", x, y, w);
            return y - y0;
        }
    }

    private static final Panel PANEL = new Panel();

    private static SideHelper.Content panel(Screen s) {
        if (!GameState.inSkyBlock || !Features.on(PROFIT)) return null;
        String t = GameState.strip(s.getTitle().getString()).trim();
        if (CHEST_TITLES.contains(t)) return PANEL;
        return RUN_MENU.matcher(t).matches() && DungeonMarks.opt(PROFIT, "croesus") ? PANEL : null;
    }

    // --- on the menu ---

    // from skyblocker CroesusProfit.getColors: best chest green, 2nd yellow when it pays back twice the key price
    private static void markRun(MenuView v) {
        markFavorites(v, true);
        if (!DungeonMarks.opt(PROFIT, "croesus")) return;
        int best = -1, second = -1;
        double bestValue = 0, secondValue = 0;
        for (int i = 0; i < v.top; i++) {
            MenuView.Item it = v.items[i];
            if (it == null || !RUN_CHEST.matcher(it.clean).matches()) continue;
            if (it.cleanLore.stream().anyMatch(l -> l.trim().equals("Already opened!"))) continue;
            double value = runChest(it.cleanLore, DungeonMarks.opt(PROFIT, "essence"), DungeonChests::priceOf, Gain::ofLoot).total();
            if (value <= 0) continue;
            if (value > bestValue) {
                second = best;
                secondValue = bestValue;
                best = i;
                bestValue = value;
            } else if (value > secondValue) {
                second = i;
                secondValue = value;
            }
        }
        double key = Math.max(0, priceOf("DUNGEON_CHEST_KEY")) * 2;
        if (best >= 0) v.back(best, MenuView.GREEN);
        if (second >= 0 && secondValue > key) v.back(second, MenuView.YELLOW);
    }

    // from skyblocker CroesusHelper.getColors + skyhanni CroesusChestTracker used kismets, and what the menu says
    // goes into the count
    private static void markCroesus(MenuView v) {
        boolean helper = DungeonMarks.opt(CROESUS, "gray_opened"), kismets = DungeonMarks.opt(CROESUS, "kismets");
        for (int i = 0; i < v.top; i++) {
            MenuView.Item it = v.items[i];
            if (it == null || it.cleanLore.isEmpty()) continue;
            if (helper) {
                if (it.cleanLore.stream().anyMatch(l -> l.contains("Opened Chest:"))) v.back(i, 0xC0808080);
                else if (it.cleanLore.stream().anyMatch(l -> l.contains("No more chests to open!"))) v.back(i, MenuView.RED);
            }
            if (kismets && it.lore.stream().anyMatch(DungeonChests::kismetCrossedOut)) v.number(i, "§a✔");
        }
        if (CROESUS_MENU.matcher(v.title).matches()) readCroesus(v);
    }

    // from skyhanni kismetUsedInCroesusPattern: " §mKismet Feather" with just the colors off
    static boolean kismetCrossedOut(String lore) {
        return lore.replaceAll("§[0-9a-frA-FR]", "").equals(" §mKismet Feather");
    }

    // from skyhanni CroesusChestTracker.onRenderItemTip: how many kismets you have, on the reroll button
    private static void markKismet(MenuView v) {
        if (!DungeonMarks.opt(CROESUS, "kismet_count")) return;
        for (int i = 0; i < v.top; i++) {
            MenuView.Item it = v.items[i];
            if (it == null || !KISMET_NAME.matcher(it.clean).matches()) continue;
            if (!it.cleanLore.isEmpty() && KISMET_USED.matcher(it.cleanLore.get(it.cleanLore.size() - 1).trim()).matches()) continue;
            v.number(i, "§a" + kismets());
        }
    }

    private static long kismets() {
        Integer sack = Sacks.amountInSacks("KISMET_FEATHER");
        long n = sack == null ? 0 : sack;
        var p = Minecraft.getInstance().player;
        if (p != null) for (ItemStack st : p.getInventory().getNonEquipmentItems()) if ("KISMET_FEATHER".equals(Gain.sbId(st))) n += st.getCount();
        return n;
    }

    // --- how many wait at croesus ---

    // from skyhanni croesusSlotMapToRun
    static int slotToRun(int slot, int page) {
        int i;
        if (slot >= 10 && slot <= 16) i = slot - 10;
        else if (slot >= 19 && slot <= 25) i = slot - 12;
        else if (slot >= 28 && slot <= 34) i = slot - 14;
        else if (slot >= 37 && slot <= 43) i = slot - 16;
        else return -1;
        return i + page * 28;
    }

    // from skyhanni OpenedState.getOpenState
    static String openState(List<String> lore) {
        boolean opened = false, unopened = false, kuudra = false;
        for (String raw : lore) {
            String l = raw.trim();
            if (KEY_USED.matcher(l).matches()) return "KEY_USED";
            if (OPENED.matcher(l).matches()) opened = true;
            if (UNOPENED.matcher(l).matches()) unopened = true;
            if (KUUDRA_TIER.matcher(l).matches()) kuudra = true;
        }
        return opened ? "OPENED" : unopened || kuudra ? "UNOPENED" : null;
    }

    private static List<Store.CroesusRun> runs() {
        return Store.I.croesusRuns.computeIfAbsent(GameState.profileName.isEmpty() ? "?" : GameState.profileName, k -> new ArrayList<>());
    }

    // from skyhanni CroesusChestTracker.checkChests
    private static void readCroesus(MenuView v) {
        List<Store.CroesusRun> runs = runs();
        if (v.top > 22 && v.items[22] != null && EMPTY.matcher(v.items[22].clean).matches()) {
            if (!runs.isEmpty()) {
                runs.clear();
                Store.markDirty();
            }
            return;
        }
        Matcher p = PAGE.matcher(v.title);
        int page = p.matches() ? Integer.parseInt(p.group(1)) - 1 : 0;
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = 0; i < v.top; i++) {
            int run = slotToRun(i, page);
            if (run < 0) continue;
            MenuView.Item it = v.items[i];
            if (it == null || it.cleanLore.isEmpty()) {
                if (run < runs.size() && runs.get(run).state != null) {
                    runs.get(run).state = null;
                    changed = true;
                }
                continue;
            }
            while (runs.size() <= run && runs.size() < MAX_CHESTS) {
                Store.CroesusRun r = new Store.CroesusRun();
                r.at = now;
                runs.add(r);
                changed = true;
            }
            if (run >= runs.size()) continue;
            String state = openState(it.cleanLore);
            if (state != null && !state.equals(runs.get(run).state)) {
                runs.get(run).state = state;
                changed = true;
            }
        }
        if (changed) Store.markDirty();
    }

    // from skyhanni countUnopenedChestsAndRemoveOld
    static int unopened(List<Store.CroesusRun> runs, long now) {
        runs.removeIf(r -> r.state == null || now - r.at > THREE_DAYS);
        int n = 0;
        for (Store.CroesusRun r : runs) if ("UNOPENED".equals(r.state)) n++;
        return n;
    }

    // from skyhanni addCroesusChest
    private static void add(String floor) {
        List<Store.CroesusRun> runs = runs();
        Store.CroesusRun r = new Store.CroesusRun();
        r.at = System.currentTimeMillis();
        r.floor = floor;
        r.state = "UNOPENED";
        runs.add(0, r);
        unopened(runs, r.at);
        while (runs.size() > MAX_CHESTS) runs.remove(runs.size() - 1);
        Store.markDirty();
        int last = 0;
        for (int i = 0; i < runs.size(); i++) if ("UNOPENED".equals(runs.get(i).state)) last = i + 1;
        if (last >= 55 && Features.on(CROESUS) && DungeonMarks.opt(CROESUS, "limit_warning")) {
            var p = Minecraft.getInstance().player;
            if (p != null) p.sendSystemMessage(Component.literal("§b[SBC] §eYou are close to the Croesus Limit. Please open your chests!"));
        }
    }

    private static boolean wasComplete;

    private static void tick(Minecraft mc) {
        boolean now = DungeonRun.inDungeon() && DungeonRun.completed();
        if (now && !wasComplete && !DungeonRun.floor().isEmpty() && !DungeonRun.floor().equals("E")) add(DungeonRun.floor());
        wasComplete = now;
    }

    // from skyhanni chestCountColor
    static String countColor(int n) {
        return n >= 45 ? "§4" : n >= 30 ? "§c" : n >= 15 ? "§e" : "§6";
    }

    private static boolean showCard() {
        if (!Features.on(CROESUS)) return false;
        if ("CATACOMBS".equals(GameState.island)) return DungeonMarks.opt(CROESUS, "card_dungeons");
        if ("KUUDRA".equals(GameState.island)) return DungeonMarks.opt(CROESUS, "card_kuudra");
        if ("DUNGEON_HUB".equals(GameState.island)) {
            var p = Minecraft.getInstance().player;
            return DungeonMarks.opt(CROESUS, "card_hub") && p != null && p.getY() > 103;
        }
        return DungeonMarks.opt(CROESUS, "card_hub") && "Forgotten Skull".equals(GameState.area);
    }

    private static RichCard.Content cardOf(int n, int total) {
        return new RichCard.Content("", List.of(RichCard.Line.row("Unopened", n + "/" + total).meter((double) n / total, n >= 45 ? Modern.BAD : n >= 15 ? 0xFFFFD166 : Modern.GOLD)));
    }

    public static final HudElement CARD = new RichCard("dungeon_croesus", "Croesus chests", "Dungeon", Zone.TOP_LEFT, 39, CROESUS) {
        @Override public boolean visible() {
            return showCard();
        }

        @Override protected Content live() {
            return cardOf(unopened(runs(), System.currentTimeMillis()), MAX_CHESTS);
        }

        @Override protected Content sample() {
            return cardOf(12, MAX_CHESTS);
        }

        @Override public List<Content> states() {
            return List.of(cardOf(52, MAX_CHESTS));
        }

        @Override protected List<Row> classicRows() {
            int n = unopened(runs(), System.currentTimeMillis());
            return List.of(new Row("Chests", countColor(n) + n + "§f/" + MAX_CHESTS, 0));
        }

        @Override protected List<Row> classicDemo() {
            return List.of(new Row("Chests", "§612§f/60", 0));
        }
    };

    public static void register() {
        Features.add(new Feature(PROFIT, "Dungeons", "Reward chest profit", "Beside a dungeon or Kuudra reward chest: what it holds, what it costs and the profit. At a run in Croesus: every chest, with the best one lit up.", "Skyblocker: Profit Calculator, Croesus Profit; SkyHanni: Croesus Chest Overlay, Highlight")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.bool("dungeon_chest_profit.croesus", "At Croesus", "Also lists and lights up the chests of a run at Croesus.", true))
                .option(Option.bool("dungeon_chest_profit.essence", "Count essence", "Essence counts as profit at its Bazaar price.", true))
                .option(Option.bool("dungeon_chest_profit.kismet", "Count kismets", "A used Kismet Feather counts as cost.", false))
                .option(Option.integer("dungeon_chest_profit.neutral", "Neutral below", "A profit or loss below this many coins is neither.", 1000, 0, 1_000_000))
                .option(Option.color("dungeon_chest_profit.profit_color", "Profit colour", "The colour of a profit.", 0xFF55FF55))
                .option(Option.color("dungeon_chest_profit.loss_color", "Loss colour", "The colour of a loss.", 0xFFFF5555))
                .option(Option.color("dungeon_chest_profit.neutral_color", "Neutral colour", "The colour of about nothing.", 0xFFAAAAAA))
                .option(Option.color("dungeon_chest_profit.incomplete", "Not all priced colour", "The colour when something has no price.", 0xFF5555FF)));
        Features.add(new Feature(CROESUS, "Dungeons", "Croesus", "Greys out runs you opened, marks runs with no chest left and runs where a kismet was used, shows your kismets on the Reroll button, and counts what waits at Croesus.", "Skyblocker: Croesus Helper; SkyHanni: Croesus Unopened Display")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).tick(DungeonChests::tick)
                .option(Option.bool("dungeon_croesus.gray_opened", "Grey out opened runs", "Opened runs are grey, runs with nothing left red.", true))
                .option(Option.bool("dungeon_croesus.kismets", "Mark used kismets", "A tick on runs where a kismet was used.", true))
                .option(Option.bool("dungeon_croesus.kismet_count", "Kismets on Reroll", "How many Kismet Feathers you have, on the Reroll button.", true))
                .option(Option.bool("dungeon_croesus.limit_warning", "Limit warning", "A line in your chat when you are close to the limit of chests.", true))
                .option(Option.bool("dungeon_croesus.card_hub", "Card in the Dungeon Hub", "The count on a card in the Dungeon Hub and the Crimson Isle's Forgotten Skull.", true))
                .option(Option.bool("dungeon_croesus.card_dungeons", "Card in dungeons", "The count on a card in dungeons.", false))
                .option(Option.bool("dungeon_croesus.card_kuudra", "Card in Kuudra", "The count on a card in Kuudra.", false)));
        MenuMarks.add(CROESUS, HELPER_MENU, DungeonChests::markCroesus);
        MenuMarks.add(PROFIT, RUN_MENU, DungeonChests::markRun);
        MenuMarks.add(PROFIT, CHEST_TITLES::contains, v -> markFavorites(v, false));
        MenuMarks.add(CROESUS, CHEST_TITLES::contains, DungeonChests::markKismet);
        SideHelper.add(DungeonChests::panel);
        KuudraApi.onKuudraComplete(tier -> add("T" + tier));
        HudLayout.add(CARD);
    }
}
