// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of Skyblocker; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.dungeon;

import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.chat.ChatBus;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Mayor;
import dev.zmuzy.sbc.feature.Feature;
import dev.zmuzy.sbc.feature.Features;
import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundEvents;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// the dungeon score like the game will count it, from sidebar + tab (rooms cleared, secrets, puzzles, deaths,
// time) + bonus kills from chat. shown on the room map
public final class Score {
    private record Floor(int secretsPercent, int timeLimit) {}

    private static final Map<String, Floor> FLOORS = Map.ofEntries(
            Map.entry("E", new Floor(30, 1200)), Map.entry("F1", new Floor(30, 600)), Map.entry("F2", new Floor(40, 600)), Map.entry("F3", new Floor(50, 600)),
            Map.entry("F4", new Floor(60, 720)), Map.entry("F5", new Floor(70, 600)), Map.entry("F6", new Floor(85, 720)), Map.entry("F7", new Floor(100, 840)),
            Map.entry("M1", new Floor(100, 480)), Map.entry("M2", new Floor(100, 480)), Map.entry("M3", new Floor(100, 480)), Map.entry("M4", new Floor(100, 480)),
            Map.entry("M5", new Floor(100, 480)), Map.entry("M6", new Floor(100, 600)), Map.entry("M7", new Floor(100, 840)));

    private static final dev.zmuzy.sbc.core.Patterns.P FLOOR = dev.zmuzy.sbc.core.Patterns.p("Score.FLOOR");
    private static final dev.zmuzy.sbc.core.Patterns.P CLEARED = dev.zmuzy.sbc.core.Patterns.p("Score.CLEARED");
    private static final dev.zmuzy.sbc.core.Patterns.P ELAPSED = dev.zmuzy.sbc.core.Patterns.p("Score.ELAPSED");
    private static final dev.zmuzy.sbc.core.Patterns.P ROOMS = dev.zmuzy.sbc.core.Patterns.p("Score.ROOMS");
    private static final dev.zmuzy.sbc.core.Patterns.P SECRETS = dev.zmuzy.sbc.core.Patterns.p("Score.SECRETS");
    private static final dev.zmuzy.sbc.core.Patterns.P CRYPTS = dev.zmuzy.sbc.core.Patterns.p("Score.CRYPTS");
    private static final dev.zmuzy.sbc.core.Patterns.P PUZZLE_COUNT = dev.zmuzy.sbc.core.Patterns.p("Score.PUZZLE_COUNT");
    private static final dev.zmuzy.sbc.core.Patterns.P PUZZLE = dev.zmuzy.sbc.core.Patterns.p("Score.PUZZLE");
    private static final dev.zmuzy.sbc.core.Patterns.P DEATH = dev.zmuzy.sbc.core.Patterns.p("Score.DEATH");

    public static String floor = "";
    public static boolean started;
    public static int score;
    private static long startedAt;
    // from skyblocker DungeonScore.DEATHS_PATTERN: who the first death line is about
    private static final Pattern WHO_DIED = Pattern.compile("^\u2620 (\\S+) ");
    private static volatile boolean firstDeathSpirit;
    private static boolean mimic, prince, bat, bloodDone, sent270, sent300, sentCrypts;
    private static int deaths;
    private static long bloodAt;

    private Score() {}

    // for DungeonPack: run is over
    static void leave() {
        reset();
    }

    private static void reset() {
        floor = "";
        started = mimic = prince = bat = bloodDone = sent270 = sent300 = sentCrypts = firstDeathSpirit = false;
        deaths = 0;
        score = 0;
        startedAt = 0;
        bloodAt = 0;
    }

    private static Matcher find(Iterable<String> lines, Pattern p) {
        for (String l : lines) {
            Matcher m = p.matcher(l);
            if (m.find()) return m;
        }
        return null;
    }

    private static boolean paul() {
        for (var p : Mayor.perks) if (p.name().startsWith("EZPZ")) return true;
        return false;
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        if (!started) return ChatBus.PASS;
        String t = line.plain();
        Matcher d = DEATH.matcher(t);
        if (d.matches()) {
            deaths++;
            if (deaths == 1) spiritPetCheck(t);
        }
        if (t.equals("A Prince falls. +1 Bonus Score") || t.endsWith("Prince dead!") || t.endsWith("Prince Killed!")) prince = true;
        if (t.equals("A Bat has been slain. +1 Bonus Score") || t.endsWith("Bat dead!") || t.endsWith("Bat Killed!")) bat = true;
        if (t.endsWith("Mimic dead!") || t.endsWith("Mimic Killed!") || t.endsWith("Mimic dead") || t.contains("$SKYTILS-DUNGEON-SCORE-MIMIC$")) mimic = true;
        if (t.equals("[BOSS] The Watcher: You have proven yourself. You may pass.")) bloodAt = System.currentTimeMillis() + 5500;
        return ChatBus.PASS;
    }

