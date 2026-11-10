// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.crimson;

import io.muzy.sbc.core.Patterns;
import io.muzy.sbc.events.diana.RareMobWaypointShare;
import io.muzy.sbc.render.WorldDraw;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;

// from skyhanni VanquisherWaypointShare: your own vanquisher gets announced (title + chat), key press or
// clicking the line shares it to party ("x: .., y: .., z: .. | Vanquisher")
// shared ones become 60s waypoints with a beam + line, "Vanquisher dead!" removes them, and we say it the
// same way once one you shared dies
// their instant share is left out, nothing sends without a press
public final class VanquisherWaypointShare {
    static final Patterns.P SHARED = Patterns.p("SH.vanquisher.waypoint.share.0");
    static final Patterns.P DIED = Patterns.p("SH.vanquisher.waypoint.died");

    public record SharedVanquisher(String playerName, Vec3 location, long spawnTime) {}

    static VanquisherApi.VanquisherData ownVanquisherData;
    static long lastShareTime = CrimsonEnv.FAR_PAST;
    static final Map<String, SharedVanquisher> sharedWaypoints = new LinkedHashMap<>();

    public static boolean readGlobalChat;
    // your vanquisher died after you shared it and party wasnt told yet
    static boolean deathToTell;
    // glue sets this to the key name for the "Click here or press X to share" line
    public static String keyName = "the share key";

    private VanquisherWaypointShare() {}

    static void init() {
        VanquisherApi.onSpawn.add(VanquisherWaypointShare::onVanquisherSpawn);
        VanquisherApi.onDeath.add(VanquisherWaypointShare::onVanquisherDeath);
        VanquisherApi.onDespawn.add(d -> {
            if (d == ownVanquisherData) ownVanquisherData = null;
        });
    }

    // key or clickable line
    public static void sendSpawn() {
        if (deathToTell) {
            deathToTell = false;
            send("Vanquisher dead!");
            return;
        }
        if (CrimsonEnv.now() - lastShareTime < 5_000) return;
        lastShareTime = CrimsonEnv.now();
        VanquisherApi.VanquisherData data = ownVanquisherData;
        if (data == null) return;
        Vec3 l = data.position;
        send("x: " + (int) l.x + ", y: " + (int) l.y + ", z: " + (int) l.z + " | Vanquisher");
    }

    private static void send(String message) {
        if (CrimsonEnv.world.inParty()) CrimsonEnv.world.sendCommand("pc " + message);
        else if (readGlobalChat) CrimsonEnv.world.sendCommand("ac " + message);
    }

    private static void onVanquisherSpawn(VanquisherApi.VanquisherData v) {
        if (!CrimsonEnv.inCrimson() || !v.isOwn) return;
        lastShareTime = CrimsonEnv.FAR_PAST;
        deathToTell = false;
        ownVanquisherData = v;
        CrimsonEnv.world.title("§5§lVanquisher Spawned!", "§r§7You found one nearby!");
        CrimsonEnv.world.chat("You Spawned a Vanquisher");
        String playerName = CrimsonEnv.world.playerName();
        sharedWaypoints.put(playerName, new SharedVanquisher(playerName, v.position, CrimsonEnv.now()));
        CrimsonEnv.world.clickableChat("You found a Vanquisher! Click here or press " + keyName + " to share!", "/sbc sharevanquisher", "Click to share!");
    }

    private static void onVanquisherDeath(VanquisherApi.VanquisherData v) {
        if (!CrimsonEnv.inCrimson() || !v.isOwn || lastShareTime == CrimsonEnv.FAR_PAST) return;
        if (CrimsonEnv.now() - lastShareTime < 2_000) return;
        deathToTell = true;
        CrimsonEnv.world.clickableChat("Your Vanquisher died. Click here or press " + keyName + " to tell the others!", "/sbc sharevanquisher", "Click to tell them");
    }

    public static void onSecondPassed() {
        long now = CrimsonEnv.now();
        sharedWaypoints.values().removeIf(w -> now - w.spawnTime() > 60_000);
    }

    // clean line, true = hide it (someone elses share)
    public static boolean onChat(String clean) {
        if (!CrimsonEnv.inCrimson()) return false;
        boolean block = handleShared(clean);
        handleDied(clean);
        return block;
    }

    private static boolean handleShared(String message) {
        Matcher m = SHARED.matcher(message);
        if (!m.matches()) return false;
        String channel = m.group("channel");
        boolean isGlobalChat = channel == null || channel.isEmpty();
        if (isGlobalChat && !readGlobalChat) return false;
        String rawName = m.group("playerName").trim();
        Vec3 location;
        try {
            location = new Vec3(Double.parseDouble(m.group("x")), Double.parseDouble(m.group("y")), Double.parseDouble(m.group("z")));
        } catch (NumberFormatException e) {
            return false;
        }
        String name = RareMobWaypointShare.cleanPlayerName(rawName);
        boolean playerIsYou = name.equalsIgnoreCase(CrimsonEnv.world.playerName());
        sharedWaypoints.put(name, new SharedVanquisher(name, location, CrimsonEnv.now()));
        if (!playerIsYou) {
            CrimsonEnv.world.chat(name + "§r found a Vanquisher at §b" + (int) location.x + " " + (int) location.y + " " + (int) location.z + "§r!");
            CrimsonEnv.world.title("§5§lVanquisher from " + name, "");
            return true;
        }
        return false;
    }

    private static void handleDied(String message) {
        Matcher m = DIED.matcher(message);
        if (!m.matches()) return;
        String channel = m.group("channel");
        if ((channel == null || channel.isEmpty()) && !readGlobalChat) return;
        sharedWaypoints.remove(RareMobWaypointShare.cleanPlayerName(m.group("playerName")));
    }

    public static Map<String, SharedVanquisher> shared() {
        return sharedWaypoints;
    }

    public static void render(WorldDraw w) {
        int beaconColor = 0xFFA025BF;
        long now = CrimsonEnv.now();
        for (SharedVanquisher wp : sharedWaypoints.values()) {
            if (now - wp.spawnTime() > 60_000) continue;
            Vec3 l = wp.location();
            w.waypointBox(l, beaconColor, true, true, 0);
            w.line(w.eye.add(w.mc.player.getLookAngle().scale(2)), l.add(0.5, 0.5, 0.5), beaconColor, 3f, true);
            w.label("§5" + wp.playerName() + "'s Vanquisher", l.add(0.5, 1.5, 0.5), true);
            w.labelBelow("§e" + Math.round(l.distanceTo(w.eye)) + " blocks", l.add(0.5, 1.5, 0.5));
        }
    }

    public static void onWorldChange() {
        sharedWaypoints.clear();
        ownVanquisherData = null;
        deathToTell = false;
    }
}
