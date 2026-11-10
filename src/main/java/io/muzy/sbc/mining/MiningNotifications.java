// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.mining;

import io.muzy.sbc.core.Patterns;

// from skyhanni MiningNotifications: short title (+ pling) for a mineshaft portal, suspicious scrap, golden
// or diamond goblin, and cold hitting the threshold
public final class MiningNotifications {
    static final Patterns.P MINESHAFT_SPAWN = Patterns.p("SH.mining.notifications.mineshaft.spawn");
    static final Patterns.P SCRAP = Patterns.p("SH.mining.notifications.scrapdrop");
    static final Patterns.P GOLDEN_GOBLIN = Patterns.p("SH.mining.notifications.goblin.goldspawn");
    static final Patterns.P DIAMOND_GOBLIN = Patterns.p("SH.mining.notifications.goblin.diamondspawn");

    private static boolean hasSentCold, hasSentRope;
    public static String lastNotification = "";

    private MiningNotifications() {}

    // formatted chat line on mining islands
    public static void onChat(String message) {
        if (!MiningEnv.isMiningIsland()) return;
        if (!Mining.b("mining_notifications", "on", true)) return;
        if (MINESHAFT_SPAWN.matcher(message).matches()) send("§bMineshaft", "mineshaft");
        else if (SCRAP.matcher(message).matches()) send("§9Suspicious Scrap", "scrap");
        else if (GOLDEN_GOBLIN.matcher(message).matches()) send("§6Golden Goblin", "goldenGoblin");
        else if (DIAMOND_GOBLIN.matcher(message).matches()) send("§bDiamond Goblin", "diamondGoblin");
    }

    // from skyhanni ColdUpdateEvent
    public static void onColdUpdate(int cold) {
        if (!MiningApi.inGlaciteArea()) return;
        if (!Mining.b("mining_notifications", "on", true)) return;
        if (MiningEnv.passedSince(MiningApi.lastColdReset) < 1000) return;
        int threshold = (int) Mining.n("mining_notifications", "coldThreshold", 100);
        if (cold >= threshold && !hasSentCold) {
            hasSentCold = true;
            send("§bCold", "cold");
        }
        if (cold < threshold) hasSentCold = false;
        if (MiningApi.inMineshaft() && Mining.b("mining_notifications", "getAscensionRope", true) && cold >= (int) Mining.n("mining_notifications", "coldAmount", 90) && !hasSentRope) {
            hasSentRope = true;
            MiningEnv.world.clickableChat("§e§lCLICK HERE§r§e to grab §ax1 §9Ascension Rope§e from sacks!", "/gfs ASCENSION_ROPE 1", "§eClick to get from sacks!");
        }
    }

    public static void onWorldChange() {
        hasSentCold = false;
        hasSentRope = false;
    }

    private static void send(String text, String key) {
        if (!Mining.b("mining_notifications", key, true)) return;
        lastNotification = text;
        MiningEnv.world.title(text);
        if (Mining.b("mining_notifications", "sound", true)) MiningEnv.world.playSound("block.note_block.pling", 1f);
    }

    public static void reset() {
        hasSentCold = false;
        lastNotification = "";
    }
}
