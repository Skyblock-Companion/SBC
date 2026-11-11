// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.rift;

import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;

// port of skyhanni rift/area/westvillage/VerminTracker.kt: vacuumed vermin from chat, totals from the vermin
// bin (slot 13 lore) + the turbomax vacuums vacuumed_* attributes
public final class VerminTracker {
    public enum VerminType {
        FLY(1, "§aFlies", "SH.rift.area.westvillage.vermintracker.fly", "vacuumed_mosquito"),
        SPIDER(2, "§aSpiders", "SH.rift.area.westvillage.vermintracker.spider", "vacuumed_spider"),
        SILVERFISH(3, "§aSilverfish", "SH.rift.area.westvillage.vermintracker.silverfish", "vacuumed_silverfish");

        public final int order;
        public final String vermin, attribute;
        public final Patterns.P pattern;

        VerminType(int order, String vermin, String key, String attribute) {
            this.order = order;
            this.vermin = vermin;
            this.pattern = Patterns.p(key);
            this.attribute = attribute;
        }
    }

    static final Patterns.P BIN = Patterns.p("SH.rift.area.westvillage.vermintracker.binline-nocolor");
    public static final String TURBOMAX_VACUUM = "TURBOMAX_VACUUM";

    // session counts (skyhanni tracker session mode) + the total (bin + bag)
    static final Map<VerminType, Integer> session = new java.util.EnumMap<>(VerminType.class);
    static final Map<VerminType, Integer> total = new java.util.EnumMap<>(VerminType.class);
    static boolean hasVacuum;

    public static boolean hideChat, showOutsideWestVillage, showWithoutVacuum;

    private VerminTracker() {}

    public static void reset() {
        session.clear();
    }

    public static void setHasVacuum(boolean v) {
        hasVacuum = v;
    }

    // from skyhanni onChat with the formatted line. true = vacuum message (hidden when hideChat)
    public static boolean onChat(String formatted) {
        for (VerminType t : VerminType.values())
            if (t.pattern.matcher(formatted).matches()) {
                session.merge(t, 1, Integer::sum);
                total.merge(t, 1, Integer::sum);
                return true;
            }
        return false;
    }

    static VerminType getVerminType(String vermin) {
        return switch (vermin) {
            case "spider", "spiders" -> VerminType.SPIDER;
            case "fly", "flies" -> VerminType.FLY;
            default -> VerminType.SILVERFISH;
        };
    }

    // from skyhanni onInventoryFullyOpened("Vermin Bin"): slot 13 lore + the vacuums vacuumed_* attributes
    public static void onVerminBin(List<String> binLore, Map<String, Integer> bagAttributes) {
        for (String l : binLore) {
            Matcher m = BIN.matcher(GameState.strip(l));
            if (!m.matches()) continue;
            total.put(getVerminType(m.group("vermin").toLowerCase(Locale.ROOT)), Integer.parseInt(m.group("count").replace(",", "")));
        }
        if (bagAttributes == null) return;
        for (VerminType t : VerminType.values()) total.merge(t, bagAttributes.getOrDefault(t.attribute, 0), Integer::sum);
    }

    public static List<String> display(boolean totals) {
        List<String> out = new ArrayList<>();
        out.add("§7Vermin Tracker:");
        Map<VerminType, Integer> data = totals ? total : session;
        for (VerminType t : VerminType.values()) {
            Integer n = data.get(t);
            if (n == null) continue;
            out.add(" §7- §e" + String.format(Locale.ROOT, "%,d", n) + " " + t.vermin);
        }
        return out;
    }

    public static Map<VerminType, Integer> session() {
        return session;
    }

    public static boolean shouldShowDisplay() {
        if (!RiftApi.inRift()) return false;
        if (!showOutsideWestVillage && !RiftApi.inWestVillage()) return false;
        if (!showWithoutVacuum && !hasVacuum) return false;
        return true;
    }
}
