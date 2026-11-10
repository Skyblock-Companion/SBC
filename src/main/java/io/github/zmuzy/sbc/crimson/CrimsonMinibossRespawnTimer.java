// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.crimson;

import io.github.zmuzy.sbc.core.GameState;
import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.events.hoppity.HoppityEnv;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

// based on skyhanni CrimsonMinibossRespawnTimer: each area's next spawn from the DOWN! line (2 min), the
// BEWARE spawn line, the boss being in its area, and the beacon that stands for the first minute after a kill
// one difference on purpose bc the original shows Unknown like all the time: skyhanni wipes a boss every
// second unless you stood in its area in the last 2 min, and only looks at your own area. so a DOWN! line
// heard just outside is gone a sec later
// here nothing is wiped until its not true anymore, and every area is watched as far as the client has it
// loaded (name tag or beacon counts from any distance)
// conclusions from something NOT being there still need you inside the area, server only sends mobs that are
// close. a boss never in reach and never in chat stays unknown, hypixel sends nothing about it
public final class CrimsonMinibossRespawnTimer {
    static final Patterns.P SPAWN = Patterns.p("SH.crimson.miniboss.spawn");
    static final Patterns.P DOWN = Patterns.p("SH.crimson.miniboss.down");

    private static final long SEEN_NOW = 3_000, EXPIRED = 30_000;

    public enum MiniBoss {
        BLADESOUL("Bladesoul", new Vec3(-330, 80, -486), new Vec3(-257, 107, -545)),
        MAGE_OUTLAW("Mage Outlaw", new Vec3(-200, 98, -843), new Vec3(-162, 116, -878)),
        BARBARIAN_DUKE_X("Barbarian Duke X", new Vec3(-550, 101, -890), new Vec3(-522, 131, -918)),
        ASHFANG("Ashfang", new Vec3(-462, 155, -1035), new Vec3(-507, 131, -955)),
        MAGMA_BOSS("Magma Boss", new Vec3(-318, 59, -751), new Vec3(-442, 90, -851));

        public final String displayName;
        public final AABB area;
        public Long nextSpawnTime;
        public long[] possibleSpawnTime;
        public Boolean foundBeacon, spawned;
        public long lastSeenArea = CrimsonEnv.FAR_PAST;
        // when the boss was last known up (name tag or BEWARE line) and when its name tag was last seen
        public long spawnedAt = CrimsonEnv.FAR_PAST, seenAt = CrimsonEnv.FAR_PAST;
        public int seenDistance;

        MiniBoss(String displayName, Vec3 a, Vec3 b) {
            this.displayName = displayName;
            this.area = new AABB(a, b);
        }

        static MiniBoss fromName(String spawnName) {
            for (MiniBoss m : values()) if (m.displayName.equalsIgnoreCase(GameState.strip(spawnName))) return m;
            return null;
        }

        boolean isTimerKnown() {
            if (nextSpawnTime == null) return false;
            return CrimsonEnv.now() - nextSpawnTime < 2 * 60_000 + 5_000;
        }

        boolean isSpawned() {
            return Boolean.TRUE.equals(spawned);
        }

        boolean onMapNow() {
            return CrimsonEnv.now() - seenAt <= SEEN_NOW;
        }

        void clear() {
            nextSpawnTime = null;
            possibleSpawnTime = null;
            foundBeacon = null;
            spawned = null;
            spawnedAt = CrimsonEnv.FAR_PAST;
            seenAt = CrimsonEnv.FAR_PAST;
        }
    }

    // what the client can tell about an area rn
    public interface Scan {
        // blocks to a mob named like the boss inside its area, -1 if none
        int bossDistance(MiniBoss boss);

        boolean beacon(MiniBoss boss);

        // whole area loaded, so no beacon found = there is none
        boolean areaLoaded(MiniBoss boss);
    }

    static MiniBoss currentAreaBoss;

    private CrimsonMinibossRespawnTimer() {}

    // formatted line, true if it was a miniboss line
    public static boolean onChat(String formatted) {
        Matcher m = DOWN.matcher(formatted);
        if (m.matches()) {
            MiniBoss boss = MiniBoss.fromName(m.group("name"));
            if (boss == null) return true;
            boss.nextSpawnTime = CrimsonEnv.now() + 2 * 60_000;
            boss.spawned = false;
            boss.seenAt = CrimsonEnv.FAR_PAST;
            boss.possibleSpawnTime = null;
            boss.foundBeacon = null;
            return true;
        }
        m = SPAWN.matcher(formatted);
        if (m.matches()) {
            MiniBoss boss = MiniBoss.fromName(m.group("name"));
            if (boss == null) return true;
            boss.spawned = true;
            boss.spawnedAt = CrimsonEnv.now();
            boss.nextSpawnTime = null;
            boss.possibleSpawnTime = null;
            boss.foundBeacon = null;
            return true;
        }
        return false;
    }

    public static void onSecondPassed(Scan scan) {
        long now = CrimsonEnv.now();
        Vec3 p = CrimsonEnv.world.playerPos();
        currentAreaBoss = null;
        for (MiniBoss b : MiniBoss.values()) if (b.area.contains(p)) {
            currentAreaBoss = b;
            break;
        }
        if (currentAreaBoss != null) currentAreaBoss.lastSeenArea = now;
        for (MiniBoss b : MiniBoss.values()) observe(b, scan, now, b == currentAreaBoss);
    }

