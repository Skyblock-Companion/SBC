// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package dev.zmuzy.sbc.crimson;

import dev.zmuzy.sbc.core.Patterns;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;

// from skyhanni KuudraApi: kuudra tier from the sidebar and the KUUDRA DOWN! line
public final class KuudraApi {
    static final Patterns.P TIER = Patterns.p("SH.data.kuudra.scoreboard.tier");
    static final Patterns.P COMPLETE = Patterns.p("SH.data.kuudra.chat.complete");

    public static final List<String> TIERS = List.of("basic", "hot", "burning", "fiery", "infernal");

    static Integer kuudraTier;
    private static final List<IntConsumer> onComplete = new java.util.ArrayList<>();

    private KuudraApi() {}

    public static Integer kuudraTier() {
        return kuudraTier;
    }

    public static boolean inKuudra() {
        return CrimsonEnv.world.inSkyBlock() && kuudraTier != null;
    }

    public static void onKuudraComplete(IntConsumer l) {
        onComplete.add(l);
    }

    // onScoreboardChange, formatted sidebar lines
    public static void onScoreboard(List<String> formatted) {
        if (kuudraTier != null) return;
        for (String l : formatted) {
            Matcher m = TIER.matcher(l);
            if (m.matches()) {
                kuudraTier = Integer.parseInt(m.group("tier"));
                return;
            }
        }
    }

    public static void onWorldChange() {
        kuudraTier = null;
    }

    // formatted line
    public static boolean onChat(String formatted) {
        if (!COMPLETE.matcher(formatted).matches()) return false;
        Integer tier = kuudraTier;
        if (tier == null) return true;
        for (IntConsumer l : onComplete) l.accept(tier);
        return true;
    }

    public static String getKuudraRunTierName(int tier) {
        return TIERS.get(tier - 1);
    }
}