    // from skyblocker checkMessageForDeaths: first death costs 1 point less when that player has a legendary spirit pet
    private static void spiritPetCheck(String line) {
        Matcher who = WHO_DIED.matcher(line.trim());
        if (!who.lookingAt()) return;
        var me = Minecraft.getInstance().player;
        String name = who.group(1).equals("You") && me != null ? me.getGameProfile().name() : who.group(1);
        dev.zmuzy.sbc.core.Profile.fetch(name, data -> {
            for (var pet : data.pets) if (pet.type().equals("SPIRIT") && pet.tier().equals("LEGENDARY")) firstDeathSpirit = true;
        }, error -> {});
    }

    private static void tick(Minecraft mc) {
        if (!"CATACOMBS".equals(GameState.island) || mc.player == null) {
            if (started || !floor.isEmpty()) reset();
            return;
        }
        if (mc.player.tickCount % 20 != 0) return;
        var sb = GameState.scoreboard;
        Matcher cleared = find(sb, CLEARED.pattern());
        if (!started) {
            if (cleared == null) return;
            reset();
            Matcher f = find(sb, FLOOR.pattern());
            floor = f == null ? "" : f.group(1);
            started = true;
            startedAt = System.currentTimeMillis();
        }
        if (bloodAt != 0 && System.currentTimeMillis() >= bloodAt) bloodDone = true;
        score = compute(sb, cleared);
        if (!sent270 && score >= 270 && score < 300) {
            sent270 = true;
            if (DungeonMarks.opt("dungeon_score", "title_270")) mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§e270 score reached"));
            if (DungeonMarks.opt("dungeon_score", "sound_270")) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.6f);
        }
        if (!sent300 && score >= 300) {
            sent300 = true;
            if (DungeonMarks.opt("dungeon_score", "title_300")) mc.gui.hud.setTitle(net.minecraft.network.chat.Component.literal("§a§l300 score reached"));
            if (DungeonMarks.opt("dungeon_score", "sound_300")) mc.player.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.2f);
        }
        // from skyblocker DungeonScore.tick: under 5 crypts when the score gets close to S+, only you get told
        if (!sentCrypts && DungeonMarks.opt("dungeon_score", "crypts") && score >= DungeonMarks.integer("dungeon_score", "crypts_at", 250)) {
            Matcher c = find(GameState.tab, CRYPTS.pattern());
            int crypts = c == null ? 0 : Integer.parseInt(c.group(1));
            sentCrypts = true;
            if (crypts < 5) mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§b[SBC] §fCrypts: §e" + crypts + "§f/5"));
        }
    }

    private static int compute(Iterable<String> sb, Matcher cleared) {
        Floor req = FLOORS.getOrDefault(floor, new Floor(0, 0));
        boolean entrance = floor.equals("E");
        Iterable<String> tab = GameState.tab;
        Matcher r = find(tab, ROOMS.pattern());
        int completed = r == null ? 0 : Integer.parseInt(r.group(1));
        double clearFraction = cleared == null ? 0 : Integer.parseInt(cleared.group(1)) / 100.0;
        int total = clearFraction <= 0 ? 0 : (int) Math.round(completed / clearFraction);
        int extra = !bloodDone ? (entrance ? 1 : 2) : (!"BOSS".equals(GameState.area) && !entrance ? 1 : 0);
        Matcher s = find(tab, SECRETS.pattern());
        double secrets = s == null ? 0 : Double.parseDouble(s.group(1));
        Matcher c = find(tab, CRYPTS.pattern());
        int crypts = c == null ? 0 : Integer.parseInt(c.group(1));

        int incomplete = 0, counted = 0;
        Matcher pc = find(tab, PUZZLE_COUNT.pattern());
        int puzzles = pc == null ? 0 : Integer.parseInt(pc.group(1));
        for (String l : tab) {
            if (counted >= puzzles) break;
            Matcher pm = PUZZLE.matcher(l);
            if (pm.find()) {
                counted++;
                if (pm.group(1).equals("✖") || pm.group(1).equals("✦")) incomplete++;
            }
        }

        int roomsDone = completed + extra;
        int skillRooms = Math.max(0, Math.min(80, total != 0 ? (int) (80.0 * roomsDone / total) : 0));
        int skill = 20 + Math.max(0, Math.min(80, skillRooms - incomplete * 10 - (deaths * 2 - (firstDeathSpirit ? 1 : 0))));
        int exploreRooms = Math.max(0, Math.min(60, total != 0 ? (int) (60.0 * roomsDone / total) : 0));
        int exploreSecrets = req.secretsPercent() == 0 ? 0 : Math.max(0, Math.min(40, (int) (40.0 * Math.min(req.secretsPercent(), secrets) / req.secretsPercent())));
        int explore = exploreRooms + exploreSecrets;
        int time = timeScore(sb, req);
        boolean mimicFloor = floor.matches("[FM][67]");
        int mimicScore = mimic || (secrets >= 100.0 && mimicFloor) ? 2 : 0;
        int bonus = (paul() ? 10 : 0) + Math.max(0, Math.min(5, crypts)) + mimicScore + (prince ? 1 : 0) + (bat ? 1 : 0);
        if (entrance) return Math.round(time * 0.7f) + Math.round(explore * 0.7f) + Math.round(skill * 0.7f) + Math.round(bonus * 0.7f);
        return time + explore + skill + bonus;
    }

    private static int timeScore(Iterable<String> sb, Floor req) {
        int seconds = (int) ((System.currentTimeMillis() - startedAt) / 1000);
        Matcher e = find(sb, ELAPSED.pattern());
        if (e != null && (e.group(1) != null || e.group(2) != null || e.group(3) != null))
            seconds = (e.group(1) == null ? 0 : Integer.parseInt(e.group(1)) * 3600) + (e.group(2) == null ? 0 : Integer.parseInt(e.group(2)) * 60) + (e.group(3) == null ? 0 : Integer.parseInt(e.group(3)));
        if (req.timeLimit() == 0 || seconds < req.timeLimit()) return 100;
        double past = (double) (seconds - req.timeLimit()) / req.timeLimit() * 100.0;
        if (past < 20) return 100 - (int) past / 2;
        if (past < 40) return 100 - (int) (10 + (past - 20) / 4);
        if (past < 50) return 100 - (int) (15 + (past - 40) / 5);
        if (past < 60) return 100 - (int) (17 + (past - 50) / 6);
        return Math.max(0, Math.min(100, 100 - (int) (18.666666666666668 + (past - 60) / 7)));
    }

    public static String grade() {
        int s = score;
        return s < 100 ? "D" : s < 160 ? "C" : s < 230 ? "B" : s < 270 ? "A" : s < 300 ? "S" : "S+";
    }

    public static void register() {
        Feature f = Features.add(new Feature("dungeon_score", "Dungeons", "Dungeon score", "Works out your score during the run (rooms, secrets, puzzles, deaths, time, bonuses) and chimes at 270 and 300.", "Skyblocker: dungeon score")
                .when(() -> "CATACOMBS".equals(GameState.island) || SbcMod.DEV).tick(Score::tick)
                .option(dev.zmuzy.sbc.feature.Option.bool("dungeon_score.title_270", "Title at 270", "A title on screen at 270.", true))
                .option(dev.zmuzy.sbc.feature.Option.bool("dungeon_score.sound_270", "Sound at 270", "A chime at 270.", true))
                .option(dev.zmuzy.sbc.feature.Option.bool("dungeon_score.title_300", "Title at 300", "A title on screen at 300.", true))
                .option(dev.zmuzy.sbc.feature.Option.bool("dungeon_score.sound_300", "Sound at 300", "A chime at 300.", true))
                .option(dev.zmuzy.sbc.feature.Option.bool("dungeon_score.crypts", "Remind about crypts", "A line in your own chat when the score passes the number below with fewer than five crypts.", true))
                .option(dev.zmuzy.sbc.feature.Option.integer("dungeon_score.crypts_at", "Crypt reminder at", "The score at which to remind you. 250 is meant for S+ runs entering the boss at 270.", 250, 0, 320)));
        ChatBus.register(ChatBus.OBSERVE + 11, "dungeonscore", f, Score::onChat);
    }
}
