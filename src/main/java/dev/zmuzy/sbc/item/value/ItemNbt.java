// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item.value;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// from skyhanni SkyBlockItemModifierUtils on the ExtraAttributes compound (CUSTOM_DATA): the modifiers an
// estimated value is made of
// every getter returns null / false / empty when the tag is missing or 0, like skyhannis takeUnless { it == 0 }
public final class ItemNbt {
    private ItemNbt() {}

    public static Integer attrInt(CompoundTag ea, String label) {
        int v = ea.getIntOr(label, 0);
        return v == 0 ? null : v;
    }

    public static String attrString(CompoundTag ea, String label) {
        String s = ea.getStringOr(label, "");
        return s.isBlank() ? null : s;
    }

    public static boolean attrBoolean(CompoundTag ea, String label) {
        return ea.getBooleanOr(label, false) || ea.getIntOr(label, 0) == 1;
    }

    public static Integer getHotPotatoCount(CompoundTag ea) { return attrInt(ea, "hot_potato_count"); }
    public static Integer getWetBookCount(CompoundTag ea) { return attrInt(ea, "wet_book_count"); }
    public static Integer getFarmingForDummiesCount(CompoundTag ea) { return attrInt(ea, "farming_for_dummies_count"); }
    public static Integer getOverclockerCount(CompoundTag ea) { return attrInt(ea, "levelable_overclocks"); }
    public static Integer getPolarvoidBookCount(CompoundTag ea) { return attrInt(ea, "polarvoid"); }
    public static Integer getBookwormBookCount(CompoundTag ea) { return attrInt(ea, "bookworm_books"); }
    public static Integer getTransmissionTunerCount(CompoundTag ea) { return attrInt(ea, "tuned_transmission"); }
    public static Integer getManaDisintegrators(CompoundTag ea) { return attrInt(ea, "mana_disintegrator_count"); }
    public static Integer getStarCount(CompoundTag ea) { return attrInt(ea, "upgrade_level"); }
    public static Integer getAppliedPocketSackInASack(CompoundTag ea) { return attrInt(ea, "sack_pss"); }
    public static boolean getMithrilInfusion(CompoundTag ea) { return ea.getByteOr("mithril_infusion", (byte) 0) == 1; }
    public static boolean getFreeWill(CompoundTag ea) { return ea.getByteOr("free_will", (byte) 0) == 1; }
    public static String getReforgeModifier(CompoundTag ea) { return attrString(ea, "modifier"); }
    public static boolean isRecombobulated(CompoundTag ea) { return attrInt(ea, "rarity_upgrades") != null; }
    public static boolean hasJalapenoBook(CompoundTag ea) { return attrInt(ea, "jalapeno_count") != null; }
    public static boolean hasEtherwarp(CompoundTag ea) { return attrBoolean(ea, "ethermerge"); }
    public static boolean hasWoodSingularity(CompoundTag ea) { return attrInt(ea, "wood_singularity_count") != null; }
    public static boolean hasDivanPowderCoating(CompoundTag ea) { return attrBoolean(ea, "divan_powder_coating"); }
    public static boolean hasArtOfWar(CompoundTag ea) { return attrInt(ea, "art_of_war_count") != null; }
    public static boolean hasBookOfStats(CompoundTag ea) { return ea.contains("stats_book"); }
    public static boolean hasArtOfPeace(CompoundTag ea) { return attrBoolean(ea, "artOfPeaceApplied"); }
    public static String getPowerScroll(CompoundTag ea) { return attrString(ea, "power_ability_scroll"); }
    public static String getEnrichment(CompoundTag ea) { return attrString(ea, "talisman_enrichment"); }
    public static String getHelmetSkin(CompoundTag ea) { return attrString(ea, "skin"); }
    public static String getArmorDye(CompoundTag ea) { return attrString(ea, "dye_item"); }

    // skyhanni getDungeonStarCount: DUNGEON items use upgrade_level, else dungeon_item_level
    public static Integer getDungeonStarCount(CompoundTag ea, boolean isDungeonItem) {
        if (!isDungeonItem) return null;
        Integer s = getStarCount(ea);
        return s != null ? s : attrInt(ea, "dungeon_item_level");
    }

    // skyhanni getSilexCount: efficiency above 5 minus the items own base (stonk 1, promising spade 5)
    public static Integer getSilexCount(CompoundTag ea, String internalName) {
        Map<String, Integer> ench = getHypixelEnchantments(ea);
        Integer eff = ench == null ? null : ench.get("efficiency");
        if (eff == null) return null;
        int base = switch (internalName) {
            case "STONK_PICKAXE" -> 1;
            case "PROMISING_SPADE" -> 5;
            default -> 0;
        };
        int v = eff - 5 - base;
        return v > 0 ? v : null;
    }

