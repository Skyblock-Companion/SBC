// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.ui;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.data.BingoData;

// from skyhanni AdvancedPlayerList: what shows after a players name, the way you set it. splits the colored
// name from the stuff after (emblems, ironman, bingo rank, crimson faction)
final class TabPlayerNames {
    private TabPlayerNames() {}

    // from skyhanni AdvancedPlayerList.CrimsonIsleFaction
    private static final String BARBARIAN = "⚒", MAGE = "ቾ";

    record Wanted(boolean hideEmblem, boolean hideFaction, boolean plainColor, boolean bingoNumber) {
        boolean nothing() {
            return !hideEmblem && !hideFaction && !plainColor;
        }
    }

    // lists a mark comes from
    record People(String own, java.util.Set<String> marked, java.util.Set<String> party, java.util.Collection<String> friends, java.util.Collection<String> guild, String markedColor) {}

    // from skyhanni getSocialIcon: first one that fits, the name itself has no mark
    static String socialIcon(String name, People p) {
        if (name.equals(p.own)) return "";
        for (String n : p.marked) if (n.equalsIgnoreCase(name)) return p.markedColor + "§lMARKED";
        if (p.party.contains(name)) return "§9§lP";
        for (String n : p.friends) if (n.equalsIgnoreCase(name)) return "§d§lF";
        for (String n : p.guild) if (n.equalsIgnoreCase(name)) return "§2§lG";
        return "";
    }

    // player name out of "§b[MVP§c+§b] Name §2ꀾ§7♲"
    static String nameOf(String coded) {
        String[] parts = GameState.strip(coded).trim().split(" ");
        int index = parts[0].startsWith("[") ? 1 : 0;
        return index < parts.length ? parts[index] : "";
    }

    // the line with its mark after it, or as is without one
    static String marked(String coded, People p) {
        String icon = socialIcon(nameOf(coded), p);
        return icon.isEmpty() ? coded : coded + " " + icon;
    }

    // from the colored text after the level, "§b[MVP§c+§b] Name §2ꀾ§7♲" etc
    static String rewrite(String coded, Wanted w, boolean crimson) {
        if (w.nothing()) return coded;
        String[] parts = coded.trim().split(" ");
        int index = GameState.strip(coded).contains("[") ? 1 : 0;
        if (index >= parts.length) return coded;
        String colored = parts[index];
        String plain = GameState.strip(colored);
        String prefix = index == 1 ? parts[0] + " " : "";
        String name = w.plainColor ? "§b" + plain : prefix + colored;
        StringBuilder suffix = new StringBuilder();
        for (int i = index + 1; i < parts.length; i++) suffix.append(i > index + 1 ? " " : "").append(parts[i]);
        String rest = suffix.toString(), plainRest = GameState.strip(rest);

        String faction = "";
        if (crimson && plainRest.contains(BARBARIAN)) {
            faction = "§c" + BARBARIAN;
            rest = rest.replaceAll("(?:§.)*" + BARBARIAN, "");
        } else if (crimson && plainRest.contains(MAGE)) {
            faction = "§5" + MAGE;
            rest = rest.replaceAll("(?:§.)*" + MAGE, "");
        }

        StringBuilder out = new StringBuilder(name);
        if (w.hideEmblem) {
            if (plainRest.contains("♲")) out.append(" §7♲");
            else {
                Integer rank = BingoData.rank(coded);
                if (rank != null) out.append(' ').append(BingoData.icon(rank)).append(w.bingoNumber ? " " + rank : "");
            }
        } else if (!rest.isBlank()) {
            out.append(' ').append(rest.trim());
        }
        if (!faction.isEmpty() && !w.hideFaction) out.append(' ').append(faction);
        return out.toString();
    }
}
