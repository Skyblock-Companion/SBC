// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item.market;

import dev.zmuzy.sbc.core.AuctionPrices;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.Prices;

// from skyhanni ItemPriceSource + getPriceOrNull: price by source. stuff not on the bazaar goes by lowest bin
// whichever bz side was picked
public final class PriceSource {
    public static final String INSTANT_BUY = "BAZAAR_INSTANT_BUY", INSTANT_SELL = "BAZAAR_INSTANT_SELL", NPC = "NPC";
    public static final String[] CHOICES = {INSTANT_BUY, INSTANT_SELL, NPC};

    private PriceSource() {}

    public static double of(String source, String id) {
        if (NPC.equals(source)) return ItemRepo.npcPrice(id);
        Prices.Product p = Prices.get(id);
        if (p != null) return INSTANT_BUY.equals(source) ? p.buy() : p.sell();
        return AuctionPrices.lowestBin(id);
    }
}
