// SPDX-License-Identifier: GPL-3.0-only
package io.muzy.sbc.ui;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.feature.Feature;
import io.muzy.sbc.ui.hud.HudElement;

import java.util.List;
import java.util.Set;

// hud editor categories = settings sections grouped the way you play, so the editor shows one activity at a time
// General shows everywhere, but in other categories only its cards that are up right now
public final class HudGroups {
    public record Group(String name, Set<String> sections, Set<String> islands) {}

    public static final Group GENERAL = new Group("General",
            Set.of("HUD", "Bar parts", "Chat", "Items", "Menus", "Navigation", "Tracking", "Skills", "Pets", "Minions", "World", "Commands", "Misc"),
            Set.of("HUB", "PRIVATE_ISLAND", "DARK_AUCTION"));

    public static final List<Group> ALL = List.of(
            GENERAL,
            new Group("Farming & Garden", Set.of("Garden", "Farming Islands"), Set.of("GARDEN", "THE_FARMING_ISLANDS")),
            new Group("Mining", Set.of("Mining"), Set.of("GOLD_MINES", "DEEP_CAVERNS", "DWARVEN_MINES", "CRYSTAL_HOLLOWS", "MINESHAFT")),
            new Group("Dungeons", Set.of("Dungeons"), Set.of("DUNGEON_HUB", "CATACOMBS")),
            new Group("Slayer & Combat", Set.of("Slayer", "Combat"), Set.of("SPIDER_DEN", "THE_END")),
            new Group("Crimson Isle", Set.of("Crimson Isle"), Set.of("CRIMSON_ISLE", "KUUDRA")),
            new Group("Rift", Set.of("Rift"), Set.of("THE_RIFT")),
            new Group("Fishing, Foraging & Hunting", Set.of("Fishing", "Foraging", "Hunting"),
                    Set.of("BACKWATER_BAYOU", "THE_PARK", "GALATEA", "TORRHUS_CANYON", "SAFARI", "LOTUS_ATOLL")),
            new Group("Events", Set.of("Events"), Set.of("WINTER")));
    private HudGroups() {}

    // its features section, or its own when it has no feature
    public static String sectionOf(HudElement e) {
        Feature f = e.feature();
        return f != null ? f.module : e.section;
    }

    // doesnt fit anywhere = General, so no card gets lost
    public static Group of(HudElement e) {
        String s = sectionOf(e);
        for (Group g : ALL) if (g.sections().contains(s)) return g;
        return GENERAL;
    }

    // null = nothing fits better than "whats here"
    public static Group forIsland(String island) {
        if (island == null) return null;
        for (Group g : ALL) if (g != GENERAL && g.islands().contains(island)) return g;
        return null;
    }

    public static Group here() {
        return forIsland(GameState.island);
    }

    // all of the category + General cards that are up now (they take the same room in game)
    public static boolean shows(Group picked, HudElement e, boolean upNow) {
        Group g = of(e);
        return g == picked || (g == GENERAL && upNow);
    }

    public static int count(Group g) {
        int n = 0;
        for (HudElement e : HudLayout.elements()) {
            Feature f = e.feature();
            if ((f == null || f.enabled()) && of(e) == g) n++;
        }
        return n;
    }
}
