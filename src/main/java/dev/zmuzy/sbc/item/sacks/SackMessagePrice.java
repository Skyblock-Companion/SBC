// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.item.sacks;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.ItemRepo;
import dev.zmuzy.sbc.core.Prices;
import dev.zmuzy.sbc.feature.Features;
import dev.zmuzy.sbc.item.market.Market;
import dev.zmuzy.sbc.item.tooltip.TipExtras;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyblocker SackMessagePrice: the hover of a [Sacks] message gets what its items are worth at npc and
// on the bazaar
// hover layout is skyblockers: "Added items:", then count, item name, sack name per item, then a "\n\n" line
public final class SackMessagePrice {
    private static final Pattern ITEM_COUNT_PATTERN = Pattern.compile("([-+][\\d,]+)");

    private SackMessagePrice() {}

    // text a component holds itself without siblings, null if its not plain text
    private static String literal(Component c) {
        return c.getContents() instanceof PlainTextContents p ? p.text() : null;
    }

    // skyblocker parseItems: item name -> count, empty if the text isnt what it should be
    static Map<String, Integer> parseItems(List<String> texts) {
        Map<String, Integer> items = new LinkedHashMap<>();
        Integer lastCount = null;
        String lastItemName = null;
        for (String content : texts) {
            if (content == null) continue;
            if (content.equals("\n\n")) break; // has to come before the blank check
            if (content.isBlank()) continue;
            String trimmed = content.trim();
            if (lastCount == null && lastItemName == null) {
                Matcher matcher = ITEM_COUNT_PATTERN.matcher(trimmed);
                if (!matcher.find()) return new LinkedHashMap<>();
                lastCount = Integer.parseInt(matcher.group(1).replace(",", "").replace("+", ""));
            } else if (lastCount != null && lastItemName == null) {
                lastItemName = trimmed;
            } else {
                items.put(lastItemName, lastCount);
                lastCount = null;
                lastItemName = null;
            }
        }
        return items;
    }

    // skyblocker ItemTooltip.getCoinsMessage for a total
    private static String coins(double price) {
        return String.format(Locale.ENGLISH, "%1$,.1f", price) + " Coins";
    }

    private static Component priceLine(String label, double price) {
        return Component.literal(label + (price > 0 ? "§3" + coins(price) : "§cNo data"));
    }

    // skyblocker deepCopy: the packet message cant be changed, and a Style equal by value keeps the old hover
    private static MutableComponent deepCopy(Component text) {
        MutableComponent copy = text.plainCopy();
        if (text.getStyle().getHoverEvent() instanceof HoverEvent.ShowText(Component showText)) copy.setStyle(Style.EMPTY.withHoverEvent(new HoverEvent.ShowText(deepCopy(showText))).applyTo(text.getStyle()));
        else copy.setStyle(text.getStyle());
        for (Component sibling : text.getSiblings()) copy.append(deepCopy(sibling));
        return copy;
    }

    private static List<List<Component>> hoverSiblings(Component text) {
        List<List<Component>> out = new ArrayList<>();
        for (Component sibling : text.getSiblings()) {
            if (sibling.getStyle().getHoverEvent() instanceof HoverEvent.ShowText(Component hover)) {
                String root = literal(hover);
                if (root != null && (root.startsWith("Added items:") || root.startsWith("Removed items:"))) out.add(hover.getSiblings());
            }
        }
        return out;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!(GameState.inSkyBlock || SbcMod.DEV) || !Features.on(Sacks.DISPLAY) || !Market.b(Sacks.DISPLAY, "sack_display.messagePrice", true)) return ChatBus.PASS;
        if (!line.component().getString().startsWith("[Sacks] ")) return ChatBus.PASS;
        MutableComponent copy = deepCopy(line.component());
        List<List<Component>> lists = hoverSiblings(copy);
        if (lists.isEmpty()) return ChatBus.PASS;
        for (List<Component> texts : lists) {
            List<String> contents = new ArrayList<>();
            for (Component c : texts) contents.add(literal(c));
            Map<String, Integer> items = parseItems(contents);
            if (items.isEmpty()) return ChatBus.PASS;
            double npc = 0, buy = 0, sell = 0;
            for (var e : items.entrySet()) {
                String id = ItemRepo.idForName(e.getKey());
                if (id == null) continue;
                npc += ItemRepo.npcPrice(id) * e.getValue();
                Prices.Product p = Prices.get(id);
                if (p != null) {
                    buy += p.buy() * e.getValue();
                    sell += p.sell() * e.getValue();
                }
            }
            texts.add(CommonComponents.NEW_LINE);
            texts.add(TipExtras.smoothLine());
            texts.add(CommonComponents.NEW_LINE);
            texts.add(priceLine("§eNPC Sell Price: ", npc));
            texts.add(CommonComponents.NEW_LINE);
            texts.add(priceLine("§6Bazaar Buy Price: ", buy));
            texts.add(CommonComponents.NEW_LINE);
            texts.add(priceLine("§6Bazaar Sell Price: ", sell));
        }
        return new ChatBus.Replace(copy);
    }

    public static void register() {
        // after everyone who reads the hover as hypixel wrote it, before anything that hides or compacts the line
        ChatBus.register(ChatBus.FILTER - 1, "sack message price", null, SackMessagePrice::onChat);
    }
}
