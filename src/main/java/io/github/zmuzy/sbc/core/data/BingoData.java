// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.data.DataFiles.Source;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

// this months bingo as data other features can read. port of skyhanni features/bingo/BingoApi.kt: times from
// hypixels public resource (no key), asked max once an hour while on skyblock
public final class BingoData {
    private static final String URL = "https://api.hypixel.net/v2/resources/skyblock/bingo";
    private static final long HOUR = 3_600_000L, DAY = 86_400_000L, NPC_OFFSET = 3 * DAY, CHECK = 60_000L;

    // skyhanni BingoData from the repo: how hard a goal is + how to do it
    public record Tip(String difficulty, List<String> note, List<String> guide, String found) {}

    // BingoApiResponseJson minus goal progress: what the current event is
    public record Event(int id, String name, long start, long end, String modifier) {}

    private static volatile Event event;
    private static Map<String, Integer> ranks;
    private static Map<String, Tip> tips;
    private static long lastFetch, lastCheck;
    private static boolean fetching;

    private BingoData() {}

    // --- the event ---

    // from skyhanni getStartOfMonth: bingo runs the first week so the last days of a month count as the next one
    public static long startOfMonth(LocalDate today) {
        return OffsetDateTime.of(today.plusDays(5).withDayOfMonth(1), LocalTime.MIDNIGHT, ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    // event as far as we know: hypixels answer, or the first week of the month like BingoApi starts with
    public static Event event() {
        Event e = event;
        if (e != null) return e;
        long start = startOfMonth(LocalDate.now(ZoneOffset.UTC));
        return new Event(0, "", start, start + 7 * DAY, "NORMAL");
    }

    // from skyhanni isInBingoEventWindow: 3 days before till 3 days after, when the bingo npc is around
    public static boolean inEventWindow(long now) {
        Event e = event();
        return now >= e.start() - NPC_OFFSET && now <= e.end() + NPC_OFFSET;
    }

    // the apis answer, or null if its not one
    static Event parse(String text) {
        try {
            JsonObject o = JsonParser.parseString(text).getAsJsonObject();
            if (!o.get("success").getAsBoolean()) return null;
            return new Event(o.get("id").getAsInt(), o.get("name").getAsString(), o.get("start").getAsLong(), o.get("end").getAsLong(),
                    o.has("modifier") ? o.get("modifier").getAsString() : "");
        } catch (Exception e) {
            return null;
        }
    }

    // from skyhanni onSecondPassed: checks once a min, fetches when the last answer is an hour old
    static void tick() {
        long now = System.currentTimeMillis();
        if (now - lastCheck < CHECK) return;
        lastCheck = now;
        if (fetching || now - lastFetch < HOUR || !GameData.autoUpdate()) return;
        fetching = true;
        CompletableFuture.runAsync(() -> {
            try {
                DataUpdater.Reply r = DataWeb.INSTANCE.get(URL, DataWeb.PLAIN_HEADERS);
                Event e = r.ok() ? parse(r.text()) : null;
                if (e != null) {
                    event = e;
                    lastFetch = System.currentTimeMillis();
                }
            } catch (Exception e) {
                SbcMod.LOG.debug("Could not read the bingo times: {}", e.toString());
            } finally {
                fetching = false;
            }
        });
    }

    // /shupdatebingodata: ask again now
    public static void refresh() {
        lastFetch = 0;
        lastCheck = 0;
        tick();
    }

    // --- the repo files ---

    private static synchronized Map<String, Integer> ranks() {
        if (ranks == null) {
            ranks = new LinkedHashMap<>();
            JsonObject o = DataFiles.object(Source.SKYHANNI, "BingoRanks.json");
            if (o != null && o.has("ranks"))
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("ranks").entrySet()) ranks.put(e.getKey(), e.getValue().getAsInt());
        }
        return ranks;
    }

    // from skyhanni BingoApi.getRank: which rank icon a scoreboard line has, or null
    public static Integer rank(String text) {
        for (Map.Entry<String, Integer> e : ranks().entrySet()) if (text.contains(e.getKey())) return e.getValue();
        return null;
    }

    // from skyhanni BingoApi.getIcon: a ranks icon, or null
    public static String icon(int rank) {
        for (Map.Entry<String, Integer> e : ranks().entrySet()) if (e.getValue() == rank) return e.getKey();
        return null;
    }

    private static synchronized Map<String, Tip> tips() {
        if (tips == null) {
            tips = new LinkedHashMap<>();
            JsonObject o = DataFiles.object(Source.SKYHANNI, "Bingo.json");
            if (o != null && o.has("bingo_tips")) {
                for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("bingo_tips").entrySet()) {
                    JsonObject t = e.getValue().getAsJsonObject();
                    tips.put(e.getKey(), new Tip(t.has("difficulty") ? t.get("difficulty").getAsString() : "", lines(t, "note"), noOtherMod(lines(t, "guide")),
                            t.has("found") ? t.get("found").getAsString() : null));
                }
            }
        }
        return tips;
    }

    private static List<String> lines(JsonObject t, String key) {
        List<String> out = new ArrayList<>();
        if (t.has(key)) for (JsonElement l : t.getAsJsonArray(key)) out.add(l.getAsString());
        return out;
    }

    // tips come as is from the other mods data and one of them recommends that mod
    private static List<String> noOtherMod(List<String> lines) {
        lines.replaceAll(l -> l.replace("SkyHanni's ", "a "));
        return lines;
    }

    // from skyhanni BingoGoal.getData for a personal goal: tip with exactly this name, null if none
    public static Tip tipExact(String itemName) {
        return tips().get(itemName);
    }

    // from skyhanni BingoApi.getData: tip by the start of the item name minus "(Community Goal)", null if none
    public static Tip tip(String itemName) {
        for (Map.Entry<String, Tip> e : tips().entrySet())
            if (itemName.startsWith(e.getKey().split(" \\(Community Goal\\)")[0])) return e.getValue();
        return null;
    }
}
