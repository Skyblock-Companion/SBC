// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.menus;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Prices;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.core.Safe;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.mc.Interactions;
import io.muzy.sbc.mc.SoundHooks;
import io.muzy.sbc.track.Profit;
import io.muzy.sbc.ui.SideHelper;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.theme.Modern;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// the experimentation table. from skyhanni ExperimentationTableApi, ExperimentationSuperpairApi,
// ExperimentsAddonsHelper, SuperpairHighlight, SuperPairsItemVisibility
// SuperpairDataDisplay, SuperpairsClicksAlert, UltraRareBookAlert, ExperimentationXPOverlay,
// ExperimentsDryStreakDisplay, GuardianReminder
// plus from skyblockers solvers: green mark on the card that pairs with the one you just turned, and refused
// clicks while an ultrasequencer round is still showing
// never clicks for you. only refuses a wrong click unless the override key is held, a click let through
// counts as that step
// superpairs has no wrong click so nothing gets refused there
public final class Experiments {
    public enum Type { CHRONOMATRON, ULTRASEQUENCER, SUPERPAIRS }

    enum Phase { READ, REPLICATE }

    // skyhanni ExperimentationTier: which chest slots are cards
    enum Tier {
        BEGINNER("Beginner", 18, 35, 1), HIGH("High", 10, 43, 2), GRAND("Grand", 10, 43, 2), SUPREME("Supreme", 9, 44, 1), TRANSCENDENT("Transcendent", 9, 44, 1), METAPHYSICAL("Metaphysical", 9, 44, 1);

        final String title;
        final List<Integer> slots = new ArrayList<>();

        Tier(String title, int from, int to, int side) {
            this.title = title;
            for (int i = from; i <= to; i++) {
                int column = i % 9;
                if (column == 0 || column == 8 || side == 2 && (column == 1 || column == 7)) continue;
                slots.add(i);
            }
        }

        static Tier of(String name) {
            for (Tier t : values()) if (t.title.equalsIgnoreCase(name)) return t;
            return null;
        }
    }

    enum Found { NORMAL, POWERUP, MATCH, PAIR }

    // a card: slot, what it gives, and the item it shows bc two rewards can share a name and differ in color
    record Card(int slot, String reward, String model) {
        String kind() {
            return reward + "|" + model;
        }
    }

    public static final String FEATURE = "experiment_solver", DRY = "experiment_dry_streak";
    private static final String KEY = "SH.enchanting.experiments.";
    private static final int ROUND_SLOT = 4, PHASE_SLOT = 49, CLICKS_SLOT = 4;

    // the table
    static Type type;
    static Tier tier;
    private static long xpGained;
    private static boolean rareFired, ignoreNextFinish;
    private static int lastOverHash, currentOverHash;
    private static Runnable queued;
    private static long leftOverAt;
    private static double tableX, tableY, tableZ;
    private static boolean tableKnown;
    private static Set<String> ultraRare;

    // chronomatron and ultrasequencer
    static Phase phase;
    static int chronoRound, ultraRound, sequenceIndex;
    static final List<String> chronoData = new ArrayList<>(), chronoUser = new ArrayList<>();
    static final List<Integer> ultraData = new ArrayList<>(), ultraUser = new ArrayList<>();
    private static final Map<Integer, ItemStack> ultraStacks = new HashMap<>();
    private static boolean chronoEmpty = true;
    private static long lastRoundSound;
    private static int roundsNeeded = -1;

    // superpairs
    static final Map<Integer, MenuView.Item> uncovered = new HashMap<>();
    private static final Map<Integer, ItemStack> uncoveredStacks = new HashMap<>();
    static final Map<Found, List<Card[]>> found = new LinkedHashMap<>();
    static int lastClicked = -1;
    private static boolean frozen;
    private static String lastRare = "";
    private static boolean lastRareBook = true;
    private static long rareAt;

    // the menu as it is rn
    private static Screen screen;
    private static ItemStack[] seen = new ItemStack[0];
    private static final ItemStack[] SHOWN = new ItemStack[54];
    private static boolean anyShown;
    private static long lastTime;

    private Experiments() {}

