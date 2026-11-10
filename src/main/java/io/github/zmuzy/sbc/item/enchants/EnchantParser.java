// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.item.enchants;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// from skyhanni EnchantParser (which is from skyblockaddons): finds the enchant block in the lore, orders
// them (ultimates, stacking, then abc)
// colors each by level vs Enchants.json (perfect / great / good / poor, ultimates bold pink)
// relays them: NORMAL keeps hypixels count per line, COMPRESSED = 3 per line, STACKED = 1 per line
// keeps or hides the description lines, adds the stacking progress line. pure, works on formatted lore strings
public final class EnchantParser {
    private EnchantParser() {}

    public enum Format { NORMAL, COMPRESSED, STACKED }

    // skyhanni EnchantParsingConfig defaults (chroma perfect color falls back to bold gold)
    public static final class Options {
        public boolean colorParsing = true;
        public Format format = Format.NORMAL;
        public String ultimateColor = "§d", perfectColor = "§6", greatColor = "§6", goodColor = "§9", poorColor = "§7";
        // skyhanni: perfect = CHROMA, which draws as bold gold without the chroma shader
        public boolean perfectIsChroma = true, boldPerfectEnchant = false;
        public boolean hideEnchantDescriptions = false, stackingEnchantProgress = true;
        public boolean isRoman = true;
    }

    public static final Patterns.P EXCLUSIVE = Patterns.p("SH.misc.items.enchantparsing.exclusive");
    public static final Patterns.P ENCHANT = Patterns.p("SH.misc.items.enchantparsing.enchants.new");
    private static final Pattern STACKING_NUMBER = Pattern.compile("[\\d,.kKmMbB]+$");

    // from skyhanni FormattedEnchant
    static final class FormattedEnchant implements Comparable<FormattedEnchant> {
        final EnchantsData.Enchant enchant;
        final int level;
        final String stacking;
        final List<String> lore = new ArrayList<>();

        FormattedEnchant(EnchantsData.Enchant enchant, int level, String stacking) {
            this.enchant = enchant;
            this.level = level;
            this.stacking = stacking;
        }

        @Override
        public int compareTo(FormattedEnchant o) {
            return enchant.compareTo(o.enchant);
        }

        String text(Options opt, String internalName, boolean miningTool) {
            String text = enchant.loreName + " " + (opt.isRoman ? roman(level) : String.valueOf(level));
            String style = style(opt, internalName, miningTool);
            String out = style + text;
            if (!stacking.equals("empty")) out += "§7" + stacking;
            return out;
        }

        // skyhanni Enchant.getStyle / Ultimate.getStyle / Dummy, color codes instead of Styles
        String style(Options opt, String internalName, boolean miningTool) {
            if (enchant.kind == EnchantsData.Kind.DUMMY) return "§9";
            if (enchant.isUltimate()) return opt.ultimateColor + "§l";
            // skyhanni checkExceptions: efficiency on a stonk, or eff 5 on a non mining tool (not the promising shovel), is perfect
            boolean perfect = level >= enchant.maxLevel;
            if (enchant.nbtName.equals("efficiency") && ("STONK_PICKAXE".equals(internalName) || (level == 5 && !miningTool && !"PROMISING_SHOVEL".equals(internalName)))) perfect = true;
            if (perfect) return opt.perfectIsChroma ? "§6§l" : opt.perfectColor + (opt.boldPerfectEnchant ? "§l" : "");
            if (level > enchant.goodLevel) return opt.greatColor;
            if (level == enchant.goodLevel) return opt.goodColor;
            return opt.poorColor;
        }
    }

    // marks a color legacy codes cant say, rgbLine turns it into a real one when the line becomes a component
    private static final char RGB_MARK = '\uE000';

    // a color as text in place of a color code
    public static String rgb(int argb) {
        return RGB_MARK + String.format("%06X", argb & 0xFFFFFF);
    }

    // formatted line as a component. marked stuff keeps its own color till the next color code
    public static net.minecraft.network.chat.Component rgbLine(String line) {
        int at = line.indexOf(RGB_MARK);
        if (at < 0) return net.minecraft.network.chat.Component.literal(line);
        net.minecraft.network.chat.MutableComponent out = net.minecraft.network.chat.Component.literal(line.substring(0, at));
        while (at >= 0 && at + 7 <= line.length()) {
            int color = Integer.parseInt(line.substring(at + 1, at + 7), 16);
            int end = at + 7;
            while (end < line.length() && line.charAt(end) != RGB_MARK && !(line.charAt(end) == '§' && end + 1 < line.length() && "0123456789abcdefr".indexOf(line.charAt(end + 1)) >= 0)) end++;
            out.append(net.minecraft.network.chat.Component.literal(line.substring(at + 7, end)).withColor(color));
            int next = line.indexOf(RGB_MARK, end);
            if (next < 0) {
                out.append(net.minecraft.network.chat.Component.literal(line.substring(end)));
                break;
            }
            out.append(net.minecraft.network.chat.Component.literal(line.substring(end, next)));
            at = next;
        }
        return out;
    }

