// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.garden.milestones;

import io.muzy.sbc.events.hoppity.HoppityEnv;
import io.muzy.sbc.garden.milestones.GardenCard.Block;
import io.muzy.sbc.garden.milestones.GardenCard.Chip;
import io.muzy.sbc.garden.milestones.GardenCard.Line;
import io.muzy.sbc.garden.milestones.GardenCard.Rule;
import io.muzy.sbc.item.enchants.EnchantParser;
import io.muzy.sbc.ui.hud.RowText;
import io.muzy.sbc.ui.theme.Modern;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// lines of the ported milestone, best crop and money displays shaped into card blocks
// display only, the displays stay as they are and a line this doesnt know is shown as is, so nothing gets lost
final class MilestoneCards {
    private static final Pattern TIERS = Pattern.compile("^(.+?) (\\d+)➜(\\d+)$");
    private static final Pattern MAXED = Pattern.compile("^(.+?) MAXED$");
    private static final Pattern PROGRESS = Pattern.compile("^([\\d,]+)/([\\d,]+)$");
    private static final Pattern COUNTER = Pattern.compile("^Counter: (.+)$");
    private static final Pattern TIME_LEFT = Pattern.compile("^In (.+?)( Inaccurate!)?$");
    private static final Pattern PER_MINUTE = Pattern.compile("^Crops/Minute: ([\\d,]+)$");
    private static final Pattern PER_SECOND = Pattern.compile("^Blocks/Second: (.+)$");
    private static final Pattern BEST_TITLE = Pattern.compile("^Best Crop Time \\((.+)\\)$");
    private static final Pattern BEST_RANK = Pattern.compile("^(\\d+)# (.+?) (\\d+)➜(\\d+)\\s+(.+)$");
    private static final Pattern MONEY_TITLE = Pattern.compile("^Money per Hour when selling: \\((.+)\\)$");
    private static final Pattern MONEY_RANK = Pattern.compile("^#(\\d+) (.+?): (.+)$");
    private static final String WATCHED = "§e";

    // the crop's whole road to the last tier, the crop itself comes from the lines
    record Road(long total, long max, int maxTier) {}

    private MilestoneCards() {}

    private static String plain(String s) {
        return RowText.plain(s).trim();
    }

    private static String sep(long n) {
        return String.format(Locale.ROOT, "%,d", n);
    }