    private static boolean opt(String key, boolean def) {
        Feature f = Features.get(FEATURE);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    private static int color(String key, int def) {
        Feature f = Features.get(FEATURE);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.color();
    }

    private static boolean dryOn() {
        return Features.on(DRY);
    }

    // what the menu says

    static boolean inTable(String title) {
        return Patterns.get(KEY + "inventories").matcher(title).matches();
    }

    static boolean isOver(String title) {
        return Patterns.get(KEY + "inventory.experiment-over").matcher(title).matches();
    }

    // menu of a running experiment, not its stakes or rewards
    private static boolean playing(String title) {
        return Patterns.get(KEY + "inventory.experiment.current-type-and-tier").matcher(title).matches();
    }

    static boolean inChronomatron() {
        return type == Type.CHRONOMATRON;
    }

    static boolean inUltrasequencer() {
        return type == Type.ULTRASEQUENCER;
    }

    static boolean inSuperpairs() {
        return type == Type.SUPERPAIRS;
    }

    private static void resetAddons() {
        chronoData.clear();
        chronoUser.clear();
        ultraData.clear();
        ultraUser.clear();
        ultraStacks.clear();
        chronoRound = ultraRound = sequenceIndex = 0;
        lastRoundSound = 0;
        phase = null;
        chronoEmpty = true;
    }

    private static void resetSuperpairs() {
        uncovered.clear();
        uncoveredStacks.clear();
        found.clear();
        lastClicked = -1;
        frozen = false;
    }

    private static void resetShown() {
        if (!anyShown) return;
        java.util.Arrays.fill(SHOWN, null);
        anyShown = false;
    }

    // skyhanni onInventoryClose, any table menu
    static void closed() {
        resetAddons();
        resetSuperpairs();
        resetShown();
        lastRare = "";
        if (currentOverHash != 0) {
            lastOverHash = currentOverHash;
            currentOverHash = 0;
        }
    }

    // tests
    static void clearAll() {
        closed();
        type = null;
        tier = null;
        xpGained = 0;
        rareFired = false;
        ignoreNextFinish = false;
        lastOverHash = 0;
        queued = null;
        roundsNeeded = -1;
    }

    // skyhanni InventoryUpdatedEvent: menu changed. nothing here clicks or sends anything
    static void update(String title, MenuView.Item[] items, ItemStack[] stacks) {
        if (!inTable(title)) return;
        updateActivity(title);
        if (title.endsWith("Stakes")) readRoundsNeeded(items);
        if (isOver(title)) {
            readOver(items);
            return;
        }
        rareUncovered(items);
        if (!playing(title)) return;
        if (inSuperpairs()) scanSuperpairs(items, stacks);
        else if (type != null) {
            Phase old = phase;
            Phase now = readPhase(items);
            if (now != null) {
                phase = now;
                if (inChronomatron()) readChronomatron(items, old);
                if (inUltrasequencer()) readUltrasequencer(items, stacks);
                checkMaxClicks();
            }
        }
        refreshShown();
        MenuMarks.invalidate();
    }

    // from skyhanni tryUpdateCurrentActivity
    private static void updateActivity(String title) {
        if (title.equals("Experimentation Table")) {
            type = null;
            tier = null;
            xpGained = 0;
            rareFired = false;
            noteTable();
            return;
        }
        Matcher m = Patterns.get(KEY + "inventory.experiment.current-type-and-tier").matcher(title);
        if (!m.matches()) return;
        Type t = typeOf(m.group("type"));
        Tier r = Tier.of(m.group("tier"));
        if (t == null || r == null || t == type && r == tier) return;
        type = t;
        tier = r;
        resetSuperpairs();
    }

    private static Type typeOf(String name) {
        for (Type t : Type.values()) if (t.name().equalsIgnoreCase(name)) return t;
        return null;
    }

    private static void noteTable() {
        var p = Minecraft.getInstance().player;
        if (p == null) return;
        tableX = p.getX();
        tableY = p.getY();
        tableZ = p.getZ();
        tableKnown = true;
    }

    // skyhanni inDistanceToTable, from where you stood when you last opened it (thats in reach of it)
    public static boolean nearTable(double max) {
        var p = Minecraft.getInstance().player;
        return tableKnown && p != null && p.distanceToSqr(tableX, tableY, tableZ) <= max * max;
    }

    // chronomatron and ultrasequencer

    private static Phase readPhase(MenuView.Item[] items) {
        if (items.length <= PHASE_SLOT) return null;
        String name = items[PHASE_SLOT].name;
        if (Patterns.get(KEY + "addons.replicate-phase-item").matcher(name).matches()) return Phase.REPLICATE;
        if (Patterns.get(KEY + "addons.read-phase-item").matcher(name).matches()) return Phase.READ;
        return null;
    }

    // color a chronomatron item stands for. lit = terracotta, at rest = stained glass, both mean the same color
    // (skyblocker TERRACOTTA_TO_GLASS)
    static String colorOf(MenuView.Item item) {
        String m = item.model;
        if (m.endsWith("_terracotta")) return m.substring(0, m.length() - "_terracotta".length());
        if (m.endsWith("_stained_glass")) return m.substring(0, m.length() - "_stained_glass".length());
        return null;
    }

    // from skyhanni readNextChronomatron
    private static void readChronomatron(MenuView.Item[] items, Phase oldPhase) {
        if (items.length <= ROUND_SLOT) return;
        Matcher round = Patterns.get(KEY + "addons.round-item").matcher(items[ROUND_SLOT].name);
        if (!round.matches()) return;
        chronoRound = Integer.parseInt(round.group("round"));
        int hypixelSize = chronoData.size(), userSize = chronoUser.size();
        List<String> active = new ArrayList<>();
        Pattern lit = Patterns.get(KEY + "addons.chronomatron.read-item");
        for (MenuView.Item it : items) {
            if (it.empty() || !lit.matcher(it.model).matches()) continue;
            String c = colorOf(it);
            if (c != null && !active.contains(c)) active.add(c);
        }
        if (active.isEmpty()) chronoEmpty = true;
        else if (!chronoEmpty) return;
        else chronoEmpty = false;
        String expected = sequenceIndex < hypixelSize ? chronoData.get(sequenceIndex) : null;
        String clicked = null;
        for (String c : active) {
            if (expected == null || c.equals(expected)) {
                clicked = c;
                break;
            }
        }
        if (clicked == null) return;
        boolean shouldReadLastReplicate = oldPhase == Phase.READ || hypixelSize < chronoRound;
        boolean readingReady = oldPhase == null || oldPhase == Phase.READ;
        boolean notYet = switch (phase) {
            case REPLICATE -> !shouldReadLastReplicate;
            case READ -> !readingReady;
        };
        if (notYet) return;
        if (sequenceIndex == hypixelSize) {
            chronoData.add(clicked);
            lastRoundSound = 0;
            sequenceIndex = 0;
            chronoUser.clear();
        } else sequenceIndex++;
    }

    // skyhanni readUltrasequencer: numbers on the items, once all of this rounds are in sight
    private static void readUltrasequencer(MenuView.Item[] items, ItemStack[] stacks) {
        Map<Integer, Integer> ordered = new java.util.TreeMap<>();
        for (int slot = 0; slot < items.length; slot++) {
            MenuView.Item it = items[slot];
            if (it.empty() || it.clean.isEmpty() || !it.clean.chars().allMatch(Character::isDigit)) continue;
            int number;
            try {
                number = Integer.parseInt(it.clean);
            } catch (NumberFormatException e) {
                continue;
            }
            ultraRound = Math.max(ultraRound, number);
            if (stacks != null && !ultraStacks.containsKey(number)) ultraStacks.put(number, stacks[slot]);
            ordered.put(number, slot);
        }
        if (ultraRound != ordered.size() || ultraData.size() == ordered.size() && phase == Phase.REPLICATE) return;
        ultraData.clear();
        ultraUser.clear();
        ultraData.addAll(ordered.values());
    }

    private static boolean blocking() {
        return opt("experiment.block_clicks", true) && !io.muzy.sbc.dungeon.DungeonKeys.overrideDown();
    }

    // skyhanni handleChronomatronClick + handleUltrasequencerClick, and skyblockers refusal while a round still
    // shows. true = refuse
    static boolean addonClick(int slotId, MenuView.Item item, boolean block) {
        if (inChronomatron() && opt("experiment.chronomatron", true)) {
            if (phase != Phase.REPLICATE || chronoUser.size() >= chronoData.size()) return false;
            String clicked = colorOf(item), expected = chronoData.get(chronoUser.size());
            if (clicked != null && clicked.equals(expected)) {
                chronoUser.add(clicked);
                return false;
            }
            if (block) return true;
            // ours: a color let through counts as this step, so the next click isnt refused for a step the player already passed
            if (clicked != null) chronoUser.add(expected);
            return false;
        }
        if (inUltrasequencer() && opt("experiment.ultrasequencer", true)) {
            if (phase == Phase.READ) return block;
            if (phase != Phase.REPLICATE || ultraUser.size() >= ultraData.size()) return false;
            int expected = ultraData.get(ultraUser.size());
            if (slotId == expected) {
                ultraUser.add(slotId);
                return false;
            }
            if (block) return true;
            // ours: one of this rounds items let through counts as this step
            if (ultraData.contains(slotId)) ultraUser.add(expected);
            return false;
        }
        return false;
    }

    // from skyhanni SuperpairsClicksAlert.onInventoryOpen
    private static final Pattern ROUNDS_NEEDED = Pattern.compile("(?:Chain|Series) of (\\d+):");

    private static void readRoundsNeeded(MenuView.Item[] items) {
        for (int i = 24; i >= 20 && i < items.length; i--) {
            MenuView.Item it = items[i];
            if (it.cleanHas("Practice mode has no rewards")) {
                roundsNeeded = -1;
                return;
            }
            if (it.cleanHas("Enchanting level too low!") || it.cleanHas("Not enough experience!")) continue;
            for (int l = it.cleanLore.size() - 1; l >= 0; l--) {
                Matcher m = ROUNDS_NEEDED.matcher(it.cleanLore.get(l));
                if (m.find()) {
                    roundsNeeded = Integer.parseInt(m.group(1));
                    return;
                }
            }
        }
    }

    // skyhanni SuperpairsClicksAlert.onInventoryUpdated: ultrasequencer needs one round less, a hypixel bug
    // skyhanni allows for
    private static void checkMaxClicks() {
        if (roundsNeeded == -1 || !opt("experiment.max_clicks_alert", true)) return;
        boolean done = inChronomatron() ? chronoRound > roundsNeeded : inUltrasequencer() && ultraRound > roundsNeeded - 1;
        if (!done) return;
        roundsNeeded = -1;
        play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.5f);
        tell(Component.literal("§eYou have reached the maximum extra Superpairs clicks from this add-on!"));
    }

