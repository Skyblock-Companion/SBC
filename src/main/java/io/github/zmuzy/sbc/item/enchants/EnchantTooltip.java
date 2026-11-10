// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item.enchants;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.LegacyText;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.item.value.ItemNbt;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// tooltip side of the EnchantParser port. runs on every item tooltip (ItemTooltipCallback, like skyhanni
// ToolTipTextEvent) and swaps the lore enchant block for the parsed one
// names/levels come from the items nbt enchants, lines are formatted strings turned back into components
public final class EnchantTooltip {
    public static final String ID = "enchant_parsing";

    private static List<Component> cachedBefore = List.of(), cachedAfter = List.of();

    private EnchantTooltip() {}

    static boolean b(String key, boolean def) {
        Feature f = Features.get(ID);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.bool();
    }

    static String choice(String key, String def) {
        Feature f = Features.get(ID);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.text();
    }

    // skyhanni ItemCategory.miningTools: pickaxe, drill, gauntlet
    static boolean isMiningTool(List<String> lore) {
        for (String l : lore) {
            String s = GameState.strip(l);
            if (s.endsWith("PICKAXE") || s.endsWith("DRILL") || s.endsWith("GAUNTLET")) return true;
        }
        return false;
    }

    static boolean isBook(ItemStack st) {
        return Gain.sbId(st).equals("ENCHANTED_BOOK");
    }

