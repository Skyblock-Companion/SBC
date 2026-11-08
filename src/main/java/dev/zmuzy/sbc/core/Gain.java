// SPDX-License-Identifier: GPL-3.0-only
package dev.zmuzy.sbc.core;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// coin values for the profit + "vs market" lines on menu pages. unknown = -1, never 0
public final class Gain {
    private Gain() {}

    private static final dev.zmuzy.sbc.core.Patterns.P COINS = dev.zmuzy.sbc.core.Patterns.p("Gain.COINS");
    private static final dev.zmuzy.sbc.core.Patterns.P QTY = dev.zmuzy.sbc.core.Patterns.p("Gain.QTY");

    // hypixel item id on a stack, or ""
    public static String sbId(ItemStack st) {
        if (st == null || st.isEmpty()) return "";
        var cd = st.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return "";
        return tag(cd).getStringOr("id", "");
    }

    // the item data without copying it, dont change it. copies when the mixin isnt there (tests)
    public static net.minecraft.nbt.CompoundTag tag(net.minecraft.world.item.component.CustomData cd) {
        if ((Object) cd instanceof dev.zmuzy.sbc.mc.mixin.CustomDataAccess a) return a.sbc$tag();
        return cd.copyTag();
    }

    // "3,000,000 Coins" -> 3000000, "FREE" -> 0, anything else -> -1
    public static double coins(String text) {
        if (text == null) return -1;
        if (text.trim().equalsIgnoreCase("FREE")) return 0;
        Matcher m = COINS.matcher(text);
        if (!m.find()) return -1;
        try {
            return Double.parseDouble(m.group(1).replace(",", ""));
        } catch (Exception e) {
            return -1;
        }
    }

    public static double ofId(String id) {
        if (id == null || id.isEmpty() || id.equals("PET") || id.equals("ENCHANTED_BOOK")) return -1;
        double v = AuctionPrices.value(id);
        return v > 0 ? v : -1;
    }

    // one stack at market value (count included), or -1
    public static double ofStack(ItemStack st) {
        double each = ofId(sbId(st));
        return each < 0 ? -1 : each * st.getCount();
    }

    // one chest contents line: "Enchanted Book (Combo I)", "Wither Essence x20", "Wither Boots". -1 = no price
    public static double ofLoot(String line) {
        if (line == null || line.isBlank()) return -1;
        String t = line.trim();
        int qty = 1;
        Matcher q = QTY.matcher(t);
        if (q.matches()) {
            t = q.group(1).trim();
            qty = Integer.parseInt(q.group(2));
        }
        if (t.startsWith("Enchanted Book (") && t.endsWith(")")) {
            String e = t.substring(16, t.length() - 1).trim();
            int sp = e.lastIndexOf(' ');
            String base = sp < 0 ? e : e.substring(0, sp), lv = sp < 0 ? "1" : e.substring(sp + 1);
            int level = lv.chars().allMatch(Character::isDigit) ? Integer.parseInt(lv) : GameState.roman(lv.toUpperCase(Locale.ROOT));
            String name = base.toUpperCase(Locale.ROOT).replace(' ', '_').replace("'", "").replace("-", "_");
            double v = ofId("ENCHANTMENT_" + name + "_" + level);
            if (v < 0) v = ofId("ENCHANTMENT_ULTIMATE_" + name + "_" + level);
            return v < 0 ? -1 : v * qty;
        }
        if (t.endsWith(" Essence")) {
            double v = ofId("ESSENCE_" + t.substring(0, t.length() - 8).trim().toUpperCase(Locale.ROOT));
            return v < 0 ? -1 : v * qty;
        }
        double v = ofId(Prices.idFromName(t));
        return v < 0 ? -1 : v * qty;
    }

    // sum of lines with a price, unknown[0] counts the ones without
    public static double ofLoot(Iterable<String> lines, int[] unknown) {
        double sum = 0;
        for (String l : lines) {
            double v = ofLoot(l);
            if (v < 0) {
                if (unknown != null) unknown[0]++;
            } else sum += v;
        }
        return sum;
    }

    public static String signed(double v) {
        return (v >= 0 ? "+" : "-") + Prices.coins(Math.abs(v));
    }
}
