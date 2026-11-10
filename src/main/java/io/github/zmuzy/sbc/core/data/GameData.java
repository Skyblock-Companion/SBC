// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.core.data;

import io.github.zmuzy.sbc.SbcMod;
import io.github.zmuzy.sbc.core.Config;
import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.feature.Feature;
import io.github.zmuzy.sbc.feature.Features;
import io.github.zmuzy.sbc.feature.Option;

// the setting that keeps data fresh + what runs while playing: bingo times + maxwells menu
public final class GameData {
    public static final String ID = "game_data";
    private static final String AUTO = "game_data.auto";

    private GameData() {}

    public static void register() {
        Feature f = Features.add(new Feature(ID, "Misc", "Game data",
                "Keeps the mod's lists of game facts current without a new version of the mod. What it fetches is used from the next start.",
                "SkyHanni: repo auto update")
                .alwaysOn()
                .when(() -> GameState.inSkyBlock || SbcMod.DEV)
                .option(Option.bool(AUTO, "Update game data automatically",
                        "Once when the game starts, asks GitHub whether the data has changed. Sends nothing about you.", true))
                .tick(mc -> BingoData.tick()));
        MaxwellData.register(f);
    }

    // for other parts that refresh their own data
    public static boolean updatesOn() {
        return autoUpdate();
    }

    // the setting read from where Option stores it, this runs before features exist
    static boolean autoUpdate() {
        return !Boolean.FALSE.equals(Config.I.options.get(AUTO));
    }
}
