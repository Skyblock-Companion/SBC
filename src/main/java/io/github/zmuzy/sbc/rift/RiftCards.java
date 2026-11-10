// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.rift;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.events.hoppity.HoppityEnv;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;
import io.github.zmuzy.sbc.ui.Ui;
import io.github.zmuzy.sbc.ui.hud.Compact;
import io.github.zmuzy.sbc.ui.hud.HudElement;
import io.github.zmuzy.sbc.ui.hud.RowText;
import io.github.zmuzy.sbc.ui.theme.Modern;
import io.github.zmuzy.sbc.ui.theme.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.github.zmuzy.sbc.rift.RichCard.Line;

// the rift cards: time left, crux talisman, vermin, punchcard, ubiks cube, each readable at a glance
final class RiftCards {
    private RiftCards() {}

    private static List<HudElement.Row> lineRows(List<String> lines) {
        List<HudElement.Row> out = new ArrayList<>();
        for (String l : lines) out.add(new HudElement.Row(l));
        return out;
    }

    private static int sum(Map<?, Integer> m) {
        int n = 0;
        for (int v : m.values()) n += v;
        return n;
    }

    // --- rift time ---

    // same thresholds as the timer text: blue, yellow from 5 min, red from 1
    private static int timeColor(long ms) {
        return ms <= 60_000 ? Modern.BAD : ms <= 5 * 60_000 ? Modern.GOLD : Modern.AQUA;
    }

    private static String timeCode(long ms) {
        return ms <= 60_000 ? "§c" : ms <= 5 * 60_000 ? "§e" : "§b";
    }

    private static RichCard.Content timer(long now, long max, List<String> changes, boolean showPercent, boolean showMax) {
        List<Line> out = new ArrayList<>();
        double p = max > 0 ? (double) now / max : 0;
        out.add(Line.bar("Time meter", p, timeColor(now)));
        String left = showMax ? "§7Max §b" + HoppityEnv.format(max) : "";
        StringBuilder right = new StringBuilder();
        for (String c : changes) right.append(c).append("  ");
        if (showPercent) right.append("§f").append(String.format(Locale.ROOT, "%.1f%%", p * 100));
        if (!left.isEmpty() || right.length() > 0) out.add(Line.row(left, right.toString().trim()).key("Maximum, changes and percent").raw());
        return new RichCard.Content(timeCode(now) + HoppityEnv.format(now), out);
    }

    static HudElement timerCard() {
        return new RichCard("rift_timer", "Rift Timer", "Rift", Zone.TOP_LEFT, 50, Rift.TIMER) {
            @Override public boolean visible() {
                return Rift.rift() && !RiftTimer.display().isEmpty();
            }

            // the number you watch in the rift, bigger than stuff you read in passing
            @Override public float defaultScale() {
                return HudLayout.largeScale();
            }

            @Override public String heading() {
                return title;
            }

            @Override protected Content live() {
                List<String> d = RiftTimer.display();
                return timer(RiftTimer.currentTime, RiftTimer.maxTime, d.size() > 1 ? d.subList(1, d.size()) : List.of(), RiftTimer.showPercentage, RiftTimer.showMaxTime);
            }

            @Override protected Content sample() {
                return timer((7 * 60 + 15) * 1000L, (62 * 60) * 1000L, List.of("§a+25s", "§c-8s"), true, true);
            }

            @Override public List<Content> states() {
                return List.of(timer(45_000L, (62 * 60) * 1000L, List.of("§c-30s"), true, true), timer(4 * 60_000L, (62 * 60) * 1000L, List.of(), false, false));
            }

            @Override protected List<Row> classicRows() {
                return lineRows(RiftTimer.display());
            }

            @Override protected List<Row> classicDemo() {
                return lineRows(List.of("§eRift Timer: §b7m 15s§7/§b1h 2m §7(11.6%)", "§a+25s"));
            }
        };
    }

    // --- crux talisman ---

    // color of a names first color code for the meter under it. dark grey says nothing so it takes the cards
    private static int codeColor(String s, int fallback) {
        int[] codes = {0, 0xFF3F7FE0, 0xFF3FC24F, 0xFF3FC4C4, 0xFFE05050, 0xFFC050C0, 0xFFFFAA00, 0, 0, 0xFF7F8CFF, 0xFF5FEA8C, 0xFF5CD8FF, 0xFFFF6B6B, 0xFFFF7FE0, 0xFFFFE066, 0};
        for (int i = 0; i + 1 < s.length(); i++) {
            if (s.charAt(i) != '§') continue;
            int n = "0123456789abcdef".indexOf(Character.toLowerCase(s.charAt(i + 1)));
            if (n >= 0 && codes[n] != 0) return codes[n];
            i++;
        }
        return fallback;
    }

