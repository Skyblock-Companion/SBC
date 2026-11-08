// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.chat.sh;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.zmuzy.sbc.SbcMod;
import dev.zmuzy.sbc.core.GameState;
import dev.zmuzy.sbc.core.Patterns;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni PlayerChatManager (which lines are player chat: coop, party, guild, dm, item show, global),
// PlayerChatFilter + MultiFilter on PlayerChatFilter.json (hides all-chat trade spam) and PlayerChatModifier
// (rank hider, ranks out, names aqua)
public final class PlayerChat {
    public enum Kind { COOP, PARTY, GUILD, PRIVATE, ITEM_SHOW, NPC, ALL, NONE }

    static final Patterns.P GLOBAL = Patterns.p("SH.data.chat.player.global");
    static final Patterns.P PARTY = Patterns.p("SH.data.chat.player.party");
    static final Patterns.P COOP = Patterns.p("SH.data.chat.player.coop");
    static final Patterns.P GUILD = Patterns.p("SH.data.chat.player.guild");
    static final Patterns.P PRIVATE = Patterns.p("SH.data.chat.player.privatemessage");
    static final Patterns.P ITEM_SHOW = Patterns.p("SH.data.chat.player.itemshow");
    static final Patterns.P[] RANK = {Patterns.p("Compact.RANK_0"), Patterns.p("Compact.RANK_1")};
    static final Patterns.P RANK_POSSESSIVE = Patterns.p("Compact.RANK_POSSESSIVE"), RANK_SPACE = Patterns.p("Compact.RANK_SPACE");

    public static final class MultiFilter {
        final List<String> equals = new ArrayList<>(), startsWith = new ArrayList<>(), endsWith = new ArrayList<>(), contains = new ArrayList<>(), containsWord = new ArrayList<>();

        void load(JsonObject o) {
            fill(equals, o, "equals");
            fill(startsWith, o, "startsWith");
            fill(endsWith, o, "endsWith");
            fill(contains, o, "contains");
            fill(containsWord, o, "containsWord");
        }

        private static void fill(List<String> list, JsonObject o, String key) {
            if (!o.has(key)) return;
            for (JsonElement e : o.getAsJsonArray(key)) list.add(e.getAsString());
        }

        public String matchResult(String string) {
            for (String s : equals) if (s.equals(string)) return s;
            for (String s : startsWith) if (string.startsWith(s)) return s;
            for (String s : endsWith) if (string.endsWith(s)) return s;
            for (String s : contains) if (string.contains(s)) return s;
            for (String s : containsWord) for (String w : string.split(" ")) if (w.trim().equals(s)) return s;
            return null;
        }

        int count() {
            return equals.size() + startsWith.size() + endsWith.size() + contains.size() + containsWord.size();
        }
    }

    static final Map<String, MultiFilter> filters = new LinkedHashMap<>();
    private static boolean loaded;

    private PlayerChat() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        try (var in = dev.zmuzy.sbc.core.data.DataFiles.sh("PlayerChatFilter.json")) {
            if (in == null) return;
            JsonObject root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (JsonElement e : root.getAsJsonArray("filters")) {
                JsonObject o = e.getAsJsonObject();
                MultiFilter f = new MultiFilter();
                f.load(o);
                filters.put(o.has("description") ? o.get("description").getAsString() : String.valueOf(filters.size()), f);
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Bad PlayerChatFilter.json", e);
        }
    }

    public static int filterCount() {
        load();
        int n = 0;
        for (MultiFilter f : filters.values()) n += f.count();
        return n;
    }

    // PlayerChatManager.onChat: what kind of player chat this is, NONE for system lines
    public static Kind kind(String formatted, String ownName) {
        if (COOP.matcher(formatted).matches()) return Kind.COOP;
        if (PARTY.matcher(formatted).matches()) return Kind.PARTY;
        if (GUILD.matcher(formatted).matches()) return Kind.GUILD;
        if (PRIVATE.matcher(formatted).matches()) return Kind.PRIVATE;
        if (ITEM_SHOW.matcher(formatted).matches()) return Kind.ITEM_SHOW;
        Matcher m = GLOBAL.matcher(formatted);
        if (m.matches()) {
            String author = m.group("author"), chatColor = m.group("chatColor");
            // last format string is always there unless its your own message
            if (chatColor.isEmpty() && !GameState.strip(author).endsWith(ownName)) return Kind.NONE;
            if (GameState.strip(author).contains("[NPC]")) return Kind.NPC;
            return Kind.ALL;
        }
        return Kind.NONE;
    }

    // PlayerChatFilter.shouldChatFilter: description of the filter that hit, or null
    public static String shouldChatFilter(String formatted) {
        load();
        String message = formatted.toLowerCase(Locale.ROOT);
        for (var f : filters.entrySet()) if (f.getValue().matchResult(message) != null) return f.getKey();
        return null;
    }

    // PlayerChatModifier.cutMessage with playerRankHider on
    public static String hideRanks(String input) {
        String string = input;
        for (Patterns.P p : RANK) string = p.matcher(string).replaceAll("§b$1");
        string = RANK_POSSESSIVE.matcher(string).replaceAll("§b$1's");
        string = RANK_SPACE.matcher(string).replaceAll("§b$1 $2");
        return string;
    }
}
