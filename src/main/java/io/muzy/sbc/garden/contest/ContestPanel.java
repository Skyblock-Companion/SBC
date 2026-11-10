// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.contest;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyItems;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.garden.GardenItems;
import io.muzy.sbc.garden.contest.Contests.Bracket;
import io.muzy.sbc.garden.milestones.CropType;
import io.muzy.sbc.item.menus.MenuMarks;
import io.muzy.sbc.item.menus.MenuView;
import io.muzy.sbc.ui.SideHelper;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.theme.Modern;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// next to Your Contests. from skyhanni JacobContestTimeNeeded (time per crop to the medal you pick) +
// JacobContestFFNeededDisplay (fortune each medal took in the contest under your mouse, and the average)
public final class ContestPanel implements SideHelper.Content {
    public static final String FEATURE = "jacob_contest_helper";
    public static final ContestPanel I = new ContestPanel();
    private static final int ROW = 11;

    private Bracket bracket = Bracket.GOLD;

    private ContestPanel() {}

    public static SideHelper.Content of(Screen s) {
        return Features.on(FEATURE) && Contests.inYourContests() ? I : null;
    }

    private static Option option(String key) {
        Feature f = Features.get(FEATURE);
        return f == null ? null : f.option(key);
    }

    private static boolean bool(String key) {
        Option o = option(key);
        return o == null || o.bool();
    }

    @Override
    public String title() {
        return "Contest Helper";
    }

    @Override
    public ItemStack icon() {
        return LegacyItems.stack("minecraft:golden_hoe");
    }

    @Override
    public int accent() {
        return 0xFFFFD166;
    }

    @Override
    public String[] tabs() {
        boolean time = bool("jacob_contest_helper.times"), ff = bool("jacob_contest_helper.ff");
        if (time && ff) return new String[]{"Time needed", "Fortune needed"};
        return new String[]{time ? "Time needed" : "Fortune needed"};
    }

    @Override
    public void refresh(AbstractContainerScreen<?> screen) {
        MenuMarks.current();
    }

    @Override
    public int summary(Ui ui, int x, int y, int w) {
        int cx = x;
        for (Bracket b : Bracket.values()) {
            String name = b.name().charAt(0) + b.name().substring(1).toLowerCase(Locale.ROOT);
            int bw = ui.width(name) + 4;
            if (cx + bw > x + w) break;
            boolean on = b == bracket;
            ui.rect(cx, y, bw, 11, on ? 0x66FFFFFF : 0x44000000);
            ui.textShadow("§" + b.color + name, cx + 2, y + 2, Modern.INK);
            final Bracket chosen = b;
            ui.click(cx, y, bw, 11, () -> bracket = chosen);
            cx += bw + 1;
        }
        return 13;
    }

    // CropType.getBps of JacobContestTimeNeeded
    private static Double bps(CropType crop) {
        Option custom = option("jacob_contest_helper.custom_bps");
        if (custom != null && custom.bool()) {
            Option value = option("jacob_contest_helper.custom_bps_value");
            return value == null ? 19.9 : value.number();
        }
        return Contests.latestBps(crop);
    }

    // one crop: what testCrop + renderCrop say, the line and its tooltip
    private record TimeLine(CropType crop, long sortBy, String text, List<String> tip) {}

    static long timeNeeded(double amount, int speed) {
        return speed <= 0 ? Long.MAX_VALUE : (long) (amount / speed * 1000);
    }