    // bonus as a number + what its for, that lets it share a row in the short form
    private static Line splitBonus(String text) {
        int space = text.indexOf(' ');
        return Line.row(text.substring(0, space), (text.startsWith("§") ? text.substring(0, 2) : "") + text.substring(space + 1)).key("Bonuses").noMeter();
    }

    // do 2 bonuses fit half a compact card each
    private static boolean shareRow(String a, String b) {
        Ui ui = Ui.MEASURE;
        return a.indexOf(' ') > 0 && b.indexOf(' ') > 0 && 2 * (Math.max(ui.width(a), ui.width(b)) + 2) + Compact.GAP <= Compact.WIDE - 2 * Modern.PAD;
    }

    private static RichCard.Content crux(List<CruxTalismanDisplay.Crux> cruxes, List<String> bonuses, double percent, boolean maxed, boolean compact, boolean showBonuses, int accent, boolean brief) {
        List<Line> out = new ArrayList<>();
        boolean asMaxed = maxed && compact;
        if (!cruxes.isEmpty()) out.add(Line.bar("Overall meter", asMaxed ? 1 : percent / 100, asMaxed ? Modern.GOOD : accent));
        if (!asMaxed) {
            int done = 0;
            for (CruxTalismanDisplay.Crux c : cruxes) if (c.maxed()) done++;
            if (!cruxes.isEmpty()) out.add(Line.row("Cruxes maxed", done + "/" + cruxes.size()).noMeter());
            for (CruxTalismanDisplay.Crux c : cruxes) {
                String label = RowText.plain(c.tier()).trim().isEmpty() ? c.name() : c.tier() + " " + c.name();
                String key = RowText.plain(c.name()).trim();
                if (c.maxed()) out.add(Line.row(label, "").key(key).chip("MAXED", Modern.GOOD).meter(1, Modern.GOOD).raw());
                else out.add(Line.row(label, c.progress()).key(key).meter(fractionOf(c.progress()), codeColor(c.name(), accent)));
            }
        }
        if (showBonuses && !bonuses.isEmpty()) {
            out.add(Line.section("Bonuses"));
            for (int i = 0; i < bonuses.size(); i++) {
                String b = bonuses.get(i);
                if (brief && i + 1 < bonuses.size() && shareRow(b, bonuses.get(i + 1))) {
                    out.add(splitBonus(b));
                    out.add(splitBonus(bonuses.get(++i)));
                } else out.add(Line.row(b, "").key("Bonuses").noMeter());
            }
        }
        String right = cruxes.isEmpty() ? "" : asMaxed ? "§aMAXED" : "§a" + percent + "%";
        return new RichCard.Content(right, out);
    }

    private static final Pattern FRACTION = Pattern.compile("(\\d+)/(\\d+)");

    // "§e88§7/§a100 §7kills" -> 0.88, just the talismans own numbers
    private static double fractionOf(String progress) {
        Matcher m = FRACTION.matcher(RowText.plain(progress));
        if (!m.find()) return 0;
        double of = Double.parseDouble(m.group(2));
        return of <= 0 ? 0 : Math.min(1, Double.parseDouble(m.group(1)) / of);
    }

