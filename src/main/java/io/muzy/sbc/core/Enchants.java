// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.core;

import io.muzy.sbc.item.enchants.EnchantsData;

// every enchant with its good + max level and if its an ultimate, from skyhannis Enchants.json (through
// EnchantsData). small api for the tooltip code
public final class Enchants {
    public record Info(String id, String name, int goodLevel, int maxLevel, boolean ultimate, boolean stacking) {}

    private Enchants() {}

    // by display name ("Sharpness"), null = unknown
    public static Info byName(String name) {
        if (name == null) return null;
        EnchantsData.Enchant e = EnchantsData.byLoreName(name.trim());
        if (e == null) return null;
        return new Info(e.nbtName, e.loreName, e.goodLevel, e.maxLevel, e.isUltimate(), e.isStacking());
    }

    // by nbt name ("ultimate_wise"), null = unknown
    public static Info byNbt(String nbt) {
        EnchantsData.Enchant e = nbt == null ? null : EnchantsData.byNbtName(nbt);
        return e == null ? null : new Info(e.nbtName, e.loreName, e.goodLevel, e.maxLevel, e.isUltimate(), e.isStacking());
    }

    public static int roman(String s) {
        return io.muzy.sbc.item.enchants.EnchantParser.level(s);
    }

    public static boolean ready() {
        return EnchantsData.hasEnchantData();
    }
}
