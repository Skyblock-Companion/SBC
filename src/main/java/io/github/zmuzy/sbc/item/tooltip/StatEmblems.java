// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.item.tooltip;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

// little stat pictures, our own 9x9 pixel art in the shapes + colors skyblock uses, so they look the same
// with or without hypixels resource pack
// in the rows: # = stat color, + lighter, - darker, w white, k black, b handle brown
public final class StatEmblems {
    private StatEmblems() {}

    static final int SIZE = 9;
    private static final Map<String, String[]> ART = new HashMap<>();
    private static final Map<String, String[]> BY_SYMBOL = new HashMap<>();
    private static final Map<String, String[]> BY_NAME = new HashMap<>();

    private static String[] art(String... rows) {
        return rows;
    }

    private static final String[] HEART = art(
            ".##...##.",
            "#+++.###-",
            "#+#######",
            "#########",
            "-#######-",
            ".-#####-.",
            "..-###-..",
            "...-#-...",
            "....-....");
    private static final String[] SHIELD = art(
            "###+.+###",
            "#+++#####",
            "#+#######",
            "#+######-",
            "#+######-",
            ".#+####-.",
            ".#+####-.",
            "..#+##-..",
            "....#....");
    private static final String[] FLOWER = art(
            "...###...",
            "..#+++#..",
            ".##+#+##.",
            "#++#w#++#",
            "#+#www#+#",
            "#-+#w#+-#",
            ".##-#-##.",
            "..#---#..",
            "...###...");
    private static final String[] SWORD = art(
            ".......w+",
            "......w+#",
            ".....w+#.",
            "....w+#..",
            "#..w+#...",
            ".#w+#....",
            "..##.....",
            ".b.##....",
            "b...#....");
    private static final String[] PENCIL = art(
            "......##.",
            ".....#++#",
            "....##+#-",
            "...#+##-.",
            "..#+##-..",
            ".#+##-...",
            ".w##-....",
            "wk.-.....",
            "k........");
    private static final String[] SKULL = art(
            "..#####..",
            ".#+++++#.",
            "#++++++##",
            "#+kk+kk+#",
            "#+kk+kk+#",
            ".#++k++#.",
            "..#+++#..",
            "..#.#.#..",
            "..-.-.-..");
    private static final String[] TARGET = art(
            "...###...",
            ".##...##.",
            ".#..+..#.",
            "#..+++..#",
            "#.++w++.#",
            "#..+++..#",
            ".#..+..#.",
            ".##...##.",
            "...###...");
    private static final String[] SWORDS = art(
            "w.......w",
            "+#.....#+",
            ".+#...#+.",
            "..+#.#+..",
            "...+#+...",
            "..#+.+#..",
            ".b#...#b.",
            "b.......b",
            ".........");
    private static final String[] SLASHES = art(
            ".....#..#",
            "....#+.#+",
            "...#+.#+.",
            "..#+.#+..",
            ".#+.#+...",
            "#+.#+....",
            "+.#+.....",
            ".#+......",
            ".-.......");
    private static final String[] SWIRL = art(
            "..#####..",
            ".#+...+#.",
            "#+..##.+#",
            "#..#..#.#",
            "#..#.+#.#",
            "#...##..#",
            "#+.....+#",
            ".#+...+#.",
            "..#####..");
    private static final String[] STAR_FOUR = art(
            "....w....",
            "....+....",
            "...+#+...",
            "..+###+..",
            "w+#####+w",
            "..-###-..",
            "...-#-...",
            "....-....",
            "....#....");
    private static final String[] STAR_FIVE = art(
            "....w....",
            "...+#+...",
            "...+#+...",
            "+++###+++",
            ".+#####+.",
            "..#####..",
            ".##-.-##.",
            ".#-...-#.",
            "#.......#");
    private static final String[] CLUB = art(
            "...###...",
            "..#+###..",
            "..#####..",
            ".##.#.##.",
            "#+#####+#",
            "#########",
            ".##.#.##.",
            "....#....",
            "...###...");
    private static final String[] PICK = art(
            ".#####...",
            "#+++++##.",
            ".....b###",
            "....b..##",
            "...b....#",
            "..b.....#",
            ".b.......",
            "b........",
            ".........");
    private static final String[] CLOVER = art(
            "..##.##..",
            ".#++#++#.",
            ".#+###+#.",
            "..#####..",
            ".#+###+#.",
            ".#++#-+#.",
            "..##b##..",
            "....b....",
            "....b....");
    private static final String[] DIAMOND = art(
            ".........",
            "..#####..",
            ".#+w+###.",
            "#+w++###-",
            ".#+++##-.",
            "..#+##-..",
            "...###...",
            "....-....",
            ".........");
    private static final String[] CHECKER = art(
            "###......",
            "#+#......",
            "###......",
            "...###...",
            "...#+#...",
            "...###...",
            "......###",
            "......#+#",
            "......###");
    private static final String[] YIN_YANG = art(
            "..#####..",
            ".#+++###.",
            "#+++k+###",
            "#+++++###",
            "#+++#####",
            "#++####+#",
            "#+####+k#",
            ".#-###--.",
            "..#####..");
    private static final String[] FLAME = art(
            "....#....",
            "...#+....",
            "...#+#...",
            "..#++#.#.",
            ".#+#+##+.",
            ".#+w+#+#.",
            "#+wwww+##",
            ".#+ww+##.",
            "..-###-..");
    private static final String[] SNOW = art(
            "....+....",
            ".+..#..+.",
            "..+.#.+..",
            "...+#+...",
            "+###w###+",
            "...+#+...",
            "..+.#.+..",
            ".+..#..+.",
            "....+....");
    private static final String[] UMBRELLA = art(
            "....w....",
            "..#####..",
            ".#+++###.",
            "#+++####-",
            "#-#-#-#-#",
            "....b....",
            "....b....",
            "..b.b....",
            "..bbb....");
    private static final String[] ALPHA = art(
            ".........",
            ".###..##.",
            "#+++#.#+.",
            "#+..##+..",
            "#+..##...",
            "#+..##+..",
            "#+++#.#+.",
            ".###..##.",
            ".........");
    private static final String[] HEXAGON = art(
            "...###...",
            ".##+++##.",
            "#+++####-",
            "#++#####-",
            "#+######-",
            "#+#####--",
            "#+####---",
            ".##---##.",
            "...###...");
    private static final String[] COMET = art(
            "#.......#",
            ".#.....#.",
            "..#..#...",
            "...###...",
            "..#+w+#..",
            "..#www#..",
            "..#+w+#..",
            "...###...",
            ".........");
    private static final String[] DROP = art(
            "....#....",
            "....#....",
            "...#+#...",
            "..#+++#..",
            ".#+++###.",
            ".#++####.",
            ".#+####-.",
            "..#--##..",
            "...###...");
    private static final String[] ANCHOR = art(
            "...###...",
            "...#.#...",
            "...###...",
            "....#....",
            "..#####..",
            "#...#...#",
            "##..#..##",
            ".##.#.##.",
            "...###...");

