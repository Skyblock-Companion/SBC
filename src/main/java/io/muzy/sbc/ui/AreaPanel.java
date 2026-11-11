// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui;

import io.muzy.sbc.core.GameState;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// tab list info widgets (visitors, pests, comms, powder, dungeon stats...) but only the ones for where you are
public final class AreaPanel {
    private record Section(String title, List<String> lines) {}

    private static final io.muzy.sbc.core.Patterns.P HEADER = io.muzy.sbc.core.Patterns.p("AreaPanel.HEADER");
    private static final int W = 150, MAX_LINES = 14;

    private static List<String> lastTab;
    private static String lastIsland = "";
    private static List<Section> cache = List.of();
    private AreaPanel() {}

    // widget titles the game uses (title alone or title + value on one line)
    private static final io.muzy.sbc.core.Patterns.P KNOWN = io.muzy.sbc.core.Patterns.p("AreaPanel.KNOWN");

    private static String[] wanted(String island) {
        return switch (island) {
            case "GARDEN" -> new String[]{"visitors", "pests", "pest traps", "full traps", "no bait", "jacob", "composter", "crop milestones", "garden level", "copper", "sowdust", "guests"};
            case "CATACOMBS" -> new String[]{"opened rooms", "puzzles", "party", "player stats"};
            case "DWARVEN_MINES", "CRYSTAL_HOLLOWS", "MINESHAFT", "GLACITE_TUNNELS" -> new String[]{"commissions", "powders", "forges", "crystals", "essence"};
            case "CRIMSON_ISLE", "KUUDRA" -> new String[]{"reputation", "faction quests", "trophy fish", "volcano"};
            case "THE_END" -> new String[]{"dragon", "protector", "eyes placed", "slayer"};
            case "THE_PARK" -> new String[]{"rain", "thunder", "slayer", "daily quests"};
            case "WINTER" -> new String[]{"island closes", "north stars"};
            case "PRIVATE_ISLAND" -> new String[]{"guests", "minions"};
            case "THE_RIFT" -> new String[]{"good to know"};
            case "DUNGEON_HUB" -> new String[]{"dungeons", "party"};
            default -> new String[]{"slayer", "daily quests", "fire sales", "forges"};
        };
    }

    private static List<Section> parse() {
        List<String> tab = GameState.tab;
        if (tab == lastTab && GameState.island.equals(lastIsland)) return cache;
        lastTab = tab;
        lastIsland = GameState.island;
        String[] want = wanted(GameState.island);
        List<Section> all = new ArrayList<>();
        Section cur = null;
        for (String raw : tab) {
            String line = GameState.strip(raw).replaceAll("[^\\x20-\\x7E\\u00A0-\\uFFFF]", "").strip();
            if (line.isBlank()) continue;
            Matcher gm = HEADER.matcher(line);
            if (KNOWN.matcher(line).matches() || gm.find()) {
                cur = new Section(line, new ArrayList<>());
                all.add(cur);
            } else if (cur != null) {
                cur.lines().add(line);
            }
        }
        List<Section> out = new ArrayList<>();
        for (Section s : all) {
            String t = s.title().toLowerCase(Locale.ROOT);
            boolean ok = t.startsWith("event");
            for (String w : want) if (t.startsWith(w)) ok = true;
            boolean inline = !s.title().endsWith(":") && !s.title().startsWith("Info");
            if (ok && (!s.lines().isEmpty() || inline)) out.add(s);
        }
        cache = out;
        return out;
    }

    public static boolean hasData() {
        return !parse().isEmpty();
    }

    private static int lineCount(List<Section> secs) {
        int n = 0;
        for (Section s : secs) n += 1 + s.lines().size();
        return Math.min(n, MAX_LINES);
    }

    public static int height() {
        return lineCount(parse()) * 10 + 10;
    }

    public static int width() {
        return W;
    }

    public static void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
        List<Section> secs = parse();
        io.muzy.sbc.ui.theme.Theme t = io.muzy.sbc.ui.theme.Theme.current();
        t.panel(ui, x, y, W, height());
        int cy = y + 5, used = 0;
        for (Section s : secs) {
            if (used >= MAX_LINES) break;
            io.muzy.sbc.ui.theme.Modern.section(ui, ui.ellipsize(s.title(), W - 8), x + 4, cy, W - 8, t.accent());
            cy += 10;
            used++;
            for (String l : s.lines()) {
                if (used >= MAX_LINES) break;
                var shaped = io.muzy.sbc.ui.hud.RowText.shape(l, "");
                io.muzy.sbc.ui.theme.Modern.row(ui, shaped.label(), shaped.value(), x + 4, cy, W - 8, io.muzy.sbc.ui.theme.Modern.LABEL, t.text());
                cy += 10;
                used++;
            }
        }
    }
}
