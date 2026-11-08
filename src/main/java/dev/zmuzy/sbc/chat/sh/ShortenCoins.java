// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.chat.sh;

import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.item.enchants.EnchantParser;

import java.util.regex.Matcher;

// from skyhanni ShortenCoins: gold amounts of 4+ digits ("2,650,000 coins") become short ("2.65M coins")
// works on the formatted line, amount needs a §6 before it (skyhanni's color == gold check)
public final class ShortenCoins {
    static final Patterns.P COINS = Patterns.p("SH.chat.coins.format-no-color");

    private ShortenCoins() {}

    public static Patterns.P COINS_FOR_TEST() {
        return COINS;
    }

    // null when nothing changed
    public static String shorten(String formatted) {
        String clean = dev.zmuzy.sbc.core.GameState.strip(formatted);
        Matcher m = COINS.matcher(clean);
        String out = formatted;
        boolean found = false;
        while (m.find()) {
            String amount = m.group("amount");
            double v;
            try {
                v = Double.parseDouble(amount.replace(",", ""));
            } catch (NumberFormatException e) {
                continue;
            }
            if (v < 1000) continue;
            String formattedNumber = EnchantParser.shortFormat(v);
            // only gold amounts: last colour code before the amount is §6
            int idx = out.indexOf(amount);
            while (idx >= 0) {
                int lastCode = out.lastIndexOf('§', idx);
                boolean gold = lastCode >= 0 && lastCode + 1 < out.length() && out.charAt(lastCode + 1) == '6';
                boolean boundary = idx == 0 || !Character.isDigit(out.charAt(idx - 1)) || (idx >= 2 && out.charAt(idx - 2) == '§');
                int end = idx + amount.length();
                boolean boundaryEnd = end >= out.length() || " ".indexOf(out.charAt(end)) >= 0;
                if (gold && boundary && boundaryEnd) {
                    out = out.substring(0, idx) + formattedNumber + out.substring(end);
                    found = true;
                    break;
                }
                idx = out.indexOf(amount, idx + 1);
            }
        }
        return found ? out : null;
    }
}