    public static String roman(int n) {
        if (n <= 0 || n > 3999) return String.valueOf(n);
        int[] v = {1000, 900, 500, 400, 100, 90, 50, 40, 10, 9, 5, 4, 1};
        String[] s = {"M", "CM", "D", "CD", "C", "XC", "L", "XL", "X", "IX", "V", "IV", "I"};
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) while (n >= v[i]) {
            n -= v[i];
            sb.append(s[i]);
        }
        return sb.toString();
    }

    // from skyhanni romanToDecimalIfNecessary
    public static int level(String numeral) {
        if (numeral.chars().allMatch(Character::isDigit)) return Integer.parseInt(numeral);
        int total = 0, prev = 0;
        for (int i = numeral.length() - 1; i >= 0; i--) {
            int v = switch (numeral.charAt(i)) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                case 'L' -> 50;
                case 'C' -> 100;
                case 'D' -> 500;
                case 'M' -> 1000;
                default -> 0;
            };
            if (v < prev) total -= v;
            else total += v;
            prev = v;
        }
        return total;
    }

    // skyhanni EnchantsJson.containsEnchantment: a lore line made of only enchants, at least one of them on the item
    static boolean containsEnchantment(Map<String, Integer> enchants, String line) {
        if (!EXCLUSIVE.matcher(line).find()) return false;
        Matcher m = ENCHANT.matcher(line);
        while (m.find()) {
            EnchantsData.Enchant e = EnchantsData.getFromLore(m.group("enchant"));
            if (!enchants.isEmpty()) {
                if (enchants.containsKey(e.nbtName)) return true;
            } else if (EnchantsData.isKnown(e.loreName.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    // skyhanni parseEnchants on formatted lore. lore = formatted lines without the name line
    // enchants = nbt enchants (name -> level), empty for /show hovers. internalName = skyblock id ("" if unknown)
    // isBook = enchanted book, miningTool = pickaxe/drill/gauntlet, progress = stacking nbt key -> value, can be null
    // returns the new lore, or the same list if nothing changed
    public static List<String> parse(List<String> lore, Map<String, Integer> enchants, String internalName, boolean isBook, boolean miningTool,
                                     Map<String, Double> progress, Options opt) {
        if (!EnchantsData.hasEnchantData()) return lore;
        // from skyhanni enchantStartAndEnd
        int startEnchant = -1, endEnchant = -1;
        for (int i = 0; i < lore.size(); i++) {
            String stripped = GameState.strip(lore.get(i));
            if (startEnchant == -1) {
                if (containsEnchantment(enchants, lore.get(i))) startEnchant = i;
            } else if (stripped.trim().isEmpty() && endEnchant == -1) endEnchant = i - 1;
        }
        if (startEnchant != -1 && endEnchant == -1) endEnchant = lore.size() - 1;
        if (endEnchant == -1) return lore;

        // from skyhanni orderEnchants
        List<EnchantsData.Enchant> stackingEnchants = new ArrayList<>();
        boolean shouldBeSingleColumn = false;
        List<String> loreLines = new ArrayList<>();
        TreeSet<FormattedEnchant> ordered = new TreeSet<>();
        int maxEnchantsPerLine = 0;
        FormattedEnchant lastEnchant = null;
        for (int i = startEnchant; i <= endEnchant; i++) {
            Matcher m = ENCHANT.matcher(lore.get(i).replace("\n", ""));
            boolean containsEnchant = false;
            int enchantsOnThisLine = 0;
            while (m.find()) {
                EnchantsData.Enchant enchant = EnchantsData.getFromLore(m.group("enchant"));
                int level = level(m.group("levelNumeral"));
                String stackingGroup = m.group("stacking");
                String stacking;
                if (STACKING_NUMBER.matcher(GameState.strip(stackingGroup.stripLeading())).matches()) {
                    shouldBeSingleColumn = true;
                    stacking = stackingGroup;
                } else stacking = "empty";
                if (enchant.isStacking()) stackingEnchants.add(enchant);
                lastEnchant = new FormattedEnchant(enchant, level, stacking);
                if (!ordered.add(lastEnchant)) {
                    for (FormattedEnchant f : ordered) if (f.compareTo(lastEnchant) == 0) {
                        lastEnchant = f;
                        break;
                    }
                }
                containsEnchant = true;
                enchantsOnThisLine++;
            }
            maxEnchantsPerLine = Math.max(maxEnchantsPerLine, enchantsOnThisLine);
            if (!containsEnchant && lastEnchant != null) {
                lastEnchant.lore.add(lore.get(i));
                loreLines.add(lore.get(i));
            }
        }
        if (ordered.isEmpty()) return lore;

        List<String> out = new ArrayList<>(lore);
        if (!opt.colorParsing) {
            if (opt.hideEnchantDescriptions && !isBook) out.removeAll(loreLines);
            return out;
        }
        List<String> insert = new ArrayList<>();
        if (opt.format == Format.NORMAL) normalFormatting(insert, ordered, maxEnchantsPerLine, isBook, internalName, miningTool, opt);
        else if (opt.format == Format.COMPRESSED && !shouldBeSingleColumn) compressedFormatting(insert, ordered, maxEnchantsPerLine, isBook, internalName, miningTool, opt);
        else stackedFormatting(insert, ordered, isBook, internalName, miningTool, opt);
        out.subList(startEnchant, endEnchant + 1).clear();
        out.addAll(startEnchant, insert);
        if (opt.stackingEnchantProgress && progress != null) {
            for (EnchantsData.Enchant stacking : stackingEnchants) {
                String p = progressString(stacking, progress.getOrDefault(stacking.nbtNum, 0.0));
                if (!p.isEmpty()) out.add(Math.max(0, out.size() - 1), p);
            }
        }
        return out;
    }

    private static void normalFormatting(List<String> insert, TreeSet<FormattedEnchant> ordered, int maxPerLine, boolean isBook, String internalName, boolean miningTool, Options opt) {
        StringBuilder line = new StringBuilder();
        FormattedEnchant last = ordered.last();
        int i = 0;
        int per = Math.max(1, maxPerLine);
        for (FormattedEnchant e : ordered) {
            boolean notLastOnLine = i % per != per - 1 && e != last;
            line.append(e.text(opt, internalName, miningTool));
            if (notLastOnLine) line.append("§9, ");
            else {
                insert.add(line.toString());
                if (!opt.hideEnchantDescriptions || isBook) insert.addAll(e.lore);
                line = new StringBuilder();
            }
            i++;
        }
        if (!line.isEmpty()) insert.add(line.toString());
    }

    private static void compressedFormatting(List<String> insert, TreeSet<FormattedEnchant> ordered, int maxPerLine, boolean isBook, String internalName, boolean miningTool, Options opt) {
        StringBuilder line = new StringBuilder();
        FormattedEnchant last = ordered.last();
        int i = 0;
        for (FormattedEnchant e : ordered) {
            boolean notLastOnLine = i % 3 != 2 && e != last;
            line.append(e.text(opt, internalName, miningTool));
            if (isBook && maxPerLine == 1) {
                insert.add(line.toString());
                insert.addAll(e.lore);
                line = new StringBuilder();
            } else if (notLastOnLine) line.append("§9, ");
            else {
                insert.add(line.toString());
                line = new StringBuilder();
            }
            i++;
        }
        if (!line.isEmpty()) insert.add(line.toString());
    }

    private static void stackedFormatting(List<String> insert, TreeSet<FormattedEnchant> ordered, boolean isBook, String internalName, boolean miningTool, Options opt) {
        for (FormattedEnchant e : ordered) {
            insert.add(e.text(opt, internalName, miningTool));
            if (!opt.hideEnchantDescriptions || isBook) insert.addAll(e.lore);
        }
    }

    // from skyhanni Enchant.Stacking.progressString
    static String progressString(EnchantsData.Enchant stacking, double rawProgress) {
        String label = splitCamelCase(stacking.statLabel);
        if (!label.isEmpty()) label = Character.toUpperCase(label.charAt(0)) + label.substring(1);
        label = label.replace("Xp", "XP");
        int progress = (int) Math.round(rawProgress);
        if (progress == 0) return "";
        Integer next = null;
        for (int l : stacking.stackLevel) if (l > progress && (next == null || l < next)) next = l;
        String tail = next == null ? "(Maxed)" : "/ " + shortFormat(next);
        return "§7" + label + ": §c" + shortFormat(progress) + " §7" + tail;
    }

    static String splitCamelCase(String s) {
        return s.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
    }

    // skyhanni NumberUtil.shortFormat (compactFormat): 1 decimal below 100 of the unit (k, M, B), none above
    public static String shortFormat(double v) {
        double abs = Math.abs(v);
        if (abs < 1) return String.valueOf(v);
        long value = (long) v;
        if (value < 0) return "-" + shortFormat(-value);
        if (value < 1000) return String.valueOf(value);
        long[] divs = {1_000_000_000_000_000_000L, 1_000_000_000_000_000L, 1_000_000_000_000L, 1_000_000_000L, 1_000_000L, 1000L};
        String[] sufs = {"E", "P", "T", "B", "M", "k"};
        long divideBy = 1000;
        String suffix = "k";
        for (int i = 0; i < divs.length; i++) if (value >= divs[i]) {
            divideBy = divs[i];
            suffix = sufs[i];
            break;
        }
        long truncated = value / (divideBy / 10);
        long truncatedAt = suffix.equals("M") ? 1000 : suffix.equals("B") ? 1_000_000 : 100;
        boolean hasDecimal = truncated < truncatedAt && truncated / 10.0 != (double) (truncated / 10);
        return hasDecimal ? (truncated / 10.0) + suffix : (truncated / 10) + suffix;
    }

}