    // superpairs

    private static String rewardOf(MenuView.Item it) {
        if (Patterns.get(KEY + "guardian.pet.internalname").matcher(it.id).matches()) {
            String[] parts = it.name.split("] ");
            if (parts.length > 1) return parts[1];
        }
        if (it.clean.equals("Enchanted Book")) return it.cleanLore.size() > 2 ? it.cleanLore.get(2) : "Enchanted Book";
        return it.clean;
    }

    private static boolean hiddenCard(MenuView.Item it) {
        return Patterns.get(KEY + "superpairs.unknown-click").matcher(it.name).matches();
    }

    private static boolean powerup(MenuView.Item it) {
        Pattern p = Patterns.get(KEY + "powerups.lore");
        for (String l : it.lore) if (p.matcher(l).matches()) return true;
        return false;
    }

    // skyhanni ExperimentationSuperpairApi.onInventoryUpdated + scan
    private static void scanSuperpairs(MenuView.Item[] items, ItemStack[] stacks) {
        if (tier == null) return;
        if (items.length > CLICKS_SLOT) {
            Matcher m = Patterns.get(KEY + "clicks").matcher(items[CLICKS_SLOT].clean);
            boolean none = m.matches() && m.group("clicks").equals("0");
            boolean was = frozen;
            frozen = none;
            if (frozen && was) return;
        }
        Map<Integer, MenuView.Item> revealed = new LinkedHashMap<>();
        List<Integer> hidden = new ArrayList<>();
        for (int slot : tier.slots) {
            if (slot >= items.length) continue;
            MenuView.Item it = items[slot];
            if (it.empty()) continue;
            if (hiddenCard(it)) hidden.add(slot);
            else {
                revealed.put(slot, it);
                uncovered.put(slot, it);
                if (stacks != null) uncoveredStacks.put(slot, stacks[slot]);
            }
        }
        List<Card[]> pairs = new ArrayList<>(), matches = new ArrayList<>(), normals = new ArrayList<>(), powerups = new ArrayList<>();
        Map<String, List<Card>> groups = new LinkedHashMap<>();
        for (var e : revealed.entrySet()) {
            Card c = new Card(e.getKey(), rewardOf(e.getValue()), e.getValue().model);
            if (powerup(e.getValue())) powerups.add(new Card[]{c});
            else groups.computeIfAbsent(c.kind(), k -> new ArrayList<>()).add(c);
        }
        for (List<Card> group : groups.values()) {
            List<Card> cards = new ArrayList<>(group);
            // collected pairs stay face up. with an odd count the card you just turned isnt part of one yet
            if (cards.size() % 2 == 1) cards.removeIf(c -> c.slot() == lastClicked);
            for (int i = 0; i + 1 < cards.size(); i += 2) pairs.add(new Card[]{cards.get(i), cards.get(i + 1)});
        }
        groups.clear();
        for (int slot : hidden) {
            MenuView.Item it = uncovered.get(slot);
            if (it == null) continue;
            Card c = new Card(slot, rewardOf(it), it.model);
            groups.computeIfAbsent(c.kind(), k -> new ArrayList<>()).add(c);
        }
        for (List<Card> group : groups.values()) {
            for (int i = 0; i < group.size(); i += 2) {
                if (i + 1 < group.size()) matches.add(new Card[]{group.get(i), group.get(i + 1)});
                else normals.add(new Card[]{group.get(i)});
            }
        }
        found.clear();
        if (!pairs.isEmpty()) found.put(Found.PAIR, pairs);
        if (!matches.isEmpty()) found.put(Found.MATCH, matches);
        if (!normals.isEmpty()) found.put(Found.NORMAL, normals);
        if (!powerups.isEmpty()) found.put(Found.POWERUP, powerups);
    }

