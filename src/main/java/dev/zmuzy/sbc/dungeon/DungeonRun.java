// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.dungeon;

import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// what the other dungeon helpers ask about the run: floor, started or not, boss room + phase, your class +
// level, the team. from skyhanni features/dungeon DungeonApi + DungeonBossApi
public final class DungeonRun {
    // from skyhanni DungeonApi.DungeonClass in its order, also the spirit leap key order
    public static final List<String> CLASSES = List.of("Archer", "Berserk", "Healer", "Mage", "Tank");

    // from skyhanni DungeonBossApi.DungeonBossPhase
    public enum Phase { NONE, F6_TERRACOTTA, F6_GIANTS, F6_SADAN, F7_MAXOR, F7_STORM, F7_GOLDOR_1, F7_GOLDOR_2, F7_GOLDOR_3, F7_GOLDOR_4, F7_GOLDOR_5, F7_NECRON, M7_WITHER_KING }

    // from skyhanni DungeonApi.TeamMember
    public record Member(String name, String dungeonClass, int level, boolean dead) {}

    // skyhanni DungeonBossApi phase lines with colors off, the same lines skyblockers DungeonSplitsWidget uses
    private static final String TERRACOTTA = "[BOSS] Sadan: So you made it all the way here... Now you wish to defy me? Sadan?!", GIANTS = "[BOSS] Sadan: ENOUGH!",
            SADAN = "[BOSS] Sadan: You did it. I understand now, you have earned my respect.", MAXOR = "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!",
            STORM = "[BOSS] Storm: Pathetic Maxor, just like expected.", GOLDOR = "[BOSS] Goldor: Who dares trespass into my domain?", CORE = "The Core entrance is opening!",
            NECRON = "[BOSS] Necron: You went further than any human before, congratulations.", WITHER_KING = "[BOSS] Necron: All this, for nothing...";

    private static final Patterns.P FLOOR = Patterns.p("Score.FLOOR"), UNIQUE = Patterns.p("SH.dungeon.unique-class-bonus"), MORT = Patterns.p("SH.dungeon.start.mort.colorless"),
            COMPLETE = Patterns.p("SH.dungeon.completecolorless"), TEAM = Patterns.p("SH.dungeon.tablist.playerteam.colorless");
    // from skyhanni goldorTerminalPattern without colors
    private static final Pattern GOLDOR_STEP = Pattern.compile("^(\\w+) (?:activated|completed) a (lever|terminal|device)! \\((\\d)/(\\d)\\)$");
    // from skyhanni DungeonBossApi: the [BOSS] line = youre in the boss room
    private static final Pattern BOSS_LINE = Pattern.compile("^\\[BOSS] ([^:]+):.*$");

    private static String floor = "";
    private static boolean started, completed, inBoss, unique;
    private static Phase phase = Phase.NONE;
    private static String myClass = "";
    private static int myLevel = -1;
    private static final Map<String, Member> team = new LinkedHashMap<>();

    private DungeonRun() {}

    public static boolean inDungeon() {
        return "CATACOMBS".equals(GameState.island);
    }

    // "F7", "M3", "E". empty till the sidebar said
    public static String floor() {
        return floor;
    }

    // from skyhanni DungeonApi.isOneOf
    public static boolean isFloor(String... floors) {
        for (String f : floors) if (f.equals(floor)) return true;
        return false;
    }

    public static boolean started() {
        return started;
    }

    // from skyhanni DungeonApi.active
    public static boolean active() {
        return started && !completed;
    }

    public static boolean completed() {
        return completed;
    }

    public static boolean inBoss() {
        return inBoss;
    }

    public static Phase phase() {
        return phase;
    }

    public static String playerClass() {
        return myClass;
    }

    public static int playerLevel() {
        return myLevel;
    }

    public static boolean uniqueClass() {
        return unique;
    }

    public static boolean isClass(String cls) {
        return cls.equalsIgnoreCase(myClass);
    }

    public static Member member(String name) {
        return team.get(name);
    }

    public static List<Member> team() {
        return new ArrayList<>(team.values());
    }

    // from skyhanni ItemAbility.getMageCooldownReduction: 25% as mage (50% if the only mage) + 1 more per 2 class
    // levels. returns the share of the cooldown left: 1 if not mage, -1 while unknown
    public static double mageMultiplier() {
        return mageMultiplier(myClass, myLevel, unique);
    }

    static double mageMultiplier(String cls, int level, boolean only) {
        if (cls.isEmpty() || level < 0) return -1;
        if (!cls.equalsIgnoreCase("Mage")) return 1;
        return 1.0 - (only ? 0.5 : 0.25) - 0.01 * Math.floor(level / 2f);
    }

