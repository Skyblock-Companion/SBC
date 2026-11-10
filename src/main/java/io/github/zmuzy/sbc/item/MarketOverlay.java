// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.item;

import io.github.zmuzy.sbc.core.AuctionPrices;
import io.github.zmuzy.sbc.core.Gain;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Prices;
import io.github.zmuzy.sbc.feature.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// price help inside hypixels own ah and bazaar menus
// under every auction the asking price vs the market as a colored percent ("-18%" green = below lowest bin),
// under bazaar items the instant buy price
// reads the lore the menu already shows, nothing gets sent
public final class MarketOverlay {
    private static final Pattern BIN = Pattern.compile("^(?:Buy it now|Top bid|Starting bid): ([\\d,]+) coins?$");
    private static final Pattern BZ_BUY = Pattern.compile("^Buy price: ([\\d,.]+) coins?$");

    private MarketOverlay() {}

    private static String title() {
        var s = Minecraft.getInstance().gui.screen();
        return s == null ? "" : GameState.strip(s.getTitle().getString()).toLowerCase(Locale.ROOT);
    }

    private static boolean inAuctions() {
        String t = title();
        return t.contains("auction") && !t.contains("create");
    }

    private static boolean inBazaar() {
        return title().startsWith("bazaar") || title().contains("➜");
    }

    private static double lore(ItemStack st, Pattern p) {
        var lore = st.get(DataComponents.LORE);
        if (lore == null) return -1;
        for (Component c : lore.lines()) {
            Matcher m = p.matcher(GameState.strip(c.getString()).trim());
            if (m.matches()) {
                try {
                    return Double.parseDouble(m.group(1).replace(",", ""));
                } catch (Exception ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    // small text under the slot, or null
    public static String[] text(ItemStack st) {
        if (!Features.on("market_overlay") || st.isEmpty()) return null;
        if (inAuctions()) {
            double ask = lore(st, BIN);
            if (ask <= 0) return null;
            String id = Gain.sbId(st);
            double market = id.isEmpty() ? 0 : AuctionPrices.value(id);
            if (market <= 0) return null;
            double pct = (ask - market) / market * 100;
            String s = (pct <= 0 ? "" : "+") + Math.round(pct) + "%";
            String col = pct <= -15 ? "§a" : pct <= 5 ? "§e" : "§c";
            return new String[]{col + s, Prices.coins(market)};
        }
        if (inBazaar()) {
            double buy = lore(st, BZ_BUY);
            if (buy <= 0) return null;
            return new String[]{"§6" + Prices.coins(buy), ""};
        }
        return null;
    }

    public static void draw(GuiGraphicsExtractor g, Slot slot) {
        String[] t = text(slot.getItem());
        if (t == null) return;
        var font = Minecraft.getInstance().font;
        var p = g.pose();
        p.pushMatrix();
        p.translate(slot.x + 0.5f, slot.y + 11.5f);
        p.scale(0.6f, 0.6f);
        g.text(font, t[0], 0, 0, 0xFFFFFFFF, true);
        p.popMatrix();
    }
}