    private static List<Card[]> foundOf(Found f) {
        return found.getOrDefault(f, List.of());
    }

    // from skyhanni calculatePossiblePairs
    static int possiblePairs() {
        if (tier == null) return 0;
        int known = foundOf(Found.PAIR).size() + foundOf(Found.MATCH).size() + foundOf(Found.NORMAL).size();
        return (tier.slots.size() - 2) / 2 - known;
    }

    // skyblocker SuperpairsSolver: card that pairs with the one you just turned, while the game waits for your 2nd click
    static int pairFor(MenuView.Item[] items) {
        if (lastClicked < 0 || lastClicked >= items.length) return -1;
        MenuView.Item open = items[lastClicked];
        if (open.empty() || hiddenCard(open)) return -1;
        String kind = rewardOf(open) + "|" + open.model;
        for (var e : uncovered.entrySet()) {
            int slot = e.getKey();
            if (slot == lastClicked || slot >= items.length || !items[slot].clean.equals("Click a second button!")) continue;
            if ((rewardOf(e.getValue()) + "|" + e.getValue().model).equals(kind)) return slot;
        }
        return -1;
    }

    // what you see of it

    // skyhanni SuperPairsItemVisibility + replaceUltrasequencerItems: what a slot shows instead of what it holds
    private static void refreshShown() {
        resetShown();
        if (inSuperpairs() && opt("superpairs.keep_visible", true)) {
            for (var e : uncoveredStacks.entrySet()) {
                int slot = e.getKey();
                if (slot >= SHOWN.length || e.getValue() == null) continue;
                SHOWN[slot] = e.getValue();
                anyShown = true;
            }
        } else if (inUltrasequencer() && opt("experiment.ultrasequencer", true) && phase == Phase.REPLICATE) {
            for (int i = 0; i < ultraData.size(); i++) {
                int slot = ultraData.get(i);
                ItemStack st = ultraStacks.get(i + 1);
                if (slot < SHOWN.length && st != null) {
                    SHOWN[slot] = st;
                    anyShown = true;
                }
            }
        }
    }

    // runs for every slot drawn, costs nothing outside an experiment
    public static ItemStack display(Slot slot, ItemStack stack) {
        if (!anyShown || slot == null || slot.index >= SHOWN.length || slot.container instanceof Inventory) return stack;
        ItemStack shown = SHOWN[slot.index];
        if (shown == null) return stack;
        // a face up card already shows itself
        if (inSuperpairs() && !stack.isEmpty() && !Patterns.get(KEY + "superpairs.unknown-click").matcher(io.muzy.sbc.core.LegacyText.name(stack)).matches()) return stack;
        return shown;
    }

    private static void mark(MenuView v, int slot, int color, boolean frame) {
        if (slot < 0 || slot >= v.top) return;
        v.back(slot, color);
        if (frame) v.mark(slot).border = 0xFF000000 | color;
    }

    private static int withAlpha(int color, int alpha) {
        return (alpha & 0xFF) << 24 | color & 0xFFFFFF;
    }

    // the marks: onBackgroundDrawn of both helpers + SuperpairHighlight, ExperimentationXPOverlay
    static void marks(MenuView v) {
        int next = color("experiment.next_color", 0xFF55FF55), second = color("experiment.second_color", 0x80FFFF55);
        if (inChronomatron() && opt("experiment.chronomatron", true) && phase == Phase.REPLICATE && chronoRound >= 1) {
            String first = chronoUser.size() < chronoData.size() ? chronoData.get(chronoUser.size()) : null;
            String then = chronoUser.size() + 1 < chronoData.size() ? chronoData.get(chronoUser.size() + 1) : null;
            for (int slot = 0; slot < v.top; slot++) {
                String c = colorOf(v.item(slot));
                if (c == null) continue;
                if (c.equals(first)) mark(v, slot, next, true);
                else if (c.equals(then)) mark(v, slot, second, false);
            }
        }
        if (inUltrasequencer() && opt("experiment.ultrasequencer", true) && phase == Phase.REPLICATE && ultraRound >= 1) {
            int shown = 0;
            for (int i = ultraUser.size(); i < ultraData.size(); i++, shown++) {
                int slot = ultraData.get(i);
                if (shown == 0) mark(v, slot, next, true);
                else mark(v, slot, withAlpha(second, 255 / shown), false);
            }
        }
        if (!inSuperpairs()) return;
        if (opt("superpairs.highlight", true)) {
            for (Card[] p : foundOf(Found.PAIR)) for (Card c : p) mark(v, c.slot(), color("superpairs.collected_color", 0x8055FF55), false);
            for (Card[] p : foundOf(Found.MATCH)) for (Card c : p) mark(v, c.slot(), color("superpairs.matched_color", 0x80FFAA00), false);
            for (Card[] p : foundOf(Found.NORMAL)) mark(v, p[0].slot(), color("superpairs.seen_color", 0x80FFFF55), false);
            for (Card[] p : foundOf(Found.POWERUP)) mark(v, p[0].slot(), color("superpairs.powerup_color", 0x80AA00AA), false);
            mark(v, pairFor(v.items), next, true);
        }
        boolean keep = opt("superpairs.keep_visible", true), xp = opt("superpairs.xp_overlay", true);
        Pattern enchanting = Patterns.get(KEY + "enchantingxp");
        for (int slot = 0; slot < v.top; slot++) {
            MenuView.Item it = v.item(slot);
            MenuView.Item card = hiddenCard(it) && keep ? uncovered.get(slot) : it;
            if (card == null) continue;
            if (xp) {
                Matcher m = enchanting.matcher(card.name);
                if (m.matches()) v.label(slot, "§3" + m.group("xp") + "k", 1, 10, 0.6f);
            }
            if (card != it) {
                List<String> tip = new ArrayList<>();
                tip.add(card.name);
                tip.addAll(card.lore);
                v.mark(slot).tip = tip;
            }
        }
    }

