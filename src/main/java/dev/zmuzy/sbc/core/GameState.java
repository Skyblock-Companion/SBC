// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.core;

import dev.zmuzy.sbc.mc.mixin.PlayerTabOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// what the player is doing rn, read like the big sb mods do: mod api location packet (exact island), sidebar
// (purse, bits, area, date), tab widgets (profile, skills...)
public final class GameState {
    public static boolean onHypixel, inSkyBlock;
    private static long lastSkyBlockSeen;
    // island key like DWARVEN_MINES, same as skyhannis island graph file names
    public static String island = "", islandName = "", area = "";
    public static long purse = -1, bits = -1, copper = -1, motes = -1, bank = -1;
    // profile type from the sidebar, from skyhanni HypixelData ironman/stranded/bingo
    public static boolean ironman, stranded, bingo;
    public static String profileName = "", profileType = "", sbLevel = "";
    public static String cookie = "";
    public static final java.util.Set<String> REAL_SKILLS = java.util.Set.of("Farming", "Mining", "Combat", "Foraging", "Fishing", "Enchanting",
            "Alchemy", "Taming", "Carpentry", "Hunting", "Runecrafting", "Social");

    // chat lines about the booster cookie
    public static void onChat(String line) {
        if (line.contains("Obtain a Booster Cookie") || line.contains("You need a Booster Cookie") || line.contains("requires a Booster Cookie")
                || line.contains("need the Cookie Buff")) {
            cookie = "Not active";
            CookieBuff.off();
        }
        // from skyhanni boostercookieate, without its colors
        if (line.trim().startsWith("You consumed a Booster Cookie!")) CookieBuff.ate(System.currentTimeMillis());
    }

    public static boolean hasCookie() {
        String c = cookieStatus().toLowerCase(Locale.ROOT);
        return !c.isEmpty() && !c.contains("not active");
    }
    private static boolean loggedFooter;

    // cookie status for display: tab first, then the api
    public static String cookieStatus() {
        String known = CookieBuff.status();
        if (!known.isEmpty()) return known;
        if (!cookie.isEmpty()) return cookie;
        Profile.Data d = Profile.current;
        if (d != null && d.cookieActive == 1) return "Active";
        if (d != null && d.cookieActive == 0) return "Not active";
        return "";
    }
    public static List<String> scoreboard = List.of();
    // same lines with color codes, for the classic scoreboard
    public static List<String> scoreboardRaw = List.of();
    // sidebar with colors kept + hypixels prefix/suffix split joined (what skyhannis patterns match)
    public static List<String> scoreboardFormatted = List.of();
    private static final Pattern FAKE_CODE = Pattern.compile("\u00a7[^0-9a-fk-orA-FK-OR\u00a7]");
    private static final Pattern LAST_COLOR = Pattern.compile("((?:\u00a7[0-9a-fk-orA-FK-OR])+)[^\u00a7]*$");

    // hypixel sends each sidebar line as team prefix + fake color code (the invisible player name) + suffix
    // repeating the prefix color. this joins them like skyhanni ScoreboardData so the patterns match
    public static String formatLine(String raw) {
        Matcher m = FAKE_CODE.matcher(raw);
        if (!m.find()) return raw;
        String start = raw.substring(0, m.start());
        String end = FAKE_CODE.matcher(raw.substring(m.end())).replaceAll("");
        Matcher lc = LAST_COLOR.matcher(start);
        if (lc.find()) {
            String codes = lc.group(1);
            for (int i = 0; i + 2 <= codes.length(); i += 2) {
                String c = codes.substring(i, i + 2);
                if (end.startsWith(c)) end = end.substring(2);
            }
        }
        return start + end;
    }
    public static List<String> tab = List.of();
    public static String tabFooter = "";
    // skill -> level, percent from the tab Skills widget
    public static final Map<String, double[]> tabSkills = new HashMap<>();

    // HypixelLocation sets these from the mod api packet
    public static volatile String apiMode = "", apiMap = "", apiServerType = "", apiServer = "", apiLobby = "";

