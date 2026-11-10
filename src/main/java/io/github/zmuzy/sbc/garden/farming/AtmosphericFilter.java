// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.garden.farming;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.SbTime;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Option;
import io.github.zmuzy.sbc.garden.GardenPack;
import io.github.zmuzy.sbc.rift.RichCard;
import io.github.zmuzy.sbc.ui.HudLayout;
import io.github.zmuzy.sbc.ui.HudLayout.Zone;

import java.util.ArrayList;
import java.util.List;

// from skyhanni AtmosphericFilterDisplay + SkyblockSeason: the buff the atmospheric filter gives this season
public final class AtmosphericFilter {
    public static final String ID = "garden_atmospheric";
    private static final String[][] SEASONS = {
            {"§dSpring", "§7Gain §6+25☘ Farming Fortune§7.", "§6+25☘"},
            {"§6Summer", "§7Gain §3+20☯ Farming Wisdom§7.", "§3+20☯"},
            {"§eAutumn", "§4Pests §7spawn §a15% §7more often.", "§a15%+ §4Pests"},
            {"§9Winter", "§7Visitors give §a5% §7more §cCopper.", "§a5%+§cC"}};

    private AtmosphericFilter() {}

    // SkyblockSeason.getSeasonByMonth for a zero based month, 3 months a season
    static String[] season(int month) {
        return SEASONS[Math.floorMod(month, 12) / 3];
    }

    private static List<RichCard.Line> lines(String[] s) {
        List<RichCard.Line> out = new ArrayList<>();
        String perk = s[GardenPack.on(ID, "short_perk") ? 2 : 1];
        if (!GardenPack.on(ID, "only_buff")) {
            String name = GardenPack.on(ID, "short_season") ? s[0].substring(0, Math.min(6, s[0].length())) : s[0];
            out.add(RichCard.Line.row("Season", name));
        }
        out.add(RichCard.Line.row("Buff", s[2]).key("Buff"));
        if (!perk.equals(s[2])) out.add(RichCard.Line.row(GameState.strip(perk), ""));
        return out;
    }

    private static final RichCard CARD = new RichCard(ID, "Atmospheric Filter", "Garden", Zone.TOP_LEFT, 39, ID) {
        @Override
        public boolean visible() {
            return GardenPack.garden() || GardenPack.skyblock() && GardenPack.on(ID, "outside");
        }

        @Override
        protected Content live() {
            return new Content("", lines(season(SbTime.sbDate(System.currentTimeMillis()).month())));
        }

        @Override
        protected Content sample() {
            return new Content("", lines(SEASONS[0]));
        }

        @Override
        protected List<Row> classicRows() {
            String[] s = season(SbTime.sbDate(System.currentTimeMillis()).month());
            return List.of(new Row(GameState.strip(s[0]), GameState.strip(s[2]), 0));
        }

        @Override
        protected List<Row> classicDemo() {
            return List.of(new Row("Spring", "+25☘", 0));
        }
    };

    public static void register() {
        Features.add(new Feature(ID, "Garden", "Atmospheric Filter", "The buff the Atmospheric Filter gives in the current season.", "SkyHanni: Atmospheric Filter Display")
                .when(GardenPack::skyblock).defaultOff()
                .option(Option.bool("garden_atmospheric.short_perk", "Short buff", "Only the amount, not the sentence.", false))
                .option(Option.bool("garden_atmospheric.short_season", "Short season", "The season's first letters.", false))
                .option(Option.bool("garden_atmospheric.only_buff", "Only the buff", "Without the season.", false))
                .option(Option.bool("garden_atmospheric.outside", "Outside the Garden too", "", false)));
        HudLayout.add(CARD);
    }
}