    // rare finds and the end of an experiment

    private static Set<String> ultraRare() {
        if (ultraRare != null) return ultraRare;
        Set<String> out = new HashSet<>();
        try {
            JsonObject o = Repo.constant("ExperimentationTable.json");
            if (o != null && o.has("ultra_rare_rewards")) for (JsonElement e : o.getAsJsonArray("ultra_rare_rewards")) out.add(e.getAsString());
        } catch (Exception e) {
            io.muzy.sbc.SbcMod.LOG.warn("Could not read the table's rare rewards", e);
        }
        return ultraRare = out;
    }

    // skyhanni tryFireRareBookUncovered + tryFireRareItemUncovered
    private static void rareUncovered(MenuView.Item[] items) {
        if (rareFired) return;
        Pattern rare = Patterns.get(KEY + "ultrarare"), book = Patterns.get(KEY + "book");
        for (MenuView.Item it : items) {
            if (it.lore.size() < 3 || !rare.matcher(it.lore.get(0)).matches()) continue;
            Matcher m = book.matcher(it.lore.get(2));
            if (!m.matches()) continue;
            rareFired = true;
            onRare(m.group("enchant"), true);
            return;
        }
        if (!inSuperpairs()) return;
        for (MenuView.Item it : items) {
            if (it.id.isEmpty() || !ultraRare().contains(it.id)) continue;
            rareFired = true;
            onRare(it.clean, false);
            return;
        }
    }

    // skyhanni UltraRareBookAlert + ExperimentsDryStreakDisplay.onTableRareUncover
    private static void onRare(String name, boolean isBook) {
        lastRare = name;
        lastRareBook = isBook;
        rareAt = System.currentTimeMillis();
        if (opt("superpairs.ultra_rare_alert", true)) {
            play(SoundEvents.ENDER_DRAGON_GROWL, 1f);
            tell(Component.literal("§eYou have uncovered an §d§kXX§5" + (isBook ? " ULTRA-RARE BOOK! " : " ULTRA-RARE ITEM! ") + "§d§kXX§e! You found: §9" + name));
            var hud = Minecraft.getInstance().gui.hud;
            hud.setTimes(5, 40, 10);
            hud.setTitle(Component.literal("§d§kXX§5" + (isBook ? " ULTRA-RARE BOOK! " : " ULTRA-RARE ITEM! ") + "§d§kXX"));
            hud.setSubtitle(Component.literal("§9" + name));
        }
        if (dryOn()) {
            double[] dry = Profit.dryStreak();
            long attempts = (long) dry[0];
            tell(Component.literal("§a§lDRY-STREAK ENDED! §eYou have " + (attempts >= 10 ? "§o(finally)§r§e " : "") + "found an §5ULTRA-RARE §eafter §3" + Prices.coins(dry[1]) + " Enchanting Exp §eand §2" + attempts + (attempts == 1 ? " attempt" : " attempts") + "§e!"));
            Profit.dryReset();
            ignoreNextFinish = true;
        }
    }

    // skyhanni tryProcessExperimentOver: what the claim button lists, once per experiment
    static void readOver(MenuView.Item[] items) {
        if (currentOverHash != 0 || type == null) return;
        int slot = inSuperpairs() ? 13 : 11;
        if (slot >= items.length) return;
        MenuView.Item item = items[slot];
        if (item.lore.isEmpty()) return;
        int hash = item.lore.hashCode();
        if (hash == lastOverHash || hash == 0) return;
        currentOverHash = hash;
        Type t = typeOf(item.clean);
        if (t == null) return;
        type = t;
        Tier r = null;
        Pattern stakes = Patterns.get(KEY + "inventory.experiment-over.stakes");
        for (String l : item.lore) {
            Matcher m = stakes.matcher(l);
            if (m.matches()) {
                r = Tier.of(m.group("stakes"));
                break;
            }
        }
        if (r == null) return;
        tier = r;
        int begin = -1, end = -1;
        for (int i = 0; i < item.lore.size(); i++) {
            if (begin < 0 && Patterns.get(KEY + "inventory.experiment-over.rewards-start").matcher(item.lore.get(i)).matches()) begin = i + 1;
            if (end < 0 && Patterns.get(KEY + "inventory.experiment-over.rewards-end").matcher(item.lore.get(i)).matches()) end = i - 1;
        }
        long xp = 0;
        if (begin >= 0 && end >= begin) {
            Pattern reward = Patterns.get(KEY + "inventory.experiment-over.rewards"), exp = Patterns.get(KEY + "exp");
            for (String l : item.lore.subList(begin, end)) {
                Matcher m = reward.matcher(l);
                if (!m.matches()) continue;
                Matcher x = exp.matcher(m.group("reward"));
                if (x.matches()) xp += (long) parseAmount(x.group("amount"));
            }
        }
        long gained = xp;
        Type done = type;
        queued = () -> completed(done, gained);
    }

