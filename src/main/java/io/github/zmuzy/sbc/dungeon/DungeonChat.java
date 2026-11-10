// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.dungeon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.chat.ChatBus;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

// lines hidden in dungeons by type: skyhanni DungeonChatFilter + the dungeon part of ChatFilter, lines like
// sbc/dungeon_chat.json has them without colors, plus skyblocker filters
public final class DungeonChat {
    private record Lines(List<String> exact, List<Pattern> regex, List<String> ends) {
        boolean has(String t) {
            if (exact.contains(t)) return true;
            for (Pattern p : regex) if (p.matcher(t).matches()) return true;
            for (String e : ends) if (t.endsWith(e)) return true;
            return false;
        }
    }

    private static final String FEATURE = "dungeon_chat_filter";
    // from skyhanni DungeonChatFilter.block: these 2 count before the dungeon starts too, rare drops anywhere
    private static final Map<String, Lines> LINES = new HashMap<>();
    private static final Pattern BREAKER = Pattern.compile("^(A mystical force prevents you (from digging that block|digging in this room|digging there)|You don't have enough charges to break this block right now)!$");
    // from skyblocker MimicFilter, the score already read it
    private static final Pattern MIMIC = Pattern.compile(".*?(?:Mimic dead!?|Mimic Killed!|\\$SKYTILS-DUNGEON-SCORE-MIMIC\\$)$");
    private static final Patterns.P MILESTONE = Patterns.p("SH.dungeon.milestone.colorless");
    private static final String[] IN_DUNGEON = {"prepare", "start", "ambience", "pickup", "reminder", "buff", "not_possible", "damage", "ability", "puzzle", "end", "solo_class", "solo_stats", "fairy", "keys_and_doors"};

    private DungeonChat() {}

    private static synchronized void load() {
        if (!LINES.isEmpty()) return;
        try (var in = DungeonChat.class.getResourceAsStream("/sbc/dungeon_chat.json")) {
            JsonObject o = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : o.entrySet()) {
                JsonObject t = e.getValue().getAsJsonObject();
                List<String> exact = new ArrayList<>(), ends = new ArrayList<>();
                List<Pattern> regex = new ArrayList<>();
                for (JsonElement x : t.getAsJsonArray("exact")) exact.add(x.getAsString());
                for (JsonElement x : t.getAsJsonArray("ends")) ends.add(x.getAsString());
                for (JsonElement x : t.getAsJsonArray("regex")) regex.add(Pattern.compile(x.getAsString()));
                LINES.put(e.getKey(), new Lines(exact, regex, ends));
            }
        } catch (Exception ex) {
            SbcMod.LOG.warn("Could not read dungeon chat lines", ex);
        }
    }

    // the type this line belongs to, or null. for the game + tests
    static boolean inType(String type, String plain) {
        load();
        Lines l = LINES.get(type);
        return l != null && l.has(plain);
    }

    private static ChatBus.Result onChat(ChatBus.Line line) {
        String t = line.plain();
        if (DungeonMarks.opt(FEATURE, "breaker") && BREAKER.matcher(t).matches()) return ChatBus.CANCEL;
        if (DungeonMarks.opt(FEATURE, "rare_drops") && inType("rare_drops", t)) return ChatBus.CANCEL;
        // from skyhanni DungeonChatFilter.block: preparing + starting count before the island says so
        if (DungeonMarks.opt(FEATURE, "prepare") && inType("prepare", t)) return ChatBus.CANCEL;
        if (DungeonMarks.opt(FEATURE, "start") && inType("start", t)) return ChatBus.CANCEL;
        if (!DungeonRun.inDungeon()) return ChatBus.PASS;
        if (DungeonMarks.opt(FEATURE, "milestones") && MILESTONE.matcher(t).matches()) return ChatBus.CANCEL;
        if (DungeonMarks.opt(FEATURE, "mimic") && MIMIC.matcher(t.trim()).matches()) return ChatBus.CANCEL;
        for (String type : IN_DUNGEON) if (DungeonMarks.opt(FEATURE, type) && inType(type, t)) return ChatBus.CANCEL;
        return ChatBus.PASS;
    }

    public static void register() {
        Feature f = Features.add(new Feature(FEATURE, "Dungeons", "Dungeon chat filter", "Hides kinds of lines in dungeons, each with its own switch. Nothing is hidden until you turn it on.", "SkyHanni: Dungeon Filters, Message Filter; Skyblocker: Hide Dungeonbreaker Messages")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.bool("dungeon_chat_filter.prepare", "Getting ready", "Countdown, ready and potion lines before the start.", false))
                .option(Option.bool("dungeon_chat_filter.start", "The start", "Mort's lines and the class stat lines.", false))
                .option(Option.bool("dungeon_chat_filter.ambience", "Ambience", "Lines like a shiver running down your spine.", false))
                .option(Option.bool("dungeon_chat_filter.pickup", "Pickups", "What others pick up, and picked up orbs and essence.", false))
                .option(Option.bool("dungeon_chat_filter.reminder", "Key reminders", "How to use a key you picked up.", false))
                .option(Option.bool("dungeon_chat_filter.buff", "Blessings", "Blessings found and what they grant.", false))
                .option(Option.bool("dungeon_chat_filter.not_possible", "Not possible", "Blocked in the way, already opened, cannot use that here.", false))
                .option(Option.bool("dungeon_chat_filter.damage", "Damage", "Who hit you and what your abilities hit.", false))
                .option(Option.bool("dungeon_chat_filter.ability", "Abilities", "Abilities ready, healing and tethers.", false))
                .option(Option.bool("dungeon_chat_filter.puzzle", "Puzzles", "Puzzle solved lines and the quiz statue's talk.", false))
                .option(Option.bool("dungeon_chat_filter.end", "The end", "Unlocked essence and team bonus lines.", false))
                .option(Option.bool("dungeon_chat_filter.fairy", "Fairy dialogue", "What a fairy says when killed.", false))
                .option(Option.bool("dungeon_chat_filter.keys_and_doors", "Keys and doors", "Picking up keys and opening doors.", false))
                .option(Option.bool("dungeon_chat_filter.rare_drops", "Rare rewards of others", "Rare rewards others get from chests.", false))
                .option(Option.bool("dungeon_chat_filter.solo_class", "Solo class", "That your stats are doubled for playing a class alone.", false))
                .option(Option.bool("dungeon_chat_filter.solo_stats", "Solo class stats", "The boosted stats when a run starts.", false))
                .option(Option.bool("dungeon_chat_filter.milestones", "Milestones", "Milestone lines; the splits card still counts them.", false))
                .option(Option.bool("dungeon_chat_filter.mimic", "Mimic kills", "Mimic dead! and Mimic Killed! in party chat; the score still counts them.", false))
                .option(Option.bool("dungeon_chat_filter.breaker", "Dungeonbreaker", "When you cannot dig there or are out of charges.", false)));
        ChatBus.register(ChatBus.FILTER + 5, "dungeonchat", f, DungeonChat::onChat);
    }
}
