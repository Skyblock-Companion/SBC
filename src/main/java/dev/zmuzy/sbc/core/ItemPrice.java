// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.core;

import java.util.Map;

// the one place money pages ask what an item costs + what it brings, so every page counts the same. like
// skyhanni ItemPriceUtils.getPriceOrNull (fixed values, bazaar, lbin, npc)
public final class ItemPrice {
    // where a price comes from, in words for the page
    public enum Source {
        BAZAAR_ORDER("Bazaar buy order"), BAZAAR_OFFER("Bazaar sell offer"), AUCTION("lowest BIN"), NPC("NPC"), FIXED("fixed value"), NONE("no price");

        public final String words;

        Source(String words) {
            this.words = words;
        }
    }

    // price + where its from + can a ranking lean on it
    public record Quote(double coins, Source source, boolean certain) {
        public static final Quote NONE = new Quote(0, Source.NONE, false);

        public boolean known() {
            return source != Source.NONE && coins > 0;
        }

        // one line for the page: "1.2k (Bazaar sell offer)" + "uncertain" when it is
        public String basis() {
            if (!known()) return "no price";
            return Prices.coins(coins) + " (" + source.words + (certain ? "" : ", uncertain") + ")";
        }
    }

    public static final int MIN_SALES = 2;
    static final long WATCH_BEFORE_SALES_MS = 60 * 60_000L;
    public static final double DEFAULT_TAX = 1.25;

    // from skyhanni getPriceOrNull: items the markets price wrong or not at all
    private static final Map<String, Double> FIXED = Map.of(
            "GEMSTONE_COLLECTION", 0.0, "JASPER_CRYSTAL", 0.0, "RUBY_CRYSTAL", 0.0, "WISP_POTION", 20_000.0,
            "ENCHANTED_HAY_BLOCK", 7_776.0, "TIGHTLY_TIED_HAY_BALE", 1_119_744.0, "SKYBLOCK_COIN", 1.0);

    private ItemPrice() {}

    // your bazaar tax %: what the bazaar said last (read by the bazaar helpers, like skyhanni), else the usual 1.25%
    public static double tax() {
        double t = Store.I == null ? 0 : Store.I.bazaarTaxRate;
        return t > 0 && t < 10 ? t : DEFAULT_TAX;
    }

    // cost to get one: bazaar buy order, else lbin
    public static Quote buy(String id) {
        if (id == null || id.isEmpty()) return Quote.NONE;
        Double fixed = FIXED.get(id);
        if (fixed != null) return new Quote(fixed, Source.FIXED, true);
        Prices.Product p = Prices.get(id);
        // apis sellPrice = best buy order (what insta sellers get, and what a buy order pays to be first)
        if (p != null && p.sell() > 0) return new Quote(p.sell(), Source.BAZAAR_ORDER, true);
        double bin = AuctionPrices.lowestBin(id);
        if (bin > 0) return new Quote(bin, Source.AUCTION, auctionCertain(id));
        return Quote.NONE;
    }

    // what one brings: bazaar sell offer minus tax, or npc if that pays more. off bazaar = lbin
    public static Quote sell(String id) {
        if (id == null || id.isEmpty()) return Quote.NONE;
        Double fixed = FIXED.get(id);
        if (fixed != null) return new Quote(fixed, Source.FIXED, true);
        double npc = ItemRepo.npcPrice(id);
        Prices.Product p = Prices.get(id);
        // apis buyPrice = best sell offer (what insta buyers pay)
        if (p != null && p.buy() > 0) {
            double offer = p.buy() * (1 - tax() / 100);
            return npc > offer ? new Quote(npc, Source.NPC, true) : new Quote(offer, Source.BAZAAR_OFFER, true);
        }
        double bin = AuctionPrices.lowestBin(id);
        if (bin > 0 && bin > npc) return new Quote(bin, Source.AUCTION, auctionCertain(id));
        if (npc > 0) return new Quote(npc, Source.NPC, true);
        return Quote.NONE;
    }

    // bulk sell price, like a minions output. bazaar sell offer minus tax, or the npc if it pays more
    // never the ah tbh, the cheapest bin says nothing about dumping thousands a day
    public static Quote sellBulk(String id) {
        if (id == null || id.isEmpty()) return Quote.NONE;
        Double fixed = FIXED.get(id);
        if (fixed != null) return new Quote(fixed, Source.FIXED, true);
        double npc = ItemRepo.npcPrice(id);
        Prices.Product p = Prices.get(id);
        if (p != null && p.buy() > 0) {
            double offer = p.buy() * (1 - tax() / 100);
            return npc > offer ? new Quote(npc, Source.NPC, true) : new Quote(offer, Source.BAZAAR_OFFER, true);
        }
        return npc > 0 ? new Quote(npc, Source.NPC, true) : Quote.NONE;
    }

    // can we trust the lbin: it sold lately, or before an hour of watching its listed a lot
    public static boolean auctionCertain(String id) {
        if (AuctionPrices.salesLastHour(id) >= MIN_SALES) return true;
        return AuctionPrices.watchedFor() < WATCH_BEFORE_SALES_MS && AuctionPrices.manyListed(id);
    }
}
