// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.item.sacks;

import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.LegacyItems;
import dev.zmuzy.sbc.item.enchants.EnchantParser;
import dev.zmuzy.sbc.rift.RichCard;
import dev.zmuzy.sbc.rift.RichCard.Content;
import dev.zmuzy.sbc.rift.RichCard.Line;
import dev.zmuzy.sbc.ui.hud.RowText;
import dev.zmuzy.sbc.ui.theme.Modern;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.IntFunction;

// sack display as tables: every item with a fill meter and lined up columns however long the list
// gems by quality and runes by level under their column names, sack worth in the title band
final class SackCard {
    private static final int FILL = 0xFFD8B26E;

    private SackCard() {}

    // one sacks numbers, from the open menu or the sample
    record Data(List<SackApi.SackOtherItem> items, List<Map.Entry<String, SackApi.SackGemstone>> gems, List<SackApi.SackRune> runes, String filter, boolean trophy, int limit,
                Function<String, String> names, IntFunction<ItemStack> icons, Function<SackApi.SackOtherItem, ItemStack> itemIcons) {}

    private static String sep(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static String amount(SackApi.SackOtherItem item) {
        return switch (SackDisplay.numberFormat) {
            case DEFAULT -> item.colorCode + sep(item.stored) + "§7/§b" + EnchantParser.shortFormat(item.total);
            case FORMATTED -> item.colorCode + EnchantParser.shortFormat(item.stored) + "§7/§b" + EnchantParser.shortFormat(item.total);
            case UNFORMATTED -> item.colorCode + sep(item.stored) + "§7/§b" + sep(item.total);
        };
    }

    private static String price(long p) {
        return SackDisplay.showPrice && p != 0 ? "§6" + SackDisplay.format(p) : "";
    }

    // a full sack loses whatever comes next, thats what the red slots in the menu warn about
    private static int fillColor(double fill) {
        return fill >= 1 ? Modern.BAD : fill >= 0.9 ? Modern.WARN : FILL;
    }

    static Content content(Data d) {
        List<Line> out = new ArrayList<>();
        long worth = 0, stored = 0, capacity = 0, magma = 0;
        for (SackApi.SackOtherItem i : d.items()) {
            worth += i.price;
            stored += i.stored;
            capacity += i.total;
            magma += i.magmaFish;
        }
        if (capacity > 0) out.add(Line.row("Sack filled", "§e" + sep(stored) + "§7/§b" + sep(capacity)).meter((double) stored / capacity, fillColor((double) stored / capacity)));
        if (!d.items().isEmpty()) {
            int showing = Math.min(d.limit(), d.items().size());
            out.add(Line.section("Items", "§f" + showing + " §7of §f" + d.items().size()));
            if (d.trophy()) out.add(Line.table("", "", "", "§dMagma", "").key("Heading"));
            for (int n = 0; n < showing; n++) {
                SackApi.SackOtherItem i = d.items().get(n);
                double fill = i.total > 0 ? (double) i.stored / i.total : 0;
                String name = i.stored == 0 ? "§7" + GameState.strip(d.names().apply(i.internalName)) : d.names().apply(i.internalName).replace("§k", "");
                String pct = "§7" + RowText.percent(fill);
                Line row = d.trophy() ? Line.table(name, amount(i), pct, i.magmaFish > 0 ? "§d" + sep(i.magmaFish) : "", price(i.price)) : Line.table(name, amount(i), pct, price(i.price));
                row.brief(name.replace("Enchanted ", "Ench ")).icon(d.itemIcons().apply(i)).meter(fill, fillColor(fill));
                out.add(row);
            }
            if (d.trophy()) out.add(Line.row("Total Magmafish", "§d" + sep(magma)).raw());
        }
        if (!d.gems().isEmpty()) {
            String filter = d.filter();
            out.add(Line.section("Gemstones", filter == null ? "" : "§7" + filter.charAt(0) + filter.substring(1).toLowerCase(Locale.ROOT) + " only"));
            String value = SackDisplay.showPrice ? "§6Value" : "";
            if (filter == null) out.add(Line.table("", "§fRough", "§aFlawed", "§9Fine", value).key("Heading"));
            for (Map.Entry<String, SackApi.SackGemstone> e : d.gems()) {
                SackApi.SackGemstone g = e.getValue();
                long p = filter == null ? g.priceSum() : "ROUGH".equals(filter) ? g.roughPrice : "FLAWED".equals(filter) ? g.flawedPrice : g.finePrice;
                worth += p;
                Line row = switch (filter == null ? "" : filter) {
                    case "ROUGH" -> Line.table(e.getKey(), "§f" + sep(g.rough), price(p));
                    case "FLAWED" -> Line.table(e.getKey(), "§a" + sep(g.flawed), price(p));
                    case "FINE" -> Line.table(e.getKey(), "§9" + sep(g.fine), price(p));
                    default -> Line.table(e.getKey(), "§f" + sep(g.rough), "§a" + sep(g.flawed), "§9" + sep(g.fine), price(p));
                };
                out.add(row.brief(e.getKey().replace(" Gemstones", "")).icon(d.icons().apply(g.slot)));
            }
        }
        if (!d.runes().isEmpty()) {
            out.add(Line.section("Runes"));
            out.add(Line.table("", "§7I", "§7II", "§7III").key("Heading"));
            for (SackApi.SackRune r : d.runes()) out.add(Line.table(r.name, "§e" + sep(r.lvl1), "§e" + sep(r.lvl2), "§e" + sep(r.lvl3)).icon(d.icons().apply(r.slot)));
        }
        return new Content(SackDisplay.showPrice ? "§6" + SackDisplay.format(worth) : "", out);
    }

    private static ItemStack slotIcon(int slot) {
        var player = Minecraft.getInstance().player;
        if (player == null || slot < 0 || slot >= player.containerMenu.slots.size()) return null;
        ItemStack st = player.containerMenu.slots.get(slot).getItem();
        return st.isEmpty() ? null : st;
    }

    // the open sack, icons are the menus own
    static Content live() {
        List<Map.Entry<String, SackApi.SackGemstone>> gems = new ArrayList<>();
        for (SackApi.SackGemstone g : SackDisplay.sortedGems())
            for (Map.Entry<String, SackApi.SackGemstone> e : SackApi.gemstoneItem.entrySet()) if (e.getValue() == g) gems.add(e);
        return content(new Data(SackDisplay.sortedItems(), gems, SackDisplay.sortedRunes(), SackApi.gemstoneStackFilter, SackApi.isTrophySack, SackDisplay.itemToShow,
                ItemRepo::coloredName, SackCard::slotIcon, i -> slotIcon(i.slot)));
    }

    private static SackApi.SackOtherItem item(String name, String code, int stored, int total, long price) {
        SackApi.SackOtherItem i = new SackApi.SackOtherItem();
        i.internalName = name;
        i.colorCode = code;
        i.stored = stored;
        i.total = total;
        i.price = price;
        return i;
    }

    private static SackApi.SackGemstone gem(int rough, int flawed, int fine, long price) {
        SackApi.SackGemstone g = new SackApi.SackGemstone();
        g.rough = rough;
        g.flawed = flawed;
        g.fine = fine;
        g.finePrice = price;
        return g;
    }

    private static SackApi.SackRune rune(String name, int a, int b, int c) {
        SackApi.SackRune r = new SackApi.SackRune();
        r.name = name;
        r.lvl1 = a;
        r.lvl2 = b;
        r.lvl3 = c;
        return r;
    }

    // sacks that look different: trophy fish in magmafish, a filter on the gemstones, runes alone
    static List<Content> states() {
        SackApi.SackOtherItem blobfish = item("§fBlobfish Bronze", "§e", 1_204, 2_880, 2_100_000L), lava = item("§9Lava Horse Silver", "§e", 12, 2_880, 96_000L);
        blobfish.magmaFish = 1_204;
        lava.magmaFish = 48;
        ItemStack fish = LegacyItems.stack("minecraft:cod");
        Data trophy = new Data(List.of(blobfish, lava), List.of(), List.of(), null, true, 15, n -> n, s -> null, i -> fish);
        Data filtered = new Data(List.of(), List.of(Map.entry("§5Amethyst Gemstones", gem(0, 0, 1_204, 90_000_000L))), List.of(), "FINE", false, 15, n -> n, s -> null, i -> null);
        Data runes = new Data(List.of(), List.of(), List.of(rune("§dEnchant Rune", 41, 12, 3)), null, false, 15, n -> n, s -> null, i -> null);
        return List.of(content(trophy), content(filtered), content(runes));
    }

    // everything the card can say at once. a real sack has one of the 3 blocks
    static Content sample() {
        List<SackApi.SackOtherItem> items = List.of(
                item("§9Enchanted Mithril Block", "§e", 60_480, 60_480, 212_000_000L),
                item("§aEnchanted Hard Stone", "§e", 58_212, 60_480, 14_300_000L),
                item("§aEnchanted Cobblestone", "§e", 28_183, 60_480, 412_000L),
                item("§fTitanium", "§e", 1_204, 60_480, 8_100_000L),
                item("§fCoal", "§8", 0, 60_480, 0L),
                item("§fMithril", "§e", 512, 60_480, 90_000L));
        Map<String, ItemStack> icons = Map.of("§9Enchanted Mithril Block", LegacyItems.stack("minecraft:prismarine"), "§aEnchanted Hard Stone", LegacyItems.stack("minecraft:stone"), "§aEnchanted Cobblestone", LegacyItems.stack("minecraft:cobblestone"),
                "§fTitanium", LegacyItems.stack("minecraft:iron_ingot"), "§fCoal", LegacyItems.stack("minecraft:coal"), "§fMithril", LegacyItems.stack("minecraft:prismarine_crystals"));
        List<Map.Entry<String, SackApi.SackGemstone>> gems = List.of(Map.entry("§aJade Gemstones", gem(78_999, 604, 35, 3_400_000L)), Map.entry("§cRuby Gemstones", gem(1_204, 88, 2, 412_000L)));
        List<SackApi.SackRune> runes = List.of(rune("§dEnchant Rune", 41, 12, 3), rune("§cBlood Rune", 7, 0, 1));
        return content(new Data(items, gems, runes, null, false, 5, n -> n, s -> s == -1 ? LegacyItems.stack("minecraft:emerald") : null, i -> icons.get(i.internalName)));
    }
}
