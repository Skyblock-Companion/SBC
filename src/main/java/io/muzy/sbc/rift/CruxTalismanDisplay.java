// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.rift;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// port of skyhanni rift/everywhere/CruxTalismanDisplay.kt: the crux talisman lore (items starting with
// CRUX_TALISMAN) read every 2 sec into a progress list + the total bonuses
public final class CruxTalismanDisplay {
    static final Patterns.P PROGRESS = Patterns.p("SH.rift.everywhere.crux.progress");
    private static final Pattern PROGRESS_NUMBER = Pattern.compile("(?<progress>\\d+)/\\d+");

    public record Crux(String name, String tier, String progress, boolean maxed) {}

    static final List<Crux> cruxes = new ArrayList<>();
    static final List<String> bonusesLine = new ArrayList<>();
    static boolean maxed;
    static double percentValue;
    static List<String> display = List.of();

    public static boolean compactWhenMaxed = true, showBonuses = true;

    private CruxTalismanDisplay() {}

    // from skyhanni onSecondPassed (every 2 sec): formatted lore of every inventory item. true = saw a talisman
    public static boolean read(List<List<String>> inventoryLore) {
        cruxes.clear();
        bonusesLine.clear();
        maxed = false;
        boolean bonusFound = false, any = false;
        for (List<String> lore : inventoryLore) {
            for (String line : lore) {
                Matcher m = PROGRESS.matcher(line);
                if (m.matches()) {
                    any = true;
                    String tier = m.group("tier").replace("-", "0");
                    String progress = m.group("progress");
                    cruxes.add(new Crux(m.group("name"), tier, progress, progress.contains("MAXED")));
                }
                if (line.startsWith("§7Total Bonuses")) {
                    bonusFound = true;
                    continue;
                }
                if (bonusFound) {
                    if (line.isEmpty()) {
                        bonusFound = false;
                        continue;
                    }
                    bonusesLine.add(line);
                }
            }
        }
        maxed = !cruxes.isEmpty() && cruxes.stream().allMatch(Crux::maxed);
        display = drawDisplay();
        return any;
    }

    static List<String> drawDisplay() {
        List<String> out = new ArrayList<>();
        boolean showAsMaxed = maxed && compactWhenMaxed;
        int percent = 0;
        if (!cruxes.isEmpty()) {
            out.add("§7Crux Talisman Progress: " + (showAsMaxed ? "§a§lMAXED!" : "§a" + percentValue + "%"));
            if (!showAsMaxed) {
                for (Crux line : cruxes) {
                    if (line.progress().contains("MAXED")) percent += 100;
                    else {
                        Matcher m = PROGRESS_NUMBER.matcher(GameState.strip(line.progress()));
                        percent += m.find() ? Integer.parseInt(m.group("progress")) : 0;
                    }
                    out.add("  " + line.tier() + " " + line.name() + ": " + line.progress());
                }
            }
        }
        int totalPercentage = cruxes.size() * 100;
        percentValue = totalPercentage == 0 ? 0 : Math.round(percent * 100.0 / totalPercentage * 10) / 10.0;
        if (!bonusesLine.isEmpty() && showBonuses) {
            out.add("§7Bonuses:");
            for (String b : bonusesLine) out.add("  " + b);
        }
        return out;
    }

    public static List<String> display() {
        if (!isEnabled()) return List.of();
        return display;
    }

    public static boolean isEnabled() {
        return RiftApi.inRift() && !RiftApi.inMirrorVerse();
    }
}
