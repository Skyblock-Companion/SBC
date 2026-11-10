// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui.hud;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Mayor;
import io.muzy.sbc.ui.HudLayout;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// small row elements: skyblock date + time, mayor + perks
public final class InfoElements {
    private static final Pattern DATE = Pattern.compile("^((?:Early |Late )?(?:Spring|Summer|Autumn|Winter)\\s+\\d+\\w*)");
    private static final Pattern TIME = Pattern.compile("^(\\d{1,2}:\\d{2}\\s?[ap]m)(.*)$");
    private static final Pattern CLOCK = Pattern.compile("^(\\d{1,2}):(\\d{2})\\s?([ap])m");
    private static final int DESCRIPTION_W = 150, BRIEF_W = Compact.WIDE - 2 * Modern.PAD;
    private InfoElements() {}

    // date in its seasons color
    private static String seasonal(String date) {
        String code = date.contains("Spring") ? "§a" : date.contains("Summer") ? "§e" : date.contains("Autumn") ? "§6" : date.contains("Winter") ? "§b" : "";
        return code + date;
    }

    // how far through the day "10:40pm" is, or -1
    private static double dayShare(String time) {
        Matcher m = CLOCK.matcher(time);
        if (!m.find()) return -1;
        int hour = Integer.parseInt(m.group(1)) % 12 + (m.group(3).equals("p") ? 12 : 0);
        return (hour * 60 + Integer.parseInt(m.group(2))) / 1440.0;
    }

    // space before hypixels sun/moon
    private static String clockText(String time) {
        Matcher m = TIME.matcher(time);
        if (!m.find() || m.group(2).isBlank()) return time;
        return m.group(1) + " " + m.group(2).trim();
    }