    private TimeLine timeLine(CropType crop) {
        Double bps = bps(crop);
        if (bps == null) return new TimeLine(crop, Long.MAX_VALUE, "§cNo speed!", List.of("§cFarm " + crop.cropName + " to show data!"));
        Double ff = Contests.latestFF(crop);
        if (ff == null) return new TimeLine(crop, Long.MAX_VALUE, "§cNo FF data!", List.of("§cHold a " + crop.cropName + " specific", "§cfarming tool in hand to show data!"));
        Map<Bracket, Integer> averages = Contests.averages(crop).brackets();
        if (averages.isEmpty()) return new TimeLine(crop, Long.MAX_VALUE - 2, "§cNo contests!", List.of("§cOpen more pages or participate", "§cin a " + crop.cropName + " Contest to show data!"));
        int speed = (int) Math.round(ff * crop.baseDrops * bps / 100);
        // renderCrop: whats left of a low speed is shown at 19.9 blocks/s
        int formula = bps < 15 ? (int) (speed / bps * 19.9) : speed;
        List<String> tip = new ArrayList<>();
        tip.add("§7Time Needed for §9" + crop.cropName + " Medals§7:");
        String text = "§cNot revealed!";
        long sort = Long.MAX_VALUE - 1;
        for (Bracket b : Bracket.values()) {
            Integer amount = averages.get(b);
            if (amount == null) {
                tip.add(b.display() + " §cBracket not revealed!");
                continue;
            }
            long time = timeNeeded(amount, formula);
            boolean possible = time < 20 * 60_000L;
            String line = b.display() + " " + (possible ? "§b" : "§c") + GardenItems.time(time);
            double usedBps = bps < 15 ? 19.9 : bps;
            if (!possible) {
                double needed = amount / usedBps / 60 * 100 / 20 / crop.baseDrops;
                line += " §7(Need " + String.format(Locale.ROOT, "%,d", (int) (needed - ff)) + " FF more)";
            }
            tip.add(line);
            if (b == bracket) {
                sort = time;
                text = possible ? "§7in §b" + GardenItems.time(time) + (bps < 15 ? " §4§l!" : "") : "§cNo " + b.display() + " §cmedal!" + (bps < 15 ? " §4§l!" : "");
            }
        }
        tip.add("");
        tip.add("§7Latest FF: §e" + String.format(Locale.ROOT, "%,.0f", ff));
        tip.add("§7" + (option("jacob_contest_helper.custom_bps") != null && option("jacob_contest_helper.custom_bps").bool() ? "Custom" : "Your") + " Blocks/Second: §e" + String.format(Locale.ROOT, "%.1f", bps));
        if (bps < 15) {
            tip.add("§cYour Blocks/second is too low,");
            tip.add("§cshowing 19.9 Blocks/second instead!");
        }
        return new TimeLine(crop, sort, text, tip);
    }

    private int timeList(Ui ui, int x, int y, int w) {
        int y0 = y;
        List<TimeLine> lines = new ArrayList<>();
        for (CropType crop : CropType.values()) lines.add(timeLine(crop));
        lines.sort((a, b) -> Long.compare(a.sortBy(), b.sortBy()));
        for (TimeLine l : lines) {
            boolean hover = ui.hover(x, y, w, ROW);
            if (hover) {
                ui.rect(x, y, w, ROW, 0x22FFFFFF);
                ui.tooltip(l.tip().toArray(new String[0]));
            }
            ui.icon(l.crop().icon(), x, y, 0.625f);
            ui.textShadow(l.text(), x + w - ui.width(l.text()), y + 1, Modern.INK);
            ui.textShadow(ui.ellipsize("§9" + l.crop().cropName, w - 14 - ui.width(l.text())), x + 12, y + 1, Modern.INK);
            y += ROW;
        }
        return y - y0;
    }

    // getRealBlocksPerSecond
    private static double realBps(CropType crop) {
        Double bps = Contests.latestBps(crop);
        double b = bps == null ? 20.0 : bps;
        return b < 15 ? 19.9 : b;
    }

    // getLine: fortune that reaches this many crops
    static long fortuneFor(int crops, double blocksPerSecond, double baseDrops) {
        double cropsPerSecond = crops / blocksPerSecond / 60;
        double ff = cropsPerSecond * 100 / 20 / baseDrops - 100;
        return (long) Math.ceil(Math.max(0, ff));
    }

    private static int bracketLines(Ui ui, Map<Bracket, Integer> map, CropType crop, int x, int y, int w) {
        int y0 = y;
        for (Bracket b : Bracket.values()) {
            Integer crops = map.get(b);
            String right = crops == null ? "§8Not found!" : "§6" + String.format(Locale.ROOT, "%,d", fortuneFor(crops, realBps(crop), crop.baseDrops)) + " FF §7" + String.format(Locale.ROOT, "%,d", crops);
            ui.textShadow(b.display(), x, y + 1, Modern.INK);
            ui.textShadow(right, x + w - ui.width(right), y + 1, Modern.INK);
            y += ROW;
        }
        return y - y0;
    }