    private static double parseAmount(String s) {
        String t = s.replace(",", "").trim();
        double mul = t.endsWith("k") ? 1e3 : t.endsWith("M") ? 1e6 : t.endsWith("B") ? 1e9 : 1;
        if (mul > 1) t = t.substring(0, t.length() - 1);
        try {
            return Double.parseDouble(t) * mul;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // skyhanni TableTaskCompletedEvent: dry streak + tracker counters
    private static void completed(Type done, long xp) {
        boolean superpairs = done == Type.SUPERPAIRS;
        Profit.experimentDone(superpairs);
        if (ignoreNextFinish) ignoreNextFinish = false;
        else if (dryOn()) Profit.dryAdd(superpairs ? 1 : 0, xp);
        type = null;
        tier = null;
        rareFired = false;
    }

    // tests: claim wasnt declined and the menu is closed
    static void fireQueued() {
        if (queued != null) queued.run();
        queued = null;
    }

    // the games side

    private static void tell(Component line) {
        var p = Minecraft.getInstance().player;
        if (p != null) p.sendSystemMessage(line);
    }

    private static void play(net.minecraft.sounds.SoundEvent sound, float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }

    // skyhanni SlotClickEvent, true eats the click
    private static boolean click(AbstractContainerScreen<?> s, Slot slot, int slotId, int button) {
        String title = GameState.strip(s.getTitle().getString()).trim();
        boolean bottles = Patterns.get(KEY + "inventory.bottles-of-enchanting").matcher(title).matches();
        if (bottles && slot != null && !slot.getItem().isEmpty() && (slotId == 11 || slotId == 12 || slotId == 14 || slotId == 15)) bottleBought(slot.getItem());
        if (!inTable(title) || slot == null || slot.container instanceof Inventory) return false;
        String name = GameState.strip(slot.getItem().getHoverName().getString());
        if (name.equals("Decline")) queued = null;
        if (!Features.on(FEATURE) || !playing(title)) return false;
        if (inSuperpairs()) {
            if (tier != null && tier.slots.contains(slotId)) lastClicked = slotId;
            MenuMarks.invalidate();
            return false;
        }
        boolean refuse = addonClick(slotId, MenuView.Item.of(slot.getItem()), blocking());
        MenuMarks.invalidate();
        return refuse;
    }

    // skyhanni ExperimentsProfitTracker.onSlotClick: a bottle you dont have gets bought for you
    private static void bottleBought(ItemStack clicked) {
        String id = Gain.sbId(clicked);
        if (!Patterns.get(KEY + "xpbottle").matcher(id).matches()) return;
        if (bottleCounts().containsKey(id)) return;
        Profit.experimentCost(id, 1);
    }

    private static Map<String, Integer> bottleCounts() {
        Map<String, Integer> out = new HashMap<>();
        var p = Minecraft.getInstance().player;
        if (p == null) return out;
        Pattern bottle = Patterns.get(KEY + "xpbottle");
        var inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st.isEmpty()) continue;
            String id = Gain.sbId(st);
            if (!id.isEmpty() && bottle.matcher(id).matches()) out.merge(id, st.getCount(), Integer::sum);
        }
        return out;
    }

    private static Map<String, Integer> bottlesBefore = new HashMap<>();
    private static long bottleCheckAt;
    private static boolean warnedBottle;

    // skyhanni handleXpBottlesUsed: a bottle thrown near the table was meant for it
    private static boolean itemClick(boolean right) {
        if (!right || !nearTable(15)) return false;
        var p = Minecraft.getInstance().player;
        if (p == null) return false;
        if (!Patterns.get(KEY + "xpbottle").matcher(Gain.sbId(p.getMainHandItem())).matches()) return false;
        bottlesBefore = bottleCounts();
        bottleCheckAt = System.currentTimeMillis() + 200;
        return false;
    }

    private static void checkBottles() {
        if (bottleCheckAt == 0 || System.currentTimeMillis() < bottleCheckAt) return;
        bottleCheckAt = 0;
        Map<String, Integer> now = bottleCounts();
        for (var e : bottlesBefore.entrySet()) {
            int used = e.getValue() - now.getOrDefault(e.getKey(), 0);
            if (used <= 0 || !Features.on("experiments_profit") || !Profit.experimentOption("experiments.used_bottles", true)) continue;
            Profit.experimentCost(e.getKey(), used);
            if (!warnedBottle && Profit.experimentOption("experiments.bottle_warnings", true)) {
                warnedBottle = true;
                tell(Component.literal("§aExperiments profit§7: §eCounted " + (used > 1 ? used + "x " : "") + Profit.pretty(e.getKey()) + " §eas used near the table. §7This note can be turned off in the settings."));
            }
        }
    }

    private static boolean guardianPet() {
        for (io.muzy.sbc.core.TabWidgets.Widget w : io.muzy.sbc.core.TabWidgets.parse(GameState.tab)) {
            if (!w.def().id().equals("PET")) continue;
            for (int i : w.lines()) if (GameState.strip(GameState.tab.get(i)).contains("Guardian")) return true;
        }
        return false;
    }

    private static long tableOpenedAt;
    private static boolean remindedAt;

