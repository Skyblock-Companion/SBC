// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// current mayor, minister + perks from hypixels public election endpoint
public final class Mayor {
    public record Perk(String name, String description) {}

    public static volatile String name = "", minister = "";
    public static volatile List<Perk> perks = List.of();
    public static volatile List<String> candidates = List.of();
    private static volatile long fetched;
    private static volatile boolean loading;

    private Mayor() {}

    public static void tick() {
        long now = System.currentTimeMillis();
        if (loading || now - fetched < 10 * 60_000) return;
        loading = true;
        Net.getJson("https://api.hypixel.net/v2/resources/skyblock/election", null).whenComplete((json, e) -> {
            loading = false;
            fetched = System.currentTimeMillis();
            if (e != null) return;
            JsonObject o = json.getAsJsonObject();
            List<Perk> ps = new ArrayList<>();
            if (o.has("mayor")) {
                JsonObject m = o.getAsJsonObject("mayor");
                name = m.get("name").getAsString();
                for (JsonElement p : m.getAsJsonArray("perks"))
                    ps.add(new Perk(p.getAsJsonObject().get("name").getAsString(), clean(p.getAsJsonObject().get("description").getAsString())));
                if (m.has("minister")) {
                    JsonObject mi = m.getAsJsonObject("minister");
                    minister = mi.get("name").getAsString();
                    JsonObject perk = mi.getAsJsonObject("perk");
                    if (perk != null) ps.add(new Perk(perk.get("name").getAsString() + " (Minister)", clean(perk.get("description").getAsString())));
                }
            }
            perks = ps;
            List<String> cs = new ArrayList<>();
            if (o.has("current") && o.getAsJsonObject("current").has("candidates"))
                for (JsonElement c : o.getAsJsonObject("current").getAsJsonArray("candidates")) cs.add(c.getAsJsonObject().get("name").getAsString());
            candidates = cs;
        });
    }

    private static String clean(String s) {
        return GameState.strip(s);
    }

    // planner boost for an activity category from the active perks text (a mining perk boosts mining)
    public static double boost(String category) {
        String c = category.toLowerCase(Locale.ROOT);
        double b = 1;
        for (Perk p : perks) {
            String t = (p.name() + " " + p.description()).toLowerCase(Locale.ROOT);
            if (t.contains(c)) b += 0.25;
            if (c.equals("diana") && t.contains("mytholog")) b += 1.0;
            if (c.equals("slayer") && t.contains("slayer")) b += 0.25;
            if (c.equals("dungeons") && (t.contains("dungeon") || t.contains("catacomb"))) b += 0.25;
        }
        return b;
    }

    // from skyhanni ElectionApi.calculateNextMayorTime: 27th of late spring after this election year
    public static long nextElection(long now) {
        SbTime.SbDate d = SbTime.sbDate(now);
        int year = d.year();
        if (d.month() < 2 || (d.month() == 2 && d.day() < 27)) year--;
        return SbTime.sbToReal(year + 1, 2, 27);
    }

    public static boolean dianaActive() {
        for (Perk p : perks) if ((p.name() + p.description()).toLowerCase(Locale.ROOT).contains("mytholog")) return true;
        return false;
    }
}
