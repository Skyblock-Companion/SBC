// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.dungeon;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.muzy.sbc.SbcMod;
import io.muzy.sbc.core.Gain;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.LegacyText;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.core.Repo;
import io.muzy.sbc.core.Vitals;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.feature.Features;
import io.muzy.sbc.feature.Option;
import io.muzy.sbc.render.ParkourHelper;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni DungeonsRaceGuide: the way through the dungeon hub races from the repos DungeonHubRaces.json
// (only the "nothing, no return" course, same as there)
public final class DungeonRaces {
    private static final String FEATURE = "dungeon_races";
    private static final Patterns.P ACTIVE = Patterns.p("SH.dungeon.race.active");
    private static Map<String, ParkourHelper> helpers;
    private static boolean inRace;
    private static String current;
    private static String lastBar = "";

    private DungeonRaces() {}

    private static Vec3 vec(String s) {
        String[] p = s.split(":");
        return new Vec3(Double.parseDouble(p[0]), Double.parseDouble(p[1]), Double.parseDouble(p[2]));
    }

    private static Map<String, ParkourHelper> helpers() {
        if (helpers != null) return helpers;
        Map<String, ParkourHelper> out = new HashMap<>();
        try {
            JsonObject root = Repo.constant("DungeonHubRaces.json");
            if (root != null) {
                for (var e : root.getAsJsonObject("data").entrySet()) {
                    JsonObject course = e.getValue().getAsJsonObject().getAsJsonObject("nothing:no_return");
                    if (course == null) continue;
                    List<Vec3> locations = new ArrayList<>();
                    for (JsonElement l : course.getAsJsonArray("locations")) locations.add(vec(l.getAsString()));
                    List<ParkourHelper.ShortCut> cuts = new ArrayList<>();
                    JsonArray sc = course.getAsJsonArray("shortCuts");
                    if (sc != null) for (JsonElement c : sc) cuts.add(new ParkourHelper.ShortCut(c.getAsJsonObject().get("from").getAsInt(), c.getAsJsonObject().get("to").getAsInt()));
                    out.put(e.getKey(), new ParkourHelper(locations, cuts, 1.0, 7.0, false, null, false));
                }
            }
        } catch (Exception ex) {
            SbcMod.LOG.warn("Could not read the dungeon hub races", ex);
        }
        helpers = out;
        return out;
    }

    private static void reset() {
        inRace = false;
        current = null;
        if (helpers != null) helpers.values().forEach(ParkourHelper::reset);
    }

    // from skyhanni DungeonsRaceGuide.onActionBarUpdate. name like the data file has it
    static String raceOf(String actionBarCoded) {
        Matcher m = ACTIVE.matcher(actionBarCoded);
        return m.find() ? m.group("race").replace(" ", "_").toLowerCase(Locale.ROOT) : null;
    }

    private static void tick(Minecraft mc) {
        if (mc.player == null) return;
        // from skyhanni onOwnInventoryItemUpdate: slot 8 has a cancel item during a race
        boolean now = "CANCEL_RACE_ITEM".equals(Gain.sbId(mc.player.getInventory().getNonEquipmentItems().get(8)));
        if (inRace && !now) reset();
        inRace = now;
        String bar = Vitals.actionBarRaw;
        if (bar != null && !bar.equals(lastBar)) {
            lastBar = bar;
            String race = raceOf(LegacyText.of(Component.literal(bar)));
            if (race != null) current = race;
        }
    }

    private static void draw(WorldDraw w) {
        if (!inRace || current == null) return;
        ParkourHelper h = helpers().get(current);
        if (h == null) return;
        h.rainbowColor = DungeonMarks.opt(FEATURE, "rainbow");
        h.monochromeColor = DungeonMarks.color(FEATURE, "color", 0xFF0000FF);
        h.lookAhead = DungeonMarks.integer(FEATURE, "look_ahead", 3) + 1;
        h.playerPos = () -> w.mc.player.position();
        h.onGround = () -> w.mc.player.onGround();
        h.render(w);
    }

    public static void register() {
        Features.add(new Feature(FEATURE, "Dungeons", "Dungeon Hub races", "Shows the way through the races of the Dungeon Hub while you run one.", "SkyHanni: Dungeon Races Guide")
                .when(() -> "DUNGEON_HUB".equals(GameState.island) || SbcMod.DEV).tick(DungeonRaces::tick).world(DungeonRaces::draw).left(DungeonRaces::reset)
                .option(Option.integer("dungeon_races.look_ahead", "Look ahead", "How many points are shown in front of you.", 3, 1, 30))
                .option(Option.bool("dungeon_races.rainbow", "Rainbow", "A moving rainbow instead of one colour.", true))
                .option(Option.color("dungeon_races.color", "Colour", "The colour when the rainbow is off.", 0xFF0000FF)));
    }
}
