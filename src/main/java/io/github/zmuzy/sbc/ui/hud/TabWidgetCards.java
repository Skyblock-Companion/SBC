// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.ui.hud;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.TabWidgets;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// from skyhanni gui/TabWidgetDisplay: any /tab widget kept on the hud, each its own card. hypixel only sends
// what you turned on in /tab so empty cards stay away
public final class TabWidgetCards extends HudElement {
    public static final String ID = "tab_widget_cards";

    // from skyhanni TabWidgetDisplay: name + which hypixel widgets it shows
    private record Entry(String name, String... widgets) {}

    private static final Entry[] ENTRIES = {
            new Entry("Soulflow", "SOULFLOW"),
            new Entry("Bank and Interest", "BANK", "INTEREST"),
            new Entry("SkyBlock Level", "SB_LEVEL"),
            new Entry("Profile", "PROFILE"),
            new Entry("Players", "PLAYER_LIST"),
            new Entry("Pet", "PET"),
            new Entry("Pet Upgrade Info", "PET_SITTER", "PET_TRAINING"),
            new Entry("Stats", "STATS", "DUNGEON_SKILLS_AND_STATS"),
            new Entry("Dungeon team", "DUNGEON_PARTY"),
            new Entry("Dungeon puzzles", "DUNGEON_PUZZLE"),
            new Entry("Dungeon overall", "DUNGEON_STATS"),
            new Entry("Bestiary", "BESTIARY"),
            new Entry("Dragon fight", "DRAGON"),
            new Entry("Protector state", "PROTECTOR"),
            new Entry("Shen's Auction", "RIFT_SHEN"),
            new Entry("Minions", "MINION"),
            new Entry("Collection", "COLLECTION"),
            new Entry("Timers", "TIMERS"),
            new Entry("Fire sale", "FIRE_SALE"),
            new Entry("Park rain", "RAIN"),
            new Entry("Pest traps", "PEST_TRAPS", "FULL_TRAPS", "NO_BAIT"),
            new Entry("Whole profile", "PROFILE", "SB_LEVEL", "BANK", "INTEREST", "SOULFLOW", "FAIRY_SOULS"),
            new Entry("Eyes placed", "EYES_PLACED"),
            new Entry("Foraging beacon", "MOONGLADE_BEACON"),
            new Entry("Starborn Temple", "STARBORN_TEMPLE"),
            new Entry("Shard traps", "SHARD_TRAPS"),
            new Entry("Whispers", "FOREST_WHISPERS"),
            new Entry("Agatha's Contest", "AGATHA_CONTEST"),
            new Entry("Commissions", "COMMISSIONS"),
            new Entry("Slayer", "SLAYER"),
            new Entry("Pity", "PITY"),
            new Entry("Pickaxe cooldown", "PICKAXE_COOLDOWN"),
            new Entry("Miria's Contest", "MIRIA_CONTEST"),
    };

    private static List<String> parsedFrom;
    private static List<TabWidgets.Widget> parsed = List.of();

    private final Entry entry;
    private final Set<String> ids;
    private final String key;

    private TabWidgetCards(Entry entry) {
        super("tabw_" + entry.widgets()[0].toLowerCase(Locale.ROOT) + (entry.widgets().length > 3 ? "_all" : ""), "Tab: " + entry.name(), "HUD", Zone.TOP_LEFT, 80, ID);
        this.entry = entry;
        this.ids = Set.of(entry.widgets());
        this.key = optionKey(entry);
    }

    private static String optionKey(Entry e) {
        return e.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z]+", "_");
    }

    // widget lines from plain tab lines
    static List<String> lines(List<String> tab, Set<String> ids) {
        if (tab != parsedFrom) {
            parsedFrom = tab;
            parsed = TabWidgets.parse(tab);
        }
        List<String> out = new ArrayList<>();
        for (TabWidgets.Widget w : parsed) {
            if (!ids.contains(w.def().id())) continue;
            out.add(tab.get(w.first()).trim());
            for (int i : w.lines()) if (!tab.get(i).isBlank()) out.add(tab.get(i).trim());
        }
        return out;
    }

    private boolean picked() {
        Feature f = feature();
        Option o = f == null ? null : f.option(key);
        return o != null && o.bool();
    }

    @Override
    public boolean active() {
        return super.active() && picked();
    }

    @Override
    public boolean visible() {
        return !lines(GameState.tab, ids).isEmpty();
    }

    @Override
    public String heading() {
        return "";
    }

    private static Row row(String line) {
        int at = line.indexOf(": ");
        if (at <= 0 || at + 2 >= line.length()) return new Row(line);
        return new Row(line.substring(0, at), line.substring(at + 2), 0);
    }

    @Override
    public List<Row> rows() {
        List<Row> out = new ArrayList<>();
        for (String l : lines(GameState.tab, ids)) out.add(row(l));
        return out;
    }

    @Override
    public List<Row> demo() {
        return List.of(new Row(entry.name()), new Row("Your /tab widget", "here", 0));
    }

    public static void register() {
        Feature f = new Feature(ID, "HUD", "/tab widgets on the HUD", "Keeps the widgets you tick on the screen, each its own card to place. Hypixel only sends what you switched on in its /tab menu.", "SkyHanni: Tab Widget")
                .when(() -> GameState.inSkyBlock || SbcMod.DEV).defaultOff();
        for (Entry e : ENTRIES) f.option(Option.bool(ID + "." + optionKey(e), e.name(), "", false));
        Features.add(f);
        for (Entry e : ENTRIES) HudLayout.add(new TabWidgetCards(e));
    }
}
