// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni, Skyblocker; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.combat;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Store;

import java.util.HashMap;
import java.util.Map;

// per profile slayer/combat data. from skyhanni SlayerRngMeterStorage + arrows, skyblocker TheEnd.EndStats
public final class CombatData {
    // from skyhanni SlayerRngMeterStorage
    public static final class Rng {
        public long currentMeter = -1, gainPerBoss = -1, goalNeeded = -1;
        public String itemGoal = "?";
    }

    // per slayer, name without the tier
    public Map<String, Rng> rng = new HashMap<>();
    // slayer kill times in ms keyed by the quests sidebar line. skyhanni slayerPersonalBests, skyblocker SlayerTimer
    public Map<String, Long> slayerBests = new HashMap<>();
    // from skyblocker TheEnd.EndStats
    public int totalZealotKills, zealotsSinceLastEye, eyes;
    // skyhanni QuiverApi: current arrow by internal name + how many of each
    public String currentArrow = "";
    public Map<String, Integer> arrows = new HashMap<>();

    public static CombatData get() {
        return Store.I.combat.computeIfAbsent(GameState.profileName, k -> new CombatData());
    }

    public static void changed() {
        Store.markDirty();
    }
}