    public static Map<String, Integer> getHypixelEnchantments(CompoundTag ea) {
        if (!ea.contains("enchantments")) return null;
        CompoundTag e = ea.getCompoundOrEmpty("enchantments");
        Map<String, Integer> out = new LinkedHashMap<>();
        for (String k : e.keySet()) out.put(k, e.getIntOr(k, 0));
        return out;
    }

    // skyhanni getRune: "<NAME>_RUNE;<tier>" of the first rune, or null
    public static String getRune(CompoundTag ea) {
        if (!ea.contains("runes")) return null;
        CompoundTag runes = ea.getCompoundOrEmpty("runes");
        for (String k : runes.keySet()) return k.toUpperCase(Locale.ROOT) + "_RUNE;" + runes.getIntOr(k, 0);
        return null;
    }

    // skyhanni getDrillUpgrades: the 3 drill part attributes as uppercase ids
    public static List<String> getDrillUpgrades(CompoundTag ea) {
        List<String> out = new ArrayList<>();
        for (String k : new String[]{"drill_part_upgrade_module", "drill_part_engine", "drill_part_fuel_tank"}) {
            String v = ea.getStringOr(k, "");
            if (!v.isEmpty()) out.add(v.toUpperCase(Locale.ROOT));
        }
        return out;
    }

    // skyhanni getRodParts (FishingApi.RodPart): line, hook, sinker
    public static List<String> getRodParts(CompoundTag ea) {
        List<String> out = new ArrayList<>();
        for (String k : new String[]{"line", "hook", "sinker"}) {
            if (!ea.contains(k)) continue;
            String v = ea.getCompoundOrEmpty(k).getStringOr("part", "");
            if (!v.isEmpty()) out.add(v.toUpperCase(Locale.ROOT));
        }
        return out;
    }

    // skyhanni getAbilityScrolls: ULTIMATE_WITHER_SCROLL expands to its 3 scrolls
    public static List<String> getAbilityScrolls(CompoundTag ea) {
        if (!ea.contains("ability_scroll")) return null;
        List<String> out = new ArrayList<>();
        ListTag list = ea.getListOrEmpty("ability_scroll");
        for (Tag t : list) {
            String s = t.asString().orElse("");
            if (s.isEmpty()) continue;
            if (s.equals("ULTIMATE_WITHER_SCROLL")) {
                for (String x : new String[]{"IMPLOSION_SCROLL", "WITHER_SHIELD_SCROLL", "SHADOW_WARP_SCROLL"}) if (!out.contains(x)) out.add(x);
                continue;
            }
            if (!out.contains(s)) out.add(s);
        }
        return out;
    }

    // skyhanni readBoosters: "<name>_BOOSTER" each
    public static List<String> readBoosters(CompoundTag ea) {
        List<String> out = new ArrayList<>();
        for (Tag t : ea.getListOrEmpty("boosters")) {
            String s = t.asString().orElse("");
            if (!s.isBlank()) out.add(s + "_BOOSTER");
        }
        return out;
    }

    public static final List<String> GEM_TYPES = List.of("JADE", "AMBER", "TOPAZ", "SAPPHIRE", "AMETHYST", "JASPER", "RUBY", "OPAL", "ONYX", "AQUAMARINE", "CITRINE", "PERIDOT");
    public static final List<String> GEM_QUALITIES = List.of("ROUGH", "FLAWED", "FINE", "FLAWLESS", "PERFECT");

    // skyhanni getGemstones: "<QUALITY>_<TYPE>_GEM" per filled slot
    public static List<String> getGemstones(CompoundTag ea) {
        if (!ea.contains("gems")) return null;
        CompoundTag gems = ea.getCompoundOrEmpty("gems");
        List<String> out = new ArrayList<>();
        for (String key : gems.keySet()) {
            if (key.endsWith("_gem") || key.equals("unlocked_slots")) continue;
            Tag t = gems.get(key);
            String value = t.asString().orElse("");
            if (value.isEmpty()) value = t.asCompound().map(c -> c.getStringOr("quality", "")).orElse("");
            if (value.isEmpty()) continue;
            String rawType = key.split("_")[0];
            String quality = value.toUpperCase(Locale.ROOT);
            if (!GEM_QUALITIES.contains(quality)) continue;
            String type = GEM_TYPES.contains(rawType) ? rawType : gems.getStringOr(key + "_gem", "");
            if (!GEM_TYPES.contains(type)) continue;
            out.add(quality + "_" + type + "_GEM");
        }
        return out;
    }

    // gems.unlocked_slots as strings, null if missing
    public static List<String> readUnlockedSlots(CompoundTag ea) {
        if (!ea.contains("gems")) return null;
        CompoundTag gems = ea.getCompoundOrEmpty("gems");
        if (!gems.contains("unlocked_slots")) return null;
        List<String> out = new ArrayList<>();
        for (Tag t : gems.getListOrEmpty("unlocked_slots")) out.add(t.asString().orElse(""));
        return out;
    }
}