    // from skyhanni DungeonApi.checkBossName
    static boolean isFloorBoss(String floor, String boss) {
        String correct = switch (floor) {
            case "E" -> "The Watcher";
            case "F1", "M1" -> "Bonzo";
            case "F2", "M2" -> "Scarf";
            case "F3", "M3" -> "The Professor";
            case "F4", "M4" -> "Thorn";
            case "F5", "M5" -> "Livid";
            case "F6", "M6" -> "Sadan";
            case "F7", "M7" -> "Maxor";
            default -> null;
        };
        return correct != null && !boss.equals("The Watcher") && boss.endsWith(correct);
    }

    // one colorless chat line, for the game + tests
    static void feed(String plain) {
        String t = plain.trim();
        if (MORT.matcher(t).matches()) started = true;
        if (UNIQUE.matcher(t).matches()) unique = true;
        if (COMPLETE.matcher(plain).matches()) {
            completed = true;
            phase = Phase.NONE;
        }
        Matcher b = BOSS_LINE.matcher(t);
        if (b.matches() && !inBoss && isFloorBoss(floor, b.group(1).trim())) inBoss = true;
        if (floor.endsWith("6")) {
            if (t.equals(TERRACOTTA)) phase = Phase.F6_TERRACOTTA;
            else if (t.equals(GIANTS)) phase = Phase.F6_GIANTS;
            else if (t.equals(SADAN)) phase = Phase.F6_SADAN;
        }
        if (floor.endsWith("7")) {
            Matcher g = GOLDOR_STEP.matcher(t);
            if (g.matches() && g.group(3).equals(g.group(4))) {
                phase = switch (phase) {
                    case F7_GOLDOR_1 -> Phase.F7_GOLDOR_2;
                    case F7_GOLDOR_2 -> Phase.F7_GOLDOR_3;
                    case F7_GOLDOR_3 -> Phase.F7_GOLDOR_4;
                    default -> phase;
                };
            }
            if (t.equals(MAXOR)) phase = Phase.F7_MAXOR;
            else if (t.equals(STORM)) phase = Phase.F7_STORM;
            else if (t.equals(GOLDOR)) phase = Phase.F7_GOLDOR_1;
            else if (t.equals(CORE)) phase = Phase.F7_GOLDOR_5;
            else if (t.equals(NECRON)) phase = Phase.F7_NECRON;
            else if (t.equals(WITHER_KING) && phase != Phase.NONE) phase = Phase.M7_WITHER_KING;
        }
    }

    // from skyhanni DungeonApi onTablistChange + onTabUpdate over the tab lines
    static void readTab(List<String> tab, String me) {
        Matcher m = TEAM.matcher("");
        for (String line : tab) {
            if (!m.reset(GameState.strip(line).trim()).matches()) continue;
            String name = m.group("playerName");
            boolean dead = "DEAD".equals(m.group("playerDead"));
            String cls = m.group("className");
            int level = m.group("classLevel") == null ? 0 : GameState.roman(m.group("classLevel"));
            Member was = team.get(name);
            if (was != null && (dead || cls == null)) team.put(name, new Member(name, was.dungeonClass(), was.level(), dead));
            else team.put(name, new Member(name, cls == null ? "" : cap(cls), level, dead));
            if (name.equals(me) && myClass.isEmpty() && cls != null && CLASSES.contains(cap(cls))) {
                myClass = cap(cls);
                myLevel = level;
            }
        }
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1).toLowerCase(Locale.ROOT);
    }

    static void reset() {
        floor = "";
        started = completed = inBoss = unique = false;
        phase = Phase.NONE;
        myClass = "";
        myLevel = -1;
        team.clear();
    }

    private static void tick(Minecraft mc) {
        if (!inDungeon() || mc.player == null) {
            if (!floor.isEmpty() || started || !myClass.isEmpty() || !team.isEmpty()) reset();
            return;
        }
        if (mc.player.tickCount % 10 != 0) return;
        if (floor.isEmpty()) {
            for (String l : GameState.scoreboard) {
                Matcher f = FLOOR.matcher(l);
                if (f.find()) floor = f.group(1);
            }
        }
        readTab(GameState.tab, mc.player.getGameProfile().name());
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (inDungeon()) feed(line.plain());
        return ChatBus.PASS;
    }

    public static void register() {
        Feature f = Features.add(new Feature("dungeon_run", "Dungeons", "Dungeon run reading", "Reads the floor, the boss phase, your class and level and the team, for the other dungeon helpers.", "").alwaysOn().tick(DungeonRun::tick));
        ChatBus.register(ChatBus.OBSERVE + 13, "dungeonrun", f, DungeonRun::onChat);
    }
}
