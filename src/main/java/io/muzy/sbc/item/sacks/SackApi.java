// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.sacks;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.regex.Matcher;

// from skyhanni SackApi: what a sack menu shows
// normal sacks (stored/total per item from "§7Stored:"), gemstones sack (rough/flawed/fine + filter), runes
// sack (3 levels), trophy fishing sacks in magmafish, each with a price
// plus the "[Sacks]" chat hover ("Added/Removed ... (sack)") that keeps stored amounts current between visits
public final class SackApi {
    static final Patterns.P SACK = Patterns.p("SH.data.sacks.sack");
    static final Patterns.P NUMBER = Patterns.p("SH.data.sacks.number");
    static final Patterns.P GEM_COUNT = Patterns.p("SH.data.sacks.gemstone.count.colorless");
    static final Patterns.P GEM_NAME = Patterns.p("SH.data.sacks.gemstone.name.colorless");
    static final Patterns.P GEM_FILTER = Patterns.p("SH.data.sacks.gemstone.filter.colorless");
    static final Patterns.P CHANGE = Patterns.p("SH.data.sacks.change");
    static final Patterns.P IGNORE_INTERNAL = Patterns.p("SH.data.sacks.ignore.internal-name");

    public enum SackStatus { MISSING, CORRECT, ALRIGHT, OUTDATED }

    // skyhanni SackItem (stored per profile)
    public record Stored(int amount, int lastChange, SackStatus status) {}

    // one menu item: formatted name, clean name, formatted lore, skyblock id
    public record Item(String formattedName, String cleanName, List<String> lore, String internalName) {}

    public static final class SackOtherItem {
        public String internalName = "", colorCode = "";
        public int stored, total, magmaFish, slot = -1;
        public long price;
    }

    public static final class SackRune {
        public String name = "";
        public int lvl1, lvl2, lvl3, stored, slot = -1;
    }

    public static final class SackGemstone {
        public String gemType = "", internalName = "";
        public int rough, flawed, fine, stored, slot = -1;
        public long roughPrice, flawedPrice, finePrice, price;

        public long priceSum() {
            return roughPrice + flawedPrice + finePrice;
        }
    }

    public static boolean isTrophySack, isRuneSack, isGemstoneSack;
    public static String gemstoneStackFilter;
    public static final Map<String, SackOtherItem> sackItem = new LinkedHashMap<>();
    public static final Map<String, SackRune> runeItem = new LinkedHashMap<>();
    public static final Map<String, SackGemstone> gemstoneItem = new LinkedHashMap<>();
    static String lastOpenedInventory = "";

    // per profile contents (skyhanni sackData)
    public static Map<String, Stored> sackData = new LinkedHashMap<>();

    // price source (ItemPriceSource sell), fillet per trophy fish, item name lookup. swappable in tests
    public static ToDoubleFunction<String> price = id -> 0;
    public static java.util.function.ToIntFunction<String> filletPerTrophy = id -> 0;
    public static java.util.function.Function<String, String> idFromName = n -> n;
    public static Runnable onSaved = () -> {};

    private SackApi() {}

    public static boolean isSackInventory(String name) {
        return SACK.matcher(name).matches();
    }

    public static void onInventoryClose() {
        isRuneSack = isGemstoneSack = isTrophySack = false;
        gemstoneStackFilter = null;
        runeItem.clear();
        gemstoneItem.clear();
        sackItem.clear();
    }

    static int formatInt(String s) {
        s = s.replace(",", "").toLowerCase(Locale.ROOT);
        double mult = s.endsWith("k") ? 1000 : s.endsWith("m") ? 1_000_000 : s.endsWith("b") ? 1_000_000_000 : 1;
        if (mult > 1) s = s.substring(0, s.length() - 1);
        return (int) Math.round(Double.parseDouble(s) * mult);
    }

    private static long sackPrice(String internalName, int stored) {
        return (long) price.applyAsDouble(internalName) * stored;
    }