    private static final dev.zmuzy.sbc.core.Patterns.P STRIP = dev.zmuzy.sbc.core.Patterns.p("GameState.STRIP");
    private static final dev.zmuzy.sbc.core.Patterns.P PURSE = dev.zmuzy.sbc.core.Patterns.p("GameState.PURSE");
    private static final dev.zmuzy.sbc.core.Patterns.P BITS = dev.zmuzy.sbc.core.Patterns.p("GameState.BITS");
    private static final dev.zmuzy.sbc.core.Patterns.P COPPER = dev.zmuzy.sbc.core.Patterns.p("GameState.COPPER");
    private static final dev.zmuzy.sbc.core.Patterns.P MOTES = dev.zmuzy.sbc.core.Patterns.p("GameState.MOTES");
    private static final dev.zmuzy.sbc.core.Patterns.P AREA_TAB = dev.zmuzy.sbc.core.Patterns.p("GameState.AREA_TAB");
    private static final dev.zmuzy.sbc.core.Patterns.P PROFILE_TAB = dev.zmuzy.sbc.core.Patterns.p("GameState.PROFILE_TAB");
    private static final dev.zmuzy.sbc.core.Patterns.P LEVEL_TAB = dev.zmuzy.sbc.core.Patterns.p("GameState.LEVEL_TAB");
    private static final dev.zmuzy.sbc.core.Patterns.P BANK_TAB = dev.zmuzy.sbc.core.Patterns.p("GameState.BANK_TAB");
    private static final dev.zmuzy.sbc.core.Patterns.P COOKIE_TAB = dev.zmuzy.sbc.core.Patterns.p("GameState.COOKIE_TAB");
    private static final dev.zmuzy.sbc.core.Patterns.P SKILL_TAB = dev.zmuzy.sbc.core.Patterns.p("GameState.SKILL_TAB");

    private static final Map<String, String> MODE_TO_ISLAND = Map.ofEntries(
            Map.entry("hub", "HUB"), Map.entry("dynamic", "PRIVATE_ISLAND"), Map.entry("garden", "GARDEN"),
            Map.entry("farming_1", "THE_FARMING_ISLANDS"), Map.entry("foraging_1", "THE_PARK"), Map.entry("foraging_2", "GALATEA"),
            Map.entry("mining_1", "GOLD_MINES"), Map.entry("mining_2", "DEEP_CAVERNS"), Map.entry("mining_3", "DWARVEN_MINES"),
            Map.entry("crystal_hollows", "CRYSTAL_HOLLOWS"), Map.entry("mineshaft", "MINESHAFT"), Map.entry("combat_1", "SPIDER_DEN"),
            Map.entry("combat_3", "THE_END"), Map.entry("crimson_isle", "CRIMSON_ISLE"), Map.entry("kuudra", "KUUDRA"),
            Map.entry("dungeon_hub", "DUNGEON_HUB"), Map.entry("dungeon", "CATACOMBS"), Map.entry("winter", "WINTER"),
            Map.entry("rift", "THE_RIFT"), Map.entry("fishing_1", "BACKWATER_BAYOU"), Map.entry("dark_auction", "DARK_AUCTION"),
            // api ids like skyhanni IslandType + skyblocker Location
            Map.entry("foraging_3", "TORRHUS_CANYON"), Map.entry("safari", "SAFARI"), Map.entry("lotus_atoll", "LOTUS_ATOLL"));

    // location mode of an island id, empty = unknown
    public static String modeOf(String island) {
        for (Map.Entry<String, String> e : MODE_TO_ISLAND.entrySet()) if (e.getValue().equals(island)) return e.getKey();
        return "";
    }

    private static final Map<String, String> NAME_ALIASES = Map.ofEntries(
            Map.entry("PRIVATE_ISLAND", "PRIVATE_ISLAND"), Map.entry("GOLD_MINE", "GOLD_MINES"), Map.entry("SPIDERS_DEN", "SPIDER_DEN"),
            Map.entry("JERRYS_WORKSHOP", "WINTER"), Map.entry("THE_GARDEN", "GARDEN"), Map.entry("GARDEN", "GARDEN"),
            Map.entry("THE_CATACOMBS", "CATACOMBS"), Map.entry("KUUDRA", "KUUDRA"), Map.entry("RIFT", "THE_RIFT"),
            // names from skyhanni IslandType + skyblocker Location, for when we only have the name
            Map.entry("CRITTER_SAFARI", "SAFARI"), Map.entry("MOONGLADE_MARSH", "GALATEA"), Map.entry("KUUDRAS_HOLLOW", "KUUDRA"),
            Map.entry("DUNGEONS", "CATACOMBS"), Map.entry("GLACITE_MINESHAFTS", "MINESHAFT"));

