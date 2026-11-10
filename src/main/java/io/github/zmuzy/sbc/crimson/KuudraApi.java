// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.crimson;

import io.github.zmuzy.sbc.core.Patterns;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;

// from skyhanni KuudraApi: kuudra tier from the sidebar and the KUUDRA DOWN! line
public final class KuudraApi {
    static final Patterns.P TIER = Patterns.p("SH.data.kuudra.scoreboard.tier");
    static final Patterns.P COMPLETE = Patterns.p("SH.data.kuudra.chat.complete");
    // the party entering says the tier too, so a changed sidebar line still leaves us the tier
    static final java.util.regex.Pattern ENTERED = java.util.regex.Pattern.compile("^(?:\\[[^\\]]+\\] )?\\w+ entered Kuudra's Hollow, (Basic|Hot|Burning|Fiery|Infernal) Tier!$");

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
        Matcher entered = ENTERED.matcher(io.github.zmuzy.sbc.core.GameState.strip(formatted).trim());
        if (entered.matches()) {
            kuudraTier = TIERS.indexOf(entered.group(1).toLowerCase(java.util.Locale.ROOT)) + 1;
            return false;
        }
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