    // skyhanni onInventoryFullyOpened + getSacksData: reads every item of an open sack
    // returns isNewInventory (a different sack than last time), which also saves the amounts
    public static boolean onSackOpen(String inventoryName, Map<Integer, Item> items) {
        boolean isNew = !inventoryName.equals(lastOpenedInventory);
        lastOpenedInventory = inventoryName;
        onInventoryClose();
        isRuneSack = inventoryName.equals("Runes Sack");
        isGemstoneSack = inventoryName.equals("Gemstones Sack");
        if (isGemstoneSack) {
            Item filter = items.get(41);
            if (filter != null) for (String l : filter.lore()) {
                Matcher m = GEM_FILTER.matcher(GameState.strip(l));
                if (m.matches()) {
                    String q = m.group("quality").toUpperCase(Locale.ROOT);
                    gemstoneStackFilter = io.muzy.sbc.item.value.ItemNbt.GEM_QUALITIES.contains(q) ? q : null;
                }
            }
        }
        isTrophySack = inventoryName.contains("Trophy Fishing Sack");
        for (var e : items.entrySet()) {
            if (isGemstoneSack) processGemstoneItem(e.getKey(), e.getValue(), isNew);
            else if (isRuneSack) processRuneItem(e.getKey(), e.getValue(), isNew);
            else processOtherItem(e.getKey(), e.getValue(), isNew);
        }
        if (isNew) onSaved.run();
        return isNew;
    }

    private static void processGemstoneItem(int slot, Item value, boolean saving) {
        Matcher n = GEM_NAME.matcher(value.cleanName());
        if (!n.matches()) return;
        String gemType = n.group("gem").toUpperCase(Locale.ROOT);
        if (!io.muzy.sbc.item.value.ItemNbt.GEM_TYPES.contains(gemType)) return;
        SackGemstone gem = new SackGemstone();
        gem.gemType = gemType;
        gem.internalName = "ROUGH_" + gemType + "_GEM";
        gem.slot = slot;
        for (String l : value.lore()) {
            Matcher m = GEM_COUNT.matcher(GameState.strip(l));
            if (!m.matches()) continue;
            int stored = formatInt(m.group("stored"));
            String q = m.group("quality");
            String quality = q.equals("Amount") ? gemstoneStackFilter : q.toUpperCase(Locale.ROOT);
            if (quality == null) continue;
            int multiplier;
            switch (quality) {
                case "ROUGH" -> multiplier = 1;
                case "FLAWED" -> multiplier = 80;
                case "FINE" -> multiplier = 80 * 80;
                default -> {
                    continue;
                }
            }
            gem.stored += stored * multiplier;
            String internalName = quality + "_" + gemType + "_GEM";
            long price = sackPrice(internalName, stored);
            switch (quality) {
                case "ROUGH" -> {
                    gem.roughPrice = price;
                    gem.rough = stored;
                }
                case "FLAWED" -> {
                    gem.flawedPrice = price;
                    gem.flawed = stored;
                }
                default -> {
                    gem.finePrice = price;
                    gem.fine = stored;
                }
            }
            gem.price += price;
            if (saving) setSackItem(internalName, stored);
            if (quality.equals("FINE") || gemstoneStackFilter != null) gemstoneItem.put(value.formattedName(), gem);
        }
    }

    private static void processRuneItem(int slot, Item value, boolean saving) {
        SackRune rune = new SackRune();
        rune.name = value.formattedName();
        for (String l : value.lore()) {
            Matcher m = NUMBER.matcher(l);
            if (!m.matches()) continue;
            String lv = m.group("level");
            int level = lv == null ? 0 : lv.length();
            int stored = formatInt(m.group("stored"));
            rune.stored += stored;
            switch (level) {
                case 1 -> rune.lvl1 = stored;
                case 2 -> rune.lvl2 = stored;
                case 3 -> {
                    rune.slot = slot;
                    rune.lvl3 = stored;
                    runeItem.put(value.formattedName(), rune);
                }
                default -> {
                }
            }
            if (saving) setSackItem(value.internalName(), stored);
        }
    }

    // "Stored: 27/20.2k" without colors, for lore with resets between them
    private static final java.util.regex.Pattern PLAIN_NUMBER = java.util.regex.Pattern.compile("Stored: (?<stored>[0-9.,kKmMbB]+)/(?<total>[0-9.,]+[kKmMbB]?)");