    static {
        for (var e : Map.ofEntries(Map.entry("❤", HEART), Map.entry("❈", SHIELD), Map.entry("❂", SHIELD), Map.entry("❁", FLOWER), Map.entry("✎", PENCIL), Map.entry("☠", SKULL),
                Map.entry("☣", TARGET), Map.entry("⚔", SWORDS), Map.entry("⫽", SLASHES), Map.entry("๑", SWIRL), Map.entry("✦", STAR_FOUR), Map.entry("✯", STAR_FIVE), Map.entry("♣", CLUB),
                Map.entry("⸕", PICK), Map.entry("☘", CLOVER), Map.entry("✧", DIAMOND), Map.entry("▚", CHECKER), Map.entry("☯", YIN_YANG), Map.entry("♨", FLAME), Map.entry("❄", SNOW),
                Map.entry("☂", UMBRELLA), Map.entry("α", ALPHA), Map.entry("⬣", HEXAGON), Map.entry("☄", COMET), Map.entry("☉", DROP), Map.entry("⚓", ANCHOR), Map.entry("❣", HEART),
                Map.entry("❦", CLOVER)).entrySet()) {
            BY_SYMBOL.put(e.getKey(), e.getValue());
        }
        // same stats as hypixels resource pack writes them, codes are in skyhanni SkyblockStat
        BY_SYMBOL.put("", SWORD);
        BY_SYMBOL.put("", HEART);
        BY_SYMBOL.put("", HEART);
        BY_SYMBOL.put("", SHIELD);
        BY_SYMBOL.put("", SHIELD);
        BY_SYMBOL.put("", FLOWER);
        BY_SYMBOL.put("", TARGET);
        BY_SYMBOL.put("", SKULL);
        BY_SYMBOL.put("", SWORDS);
        BY_SYMBOL.put("", SLASHES);
        BY_SYMBOL.put("", PENCIL);
        BY_SYMBOL.put("", SWIRL);
        BY_SYMBOL.put("", PICK);
        BY_SYMBOL.put("", PICK);
        BY_SYMBOL.put("", CLOVER);
        BY_SYMBOL.put("", CLOVER);
        BY_SYMBOL.put("", CLOVER);
        BY_SYMBOL.put("", DIAMOND);
        BY_SYMBOL.put("", CHECKER);
        BY_SYMBOL.put("", CHECKER);
        BY_SYMBOL.put("", STAR_FOUR);
        BY_SYMBOL.put("", STAR_FIVE);
        BY_SYMBOL.put("", CLUB);
        BY_SYMBOL.put("", FLAME);
        BY_SYMBOL.put("", SNOW);
        BY_SYMBOL.put("", UMBRELLA);
        BY_SYMBOL.put("", ALPHA);
        BY_SYMBOL.put("", DROP);
        BY_SYMBOL.put("", ANCHOR);
        BY_SYMBOL.put("", COMET);
        BY_NAME.put("damage", SWORD);
        BY_NAME.put("health regen", DROP);
    }

