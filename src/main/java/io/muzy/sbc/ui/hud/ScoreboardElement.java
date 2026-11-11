// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.ui.hud;

import io.muzy.sbc.SbcMod;
import io.muzy.sbc.chat.Party;
import io.muzy.sbc.core.Config;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Mayor;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Store;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.ui.HudLayout.Zone;
import io.muzy.sbc.ui.Ui;
import io.muzy.sbc.ui.theme.Modern;
import io.muzy.sbc.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// custom scoreboard built like skyhannis: you pick entries + order, each claims the sidebar lines it knows
// through skyhannis own line patterns (ported to patterns/skyhanni_scoreboard.json)
public final class ScoreboardElement extends HudElement {
    public static final ScoreboardElement I = new ScoreboardElement();

    public static final List<String> ALL = List.of("TITLE", "LOBBY_CODE", "DATE", "TIME", "ISLAND", "LOCATION", "PLAYER_AMOUNT", "SKYBLOCK_XP",
            "PURSE", "BANK", "BITS", "MOTES", "COPPER", "GEMS", "SOULFLOW", "NORTH_STARS", "HEAT", "COLD", "SOWDUST", "VISITING", "QUIVER",
            "POWER", "TUNING", "COOKIE", "GROWTH_STAGE", "POWDER", "OBJECTIVE", "SLAYER", "EVENTS", "PARTY", "MAYOR", "FOOTER", "EMPTY_LINE", "PROFILE", "CHUNKED_STATS");
    public static final List<String> DEFAULT = List.of("TITLE", "LOBBY_CODE", "EMPTY_LINE", "DATE", "TIME", "ISLAND", "LOCATION", "PLAYER_AMOUNT",
            "EMPTY_LINE", "SKYBLOCK_XP", "EMPTY_LINE", "MOTES", "PURSE", "BANK", "SOULFLOW", "VISITING", "COPPER", "GEMS", "COLD", "NORTH_STARS",
            "HEAT", "SOWDUST", "EVENTS", "EMPTY_LINE", "BITS", "QUIVER", "TUNING", "POWER", "OBJECTIVE", "COOKIE", "GROWTH_STAGE", "EMPTY_LINE", "SLAYER",
            "POWDER", "PARTY", "MAYOR");
    public static final Map<String, String> NAMES = new LinkedHashMap<>();

    static {
        NAMES.put("TITLE", "Title");
        NAMES.put("LOBBY_CODE", "Date and lobby code");
        NAMES.put("DATE", "SkyBlock date");
        NAMES.put("TIME", "SkyBlock time");
        NAMES.put("ISLAND", "Island");
        NAMES.put("LOCATION", "Location (area)");
        NAMES.put("PLAYER_AMOUNT", "Players on the island");
        NAMES.put("SKYBLOCK_XP", "SkyBlock level");
        NAMES.put("PURSE", "Purse");
        NAMES.put("BANK", "Bank");
        NAMES.put("BITS", "Bits");
        NAMES.put("MOTES", "Motes");
        NAMES.put("COPPER", "Copper");
        NAMES.put("GEMS", "Gems");
        NAMES.put("SOULFLOW", "Soulflow");
        NAMES.put("NORTH_STARS", "North Stars");
        NAMES.put("HEAT", "Heat");
        NAMES.put("COLD", "Cold");
        NAMES.put("SOWDUST", "Sowdust");
        NAMES.put("VISITING", "Visiting");
        NAMES.put("QUIVER", "Quiver");
        NAMES.put("POWER", "Power (Maxwell)");
        NAMES.put("TUNING", "Tuning");
        NAMES.put("COOKIE", "Booster cookie");
        NAMES.put("GROWTH_STAGE", "Next growth stage");
        NAMES.put("POWDER", "Powders");
        NAMES.put("OBJECTIVE", "Objective");
        NAMES.put("SLAYER", "Slayer quest");
        NAMES.put("EVENTS", "Events");
        NAMES.put("PARTY", "Party");
        NAMES.put("MAYOR", "Mayor");
        NAMES.put("FOOTER", "Footer (www.hypixel.net)");
        NAMES.put("EMPTY_LINE", "Empty line");
        NAMES.put("PROFILE", "Profile");
        NAMES.put("CHUNKED_STATS", "Several stats on few lines");
    }

