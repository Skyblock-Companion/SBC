// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.diana;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// from skyhanni ArrowGuessBurrow: after a burrow in a chain hypixel draws an arrow of DUST particles (20
// point shaft, 2 extra near the base, 4 near the tip, colour = distance band)
// arrow is a ray from the dug burrow, next burrow = valid grass block closest to the ray within the band
public final class ArrowGuessBurrow {
    private static final int SHAFT_LENGTH = 20;
    private static final double PARTICLE_DETECTION_TOLERANCE = 0.12;
    private static final int COUNT_NEAR_TIP = 4;
    private static final int COUNT_NEAR_BASE = 2;
    private static final double EPSILON = 1e-6;

    private static final Set<Vec3> points = new LinkedHashSet<>();
    private static final TimeLimitedSet<Raycast.Ray> recentFoundArrows = new TimeLimitedSet<>(18_000);
    public static long lastArrowTime = DianaEnv.FAR_PAST;
    private static int failures;

    private ArrowGuessBurrow() {}

    // ArrowGuessBurrow.onParticle (hub). offset = the DUST colour (xd, yd, zd)
    public static void onParticle(String type, double x, double y, double z, int count, float speed, float xd, float yd, float zd) {
        if (!isEnabled()) return;
        Vec3 location = new Vec3(x, y, z);
        if (location.distanceTo(DianaEnv.world.playerPos()) > 6) return;
        if (!type.equals("minecraft:dust")) return;
        if (count != 0) return;
        if (speed != 1.0f) return;

        int[] range = getArrowRange(xd, yd, zd);
        if (range == null) return;

        points.add(location);
        Raycast.Ray ray = detectArrow(points);
        if (ray == null) return;
        Vec3 dugBlock = roundToBlock(ray.origin());
        GriffinBurrowHelper.addDebug("detected arrow origin above block [" + dugBlock.x + ", " + dugBlock.y + ", " + dugBlock.z + "]");
        GriffinBurrowHelper.removeGuess(dugBlock, "origin of detected arrow");
        Delayed.run(Diana.averagePingMs() + 200, () -> GriffinBurrowHelper.removeGuess(dugBlock, "origin of detected arrow (delayed)"));
        lastArrowTime = DianaEnv.now();
        if (addGuessFromRay(ray, range) == null) {
            GriffinBurrowHelper.addDebug("arrow guess returned null");
            failures++;
            if (Diana.b("warnOnFail", true)) GriffinBurrowHelper.showUseSpadeTitle();
        }
    }

    public static void onBurrowDug(int current, int max) {
        if (current != max) points.clear();
    }

    public static int failures() {
        return failures;
    }

    // distance band per DUST colour: yellow 0-117, red 112-282, black 281-600 blocks
    public static int[] getArrowRange(float r, float g, float b) {
        if (r == 0f && g == 128f && b == 0f) return new int[]{0, 117};
        if (r == 255f && g == 255f && b == 0f) return new int[]{112, 282};
        if (r == 255f && g == 0f && b == 0f) return new int[]{281, 600};
        return null;
    }

    static Vec3 roundToBlock(Vec3 v) {
        return new Vec3(Math.floor(v.x), Math.floor(v.y), Math.floor(v.z));
    }