    // picture for a stat by its lore name + symbol, null if none
    static String[] of(String name, String symbol) {
        String[] art = name == null ? null : BY_NAME.get(name.trim().toLowerCase(Locale.ROOT));
        return art != null ? art : BY_SYMBOL.get(symbol);
    }

    private static int mix(int color, int toward, float amount) {
        int r = (color >> 16 & 255), g = (color >> 8 & 255), b = (color & 255);
        int tr = (toward >> 16 & 255), tg = (toward >> 8 & 255), tb = (toward & 255);
        return 0xFF000000 | Math.round(r + (tr - r) * amount) << 16 | Math.round(g + (tg - g) * amount) << 8 | Math.round(b + (tb - b) * amount);
    }

    // draws it with its corner at x, y, one square per screen unit
    static void draw(GuiGraphicsExtractor g, String[] art, int x, int y, int color) {
        int base = 0xFF000000 | color, light = mix(color, 0xFFFFFF, 0.45f), dark = mix(color, 0x000000, 0.42f);
        for (int row = 0; row < art.length; row++) {
            String line = art[row];
            int col = 0;
            while (col < line.length()) {
                char c = line.charAt(col);
                int run = col + 1;
                while (run < line.length() && line.charAt(run) == c) run++;
                if (c != '.') {
                    int fill = switch (c) {
                        case '+' -> light;
                        case '-' -> dark;
                        case 'w' -> 0xFFFFFFFF;
                        case 'k' -> 0xFF14161C;
                        case 'b' -> 0xFF8A5A2B;
                        default -> base;
                    };
                    g.fill(x + col, y + row, x + run, y + row + 1, fill);
                }
                col = run;
            }
        }
    }
}
