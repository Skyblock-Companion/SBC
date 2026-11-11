// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mining;

import io.muzy.sbc.core.Patterns;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;

// from skyhanni MiningApi (area, cold, heat part): which mining area youre in, cold from the sidebar, heat in
// the hollows
// cold is absolute, reset when the line goes, on campfire or death, or when you leave a cold area
// ColdUpdateEvent listeners get every change. the ore mined detection is in OreMined, not here
public final class MiningApi {
    static final Patterns.P GLACITE_AREA = Patterns.p("SH.data.miningapi.area.glacite");
    static final Patterns.P BASE_CAMP = Patterns.p("SH.data.miningapi.area.basecamp");
    static final Patterns.P MINES_OF_DIVAN = Patterns.p("SH.data.miningapi.area.minesofdivan");
    static final Patterns.P ICY_BIOME = Patterns.p("SH.data.miningapi.area.icybiome");
    static final Patterns.P COLD_RESET = Patterns.p("SH.data.miningapi.cold.reset");
    static final Patterns.P HEAT = Patterns.p("SH.data.miningapi.heat.scoreboard");
    static final Patterns.P COLD = Patterns.p("SH.data.miningapi.cold");

    public static int cold, heat;
    public static String heatDisplay;
    public static long lastColdUpdate = MiningEnv.FAR_PAST, lastColdReset = MiningEnv.FAR_PAST, lastHeatUpdate = MiningEnv.FAR_PAST;
    private static final List<IntConsumer> COLD_LISTENERS = new ArrayList<>();

    private MiningApi() {}

    public static void onColdUpdate(IntConsumer l) {
        COLD_LISTENERS.add(l);
    }

    private static boolean island(String id) {
        return MiningEnv.world.island().equals(id);
    }

    public static boolean inGlaciteArea() {
        return inGlacialTunnels() || island("MINESHAFT");
    }

    public static boolean inDwarvenBaseCamp() {
        return island("DWARVEN_MINES") && BASE_CAMP.matcher(MiningEnv.world.area()).matches();
    }

    public static boolean inRegularDwarven() {
        return island("DWARVEN_MINES") && !inGlacialTunnels();
    }

    public static boolean inCrystalHollows() {
        return island("CRYSTAL_HOLLOWS");
    }

    public static boolean inMinesOfDivan() {
        return inCrystalHollows() && MINES_OF_DIVAN.matcher(MiningEnv.world.area()).matches();
    }

    public static boolean inMineshaft() {
        return island("MINESHAFT");
    }

    public static boolean inGlacialTunnels() {
        return island("DWARVEN_MINES") && GLACITE_AREA.matcher(MiningEnv.world.area()).matches();
    }

    // can cold hit the player rn (on the safari only in the icy biome)
    public static boolean inColdArea() {
        if (!MiningEnv.isColdIsland()) return false;
        if (island("SAFARI")) return ICY_BIOME.matcher(MiningEnv.world.area()).matches();
        return true;
    }

    // skyhanni ScoreboardUpdateEvent, formatted sidebar lines
    public static void onScoreboard(List<String> formatted) {
        if (MiningEnv.isColdIsland()) {
            boolean found = false;
            if (inColdArea()) {
                for (String l : formatted) {
                    Matcher m = COLD.matcher(l);
                    if (!m.matches()) continue;
                    found = true;
                    int newCold = Math.abs(Integer.parseInt(m.group("cold")));
                    if (newCold != cold) updateCold(newCold);
                    break;
                }
            }
            // reset cold when the line is gone, and also when the player left the cold area bc hypixel only drops the
            // line after a delay
            if (!found) resetCold();
        }
        if (inCrystalHollows()) {
            boolean found = false;
            for (String l : formatted) {
                Matcher m = HEAT.matcher(l);
                if (!m.matches()) continue;
                found = true;
                String newHeat = m.group("heat");
                heatDisplay = m.group("scoreboard").isEmpty() ? null : m.group("scoreboard");
                if (newHeat.equals("IMMUNE")) updateHeat(0);
                else if (Integer.parseInt(newHeat) != heat) updateHeat(Integer.parseInt(newHeat));
                break;
            }
            if (!found) {
                if (heat != 0) updateHeat(0);
                heatDisplay = null;
            }
        }
    }

    // skyhanni MiningApi.onChat, formatted line
    public static void onChat(String message) {
        if (MiningEnv.isColdIsland() && COLD_RESET.matcher(message).matches()) {
            updateCold(0);
            lastColdReset = MiningEnv.now();
        }
    }

    public static void onPlayerDeath() {
        updateCold(0);
        updateHeat(0);
        lastColdReset = MiningEnv.now();
        lastHeatUpdate = MiningEnv.now();
    }

    // skyhanni GraphAreaChangeEvent: reset cold right when the player leaves a cold area
    public static void onAreaChange() {
        if (!inColdArea()) resetCold();
    }

    public static void onWorldChange() {
        if (cold != 0) updateCold(0);
        lastColdReset = MiningEnv.now();
    }

    private static void resetCold() {
        if (cold == 0) return;
        updateCold(0);
        lastColdReset = MiningEnv.now();
    }

    private static void updateCold(int newCold) {
        // hypixel sends cold once more in the scoreboard even after resetting it
        if (cold == 0 && MiningEnv.passedSince(lastColdUpdate) < 1000) return;
        lastColdUpdate = MiningEnv.now();
        for (IntConsumer l : COLD_LISTENERS) {
            try {
                l.accept(newCold);
            } catch (Throwable t) {
                io.muzy.sbc.SbcMod.LOG.warn("cold listener failed", t);
            }
        }
        cold = newCold;
    }

    private static void updateHeat(int newHeat) {
        if (heat == 0 && MiningEnv.passedSince(lastHeatUpdate) < 1000) return;
        lastHeatUpdate = MiningEnv.now();
        heat = newHeat;
    }

    public static void reset() {
        cold = heat = 0;
        heatDisplay = null;
        lastColdUpdate = lastColdReset = lastHeatUpdate = MiningEnv.FAR_PAST;
    }

    static void clearListeners() {
        COLD_LISTENERS.clear();
    }
}
