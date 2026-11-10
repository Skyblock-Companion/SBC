// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.ui.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.SbTime;
import io.github.zmuzy.sbc.core.data.DataFiles;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// what the custom scoreboard settings need besides its lines. from skyhanni customscoreboard (Profile, Time,
// PlayerAmount, LobbyCode, ChunkedStats, NumberTracking)
final class BoardExtras {
    private static final long CHANGE_MS = 5000;
    private static final Map<String, long[]> CHANGES = new HashMap<>();
    private static Map<String, Integer> maxByMode;
    private static int maxDefault = 24, maxMega = 60;

    private BoardExtras() {}

    // from skyhanni getProfileTypeSymbol + ScoreboardElementProfile
    static String profile(String type, String name, boolean showName, List<String> sidebar) {
        String t = type == null ? "" : type.toLowerCase(Locale.ROOT);
        String symbol = "§e";
        String kind = "Normal";
        if (t.contains("ironman")) {
            symbol = "§7♲ ";
            kind = "Ironman";
        } else if (t.contains("stranded")) {
            symbol = "§a☀ ";
            kind = "Stranded";
        } else if (t.contains("bingo")) {
            symbol = "§e❤ ";
            kind = "Bingo";
            for (String line : sidebar) {
                Integer rank = io.github.zmuzy.sbc.core.data.BingoData.rank(line);
                if (rank != null) {
                    symbol = io.github.zmuzy.sbc.core.data.BingoData.icon(rank) + " ";
                    break;
                }
            }
        }
        if (showName && name != null && !name.isEmpty()) kind = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        return symbol + kind;
    }

    // from skyhanni ScoreboardElementTime: our own clock, for 24h or the exact minute (hypixel rounds to 10)
    static String time(long now, boolean h24, boolean exact, String symbol) {
        SbTime.SbDate d = SbTime.sbDate(now);
        int minute = exact ? d.minute() : d.minute() / 10 * 10;
        String clock;
        if (h24) clock = String.format(Locale.ROOT, "%02d:%02d", d.hour(), minute);
        else clock = String.format(Locale.ROOT, "%d:%02d%s", d.hour() % 12 == 0 ? 12 : d.hour() % 12, minute, d.hour() < 12 ? "am" : "pm");
        return ("§7" + clock + " " + (symbol == null ? "" : symbol)).trim();
    }

    // from skyhanni ScoreboardElementQuiver + NumberUtil.percentageColor
    static String arrow(String name, int amount, int max, boolean endless, boolean percent, boolean color) {
        double share = max <= 0 ? 0 : (double) amount / max;
        String c = !color ? "" : share > 0.9 ? "§2" : share > 0.75 ? "§a" : share > 0.5 ? "§e" : share > 0.25 ? "§6" : "§c";
        String shown = endless ? "∞" : percent ? Math.round(share * 100) + "%" : String.format(Locale.ROOT, "%,d", amount);
        return "§f" + name + ": " + (c.isEmpty() ? "§f" : c) + shown;
    }

    // hypixel writes MM/DD/YY, DisplayConfig.DateFormat flips it
    static String date(String mmddyy, String format) {
        String[] p = mmddyy.split("/");
        if (p.length != 3) return mmddyy;
        String year = p[2].length() == 2 ? "20" + p[2] : p[2];
        return switch (format) {
            case "DD/MM/YYYY" -> p[1] + "/" + p[0] + "/" + year;
            case "YYYY-MM-DD" -> year + "-" + p[0] + "-" + p[1];
            case "MM/DD/YYYY" -> p[0] + "/" + p[1] + "/" + year;
            default -> mmddyy;
        };
    }

    // from skyhanni HypixelData.getMaxPlayersForCurrentServer minus the visiting line: mega servers, the islands
    // own number, or the usual
    static int maxPlayers(String mode, String serverId) {
        if (maxByMode == null) {
            maxByMode = new HashMap<>();
            JsonObject o = DataFiles.object(DataFiles.Source.SKYHANNI, "IslandType.json");
            if (o != null) {
                if (o.has("max_players")) maxDefault = o.get("max_players").getAsInt();
                if (o.has("max_players_mega")) maxMega = o.get("max_players_mega").getAsInt();
                if (o.has("islands")) {
                    for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("islands").entrySet()) {
                        JsonObject island = e.getValue().getAsJsonObject();
                        if (island.has("api_name") && island.has("max_players")) maxByMode.put(island.get("api_name").getAsString(), island.get("max_players").getAsInt());
                    }
                }
            }
        }
        if (serverId != null && serverId.startsWith("mega")) return maxMega;
        return maxByMode.getOrDefault(mode, maxDefault);
    }

    // from skyhanni NumberTrackingElement: " (+1,234)" for 5 sec after a number changes
    static String change(String key, long value, long now, java.util.function.LongFunction<String> format, String color) {
        long[] c = CHANGES.computeIfAbsent(key, k -> new long[]{value, 0, 0});
        if (value != c[0]) {
            c[1] = value - c[0];
            c[2] = now + CHANGE_MS;
            c[0] = value;
        }
        if (now >= c[2]) return "";
        return " §7(" + color + (c[1] > 0 ? "+" : "") + format.apply(c[1]) + "§7)" + color;
    }

    static void clearChanges() {
        CHANGES.clear();
    }

    // from skyhanni ChunkedStats: lots of stats on few lines, split by a bar
    static List<String> chunked(List<String> stats, int perLine) {
        List<String> out = new ArrayList<>();
        int per = Math.max(1, perLine);
        for (int i = 0; i < stats.size(); i += per) out.add(String.join(" §f| ", stats.subList(i, Math.min(stats.size(), i + per))));
        return out;
    }

    // value of a "Label: value" sidebar line, first line a skyhanni pattern claims. null = none
    static String value(List<String> fmt, String patternKey, String label) {
        for (String f : fmt) {
            Matcher m = Patterns.get(patternKey).matcher(f);
            if (m.matches()) return GameState.strip(f).trim().replaceFirst("^" + label + ":\\s*", "").trim();
        }
        return null;
    }
}
