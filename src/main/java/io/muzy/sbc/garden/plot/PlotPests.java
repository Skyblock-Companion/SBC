// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.garden.plot;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.garden.GardenExtras;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;

// pests per plot, skyhanni PestApi with GardenPlot.pests + isPestCountInaccurate: from spawn lines, sidebar,
// the tab's Plots line and Configure Plots
// tab only says which plots have pests, so a plot can be known to have some without a count
public final class PlotPests {
    private static final Map<Integer, Integer> PESTS = new HashMap<>();
    // isPestCountInaccurate
    private static final Set<Integer> UNSURE = new HashSet<>();
    // scoreboardPests
    private static int total;

    private PlotPests() {}

    // resetAllPests. leaving the garden isnt it, counts stay true while you're away
    public static void reset() {
        PESTS.clear();
        UNSURE.clear();
        total = 0;
    }

    // pests on the plot, -1 if unknown or none
    public static int known(int plot) {
        Integer n = PESTS.get(plot);
        return n == null || n <= 0 || UNSURE.contains(plot) ? -1 : n;
    }

    // getNearestInfestedPlot's ignoreSinglePestPlots
    public static boolean single(int plot) {
        return known(plot) == 1;
    }

    // onPestSpawn: amount null for the line that names plots without a number
    public static void spawned(Integer amount, String plots) {
        for (String name : plots.split(",")) {
            int id = GardenPlotInfo.byName(name.trim());
            if (id < 0) continue;
            if (amount == null) UNSURE.add(id);
            else {
                PESTS.merge(id, amount, Integer::sum);
                UNSURE.remove(id);
            }
        }
        if (amount != null) total += amount;
        fix();
    }

    // removeNearestPest, for a pest you killed
    public static void killed() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) return;
        int nearest = -1;
        double best = Double.MAX_VALUE;
        for (int id : infested()) {
            Vec3 c = GardenExtras.plotCenter(id);
            if (c == null) continue;
            double d = c.distanceToSqr(mc.player.position());
            if (d < best) {
                best = d;
                nearest = id;
            }
        }
        if (nearest < 0) return;
        if (!UNSURE.contains(nearest)) PESTS.merge(nearest, -1, Integer::sum);
        total = Math.max(0, total - 1);
        fix();
    }

    private static List<Integer> infested() {
        List<Integer> out = new ArrayList<>(UNSURE);
        for (var e : PESTS.entrySet()) if (e.getValue() > 0 && !UNSURE.contains(e.getKey())) out.add(e.getKey());
        return out;
    }

    // fixPests
    static void fix() {
        int accurate = 0;
        for (var e : PESTS.entrySet()) if (e.getValue() > 0 && !UNSURE.contains(e.getKey())) accurate += e.getValue();
        int unsure = UNSURE.size();
        if (unsure == 0) return;
        if (total == accurate + unsure) {
            for (int id : UNSURE) PESTS.put(id, 1);
            UNSURE.clear();
        } else if (unsure == 1) {
            int id = UNSURE.iterator().next();
            if (total > accurate) {
                PESTS.put(id, total - accurate);
                UNSURE.clear();
            }
        } else if (accurate + unsure > total) {
            for (int id : infested()) {
                PESTS.put(id, 0);
                UNSURE.add(id);
            }
        }
    }

    // onInventoryFullyOpened: Configure Plots says it for every plot you can see
    static void fromMenu(int plot, List<String> lore, boolean skip) {
        if (plot == 0 || skip) return;
        int n = 0;
        for (String line : lore) {
            Matcher m = Patterns.get("SH.garden.pests-api.inventory").matcher(line);
            if (m.matches()) {
                n = Integer.parseInt(m.group("amount"));
                break;
            }
        }
        PESTS.put(plot, n);
        UNSURE.remove(plot);
    }

    // onWidgetUpdate: what the tab says is infested
    static void fromTab(List<Integer> tabPlots) {
        Set<Integer> now = new HashSet<>(tabPlots), mine = new HashSet<>(infested());
        if (now.equals(mine)) return;
        for (int id : mine) {
            if (!now.contains(id)) {
                PESTS.remove(id);
                UNSURE.remove(id);
            }
        }
        for (int id : now) if (PESTS.getOrDefault(id, 0) <= 0) UNSURE.add(id);
        fix();
    }

    // checkScoreboardLines
    static void fromSidebar(List<String> lines) {
        for (String line : lines) {
            Matcher m = Patterns.get("SH.garden.pests-api.scoreboard.pests").matcher(line);
            if (m.matches()) {
                try {
                    total = Integer.parseInt(m.group("pests").trim());
                    fix();
                } catch (NumberFormatException ignored) {
                }
            }
            if (Patterns.get("SH.garden.pests-api.scoreboard.no-pests").matcher(line).matches()) total = 0;
            m = Patterns.get("SH.garden.pests-api.scoreboard.plot.pests").matcher(line);
            if (m.matches()) {
                int id = GardenPlotInfo.byName(m.group("plot"));
                if (id >= 0) {
                    PESTS.put(id, Integer.parseInt(m.group("pests")));
                    UNSURE.remove(id);
                    fix();
                }
                continue;
            }
            m = Patterns.get("SH.garden.pests-api.scoreboard.plot.no-pests").matcher(line);
            if (m.matches()) {
                int id = GardenPlotInfo.byName(m.group("plot"));
                if (id >= 0) {
                    PESTS.remove(id);
                    UNSURE.remove(id);
                    fix();
                }
            }
        }
    }

    static void second() {
        fromSidebar(GameState.scoreboardFormatted);
        fromTab(GardenExtras.infestedPlots());
    }
}
