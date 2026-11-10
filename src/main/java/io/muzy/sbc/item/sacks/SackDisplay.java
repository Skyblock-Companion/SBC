// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.sacks;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.item.enchants.EnchantParser;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

// from skyhanni SackDisplay: lines next to an open sack
// items with stored / total + price (sorted), gems with qualities, runes with 3 levels, magmafish totals for
// trophy sacks, total price
public final class SackDisplay {
    public enum SortingType { DESC_STORED, ASC_STORED, DESC_PRICE, ASC_PRICE }

    public enum NumberFormat { DEFAULT, FORMATTED, UNFORMATTED }

    public static SortingType sortingType = SortingType.DESC_STORED;
    public static NumberFormat numberFormat = NumberFormat.DEFAULT;
    public static boolean showPrice = true, showEmpty = true, priceFormatted = true;
    public static int itemToShow = 15;
    public static Function<String, String> itemName = id -> id;

    private SackDisplay() {}

    static String sep(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    static String format(long price) {
        return priceFormatted ? EnchantParser.shortFormat(price) : sep(price);
    }

    private static <T> List<T> sort(List<T> items, java.util.function.ToIntFunction<T> stored, java.util.function.ToLongFunction<T> price) {
        List<T> out = new ArrayList<>(items);
        Comparator<T> c = switch (sortingType) {
            case DESC_STORED -> Comparator.comparingInt(stored).reversed();
            case ASC_STORED -> Comparator.comparingInt(stored);
            case DESC_PRICE -> Comparator.comparingLong(price).reversed();
            case ASC_PRICE -> Comparator.comparingLong(price);
        };
        out.sort(c);
        if (!showEmpty) out.removeIf(t -> stored.applyAsInt(t) == 0);
        return out;
    }

    // same selection + order as the lines below, for the card that draws them as tables
    static List<SackApi.SackOtherItem> sortedItems() {
        return sort(new ArrayList<>(SackApi.sackItem.values()), i -> i.stored, i -> i.price);
    }

    static List<SackApi.SackGemstone> sortedGems() {
        return sort(new ArrayList<>(SackApi.gemstoneItem.values()), g -> g.stored, g -> g.price);
    }

    static List<SackApi.SackRune> sortedRunes() {
        return sort(new ArrayList<>(SackApi.runeItem.values()), r -> r.stored, r -> 0L);
    }

    public static List<String> drawDisplay() {
        List<String> out = new ArrayList<>();
        long totalPrice = 0;
        totalPrice += drawNormalList(out);
        totalPrice += drawGemstoneDisplay(out);
        drawRunesDisplay(out);
        if (showPrice) out.add("§eTotal price: §6" + format(totalPrice));
        return out;
    }

    private static long drawNormalList(List<String> out) {
        List<SackApi.SackOtherItem> items = new ArrayList<>(SackApi.sackItem.values());
        if (items.isEmpty()) return 0;
        long totalPrice = 0, totalMagmaFish = 0;
        int rendered = 0;
        List<SackApi.SackOtherItem> sorted = sort(items, i -> i.stored, i -> i.price);
        int amountShowing = Math.min(itemToShow, sorted.size());
        out.add("§7Items in Sacks: §o(Rendering " + amountShowing + " of " + sorted.size() + " items)");
        for (SackApi.SackOtherItem item : sorted) {
            totalPrice += item.price;
            if (rendered >= itemToShow) continue;
            if (item.stored == 0 && !showEmpty) continue;
            String name = itemName.apply(item.internalName).replace("§k", "");
            StringBuilder row = new StringBuilder(" §7- " + name + " ");
            switch (numberFormat) {
                case DEFAULT -> row.append(item.colorCode).append(sep(item.stored)).append("§7/§b").append(EnchantParser.shortFormat(item.total));
                case FORMATTED -> row.append(item.colorCode).append(EnchantParser.shortFormat(item.stored)).append("§7/§b").append(EnchantParser.shortFormat(item.total));
                case UNFORMATTED -> row.append(item.colorCode).append(sep(item.stored)).append("§7/§b").append(sep(item.total));
            }
            if (SackApi.isTrophySack && item.magmaFish > 0) {
                totalMagmaFish += item.magmaFish;
                row.append(" §d").append(item.magmaFish).append(" §6Magmafish");
            }
            if (showPrice && item.price != 0) row.append(" §6").append(format(item.price));
            out.add(row.toString());
            rendered++;
        }
        if (SackApi.isTrophySack) out.add("§cTotal Magmafish: §6" + sep(totalMagmaFish));
        return totalPrice;
    }

    private static void drawRunesDisplay(List<String> out) {
        if (SackApi.runeItem.isEmpty()) return;
        out.add("§7Runes:");
        for (SackApi.SackRune rune : sort(new ArrayList<>(SackApi.runeItem.values()), r -> r.stored, r -> 0L))
            out.add(" §7- " + rune.name + " §e" + rune.lvl1 + " §e" + rune.lvl2 + " §e" + rune.lvl3);
    }

    private static long drawGemstoneDisplay(List<String> out) {
        if (SackApi.gemstoneItem.isEmpty()) return 0;
        String filter = SackApi.gemstoneStackFilter;
        String filterFormat = filter == null ? "" : " (" + filter.charAt(0) + filter.substring(1).toLowerCase(Locale.ROOT) + "§7)";
        out.add("§7Gemstones" + filterFormat + "§7:");
        long totalPrice = 0;
        for (SackApi.SackGemstone gem : sort(new ArrayList<>(SackApi.gemstoneItem.values()), g -> g.stored, g -> g.price)) {
            String name = GameState.pretty(gem.gemType) + " Gemstones";
            StringBuilder row = new StringBuilder(" §7- " + name + " ");
            long price;
            if ("ROUGH".equals(filter)) {
                row.append(sep(gem.rough));
                price = gem.roughPrice;
            } else if ("FLAWED".equals(filter)) {
                row.append("§a").append(sep(gem.flawed));
                price = gem.flawedPrice;
            } else if ("FINE".equals(filter)) {
                row.append("§9").append(sep(gem.fine));
                price = gem.finePrice;
            } else {
                row.append(sep(gem.rough)).append(" §a").append(sep(gem.flawed)).append(" §9").append(sep(gem.fine));
                price = gem.priceSum();
            }
            totalPrice += price;
            if (showPrice && price != 0) row.append(" §7(§6").append(format(price)).append("§7)");
            out.add(row.toString());
        }
        return totalPrice;
    }
}
