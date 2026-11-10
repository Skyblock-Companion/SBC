// SPDX-License-Identifier: GPL-3.0-only
// Changed in 2026 from code or data of SkyHanni; see THIRD_PARTY_NOTICES.md.
package io.muzy.sbc.events.diana;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

// from skyhanni RaycastUtils, the ray helpers the chain arrow guess uses
public final class Raycast {
    public static final double EPSILON = 1e-12;

    // direction is normalised
    public record Ray(Vec3 origin, Vec3 direction) {}

    private Raycast() {}

    // ray to point distance, MAX_VALUE if the point is behind the origin
    public static double findDistanceToRay(Ray ray, Vec3 point) {
        // plane through the point orthogonal to the ray, intersect the ray with it
        Vec3 n = ray.direction();
        double along = (n.dot(point) - n.dot(ray.origin())) / n.dot(ray.direction());
        Vec3 intersection = ray.origin().add(ray.direction().scale(along));
        if (intersection.subtract(ray.origin()).dot(ray.direction()) < 0) return Double.MAX_VALUE;
        return intersection.distanceTo(point);
    }

    // entry + exit of a ray through a box, null if it misses. entry can be behind the origin
    public static Vec3[] intersectAABBWithRay(AABB aabb, Ray ray) {
        double[] aabbMin = {aabb.minX, aabb.minY, aabb.minZ};
        double[] aabbMax = {aabb.maxX, aabb.maxY, aabb.maxZ};
        double[] dir = {ray.direction().x, ray.direction().y, ray.direction().z};
        double[] origin = {ray.origin().x, ray.origin().y, ray.origin().z};
        double tMin = -Double.MAX_VALUE, tMax = Double.MAX_VALUE;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dir[i]) < EPSILON) {
                if (origin[i] < aabbMin[i] || origin[i] > aabbMax[i]) return null;
            } else {
                double ood = 1.0 / dir[i];
                double t1 = (aabbMin[i] - origin[i]) * ood;
                double t2 = (aabbMax[i] - origin[i]) * ood;
                if (t1 > t2) {
                    double t = t1;
                    t1 = t2;
                    t2 = t;
                }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) return null;
            }
        }
        return new Vec3[]{ray.origin().add(ray.direction().scale(tMin)), ray.origin().add(ray.direction().scale(tMax))};
    }

    // point on the ray where axis (0=x, 1=y, 2=z) hits the target value, null if unreachable
    public static Vec3 findPointOnRay(Ray ray, int axis, double targetValue) {
        double[] origin = {ray.origin().x, ray.origin().y, ray.origin().z};
        double[] dir = {ray.direction().x, ray.direction().y, ray.direction().z};
        double dirComponent = dir[axis];
        if (Math.abs(dirComponent) < EPSILON) {
            return Math.abs(origin[axis] - targetValue) < EPSILON ? ray.origin() : null;
        }
        double t = (targetValue - origin[axis]) / dirComponent;
        return ray.origin().add(ray.direction().scale(t));
    }
}
