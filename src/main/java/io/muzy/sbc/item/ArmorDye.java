// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.item;

import io.muzy.sbc.core.Config;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DyedItemColor;

import java.util.List;
import java.util.Locale;

// dyes the armor you wear, only on your screen. color per piece with /sbc dye
// shows on every piece whose model can be dyed (leather style, which most skyblock armor uses)
// other players never touched, the item itself isnt changed
public final class ArmorDye {
    public static final List<String> PIECES = List.of("head", "chest", "legs", "feet");

    // true only while the local players body is being submitted for rendering (set by the renderer mixin)
    public static boolean drawingSelf;

    private ArmorDye() {}

    public static void register() {
        Features.add(new Feature("armor_dye", "Items", "Armor dye", "Colours the armor you wear (only you see it). Set it with /sbc dye <head|chest|legs|feet|all> <RRGGBB|off>.", "SkyHanni: custom armor dye")
                .when(() -> true));
    }

    private static String key(EquipmentSlot s) {
        return switch (s) {
            case HEAD -> "head";
            case CHEST -> "chest";
            case LEGS -> "legs";
            case FEET -> "feet";
            default -> "";
        };
    }

    // stack to draw instead, same stack if nothing is set for this piece
    public static ItemStack dyed(ItemStack stack) {
        if (!drawingSelf || Config.I.armorDye.isEmpty() || !Features.on("armor_dye")) return stack;
        var eq = stack.get(DataComponents.EQUIPPABLE);
        if (eq == null) return stack;
        Integer rgb = Config.I.armorDye.get(key(eq.slot()));
        if (rgb == null) return stack;
        ItemStack copy = stack.copy();
        copy.set(DataComponents.DYED_COLOR, new DyedItemColor(rgb & 0xFFFFFF));
        return copy;
    }

    // /sbc dye <piece> <color|off>, returns the message to show
    public static String set(String piece, String colour) {
        piece = piece.toLowerCase(Locale.ROOT);
        List<String> targets = piece.equals("all") ? PIECES : PIECES.contains(piece) ? List.of(piece) : List.of();
        if (targets.isEmpty()) return "Pieces: head, chest, legs, feet, all";
        colour = colour.trim().replace("#", "");
        if (colour.equalsIgnoreCase("off")) {
            for (String t : targets) Config.I.armorDye.remove(t);
            Config.save();
            return "Dye removed from " + piece;
        }
        int rgb;
        try {
            rgb = Integer.parseInt(colour, 16);
        } catch (NumberFormatException e) {
            return "Use a colour like FF8800, or off";
        }
        if (colour.length() != 6) return "Use six hex digits, like FF8800";
        for (String t : targets) Config.I.armorDye.put(t, rgb);
        Config.save();
        return "Dyed " + piece + " #" + colour.toUpperCase(Locale.ROOT) + " (only visible on dyeable armor models)";
    }
}