    // line by line, as wide as the card comfortably gets
    private static List<String> wrap(String text) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.trim().split("\\s+")) {
            if (line.length() > 0 && Ui.MEASURE.width(line + " " + word) > DESCRIPTION_W) {
                lines.add(line.toString());
                line.setLength(0);
            }
            if (line.length() > 0) line.append(' ');
            line.append(word);
        }
        if (line.length() > 0) lines.add(line.toString());
        // a last line with one word looks like a leftover, the line before lends it a word
        int last = lines.size() - 1;
        if (last > 0 && lines.get(last).indexOf(' ') < 0) {
            String before = lines.get(last - 1);
            int cut = before.lastIndexOf(' ');
            String joined = before.substring(cut + 1) + " " + lines.get(last);
            if (cut > 0 && Ui.MEASURE.width(joined) <= DESCRIPTION_W) {
                lines.set(last - 1, before.substring(0, cut));
                lines.set(last, joined);
            }
        }
        return lines;
    }

    private static void perk(List<HudElement.Row> out, String code, String name, String description) {
        out.add(new HudElement.Row(code + name));
        for (String l : wrap(description)) out.add(new HudElement.Row("§7" + l));
    }

    // compact perk: what it does starts on the name line, lines as wide as the card
    private static void briefPerk(List<HudElement.Row> out, String code, String name, String description) {
        StringBuilder line = new StringBuilder(code).append(name).append("§7");
        for (String word : description.trim().split("\\s+")) {
            if (Ui.MEASURE.width(line + " " + word) > BRIEF_W) {
                out.add(new HudElement.Row(line.toString()));
                line.setLength(0);
                line.append("§7").append(word);
            } else line.append(' ').append(word);
        }
        out.add(new HudElement.Row(line.toString()));
    }

    private static final String MINISTER_MARK = " (Minister)";

    public static final HudElement DATE_EL = new HudElement("sbdate", "SkyBlock date", "GUI", Zone.TOP_RIGHT, 28, "sbdate") {
        private String[] find() {
            String d = "", t = "";
            for (String l : GameState.scoreboard) {
                Matcher m = DATE.matcher(l.trim());
                if (m.find()) d = m.group(1);
                m = TIME.matcher(l.trim());
                if (m.find()) t = m.group(1) + m.group(2).trim();
            }
            return new String[]{d, t};
        }

        @Override
        public String defaultShow() {
            return ScoreboardElement.on() ? "Never" : "When needed"; // scoreboard already has date + time
        }

        @Override
        public boolean visible() {
            String[] f = find();
            return !f[0].isEmpty() || !f[1].isEmpty();
        }

        @Override
        public String heading() {
            return "";
        }

        // date left, time right, under them how far the day is in sun/moon color
        private Row row(String date, String time) {
            int color = time.contains("☽") ? Modern.AQUA : Modern.GOLD;
            double share = dayShare(time);
            String clock = clockText(time);
            if (date.isEmpty()) return share < 0 ? new Row(clock) : Row.meter("", clock, color, share, color);
            return share < 0 ? new Row(seasonal(date), clock, color) : Row.meter(seasonal(date), clock, color, share, color);
        }

        @Override
        public List<Row> rows() {
            String[] f = find();
            List<Row> out = new ArrayList<>();
            out.add(row(f[0], f[1]));
            return out;
        }

        @Override
        public List<Row> demo() {
            return List.of(row("Late Autumn 14th", "10:40pm ☽"));
        }

        @Override
        protected boolean meters() {
            return false;
        }
    };

    public static final HudElement MAYOR_EL = new HudElement("mayor", "Mayor", "GUI", Zone.TOP_RIGHT, 35, "mayor") {
        // perks get read every frame but only change on a fetch (which swaps the lists), so no rewrapping each time
        private List<Row> built = List.of();
        private List<Mayor.Perk> builtPerks;
        private List<String> builtCandidates;
        private String builtMinister;
        private boolean builtBrief;

        @Override
        public String defaultShow() {
            return ScoreboardElement.on() ? "Never" : "When needed"; // scoreboard already has the mayor + perks
        }

        @Override
        public boolean visible() {
            return !Mayor.name.isEmpty();
        }

        @Override
        public String heading() {
            return "Mayor " + (Mayor.name.isEmpty() && demo ? "Cole" : Mayor.name) + (Mayor.name.isEmpty() ? "" : " §7" + io.muzy.sbc.core.SbTime.dur(Mayor.nextElection(System.currentTimeMillis()) - System.currentTimeMillis()));
        }

        @Override
        protected boolean meters() {
            return false; // perk texts are sentences, a number in one isnt progress
        }

        // mayor perks + what they do, then the minister, then whos running
        private List<Row> card(String minister, List<Mayor.Perk> perks, List<String> candidates) {
            return compact() ? briefCard(minister, perks, candidates) : openCard(minister, perks, candidates);
        }

        // same as openCard, each perk with its name on its first line
        private List<Row> briefCard(String minister, List<Mayor.Perk> perks, List<String> candidates) {
            List<Row> out = new ArrayList<>();
            for (Mayor.Perk p : perks) if (!p.name().endsWith(MINISTER_MARK)) briefPerk(out, "§b", p.name(), p.description());
            if (!minister.isEmpty()) {
                if (!out.isEmpty()) out.add(new Row(""));
                out.add(new Row("Minister", "§e" + minister, 0));
                for (Mayor.Perk p : perks) {
                    if (p.name().endsWith(MINISTER_MARK)) briefPerk(out, "§e", p.name().substring(0, p.name().length() - MINISTER_MARK.length()), p.description());
                }
            }
            if (!candidates.isEmpty()) {
                if (!out.isEmpty()) out.add(new Row(""));
                briefPerk(out, "§d", "Election candidates", String.join(", ", candidates));
            }
            return out;
        }

        private List<Row> openCard(String minister, List<Mayor.Perk> perks, List<String> candidates) {
            List<Row> out = new ArrayList<>();
            for (Mayor.Perk p : perks) if (!p.name().endsWith(MINISTER_MARK)) perk(out, "§b", p.name(), p.description());
            if (!minister.isEmpty()) {
                if (!out.isEmpty()) out.add(new Row(""));
                out.add(new Row("Minister", "§e" + minister, 0));
                for (Mayor.Perk p : perks) {
                    if (p.name().endsWith(MINISTER_MARK)) perk(out, "§e", p.name().substring(0, p.name().length() - MINISTER_MARK.length()), p.description());
                }
            }
            if (!candidates.isEmpty()) {
                if (!out.isEmpty()) out.add(new Row(""));
                out.add(new Row("§dElection candidates"));
                for (String l : wrap(String.join(", ", candidates))) out.add(new Row("§7" + l));
            }
            return out;
        }

        @Override
        public List<Row> rows() {
            List<Row> out = new ArrayList<>();
            List<Mayor.Perk> perks = Mayor.perks;
            List<String> candidates = Mayor.candidates;
            String minister = Mayor.minister;
            boolean brief = compact();
            if (perks != builtPerks || candidates != builtCandidates || !minister.equals(builtMinister) || brief != builtBrief) {
                built = List.copyOf(card(minister, perks, candidates));
                builtPerks = perks;
                builtCandidates = candidates;
                builtMinister = minister;
                builtBrief = brief;
            }
            return built;
        }

        @Override
        public List<Row> demo() {
            return card("Diana", List.of(new Mayor.Perk("Mining XP Buff", "Gain +60 Mining Wisdom on public islands."),
                            new Mayor.Perk("Prospection", "Mining minions work 25% faster."),
                            new Mayor.Perk("Pet XP Buff (Minister)", "Gain 35% more pet XP.")),
                    List.of());
        }
    };

    public static void register() {
        HudLayout.add(DATE_EL);
        HudLayout.add(MAYOR_EL);
    }
}
