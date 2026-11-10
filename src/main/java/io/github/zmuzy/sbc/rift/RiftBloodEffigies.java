// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.github.zmuzy.sbc.rift;

import io.github.zmuzy.sbc.core.Patterns;
import io.github.zmuzy.sbc.events.hoppity.HoppityEnv;
import io.github.zmuzy.sbc.render.WorldDraw;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.regex.Matcher;

// port of skyhanni rift/stillgorechateau/RiftBloodEffigies.kt: the 6 effigies from the "Effigies:" sidebar
// line (§7 = standing, §c = broken) + respawn time from the stands nearby
public final class RiftBloodEffigies {
    public enum EffigyState { UNKNOWN, NOT_BROKEN, BROKEN }

    public static final class Effigy {
        public EffigyState state = EffigyState.UNKNOWN;
        public long respawnTime = RiftEnv.FAR_PAST;

        void reset() {
            state = EffigyState.UNKNOWN;
            respawnTime = RiftEnv.FAR_PAST;
        }
    }

    static final Patterns.P TIMER = Patterns.p("SH.rift.area.stillgore.effegies.respawn");
    static final Patterns.P BREAK = Patterns.p("SH.rift.area.stillgore.effegies.break");
    static final Patterns.P HEARTS = Patterns.p("SH.rift.area.stillgore.effegies.heart");

    static final Effigy[] effigies = {new Effigy(), new Effigy(), new Effigy(), new Effigy(), new Effigy(), new Effigy()};
    private static String lastLine = "";

    // skyhanni config unknownTime / respawningSoon / respawningSoonTime (min)
    public static boolean unknownTime = true, respawningSoon = true;
    public static int respawningSoonTime = 3;

    private RiftBloodEffigies() {}

    public static void onWorldChange() {
        for (Effigy e : effigies) e.reset();
        lastLine = "";
    }

    static int getIndex(Vec3 entityPos) {
        List<Vec3> locations = RiftData.effigies;
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (int i = 0; i < locations.size(); i++) {
            double d = locations.get(i).distanceToSqr(entityPos);
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    // from skyhanni onRawScoreboardChange: the raw "Effigies:" line
    public static void onScoreboard(List<String> raw) {
        String line = null;
        for (String l : raw) if (l.startsWith("Effigies:")) {
            line = l;
            break;
        }
        if (line == null || line.equals(lastLine)) return;
        lastLine = line;
        Matcher m = HEARTS.matcher(line);
        if (!m.matches()) return;
        String[] split = m.group("hearts").split("§");
        for (int i = 1; i < split.length; i++) {
            int index = i - 1;
            if (index >= effigies.length || split[i].isEmpty()) continue;
            Effigy effigy = effigies[index];
            EffigyState oldState = effigy.state;
            char c = split[i].charAt(0);
            if (c == '7') effigy.state = EffigyState.NOT_BROKEN;
            else if (c == 'c') effigy.state = EffigyState.BROKEN;
            else continue;
            if (oldState == EffigyState.BROKEN && effigy.state == EffigyState.NOT_BROKEN) RiftEnv.world.chat("Effigy #" + (index + 1) + " respawned!");
            else if (oldState == EffigyState.NOT_BROKEN && effigy.state == EffigyState.BROKEN) {
                RiftEnv.world.chat("Effigy #" + (index + 1) + " broken!");
                effigy.respawnTime = RiftEnv.now() + 20 * 60_000L;
            }
        }
    }

    // from skyhanni onSecondPassed: one stands formatted name near its effigy
    public static void onArmorStand(String formattedName, Vec3 pos) {
        Matcher m = TIMER.matcher(formattedName);
        if (m.matches()) {
            int index = getIndex(pos);
            if (index < 0) return;
            Long time = HoppityEnv.duration(m.group("time"));
            if (time == null) return;
            effigies[index].state = EffigyState.BROKEN;
            effigies[index].respawnTime = RiftEnv.now() + time;
            return;
        }
        if (BREAK.matcher(formattedName).matches()) {
            int index = getIndex(pos);
            if (index >= 0) effigies[index].state = EffigyState.NOT_BROKEN;
        }
    }

    public static boolean isEnabled() {
        return RiftApi.inRift() && RiftApi.inStillgoreChateau();
    }

    public static void render(WorldDraw w) {
        if (!isEnabled()) return;
        List<Vec3> locations = RiftData.effigies;
        long now = RiftEnv.now();
        for (int index = 0; index < locations.size() && index < effigies.length; index++) {
            Vec3 location = locations.get(index);
            Vec3 center = location.add(0.5, 0.5, 0.5);
            String name = "Effigy #" + (index + 1);
            Effigy effigy = effigies[index];
            switch (effigy.state) {
                case BROKEN -> {
                    if (effigy.respawnTime == RiftEnv.FAR_PAST) {
                        if (unknownTime) {
                            w.box(new net.minecraft.world.phys.AABB(location, location.add(1, 1, 1)), 0xFFAAAAAA, true, true);
                            w.label("§7Unknown Time (" + name + ")", center.add(0, 1, 0), true);
                            continue;
                        }
                    } else if (respawningSoon && effigy.respawnTime - now < respawningSoonTime * 60_000L) {
                        w.box(new net.minecraft.world.phys.AABB(location, location.add(1, 1, 1)), 0xFFFFFF55, true, true);
                        w.label("§e" + name + " respawning in §b" + HoppityEnv.format(effigy.respawnTime - now), center.add(0, 1, 0), true);
                        continue;
                    }
                }
                case NOT_BROKEN -> {
                    w.box(new net.minecraft.world.phys.AABB(location, location.add(1, 1, 1)), 0xFFFF5555, true, true);
                    w.label("§cBreak " + name + "!", center.add(0, 1, 0), true);
                    continue;
                }
                case UNKNOWN -> {
                    if (unknownTime) {
                        w.box(new net.minecraft.world.phys.AABB(location, location.add(1, 1, 1)), 0xFFAAAAAA, true, true);
                        w.label("§7Unknown State (" + name + ")", center.add(0, 1, 0), true);
                        continue;
                    }
                }
            }
            if (center.distanceTo(RiftEnv.world.playerPos()) <= 15) w.label("§7" + name, center.add(0, 1, 0), true);
        }
    }

    // one effigy for the panel: state + time till respawn, -1 = unknown
    public record View(EffigyState state, long respawnIn, boolean soon) {}

    public static List<View> views() {
        long now = RiftEnv.now();
        List<View> out = new java.util.ArrayList<>();
        for (Effigy e : effigies) {
            long in = e.state == EffigyState.BROKEN && e.respawnTime != RiftEnv.FAR_PAST ? Math.max(0, e.respawnTime - now) : -1;
            out.add(new View(e.state, in, in >= 0 && in < respawningSoonTime * 60_000L));
        }
        return out;
    }

    // "red/6" for the old panel. careful: skyhanni reads §c as BROKEN
    public static int brokenCount() {
        int n = 0;
        for (Effigy e : effigies) if (e.state == EffigyState.BROKEN) n++;
        return n;
    }
}
