// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHelper; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.Locale;
import java.util.Set;

// one items value like networth calcs do it: cheapest clean copy (bazaar or lbin) + whats applied (enchants,
// recomb, potato books, gems, scrolls, art of war, drill parts...)
public final class Worth {
    private static final Set<String> GEM_TYPES = Set.of("RUBY", "AMBER", "SAPPHIRE", "JADE", "AMETHYST", "TOPAZ", "JASPER", "OPAL",
            "ONYX", "AQUAMARINE", "CITRINE", "PERIDOT");

    private Worth() {}

    public static double item(CompoundTag ea, int count) {
        String id = ea.getStringOr("id", "");
        if (id.isEmpty()) return 0;
        if (id.equals("PET")) {
            try {
                JsonObject info = JsonParser.parseString(ea.getStringOr("petInfo", "{}")).getAsJsonObject();
                return AuctionPrices.pet(str(info, "type"), str(info, "tier"), info.has("exp") ? info.get("exp").getAsDouble() : 0,
                        str(info, "heldItem"), str(info, "skin"));
            } catch (Exception e) {
                return 0;
            }
        }
        double base = AuctionPrices.valueOf(ea) * Math.max(1, count);
        // kuudra armor + other attribute items priced by their 2 rolls, shiny items by their shiny listing
        CompoundTag attrs = ea.getCompoundOrEmpty("attributes");
        if (attrs.size() == 2) {
            java.util.List<String> names = new java.util.ArrayList<>(attrs.keySet());
            java.util.Collections.sort(names);
            double roll = SkyHelperPrices.get(id + "_roll_" + names.get(0) + "_roll_" + names.get(1));
            if (roll > base) base = roll;
        }
        if (ea.getIntOr("is_shiny", 0) > 0) {
            double shiny = SkyHelperPrices.get(id + "_shiny");
            if (shiny > 0) base = shiny;
        }
        boolean book = id.equals("ENCHANTED_BOOK");
        double extra = 0;

        CompoundTag ench = ea.getCompoundOrEmpty("enchantments");
        for (String e : ench.keySet()) {
            int lvl = ench.getIntOr(e, 0);
            double p = AuctionPrices.value("ENCHANTMENT_" + e.toUpperCase(Locale.ROOT) + "_" + lvl);
            extra += p * (book ? 1 : 0.85);
        }
        if (ea.getIntOr("rarity_upgrades", 0) > 0 && !book) extra += AuctionPrices.value("RECOMBOBULATOR_3000") * 0.8;
        int hpb = ea.getIntOr("hot_potato_count", 0);
        if (hpb > 0) {
            extra += Math.min(10, hpb) * AuctionPrices.value("HOT_POTATO_BOOK");
            if (hpb > 10) extra += (hpb - 10) * AuctionPrices.value("FUMING_POTATO_BOOK") * 0.6;
        }
        extra += ea.getIntOr("art_of_war_count", 0) * AuctionPrices.value("THE_ART_OF_WAR") * 0.6;
        extra += ea.getIntOr("artOfPeaceApplied", 0) * AuctionPrices.value("THE_ART_OF_PEACE") * 0.8;
        extra += ea.getIntOr("wood_singularity_count", 0) * AuctionPrices.value("WOOD_SINGULARITY") * 0.5;
        extra += ea.getIntOr("jalapeno_count", 0) * AuctionPrices.value("JALAPENO_BOOK") * 0.8;
        extra += ea.getIntOr("tuned_transmission", 0) * AuctionPrices.value("TRANSMISSION_TUNER") * 0.7;
        extra += ea.getIntOr("mana_disintegrator_count", 0) * AuctionPrices.value("MANA_DISINTEGRATOR") * 0.8;
        extra += ea.getIntOr("farming_for_dummies_count", 0) * AuctionPrices.value("FARMING_FOR_DUMMIES") * 0.5;
        if (ea.getIntOr("ethermerge", 0) > 0) extra += AuctionPrices.value("ETHERWARP_CONDUIT");
        for (String k : new String[]{"power_ability_scroll", "dye_item", "skin", "drill_part_upgrade_module", "drill_part_fuel_tank", "drill_part_engine"}) {
            String v = ea.getStringOr(k, "");
            if (!v.isEmpty()) extra += AuctionPrices.value(v.toUpperCase(Locale.ROOT)) * (k.equals("skin") || k.equals("dye_item") ? 0.9 : 1);
        }
        String enrich = ea.getStringOr("talisman_enrichment", "");
        if (!enrich.isEmpty()) extra += AuctionPrices.value("TALISMAN_ENRICHMENT_" + enrich.toUpperCase(Locale.ROOT)) * 0.5;
        ListTag scrolls = ea.getListOrEmpty("ability_scroll");
        for (Tag t : scrolls) extra += AuctionPrices.value(t.asString().orElse(""));
        extra += gems(ea.getCompoundOrEmpty("gems"));
        extra += slotUnlocks(id, ea.getCompoundOrEmpty("gems"));
        extra += stars(id, Math.max(ea.getIntOr("upgrade_level", 0), ea.getIntOr("dungeon_item_level", 0)));
        return base + extra;
    }