    private static void observe(MiniBoss boss, Scan scan, long now, boolean inside) {
        int distance = scan.bossDistance(boss);
        if (distance >= 0) {
            boss.spawned = true;
            boss.spawnedAt = now;
            boss.seenAt = now;
            boss.seenDistance = distance;
            boss.nextSpawnTime = null;
            boss.foundBeacon = null;
            boss.possibleSpawnTime = null;
            return;
        }
        if (boss.isTimerKnown()) return;
        boolean beacon = scan.beacon(boss);
        // outside the area only a beacon tells us anything: it showing up, or (chunks loaded) it being gone
        if (!inside && !beacon && !(Boolean.TRUE.equals(boss.foundBeacon) && scan.areaLoaded(boss))) return;
        if (inside) boss.nextSpawnTime = null; // its time passed long ago and the boss isnt here
        boss.spawned = false;
        if (Boolean.TRUE.equals(boss.foundBeacon) && !beacon) {
            boss.foundBeacon = false;
            boss.possibleSpawnTime = null;
            boss.nextSpawnTime = now + 60_000;
            return;
        }
        if (beacon && Boolean.FALSE.equals(boss.foundBeacon)) boss.possibleSpawnTime = null; // killed since the last look
        if (boss.possibleSpawnTime != null) {
            if (!inside || now - boss.possibleSpawnTime[1] <= EXPIRED) return;
            boss.possibleSpawnTime = null; // standing here way past the window and nothing came, look again
            boss.foundBeacon = null;
        }
        if (beacon) {
            boss.foundBeacon = true;
            boss.possibleSpawnTime = new long[]{now + 60_000, now + 2 * 60_000};
            return;
        }
        if (boss.foundBeacon == null) {
            boss.foundBeacon = false;
            boss.possibleSpawnTime = new long[]{now, now + 60_000};
        }
    }

    // which kind of answer, for the card colours
    public enum Kind {
        ON_MAP, SPAWNED, COUNTDOWN, SOON, PROBABLY_UP, WINDOW, UNKNOWN
    }

    // status in parts: shown state, extra (distance, time since), how much of the 2 min respawn surely passed (-1
    // = says nothing), ms left of a countdown (-1 none), and the whole thing as one line
    public record Status(Kind kind, String state, String detail, double progress, long left, String text) {}

    private static double respawned(long left) {
        return Math.max(0, Math.min(1, 1 - left / (2 * 60_000.0)));
    }

    public static Status statusOf(MiniBoss it, long now) {
        if (it.onMapNow()) return new Status(Kind.ON_MAP, "On the map", it.seenDistance + "m", -1, -1, "§aOn the map §7(" + it.seenDistance + "m)");
        if (it.isSpawned()) {
            long ago = now - it.spawnedAt;
            if (ago < 10_000) return new Status(Kind.SPAWNED, "SPAWNED!", "", -1, -1, "§aSPAWNED!");
            String since = HoppityEnv.format(ago);
            return new Status(Kind.SPAWNED, "SPAWNED!", since + " ago", -1, -1, "§aSPAWNED! §7(" + since + " ago)");
        }
        if (it.nextSpawnTime != null) {
            long left = it.nextSpawnTime - now;
            if (left > 0) return new Status(Kind.COUNTDOWN, HoppityEnv.format(left), "", respawned(left), left, "§e" + HoppityEnv.format(left));
            if (-left <= 10_000 || (it == currentAreaBoss && it.isTimerKnown())) return new Status(Kind.SOON, "Soon!", "", -1, -1, "§6Soon!");
            String since = HoppityEnv.format(-left);
            return new Status(Kind.PROBABLY_UP, "Probably up", since + " ago", -1, -1, "§eProbably up §7(" + since + ")");
        }
        if (it.possibleSpawnTime != null) {
            long start = it.possibleSpawnTime[0], end = it.possibleSpawnTime[1];
            if (end - now < 0 && it != currentAreaBoss) {
                String since = HoppityEnv.format(now - end);
                return new Status(Kind.PROBABLY_UP, "Probably up", since + " ago", -1, -1, "§eProbably up §7(" + since + ")");
            }
            String from = start - now < 0 ? "Now" : HoppityEnv.format(start - now), to = end - now < 0 ? "Now" : HoppityEnv.format(end - now);
            return new Status(Kind.WINDOW, "~" + from + " - " + to, "", respawned(end - now), -1, "§e~" + from + " - §e" + to);
        }
        return new Status(Kind.UNKNOWN, "Not seen yet", "", -1, -1, "§7Not seen yet");
    }

    public static List<String> display() {
        List<String> lines = new ArrayList<>();
        long now = CrimsonEnv.now();
        for (MiniBoss it : MiniBoss.values()) lines.add("§b" + it.displayName + ": " + statusOf(it, now).text());
        return lines;
    }

    // raw state of every boss, for the dump
    public static List<String> debug() {
        List<String> lines = new ArrayList<>();
        long now = CrimsonEnv.now();
        lines.add("current area: " + currentAreaBoss);
        for (MiniBoss b : MiniBoss.values())
            lines.add(b.displayName + " | next " + (b.nextSpawnTime == null ? "-" : (b.nextSpawnTime - now) / 1000 + "s")
                    + " | window " + (b.possibleSpawnTime == null ? "-" : (b.possibleSpawnTime[0] - now) / 1000 + "s.." + (b.possibleSpawnTime[1] - now) / 1000 + "s")
                    + " | beacon " + b.foundBeacon + " | spawned " + b.spawned
                    + " | seen " + (b.seenAt == CrimsonEnv.FAR_PAST ? "never" : (now - b.seenAt) / 1000 + "s ago at " + b.seenDistance + "m")
                    + " | in area " + (b.lastSeenArea == CrimsonEnv.FAR_PAST ? "never" : (now - b.lastSeenArea) / 1000 + "s ago"));
        return lines;
    }

    public static void onWorldChange() {
        for (MiniBoss b : MiniBoss.values()) {
            b.clear();
            b.lastSeenArea = CrimsonEnv.FAR_PAST;
        }
        currentAreaBoss = null;
    }
}
