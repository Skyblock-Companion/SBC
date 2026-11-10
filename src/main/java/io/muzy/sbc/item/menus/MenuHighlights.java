// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.menus;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.ItemRepo;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.SbTime;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.item.market.Market;
import io.muzy.sbc.mc.Interactions;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// hypixel menus that only want a color or a number. from skyhanni SkyblockGuideHighlightFeature,
// PowerStoneGuideFeatures, RngMeterInventory, StatsTuning
// ActiveBeaconEffect, TabWidgetSettings, HubSelectorCapacityHighlight, JacobFarmingContestsInventory. all on MenuMarks
public final class MenuHighlights {
    private static final String GUIDE_FEATURE = "guide_highlight";

    private MenuHighlights() {}

    private static Pattern sh(String key) {
        return Patterns.get("SH." + key);
    }

    static void command(String command) {
        var p = Minecraft.getInstance().player;
        if (p != null && p.connection != null) p.connection.sendCommand(command);
    }

    // skyhanni searchForBazaarItem: the search + how many you want on the clipboard for the sign
    static void bazaar(String name, int amount) {
        if (GameState.noTrade() || "CATACOMBS".equals(GameState.island) || "KUUDRA".equals(GameState.island)) return;
        Market.searched(name);
        command("bz " + name);
        if (amount > 0) Minecraft.getInstance().keyboardHandler.setClipboard(String.valueOf(amount));
    }

    // skyblock guide

    private enum Wiki { NONE, TRAVEL, ITEM }

    // one line of skyhannis init block: setting, menu, what a missing thing says
    private record Guide(String option, String inventory, String condition, Wiki wiki) {
        boolean enabled() {
            return option.startsWith("attribute_shards.") ? MenuMarks.on("attribute_shards", option) : MenuMarks.on(GUIDE_FEATURE, option);
        }
    }

    private static final String ONCE = "skyblockguide.highlight.condition.once", X = "skyblockguide.highlight.condition.x";
    // skyhannis order, the first one thats on and matches the title wins
    private static final List<Guide> GUIDES = List.of(
            new Guide("guide_highlight.missing_tasks", "skyblockguide.highlight.level.guide.inventory", X, Wiki.NONE),
            new Guide("guide_highlight.abiphone", "skyblockguide.highlight.abiphone.inventory", ONCE, Wiki.NONE),
            new Guide("guide_highlight.one_time", "skyblockguide.highlight.bank.inventory", ONCE, Wiki.NONE),
            new Guide("guide_highlight.story", "skyblockguide.highlight.travel.inventory", ONCE, Wiki.TRAVEL),
            new Guide("guide_highlight.one_time", "skyblockguide.highlight.spooky.inventory", ONCE, Wiki.NONE),
            new Guide("guide_highlight.one_time", "skyblockguide.highlight.belt.inventory", ONCE, Wiki.NONE),
            new Guide("guide_highlight.jacob", "skyblockguide.highlight.jacob.inventory", ONCE, Wiki.NONE),
            new Guide("guide_highlight.one_time", "skyblockguide.highlight.slaying.inventory", ONCE, Wiki.NONE),
            new Guide("guide_highlight.story", "skyblockguide.highlight.story.inventory", ONCE, Wiki.NONE),
            new Guide("guide_highlight.one_time", "skyblockguide.highlight.pet.rock.inventory", ONCE, Wiki.NONE),
            new Guide("guide_highlight.one_time", "skyblockguide.highlight.pet.dolphin.inventory", ONCE, Wiki.NONE),
            new Guide("guide_highlight.essence", "skyblockguide.highlight.essence.inventory", X, Wiki.NONE),
            new Guide("guide_highlight.minion", "skyblockguide.highlight.minion.inventory", "skyblockguide.highlight.minion.condition", Wiki.NONE),
            new Guide("guide_highlight.story", "skyblockguide.highlight.harp.inventory", X, Wiki.NONE),
            new Guide("guide_highlight.consumable", "skyblockguide.highlight.consumable.inventory", "skyblockguide.highlight.consumable.condition", Wiki.ITEM),
            new Guide("guide_highlight.one_time", "skyblockguide.highlight.dungeon.floor.inventory", "skyblockguide.highlight.dungeon.floor.condition", Wiki.NONE),
            new Guide("guide_highlight.one_time", "skyblockguide.highlight.dungeon.layers.inventory", X, Wiki.NONE),
            new Guide("guide_highlight.menu", "skyblockguide.highlight.tasks.inventory", "skyblockguide.highlight.condition.total", Wiki.NONE),
            new Guide("guide_highlight.menu", "skyblockguide.highlight.tasks.skill.inventory", "skyblockguide.highlight.condition.category", Wiki.NONE),
            new Guide("guide_highlight.collection", "skyblockguide.highlight.collections.inventory", "skyblockguide.highlight.collections.condition", Wiki.NONE),
            new Guide("attribute_shards.highlight_disabled", "skyblockguide.highlight.attribute.disable.inventory", "skyblockguide.highlight.attribute.disable.condition", Wiki.NONE));

