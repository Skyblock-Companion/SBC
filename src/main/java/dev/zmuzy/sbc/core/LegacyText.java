// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.core;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// components as legacy "§" text like skyhannis patterns want (TextCompat.kt formattedTextCompat): every
// styled part gets its color + format codes in front, parts split by §r
public final class LegacyText {
    private LegacyText() {}

    private static final Map<Integer, ChatFormatting> COLOR_LUT = new HashMap<>();

    static {
        for (ChatFormatting f : ChatFormatting.values()) {
            TextColor c = TextColor.fromLegacyFormat(f);
            if (c != null) COLOR_LUT.put(c.getValue(), f);
        }
    }

    // Style.chatStyle(): color code (or <#hex> for rgb) then the format codes
    public static String chatStyle(Style s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        TextColor c = s.getColor();
        if (c != null) {
            ChatFormatting f = COLOR_LUT.get(c.getValue());
            sb.append(f != null ? f.toString() : "<" + c.serialize() + ">");
        }
        if (s.isBold()) sb.append("§l");
        if (s.isItalic()) sb.append("§o");
        if (s.isUnderlined()) sb.append("§n");
        if (s.isStrikethrough()) sb.append("§m");
        if (s.isObfuscated()) sb.append("§k");
        return sb.toString();
    }

    private static void flatten(Component c, List<Component> out) {
        out.add(c);
        for (Component s : c.getSiblings()) flatten(s, out);
    }

    // components own text without siblings (unformattedTextForChatCompat)
    private static String ownText(Component c) {
        StringBuilder sb = new StringBuilder();
        c.getContents().visit(t -> {
            sb.append(t);
            return java.util.Optional.empty();
        });
        return sb.toString();
    }

    // formattedTextCompat(noExtraResets, leadingWhite)
    public static String of(Component c, boolean noExtraResets, boolean leadingWhite) {
        if (c == null) return "";
        List<Component> parts = new ArrayList<>();
        flatten(c, parts);
        StringBuilder sb = new StringBuilder(50);
        boolean wasFormatted = false;
        for (Component part : parts) {
            String chatStyle = chatStyle(part.getStyle());
            if (!chatStyle.isEmpty() && (leadingWhite || (wasFormatted && (sb.length() != 2 || sb.charAt(0) != '§' || sb.charAt(1) != 'r')) || !chatStyle.equals("§f"))) {
                sb.append(chatStyle);
                wasFormatted = true;
            }
            sb.append(ownText(part));
            if (!noExtraResets) {
                sb.append("§r");
                wasFormatted = true;
            } else if (part.equals(Component.empty())) {
                sb.append("§r");
                wasFormatted = true;
            }
        }
        return stripResets(sb.toString());
    }

    // from skyhanni StringUtils.stripHypixelMessage (ChatManager.onChatAllow runs it on every line): all leading
    // + trailing resets go, not just one each
    static String stripResets(String s) {
        int from = 0, to = s.length();
        while (s.startsWith("§r", from)) from += 2;
        while (to - 2 >= from && s.startsWith("§r", to - 2)) to -= 2;
        return s.substring(from, to);
    }

    // a centered title line (miniboss, kuudras DOWN!) starts with white spaces. of(Component) drops a leading
    // white like the compat code, and those patterns want it, so it goes back
    public static String whiteStart(String s) {
        return !s.isEmpty() && Character.isWhitespace(s.charAt(0)) ? "§f" + s : s;
    }

    // formattedTextCompat(): the default form skyhanni feeds its chat + lore patterns
    public static String of(Component c) {
        return of(c, false, false);
    }

    // formattedTextCompatLeadingWhiteLessResets(): item display names
    public static String name(ItemStack st) {
        if (st == null || st.isEmpty()) return "";
        return of(st.getHoverName(), true, true);
    }

    // from skyhanni ItemUtils.getLore (formattedTextCompatLessResets). hypixel sends every color of a line as its own piece
    // and the lore patterns want them glued together, "§7Total: §a619,815"
    public static List<String> lore(ItemStack st) {
        List<String> out = new ArrayList<>();
        if (st == null || st.isEmpty()) return out;
        ItemLore lore = st.get(DataComponents.LORE);
        if (lore == null) return out;
        for (Component l : lore.lines()) out.add(of(l, true, false));
        return out;
    }

    // from skyhanni ItemUtils.cleanName: name without color codes
    public static String cleanName(ItemStack st) {
        return GameState.strip(name(st));
    }
}
