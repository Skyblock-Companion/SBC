// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.crimson;

import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.core.TabWidgets;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// small crimson helpers, from skyhanni PabloHelper (flower from sacks when pablo asks), SirihHelper (sulphur
// for barbarians), AvoriusHelper (cup of blood for mages), VolcanoExplosivityDisplay (VOLCANO tab widget)
public final class CrimsonHelpers {
    static final Patterns.P[] PABLO = {Patterns.p("SH.crimson.pablo.helper.0"), Patterns.p("SH.crimson.pablo.helper.1"), Patterns.p("SH.crimson.pablo.helper.2"), Patterns.p("SH.crimson.pablo.helper.3")};
    static final Patterns.P SIRIH = Patterns.p("SH.crimson.sirih.helper.colorless");
    static final Patterns.P[] AVORIUS = {Patterns.p("SH.crimson.avorius.helper.0"), Patterns.p("SH.crimson.avorius.helper.1"), Patterns.p("SH.crimson.avorius.helper.2")};
    private static final Pattern VOLCANO = Pattern.compile("Volcano: (?<status>\\S+)");

    static long lastPablo = CrimsonEnv.FAR_PAST, lastSirih = CrimsonEnv.FAR_PAST, lastAvorius = CrimsonEnv.FAR_PAST;
    static String volcano = "";

    public static boolean pabloHelper = true, sirihHelper = true, avoriusHelper = true;

    private CrimsonHelpers() {}

    // "Enchanted Dandelion" -> ENCHANTED_DANDELION, the enchanted flowers' ids are just their names
    static String flowerId(String name) {
        return name.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
    }

    private static boolean hasItem(List<CrimsonEnv.ItemCount> inv, String cleanNameContains, String internalName) {
        for (CrimsonEnv.ItemCount it : inv) {
            if (internalName != null && it.internalName().equals(internalName)) return true;
            if (cleanNameContains != null && it.cleanName().contains(cleanNameContains)) return true;
        }
        return false;
    }

    // clean line, the npc lines are matched without colours
    public static boolean onChat(String clean) {
        long now = CrimsonEnv.now();
        String t = clean.trim();
        if (pabloHelper && CrimsonEnv.world.inSkyBlock() && now - lastPablo >= 5 * 60_000) {
            for (Patterns.P p : PABLO) {
                Matcher m = p.matcher(t);
                if (!m.matches()) continue;
                String flower = m.group("flower");
                if (!hasItem(CrimsonEnv.world.lowerInventory(), flower, null))
                    CrimsonEnv.world.clickableChat("Click here to grab an " + flower + " from sacks!", "/gfs " + flowerId(flower) + " 1", "§eClick to get from sacks!");
                lastPablo = now;
                return true;
            }
        }
        if (!CrimsonEnv.inCrimson()) return false;
        if (sirihHelper && ReputationApi.factionType() == ReputationApi.FactionType.BARBARIAN && now - lastSirih >= 60_000 && SIRIH.matcher(t).matches()) {
            if (!hasItem(CrimsonEnv.world.lowerInventory(), null, "SULPHUR_ORE"))
                CrimsonEnv.world.clickableChat("Click here to grab Sulphur from sacks!", "/gfs SULPHUR_ORE 1", "§eClick to get from sacks!");
            lastSirih = now;
            return true;
        }
        if (avoriusHelper && ReputationApi.factionType() == ReputationApi.FactionType.MAGE && now - lastAvorius >= 15_000) {
            for (Patterns.P p : AVORIUS) {
                if (!p.matcher(t).matches()) continue;
                if (!hasItem(CrimsonEnv.world.lowerInventory(), null, "CUP_OF_BLOOD"))
                    CrimsonEnv.world.clickableChat("Click here to grab a Cup of Blood from sacks!", "/gfs CUP_OF_BLOOD 1", "§eClick to get from sacks!");
                lastAvorius = now;
                return true;
            }
        }
        return false;
    }

    // VolcanoExplosivityDisplay.onWidgetUpdate from the plain tab
    public static void onTab(List<String> tab) {
        volcano = "";
        for (TabWidgets.Widget w : TabWidgets.parse(tab)) {
            if (!w.def().id().equals("VOLCANO")) continue;
            Matcher m = VOLCANO.matcher(tab.get(w.first()).trim());
            if (m.find()) volcano = "§bVolcano Explosivity§7: " + m.group("status");
            return;
        }
    }

    public static String volcano() {
        return CrimsonEnv.inCrimson() ? volcano : "";
    }

    // just the state ("LOW"), empty if unknown
    public static String volcanoStatus() {
        String v = volcano();
        int i = v.lastIndexOf(": ");
        return i < 0 ? "" : v.substring(i + 2).replaceAll("§.", "");
    }
}