    private static Guide activeGuide;
    private static final Set<Integer> MISSING = new HashSet<>();

    private static Guide guideFor(String title) {
        for (Guide g : GUIDES) if (g.enabled() && sh(g.inventory()).matcher(title).matches()) return g;
        return null;
    }

    static void guide(MenuView v) {
        Guide g = guideFor(v.title);
        activeGuide = g;
        MISSING.clear();
        if (g == null) return;
        Pattern condition = sh(g.condition());
        for (int slot = 0; slot < v.top; slot++) {
            if (slot == 4) continue; // the overview item
            MenuView.Item it = v.items[slot];
            if (it.empty()) continue;
            boolean missing = condition.matcher(it.name).matches();
            for (int i = 0; !missing && i < it.lore.size(); i++) missing = condition.matcher(it.lore.get(i)).matches();
            if (!missing) continue;
            MISSING.add(slot);
            v.back(slot, MenuView.RED);
        }
    }

    private static void wikiClick(int slot) {
        if (activeGuide == null || !MISSING.contains(slot) || activeGuide.wiki() == Wiki.NONE) return;
        if (activeGuide.wiki() == Wiki.TRAVEL) command("wiki MUSEUM_TRAVEL_SCROLL");
        else {
            MenuView v = MenuMarks.current();
            String id = v == null ? "" : v.item(slot).id;
            if (!id.isEmpty()) command("wiki " + id);
        }
    }

    private static void wikiTooltip(ItemStack stack, List<Component> lines) {
        int slot = MenuMarks.hoveredSlot();
        if (slot < 0 || activeGuide == null || !MISSING.contains(slot) || activeGuide.wiki() == Wiki.NONE) return;
        lines.add(Component.empty());
        lines.add(Component.literal("§eClick to view on the SkyBlock Wiki!"));
    }

    // power stones guide

    private static final Map<Integer, String> STONES = new HashMap<>();

    static void powerStones(MenuView v) {
        STONES.clear();
        if (!v.title.equals("Power Stones Guide") || !MenuMarks.on(GUIDE_FEATURE, "guide_highlight.power_stone")) return;
        for (int slot = 0; slot < v.top; slot++) {
            List<String> lore = v.items[slot].lore;
            if (!lore.contains("§7Learned: §cNot Yet ✖")) continue;
            int at = lore.indexOf("§7Power stone:");
            if (at < 0 || at + 1 >= lore.size()) continue;
            STONES.put(slot, GameState.strip(lore.get(at + 1)).trim());
            v.back(slot, MenuView.RED);
        }
    }

    private static String stoneId(String name) {
        String id = ItemRepo.idForName(name);
        return id == null || id.isEmpty() ? Prices.idFromName(name) : id;
    }

    private static void stoneClick(int slot) {
        String name = STONES.get(slot);
        if (name == null) return;
        bazaar(name, 9);
    }

    private static void stoneTooltip(ItemStack stack, List<Component> lines) {
        String name = STONES.get(MenuMarks.hoveredSlot());
        if (name == null) return;
        var p = Prices.get(stoneId(name));
        if (p == null) return;
        lines.add(Math.min(5, lines.size()), Component.literal("§79x from Bazaar: §6" + Prices.coins(p.buy() * 9)));
    }