    private GameState() {}

    // every id island can be, comparing with anything else is always false
    public static java.util.Set<String> islandIds() {
        java.util.Set<String> out = new java.util.TreeSet<>(MODE_TO_ISLAND.values());
        out.addAll(NAME_ALIASES.values());
        return out;
    }

    // island id for a packet mode, "" if unknown
    public static String islandOfMode(String mode) {
        return MODE_TO_ISLAND.getOrDefault(mode, "");
    }

    public static String strip(String s) {
        return s == null ? "" : STRIP.matcher(s).replaceAll("");
    }

    // from skyhanni HypixelData.serverId: what the packet said. without the mod api, the lobby code on the board
    // (lobbyCodePattern) up to any room id after it
    public static String serverId() {
        if (!apiServer.isEmpty()) return apiServer;
        for (String raw : scoreboardRaw) {
            java.util.regex.Matcher m = dev.zmuzy.sbc.core.Patterns.get("SH.sb.main.lobbyCode").matcher(formatLine(raw));
            if (m.matches()) return strip(m.group("code")).trim().split(" ")[0];
        }
        return "";
    }

    // from skyhanni HypixelLocationApi.reset: left the server so nothing the packets said holds anymore
    public static void disconnected() {
        apiMode = apiMap = apiServerType = apiServer = apiLobby = "";
        onHypixel = false;
        inSkyBlock = false;
    }

    public static void update(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            inSkyBlock = false;
            return;
        }
        var conn = mc.getConnection();
        String brand = conn != null && conn.serverBrand() != null ? conn.serverBrand() : "";
        String addr = conn != null && conn.getServerData() != null ? conn.getServerData().ip.toLowerCase(Locale.ROOT) : "";
        onHypixel = brand.toLowerCase(Locale.ROOT).contains("hypixel") || addr.contains("hypixel.net") || !apiServerType.isEmpty();