    private static void processOtherItem(int slot, Item value, boolean saving) {
        for (String l : value.lore()) {
            Matcher m = NUMBER.matcher(l);
            String color;
            if (m.matches()) color = m.group("color");
            else {
                m = PLAIN_NUMBER.matcher(GameState.strip(l).trim());
                if (!m.matches()) continue;
                color = "§e";
            }
            int stored = formatInt(m.group("stored"));
            String internalName = value.internalName();
            // the witch's sack shows its ingredients without an id
            if (internalName.isEmpty()) internalName = idFromName.apply(value.cleanName());
            if (internalName == null || internalName.isEmpty()) continue;
            if (IGNORE_INTERNAL.matcher(internalName).matches()) {
                String byName = idFromName.apply(value.cleanName());
                if (byName != null && !byName.isEmpty()) internalName = byName;
            }
            SackOtherItem item = new SackOtherItem();
            item.internalName = internalName;
            item.colorCode = color;
            item.stored = stored;
            item.total = formatInt(m.group("total"));
            if (saving) setSackItem(internalName, stored);
            if (isTrophySack) {
                int fillet = filletPerTrophy.applyAsInt(internalName) * stored;
                item.magmaFish = fillet;
                item.price = sackPrice("MAGMA_FISH", fillet);
            } else item.price = Math.max(0, sackPrice(internalName, stored));
            item.slot = slot;
            sackItem.put(internalName, item);
            return;
        }
    }

    // chat

    public record SackChange(int delta, String internalName, List<String> sacks) {}

    // whoever else wants to know what just went in or out of the sacks, the gemstone money card
    public static final List<java.util.function.Consumer<List<SackChange>>> onChange = new ArrayList<>();

    // skyhanni onChat for a "[Sacks]" line: the colorless Added / Removed hover texts. null if its not a sack change
    public static List<SackChange> onSackChat(String addText, String removeText) {
        String text = (addText == null ? "" : addText) + (removeText == null ? "" : removeText);
        if (text.isEmpty()) return null;
        boolean otherItemsAdded = addText != null && addText.contains("other items");
        boolean otherItemsRemoved = removeText != null && removeText.contains("other items");
        List<SackChange> changes = new ArrayList<>();
        Matcher m = CHANGE.matcher(text);
        while (m.find()) {
            int delta = Integer.parseInt(m.group("delta").replace(",", "").replace("+", ""));
            String item = m.group("item");
            changes.add(new SackChange(delta, idFromName.apply(item), List.of(m.group("sacks").split(", "))));
        }
        updateSacks(changes, otherItemsAdded, otherItemsRemoved);
        for (var l : onChange) l.accept(changes);
        return changes;
    }

    private static void updateSacks(List<SackChange> changes, boolean otherItemsAdded, boolean otherItemsRemoved) {
        Map<String, Integer> justChanged = new LinkedHashMap<>();
        for (SackChange c : changes) justChanged.merge(c.internalName(), c.delta(), Integer::sum);
        for (var e : justChanged.entrySet()) {
            Stored old = sackData.get(e.getKey());
            if (old != null) {
                int newAmount = old.amount() + e.getValue();
                int changed = newAmount - old.amount();
                if (newAmount < 0) {
                    newAmount = 0;
                    changed = 0;
                }
                sackData.put(e.getKey(), new Stored(newAmount, changed, old.status()));
            } else {
                int newAmount = Math.max(0, e.getValue());
                sackData.put(e.getKey(), new Stored(newAmount, newAmount, SackStatus.OUTDATED));
            }
        }
        // "and other items": the ones not listed may have changed. after a removal (a Sell Sacks, say) their old amount
        // can be gone, so it is not known any more; after an addition it is at least what it was
        if (otherItemsAdded || otherItemsRemoved) for (var e : new ArrayList<>(sackData.entrySet())) {
            if (justChanged.containsKey(e.getKey())) continue;
            sackData.put(e.getKey(), new Stored(e.getValue().amount(), 0, otherItemsRemoved ? SackStatus.OUTDATED : SackStatus.ALRIGHT));
        }
        onSaved.run();
    }

    private static void setSackItem(String item, int amount) {
        sackData.put(item, new Stored(amount, 0, SackStatus.CORRECT));
    }

    // skyhanni getAmountInSacksOrNull: amount when the data is CORRECT or ALRIGHT, else null
    public static Integer getAmountInSacksOrNull(String item) {
        Stored s = sackData.get(item);
        if (s == null) {
            sackData.put(item, new Stored(0, 0, SackStatus.MISSING));
            return null;
        }
        return s.status() == SackStatus.CORRECT || s.status() == SackStatus.ALRIGHT ? s.amount() : null;
    }

    // skyhanni SackDisplay.onBackgroundDrawn: a full sack item ("§7Stored: §a...")
    public static boolean isFull(List<String> lore) {
        for (String l : lore) if (l.startsWith("§7Stored: §a")) return true;
        return false;
    }
}