    // rng meter

    static void rngMeter(MenuView v) {
        boolean catacombs = v.title.equals("Catacombs RNG Meter");
        boolean floors = catacombs && MenuMarks.on("rng_meter", "rng_meter.floor_names"), noDrop = catacombs && MenuMarks.on("rng_meter", "rng_meter.no_drop"), selected = MenuMarks.on("rng_meter", "rng_meter.selected");
        Pattern floor = sh("rngmeterinventory.floor.name");
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            if (it.empty()) continue;
            if (floors && it.clean.equals("RNG Meter")) {
                Matcher m = it.first(floor);
                if (m != null) v.number(slot, m.group("floor"));
            }
            if (noDrop && it.loreHas("You don't have an RNG drop")) v.back(slot, MenuView.RED);
            if (selected && it.loreHas("§a§lSELECTED")) v.back(slot, MenuView.YELLOW);
        }
    }

    // stats tuning

    // skyhanni MaxwellApi.loadThaumaturgyTuningsFromTuning: "§b6✎" for each stat with points
    static List<String> readTunings(MenuView v) {
        List<String> out = new ArrayList<>();
        for (int slot = 0; slot < v.top; slot++) {
            String t = tuning(v.items[slot]);
            if (t != null) out.add(t);
        }
        return out;
    }

    // icons are font glyphs with their own color now, so match without colors too
    private static final Pattern TUNING_PLAIN = Pattern.compile("You have: .+ \\+ (?<amount>[^ ]+)(?: (?<icon>.+))?");
    static final Pattern POINTS_PLAIN = Pattern.compile("Stat has: (?<amount>\\d+) points?");

    // "§b6✎", null = no points
    static String tuning(MenuView.Item it) {
        if (it.empty()) return null;
        Matcher m = it.first(sh("data.maxwell.thaumaturgy.statstuning"));
        if (m != null) return "§" + m.group("color") + m.group("amount") + m.group("icon");
        for (String line : it.lore) {
            Matcher p = TUNING_PLAIN.matcher(GameState.strip(line).trim());
            if (!p.matches()) continue;
            String icon = p.group("icon") == null ? "" : p.group("icon").trim();
            return "§" + colorBefore(line, p.group("amount")) + p.group("amount") + icon;
        }
        return null;
    }

    // last color before the amount
    static char colorBefore(String colored, String amount) {
        int plus = Math.max(0, colored.lastIndexOf("+ "));
        int at = colored.indexOf(amount, plus);
        if (at < 0) at = colored.length();
        char c = 'f';
        for (int i = plus; i < at - 1; i++) {
            if (colored.charAt(i) != '§') continue;
            char k = Character.toLowerCase(colored.charAt(i + 1));
            if ("0123456789abcdef".indexOf(k) >= 0) c = k;
        }
        return c;
    }

    // skyhanni loadThaumaturgyTuningsFromTuning: which stat each of those is, from the item name
    static List<String> readTuningNames(MenuView v) {
        List<String> out = new ArrayList<>();
        Pattern name = sh("data.maxwell.tuning.name");
        for (int slot = 0; slot < v.top; slot++) {
            if (tuning(v.items[slot]) == null) continue;
            Matcher m = name.matcher(v.items[slot].clean);
            out.add(m.matches() ? m.group("name") : v.items[slot].clean);
        }
        return out;
    }

    // skyhanni loadThaumaturgyTunings: only when nothing better is known, the rounded values the bag menu shows
    private static List<String> tuningsFromBag(MenuView v, List<String> names) {
        MenuView.Item item = v.item(51);
        Pattern start = sh("data.maxwell.gui.thaumaturgy.start"), data = sh("data.maxwell.gui.thaumaturgy.data");
        boolean active = false;
        List<String> out = new ArrayList<>();
        for (String line : item.lore) {
            if (start.matcher(line).matches()) {
                active = true;
                continue;
            }
            if (!active) continue;
            if (line.isEmpty()) break;
            Matcher m = data.matcher(line);
            if (m.matches()) {
                out.add("§" + m.group("color") + m.group("amount") + m.group("icon"));
                names.add(m.group("name"));
            }
        }
        return out;
    }

    // from skyhanni StringUtils.createCommaSeparatedList
    static String commaList(List<String> items, String separatorColor) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) b.append(separatorColor).append(i == items.size() - 1 ? " and " : ", ");
            b.append(items.get(i));
        }
        return b.toString();
    }

    static void statsTuning(MenuView v) {
        boolean tuningMenu = v.title.equals("Stats Tuning");
        if (tuningMenu) {
            List<String> read = readTunings(v);
            List<String> names = readTuningNames(v);
            if (!read.equals(Store.I.statTunings) || !names.equals(Store.I.statTuningNames) || !Store.I.statTuningsRead) {
                Store.I.statTunings = read;
                Store.I.statTuningNames = names;
                Store.I.statTuningsRead = true;
                Store.markDirty();
            }
        }
        boolean template = MenuMarks.on("stats_tuning", "stats_tuning.template_stats"), selected = MenuMarks.on("stats_tuning", "stats_tuning.selected_stats");
        boolean points = MenuMarks.on("stats_tuning", "stats_tuning.points"), chosen = MenuMarks.on("stats_tuning", "stats_tuning.selected_template");
        Pattern statPoints = sh("inventory.statstuning.points"), unassigned = Pattern.compile("Unassigned Points: (?<points>\\d+)!!!");
        boolean thaumaturgy = sh("data.maxwell.gui.thaumaturgy").matcher(v.title).matches();
        if (thaumaturgy && Store.I.statTunings.isEmpty()) {
            List<String> names = new ArrayList<>();
            List<String> rounded = tuningsFromBag(v, names);
            if (!rounded.isEmpty() || !Store.I.statTuningsRead) {
                Store.I.statTunings = rounded;
                Store.I.statTuningNames = names;
                Store.I.statTuningsRead = true;
                Store.markDirty();
            }
        }
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            if (it.empty()) continue;
            if (tuningMenu && chosen && (it.lore.contains("§aCurrently selected!") || it.cleanLore.contains("Currently selected!"))) v.back(slot, MenuView.GREEN);
            if (tuningMenu && template && (it.clean.equals("Load") || it.clean.startsWith("Tuning Template"))) {
                String text = loading(it.lore);
                if (!text.isEmpty()) {
                    v.label(slot, text, 37, 4, 1f);
                    continue;
                }
            }
            if (thaumaturgy && selected && it.clean.equals("Stats Tuning") && !Store.I.statTunings.isEmpty()) {
                v.label(slot, commaList(Store.I.statTunings, "§7"), 20, 4, 1f);
                continue;
            }
            if (tuningMenu && points) {
                Matcher m = it.first(statPoints);
                if (m == null) m = it.firstClean(POINTS_PLAIN);
                if (m != null) v.number(slot, m.group("amount"));
                // skyblocker StatsTuningAdder: whats still free to assign, on the tuning button
                if (it.clean.equals("Stats Tuning")) {
                    Matcher free = it.firstClean(unassigned);
                    if (free != null) v.number(slot, free.group("points"));
                }
            }
        }
    }

    private static String loading(List<String> lore) {
        boolean grab = false;
        List<String> list = new ArrayList<>();
        for (String line : lore) {
            String plain = GameState.strip(line).trim();
            if (plain.equals("You are loading:")) {
                grab = true;
                continue;
            }
            if (!grab) continue;
            if (plain.isEmpty() || plain.endsWith("to load!") || plain.endsWith("to save!")) {
                grab = false;
                continue;
            }
            // old "§bIntelligence: 3", new "§c+71 Crit Damage"
            list.add(line.contains(":") ? line.split(":")[0] : line.trim());
        }
        return String.join(" + ", list);
    }

    // beacon, tab widgets, hub selector

    static void beacon(MenuView v) {
        if (!MenuMarks.on("menu_highlights", "menu_highlights.beacon") || !v.title.equals("Profile Stat Upgrades")) return;
        Pattern active = sh("inventory.activebeaconeffect.slot.active");
        for (int slot = 0; slot < v.top; slot++) {
            if (v.items[slot].first(active) != null) {
                v.back(slot, MenuView.GREEN);
                return;
            }
        }
    }

    static void widgets(MenuView v) {
        if (!MenuMarks.on("menu_highlights", "menu_highlights.widgets")) return;
        if (sh("tab.widget.setting.gui").matcher(v.title).matches()) {
            Pattern widget = sh("tab.widget.setting.main.colorless"), enabled = sh("tab.widget.setting.is.enabled.colorless");
            for (int slot = 0; slot < v.top; slot++) {
                MenuView.Item it = v.items[slot];
                if (it.firstClean(widget) != null) v.back(slot, it.firstClean(enabled) != null ? MenuView.GREEN : MenuView.RED);
            }
        }
        if (sh("tab.widget.setting.show").matcher(v.title).matches()) {
            Pattern sub = sh("tab.widget.setting.sub.colorless"), disable = sh("tab.widget.setting.click.disable.colorless");
            for (int slot = 0; slot < v.top; slot++) {
                MenuView.Item it = v.items[slot];
                if (sub.matcher(it.lastClean()).matches()) v.back(slot, it.firstClean(disable) != null ? MenuView.GREEN : MenuView.RED);
            }
        }
    }

    static void hubSelector(MenuView v) {
        Feature f = Features.get("hub_selector");
        int moderate = f.option("hub_selector.moderate").integer();
        int busy = Math.max(f.option("hub_selector.busy").integer(), moderate);
        int veryBusy = Math.max(f.option("hub_selector.very_busy").integer(), busy);
        Pattern players = sh("inventory.hubselector.lobby.players");
        for (int slot = 0; slot < v.top; slot++) {
            Matcher m = v.items[slot].firstClean(players);
            if (m == null) continue;
            int max = Integer.parseInt(m.group("max"));
            double percent = max == 0 ? 0 : Integer.parseInt(m.group("current")) * 100.0 / max;
            int color = percent >= veryBusy ? f.option("hub_selector.very_busy_color").color()
                    : percent >= busy ? f.option("hub_selector.busy_color").color()
                    : percent >= moderate ? f.option("hub_selector.moderate_color").color()
                    : f.option("hub_selector.quiet_color").color();
            if ((color >>> 24) != 0) v.back(slot, color);
        }
    }

    // jacobs contests

    private static final Map<Integer, String> REAL_TIME = new HashMap<>();
    private static final char[] MEDAL_COLOR = {'b', '3', '6', 'f', 'c'};
    private static final String[] MEDALS = {"DIAMOND", "PLATINUM", "GOLD", "SILVER", "BRONZE"};

    private static int month(String name) {
        for (int i = 0; i < SbTime.MONTHS.length; i++) if (SbTime.MONTHS[i].equals(name)) return i;
        return -1;
    }

    static void jacob(MenuView v) {
        REAL_TIME.clear();
        if (!sh("garden.farming.contest.yourcontests").matcher(v.title).matches()) return;
        // skyhanni FarmingContestApi.inInventory: the bulk claim button tells it apart from a page with the same title
        boolean inMenu = v.item(50).cleanLore.stream().findFirst().map(l -> sh("garden.farming.contest.bulkclaim.colorless").matcher(l).matches()).orElse(false);
        if (!inMenu) return;
        boolean rewards = MenuMarks.on("jacob_menu", "jacob_menu.rewards"), realTime = MenuMarks.on("jacob_menu", "jacob_menu.real_time");
        boolean medal = MenuMarks.on("jacob_menu", "jacob_menu.medal"), finnegan = MenuMarks.on("jacob_menu", "jacob_menu.finnegan");
        Pattern time = sh("garden.farming.contest.time.colorless"), bracket = sh("garden.jacob.contests.inventory.medal");
        SimpleDateFormat day = new SimpleDateFormat("dd MMMM yyyy", Locale.US), clock = new SimpleDateFormat("HH:mm", Locale.US);
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.items[slot];
            if (it.empty()) continue;
            if (rewards && it.lastLore().equals("§eClick to claim reward!")) v.back(slot, MenuView.GREEN);
            if (realTime && it.cleanHas("Your score: ")) {
                Matcher m = time.matcher(it.clean);
                if (m.matches() && month(m.group("month")) >= 0) {
                    try {
                        long start = SbTime.sbToReal(Integer.parseInt(m.group("year")), month(m.group("month")), Integer.parseInt(m.group("day")));
                        REAL_TIME.put(slot, day.format(new Date(start)) + " " + clock.format(new Date(start)) + "-" + clock.format(new Date(start + 20 * 60_000L)));
                    } catch (NumberFormatException ignored) {
                        // a name that only looks like a date
                    }
                }
            }
            if (medal) {
                boolean boosted = false;
                for (String line : it.lore) {
                    if (line.contains("Contest boosted by Finnegan!")) boosted = true;
                    Matcher m = bracket.matcher(line);
                    if (!m.matches()) continue;
                    String name = GameState.strip(m.group("medal"));
                    for (int b = 0; b < MEDALS.length; b++) {
                        if (!MEDALS[b].equals(name)) continue;
                        if (boosted && finnegan) v.label(slot, "§" + MEDAL_COLOR[b] + "▲", 5, -2, 1.3f);
                        else v.label(slot, "§" + MEDAL_COLOR[b] + "✦", 9, 1, 0.7f);
                    }
                }
            }
        }
    }

    private static void jacobTooltip(ItemStack stack, List<Component> lines) {
        String real = REAL_TIME.get(MenuMarks.hoveredSlot());
        if (real != null && lines.size() > 1) lines.add(1, Component.literal("§7" + real));
    }

    private static final Pattern PAGE = Pattern.compile("^(\\(\\d+/\\d+\\) )?");

    static void register(BooleanSupplier skyblock) {
        Features.add(new Feature(GUIDE_FEATURE, "Menus", "SkyBlock Guide highlights", "In the SkyBlock Guide and the menus around it, what you have not done yet is red. Choose which menus below.", "SkyHanni: SkyBlock Guide")
                .when(skyblock)
                .tooltip((stack, lines) -> {
                    wikiTooltip(stack, lines);
                    stoneTooltip(stack, lines);
                })
                .option(Option.bool("guide_highlight.missing_tasks", "Missing tasks", "Tasks of the Guide you have not done.", true))
                .option(Option.bool("guide_highlight.abiphone", "Abiphone contacts", "Contacts you have not added yet.", true))
                .option(Option.bool("guide_highlight.consumable", "Consumable items", "Items you have not used yet; click one for its wiki page.", true))
                .option(Option.bool("guide_highlight.jacob", "Jacob's contests", "Contest tasks you have not done.", true))
                .option(Option.bool("guide_highlight.menu", "Task categories", "Categories that are not complete.", true))
                .option(Option.bool("guide_highlight.minion", "Minions", "Minions you have not crafted.", true))
                .option(Option.bool("guide_highlight.one_time", "One-time tasks", "Bank upgrades, Spooky Festival, the Dojo, slaying, dungeon floors and pet milestones.", true))
                .option(Option.bool("guide_highlight.power_stone", "Power stones", "Stones you have not learned, with what nine cost; click one to look it up on the Bazaar.", true))
                .option(Option.bool("guide_highlight.story", "Story and travel", "Story objectives, fast travels and Harp songs you have not done.", true))
                .option(Option.bool("guide_highlight.collection", "Collections", "Collection tiers still open.", false))
                .option(Option.bool("guide_highlight.essence", "Essence shops", "Upgrades you have not bought.", false)));
        MenuMarks.add(GUIDE_FEATURE, t -> true, MenuHighlights::guide);
        MenuMarks.add(GUIDE_FEATURE, t -> true, MenuHighlights::powerStones);
        Interactions.onSlotClick((screen, slot, slotId, button) -> {
            if (Features.on(GUIDE_FEATURE)) {
                wikiClick(slotId);
                stoneClick(slotId);
            }
            return false;
        });

        Features.add(new Feature("rng_meter", "Menus", "RNG Meter menu", "In the RNG Meter menus: the floor on each meter, floors with no drop chosen, and the chosen drop.", "SkyHanni: RNG Meter")
                .when(skyblock)
                .option(Option.bool("rng_meter.floor_names", "Floor names", "F1, M3... where the stack count goes.", true))
                .option(Option.bool("rng_meter.no_drop", "Floors with no drop chosen", "Red.", true))
                .option(Option.bool("rng_meter.selected", "The chosen drop", "Yellow.", true)));
        MenuMarks.add("rng_meter", (Predicate<String>) t -> t.endsWith(" RNG Meter"), MenuHighlights::rngMeter);

        Features.add(new Feature("stats_tuning", "Menus", "Stats Tuning menu", "Maxwell's tuning: points on each stat, what a template loads, the chosen template, and your tuning on the Thaumaturgy button.", "SkyHanni: Stats Tuning · Skyblocker: stats tuning slot text")
                .when(skyblock)
                .option(Option.bool("stats_tuning.points", "Points on each stat", "", true))
                .option(Option.bool("stats_tuning.template_stats", "What a template loads", "", true))
                .option(Option.bool("stats_tuning.selected_template", "Chosen template", "Green.", true))
                .option(Option.bool("stats_tuning.selected_stats", "Tuning in the Thaumaturgy menu", "", true)));
        MenuMarks.add("stats_tuning", (Predicate<String>) t -> t.equals("Stats Tuning") || sh("data.maxwell.gui.thaumaturgy").matcher(t).matches(), MenuHighlights::statsTuning);

        Features.add(new Feature("menu_highlights", "Menus", "Small menu highlights", "The active stat boost in the Beacon menu, and which widgets are on or off in the /tab settings.", "SkyHanni: Highlight Active Beacon Effect, Highlight Widgets")
                .when(skyblock)
                .option(Option.bool("menu_highlights.beacon", "Active beacon effect", "Green.", true))
                .option(Option.bool("menu_highlights.widgets", "Tab widgets", "Green is on, red is off.", true)));
        MenuMarks.add("menu_highlights", "Profile Stat Upgrades", MenuHighlights::beacon);
        MenuMarks.add("menu_highlights", (Predicate<String>) t -> sh("tab.widget.setting.gui").matcher(t).matches() || sh("tab.widget.setting.show").matcher(t).matches(), MenuHighlights::widgets);

        Features.add(new Feature("hub_selector", "Menus", "Hub selector capacity", "How full each lobby is, as a colour on the hub selector.", "SkyHanni: Hub Selector")
                .when(skyblock).defaultOff()
                .option(Option.integer("hub_selector.moderate", "Moderate from (%)", "", 25, 0, 100))
                .option(Option.integer("hub_selector.busy", "Busy from (%)", "", 50, 0, 100))
                .option(Option.integer("hub_selector.very_busy", "Very busy from (%)", "", 75, 0, 100))
                .option(Option.color("hub_selector.quiet_color", "Quiet colour", "", 0x00FFFFFF))
                .option(Option.color("hub_selector.moderate_color", "Moderate colour", "", 0xFFFFFF55))
                .option(Option.color("hub_selector.busy_color", "Busy colour", "", 0xFFFFAA00))
                .option(Option.color("hub_selector.very_busy_color", "Very busy colour", "", 0xFFFF5555)));
        MenuMarks.add("hub_selector", sh("inventory.hubselector.title"), MenuHighlights::hubSelector);

        Features.add(new Feature("jacob_menu", "Garden", "Jacob's contest menu", "In Your Contests: rewards still to claim, the medal of each contest, and when it really took place.", "SkyHanni: Jacob Farming Contest")
                .when(skyblock)
                .tooltip(MenuHighlights::jacobTooltip)
                .option(Option.bool("jacob_menu.rewards", "Unclaimed rewards", "Green.", true))
                .option(Option.bool("jacob_menu.medal", "Medal icon", "", true))
                .option(Option.bool("jacob_menu.finnegan", "Finnegan icon", "A different mark for a contest during Mayor Finnegan.", true))
                .option(Option.bool("jacob_menu.real_time", "Real date and time", "On the contest's tooltip.", true)));
        MenuMarks.add("jacob_menu", t -> true, MenuHighlights::jacob);
    }
}