    static HudElement cruxCard() {
        return new RichCard("rift_crux", "Crux Talisman", "Rift", Zone.TOP_LEFT, 52, Rift.CRUX) {
            @Override public boolean visible() {
                return Rift.rift() && !CruxTalismanDisplay.display().isEmpty();
            }

            @Override public String heading() {
                return title;
            }

            @Override protected Content live() {
                return crux(CruxTalismanDisplay.cruxes, CruxTalismanDisplay.bonusesLine, CruxTalismanDisplay.percentValue, CruxTalismanDisplay.maxed, CruxTalismanDisplay.compactWhenMaxed, CruxTalismanDisplay.showBonuses, accent(), compact());
            }

            @Override protected Content sample() {
                List<CruxTalismanDisplay.Crux> c = List.of(new CruxTalismanDisplay.Crux("§2Shy", "§23", "§e88§7/§a100 §7kills", false),
                        new CruxTalismanDisplay.Crux("§7Shadow", "§72", "§e42§7/§a50 §7kills", false),
                        new CruxTalismanDisplay.Crux("§eVolt", "§e0", "§e6§7/§a10 §7kills", false),
                        new CruxTalismanDisplay.Crux("§dPuff", "§d4", "§a§lMAXED", true));
                return crux(c, List.of("§c+15❤ Hearts", "§a+45ф Rift Time", "§b+8✎ Mana Regen"), 59.0, false, true, true, accent(), compact());
            }

            @Override public List<Content> states() {
                List<CruxTalismanDisplay.Crux> all = List.of(new CruxTalismanDisplay.Crux("§2Shy", "§26", "§a§lMAXED", true), new CruxTalismanDisplay.Crux("§dPuff", "§d6", "§a§lMAXED", true));
                return List.of(crux(all, List.of("§c+15❤ Hearts"), 100.0, true, true, true, accent(), compact()), crux(all, List.of(), 100.0, true, false, false, accent(), compact()));
            }

            @Override protected List<Row> classicRows() {
                return lineRows(CruxTalismanDisplay.display());
            }

            @Override protected List<Row> classicDemo() {
                return lineRows(List.of("§7Crux Talisman Progress: §a62.5%", "  §23 §2Shy§7: §e88§7/§a100 §7kills", "  4 Puff§7: §a§lMAXED"));
            }
        };
    }

    // --- vermin ---

    private static String verminIcon(VerminTracker.VerminType t) {
        return switch (t) {
            case FLY -> "minecraft:bee_spawn_egg";
            case SPIDER -> "minecraft:spider_spawn_egg";
            case SILVERFISH -> "minecraft:silverfish_spawn_egg";
        };
    }

    private static int verminColor(VerminTracker.VerminType t) {
        return switch (t) {
            case FLY -> 0xFF9CCC65;
            case SPIDER -> 0xFFFF8A65;
            case SILVERFISH -> 0xFFB0BEC5;
        };
    }

    private static RichCard.Content vermin(Map<VerminTracker.VerminType, Integer> session, Map<VerminTracker.VerminType, Integer> total, boolean hasVacuum, boolean brief) {
        List<Line> out = new ArrayList<>();
        int sum = sum(session);
        // the total only knows more than this session once the vermin bin was opened
        boolean binRead = false;
        for (VerminTracker.VerminType t : VerminTracker.VerminType.values()) if (total.getOrDefault(t, 0) > session.getOrDefault(t, 0)) binRead = true;
        if (sum > 0 || binRead) {
            String word = brief ? "§7Sess" : "§7Session";
            out.add(binRead ? Line.table("§7Vacuumed", word, "§7Share", "§7Total").key("Heading") : Line.table("§7Vacuumed", word, "§7Share").key("Heading"));
            for (VerminTracker.VerminType t : VerminTracker.VerminType.values()) {
                int n = session.getOrDefault(t, 0), all = total.getOrDefault(t, 0);
                if (n == 0 && all == 0) continue;
                String name = GameState.strip(t.vermin), now = "§e" + String.format(Locale.ROOT, "%,d", n), share = "§7" + (sum > 0 ? RowText.percent((double) n / sum) : "-");
                Line row = binRead ? Line.table(name, now, share, "§f" + String.format(Locale.ROOT, "%,d", all)) : Line.table(name, now, share);
                row.icon(verminIcon(t));
                if (sum > 0) row.meter((double) n / sum, verminColor(t));
                out.add(row);
            }
        } else out.add(Line.row("Vacuumed this session", "§70").key("Heading").raw());
        if (!hasVacuum) out.add(Line.row("Turbomax Vacuum", "").chip("NOT CARRIED", Modern.WARN).raw());
        return new RichCard.Content(sum > 0 ? "§e" + String.format(Locale.ROOT, "%,d", sum) : "", out);
    }

    static HudElement verminCard(Supplier<List<String>> classic) {
        return new RichCard("rift_vermin", "Vermin Tracker", "Rift", Zone.TOP_LEFT, 53, Rift.VERMIN) {
            @Override public boolean visible() {
                return Rift.rift() && !classic.get().isEmpty();
            }

            @Override protected Content live() {
                return vermin(VerminTracker.session, VerminTracker.total, VerminTracker.hasVacuum, compact());
            }

            @Override protected Content sample() {
                return vermin(Map.of(VerminTracker.VerminType.SILVERFISH, 27, VerminTracker.VerminType.FLY, 3), Map.of(VerminTracker.VerminType.SILVERFISH, 1204, VerminTracker.VerminType.FLY, 318, VerminTracker.VerminType.SPIDER, 96), true, compact());
            }

            @Override public List<Content> states() {
                Map<VerminTracker.VerminType, Integer> some = Map.of(VerminTracker.VerminType.SPIDER, 4);
                return List.of(vermin(Map.of(), Map.of(), false, compact()), vermin(some, some, true, compact()));
            }

            @Override protected List<Row> classicRows() {
                return lineRows(classic.get());
            }

            @Override protected List<Row> classicDemo() {
                return lineRows(List.of("§7Vermin Tracker:", " §7- §e27 §aSilverfish", " §7- §e3 §aFlies"));
            }
        };
    }

