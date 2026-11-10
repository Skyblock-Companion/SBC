// SPDX-License-Identifier: GPL-3.0-only
package io.github.zmuzy.sbc.ui.hud;

import io.github.zmuzy.sbc.ui.HudLayout;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// which cards are one card: a host + the cards it carries under its lines, in order. each value shows once
// a member keeps its feature, settings and look, its just drawn inside the host
// and alone again when the host is off, hidden or has nothing to say
public final class HudMerges {
    // a "~" in front: says the same kind of thing at another time, never shows while the host does
    static final Map<String, List<String>> TABLE = new LinkedHashMap<>();

    static {
        // garden: farming card for the crop in hand, fortune + speed, then tool rows that only show when they have something
        // (hoe, mooshroom perk, lane, angles, mouse lock). all of it in one card is a tower, so crop progress is its own card
        // (milestones, best crop time, coins an hour, profit). yaw/pitch and mouse lock stay separate, they change
        // every head turn and would make the card jump
        TABLE.put("garden_farming", List.of("garden_true_fortune", "garden_speed", "garden_hoe_level", "garden_mushroom_perk", "garden_lanes"));
        TABLE.put("garden_crop_milestones", List.of("garden_best_crop_time", "garden_money_per_hour", "garden_profit"));
        // the garden band: your level, and under it what changes the garden for a while (stereo, filter, mantid, feast)
        TABLE.put("garden_level", List.of("garden_composter", "garden_stereo", "garden_atmospheric", "garden_mantid", "garden_feast"));
        TABLE.put("garden_pests", List.of("garden_pest_timer", "garden_spray", "pest_profit"));
        TABLE.put("garden_visitor_drops", List.of("garden_visitors"));
        TABLE.put("garden_contest", List.of("~garden_next_contest"));
    }

    // host -> member -> host rows that member says better, hidden while the member is drawn in it
    static final Map<String, Map<String, java.util.Set<String>>> COVERS = new LinkedHashMap<>();

    static {
        // the pest timer counts the cooldown from your kills, the spray tracker has every plot's spray + time left
        COVERS.put("garden_pests", Map.of("garden_pest_timer", java.util.Set.of("Cooldown"), "garden_spray", java.util.Set.of("Spray")));
    }

    private HudMerges() {}

    static Map<String, java.util.Set<String>> covers(String host) {
        return COVERS.getOrDefault(host, Map.of());
    }

    public static Map<String, List<String>> table() {
        return java.util.Collections.unmodifiableMap(TABLE);
    }

    // is it a host or a member
    public static boolean involves(String id) {
        if (TABLE.containsKey(id)) return true;
        for (List<String> ms : TABLE.values()) if (ms.contains(id) || ms.contains("~" + id)) return true;
        return false;
    }

    // after a card joins the hud every host grabs its members, in table order
    public static void link() {
        for (Map.Entry<String, List<String>> e : TABLE.entrySet()) {
            HudElement host = HudLayout.element(e.getKey());
            if (host == null) continue;
            host.clearMembers();
            for (String m : e.getValue()) {
                boolean alone = m.startsWith("~");
                HudElement card = HudLayout.element(alone ? m.substring(1) : m);
                if (card == null) continue;
                if (alone) host.absorbWhenAlone(card);
                else host.absorb(card);
            }
        }
    }
}
