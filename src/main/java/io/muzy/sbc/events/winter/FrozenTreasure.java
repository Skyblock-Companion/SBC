// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.winter;

import io.muzy.sbc.chat.ChatBus;
import io.muzy.sbc.combat.Combat;
import io.muzy.sbc.core.GameState;
import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.feature.Features;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// frozen treasure for the jerry's workshop profit card. from skyhanni FrozenTreasure + FrozenTreasureTracker:
// each treasure's base drop, its worth in ice, and ice per hour
public final class FrozenTreasure {
    // where the counts live on the card's session
    public static final String COUNT = "Treasure: ", MINED = "Frozen Treasures", COMPACT = "Compact";
    private static final String PROFIT = "winter_profit";

    public enum Type {
        WHITE_GIFT("whitegift", "§fWhite Gift", 1, 0), GREEN_GIFT("greengift", "§aGreen Gift", 1, 0), RED_GIFT("redgift", "§cRed Gift", 1, 0), PACKED_ICE("packedice", "§fPacked Ice", 32, 9), ENCHANTED_ICE("enchantedice", "§aEnchanted Ice", 9, 160),
        ENCHANTED_PACKED_ICE("enchantedpackedice", "§9Enchanted Packed Ice", 1, 25600), ICE_BAIT("icebait", "§aIce Bait", 16, 0), GLOWY_CHUM_BAIT("glowychumbait", "§aGlowy Chum Bait", 16, 0), GLACIAL_FRAGMENT("glacialfragment", "§5Glacial Fragment", 1, 0),
        GLACIAL_TALISMAN("glacialtalisman", "§fGlacial Talisman", 1, 0), FROZEN_BAIT("frozenbait", "§9Frozen Bait", 1, 0), EINARY_RED_HOODIE("einaryredhoodie", "§cEinary's Red Hoodie", 1, 0);

        public final String key, shown;
        public final int defaultAmount, iceMultiplier;

        Type(String key, String shown, int defaultAmount, int iceMultiplier) {
            this.key = key;
            this.shown = shown;
            this.defaultAmount = defaultAmount;
            this.iceMultiplier = iceMultiplier;
        }
    }

    private static final List<Long> perSecond = new ArrayList<>();
    private static long lastIce, lastSecond;
    private static int stoppedChecks, icePerHour;

    private FrozenTreasure() {}

    // treasure a clean chat line says you found, or null
    public static Type found(String clean) {
        if (!clean.startsWith("FROZEN TREASURE! You found ")) return null;
        for (Type t : Type.values()) if (Patterns.get("SH.event.jerry.frozentreasure.item." + t.key).matcher(clean).matches()) return t;
        return null;
    }

    public static boolean compact(String clean) {
        return Patterns.get("SH.event.jerry.frozentreasure.compact").matcher(clean).matches();
    }

    // calculateIce
    public static long ice(Map<String, Double> counts) {
        long ice = (long) (counts.getOrDefault(COMPACT, 0d) * 160);
        for (Type t : Type.values()) ice += (long) (counts.getOrDefault(COUNT + t.name(), 0d) * t.defaultAmount * t.iceMultiplier);
        return ice;
    }

    // onSecondPassed: over the last 20 min you were at it, a 1 min pause is forgiven
    static int step(long ice) {
        long difference = ice - lastIce;
        boolean first = difference == ice || difference < 0;
        lastIce = ice;
        if (first) return icePerHour;
        if (difference == 0) {
            if (perSecond.isEmpty()) return icePerHour;
            stoppedChecks++;
        } else {
            if (stoppedChecks > 60) {
                stoppedChecks = 0;
                perSecond.clear();
                icePerHour = 0;
            }
            while (stoppedChecks > 0) {
                stoppedChecks--;
                perSecond.add(0L);
            }
            perSecond.add(difference);
            while (perSecond.size() > 1200) perSecond.removeFirst();
        }
        if (!perSecond.isEmpty()) {
            long sum = 0;
            for (long d : perSecond) sum += d;
            icePerHour = (int) (sum / (double) perSecond.size() * 3600);
        }
        return icePerHour;
    }

    // the Ice/hr line, updated once a sec
    public static int icePerHour(long ice) {
        long now = System.currentTimeMillis();
        if (now - lastSecond >= 1000) {
            lastSecond = now;
            step(ice);
        }
        return icePerHour;
    }

    public static boolean showAsDrops() {
        return Combat.opt(PROFIT, "as_drops");
    }

    // onWorldChange
    public static void worldChanged() {
        lastIce = 0;
        icePerHour = 0;
        stoppedChecks = 0;
        perSecond.clear();
    }

    public static void register() {
        ChatBus.register(ChatBus.FILTER + 28, "frozen treasure messages", null, line -> {
            if (!"WINTER".equals(GameState.island) || !Features.on(PROFIT) || !Combat.opt(PROFIT, "hide_messages")) return ChatBus.PASS;
            String clean = GameState.strip(line.plain()).trim();
            return found(clean) != null || compact(clean) ? ChatBus.CANCEL : ChatBus.PASS;
        });
    }
}