    // from skyhanni GuardianReminder
    private static void guardian(String title) {
        if (!opt("experiment.guardian_reminder", false) || !title.equals("Experimentation Table")) return;
        long now = System.currentTimeMillis();
        if (now - tableOpenedAt < 200 || remindedAt) return;
        remindedAt = true;
        if (guardianPet()) return;
        tell(Component.literal("§eUse a §9§lGuardian Pet §efor more Exp in the Experimentation Table. §7Click to open your pets.").setStyle(Style.EMPTY
                .withClickEvent(new ClickEvent.RunCommand("/pets")).withHoverEvent(new HoverEvent.ShowText(Component.literal("§eClick to open your pets")))));
        var hud = Minecraft.getInstance().gui.hud;
        hud.setTimes(5, 40, 10);
        hud.setTitle(Component.literal("§cWrong Pet equipped!"));
        hud.setSubtitle(Component.empty());
        play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.2f);
    }

    // from skyhanni ExperimentsProfitTracker.onSecondPassed
    private static void timeSpent(String title) {
        long now = System.currentTimeMillis();
        if (title == null || !inTable(title) || isOver(title) || type == null || !Profit.experimentOption("experiments.time_spent", true)) {
            lastTime = 0;
            return;
        }
        if (now - lastTime < 1000 && lastTime != 0) return;
        if (lastTime != 0 && Features.on("experiments_profit")) Profit.experimentTime(now - lastTime);
        lastTime = now;
    }

    private static void tick(Minecraft mc) {
        if (!GameState.inSkyBlock || mc.player == null) return;
        checkBottles();
        Screen now = mc.gui.screen();
        if (now != screen) {
            boolean wasTable = screen != null && inTable(GameState.strip(screen.getTitle().getString()).trim());
            screen = now;
            seen = new ItemStack[0];
            if (wasTable) {
                closed();
                leftOverAt = System.currentTimeMillis();
            }
            tableOpenedAt = System.currentTimeMillis();
            remindedAt = false;
        }
        String title = now instanceof AbstractContainerScreen<?> ? GameState.strip(now.getTitle().getString()).trim() : null;
        if (queued != null && (title == null || !isOver(title)) && System.currentTimeMillis() - leftOverAt >= 150) fireQueued();
        timeSpent(title);
        if (title == null || !inTable(title)) return;
        guardian(title);
        AbstractContainerScreen<?> s = (AbstractContainerScreen<?>) now;
        List<Slot> slots = s.getMenu().slots;
        int top = 0;
        for (Slot slot : slots) {
            if (slot.container instanceof Inventory) break;
            top++;
        }
        boolean changed = seen.length != top;
        if (!changed) for (int i = 0; i < top; i++) {
            if (slots.get(i).getItem() != seen[i]) {
                changed = true;
                break;
            }
        }
        if (!changed) return;
        ItemStack[] stacks = new ItemStack[top];
        MenuView.Item[] items = new MenuView.Item[top];
        for (int i = 0; i < top; i++) {
            stacks[i] = slots.get(i).getItem();
            items[i] = MenuView.Item.of(stacks[i]);
        }
        seen = stacks;
        update(title, items, stacks);
    }

    private static boolean sound(SoundHooks.Sound s) {
        // the sound that says a chronomatron round is done
        if (inChronomatron() && s.name().equals("entity.player.levelup") && s.pitch() == 1.7619047f && s.volume() == 0.7f) lastRoundSound = System.currentTimeMillis();
        return false;
    }

    // next to the chest

    private static final class Panel implements SideHelper.Content {
        @Override
        public String title() {
            return "Experiments";
        }

        @Override
        public ItemStack icon() {
            return io.muzy.sbc.core.LegacyItems.stack("enchanting_table");
        }

        @Override
        public int accent() {
            return 0xFFB57BFF;
        }

        @Override
        public String[] tabs() {
            return new String[]{inSuperpairs() ? "Superpairs" : "This run"};
        }

        @Override
        public void refresh(AbstractContainerScreen<?> s) {
            MenuMarks.current();
        }

        @Override
        public int summary(Ui ui, int x, int y, int w) {
            int half = (w - 3) / 2;
            if (!dryOn()) return 0;
            double[] dry = Profit.dryStreak();
            boolean attempts = dryOption("dry.attempts"), xp = dryOption("dry.xp");
            if (attempts && xp) {
                SideHelper.tile(ui, x, y, half, "Dry attempts", Prices.coins(dry[0]), "", 0xFFFFD166);
                SideHelper.tile(ui, x + half + 3, y, w - half - 3, "XP since rare", Prices.coins(dry[1]), "", 0xFF55FFFF);
            } else if (attempts || xp) {
                SideHelper.tile(ui, x, y, w, attempts ? "Dry attempts" : "XP since rare", Prices.coins(attempts ? dry[0] : dry[1]), "", 0xFFFFD166);
            } else return 0;
            return 26;
        }

        private void group(Ui ui, String head, List<String> rows, int x, int[] y, int w) {
            if (rows.isEmpty()) return;
            Modern.row(ui, head, "§f" + rows.size(), x, y[0], w, Modern.LABEL, Modern.INK);
            y[0] += 11;
            for (String r : rows) {
                ui.textShadow(ui.ellipsize(r, w - 8), x + 6, y[0], Modern.LABEL);
                y[0] += 10;
            }
        }

        @Override
        public int list(Ui ui, int tab, int x, int y, int w) {
            int[] at = {y};
            if (System.currentTimeMillis() - rareAt < 5000 && !lastRare.isEmpty()) {
                ui.textShadow(ui.ellipsize("§5" + (lastRareBook ? "ULTRA-RARE BOOK: " : "ULTRA-RARE ITEM: ") + "§9" + lastRare, w - 4), x + 2, at[0], Modern.INK);
                at[0] += 12;
            }
            if (inSuperpairs() && opt("superpairs.data", true)) {
                List<String> collected = new ArrayList<>(), matched = new ArrayList<>(), power = new ArrayList<>(), left = new ArrayList<>();
                for (Card[] p : foundOf(Found.PAIR)) collected.add("§a" + p[0].reward());
                for (Card[] p : foundOf(Found.MATCH)) matched.add("§e" + p[0].reward());
                for (Card[] p : foundOf(Found.POWERUP)) power.add("§b" + p[0].reward());
                if (possiblePairs() >= 1) left.add("§ePairs - " + possiblePairs());
                int powerLeft = 2 - foundOf(Found.POWERUP).size();
                if (powerLeft >= 1) left.add("§bPowerUps - " + powerLeft);
                if (!foundOf(Found.NORMAL).isEmpty()) left.add("§7Normals - " + foundOf(Found.NORMAL).size());
                group(ui, "§aCollected", collected, x, at, w);
                group(ui, "§eMatched", matched, x, at, w);
                group(ui, "§bPowerUp", power, x, at, w);
                group(ui, "§cNot Collected", left, x, at, w);
            } else if (type != null && !inSuperpairs()) {
                int round = inChronomatron() ? chronoRound : ultraRound;
                Modern.row(ui, "Round", "§e" + round, x, at[0], w, Modern.LABEL, Modern.INK);
                at[0] += 11;
                if (roundsNeeded > 0) {
                    Modern.row(ui, "Most clicks at", "§a" + roundsNeeded, x, at[0], w, Modern.LABEL, Modern.INK);
                    at[0] += 11;
                }
            }
            if (at[0] == y) at[0] += SideHelper.emptyText(ui, "Start an experiment to see it here.", x, at[0], w);
            return at[0] - y;
        }
    }

    private static boolean dryOption(String key) {
        Feature f = Features.get(DRY);
        Option o = f == null ? null : f.option(key);
        return o == null || o.bool();
    }

    private static final Panel PANEL = new Panel();

    private static SideHelper.Content panel(Screen s) {
        if (!Features.on(FEATURE) && !dryOn()) return null;
        return inTable(GameState.strip(s.getTitle().getString()).trim()) ? PANEL : null;
    }

    // for the picture: a half played superpairs game
    public static SideHelper.Content panelForTest() {
        clearAll();
        MenuView.Item[] items = new MenuView.Item[54];
        java.util.Arrays.fill(items, MenuView.Item.EMPTY);
        MenuView.Item growth = MenuView.Item.of("", "§fEnchanted Book", "enchanted_book", "§7x", "", "§9Growth VI");
        MenuView.Item xp = MenuView.Item.of("", "§331k Enchanting Exp", "red_dye");
        MenuView.Item power = MenuView.Item.of("", "§bInstant Find", "gold_ingot", "§7Superpairs Powerup");
        int[] slots = {19, 20, 21, 22, 23};
        MenuView.Item[] shown = {growth, growth, xp, power, MenuView.Item.of("", "§8?", "cyan_stained_glass")};
        for (int i = 0; i < slots.length; i++) items[slots[i]] = shown[i];
        update("Superpairs (Beginner)", items, null);
        return PANEL;
    }

    static void register(java.util.function.BooleanSupplier skyblock) {
        Features.add(new Feature(FEATURE, "Skills", "Experiment solvers", "Chronomatron and Ultrasequencer: the next click stands out and a wrong click is refused. Superpairs: every card you have seen stays visible, pairs stand out.", "Skyblocker: experiment solvers, SkyHanni: Experimentation Table")
                .when(skyblock)
                .option(Option.bool("experiment.chronomatron", "Chronomatron", "The next colour to click.", true))
                .option(Option.bool("experiment.ultrasequencer", "Ultrasequencer", "The next number to click, and the numbers stay on the items.", true))
                .option(Option.bool("superpairs.keep_visible", "Superpairs: keep cards visible", "Cards you turned keep showing what they are.", true))
                .option(Option.bool("experiment.block_clicks", "Refuse wrong clicks", "In Chronomatron and Ultrasequencer. With this off or while you hold the key a wrong click goes through, and the helper moves on to the next step as if it was right.", true))
                .option(Option.bool("experiment.max_clicks_alert", "Alert at the most clicks", "A sound and a line when an add-on has given all the Superpairs clicks it can.", true))
                .option(Option.color("experiment.next_color", "Next click colour", "", 0xFF55FF55))
                .option(Option.color("experiment.second_color", "Click after that colour", "", 0x80FFFF55))
                .option(Option.bool("superpairs.highlight", "Superpairs: highlight pairs", "Collected pairs, known matches, cards seen once and powerups each have a colour.", true))
                .option(Option.color("superpairs.collected_color", "Collected pair colour", "", 0x8055FF55))
                .option(Option.color("superpairs.matched_color", "Known match colour", "", 0x80FFAA00))
                .option(Option.color("superpairs.seen_color", "Seen once colour", "", 0x80FFFF55))
                .option(Option.color("superpairs.powerup_color", "Powerup colour", "", 0x80AA00AA))
                .option(Option.bool("superpairs.data", "Superpairs: what you found", "Beside the chest.", true))
                .option(Option.bool("superpairs.xp_overlay", "Superpairs: XP on the card", "What each Enchanting Exp card is worth.", true))
                .option(Option.bool("superpairs.ultra_rare_alert", "Ultra-rare alert", "A sound, a title and a line when you turn an ultra-rare reward.", true))
                .option(Option.bool("experiment.guardian_reminder", "Guardian pet reminder", "A warning and a sound when you open the table without a Guardian pet.", false)));
        Features.add(new Feature(DRY, "Skills", "Experiments dry streak", "Attempts and Enchanting XP since your last ultra-rare, beside the table's menus. /sbc resetdrystreak starts it over.", "SkyHanni: Dry-Streak Display")
                .when(skyblock)
                .option(Option.bool("dry.attempts", "Attempts", "", true))
                .option(Option.bool("dry.xp", "XP", "", true)));
        MenuMarks.add(FEATURE, t -> inTable(t) && playing(t), Experiments::marks);
        Interactions.onSlotClick(Experiments::click);
        Interactions.onItemClick(Experiments::itemClick);
        SoundHooks.listen(Experiments::sound);
        SideHelper.add(Experiments::panel);
        Safe.endTick("Experiments.tick", Experiments::tick);
        io.muzy.sbc.core.Commands.describe("resetdrystreak", "Start the Experimentation Table dry streak over");
        Safe.commands("Experiments.commands", (d, r) -> d.register(ClientCommands.literal("sbc").then(ClientCommands.literal("resetdrystreak").executes(c -> {
            Profit.dryReset();
            c.getSource().sendFeedback(Component.literal("§7Dry-streak counter reset."));
            return 1;
        }))));
    }
}