    private int fortuneList(Ui ui, int x, int y, int w) {
        int y0 = y;
        MenuView v = MenuMarks.current();
        int slot = MenuMarks.hoveredSlot();
        Contests.Contest contest = null;
        if (v != null && slot >= 0 && slot < v.top) {
            Long time = Contests.timeFor(v.item(slot).clean);
            if (time != null) contest = Contests.contestAt(time);
        }
        if (contest == null) {
            y += SideHelper.emptyText(ui, "Hover over a contest.", x, y, w);
            return y - y0;
        }
        CropType crop = contest.crop();
        ui.icon(crop.icon(), x, y, 0.625f);
        ui.textShadow(ui.ellipsize("§7For this " + crop.cropName + " contest:", w - 12), x + 12, y + 1, Modern.INK);
        y += ROW;
        y += bracketLines(ui, contest.brackets(), crop, x, y, w);
        Contests.Averages averages = Contests.averages(crop);
        ui.textShadow(ui.ellipsize("§7For the last §e" + averages.size() + " §7of them:", w), x, y + 3, Modern.INK);
        y += ROW + 2;
        y += bracketLines(ui, averages.brackets(), crop, x, y, w);
        y += 2;
        Double bps = Contests.latestBps(crop);
        if (bps == null) {
            ui.textShadow(ui.ellipsize("§cNo blocks/second data, assuming 19.9.", w), x, y + 1, Modern.INK);
            y += ROW;
        } else if (bps < 15) {
            ui.textShadow(ui.ellipsize("§cYour blocks/second: §e" + String.format(Locale.ROOT, "%.2f", bps) + "§c, too low: 19.9", w), x, y + 1, Modern.INK);
            y += ROW;
            bps = 19.9;
        }
        Double ff = Contests.latestFF(crop);
        if (ff == null) {
            ui.textShadow(ui.ellipsize("§cNo latest fortune saved!", w), x, y + 1, Modern.INK);
            y += ROW;
        } else {
            String your = "§6" + String.format(Locale.ROOT, "%,d", (long) Math.ceil(ff));
            ui.textShadow("§6Your latest FF", x, y + 1, Modern.INK);
            ui.textShadow(your, x + w - ui.width(your), y + 1, Modern.INK);
            y += ROW;
        }
        if (bps != null && ff != null) {
            String predicted = "§6" + String.format(Locale.ROOT, "%,d", predicted(ff, bps, crop.baseDrops));
            ui.textShadow("§6Predicted crops", x, y + 1, Modern.INK);
            ui.textShadow(predicted, x + w - ui.width(predicted), y + 1, Modern.INK);
            y += ROW;
        }
        return y - y0;
    }

    // predictedScore
    static int predicted(double ff, double bps, double baseDrops) {
        return (int) ((100.0 + ff) * bps * baseDrops * 20 * 60 / 100);
    }

    @Override
    public int list(Ui ui, int tab, int x, int y, int w) {
        boolean time = bool("jacob_contest_helper.times"), ff = bool("jacob_contest_helper.ff");
        boolean showTime = time && (!ff || tab == 0);
        return showTime ? timeList(ui, x, y, w) : fortuneList(ui, x, y, w);
    }

    static void register() {
        Features.add(new Feature(FEATURE, "Garden", "Contest helper", "Beside Your Contests: how long each crop takes to the medal you pick, at your fortune and speed, and the fortune every medal of the contest under your mouse took. Open more pages for more contests.", "SkyHanni: Contest Time Needed, FF for Contest")
                .when(() -> GameState.inSkyBlock)
                .option(Option.bool("jacob_contest_helper.times", "Time needed", "The time and missing fortune for every crop.", true))
                .option(Option.bool("jacob_contest_helper.ff", "Fortune needed", "The fortune of each medal for the contest under your mouse.", true))
                .option(Option.bool("jacob_contest_helper.custom_bps", "Custom blocks per second", "Work the time out with this speed instead of your real one.", true))
                .option(Option.number("jacob_contest_helper.custom_bps_value", "Custom blocks per second value", "", 19.9, 1, 20, 0.1)));
    }
}
