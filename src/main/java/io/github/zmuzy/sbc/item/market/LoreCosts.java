// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item.market;

import java.util.ArrayList;
import java.util.List;

// skyhanni LoreCostUtils for other packages: what a shop item costs, from what NpcTrade reads off its lore
public final class LoreCosts {
    public record Entry(String id, long amount) {}

    public static final String COPPER = NpcTrade.Currency.COPPER.id, GOLD_MEDAL = NpcTrade.Currency.GOLD_MEDAL.id, SILVER_MEDAL = NpcTrade.Currency.SILVER_MEDAL.id, BRONZE_MEDAL = NpcTrade.Currency.BRONZE_MEDAL.id;

    private LoreCosts() {}

    public static List<Entry> read(List<String> lore) {
        List<Entry> out = new ArrayList<>();
        for (NpcTrade.Cost c : NpcTrade.readLoreCosts(lore)) out.add(new Entry(c.id(), c.amount()));
        return out;
    }

    public static boolean hasTradeLine(List<String> lore) {
        return NpcTrade.hasTradeLine(lore);
    }
}