    static void onTooltip(ItemStack st, List<Component> lines) {
        if (!(GameState.inSkyBlock || SbcMod.DEV) || lines.size() < 2) return;
        var cd = st.get(DataComponents.CUSTOM_DATA);
        if (cd == null) return;
        var ea = cd.copyTag();
        Map<String, Integer> enchants = ItemNbt.getHypixelEnchantments(ea);
        if (enchants == null || enchants.isEmpty()) return;
        // skyhanni EnchantCache: same lore in, same lore out
        if (lines.equals(cachedBefore)) {
            lines.clear();
            lines.addAll(cachedAfter);
            return;
        }
        List<Component> before = new ArrayList<>(lines);
        List<String> lore = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) lore.add(LegacyText.of(lines.get(i)));
        EnchantParser.Options opt = new EnchantParser.Options();
        opt.colorParsing = b("colorParsing", true);
        opt.format = EnchantParser.Format.valueOf(choice("format", "NORMAL"));
        opt.hideEnchantDescriptions = b("hideEnchantDescriptions", false);
        opt.stackingEnchantProgress = b("stackingEnchantProgress", true);
        opt.boldPerfectEnchant = b("boldPerfectEnchant", false);
        opt.perfectIsChroma = !b("goldPerfect", false);
        colours(opt);
        Map<String, Double> progress = new HashMap<>();
        for (var s : EnchantsData.stacking().values()) if (!s.nbtNum.isEmpty() && ea.contains(s.nbtNum)) progress.put(s.nbtNum, ea.getDoubleOr(s.nbtNum, ea.getLongOr(s.nbtNum, 0)));
        List<String> parsed = EnchantParser.parse(lore, enchants, Gain.sbId(st), isBook(st), isMiningTool(lore), progress, opt);
        if (parsed == lore) return;
        Component name = lines.get(0);
        lines.clear();
        lines.add(name);
        for (String l : parsed) lines.add(EnchantParser.rgbLine(l));
        cachedBefore = before;
        cachedAfter = new ArrayList<>(lines);
    }

    private static final String[] COLOUR_NAMES = {"BLACK", "DARK_BLUE", "DARK_GREEN", "DARK_AQUA", "DARK_RED", "DARK_PURPLE", "GOLD", "GRAY", "DARK_GRAY", "BLUE", "GREEN", "AQUA", "RED", "LIGHT_PURPLE", "YELLOW", "WHITE"};
    private static final String[] CHOICES = java.util.stream.Stream.concat(java.util.Arrays.stream(COLOUR_NAMES), java.util.stream.Stream.of("CHROMA")).toArray(String[]::new);

    // skyhanni LorenzColor -> color code. chroma has no code here so it stays the sources stand in, bold gold
    static String code(String colour) {
        int i = java.util.Arrays.asList(COLOUR_NAMES).indexOf(colour);
        return i < 0 ? "§6" : "§" + "0123456789abcdef".charAt(i);
    }

    private static String choiceOf(String key, String def) {
        Feature f = Features.get(ID);
        Option o = f == null ? null : f.option(key);
        return o == null ? def : o.choice();
    }

    // one tiers color: the advanced color if thats on, else the picked name
    private static String tier(String name, String def) {
        Feature f = Features.get(ID);
        Option use = f == null ? null : f.option("enchant_parsing.useAdvanced" + name), custom = f == null ? null : f.option("enchant_parsing.advanced" + name);
        if (use != null && custom != null && use.bool()) return EnchantParser.rgb(custom.color());
        return code(choiceOf("enchant_parsing." + name.toLowerCase(java.util.Locale.ROOT) + "Color", def));
    }

    // skyhanni Enchant.getStyle choices
    private static void colours(EnchantParser.Options opt) {
        opt.ultimateColor = tier("Ultimate", "LIGHT_PURPLE");
        opt.perfectColor = tier("Perfect", "CHROMA");
        opt.greatColor = tier("Great", "GOLD");
        opt.goodColor = tier("Good", "BLUE");
        opt.poorColor = tier("Poor", "GRAY");
        boolean advancedPerfect = Features.get(ID) != null && Features.get(ID).option("enchant_parsing.useAdvancedPerfect") != null && Features.get(ID).option("enchant_parsing.useAdvancedPerfect").bool();
        if (advancedPerfect || !choiceOf("enchant_parsing.perfectColor", "CHROMA").equals("CHROMA")) opt.perfectIsChroma = false;
    }

    public static void register() {
        Features.add(new Feature(ID, "Items", "Enchantment lists", "Re-colours and re-orders the enchantments on item tooltips (ultimates bold pink, max level gold, above the good level gold, good blue, below grey), optionally compressed to three per line or one per line, hides descriptions, and shows stacking enchant progress.", "SkyHanni: Enchant Parsing")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).tooltip(EnchantTooltip::onTooltip)
                .option(Option.bool("colorParsing", "Colour enchants", "", true))
                .option(Option.choice("format", "Format", "NORMAL keeps Hypixel's layout, COMPRESSED three per line, STACKED one per line.", "NORMAL", "NORMAL", "COMPRESSED", "STACKED"))
                .option(Option.bool("hideEnchantDescriptions", "Hide descriptions", "", false))
                .option(Option.bool("stackingEnchantProgress", "Stacking progress", "Show the stacking enchant's progress at the bottom.", true))
                .option(Option.bool("boldPerfectEnchant", "Bold perfect", "", false))
                .option(Option.bool("goldPerfect", "Plain gold for perfect", "Gold without bold instead of the chroma stand-in (bold gold).", false)));
        Features.get(ID)
                .option(Option.choice("enchant_parsing.ultimateColor", "Ultimate enchant colour", "Always bold.", "LIGHT_PURPLE", CHOICES))
                .option(Option.bool("enchant_parsing.useAdvancedUltimate", "Ultimate: your own colour", "Use the colour below instead.", false))
                .option(Option.color("enchant_parsing.advancedUltimate", "Ultimate: own colour", "", 0xFFFFFF55))
                .option(Option.choice("enchant_parsing.perfectColor", "Perfect enchant colour", "Chroma shows as bold gold.", "CHROMA", CHOICES))
                .option(Option.bool("enchant_parsing.useAdvancedPerfect", "Perfect: your own colour", "Use the colour below instead.", false))
                .option(Option.color("enchant_parsing.advancedPerfect", "Perfect: own colour", "", 0xFFFFFF55))
                .option(Option.choice("enchant_parsing.greatColor", "Great enchant colour", "", "GOLD", CHOICES))
                .option(Option.bool("enchant_parsing.useAdvancedGreat", "Great: your own colour", "Use the colour below instead.", false))
                .option(Option.color("enchant_parsing.advancedGreat", "Great: own colour", "", 0xFFFFFF55))
                .option(Option.choice("enchant_parsing.goodColor", "Good enchant colour", "", "BLUE", CHOICES))
                .option(Option.bool("enchant_parsing.useAdvancedGood", "Good: your own colour", "Use the colour below instead.", false))
                .option(Option.color("enchant_parsing.advancedGood", "Good: own colour", "", 0xFFFFFF55))
                .option(Option.choice("enchant_parsing.poorColor", "Poor enchant colour", "", "GRAY", CHOICES))
                .option(Option.bool("enchant_parsing.useAdvancedPoor", "Poor: your own colour", "Use the colour below instead.", false))
                .option(Option.color("enchant_parsing.advancedPoor", "Poor: own colour", "", 0xFFFFFF55));
    }
}