    // ArrowGuessBurrow.addGuessFromRay: blocks along the ray inside the hub, nearest to it, within the band
    static Vec3 addGuessFromRay(Raycast.Ray ray, int[] range) {
        AABB bounds = DianaData.islandBounds("HUB");
        if (bounds == null) {
            GriffinBurrowHelper.addDebug("couldn't get hub bounds");
            return null;
        }
        if (!bounds.contains(ray.origin())) { // guarantees exit point is first intersect
            GriffinBurrowHelper.addDebug("origin not in bounds");
            return null;
        }
        Vec3[] hit = Raycast.intersectAABBWithRay(bounds, ray);
        if (hit == null) {
            GriffinBurrowHelper.addDebug("couldn't find endpoint");
            return null;
        }
        Vec3 endPoint = hit[1];
        double[] diff = {endPoint.x - ray.origin().x, endPoint.y - ray.origin().y, endPoint.z - ray.origin().z};
        // axis with the least change among those leaving the block
        int axisIndex = -1;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(diff[i]) <= 0.9) continue;
            if (axisIndex == -1 || Math.abs(diff[i]) < Math.abs(diff[axisIndex])) axisIndex = i;
        }
        if (axisIndex == -1) {
            GriffinBurrowHelper.addDebug("couldn't find axis index");
            return null;
        }

        Map<Vec3, double[]> candidates = new LinkedHashMap<>(); // position -> (scaledDistToRay, distFromOrigin)
        double[] endArr = {endPoint.x, endPoint.y, endPoint.z};
        double[] originArr = {ray.origin().x, ray.origin().y, ray.origin().z};
        double[] dirArr = {ray.direction().x, ray.direction().y, ray.direction().z};

        double iterations = Math.abs(endArr[axisIndex] - originArr[axisIndex]);
        for (int i = 1; i <= (int) iterations; i++) {
            double axisValue = originArr[axisIndex] + i * Math.signum(dirArr[axisIndex]);
            Vec3 candidatePoint = Raycast.findPointOnRay(ray, axisIndex, axisValue);
            if (candidatePoint == null) continue;
            Vec3 candidateBlock = roundToBlock(candidatePoint);
            if (!GriffinBurrowHelper.isBlockValid(candidateBlock)) continue;
            Vec3 blockCenter = candidateBlock.add(0.5, 0.5, 0.5);
            double distanceToRay = Raycast.findDistanceToRay(ray, blockCenter);
            double distanceFromOrigin = candidatePoint.distanceTo(ray.origin());
            // ratio to account for errors
            double scaledDistance = distanceToRay * 500000 / distanceFromOrigin;
            candidates.put(candidateBlock, new double[]{Math.round(scaledDistance * 100.0) / 100.0, distanceFromOrigin});
        }
        if (candidates.isEmpty()) {
            GriffinBurrowHelper.addDebug("candidates list is empty");
            return null;
        }
        double minValue = Double.MAX_VALUE;
        for (double[] v : candidates.values()) minValue = Math.min(minValue, v[0]);
        List<Vec3> withinRange = new ArrayList<>();
        for (Map.Entry<Vec3, double[]> e : candidates.entrySet()) {
            if (e.getValue()[0] != minValue) continue;
            int d = (int) e.getValue()[1];
            if (d >= range[0] && d <= range[1]) withinRange.add(e.getKey());
        }
        if (withinRange.isEmpty()) {
            GriffinBurrowHelper.addDebug("no candidates within range");
            return null;
        }
        GriffinBurrowHelper.onBurrowGuess(new GuessEntry(withinRange), "arrow guess");
        return withinRange.get(0);
    }

    // ArrowGuessBurrow.detectArrow: shaft line, base and tip from the extra particles, as a ray
    static Raycast.Ray detectArrow(Set<Vec3> points) {
        List<Vec3> line = findLine(points, SHAFT_LENGTH, PARTICLE_DETECTION_TOLERANCE);
        if (line.isEmpty()) return null;

        // arrow head intersects the particle one off the end
        Vec3 candidate1 = line.get(1);
        Vec3 candidate2 = line.get(line.size() - 2);
        int count1 = getPointsWithinDistance(points, candidate1, PARTICLE_DETECTION_TOLERANCE);
        int count2 = getPointsWithinDistance(points, candidate2, PARTICLE_DETECTION_TOLERANCE);

        // one should be 2 (base), other 4 (tip)
        boolean ok = (count1 == COUNT_NEAR_BASE && count2 == COUNT_NEAR_TIP) || (count1 == COUNT_NEAR_TIP && count2 == COUNT_NEAR_BASE);
        if (!ok) return null;

        Vec3 base, tip;
        if (count1 == COUNT_NEAR_TIP) {
            tip = line.get(0);
            base = line.get(line.size() - 1);
        } else {
            tip = line.get(line.size() - 1);
            base = line.get(0);
        }

        // arrow is a block above the center of the start block
        Vec3 adjustedBase = base.add(0, -1.5, 0);
        Vec3 adjustedTip = tip.add(0, -1.5, 0);
        Raycast.Ray ray = new Raycast.Ray(adjustedBase, adjustedTip.subtract(adjustedBase).normalize());
        if (recentFoundArrows.add(ray)) return null;
        points.clear();

        // not your arrow
        if (DianaEnv.passedSince(BurrowApi.lastBurrowRelatedChatMessage) > 500) {
            Vec3 b = roundToBlock(adjustedBase);
            Vec3 p = DianaEnv.world.playerPos();
            GriffinBurrowHelper.addDebug("not your arrow detected at [" + b.x + ", " + b.y + ", " + b.z + "], player pos [" + p.x + ", " + p.y + ", " + p.z + "]");
            return null;
        }
        return ray;
    }

    private static int getPointsWithinDistance(Iterable<Vec3> points, Vec3 origin, double maxDist) {
        double maxDistSq = maxDist * maxDist;
        int n = 0;
        for (Vec3 p : points) if (!p.equals(origin) && p.distanceToSqr(origin) <= maxDistSq) n++;
        return n;
    }

    private static List<Vec3> findLine(Iterable<Vec3> points, int shaftLength, double maxDist) {
        for (Vec3 point : points) {
            List<Vec3> line = new ArrayList<>();
            Set<Vec3> visited = new java.util.HashSet<>();
            line.add(point);
            visited.add(point);
            if (extendLine(line, visited, points, shaftLength, maxDist)) return line;
        }
        return List.of();
    }

    private static boolean extendLine(List<Vec3> line, Set<Vec3> visited, Iterable<Vec3> points, int numPoints, double maxDist) {
        if (line.size() == numPoints) return true; // line done
        Vec3 nextPoint = null;
        double minDist = Double.MAX_VALUE;
        for (Vec3 point : points) {
            if (visited.contains(point)) continue;
            double dist = line.get(line.size() - 1).distanceTo(point);
            if (dist > maxDist) continue;
            // must be collinear with all existing points
            Vec3 second = line.size() > 1 ? line.get(1) : line.get(0);
            if (!isCollinear(line.get(0), second, point)) continue;
            if (dist < minDist) {
                minDist = dist;
                nextPoint = point;
            }
        }
        if (nextPoint != null) {
            line.add(nextPoint);
            visited.add(nextPoint);
            if (extendLine(line, visited, points, numPoints, maxDist)) return true;
            // backtrack
            line.remove(line.size() - 1);
            visited.remove(nextPoint);
        }
        return false;
    }

    private static boolean isCollinear(Vec3 a, Vec3 b, Vec3 c) {
        Vec3 ab = b.subtract(a);
        Vec3 ac = c.subtract(a);
        return ab.cross(ac).lengthSqr() < EPSILON;
    }

    public static void reset() {
        points.clear();
        recentFoundArrows.clear();
        lastArrowTime = DianaEnv.FAR_PAST;
        failures = 0;
    }

    private static boolean isEnabled() {
        return DianaApi.isDoingDiana() && Diana.guess() && Diana.b("guessFromArrow", true);
    }
}
