// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.core;

import java.util.ArrayList;
import java.util.List;

// a pet as an item card id like neu + george do it, "BEE;4" = legendary bee
// so the pet + accessory helpers can open a pet like the item list opens an item
public final class PetCard {
    // neu tier index order (same as GeorgePrices.tierIndex)
    static final String[] TIERS = {"COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "DIVINE", "SPECIAL", "VERY_SPECIAL"};
    // rarity colors in that same order
    private static final String CODES = "fa956dbcc";

    private PetCard() {}

    public static String id(String type, String tier) {
        for (int i = 0; i < TIERS.length; i++) if (TIERS[i].equals(tier)) return type + ";" + i;
        return null;
    }

    // type + tier of a pet id, null if its not one of the pets the repo knows
    public static String[] parse(String id) {
        if (id == null) return null;
        int semi = id.indexOf(';');
        if (semi <= 0 || semi != id.length() - 2 || !Character.isDigit(id.charAt(semi + 1))) return null;
        int index = id.charAt(semi + 1) - '0';
        String type = id.substring(0, semi);
        if (index >= TIERS.length || !ItemRepo.petTiers().containsKey(type)) return null;
        return new String[]{type, TIERS[index]};
    }

    static String name(String[] pet) {
        return GameState.pretty(pet[0]);
    }

    static String coloredName(String[] pet) {
        int i = java.util.Arrays.asList(TIERS).indexOf(pet[1]);
        char code = i >= 0 && i < CODES.length() ? CODES.charAt(i) : 'f';
        return "§" + code + name(pet);
    }

    // the tier and what it does at max level, from the pet data + neus numbers
    static List<String> lore(String[] pet) {
        List<String> out = new ArrayList<>();
        out.add("§8" + GameState.pretty(pet[1]) + " pet");
        out.addAll(PetLore.lines(pet[0], pet[1], Pets.maxLevel(pet[0])));
        return out;
    }

    // ah prices of a fresh one and a maxed one, both only when theyre listed
    static List<String> prices(String[] pet) {
        List<String> out = new ArrayList<>();
        double low = AuctionPrices.lowestBin(AuctionPrices.petKey(pet[0], pet[1], false));
        double max = AuctionPrices.lowestBin(AuctionPrices.petKey(pet[0], pet[1], true));
        if (low > 0) out.add("§7Lowest BIN (lvl 1): §6" + Prices.coins(low));
        if (max > 0) out.add("§7Lowest BIN (max level): §6" + Prices.coins(max));
        return out;
    }
}
