// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item.menus;

import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.ui.DripKit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// skyblocker item backgrounds behind a slots item: ItemRarityBackground, JacobMedalBackground,
// LegacyAttributeBackground
// their 2 sprites are drawn here in the same shape with fills, no texture needed
public final class SlotBackgrounds {
    private static final Pattern MEDAL = Pattern.compile("You placed in the (?<bracket>DIAMOND|PLATINUM|GOLD|SILVER|BRONZE) bracket!");
    // columns of the round shapes rows
    private static final int[] ROUND = {5, 3, 2, 1, 1, 0, 0, 0, 0, 0, 0, 1, 1, 2, 3, 5};
    private static final Map<ItemStack, Integer> MEDALS = new WeakHashMap<>(), ATTRIBUTES = new WeakHashMap<>();
    private static long clearedAt;
    // settings kept at hand bc this runs for every slot every frame
    private static Option rarityOn, medalsOn, attributesOn, opacity, style, legacyColors;

    private SlotBackgrounds() {}

    // medal colors
    static int medalColor(String bracket) {
        return switch (bracket) {
            case "DIAMOND" -> 0x55FFFF;
            case "PLATINUM" -> 0x00AAAA;
            case "GOLD" -> 0xFFAA00;
            case "SILVER" -> 0xFFFFFF;
            case "BRONZE" -> 0xFF5555;
            default -> 0;
        };
    }

    // skyblocker SkyblockItemRarity: hypixels newer shades, or with the setting the classic ones
    public static int rarity(int color) {
        if (legacyColors == null || legacyColors.bool()) return color;
        return switch (color & 0xFFFFFF) {
            case 0x5555FF -> 0xFF459BFF;
            case 0xAA00AA -> 0xFFA335EE;
            case 0xFFAA00 -> 0xFFFF9000;
            case 0xAA0000 -> 0xFFD13228;
            default -> color;
        };
    }

    private static int medal(ItemStack st) {
        return MEDALS.computeIfAbsent(st, s -> {
            for (String l : io.github.zmuzy.sbc.track.Watcher.lore(s)) {
                Matcher m = MEDAL.matcher(io.github.zmuzy.sbc.core.GameState.strip(l));
                if (m.find()) return medalColor(m.group("bracket"));
            }
            return 0;
        });
    }

    private static boolean legacyAttributes(ItemStack st) {
        return ATTRIBUTES.computeIfAbsent(st, s -> {
            var data = s.get(DataComponents.CUSTOM_DATA);
            return data != null && data.copyTag().contains("attributes") && !Gain.sbId(s).equals("ATTRIBUTE_SHARD") ? 1 : 0;
        }) == 1;
    }

    // draws at a slots spot in the picked shape
    public static void fill(GuiGraphicsExtractor g, int x, int y, int color) {
        if (style == null || !style.choice().equals("Round")) {
            g.fill(x, y, x + 16, y + 16, color);
            return;
        }
        for (int row = 0; row < 16; row++) g.fill(x + ROUND[row], y + row, x + 16 - ROUND[row], y + row + 1, color);
    }

    private static int alpha(int rgb) {
        return ((int) Math.round(opacity.number() * 255) << 24) | (rgb & 0xFFFFFF);
    }

    // what SlotInfo calls behind each item. rarity comes from the caller, it has it already
    public static void draw(GuiGraphicsExtractor g, int x, int y, ItemStack st, int rarity) {
        if (rarityOn == null) return;
        long now = System.currentTimeMillis();
        if (now - clearedAt > 300_000) { // skyblocker clears these every 5 min
            MEDALS.clear();
            ATTRIBUTES.clear();
            clearedAt = now;
        }
        if (rarity != 0 && rarityOn.bool()) fill(g, x, y, alpha(rarity));
        if (medalsOn.bool()) {
            int medal = medal(st);
            if (medal != 0) fill(g, x, y, alpha(medal));
        }
        if (attributesOn.bool() && legacyAttributes(st)) fill(g, x, y, 0xFFFF0000);
    }

    // called from Menus. the settings live on the feature that already draws the rarity glow
    static void options() {
        Feature f = Features.get("slotinfo");
        if (f == null) return;
        f.option(rarityOn = Option.bool("slotinfo.rarity", "Rarity colour", "", true))
                .option(legacyColors = Option.bool("slotinfo.legacy_colors", "Classic rarity colours", "", false))
                .option(medalsOn = Option.bool("slotinfo.jacob_medals", "Jacob's contest medals", "The colour of the medal behind a contest in Your Contests.", true))
                .option(attributesOn = Option.bool("slotinfo.legacy_attributes", "Items with old attributes", "Red behind an item that still has attributes.", true))
                .option(opacity = Option.number("slotinfo.opacity", "Opacity", "", 0.28, 0.05, 1.0, 0.05))
                .option(style = Option.choice("slotinfo.style", "Shape", "", "Square", "Square", "Round"))
                .option(Option.bool("slotinfo.max_pet_level", "Level of maxed pets", "Off: a pet at its top level shows no number.", false));
    }
}