    private static long number(String s) {
        try {
            return Long.parseLong(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static String tier(String from, String to) {
        return "§7" + from + " §f→ §b" + to;
    }

    // crop milestone card: crop + tiers, meter with both numbers, then how long and how fast
    // in a compact card time left goes after the tiers and blocks/s before the crops so it pairs with whats left
    static List<Block> progress(List<String> lines, Road road, boolean compact) {
        List<Block> out = new ArrayList<>();
        int tint = 0xFF9CE37D;
        boolean maxTierMode = GardenCropMilestoneDisplay.showMaxTier;
        Line heroLine = null;
        List<Block> last = new ArrayList<>();
        String tiers = "";
        for (String line : lines) {
            String t = plain(line);
            if (t.equals("Crop Milestones")) continue;
            Matcher m;
            if ((m = TIERS.matcher(t)).matches() || (m = MAXED.matcher(t)).matches()) {
                CropType crop = CropType.getByNameOrNull(m.group(1));
                if (crop != null) tint = crop.tint();
                Line hero = new Line(m.group(1)).labelColor(Modern.lighten(tint, 0.25f));
                if (crop != null) hero.icon(crop.icon());
                if (m.groupCount() == 3) hero.value(tiers = tier(m.group(2), m.group(3)));
                out.add(hero);
                heroLine = hero;
            } else if ((m = PROGRESS.matcher(t)).matches()) {
                long have = number(m.group(1)), need = number(m.group(2));
                double p = need > 0 ? Math.min(1, (double) have / need) : 0;
                out.add(new Line(Modern.fraction(m.group(1), m.group(2), 'e', 'f')).value(RowText.percent(p), Modern.INK).meter(p, tint));
                if (need > have) out.add(new Line("To go").value(sep(need - have), Modern.INK));
            } else if ((m = COUNTER.matcher(t)).matches()) {
                out.add(new Line("Counter").value(m.group(1), Modern.GOLD));
            } else if ((m = TIME_LEFT.matcher(t)).matches()) {
                if (compact && heroLine != null && m.group(2) == null) {
                    heroLine.value(tiers + "  §b" + m.group(1));
                    continue;
                }
                Line left = new Line("Time left").value(m.group(1), Modern.AQUA);
                if (m.group(2) != null) left.chip("INACCURATE", Modern.WARN);
                (compact ? last : out).add(left);
            } else if ((m = PER_MINUTE.matcher(t)).matches()) {
                long perMinute = number(m.group(1));
                (compact ? last : out).add(new Line("Crops").value("§f" + sep(perMinute / 60) + "§7/s  §f" + m.group(1) + "§7/min  §f" + sep(perMinute * 60) + "§7/h"));
            } else if ((m = PER_SECOND.matcher(t)).matches()) {
                out.add(compact ? new Line("BPS").value("§f" + m.group(1)) : new Line("Blocks").value("§f" + m.group(1) + "§7/s"));
            } else if (!t.isEmpty()) {
                out.add(new Line(line).labelColor(Modern.WARN));
            }
        }
        out.addAll(last);
        if (road != null && road.max() > 0 && !maxTierMode && road.total() < road.max()) {
            double p = (double) road.total() / road.max();
            if (!compact) out.add(new Rule());
            out.add(new Line("To tier " + road.maxTier()).value(Modern.fraction(EnchantParser.shortFormat(road.total()), EnchantParser.shortFormat(road.max()), 'f', '7') + "  §f" + RowText.percent(p)).meter(p, Modern.darken(tint, 0.15f)));
        }
        return out;
    }

    // ranking kind, for the band
    static String bestKind(List<String> lines) {
        Matcher m = lines.isEmpty() ? null : BEST_TITLE.matcher(plain(lines.get(0)));
        return m != null && m.matches() ? "§b" + m.group(1) : "";
    }

    // rank column width
    private static int rankColumn(List<String> lines, Pattern rank) {
        for (String line : lines) {
            Matcher m = rank.matcher(plain(line));
            if (m.matches() && m.group(1).length() > 1) return 22;
        }
        return 16;
    }

    // best crop time: rank, crop, tiers, time, your current crop picked out. quickest gets the full bar, slower ones less
    static List<Block> best(List<String> lines, boolean compact) {
        List<Block> out = new ArrayList<>();
        long shortest = Long.MAX_VALUE;
        int column = rankColumn(lines, BEST_RANK);
        for (String line : lines) {
            Matcher m = BEST_RANK.matcher(plain(line));
            if (!m.matches()) continue;
            Long ms = HoppityEnv.duration(m.group(5));
            if (ms != null && ms > 0) shortest = Math.min(shortest, ms);
        }
        for (String line : lines) {
            String t = plain(line);
            if (BEST_TITLE.matcher(t).matches()) continue;
            Matcher m = BEST_RANK.matcher(t);
            if (!m.matches()) {
                if (!t.isEmpty()) out.add(new Line(line));
                continue;
            }
            boolean farmed = line.contains(WATCHED);
            CropType crop = CropType.getByNameOrNull(m.group(2));
            int rank = Integer.parseInt(m.group(1));
            Line row = new Line((farmed ? "§e" : "§f") + (line.contains("§n") ? "§n" : "") + m.group(2) + " §7" + m.group(3) + "→" + m.group(4))
                    .rank("#" + rank, rank == 1 ? Modern.GOLD : Modern.DIM, column)
                    .value(m.group(5), Modern.AQUA);
            // the line says the crop, a compact card keeps the room
            if (crop != null && !compact) row.icon(crop.icon());
            Long ms = HoppityEnv.duration(m.group(5));
            if (ms != null && ms > 0) row.share((double) shortest / ms, 0);
            out.add(row);
        }
        return out;
    }

    // selling kind, for the band
    static String moneyKind(List<String> lines) {
        Matcher m = lines.isEmpty() ? null : MONEY_TITLE.matcher(plain(lines.get(0)));
        return m != null && m.matches() ? "§e" + m.group(1) : "";
    }

    // what a coin text is worth, only for the bars: "1.2M", "842.5k", "1,234,567". -1 for anything else
    static double coins(String s) {
        String t = s.replace(",", "").trim();
        if (t.isEmpty()) return -1;
        double mul = switch (Character.toLowerCase(t.charAt(t.length() - 1))) {
            case 'k' -> 1e3;
            case 'm' -> 1e6;
            case 'b' -> 1e9;
            case 't' -> 1e12;
            default -> 1;
        };
        if (mul != 1) t = t.substring(0, t.length() - 1);
        try {
            return Double.parseDouble(t) * mul;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    // which item a bazaar form of a crop shows as
    static String formIcon(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        String[][] forms = {{"brown mushroom", "brown_mushroom"}, {"red mushroom", "red_mushroom"}, {"mushroom", "red_mushroom"}, {"golden carrot", "golden_carrot"}, {"carrot", "carrot"},
                {"baked potato", "baked_potato"}, {"potato", "potato"}, {"wart", "nether_wart"}, {"nether stalk", "nether_wart"}, {"pumpkin", "pumpkin"}, {"melon block", "melon"}, {"melon", "melon_slice"},
                {"cocoa", "cocoa_beans"}, {"sugar cane", "sugar_cane"}, {"sugar", "sugar"}, {"cactus green", "green_dye"}, {"cactus", "cactus"}, {"hay", "hay_block"}, {"wheat", "wheat"}, {"seeds", "wheat_seeds"}};
        for (String[] f : forms) if (n.contains(f[0])) return "minecraft:" + f[1];
        return null;
    }

    // money per hour: rank, item + icon, coins. bars compare each form to the best
    static List<Block> money(List<String> lines, boolean compact) {
        List<Block> out = new ArrayList<>();
        double best = 0;
        int column = rankColumn(lines, MONEY_RANK);
        for (String line : lines) {
            Matcher m = MONEY_RANK.matcher(plain(line));
            if (m.matches()) best = Math.max(best, coins(m.group(3)));
        }
        for (String line : lines) {
            String t = plain(line);
            if (MONEY_TITLE.matcher(t).matches()) continue;
            Matcher m = MONEY_RANK.matcher(t);
            if (!m.matches()) {
                if (!t.isEmpty()) out.add(new Line(line));
                continue;
            }
            boolean farmed = line.contains(WATCHED);
            int rank = Integer.parseInt(m.group(1));
            String mark = line.contains("§n") ? "§n" : "";
            Line row = new Line((farmed ? "§e" : "§f") + mark + m.group(2))
                    .brief((farmed ? "§e" : "§f") + mark + m.group(2).replace("Enchanted ", "Ench "))
                    .rank("#" + rank, rank == 1 ? Modern.GOLD : Modern.DIM, column)
                    .value(m.group(3), Modern.GOLD);
            String icon = formIcon(m.group(2));
            if (icon != null && !compact) row.icon(icon);
            double worth = coins(m.group(3));
            if (best > 0 && worth > 0) row.share(worth / best, Modern.GOLD);
            out.add(row);
        }
        return out;
    }

    // milestone card state: crop done, or numbers need the menu first
    static Chip state(List<String> lines) {
        for (String line : lines) {
            String t = plain(line);
            if (MAXED.matcher(t).matches()) return new Chip("MAXED", Modern.GOLD);
            if (t.startsWith("Open /cropmilestones") || t.startsWith("Missing Milestone")) return new Chip("UPDATE", Modern.WARN);
        }
        return null;
    }
}
