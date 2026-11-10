// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.rift;

import io.github.zmuzy.sbc.core.Patterns;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;

// port of skyhanni rift/everywhere/PunchcardHighlight.kt: players punched this rift visit (max 20) from the
// PUNCHCARD!/AWKWARD!/UH OH! lines after clicking a player
public final class PunchcardHighlight {
    static final Patterns.P PUNCHED = Patterns.p("SH.rift.punchcard.new");
    static final Patterns.P REPEAT = Patterns.p("SH.rift.punchcard.repeat");
    static final Patterns.P LIMIT = Patterns.p("SH.rift.punchcard.limit");
    public static final String PUNCHCARD_ARTIFACT = "PUNCHCARD_ARTIFACT";

    static final Set<String> playerList = new LinkedHashSet<>();
    static final List<String> playerQueue = new ArrayList<>();
    static long queuedAt = RiftEnv.FAR_PAST;
    static String lastRiftServer = "";

    public static boolean reverse, compact, reverseGUI;

    private PunchcardHighlight() {}

    // from skyhanni onPunch: clicked a non npc player
    public static void onPunch(String name) {
        if (!RiftApi.inRift()) return;
        if (playerList.contains(name) || playerQueue.contains(name)) return;
        playerQueue.add(name);
        queuedAt = RiftEnv.now();
    }

    // from skyhanni DelayedRun 1 sec: queue gets dropped if no chat line confirmed the punch
    public static void tick() {
        if (!playerQueue.isEmpty() && RiftEnv.now() - queuedAt > 1000) playerQueue.clear();
    }

    // from skyhanni onChat with the formatted line. true = punchcard line
    public static boolean onChat(String formatted) {
        if (!RiftApi.inRift()) return false;
        if (playerQueue.isEmpty() || RiftEnv.now() - queuedAt > 1000) return false;
        String queuedName = playerQueue.get(0);
        Matcher m = PUNCHED.matcher(formatted);
        if (m.matches()) {
            String name = m.group("name");
            if (queuedName.equals(name)) addPunch(name);
            return true;
        }
        if (LIMIT.matcher(formatted).matches() || REPEAT.matcher(formatted).matches()) {
            addPunch(queuedName);
            return true;
        }
        return false;
    }

    private static void addPunch(String playerName) {
        playerList.add(playerName);
        playerQueue.remove(playerName);
    }

    // from skyhanni onIslandChange: new rift server clears the list
    public static void onServerChange(String server) {
        if (server != null && !server.isEmpty() && !lastRiftServer.equals(server)) {
            lastRiftServer = server;
            playerList.clear();
        }
    }

    // highlight this player? (not punched yet, or punched with reverse on)
    public static boolean highlight(String name) {
        if (playerList.size() >= 20 && !reverse) return false;
        boolean punched = playerList.contains(name);
        return reverse == punched;
    }

    public static int count() {
        return playerList.size();
    }

    // last punched player, "" = none yet
    static String lastPunched() {
        String last = "";
        for (String p : playerList) last = p;
        return last;
    }

    public static String display() {
        String s = compact ? "" : "Punchcard Artifact: ";
        return s + "§d" + (reverseGUI ? 20 - playerList.size() : playerList.size());
    }

    public static void reset() {
        playerList.clear();
        playerQueue.clear();
    }
}