    // skyhanni pattern groups that count as events (everything thats not a stat)
    private static final String[] EVENT_GROUPS = {"multiuse", "dungeon", "kuudra", "farming", "mining", "combat", "misc", "events", "rift", "carnival", "galatea", "safari"};
    private static final List<String> NOT_EVENTS = List.of("SH.sb.farming.plot", "SH.sb.mining.powder", "SH.sb.combat.slayerQuest", "SH.sb.misc.objective",
            "SH.sb.misc.thirdObjectiveLine", "SH.sb.misc.wtfAreThoseLines");
    private static final Pattern PURSE_LINE = Pattern.compile("(?:§.)*(?:Purse|Piggy): (?:§.)*(?<coins>[\\d,]+).*");
    private static final Pattern BITS_LINE = Pattern.compile("(?:§.)*Bits: (?:§.)*(?<bits>[\\d,]+).*");
    private static final Pattern PLAYERS = Pattern.compile("^Players \\((\\d+)\\)$");
    private static final Pattern SB_LEVEL = Pattern.compile("^SB Level: \\[(\\d+)\\] (\\d+).*$");
    private static final Pattern POWDER_TAB = Pattern.compile("^(Mithril|Gemstone|Glacite)(?: Powder)?:\\s*([\\d,.]+[kKmM]?)$");
    private static final Pattern DATE_PART = Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{2,4})");

    private List<String> cache = List.of();
    private long cacheAt;
    private Object cacheKey;

    private ScoreboardElement() {
        super("board", "Scoreboard", "GUI", Zone.TOP_RIGHT, 20, "board");
    }

    // board replaces the vanilla sidebar
    public static boolean on() {
        return Features.on("board") && (GameState.inSkyBlock || SbcMod.DEV) && !GameState.scoreboardRaw.isEmpty();
    }

    @Override
    public boolean visible() {
        return on() && !lines().isEmpty();
    }

    @Override
    public String heading() {
        return "";
    }

    public static List<String> entries() {
        if (Config.I.boardEntries.isEmpty()) Config.I.boardEntries.addAll(DEFAULT);
        return Config.I.boardEntries;
    }

    private static Option opt(String key) {
        Feature f = Features.get("board");
        return f == null ? null : f.option(key);
    }

    private static boolean optBool(String key, boolean def) {
        Option o = opt(key);
        return o == null ? def : o.bool();
    }

    private static String optText(String key, String def) {
        Option o = opt(key);
        return o == null ? def : o.kind == Option.Kind.ENUM ? o.choice() : o.text();
    }

    private static int optInt(String key, int def) {
        Option o = opt(key);
        return o == null ? def : o.integer();
    }

    private static String num(long v) {
        if (v < 0) return "?";
        return "Short".equals(optText("numbers", "Long")) ? io.muzy.sbc.core.Prices.coins(v) : String.format(Locale.ROOT, "%,d", v);
    }

    private static String colours(String s) {
        return s.replace("&&", "§").replace('&', '§');
    }

    private static String stat(String label, String value, String colour) {
        return "§7" + label + ": " + colour + value;
    }

    // colored lines, rebuilt max 4 times a sec
    public List<String> lines() {
        long now = System.currentTimeMillis();
        Object key = GameState.scoreboardRaw;
        if (key == cacheKey && now - cacheAt < 250) return cache;
        // from skyhanni CustomScoreboard: tab list not loaded yet after a switch, keep what was there
        if (optBool("keepOnSwitch", false) && GameState.tab.isEmpty() && !cache.isEmpty()) return cache;
        cacheAt = now;
        cacheKey = key;
        cache = build(GameState.scoreboardRaw, GameState.scoreboard, GameState.tab);
        return cache;
    }

    // --- building ---

    private static final class Lines {
        final List<String> fmt, plain;
        final boolean[] used;

        Lines(List<String> raw, List<String> plain) {
            this.fmt = new ArrayList<>(raw.size());
            for (String r : raw) fmt.add(GameState.formatLine(r));
            this.plain = plain;
            used = new boolean[Math.min(fmt.size(), plain.size())];
        }

        // first unused line matching a skyhanni key, or -1
        int find(String key) {
            Pattern p = Patterns.get(key);
            for (int i = 0; i < used.length; i++) if (!used[i] && p.matcher(fmt.get(i)).matches()) return i;
            return -1;
        }

        int findPlainPrefix(String... prefixes) {
            for (int i = 0; i < used.length; i++) {
                if (used[i]) continue;
                String t = plain.get(i).trim();
                for (String p : prefixes) if (t.startsWith(p)) return i;
            }
            return -1;
        }

        String take(int i) {
            used[i] = true;
            return fmt.get(i).trim();
        }

        // grabs the first line matching the key, or null
        String take(String key) {
            int i = find(key);
            return i < 0 ? null : take(i);
        }

        boolean matches(int i, String key) {
            return i >= 0 && i < used.length && Patterns.get(key).matcher(fmt.get(i)).matches();
        }
    }

    // board from the raw sidebar (hypixels split lines), plain sidebar and plain tab
    public static List<String> build(List<String> raw, List<String> plainIn, List<String> tab) {
        Lines L = new Lines(raw, plainIn);
        List<String> out = new ArrayList<>();
        for (String e : entries()) {
            switch (e) {
                case "TITLE" -> out.add("§#TITLE#" + colours(optText("title", "&6&lSKYBLOCK")));
                case "EMPTY_LINE" -> out.add("");
                case "FOOTER" -> {
                    L.take("SH.sb.main.footer");
                    for (String f : optText("footer", "&ewww.hypixel.net").split("\\\\n")) out.add(colours(f));
                }
                case "LOBBY_CODE" -> {
                    int i = L.find("SH.sb.main.lobbyCode");
                    if (i >= 0) {
                        String line = L.take(i);
                        Matcher m = Patterns.get("SH.sb.main.lobbyCode").matcher(line);
                        Matcher d = DATE_PART.matcher(GameState.strip(line));
                        if (m.matches()) {
                            String day = optBool("lobbyDate", true) && d.find() ? BoardExtras.date(d.group(1), optText("dateFormat", "As Hypixel writes it")) : "";
                            out.add((day.isEmpty() ? "" : "§7" + day + " ") + "§8" + GameState.strip(m.group("code")).trim());
                        }
                    }
                }
                case "DATE" -> add(out, io.muzy.sbc.misc.SmallMisc.monthColored(L.take("SH.sb.main.date")));
                case "TIME" -> {
                    int i = L.find("SH.sb.main.time");
                    if (i >= 0) {
                        Matcher m = Patterns.get("SH.sb.main.time").matcher(L.take(i));
                        boolean h24 = optBool("time24", false), exact = optBool("timeExact", false);
                        if (h24 || exact) out.add(BoardExtras.time(System.currentTimeMillis(), h24, exact, m.matches() ? m.group("symbol") : ""));
                        else out.add(L.fmt.get(i).trim());
                    }
                }
                case "PROFILE" -> out.add(BoardExtras.profile(GameState.profileType, GameState.profileName, optBool("profileName", false), L.plain));
                case "CHUNKED_STATS" -> out.addAll(BoardExtras.chunked(chunks(L), optInt("chunkMax", 3)));
                case "ISLAND" -> {
                    if (!GameState.islandName.isEmpty()) out.add("§7Island: §a" + GameState.islandName);
                    else if (!GameState.island.isEmpty()) out.add("§7Island: §a" + GameState.pretty(GameState.island));
                }
                case "LOCATION" -> {
                    // our own area line detection (icon glyphs), the skyhanni pattern takes the plot line
                    for (int i = 0; i < L.used.length; i++) {
                        String p = L.plain.get(i).trim();
                        if (!L.used[i] && (p.startsWith("⏣") || p.startsWith("ф") || (!p.isEmpty() && p.charAt(0) >= 0xE000 && p.charAt(0) <= 0xF8FF && p.length() > 2 && p.charAt(1) == ' '))) {
                            out.add(L.take(i));
                            break;
                        }
                    }
                    add(out, L.take("SH.sb.farming.plot"));
                }
                case "PLAYER_AMOUNT" -> {
                    for (String t : tab) {
                        Matcher m = PLAYERS.matcher(t.trim());
                        if (m.matches()) {
                            out.add(stat("Players", m.group(1) + (optBool("maxPlayers", true) ? "§7/§a" + BoardExtras.maxPlayers(GameState.apiMode, serverId(L)) : ""), "§a"));
                            break;
                        }
                    }
                }
                case "SKYBLOCK_XP" -> {
                    boolean done = false;
                    for (String t : tab) {
                        Matcher m = SB_LEVEL.matcher(t.trim());
                        if (m.matches()) {
                            out.add(stat("SB Level", m.group(1), "§b"));
                            out.add(stat("XP", m.group(2) + "§3/§b100", "§b"));
                            done = true;
                            break;
                        }
                    }
                    if (!done && !GameState.sbLevel.isEmpty()) out.add(stat("SB Level", GameState.sbLevel, "§b"));
                }
                case "PURSE" -> {
                    int i = -1;
                    for (int k = 0; k < L.used.length; k++) if (!L.used[k] && PURSE_LINE.matcher(L.fmt.get(k)).matches()) {
                        i = k;
                        break;
                    }
                    long coins = GameState.purse;
                    if (i >= 0) {
                        Matcher m = PURSE_LINE.matcher(L.take(i));
                        if (m.matches()) try {
                            coins = Long.parseLong(m.group("coins").replace(",", ""));
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    if (coins >= 0 && !(coins == 0 && optBool("hideEmpty", true))) out.add(stat(i >= 0 && L.plain.get(i).trim().startsWith("Piggy") && !optBool("piggyAsPurse", true) ? "Piggy" : "Purse", num(coins) + changed("purse", coins, "§6"), "§6"));
                }
                case "BANK" -> {
                    if (GameState.bank > 0 || (GameState.bank == 0 && !optBool("hideEmpty", true))) out.add(stat("Bank", num(GameState.bank), "§6"));
                }
                case "BITS" -> {
                    long bits = GameState.bits;
                    for (int k = 0; k < L.used.length; k++) if (!L.used[k] && BITS_LINE.matcher(L.fmt.get(k)).matches()) {
                        Matcher m = BITS_LINE.matcher(L.take(k));
                        if (m.matches()) try {
                            bits = Long.parseLong(m.group("bits").replace(",", ""));
                        } catch (NumberFormatException ignored) {
                        }
                        break;
                    }
                    // -1 till the sb menu showed it, 0 = nothing to claim: both are just the bits
                    long claim = optBool("unclaimedBits", true) ? io.muzy.sbc.core.Bits.available() : -1;
                    if (bits > 0 || claim > 0 || (bits == 0 && !optBool("hideEmpty", true))) out.add(stat("Bits", (claim > 0 ? num(bits) + "§7/§b" + num(claim) : num(bits)) + changed("bits", bits, "§b"), "§b"));
                }
                case "MOTES" -> {
                    String l = L.take("SH.sb.main.motes");
                    if (l != null) out.add(stat("Motes", GameState.strip(l).replaceFirst("^Motes:\\s*", "").trim(), "§d"));
                    else if (GameState.motes > 0) out.add(stat("Motes", num(GameState.motes), "§d"));
                }
                case "COPPER" -> {
                    String l = L.take("SH.sb.main.copper");
                    if (l != null) out.add(stat("Copper", GameState.strip(l).replaceFirst("^Copper:\\s*", "").trim(), "§c"));
                }
                case "GEMS" -> {
                    String l = L.take("SH.sb.main.gems");
                    if (l != null) out.add(stat("Gems", GameState.strip(l).replaceFirst("^Gems:\\s*", "").trim(), "§a"));
                }
                case "SOWDUST" -> {
                    String l = L.take("SH.sb.main.sowdust");
                    if (l == null) l = L.take("SH.sb.main.sowdustGained");
                    if (l != null) out.add(l);
                }
                case "NORTH_STARS" -> add(out, L.take("SH.sb.main.northStars"));
                case "VISITING" -> add(out, L.take("SH.sb.misc.visiting"));
                case "SOULFLOW" -> takePrefix(L, out, "Soulflow:");
                case "HEAT" -> takePrefix(L, out, "Heat:");
                case "COLD" -> takePrefix(L, out, "Cold:");
                case "QUIVER" -> {
                    String arrow = io.muzy.sbc.combat.Quiver.boardName();
                    if (!arrow.isEmpty()) {
                        takePrefix(L, new ArrayList<>(), "Quiver");
                        out.add(BoardExtras.arrow(arrow, io.muzy.sbc.combat.Quiver.boardAmount(), io.muzy.sbc.combat.Quiver.boardMax(), io.muzy.sbc.combat.Quiver.boardEndless(), optBool("arrowPercent", false), optBool("arrowColor", false)));
                        break;
                    }
                    takePrefix(L, out, "Quiver");
                    tabPrefix(tab, out, "Quiver:", "§7Quiver: §f");
                }
                case "POWER" -> {
                    tabPrefix(tab, out, "Power:", "§7Power: §a");
                    int mp = io.muzy.sbc.core.data.MaxwellData.magicalPower();
                    if (optBool("magicalPower", true) && mp > 0 && !out.isEmpty() && out.get(out.size() - 1).startsWith("§7Power: ")) out.set(out.size() - 1, out.get(out.size() - 1) + " §7(§6" + String.format(Locale.ROOT, "%,d", mp) + "§7)");
                }
                case "TUNING" -> tuning(out);
                case "COOKIE" -> {
                    String cookie = GameState.cookieStatus();
                    if (!cookie.isEmpty()) {
                        boolean off = cookie.toLowerCase(Locale.ROOT).contains("not active");
                        if (!off || !optBool("hideEmpty", true)) out.add("§dCookie Buff§f: " + (off ? "§cNot Active" : cookie));
                    }
                }
                case "GROWTH_STAGE" -> {
                    String growth = io.muzy.sbc.garden.GrowthStage.line();
                    if (!growth.isEmpty()) out.add(growth);
                }
                case "POWDER" -> {
                    List<String> p = new ArrayList<>();
                    for (String t : tab) {
                        Matcher m = POWDER_TAB.matcher(t.trim());
                        if (m.matches()) p.add(" §7- §f" + m.group(1) + ": " + switch (m.group(1)) {
                            case "Mithril" -> "§2";
                            case "Gemstone" -> "§d";
                            default -> "§b";
                        } + m.group(2));
                    }
                    while (true) {
                        String l = L.take("SH.sb.mining.powder");
                        if (l == null) break;
                        if (p.isEmpty()) out.add(l);
                    }
                    if (!p.isEmpty()) {
                        out.add("§9§lPowder");
                        out.addAll(p);
                    }
                }
                case "OBJECTIVE" -> {
                    int i = L.find("SH.sb.misc.objective");
                    if (i >= 0) {
                        out.add(L.take(i));
                        if (i + 1 < L.used.length && !L.plain.get(i + 1).isBlank()) out.add(L.take(i + 1));
                        int j = i + 2;
                        while (L.matches(j, "SH.sb.misc.thirdObjectiveLine")) out.add(L.take(j++));
                        while (true) {
                            String w = L.take("SH.sb.misc.wtfAreThoseLines");
                            if (w == null) break;
                            out.add(w);
                        }
                    }
                }
                case "SLAYER" -> {
                    int i = L.find("SH.sb.combat.slayerQuest");
                    if (i >= 0) {
                        out.add(L.take(i));
                        for (int j = i + 1; j < L.used.length && j <= i + 2; j++) if (!L.plain.get(j).isBlank()) out.add(L.take(j));
                    }
                }
                case "PARTY" -> {
                    int i = L.findPlainPrefix("Party");
                    if (i >= 0) {
                        out.add(L.take(i));
                        int max = optInt("partyMax", 4);
                        for (int j = i + 1, k = 0; j < L.used.length && k < max && !L.plain.get(j).isBlank(); j++, k++) out.add(L.take(j));
                    }
                }
                case "MAYOR" -> {
                    // from skyhanni ScoreboardElementMayor, when the next one comes + jerrys temp mayor. time first, needs
                    // nothing downloaded
                    String election = "Election in: §e" + io.muzy.sbc.core.SbTime.dur(Mayor.nextElection(System.currentTimeMillis()) - System.currentTimeMillis());
                    if (Mayor.name.isEmpty()) {
                        if (optBool("mayorTime", true)) out.add("§7" + election);
                    } else {
                        out.add("§b" + Mayor.name + (Mayor.minister.isEmpty() ? "" : " §7(" + Mayor.minister + ")"));
                        if (optBool("mayorTime", true)) out.add(" §7" + election);
                        if (optBool("mayorPerks", true)) {
                            int k = 0;
                            for (Mayor.Perk p : Mayor.perks) {
                                if (k++ >= 3) break;
                                out.add(" §7- §e" + p.name());
                            }
                        }
                        if (optBool("mayorExtra", true) && !io.muzy.sbc.events.EventExtras.perkpocalypsePerk.isEmpty()) out.add(" §7- §e" + io.muzy.sbc.events.EventExtras.perkpocalypsePerk + " §7(Perkpocalypse)");
                    }
                }
                case "EVENTS" -> {
                    // from skyhanni EventActiveTablist / StartingSoonTablist / Broodmother: stuff only the tab says
                    if (optBool("tabEvents", true)) {
                        List<String> tabEvents = io.muzy.sbc.events.TabEvents.lines();
                        out.addAll(tabEvents);
                        out.addAll(io.muzy.sbc.events.TabEvents.fromCalendar(tabEvents, System.currentTimeMillis()));
                    }
                    for (int i = 0; i < L.used.length; i++) {
                        if (L.used[i] || L.plain.get(i).isBlank()) continue;
                        if (isEvent(L.fmt.get(i))) out.add(L.take(i));
                    }
                }
                default -> {
                }
            }
        }
        // whatever hypixel sent that nothing claimed stays, never lost (skyhanni calls these "Undetected lines")
        if (optBool("showUnknown", true)) {
            for (int i = 0; i < L.used.length; i++) {
                if (L.used[i]) continue;
                String p = L.plain.get(i).trim();
                if (p.isEmpty() || Patterns.get("SH.sb.main.footer").matcher(L.fmt.get(i).trim()).matches()) continue;
                out.add(L.fmt.get(i).trim());
            }
        }
        out = withContestRank(out, GameState.tab);
        out = withEventLines(out, GameState.tabFooter);
        if (optBool("hideEmpty", true)) {
            List<String> c = new ArrayList<>();
            for (String l : out) if (!(l.isEmpty() && (c.isEmpty() || c.get(c.size() - 1).isEmpty()))) c.add(l);
            while (!c.isEmpty() && c.get(c.size() - 1).isEmpty()) c.remove(c.size() - 1);
            out = c;
        }
        return out;
    }

    // from skyhanni ScoreboardElementTuning, from what maxwells menus showed last. not in the rift, same as theirs
    private static void tuning(List<String> out) {
        if ("THE_RIFT".equals(GameState.island)) return;
        out.addAll(tuningLines(Store.I.statTunings, Store.I.statTuningNames, Store.I.statTuningsRead, optBool("tuningCompact", false), optInt("tuningAmount", 2)));
    }

    // from skyhanni Tuning.getDisplay: first 3 on one line when compact, else as many as you want under a title.
    // each = color, value, icon
    static List<String> tuningLines(List<String> tunings, List<String> names, boolean read, boolean compact, int amount) {
        if (tunings.isEmpty()) return List.of(read ? "§cNo Maxwell Tunings :(" : "§cTalk to \"Maxwell\"!");
        String title = tunings.size() == 1 ? "Tuning" : "Tunings";
        if (compact) return List.of("§7" + title + ": " + String.join("§7, ", tunings.subList(0, Math.min(3, tunings.size()))));
        List<String> out = new ArrayList<>();
        out.add("§7" + title + ":");
        boolean named = names.size() == tunings.size();
        int shown = Math.min(Math.max(1, amount), tunings.size());
        for (int i = 0; i < shown; i++) out.add(" §7- §f" + (named ? names.get(i) + ": " : "") + tunings.get(i));
        return out;
    }

    // "72%" on a tuning is the stat, not progress
    static boolean tuningRow(String label) {
        String l = GameState.strip(label).replaceFirst("^\\s*-\\s*", "").trim();
        // players and the sb level say it in numbers and a percent, no bar either
        return l.startsWith("Tuning") || l.startsWith("Players") || l.startsWith("SB Level") || Store.I.statTuningNames.contains(l);
    }

    // from skyhanni ChunkedStatsLine: what has a value now, without stealing lines from their own entries
    private static List<String> chunks(Lines L) {
        List<String> out = new ArrayList<>();
        if (GameState.purse > 0) out.add("§6" + num(GameState.purse));
        chunk(out, L, "SH.sb.main.motes", "Motes", "§d");
        if (GameState.bank > 0) out.add("§6" + num(GameState.bank));
        if (GameState.bits > 0) out.add("§b" + num(GameState.bits));
        chunk(out, L, "SH.sb.main.copper", "Copper", "§c");
        chunk(out, L, "SH.sb.main.sowdust", "Sowdust", "§2");
        chunk(out, L, "SH.sb.main.gems", "Gems", "§a");
        chunk(out, L, "SH.sb.main.northStars", "North Stars", "§d");
        return out;
    }

    private static void chunk(List<String> out, Lines L, String key, String label, String color) {
        String v = BoardExtras.value(L.fmt, key, label);
        if (v != null && !v.equals("0")) out.add(color + v);
    }

    // lobby code like HypixelData.serverId, to spot a mega server
    private static String serverId(Lines L) {
        for (String f : L.fmt) {
            Matcher m = Patterns.get("SH.sb.main.lobbyCode").matcher(f);
            if (m.matches()) return GameState.strip(m.group("code")).trim();
        }
        return "";
    }

    private static String changed(String key, long value, String color) {
        if (!optBool("showChanges", false)) return "";
        return BoardExtras.change(key, value, System.currentTimeMillis(), ScoreboardElement::num, color);
    }

    // worst first, skyhanni ContestBracket colors
    private static final String[][] BRACKETS = {{"Bronze", "§c"}, {"Silver", "§f"}, {"Gold", "§6"}, {"Platinum", "§3"}, {"Diamond", "§b"}};
    private static final Pattern CONTEST_NEXT = Pattern.compile("^(\\w+) has (.+)$");

    // what only the tab says about a running contest under "Jacob's Contest: 1m 8s left": " Wheat ◆ Top 88.5%",
    // "Bronze has +8,229", next medal + how far. the one you hold is the one below
    static List<String> contestLines(List<String> tab) {
        return contestLines(tab, -1);
    }

    // held: the bracket the sidebar says you are in ("BRONZE with 30,000"), -1 when it says none. the tab's
    // "Bronze has" line can stay behind after you pass it, so a bracket you already hold is never the next one
    static List<String> contestLines(List<String> tab, int held) {
        String rank = null, medal = held >= 0 ? BRACKETS[held][1] + BRACKETS[held][0] + " " : null, next = null;
        for (int i = 0; i < tab.size(); i++) {
            if (!GameState.strip(tab.get(i)).trim().startsWith("Jacob's Contest")) continue;
            for (int j = i + 1; j < tab.size(); j++) {
                String line = GameState.strip(tab.get(j)).trim();
                if (line.isEmpty()) break;
                int at = line.indexOf('◆');
                if (at >= 0) {
                    rank = line.substring(at + 1).trim();
                    continue;
                }
                Matcher m = CONTEST_NEXT.matcher(line);
                if (!m.matches()) continue;
                for (int b = 0; b < BRACKETS.length; b++) {
                    if (!BRACKETS[b][0].equalsIgnoreCase(m.group(1)) || b <= held) continue;
                    next = "§7To " + BRACKETS[b][1] + BRACKETS[b][0] + "§7: §f" + m.group(2);
                    if (b > 0 && held < 0) medal = BRACKETS[b - 1][1] + BRACKETS[b - 1][0] + " ";
                }
            }
            break;
        }
        List<String> out = new ArrayList<>();
        if (rank != null) out.add("§7Rank: " + (medal == null ? "" : medal) + "§f" + rank);
        if (next != null) out.add(next);
        return out;
    }

    // "61 Green, 5 Purple (86 pts.)" in a few letters, so the board keeps its width
    private static final Pattern CANDY = Pattern.compile("(?:§.)*([\\d,]+) (?:§.)*Green(?:§.)*, (?:§.)*([\\d,]+) (?:§.)*Purple(?:§.)*\\s*(?:§.)*\\((?:§.)*([\\d,]+) (?:§.)*pts\\.?(?:§.)*\\).*");

    static String candy(String text) {
        Matcher m = CANDY.matcher(text);
        if (!m.matches()) return "§f" + text;
        return "§a" + m.group(1) + " §8· §5" + m.group(2) + " §8· §6" + m.group(3) + " pts";
    }

    // from skyhanni EventWinter: skip lines that only say Soon!. EventSpooky: your candy from the tab footer
    // under the festival line
    static List<String> withEventLines(List<String> lines, String footer) {
        List<String> out = new ArrayList<>(lines.size());
        for (String l : lines) {
            if (l.endsWith("Soon!") && Patterns.get("SH.sb.events.winterNextWave").matcher(l.trim()).matches()) continue;
            out.add(l);
            if (footer != null && Patterns.get("SH.sb.events.spooky").matcher(l.trim()).matches()) {
                for (String f : footer.split("\n")) {
                    if (!f.startsWith("Your Candy:")) continue;
                    out.add("§7Candy: " + candy(f.substring("Your Candy:".length()).trim()));
                    break;
                }
            }
        }
        return out;
    }

    // rank goes right under the contest lines like on hypixels sidebar
    private static final Pattern HELD = Pattern.compile("^(BRONZE|SILVER|GOLD|PLATINUM|DIAMOND) with .*");

    // the bracket the sidebar says you are in, -1 for none
    static int heldBracket(List<String> lines) {
        for (String l : lines) {
            Matcher m = HELD.matcher(GameState.strip(l).trim());
            if (!m.matches()) continue;
            for (int b = 0; b < BRACKETS.length; b++) if (BRACKETS[b][0].equalsIgnoreCase(m.group(1))) return b;
        }
        return -1;
    }

    static List<String> withContestRank(List<String> lines, List<String> tab) {
        List<String> rank = contestLines(tab, heldBracket(lines));
        if (rank.isEmpty()) return lines;
        int after = -1;
        for (int i = 0; i < lines.size(); i++) {
            String plain = GameState.strip(lines.get(i)).trim();
            if (plain.startsWith("Collected") || after < 0 && plain.startsWith("Jacob's Contest")) after = i;
        }
        if (after < 0) return lines;
        List<String> out = new ArrayList<>(lines);
        out.addAll(after + 1, rank);
        return out;
    }

    private static void add(List<String> out, String line) {
        if (line != null) out.add(line);
    }

    private static void takePrefix(Lines L, List<String> out, String prefix) {
        int i = L.findPlainPrefix(prefix);
        if (i >= 0) out.add(L.take(i));
    }

    private static void tabPrefix(List<String> tab, List<String> out, String prefix, String format) {
        for (String t : tab) {
            String s = t.trim();
            if (s.startsWith(prefix)) {
                out.add(format + s.substring(prefix.length()).trim());
                return;
            }
        }
    }

    private static List<Pattern> eventPatterns;

    // a sidebar line skyhannis event patterns know
    static boolean isEvent(String fmt) {
        if (eventPatterns == null) {
            List<Pattern> ps = new ArrayList<>();
            for (String g : EVENT_GROUPS) for (String key : Patterns.list("SH.sb.group." + g)) if (!NOT_EVENTS.contains(key)) ps.add(Patterns.get(key));
            // from skyhanni EventVoting: these 3 are in the main group
            for (String key : List.of("SH.sb.main.yearVotes", "SH.sb.main.votes", "SH.sb.main.waitingForVote")) ps.add(Patterns.get(key));
            eventPatterns = ps;
        }
        String t = fmt.trim();
        for (Pattern p : eventPatterns) if (p.matcher(t).matches()) return true;
        return false;
    }

    // --- drawing ---

    private static String text(String l) {
        return l.startsWith("§#TITLE#") ? l.substring(8) : l;
    }

    // you read this most so its closer to game text size than the other corner cards
    @Override
    public float defaultScale() {
        return io.muzy.sbc.ui.HudLayout.largeScale();
    }

    // width + height only change with the rows (every 250ms max), theme or style, but the layout asks a few times a frame
    // so keep the last answer and add it up again only when one of those changed
    private List<Item> sizedFor;
    private Theme sizedTheme;
    private int sizedPad = -1, sizedRh = -1, sizedW, sizedH;

    private void size() {
        Theme t = Theme.current();
        ElementStyle st = style();
        int pad = pad(t, st), rh = rowH(t, st);
        List<Item> items = items();
        if (items == sizedFor && t == sizedTheme && pad == sizedPad && rh == sizedRh) return;
        int w = MIN_WIDTH;
        for (Item it : items) w = Math.max(w, it.needs(Ui.MEASURE) + 2 * pad);
        w = Math.min(MAX_WIDTH, w);
        int h = (items.isEmpty() || items.get(0).kind != Kind.TITLE ? pad : 0) + Math.max(2, pad - 2);
        for (Item it : items) h += it.height(rh);
        sizedW = w + (w & 1);
        sizedH = h + (h & 1);
        sizedFor = items;
        sizedTheme = t;
        sizedPad = pad;
        sizedRh = rh;
    }

    @Override
    public int width() {
        size();
        return sizedW;
    }

    @Override
    public int height() {
        size();
        return sizedH;
    }

    @Override
    public void draw(Ui ui, GuiGraphicsExtractor g, int x, int y) {
        Theme t = Theme.current();
        ElementStyle st = style();
        List<Item> items = items();
        int w = drawWidth(), h = height(), pad = pad(t, st), rh = rowH(t, st), cx = x + pad, cw = w - 2 * pad;
        int accent = st.titleColor() != null ? st.titleColor() : accent();
        boolean shadow = st.shadow() == null || st.shadow(), titled = !items.isEmpty() && items.get(0).kind == Kind.TITLE;
        String align = optText("align", "Left");
        t.panel(ui, x, y, w, h, st);
        if (!titled) Modern.edge(ui, x, y, h, accent);
        int cy = titled ? y : y + pad;
        for (Item it : items) {
            switch (it.kind) {
                case TITLE -> drawBand(ui, it, x, cy, w, accent, st.titleColor(), shadow);
                case RULE -> Modern.rule(ui, cx, cy + 2, cw);
                case SECTION -> drawSection(ui, it, cx, cy, cw, align, shadow);
                case ROW -> drawRow(ui, it, cx, cy, cw, rh, accent, st, align, shadow);
                default -> {
                }
            }
            cy += it.height(rh);
        }
    }

    // lines exactly like build() makes them, default order
    private List<String> demoLines() {
        String title = optText("title", "&6&lSKYBLOCK");
        if (!title.equals(demoTitle)) {
            demoTitle = title;
            demoLines = List.of("§#TITLE#" + colours(title), "§711/03/26 §8m84AF", "", "Late Autumn 13th", "§72:20am §5☽", "§7Island: §aHub", "§7⏣ §bVillage", "§7Players: §a24", "",
                    "§7SB Level: §b196", "§7XP: §b52§3/§b100", "",
                    "§7Purse: §61,423,085,776", "§7Bank: §61,250,000,000", "§aTraveling Zoo§f 12:34", "",
                    "§7Bits: §b140,965", "Objective", "§eTalk to the Mayor", "§dCookie Buff§f: 3d 12h 5m", "§aGrowth Stage§f: §a1h 40m", "",
                    "Slayer Quest", "§5Voidgloom Seraph IV", "§7(§e1,234§7/§c1,500§7) §fCombat XP",
                    "§9§lParty (2)", "§fBlaze_Hunter", "§fIronLung_77",
                    "§bDerpy §7(Aatrox)", " §7- §eTURBO MINIONS!!!", " §7- §eDOUBLE MOBS HP!!!", " §7- §eMOAR SKILLZ!!!", " §7- §eSlayer XP Buff (Minister)");
        }
        return demoLines;
    }

    // --- classic card: same lines, read for what they say ---

    private static final int MIN_WIDTH = 132, MAX_WIDTH = 236, SECTION_GAP = 4, G_STATS = 0, G_EVENT = 1, G_SECTION = 2;
    private static final int MOTES = 0xFFFF8FD8, COPPER = 0xFFEC9A62, SOWDUST = 0xFF9BD96B, SOULFLOW = 0xFF4FD8C4, STARS = 0xFFE59CFF, HEAT = 0xFFFF8F6B, COLD = 0xFF9BE7FF,
            MITHRIL = 0xFF4FE3A6, GEMSTONE = 0xFFFF7FD4, GLACITE = 0xFF9BE7FF, COOKIE = 0xFFFFB3D9, OBJECTIVE = 0xFFFFE27A, SLAYER = 0xFFFF5252, POWDER = 0xFF58C7FF, PARTY = 0xFF7FA8FF, MAYOR = 0xFF5CD8FF, PERK = 0xFFE8DDB5;

    private static final Pattern LOBBY = Pattern.compile("^§7(\\d{1,2}/\\d{1,2}/\\d{2,4})? §8(\\S*)$");
    private static final Pattern TIME = Pattern.compile("^(?:§7)?(\\d+:\\d+(?:am|pm))\\s*(.*)$");
    private static final Pattern GLYPHED = Pattern.compile("^((?:§.)*[^§\\s])\\s+(.*)$");
    private static final Pattern PESTS = Pattern.compile("^(.*?)\\s*((?:§.)*[\\uE000-\\uF8FF](?:§.)*\\s*x\\d+)$");
    private static final Pattern FRACTION = Pattern.compile("^\\(?(\\d[\\d.,]*[kKmMbB]?)/(\\d[\\d.,]*[kKmMbB]?)\\)?\\s*(.*)$");
    private static final Pattern VISITING = Pattern.compile("^✌ \\((\\d+)/(\\d+)\\)$");
    private static final Pattern EVENT_TIME = Pattern.compile("^(.*?)§f (\\d+(?::\\d+)*)$");
    private static final Pattern TIER = Pattern.compile("^(.*\\S)\\s+(I{1,3}|IV|V)$");
    private static final Pattern COUNT = Pattern.compile("^(.*?)\\s*\\((\\d+)\\)$");
    private static final Pattern LEADER = Pattern.compile("^(.*?)\\s*\\(Leader\\)$");
    private static final Pattern MAYOR_LINE = Pattern.compile("^§b(.+?)(?: §7\\((.+)\\))?$");
    private static final Pattern MINISTER_PERK = Pattern.compile("^(.*?)\\s*\\(Minister\\)$");
    // hypixels "+5" after purse/bits, on the sidebar but not in the boards own line
    private static final Pattern GAIN = Pattern.compile("^\\s*(?:§.)*(Purse|Piggy|Bits): (?:§.)*[\\d,.]+\\s*(?:§.)*\\((?:§.)*([+-][\\d,.kKmMbB]+)(?:§.)*\\).*$");
    // same "(+5)" where it stays in the line (motes, copper, sowdust etc)
    private static final Pattern VALUE_GAIN = Pattern.compile("^(.*?)\\s*(?:§.)*\\((?:§.)*([+-][\\d,.kKmMbB]+)(?:§.)*\\)(?:§.)*$");

    private record Stat(int color, String item) {}

    private static final Map<String, Stat> STATS = Map.ofEntries(
            Map.entry("purse", new Stat(Modern.GOLD, "gold_ingot")), Map.entry("piggy", new Stat(Modern.GOLD, "gold_ingot")), Map.entry("bank", new Stat(Modern.GOLD, "gold_block")),
            Map.entry("bits", new Stat(Modern.AQUA, "diamond")), Map.entry("motes", new Stat(MOTES, "pink_dye")), Map.entry("copper", new Stat(COPPER, "copper_ingot")),
            Map.entry("gems", new Stat(Modern.GOOD, "emerald")), Map.entry("sowdust", new Stat(SOWDUST, "wheat")), Map.entry("soulflow", new Stat(SOULFLOW, "soul_lantern")),
            Map.entry("north stars", new Stat(STARS, "nether_star")), Map.entry("heat", new Stat(HEAT, "blaze_powder")), Map.entry("cold", new Stat(COLD, "packed_ice")),
            Map.entry("island", new Stat(Modern.GOOD, "grass_block")), Map.entry("players", new Stat(Modern.GOOD, "player_head")), Map.entry("quiver", new Stat(Modern.INK, "arrow")),
            Map.entry("power", new Stat(Modern.GOOD, "redstone")), Map.entry("tuning", new Stat(Modern.INK, "note_block")), Map.entry("growth stage", new Stat(Modern.GOOD, "wheat_seeds")), Map.entry("cookie buff", new Stat(COOKIE, "cookie")),
            Map.entry("mithril", new Stat(MITHRIL, "prismarine_crystals")), Map.entry("gemstone", new Stat(GEMSTONE, "amethyst_shard")), Map.entry("glacite", new Stat(GLACITE, "packed_ice")));
    private static final Set<String> CURRENCIES = Set.of("purse", "piggy", "bank", "bits", "gems", "copper", "motes", "sowdust");

    private enum Kind {TITLE, BLANK, RULE, GAP, SECTION, ROW}

    // one thing the classic card draws. with a value = label + value, without = follows the text alignment option
    private static final class Item {
        final Kind kind;
        String glyph = "", label = "", value = "", note = "", chip = "";
        int labelColor, valueColor, noteColor = Modern.DIM, chipColor, barColor, accent, indent, group = G_STATS, gap;
        double progress = -1;
        boolean bright, currency;
        ItemStack icon;
        // the title band's text + color, worked out once per title color setting (see drawBand)
        String bandText;
        int bandColor;
        Integer bandFor;

        Item(Kind kind) {
            this.kind = kind;
        }

        static Item gap(int size) {
            Item it = new Item(Kind.GAP);
            it.gap = size;
            return it;
        }

        boolean alone() {
            return value.isEmpty() && note.isEmpty() && chip.isEmpty() && icon == null;
        }

        int height(int rh) {
            return switch (kind) {
                case TITLE -> Modern.BAND + 2;
                case RULE -> Modern.RULE_BLOCK;
                case GAP -> gap;
                case BLANK -> 0;
                case SECTION -> rh;
                case ROW -> rh + (progress >= 0 ? Modern.METER_BLOCK : 0);
            };
        }

        int needs(Ui ui) {
            return switch (kind) {
                case TITLE -> ui.width(label) + 24;
                case SECTION -> ui.width(label) + 12;
                case ROW -> indent + (glyph.isEmpty() ? 0 : 12) + ui.width(label) + (value.isEmpty() ? 0 : ui.width(value) + 8) + (note.isEmpty() ? 0 : ui.width(note) + 5) + (chip.isEmpty() ? 0 : ui.width(chip) + 10) + (icon == null ? 0 : 12);
                default -> 0;
            };
        }
    }

    private List<Item> items = List.of();
    private List<String> itemsFor;
    private long itemsAt;
    private List<String> demoLines = List.of();
    private String demoTitle;

    private List<Item> items() {
        List<String> ls = demo ? demoLines() : lines();
        long now = System.currentTimeMillis();
        if (ls != itemsFor || now - itemsAt >= 250) {
            itemsFor = ls;
            itemsAt = now;
            items = spaced(parse(demo ? ls : withParty(ls)), optBool("hideEmpty", true));
        }
        return items;
    }

    // the party we follow from chat, how the sidebar would say it. build() only reads the sidebar which rarely
    // lists one. heading goes through build() so the PARTY entry places it. not in dungeons
    private static List<String> withParty(List<String> ls) {
        if (Party.members().isEmpty() || !entries().contains("PARTY") || "CATACOMBS".equals(GameState.island)) return ls;
        for (String p : GameState.scoreboard) if (p.trim().startsWith("Party")) return ls;
        String me = Minecraft.getInstance().getUser().getName();
        List<String> names = new ArrayList<>();
        // from skyhanni ScoreboardElementParty: leader first with a crown
        String leader = optBool("partyLeader", true) ? Party.leader() : null;
        if (leader != null) names.add(leader);
        for (String n : Party.members()) if (!n.equals(me) && !n.equals(leader)) names.add(n);
        if (names.isEmpty()) return ls;
        String head = "§9§lParty (" + names.size() + ")";
        List<String> raw = new ArrayList<>(List.of(head, "")), plain = new ArrayList<>(List.of(GameState.strip(head), ""));
        raw.addAll(GameState.scoreboardRaw);
        plain.addAll(GameState.scoreboard);
        List<String> out = new ArrayList<>(build(raw, plain, GameState.tab));
        int at = out.indexOf(head);
        if (at < 0) return ls;
        for (int k = Math.min(optInt("partyMax", 4), names.size()) - 1; k >= 0; k--) out.add(at + 1, "§f" + names.get(k) + (names.get(k).equals(leader) ? " §e♚" : ""));
        return out;
    }

    // hypixel dark codes in mixed text -> bright twins, dark ones are unreadable on see through glass
    private static String lift(String s) {
        if (s.indexOf('§') < 0) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            b.append(c);
            if (c != '§' || i + 1 >= s.length()) continue;
            char code = s.charAt(++i);
            b.append(switch (Character.toLowerCase(code)) {
                case '0', '8' -> '7';
                case '1' -> '9';
                case '2' -> 'a';
                case '3' -> 'b';
                case '4' -> 'c';
                case '5' -> 'd';
                default -> code;
            });
        }
        return b.toString();
    }

    // color code as the board draws it, dark ones lifted (unreadable on glass)
    private static int codeColor(char code) {
        return switch (Character.toLowerCase(code)) {
            case '0', '8' -> Modern.DIM;
            case '1', '9' -> PARTY;
            case '2' -> 0xFF4FCB6B;
            case '3' -> 0xFF35C9C9;
            case '4' -> 0xFFE5484D;
            case '5' -> 0xFFC77DFF;
            case '6' -> Modern.GOLD;
            case '7' -> Modern.LABEL;
            case 'a' -> Modern.GOOD;
            case 'b' -> Modern.AQUA;
            case 'c' -> Modern.BAD;
            case 'd' -> MOTES;
            case 'e' -> OBJECTIVE;
            default -> Modern.INK;
        };
    }

    // one color text with the code stripped so the board palette draws it. null if more than one
    private record Single(String text, int color) {
        static Single of(String s) {
            StringBuilder formats = new StringBuilder();
            int i = 0, color = 0;
            while (i + 1 < s.length() && s.charAt(i) == '§') {
                char c = Character.toLowerCase(s.charAt(i + 1));
                if ("klmno".indexOf(c) >= 0) formats.append('§').append(c);
                else if (c != 'r') color = codeColor(c);
                i += 2;
            }
            String rest = s.substring(i);
            return rest.indexOf('§') >= 0 ? null : new Single(formats + rest, color);
        }
    }

    private static void setValue(Item it, String s, int known) {
        Single one = Single.of(s);
        it.value = one != null ? one.text() : lift(s);
        it.valueColor = known != 0 ? known : one != null ? one.color() : 0;
    }

    // label keeps its color only if its not the usual grey/white
    private static void setLabel(Item it, String s) {
        String trimmed = s.replaceAll("(?:§.)+$", "");
        Single one = Single.of(trimmed);
        it.label = one != null ? one.text() : lift(trimmed);
        it.labelColor = one == null || one.color() == Modern.LABEL || one.color() == Modern.INK ? 0 : one.color();
    }

    private static String statKey(String label) {
        return GameState.strip(label).toLowerCase(Locale.ROOT).replaceAll("^[^a-z]+", "").replace(" powder", "").trim();
    }

    private static Stat statOf(String label) {
        return STATS.get(statKey(label));
    }

    // hypixels "+5": green/red, next to the value not inside
    private static void setGain(Item it, String gain) {
        it.note = gain;
        it.noteColor = gain.startsWith("-") ? Modern.BAD : Modern.GOOD;
    }

    private static String withoutThe(String name) {
        return GameState.strip(name).trim().replaceFirst("^The ", "");
    }

    private static ItemStack stack(String item) {
        return io.muzy.sbc.core.LegacyItems.stack("minecraft:" + item);
    }

    private static Item text(String line, int group) {
        Item it = new Item(Kind.ROW);
        it.group = group;
        Single one = Single.of(line);
        it.label = one != null ? one.text() : lift(line);
        it.labelColor = one != null ? one.color() : 0;
        return it;
    }

    // "(1,234/1,500) Combat XP" etc: 2 numbers, percent, meter. null for anything else
    private static Item fraction(String line, int group, char nowCode, char ofCode) {
        Matcher m = FRACTION.matcher(GameState.strip(line).trim());
        if (!m.matches()) return null;
        double a = RowText.number(m.group(1)), b = RowText.number(m.group(2));
        if (a < 0 || b <= 0 || a > b * 1.0001 || m.group(3).startsWith("/")) return null;
        double p = Math.min(1, a / b);
        Item it = new Item(Kind.ROW);
        it.group = group;
        it.label = m.group(3).isEmpty() ? "Progress" : m.group(3);
        it.value = Modern.fraction(m.group(1), m.group(2), nowCode, ofCode) + "  §7" + RowText.percent(p);
        it.progress = p;
        return it;
    }

    // any other line: "Key: value" aligned in its color, meter on "a/b" or "n%", else the line as is
    private static Item generic(String line, int group) {
        Matcher et = EVENT_TIME.matcher(line);
        if (et.matches()) {
            Item it = new Item(Kind.ROW);
            it.group = group;
            setLabel(it, et.group(1));
            it.value = et.group(2);
            return it;
        }
        Item progress = fraction(line, group, 'e', 'f');
        if (progress != null) return progress;
        if (Patterns.get("SH.sb.dungeon.teammates").matcher(line).matches()) {
            int cut = line.lastIndexOf(' ');
            Item it = new Item(Kind.ROW);
            it.group = group;
            it.label = lift(line.substring(0, cut));
            setValue(it, line.substring(cut + 1), 0);
            return it;
        }
        RowText.Shaped s = RowText.shape(line, "");
        if (s.value().isEmpty()) return text(line, group);
        Item it = new Item(Kind.ROW);
        it.group = group;
        Stat stat = statOf(s.label());
        boolean amount = stat != null && CURRENCIES.contains(statKey(s.label()));
        // RowText reads "a/b" as progress + adds a percent but bits/to claim are 2 amounts, keep the value as is
        String value = amount && line.contains(": ") ? line.substring(line.indexOf(": ") + 2).trim() : s.value();
        Matcher g = stat == null ? null : VALUE_GAIN.matcher(value);
        if (g != null && g.matches()) {
            value = g.group(1);
            setGain(it, g.group(2));
        }
        setLabel(it, s.label());
        setValue(it, value, stat == null ? 0 : stat.color());
        it.progress = amount || tuningRow(s.label()) ? -1 : s.progress();
        if (stat != null) {
            it.icon = stack(stat.item());
            it.labelColor = 0;
            it.currency = CURRENCIES.contains(statKey(s.label()));
        }
        return it;
    }

    private String gain(String key) {
        if (demo) return key.equals("Purse") ? "+1,250" : "";
        for (String l : GameState.scoreboardFormatted) {
            Matcher m = GAIN.matcher(l);
            if (m.matches() && m.group(1).equals(key)) return m.group(2);
        }
        return "";
    }

    private static Item section(String title, int accent, int group) {
        Item it = new Item(Kind.SECTION);
        it.label = title;
        it.accent = accent;
        it.group = group;
        return it;
    }

    // "§72:20am §b☽" with the moon first so the time ends on the right edge like the other values
    private static String clock(String line) {
        Matcher m = TIME.matcher(line.trim());
        if (!m.matches()) return lift(line.trim().replaceFirst("^§7", ""));
        return m.group(2).isBlank() ? m.group(1) : lift(m.group(2).trim()) + "  §r" + m.group(1);
    }

    // mayor/minister perk as a row, the ministers with its chip
    private static Item perk(String name, int group) {
        Item it = new Item(Kind.ROW);
        it.group = group;
        Matcher m = MINISTER_PERK.matcher(name);
        if (m.matches()) {
            it.label = m.group(1);
            it.chip = "Minister";
            it.chipColor = MAYOR;
        } else it.label = name;
        it.labelColor = PERK;
        return it;
    }

    private static String slayerIcon(String name) {
        if (name.contains("Revenant")) return "rotten_flesh";
        if (name.contains("Tarantula")) return "spider_eye";
        if (name.contains("Sven")) return "bone";
        if (name.contains("Voidgloom")) return "ender_pearl";
        if (name.contains("Inferno")) return "blaze_powder";
        if (name.contains("Riftstalker")) return "redstone";
        return null;
    }

    // reads lines for display. what goes on the board is still decided in build(), only mayor perks get read whole
    private List<Item> parse(List<String> ls) {
        List<Item> out = new ArrayList<>(ls.size());
        String mayor = demo ? "Derpy" : Mayor.name;
        int group = G_STATS, sections = G_SECTION, left = 0, tint = SLAYER;
        String in = "";
        for (int i = 0; i < ls.size(); i++) {
            String l = ls.get(i), p = GameState.strip(l).trim();
            String next = i + 1 < ls.size() ? ls.get(i + 1) : "";
            boolean member = left > 0 && in.equals("PARTY");
            Matcher m;
            if (l.startsWith("§#TITLE#")) {
                Item it = new Item(Kind.TITLE);
                it.label = l.substring(8);
                out.add(it);
                in = "";
                left = 0;
                group = G_STATS;
            } else if (p.isEmpty()) {
                out.add(new Item(Kind.BLANK));
                in = "";
                left = 0;
                group = G_STATS;
            } else if ((m = LOBBY.matcher(l)).matches()) {
                Item it = new Item(Kind.ROW);
                it.label = m.group(1) == null ? "" : m.group(1);
                it.labelColor = Modern.DIM;
                it.value = m.group(2);
                it.valueColor = Modern.DIM;
                out.add(it);
            } else if (Patterns.get("SH.sb.main.date").matcher(l).matches()) {
                Item it = text(l.trim(), G_STATS);
                if (it.labelColor == 0 || it.labelColor == Modern.INK || it.labelColor == Modern.LABEL) {
                    it.labelColor = 0;
                    it.bright = true;
                }
                if (Patterns.get("SH.sb.main.time").matcher(next).matches()) {
                    it.value = clock(next);
                    i++;
                }
                out.add(it);
            } else if (Patterns.get("SH.sb.main.time").matcher(l).matches()) {
                out.add(text(clock(l), G_STATS));
            } else if (p.startsWith("⏣") || p.startsWith("ф") || (p.length() > 2 && p.charAt(0) >= 0xE000 && p.charAt(0) <= 0xF8FF && p.charAt(1) == ' ' && !p.contains(": "))) {
                String where = l.trim();
                String pests = "";
                if ((m = PESTS.matcher(where)).matches()) {
                    where = m.group(1);
                    pests = lift(m.group(2)).replace("§7 x", "§c x");
                }
                boolean glyphed = (m = GLYPHED.matcher(where)).matches();
                String name = glyphed ? m.group(2) : where;
                Item island = out.isEmpty() ? null : out.get(out.size() - 1);
                if (island != null && island.kind == Kind.ROW && island.label.equals("Island") && withoutThe(name).equals(withoutThe(island.value))) {
                    // "Island Garden" then "The Garden" = same thing, one row with the pests on it
                    Single one = Single.of(name);
                    island.value = (one != null ? one.text() : lift(name)) + (pests.isEmpty() ? "" : "  " + pests);
                    if (one != null && one.color() != 0) island.valueColor = one.color();
                } else {
                    Item it = text(name, G_STATS);
                    if (glyphed) it.glyph = lift(m.group(1));
                    it.value = pests;
                    out.add(it);
                }
            } else if (p.startsWith("Plot - ") && l.contains("- ")) {
                Item it = new Item(Kind.ROW);
                it.label = "Plot";
                it.icon = stack("farmland");
                setValue(it, l.substring(l.indexOf("- ") + 2).trim(), 0);
                out.add(it);
            } else if ((m = VISITING.matcher(p)).matches() && Patterns.get("SH.sb.misc.visiting").matcher(l).matches()) {
                double a = RowText.number(m.group(1)), b = RowText.number(m.group(2));
                Item it = new Item(Kind.ROW);
                it.glyph = "§a✌";
                it.label = "Visiting";
                it.value = Modern.fraction(m.group(1), m.group(2), 'a', 'f') + "  §7" + (b > 0 ? RowText.percent(Math.min(1, a / b)) : "");
                // the numbers and the percent say it, no bar
                it.progress = -1;
                out.add(it);
            } else if (l.startsWith("§7SB Level: ") && next.startsWith("§7XP: ")) {
                Item it = fraction(next.substring(6), G_STATS, 'b', 'b');
                if (it == null) it = generic(l, G_STATS);
                else {
                    it.label = "SB Level §b" + GameState.strip(l.substring(12)).trim();
                    it.progress = -1;
                    i++;
                }
                it.icon = stack("experience_bottle");
                out.add(it);
            } else if (l.startsWith("§7SB Level: ")) {
                Item it = generic(l, G_STATS);
                it.icon = stack("experience_bottle");
                out.add(it);
            } else if (p.equals("Slayer Quest")) {
                out.add(section("Slayer Quest", SLAYER, group = ++sections));
                in = "SLAYER";
                left = 2;
            } else if (Patterns.get("SH.sb.misc.objective").matcher(l).matches() && (!member || p.startsWith("Objective"))) {
                out.add(section(p, OBJECTIVE, group = ++sections));
                in = "OBJECTIVE";
                left = 1;
            } else if (l.equals("§9§lPowder")) {
                out.add(section("Powder", POWDER, group = ++sections));
                in = "POWDER";
            } else if (in.equals("POWDER") && l.startsWith(" §7- §f")) {
                Item it = generic(l.substring(7), group);
                out.add(it);
            } else if (!mayor.isEmpty() && (m = MAYOR_LINE.matcher(l)).matches() && m.group(1).equals(mayor)) {
                out.add(section("Mayor", MAYOR, group = ++sections));
                Item it = new Item(Kind.ROW);
                it.group = group;
                it.label = mayor;
                it.labelColor = Modern.AQUA;
                if (m.group(2) != null) {
                    it.note = "Minister";
                    it.value = m.group(2);
                    it.valueColor = Modern.INK;
                }
                out.add(it);
                in = "MAYOR";
                if (!demo) {
                    // build() stops at 3 perks and the minister is last, show all of them. election line + jerrys perk stay
                    while (i + 1 < ls.size() && (ls.get(i + 1).startsWith(" §7- §e") || ls.get(i + 1).startsWith(" §7Election in: "))) {
                        String sub = ls.get(++i);
                        if (sub.startsWith(" §7Election in: ")) {
                            Item e = new Item(Kind.ROW);
                            e.group = group;
                            setLabel(e, "Election in");
                            e.value = sub.substring(" §7Election in: ".length());
                            out.add(e);
                        } else if (sub.contains("(Perkpocalypse)")) out.add(perk(sub.substring(7), group));
                    }
                    if (optBool("mayorPerks", true)) for (Mayor.Perk one : Mayor.perks) out.add(perk(one.name(), group));
                }
            } else if (in.equals("MAYOR") && l.startsWith(" §7- §e")) {
                out.add(perk(l.substring(7), group));
            } else if (p.startsWith("Party") && !member) {
                Matcher c = COUNT.matcher(p);
                int max = optInt("partyMax", 4);
                boolean counted = c.matches();
                out.add(section(counted ? c.group(1) + " §7(" + c.group(2) + ")" : p, PARTY, group = ++sections));
                in = "PARTY";
                left = counted ? Math.min(max, Integer.parseInt(c.group(2))) : max;
            } else if (left > 0 && in.equals("SLAYER") && left == 2 && !p.contains(": ")) {
                Item it = new Item(Kind.ROW);
                it.group = group;
                Matcher tier = TIER.matcher(p);
                Single one = Single.of(l);
                int color = one != null && one.color() != 0 ? one.color() : SLAYER;
                tint = color;
                it.label = tier.matches() ? tier.group(1) : p;
                it.labelColor = color;
                if (tier.matches()) {
                    it.chip = tier.group(2);
                    it.chipColor = color;
                }
                String icon = slayerIcon(p);
                if (icon != null) it.icon = stack(icon);
                out.add(it);
                left--;
            } else if (member) {
                Item it = (m = LEADER.matcher(l)).matches() ? text(m.group(1), group) : text(l, group);
                if (m.matches()) {
                    it.chip = "Leader";
                    it.chipColor = Modern.GOLD;
                }
                out.add(it);
                left--;
            } else if (left > 0 && (in.equals("SLAYER") || in.equals("OBJECTIVE"))) {
                Item it = generic(l, group);
                if (in.equals("SLAYER")) {
                    it.barColor = tint;
                    it.indent = slayerIcon(ls.get(i - 1)) == null ? 0 : 12;
                } else if (it.labelColor == OBJECTIVE) it.labelColor = 0;
                out.add(it);
                left--;
            } else if (in.equals("OBJECTIVE") && (Patterns.get("SH.sb.misc.thirdObjectiveLine").matcher(l).matches() || Patterns.get("SH.sb.misc.wtfAreThoseLines").matcher(l).matches())) {
                out.add(generic(l, group));
            } else {
                in = "";
                left = 0;
                Item it = generic(l, isEvent(l) ? G_EVENT : G_STATS);
                if (it.group == G_STATS && it.icon != null) {
                    String key = GameState.strip(it.label).trim();
                    if (key.equals("Purse") || key.equals("Piggy") || key.equals("Bits")) setGain(it, gain(key));
                    else if (key.equals("Cookie Buff") && GameState.strip(it.value).contains("Not Active")) it.valueColor = Modern.BAD;
                }
                out.add(it);
            }
            if (!out.isEmpty() && out.get(out.size() - 1).group == G_STATS) {
                in = "";
                left = 0;
                group = G_STATS;
            }
        }
        return out;
    }

    // hairlines between groups not empty lines. a section line already splits so it only needs air above
    private static List<Item> spaced(List<Item> in, boolean hideEmpty) {
        List<Item> out = new ArrayList<>(in.size() + 8);
        Item last = null;
        boolean broke = false;
        for (int i = 0; i < in.size(); i++) {
            Item it = in.get(i);
            if (it.kind == Kind.BLANK) {
                Item following = null;
                for (int j = i + 1; j < in.size() && following == null; j++) if (in.get(j).kind != Kind.BLANK) following = in.get(j);
                if (broke || last == null || last.kind == Kind.TITLE || following == null) {
                    if (!hideEmpty) out.add(Item.gap(Modern.RULE_BLOCK));
                } else if (following.kind != Kind.SECTION && !(hideEmpty && last.currency && following.currency)) {
                    out.add(last.progress >= 0 ? Item.gap(SECTION_GAP) : new Item(Kind.RULE));
                    broke = true;
                }
                continue;
            }
            if (it.kind == Kind.SECTION) {
                if (last != null && last.kind != Kind.TITLE) out.add(Item.gap(SECTION_GAP));
            } else if (it.kind == Kind.ROW && last != null && last.kind != Kind.TITLE && !broke && last.group != it.group) {
                out.add(last.progress >= 0 ? Item.gap(SECTION_GAP) : new Item(Kind.RULE));
            }
            out.add(it);
            last = it;
            broke = false;
        }
        return out;
    }

    private static void put(Ui ui, String s, int x, int y, int color, boolean shadow) {
        if (shadow) ui.textShadow(s, x, y, color);
        else ui.text(s, x, y, color);
    }

    // cut to fit, never half a color code before the dots
    private static String fit(Ui ui, String s, int w) {
        String c = ui.ellipsize(s, Math.max(0, w));
        return c.endsWith("§…") ? c.substring(0, c.length() - 2) + "…" : c;
    }

    // title in the middle of a gold glow fading out both sides
    private static void drawBand(Ui ui, Item it, int x, int y, int w, int accent, Integer titleColor, boolean shadow) {
        int c = accent | 0xFF000000, mid = w / 2, h = Modern.BAND - 2;
        ui.gradH(x + 1, y + 1, mid - 1, h, Ui.alpha(c, 0.02f), Ui.alpha(c, 0.24f));
        ui.gradH(x + mid, y + 1, w - mid - 1, h, Ui.alpha(c, 0.24f), Ui.alpha(c, 0.02f));
        ui.gradH(x + 1, y + Modern.BAND - 1, mid - 1, 1, Ui.alpha(c, 0.04f), c);
        ui.gradH(x + mid, y + Modern.BAND - 1, w - mid - 1, 1, c, Ui.alpha(c, 0.04f));
        if (it.bandText == null || !java.util.Objects.equals(it.bandFor, titleColor)) {
            Single one = titleColor == null ? Single.of(it.label) : null;
            it.bandText = titleColor != null ? it.label.replaceAll("§[0-9a-fA-F]", "") : one != null ? one.text() : lift(it.label);
            it.bandColor = titleColor != null ? titleColor | 0xFF000000 : one != null && one.color() != 0 ? one.color() : 0;
            it.bandFor = titleColor;
        }
        int color = it.bandColor != 0 ? it.bandColor : Modern.lighten(c, 0.45f);
        String s = fit(ui, it.bandText, w - 8);
        int hot = Math.min(mid - 1, ui.width(s) / 2 + 22);
        ui.gradH(x + mid - hot, y + 1, hot, h, Ui.alpha(c, 0f), Ui.alpha(c, 0.06f));
        ui.gradH(x + mid, y + 1, hot, h, Ui.alpha(c, 0.06f), Ui.alpha(c, 0f));
        put(ui, s, x + (w - ui.width(s)) / 2, y + 3, color, shadow);
    }

    // sub heading: name in its color + fading line on whatever side the alignment leaves free
    private static void drawSection(Ui ui, Item it, int x, int y, int w, String align, boolean shadow) {
        int c = it.accent | 0xFF000000, tw = ui.width(it.label), tc = Modern.lighten(c, 0.3f), line = y + 4;
        int a = Ui.alpha(c, 0.6f), z = Ui.alpha(c, 0.02f);
        int tx = "Center".equals(align) ? x + (w - tw) / 2 : "Right".equals(align) ? x + w - tw : x;
        put(ui, it.label, tx, y, tc, shadow);
        if (tx > x + 6) ui.gradH(x, line, tx - x - 4, 1, z, a);
        if (tx + tw + 6 < x + w) ui.gradH(tx + tw + 4, line, x + w - tx - tw - 4, 1, a, z);
    }

    private static void drawRow(Ui ui, Item it, int x, int y, int w, int rh, int accent, ElementStyle st, String align, boolean shadow) {
        int labelColor = it.labelColor != 0 ? it.labelColor : st.textColor() != null ? st.textColor() : it.bright || it.alone() ? Modern.INK : Modern.LABEL;
        if (it.alone()) {
            boolean left = !"Center".equals(align) && !"Right".equals(align);
            int gw = ui.width(it.glyph), pre = it.glyph.isEmpty() ? 0 : left ? 12 : gw + 4, in = left ? it.indent : 0;
            String s = fit(ui, it.label, w - pre - in);
            int sw = pre + ui.width(s), lx = left ? x + in : "Center".equals(align) ? x + (w - sw) / 2 : x + w - sw;
            if (pre > 0) put(ui, it.glyph, left ? lx + (10 - gw) / 2 : lx, y, labelColor, shadow);
            put(ui, s, lx + pre, y, labelColor, shadow);
            return;
        }
        int lx = x + it.indent, right = x + w;
        if (it.icon != null) {
            ui.icon(it.icon, lx - 1, y - 1, 0.6f);
            lx += 12;
        } else if (!it.glyph.isEmpty()) {
            put(ui, it.glyph, lx + (10 - ui.width(it.glyph)) / 2, y, labelColor, shadow);
            lx += 12;
        }
        if (!it.chip.isEmpty()) {
            int cw = ui.width(it.chip) + 6;
            Modern.chip(ui, right - cw, y, it.chip, it.chipColor);
            right -= cw + 4;
        }
        if (!it.value.isEmpty()) {
            int vc = it.valueColor != 0 ? it.valueColor : ElementStyle.or(st.valueColor(), Modern.INK);
            String v = fit(ui, it.value, right - lx);
            right -= ui.width(v);
            put(ui, v, right, y, vc, shadow);
        }
        if (!it.note.isEmpty()) {
            right -= ui.width(it.note) + 5;
            if (right > lx + 12) put(ui, it.note, right, y, it.noteColor, shadow);
        }
        if (!it.label.isEmpty() && right - lx > 14) put(ui, fit(ui, it.label, right - lx - (right < x + w ? 6 : 0)), lx, y, labelColor, shadow);
        if (it.progress >= 0) Modern.meter(ui, x, y + rh - 1, w, Modern.METER, it.progress, meterColor(new Row(null, it.label, it.value, it.valueColor, it.progress, it.barColor), accent));
    }

    public static void register() {
        Features.add(new Feature("board", "HUD", "Custom scoreboard", "Your own sidebar: pick the lines and their order in the settings (title, date, island, purse, bank, bits, powders, objective, slayer, party, mayor, events...). Lines the patterns do not know stay at the end.", "")
                .bind(() -> Config.I.customBoard, v -> Config.I.customBoard = v)
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.text("board.title", "Title", "& codes for colours, e.g. &6&lSKYBLOCK", "&6&lSKYBLOCK"))
                .option(Option.bool("board.unclaimedBits", "Bits still to claim", "After your bits: 140,965/19,176. Only while there are some; open the SkyBlock Menu once so the mod knows.", true))
                .option(Option.bool("board.hideEmpty", "Hide empty lines", "Collapses repeated empty lines, trims the ends, hides zero purse/bank/bits.", true))
                .option(Option.choice("board.numbers", "Number format", "", "Long", "Long", "Short"))
                .option(Option.choice("board.align", "Text alignment", "", "Left", "Left", "Center", "Right"))
                .option(Option.bool("board.showUnknown", "Keep lines the board does not know", "Lines Hypixel sends that no entry claims are shown at the end.", true))
                .option(Option.bool("board.mayorPerks", "Mayor perks under the mayor", "", true))
                .option(Option.bool("board.mayorTime", "Time until the next mayor", "", true))
                .option(Option.bool("board.mayorExtra", "Jerry's temporary mayor", "Its perk, once the calendar was opened.", true))
                .option(Option.bool("board.tabEvents", "Events the tab list names", "What runs or starts soon and the Broodmother, under Events, and the long events beside it (Year of the Witch, Harvest Feast...) with the time they have left.", true))
                .option(Option.integer("board.partyMax", "Party members shown", "", 4, 1, 10))
                .option(Option.text("board.footer", "Footer", "& codes for colours, \\n for a new line.", "&ewww.hypixel.net"))
                .option(Option.bool("board.piggyAsPurse", "Purse instead of Piggy", "", true))
                .option(Option.bool("board.maxPlayers", "Most players the island holds", "After the number of players.", true))
                .option(Option.bool("board.profileName", "Profile's name, not its type", "On the Profile line.", false))
                .option(Option.bool("board.lobbyDate", "Date before the lobby code", "", true))
                .option(Option.choice("board.dateFormat", "How that date is written", "", "As Hypixel writes it", "As Hypixel writes it", "MM/DD/YYYY", "DD/MM/YYYY", "YYYY-MM-DD"))
                .option(Option.bool("board.time24", "SkyBlock time in 24 hours", "", false))
                .option(Option.bool("board.timeExact", "SkyBlock time to the exact minute", "Hypixel rounds it to ten.", false))
                .option(Option.bool("board.showChanges", "Earned and lost", "The change behind Purse and Bits for five seconds.", false))
                .option(Option.bool("board.arrowPercent", "Arrows as a percentage", "", false))
                .option(Option.bool("board.arrowColor", "Arrow amount in colour", "From green when full to red when nearly out.", false))
                .option(Option.bool("board.magicalPower", "Magical Power after your Power", "", true))
                .option(Option.bool("board.tuningCompact", "Tunings on one line", "", false))
                .option(Option.integer("board.tuningAmount", "Tunings shown", "When not on one line.", 2, 1, 8))
                .option(Option.integer("board.chunkMax", "Stats per line", "For the line with several stats.", 3, 1, 6))
                .option(Option.bool("board.keepOnSwitch", "Keep it while switching islands", "", false))
                .option(Option.bool("board.partyLeader", "Party leader first", "With a crown, when the chat has said who leads.", true)));
        io.muzy.sbc.ui.HudLayout.add(I);
    }
}
