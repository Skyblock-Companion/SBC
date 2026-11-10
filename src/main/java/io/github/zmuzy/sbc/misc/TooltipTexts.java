// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.misc;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.tooltip.StatIcons;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.regex.Matcher;

// 2 tooltip rewrites that start off. from skyhanni misc/ColorfulItemStats.kt (stat icon after its number) +
// ReplaceRomanNumerals.kt (roman numerals as numbers, tooltips only)
public final class TooltipTexts {
    public static final String STATS = "colorful_stats", ROMAN = "roman_numerals";
    private static final Patterns.P STAT = Patterns.p("SH.misc.itemstats.generic-stats-no-color");
    private static final String[] ROMAN_KEYS = {"level", "tier", "chapter", "skill", "milestone", "floor", "collection", "progress", "skill-name", "heart-of-the", "rewards"};
    private static final Patterns.P NAMED_TIER = Patterns.p("SH.romannumerals.inventory.context.named-tier"), TASK_LIST = Patterns.p("SH.romannumerals.inventory.context.task-list"),
            PERK_SHOP = Patterns.p("SH.romannumerals.inventory.context.perk-shop"), UPGRADE_MENU = Patterns.p("SH.romannumerals.inventory.context.upgrade-menu"),
            COLLECTION_ITEM = Patterns.p("SH.romannumerals.inventory.context.collection-item"), COLLECTION_MENU = Patterns.p("SH.romannumerals.inventory.context.collection-menu"),
            CROP = Patterns.p("SH.romannumerals.inventory.context.crop-milestone");
    private static final java.util.regex.Pattern IS_ROMAN = java.util.regex.Pattern.compile("^M{0,3}(CM|CD|D?C{0,3})(XC|XL|L?X{0,3})(IX|IV|V?I{0,3})$");

    private TooltipTexts() {}

    // --- skyhanni ColorfulItemStats ---

    // one line with its bonus + the stat icon after it, the line as is if its not a stat. works on coded text so
    // colors stay
    static String withIcon(String coded, String plain, boolean noPercent, boolean noSeconds) {
        Matcher m = STAT.matcher(plain);
        if (!m.find()) return coded;
        StatIcons.Stat stat = StatIcons.of(m.group("stat").trim());
        if (stat == null) return coded;
        String bonus = m.group("bonus"), shown = bonus;
        if (noPercent && shown.endsWith("%")) shown = shown.substring(0, shown.length() - 1);
        else if (noSeconds && shown.endsWith("s")) shown = shown.substring(0, shown.length() - 1);
        int at = coded.indexOf(bonus);
        return at < 0 ? coded : coded.substring(0, at) + shown + stat.icon() + coded.substring(at + bonus.length());
    }

    private static void stats(ItemStack stack, List<Component> lines) {
        Feature f = Features.get(STATS);
        boolean percent = f.option("colorful_stats.replacePercentages").bool(), seconds = f.option("colorful_stats.replaceRiftSeconds").bool();
        for (int i = 0; i < lines.size(); i++) {
            String plain = lines.get(i).getString();
            if (plain.indexOf(':') < 0) continue;
            String coded = LegacyText.of(lines.get(i));
            String out = withIcon(coded, plain, percent, seconds);
            if (!out.equals(coded)) lines.set(i, Component.literal(out));
        }
    }

    // --- skyhanni ReplaceRomanNumerals ---

    // from skyhanni String.romanToDecimal
    static int decimal(String roman) {
        int total = 0, last = 0;
        for (int i = roman.length() - 1; i >= 0; i--) {
            int v = switch (roman.charAt(i)) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                case 'D' -> 500;
                case 'M' -> 1000;
                default -> 0;
            };
            total += v < last ? -v : v;
            last = Math.max(last, v);
        }
        return total;
    }

    static boolean isRoman(String s) {
        return !s.isEmpty() && IS_ROMAN.matcher(s).matches();
    }

    private static String replaceWith(String text, java.util.regex.Pattern p) {
        Matcher m = p.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        boolean changed = false;
        while (m.find()) {
            String roman = m.group("roman");
            if (!isRoman(roman)) continue;
            out.append(text, last, m.start("roman")).append(decimal(roman));
            last = m.end("roman");
            changed = true;
        }
        return changed ? out.append(text, last, text.length()).toString() : text;
    }

    // from skyhanni replaceInText: every listed pattern + the titles own numeral where it stands alone
    static String numerals(String text, boolean named, String titleNumeral) {
        String out = text;
        for (String key : ROMAN_KEYS) out = replaceWith(out, Patterns.get("SH.romannumerals.inventory." + key));
        if (named) out = replaceWith(out, NAMED_TIER.pattern());
        if (titleNumeral != null) out = out.replaceAll("\\b" + titleNumeral + "\\b", String.valueOf(decimal(titleNumeral)));
        return out;
    }

    private static boolean context(List<Component> lines) {
        var screen = Minecraft.getInstance().gui.screen();
        String menu = screen == null ? "" : GameState.strip(screen.getTitle().getString());
        if (UPGRADE_MENU.matcher(menu).matches() || COLLECTION_MENU.matcher(menu).matches()) return true;
        for (Component c : lines) {
            String l = c.getString();
            if (TASK_LIST.matcher(l).matches() || PERK_SHOP.matcher(l).find() || CROP.matcher(l).find() || COLLECTION_ITEM.matcher(l).find()) return true;
        }
        return false;
    }

    // from skyhanni replaceNumerals: piece by piece like the game styles them so a color code never lands between
    // a word and its numeral
    static Component numerals(Component line, boolean named, String titleNumeral) {
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        boolean[] changed = {false};
        line.visit((style, text) -> {
            String replaced = numerals(text, named, titleNumeral);
            if (!replaced.equals(text)) changed[0] = true;
            out.append(Component.literal(replaced).withStyle(style));
            return java.util.Optional.empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return changed[0] ? out : line;
    }

    private static void roman(ItemStack stack, List<Component> lines) {
        if (lines.isEmpty()) return;
        boolean named = context(lines);
        String title = null;
        Matcher m = NAMED_TIER.matcher(lines.get(0).getString());
        if (named && m.matches() && isRoman(m.group("roman"))) title = m.group("roman");
        for (int i = 0; i < lines.size(); i++) lines.set(i, numerals(lines.get(i), named && i == 0, i == 0 ? null : title));
    }

    public static void register() {
        Features.add(new Feature(STATS, "Items", "Stat icons on tooltips", "Puts each stat's icon after its number.", "SkyHanni: Colorful Item Tooltips")
                .when(() -> GameState.inSkyBlock).defaultOff().tooltip(TooltipTexts::stats)
                .option(Option.bool("colorful_stats.replacePercentages", "Icon instead of a percent sign", "", false))
                .option(Option.bool("colorful_stats.replaceRiftSeconds", "Icon instead of the seconds of Rift Time", "", false)));
        Features.add(new Feature(ROMAN, "Items", "Numbers for Roman numerals", "On tooltips: Level XII is Level 12.", "SkyHanni: Replace Roman Numerals")
                .when(() -> GameState.inSkyBlock).defaultOff().tooltip(TooltipTexts::roman));
    }
}