    // --- punchcard ---

    private static final int PUNCHES = 20;

    private static RichCard.Content punchcard(int count, String last, boolean countDown) {
        List<Line> out = new ArrayList<>();
        int left = Math.max(0, PUNCHES - count);
        double done = Math.min(1, (double) count / PUNCHES);
        out.add(Line.bar("Punches meter", done, done >= 1 ? Modern.GOOD : 0xFFFF80AB));
        if (left > 0) out.add(Line.row("Still to punch", "§f" + left).key("Left").raw().brief("Left"));
        else out.add(Line.row("Still to punch", "").key("Left").chip("ALL DONE", Modern.GOOD).raw().brief("Left"));
        if (!last.isEmpty()) out.add(Line.row("Last punched", "§b" + last).key("Last punched").raw().brief("Last"));
        String head = countDown ? "§d" + left + " §7left" : "§d" + count + "§7/§f" + PUNCHES;
        return new RichCard.Content(head + "  §7" + RowText.percent(done), out);
    }

    static HudElement punchcardCard(Supplier<List<String>> classic) {
        return new RichCard("rift_punchcard", "Punchcard", "Rift", Zone.TOP_LEFT, 54, Rift.PUNCHCARD) {
            @Override public boolean visible() {
                return Rift.rift() && !classic.get().isEmpty();
            }

            @Override protected Content live() {
                return punchcard(PunchcardHighlight.count(), PunchcardHighlight.lastPunched(), PunchcardHighlight.reverseGUI);
            }

            @Override protected Content sample() {
                return punchcard(14, "Technoblade_Fan99", false);
            }

            @Override public List<Content> states() {
                return List.of(punchcard(20, "xX_Rift_Runner_Xx", false), punchcard(3, "", true));
            }

            @Override protected List<Row> classicRows() {
                return lineRows(classic.get());
            }

            @Override protected List<Row> classicDemo() {
                return lineRows(List.of("Punchcard Artifact: §d14"));
            }
        };
    }

    // --- ubiks cube ---

    private static RichCard.Content ubik(long left) {
        List<Line> out = new ArrayList<>();
        if (left < 0) out.add(Line.row("Ubik's Cube", "").chip("READY", Modern.GOOD).meter(1, Modern.GOOD).raw().brief("Ubik"));
        else {
            // runs down like a cooldown, meter + percent say what the 2 times say
            double share = Math.min(1, (double) left / UbikReminder.FULL_COOLDOWN);
            String of = HoppityEnv.format(UbikReminder.FULL_COOLDOWN);
            out.add(Line.row("Ubik's Cube", "§e" + HoppityEnv.format(left, 2) + " §7of §b" + of + "  §7" + RowText.percent(share)).meter(share).raw().brief("Ubik"));
        }
        return new RichCard.Content("", out);
    }

    static HudElement ubikCard() {
        return new RichCard("rift_ubik", "Ubik's Cube", "Rift", Zone.TOP_LEFT, 55, Rift.UBIK) {
            @Override public boolean visible() {
                if (!GameState.inSkyBlock && !SbcMod.DEV) return false;
                UbikReminder.onlyWhenReady = Rift.b(Rift.UBIK, "onlyWhenReady", false);
                return UbikReminder.display() != null && RiftEnv.profile().ubikRemindTime != RiftEnv.FAR_FUTURE || (UbikReminder.display() != null && Rift.rift());
            }

            @Override protected Content live() {
                return ubik(UbikReminder.millisLeft());
            }

            @Override protected Content sample() {
                return ubik(83 * 60_000L);
            }

            @Override public List<Content> states() {
                return List.of(ubik(-1), ubik(40_000L));
            }

            @Override protected List<Row> classicRows() {
                String d = UbikReminder.display();
                return d == null ? List.of() : List.of(new Row("Ubik's Cube", d, 0));
            }

            @Override protected List<Row> classicDemo() {
                return List.of(new Row("Ubik's Cube", "§e1h 23m", 0));
            }
        };
    }
}