        readScoreboard(mc);
        long now = System.currentTimeMillis();
        if (inSkyBlock) lastSkyBlockSeen = now;
        else if (now - lastSkyBlockSeen < 4000) inSkyBlock = true; // ignore short scoreboard gaps (server switch, animations)
        readTab(mc);
        resolveIsland();
        if (inSkyBlock && !island.isEmpty() && !Store.I.visited.contains(island)) {
            Store.I.visited.add(island);
            Store.markDirty();
        }
    }

    private static void readScoreboard(Minecraft mc) {
        Scoreboard sb = mc.level.getScoreboard();
        Objective obj = sb.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (obj == null) {
            if (!dev.zmuzy.sbc.SbcMod.DEV) scoreboard = List.of();
            inSkyBlock = false;
            return;
        }
        String title = strip(obj.getDisplayName().getString());
        inSkyBlock = title.toUpperCase(Locale.ROOT).contains("SKYBLOCK") || "SKYBLOCK".equalsIgnoreCase(apiServerType);
        List<PlayerScoreEntry> entries = new ArrayList<>(sb.listPlayerScores(obj));
        entries.removeIf(PlayerScoreEntry::isHidden);
        entries.sort(Comparator.comparingInt(PlayerScoreEntry::value).reversed());
        List<String> raw = new ArrayList<>();
        for (PlayerScoreEntry e : entries) {
            PlayerTeam team = sb.getPlayersTeam(e.owner());
            Component c = e.display() != null ? e.display() : PlayerTeam.formatNameForTeam(team, e.ownerName());
            raw.add(c.getString());
        }
        parseScoreboard(raw);
        readProfileKind(title, scoreboardFormatted);
    }

    private static final dev.zmuzy.sbc.core.Patterns.P BINGO_LINE = dev.zmuzy.sbc.core.Patterns.p("SH.bingo.detection.scoreboard");
    private static final dev.zmuzy.sbc.core.Patterns.P BINGO_TITLE = dev.zmuzy.sbc.core.Patterns.p("SH.bingo.detection.scoreboardtitle");

    // from skyhanni HypixelData.checkProfile + BingoApi.getRankFromScoreboard, an icon in the sidebar title or a
    // line names the type
    // on someone elses island the title says GUEST and none of it is yours
    public static volatile boolean guest;

    public static void readProfileKind(String title, List<String> sidebar) {
        String plainTitle = strip(title);
        guest = plainTitle.contains("GUEST");
        if (guest) return;
        boolean iron = plainTitle.contains("♲"), strand = !iron && plainTitle.contains("☀"), bing = BINGO_TITLE.matcher(plainTitle).matches();
        for (String line : sidebar) {
            String plain = strip(line).trim();
            if (BINGO_LINE.matcher(line).matches() || plain.equals("Ⓑ Bingo")) bing = true;
            if (plain.equals("♲ Ironman")) iron = true;
            else if (plain.equals("☀ Stranded")) strand = true;
        }
        ironman = iron;
        stranded = strand;
        bingo = bing;
    }

    // from skyhanni HypixelData.noTrade: no bazaar + no ah for this profile
    public static boolean noTrade() {
        return ironman || stranded || bingo;
    }

    // pure: sidebar lines as the server sends them (color codes ok). tested on real lines
    public static void parseScoreboard(List<String> raw) {
        scoreboardRaw = new ArrayList<>(raw);
        List<String> fmt = new ArrayList<>(raw.size());
        for (String r : raw) fmt.add(formatLine(r));
        scoreboardFormatted = fmt;
        List<String> lines = new ArrayList<>();
        for (String r : fmt) lines.add(strip(r).replace(' ', ' ').trim());
        scoreboard = lines;
        for (String l : lines) {
            Matcher m;
            if ((m = PURSE.matcher(l)).find()) purse = num(m.group(1));
            else if ((m = BITS.matcher(l)).find()) bits = num(m.group(1));
            else if ((m = COPPER.matcher(l)).find()) copper = num(m.group(1));
            else if ((m = MOTES.matcher(l)).find()) motes = num(m.group(1));
            // area line starts with the location glyph (hypixel uses a private use font icon now, older text used the symbols)
            if (l.startsWith("⏣") || l.startsWith("ф") || (l.length() > 2 && l.charAt(0) >= 0xE000 && l.charAt(0) <= 0xF8FF && l.charAt(1) == ' ' && !l.contains(": ")))
                area = l.substring(1).replaceAll("[\\uE000-\\uF8FF]", "").replaceAll("\\s+x\\d+$", "").trim();
        }
    }

    private static void readTab(Minecraft mc) {
        var conn = mc.getConnection();
        if (conn == null) return;
        List<PlayerInfo> infos = new ArrayList<>(conn.getListedOnlinePlayers());
        infos.sort(Comparator.comparing((PlayerInfo p) -> p.getTeam() != null ? p.getTeam().getName() : "")
                .thenComparing(p -> p.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
        List<String> lines = new ArrayList<>(infos.size());
        for (PlayerInfo p : infos) {
            Component dn = p.getTabListDisplayName();
            if (dn != null) {
                String plain = strip(dn.getString());
                lines.add(plain);
                if (plain.trim().startsWith("[Lvl ")) Vitals.readPetStyle(dn);
            }
        }
        Component footer = ((PlayerTabOverlayAccessor) ((dev.zmuzy.sbc.mc.mixin.HudAccessor) mc.gui.hud).sbc$tabList()).sbc$footer();
        parseTab(lines, footer == null ? "" : strip(footer.getString()));
    }

    // pure: tab lines (codes already stripped) + footer text. tested on real lines
    public static void parseTab(List<String> lines, String footerPlain) {
        tab = lines;
        Vitals.readTab(lines);
        tabFooter = footerPlain;

        tabSkills.clear();
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            Matcher m;
            if ((m = PROFILE_TAB.matcher(l)).find()) {
                profileName = m.group(1);
                profileType = m.group(2).trim();
            } else if ((m = LEVEL_TAB.matcher(l)).find()) {
                sbLevel = m.group(1);
            } else if ((m = BANK_TAB.matcher(l)).find()) {
                bank = parseShort(m.group(1));
            } else if (l.contains("Cookie Buff")) {
                String rest = l.substring(l.indexOf("Cookie Buff") + 11).replaceFirst("^[:\\s]+", "").trim();
                for (int j = i + 1; rest.isEmpty() && j < lines.size() && j < i + 3; j++) rest = lines.get(j).trim();
                if (!rest.isEmpty()) {
                    cookie = rest;
                    CookieBuff.fromTab(rest, System.currentTimeMillis());
                }
            } else if ((m = SKILL_TAB.matcher(l)).find()) {
                String name = m.group(1) != null ? m.group(1) : m.group(4);
                String lvl = m.group(2) != null ? m.group(2) : m.group(5);
                double pct = m.group(3) != null ? Double.parseDouble(m.group(3)) : 100;
                int level = lvl.chars().allMatch(Character::isDigit) ? Integer.parseInt(lvl) : roman(lvl);
                if (!REAL_SKILLS.contains(name)) continue; // tab lists collections in the same format
                tabSkills.put(name, new double[]{level, pct});
                Store.I.observedSkills.put(name, new double[]{level, pct});
            }
        }
        // footer has "Cookie Buff" on one line and the time left (or "Not active!") on the next
        int ci = tabFooter.toLowerCase().indexOf("cookie buff");
        if (ci >= 0) {
            for (String part : tabFooter.substring(ci + 11).split("\n")) {
                String p = part.replaceFirst("^[:\\s]+", "").trim();
                if (p.isEmpty()) continue;
                cookie = p.length() > 60 ? p.substring(0, 60) : p;
                CookieBuff.fromTab(p, System.currentTimeMillis());
                break;
            }
        } else {
            CookieBuff.noTabText();
            if (!loggedFooter && !tabFooter.isEmpty()) {
                loggedFooter = true;
                dev.zmuzy.sbc.SbcMod.LOG.info("[sbc] tab footer has no Cookie Buff line: {}", tabFooter.replace("\n", " | "));
            }
        }
    }

    private static void resolveIsland() {
        String key = "";
        if (!apiMode.isEmpty()) key = MODE_TO_ISLAND.getOrDefault(apiMode, "");
        if (key.isEmpty() && !apiMap.isEmpty()) key = normalise(apiMap);
        if (key.isEmpty()) {
            for (String l : tab) {
                Matcher m = AREA_TAB.matcher(l);
                if (m.find()) {
                    islandName = m.group(1).trim();
                    key = normalise(islandName);
                    break;
                }
            }
        } else if (!apiMap.isEmpty()) {
            islandName = apiMap;
        }
        island = key;
        if (islandName.isEmpty()) islandName = pretty(key);
    }

    public static String normalise(String name) {
        String k = name.toUpperCase(Locale.ROOT).replace("'", "").replaceAll("[^A-Z0-9]+", "_").replaceAll("^_|_$", "");
        return NAME_ALIASES.getOrDefault(k, k);
    }

    public static String pretty(String key) {
        if (key == null || key.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String w : key.toLowerCase(Locale.ROOT).split("_")) {
            if (w.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
        }
        return sb.toString();
    }

    public static long num(String s) {
        try {
            return Long.parseLong(s.replace(",", "").trim());
        } catch (Exception e) {
            return -1;
        }
    }

    public static long parseShort(String s) {
        try {
            String t = s.replace(",", "").trim().toLowerCase(Locale.ROOT);
            double mul = 1;
            if (t.endsWith("k")) mul = 1e3;
            else if (t.endsWith("m")) mul = 1e6;
            else if (t.endsWith("b")) mul = 1e9;
            if (mul != 1) t = t.substring(0, t.length() - 1);
            return (long) (Double.parseDouble(t) * mul);
        } catch (Exception e) {
            return -1;
        }
    }

    public static int roman(String r) {
        int total = 0, prev = 0;
        for (int i = r.length() - 1; i >= 0; i--) {
            int v = switch (r.charAt(i)) { case 'I' -> 1; case 'V' -> 5; case 'X' -> 10; case 'L' -> 50; case 'C' -> 100; default -> 0; };
            total += v < prev ? -v : v;
            prev = Math.max(prev, v);
        }
        return total;
    }
}
