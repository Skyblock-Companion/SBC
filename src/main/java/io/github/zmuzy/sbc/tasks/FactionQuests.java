// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.tasks;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Profile;
import io.github.zmuzy.sbc.map.MapRender;
import io.github.zmuzy.sbc.nav.Navigator;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// crimson faction quests from the tab ("✖ Mage Outlaw", "✔ Tenacity"), placed with skyhannis quest data
// (MIT): where each miniboss, dojo test, kuudra tier + fishing spot is
public final class FactionQuests {
    public record Quest(String name, String category, boolean done, Vec3 location, String item) {}

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("skyhanni/repo/constants/CrimsonIsleReputation.json");
    private static final io.github.zmuzy.sbc.core.Patterns.P LINE = io.github.zmuzy.sbc.core.Patterns.p("FactionQuests.LINE");
    private static final io.github.zmuzy.sbc.core.Patterns.P KUUDRA = io.github.zmuzy.sbc.core.Patterns.p("FactionQuests.KUUDRA");
    private static final io.github.zmuzy.sbc.core.Patterns.P FACTION_TAB = io.github.zmuzy.sbc.core.Patterns.p("FactionQuests.FACTION_TAB");
    private static final Map<String, Object[]> DATA = new HashMap<>(); // name -> category, Vec3 or null, item
    private static final Vec3 LAVA_FISHING = new Vec3(-366, 120, -780);

    private FactionQuests() {}

    public static void load() {
        try {
            String text = Files.exists(FILE) ? Files.readString(FILE) : io.github.zmuzy.sbc.core.Repo.bundled("CrimsonIsleReputation.json");
            if (text == null) return;
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            for (Map.Entry<String, JsonElement> cat : root.entrySet()) {
                if (!cat.getValue().isJsonObject()) continue;
                for (Map.Entry<String, JsonElement> q : cat.getValue().getAsJsonObject().entrySet()) {
                    JsonObject o = q.getValue().getAsJsonObject();
                    Vec3 loc = null;
                    if (o.has("location") && o.getAsJsonArray("location").size() == 3) {
                        var a = o.getAsJsonArray("location");
                        loc = new Vec3(a.get(0).getAsDouble(), a.get(1).getAsDouble(), a.get(2).getAsDouble());
                    }
                    DATA.put(q.getKey().toLowerCase(Locale.ROOT), new Object[]{cat.getKey(), loc, o.has("item") ? o.get("item").getAsString() : ""});
                }
            }
        } catch (Exception e) {
            SbcMod.LOG.warn("Could not read SkyHanni's Crimson Isle quest data", e);
        }
    }

    // "Barbarian" or "Mage": the tab rep widget, else your profile, else whichever faction you have more rep with
    public static String faction() {
        for (String l : GameState.tab) {
            Matcher m = FACTION_TAB.matcher(l);
            if (m.find()) return m.group(1);
        }
        Profile.Data d = Profile.current;
        if (d != null && !d.faction.isEmpty()) return d.faction.toLowerCase(Locale.ROOT).startsWith("mage") ? "Mage" : "Barbarian";
        if (d != null && d.magesRep > d.barbRep) return "Mage";
        return "Barbarian";
    }

    // todays quests from the tabs "Faction Quests:" part
    public static List<Quest> quests() {
        List<Quest> out = new ArrayList<>();
        List<String> tab = GameState.tab;
        for (int i = 0; i < tab.size(); i++) {
            if (!tab.get(i).trim().startsWith("Faction Quests")) continue;
            for (int j = i + 1; j < tab.size() && j < i + 8; j++) {
                Matcher m = LINE.matcher(tab.get(j));
                if (!m.find()) break;
                out.add(resolve(m.group(2).trim(), m.group(1).equals("✔")));
            }
            break;
        }
        return out;
    }

    private static Quest resolve(String name, boolean done) {
        Matcher k = KUUDRA.matcher(name);
        Object[] d = DATA.get((k.find() ? k.group(1) : name).toLowerCase(Locale.ROOT));
        if (d == null) for (Map.Entry<String, Object[]> e : DATA.entrySet()) // "Barbarian Duke X" vs "Barbarian Duke"
            if (name.toLowerCase(Locale.ROOT).startsWith(e.getKey()) || e.getKey().startsWith(name.toLowerCase(Locale.ROOT))) d = e.getValue();
        if (d == null) return new Quest(name, name.toLowerCase(Locale.ROOT).contains("rescue") ? "RESCUE" : "", done, null, "");
        Vec3 loc = (Vec3) d[1];
        if (loc == null && d[0].equals("FISHING")) loc = LAVA_FISHING;
        return new Quest(name, (String) d[0], done, loc, (String) d[2]);
    }

    // your factions town (where the town board is): dragontail for barbarians, scarleton for mages
    public static Vec3 townBoard() {
        String town = faction().equals("Mage") ? "Scarleton" : "Dragontail";
        for (MapRender.Marker a : MapRender.areas("CRIMSON_ISLE")) if (a.name().equals(town)) return a.pos();
        return faction().equals("Mage") ? new Vec3(-60, 108, -775) : new Vec3(-560, 105, -720);
    }

    // where to go next: closest open quest with a spot. rescue mission only when its the last one
    public static Navigator.Target next() {
        var p = Minecraft.getInstance().player;
        if (p == null) return null;
        Quest best = null, rescue = null;
        Vec3 bestPos = null;
        double bd = Double.MAX_VALUE;
        for (Quest q : quests()) {
            if (q.done()) continue;
            if (q.category().equals("RESCUE")) {
                rescue = q;
                continue;
            }
            Vec3 pos = q.location();
            if (q.category().equals("FETCH")) {
                // hand in at the town board once you carry the item, till then theres nowhere to walk
                String item = io.github.zmuzy.sbc.core.ItemRepo.name(q.item());
                if (Mission.inInventory(item) <= 0 && Mission.inInventory(q.name()) <= 0) continue;
                pos = townBoard();
            }
            if (pos == null) continue;
            double d = pos.distanceToSqr(p.position());
            if (d < bd) {
                bd = d;
                best = q;
                bestPos = pos;
            }
        }
        if (best != null)
            return new Navigator.Target("CRIMSON_ISLE", best.name() + (best.category().equals("FETCH") ? " (hand in at the Town Board)" : ""), bestPos);
        if (rescue != null) {
            String enemy = faction().equals("Mage") ? "Dragontail" : "Scarleton";
            for (MapRender.Marker a : MapRender.areas("CRIMSON_ISLE"))
                if (a.name().equals(enemy)) return new Navigator.Target("CRIMSON_ISLE", "Rescue Mission (" + enemy + ")", a.pos());
        }
        return null;
    }

    // fetch quests you cant walk to yet: the items you still need
    public static List<String> missingFetch() {
        List<String> out = new ArrayList<>();
        for (Quest q : quests())
            if (!q.done() && q.category().equals("FETCH")) {
                String item = io.github.zmuzy.sbc.core.ItemRepo.name(q.item());
                if (Mission.inInventory(item) <= 0 && Mission.inInventory(q.name()) <= 0) out.add(item.isEmpty() ? q.name() : item);
            }
        return out;
    }

    // ticks the daily task by the number of ✔ quests in tab, so done quests count even without a chat line
    public static void syncProgress() {
        if (!"CRIMSON_ISLE".equals(GameState.island)) return;
        long done = quests().stream().filter(Quest::done).count();
        TaskDef t = Catalog.byId("crimson_quests");
        if (t == null || done <= 0) return;
        int have = Routine.progress(t, System.currentTimeMillis());
        if (done > have) Routine.add(t, (int) (done - have));
    }
}