    // same value split into parts, biggest first. whatever the parts dont cover = "Other"
    public static java.util.LinkedHashMap<String, Double> breakdown(CompoundTag ea) {
        java.util.LinkedHashMap<String, Double> res = new java.util.LinkedHashMap<>();
        String id = ea.getStringOr("id", "");
        double total = item(ea, 1);
        if (id.isEmpty() || total <= 0) return res;
        if (id.equals("PET")) {
            res.put("Pet", total);
            return res;
        }
        boolean book = id.equals("ENCHANTED_BOOK");
        double base = AuctionPrices.valueOf(ea), ench = 0, hpb = 0;
        CompoundTag e = ea.getCompoundOrEmpty("enchantments");
        for (String k : e.keySet()) ench += AuctionPrices.value("ENCHANTMENT_" + k.toUpperCase(Locale.ROOT) + "_" + e.getIntOr(k, 0)) * (book ? 1 : 0.85);
        int pots = ea.getIntOr("hot_potato_count", 0);
        if (pots > 0) {
            hpb += Math.min(10, pots) * AuctionPrices.value("HOT_POTATO_BOOK");
            if (pots > 10) hpb += (pots - 10) * AuctionPrices.value("FUMING_POTATO_BOOK") * 0.6;
        }
        double recomb = ea.getIntOr("rarity_upgrades", 0) > 0 && !book ? AuctionPrices.value("RECOMBOBULATOR_3000") * 0.8 : 0;
        double gem = gems(ea.getCompoundOrEmpty("gems")) + slotUnlocks(id, ea.getCompoundOrEmpty("gems"));
        double star = stars(id, Math.max(ea.getIntOr("upgrade_level", 0), ea.getIntOr("dungeon_item_level", 0)));
        double[] vals = {base, ench, hpb, recomb, gem, star};
        String[] names = {"Base item", "Enchantments", "Potato books", "Recombobulator", "Gemstones", "Stars"};
        java.util.List<java.util.Map.Entry<String, Double>> parts = new java.util.ArrayList<>();
        double sum = 0;
        for (int i = 0; i < vals.length; i++) {
            sum += vals[i];
            if (vals[i] > 0) parts.add(java.util.Map.entry(names[i], vals[i]));
        }
        if (total - sum > 1) parts.add(java.util.Map.entry("Other", total - sum));
        parts.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        for (var en : parts) res.put(en.getKey(), en.getValue());
        return res;
    }

    private static final String[] MASTER_STARS = {"FIRST_MASTER_STAR", "SECOND_MASTER_STAR", "THIRD_MASTER_STAR", "FOURTH_MASTER_STAR", "FIFTH_MASTER_STAR"};

    // essence (+ items) spent on dungeon / crimson stars, plus master stars past the 5th
    private static double stars(String id, int level) {
        if (level <= 0) return 0;
        double v = 0;
        com.google.gson.JsonArray costs = ItemRepo.upgradeCosts(id);
        if (costs != null) for (int i = 0; i < Math.min(level, costs.size()); i++) v += costList(costs.get(i).getAsJsonArray());
        for (int i = 5; i < Math.min(level, 10); i++) if (costs == null || i >= costs.size()) v += AuctionPrices.value(MASTER_STARS[i - 5]);
        return v;
    }

    // coins + gems paid to unlock gem slots (unlocked_slots matched to the items slots in order)
    private static double slotUnlocks(String id, CompoundTag gems) {
        ListTag unlocked = gems.getListOrEmpty("unlocked_slots");
        com.google.gson.JsonArray slots = ItemRepo.gemSlots(id);
        if (unlocked.isEmpty() || slots == null) return 0;
        double v = 0;
        for (Tag t : unlocked) {
            String s = t.asString().orElse("");
            int us = s.lastIndexOf('_');
            if (us < 0) continue;
            String type = s.substring(0, us);
            int idx;
            try {
                idx = Integer.parseInt(s.substring(us + 1));
            } catch (NumberFormatException e) {
                continue;
            }
            int seen = 0;
            for (com.google.gson.JsonElement se : slots) {
                JsonObject so = se.getAsJsonObject();
                if (!so.has("slot_type") || !so.get("slot_type").getAsString().equals(type)) continue;
                if (seen++ == idx && so.has("costs")) v += costList(so.getAsJsonArray("costs"));
            }
        }
        return v;
    }

    private static double costList(com.google.gson.JsonArray list) {
        double v = 0;
        for (com.google.gson.JsonElement ce : list) {
            JsonObject c = ce.getAsJsonObject();
            String type = str(c, "type");
            int amount = c.has("amount") ? c.get("amount").getAsInt() : 1;
            switch (type) {
                case "ESSENCE" -> v += amount * Prices.sell("ESSENCE_" + str(c, "essence_type"));
                case "ITEM" -> v += amount * AuctionPrices.value(str(c, "item_id"));
                case "COINS" -> v += c.has("coins") ? c.get("coins").getAsDouble() : 0;
                default -> {
                }
            }
        }
        return v;
    }

    // gem slots look like RUBY_0: "FINE" or COMBAT_0: "PERFECT" + COMBAT_0_gem: "SAPPHIRE" (or a quality object)
    private static double gems(CompoundTag gems) {
        double v = 0;
        for (String slot : gems.keySet()) {
            if (slot.endsWith("_gem") || slot.equals("unlocked_slots")) continue;
            Tag t = gems.get(slot);
            String quality = t.asString().orElse(null);
            if (quality == null) quality = t.asCompound().map(c -> c.getStringOr("quality", "")).orElse("");
            if (quality.isEmpty()) continue;
            String type = slot.substring(0, Math.max(0, slot.lastIndexOf('_')));
            if (!GEM_TYPES.contains(type)) type = gems.getStringOr(slot + "_gem", "");
            if (type.isEmpty()) continue;
            v += AuctionPrices.value(quality + "_" + type + "_GEM");
        }
        return v;
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }
}
