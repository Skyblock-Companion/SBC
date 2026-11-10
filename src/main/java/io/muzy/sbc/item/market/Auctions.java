// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item.market;

import io.muzy.sbc.core.Patterns;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni AuctionsHighlighter (manage auctions: sold / expired / underbid),
// AuctionHouseCopyUnderbidPrice (copy price - 1 of the hovered auction, and your own item in create bin
// auction)
// and AuctionOutbidWarning (title on the outbid line)
public final class Auctions {
    static final Patterns.P BUY_IT_NOW = Patterns.p("SH.auctions.highlight.buyitnow");
    static final Patterns.P AUCTION = Patterns.p("SH.auctions.highlight.auction");
    static final Patterns.P UNDERBID_PRICE = Patterns.p("SH.auctions.underbid.price");
    static final Patterns.P ALLOWED_INVENTORIES = Patterns.p("SH.auctions.underbid.allowedinventories");
    static final Patterns.P OUTBID = Patterns.p("SH.auction.outbid");

    public static int soldColor = 0x8055FF55, expiredColor = 0x80FF5555, underbidColor = 0x80FFAA00;
    public static boolean highlightAuctionsUnderbid = true;

    private Auctions() {}

    static long formatLong(String s) {
        return Long.parseLong(s.replace(",", ""));
    }

    // skyhanni AuctionsHighlighter.onBackgroundDrawn for one manage auctions slot. estimatedValue null when none
    // / doesnt apply. returns the highlight color or null
    public static Integer highlightManageAuctions(List<String> lore, Double estimatedValue) {
        for (String l : lore) {
            if (l.equals("§7Status: §aSold!")) return soldColor;
            if (l.equals("§7Status: §cExpired!")) return expiredColor;
        }
        if (highlightAuctionsUnderbid && estimatedValue != null) for (String l : lore) {
            Matcher m = BUY_IT_NOW.matcher(l);
            if (m.matches()) {
                long coins = formatLong(m.group("coins"));
                if (coins > estimatedValue) return underbidColor;
            }
        }
        return null;
    }

    // skyhanni AuctionHouseCopyUnderbidPrice: hovered auctions price in an allowed menu, or -1
    public static long underbidPriceOf(String inventoryName, List<String> lore) {
        if (!ALLOWED_INVENTORIES.matcher(inventoryName).matches()) return -1;
        for (String l : lore) {
            Matcher m = UNDERBID_PRICE.matcher(l);
            if (m.matches()) return formatLong(m.group("coins")) - 1;
        }
        return -1;
    }

    // AuctionHousePriceComparison

    private static Map<Integer, Long> slotPriceMap = Map.of();
    private static long bestPrice, worstPrice;

    // from skyhanni AuctionHousePriceComparison.inInventory
    public static boolean comparisonMenu(String inventoryName) {
        return inventoryName.startsWith("Auctions") || inventoryName.startsWith("Cosmetics Browser");
    }

    // asking price on an auction lore line, or -1
    static long askingPrice(String line) {
        Matcher m = BUY_IT_NOW.matcher(line);
        if (m.matches()) return formatLong(m.group("coins"));
        m = AUCTION.matcher(line);
        return m.matches() ? formatLong(m.group("coins")) : -1;
    }

    static void clearComparison() {
        slotPriceMap = Map.of();
        bestPrice = 0;
        worstPrice = 0;
    }

    // skyhanni onInventoryOpen, one slot at a time: estimated minus asked. map keeps best + worst for the color scale
    // only items with stuff on them count (getTotalPrice with ignoreBasePrice), so the caller passes null for a plain one
    static void compare(Map<Integer, Long> into, int slot, List<String> lore, Double estimated) {
        if (estimated == null) return;
        for (String l : lore) {
            long asked = askingPrice(l);
            if (asked < 0) continue;
            long diff = estimated.longValue() - asked;
            into.put(slot, diff);
            if (diff >= 0) {
                if (diff > bestPrice) bestPrice = diff;
            } else if (diff < worstPrice) worstPrice = diff;
        }
    }

    static void finishComparison(Map<Integer, Long> map) {
        slotPriceMap = map;
    }

    // skyhanni onBackgroundDrawn: good to very good, bad to very bad, by how far along the pages best and worst it is
    public static Integer comparisonColor(int slot, int good, int veryGood, int bad, int veryBad) {
        Long diff = slotPriceMap.get(slot);
        if (diff == null) return null;
        if (diff == 0L) return good;
        boolean isGood = diff >= 0;
        double percentage = isGood ? diff.doubleValue() / bestPrice : -diff.doubleValue() / -worstPrice;
        return isGood ? between(good, veryGood, percentage) : between(bad, veryBad, percentage);
    }

    // skyhanni getColorInBetween, keeps the alpha of the first
    static int between(int a, int b, double t) {
        int r = channel(a >> 16 & 255, b >> 16 & 255, t), g = channel(a >> 8 & 255, b >> 8 & 255, t), bl = channel(a & 255, b & 255, t);
        return a & 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static int channel(int a, int b, double t) {
        return Math.max(0, Math.min(255, (int) (a + t * (b - a))));
    }

    // from skyhanni onToolTip
    public static void comparisonTooltip(int slot, List<net.minecraft.network.chat.Component> lines) {
        Long diff = slotPriceMap.get(slot);
        if (diff == null) return;
        lines.add(net.minecraft.network.chat.Component.empty());
        String coins = String.format(java.util.Locale.ROOT, "%,d", Math.abs(diff));
        if (diff >= 0) {
            lines.add(net.minecraft.network.chat.Component.literal("§aThis item is §6" + coins + " coins §acheaper"));
            lines.add(net.minecraft.network.chat.Component.literal("§athan the estimated item value!"));
        } else {
            lines.add(net.minecraft.network.chat.Component.literal("§cThis item is §6" + coins + " coins §cmore"));
            lines.add(net.minecraft.network.chat.Component.literal("§cexpensive than the estimated item value!"));
        }
    }

    // AuctionHouseOpenPriceWebsite

    // skyhanni AuctionOutbidWarning.onChat, formatted line
    public static boolean isOutbid(String formatted) {
        return OUTBID.matcher(formatted).matches();
    }
}
