package com.recall.physics;

/**
 * AABB - Axis-Aligned Bounding Box
 *
 * Purpose: Represents a rectangular volume aligned to world axes.
 * Stored as (min, max) corner points. Used for collision detection,
 * raycasting targets, and trigger volumes.
 *
 * Coordinate contract:
 *   min.x <= max.x, min.y <= max.y, min.z <= max.z
 *   A point p is inside when min <= p <= max on every axis.
 *
 * Example:
 *   // Box 2 units wide, sitting on the ground, centered at origin:
 *   AABB box = AABB.fromCenterSize(new Vector3(0,1,0), new Vector3(2,2,2));
 *   box.containsPoint(new Vector3(0.5f, 1f, 0.5f)); // true
 */
public class AABB {
    public Vector3 min;
    public Vector3 max;

    /** Construct from two corner points. Corners are normalized so min <= max on every axis. */
    public AABB(Vector3 min, Vector3 max) {
        this.min = new Vector3(
            Math.min(min.x, max.x),
            Math.min(min.y, max.y),
            Math.min(min.z, max.z)
        );
        this.max = new Vector3(
            Math.max(min.x, max.x),
            Math.max(min.y, max.y),
            Math.max(min.z, max.z)
        );
    }

    /** Build an AABB centered at `center` with full extents (width, height, depth) given by `size`. */
    public static AABB fromCenterSize(Vector3 center, Vector3 size) {
        Vector3 half = size.mul(0.5f);
        return new AABB(center.sub(half), center.add(half));
    }

    // ===== Queries =====

    /** Geometric center of the box. */
    public Vector3 getCenter() {
        return new Vector3(
            (min.x + max.x) * 0.5f,
            (min.y + max.y) * 0.5f,
            (min.z + max.z) * 0.5f
        );
    }

    /** Full size (width, height, depth). */
    public Vector3 getSize() {
        return max.sub(min);
    }

    /** True if the point lies inside (or exactly on the boundary of) this box. */
    public boolean containsPoint(Vector3 p) {
        return p.x >= min.x && p.x <= max.x
            && p.y >= min.y && p.y <= max.y
            && p.z >= min.z && p.z <= max.z;
    }

    /** True if this box overlaps another AABB (touching counts as overlap). */
    public boolean intersects(AABB other) {
        return this.min.x <= other.max.x && this.max.x >= other.min.x
            && this.min.y <= other.max.y && this.max.y >= other.min.y
            && this.min.z <= other.max.z && this.max.z >= other.min.z;
    }

    // ===== Ray Intersection (Slab Method) =====

    /**
     * Ray-AABB intersection using the slab method.
     *
     * Treats the box as the intersection of three pairs of parallel planes
     * (slabs) — one pair per axis. For each axis we find the t-values at which
     * the ray enters and exits the slab. The ray hits the box if the largest
     * entry-t is less than or equal to the smallest exit-t, and that exit-t
     * is non-negative (box not entirely behind the ray origin).
     *
     * @param origin      Ray start (world space)
     * @param dirUnit     Ray direction — MUST be unit-length; the returned t is world-space distance
     * @param maxDistance Ignore hits farther than this (use Float.POSITIVE_INFINITY for no limit)
     * @return Distance along the ray to the first hit, or Float.POSITIVE_INFINITY on miss.
     *         If the origin is inside the box, returns 0 (immediate hit).
     */
    public float rayIntersect(Vector3 origin, Vector3 dirUnit, float maxDistance) {
        // Origin inside the box → immediate hit at distance 0
        if (containsPoint(origin)) return 0f;

        float tMin = Float.NEGATIVE_INFINITY;
        float tMax = Float.POSITIVE_INFINITY;

        // --- X slab ---
        if (Math.abs(dirUnit.x) < 1e-8f) {
            // Ray parallel to X slab → miss unless origin is already inside the slab
            if (origin.x < min.x || origin.x > max.x) return Float.POSITIVE_INFINITY;
        } else {
            float invDx = 1.0f / dirUnit.x;
            float t1 = (min.x - origin.x) * invDx;
            float t2 = (max.x - origin.x) * invDx;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tMin) tMin = t1;
            if (t2 < tMax) tMax = t2;
            if (tMin > tMax) return Float.POSITIVE_INFINITY;
        }

        // --- Y slab ---
        if (Math.abs(dirUnit.y) < 1e-8f) {
            if (origin.y < min.y || origin.y > max.y) return Float.POSITIVE_INFINITY;
        } else {
            float invDy = 1.0f / dirUnit.y;
            float t1 = (min.y - origin.y) * invDy;
            float t2 = (max.y - origin.y) * invDy;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tMin) tMin = t1;
            if (t2 < tMax) tMax = t2;
            if (tMin > tMax) return Float.POSITIVE_INFINITY;
        }

        // --- Z slab ---
        if (Math.abs(dirUnit.z) < 1e-8f) {
            if (origin.z < min.z || origin.z > max.z) return Float.POSITIVE_INFINITY;
        } else {
            float invDz = 1.0f / dirUnit.z;
            float t1 = (min.z - origin.z) * invDz;
            float t2 = (max.z - origin.z) * invDz;
            if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
            if (t1 > tMin) tMin = t1;
            if (t2 < tMax) tMax = t2;
            if (tMin > tMax) return Float.POSITIVE_INFINITY;
        }

        // Box entirely behind the ray origin
        if (tMax < 0f) return Float.POSITIVE_INFINITY;

        // First hit: tMin if in front of the origin, else tMax (origin was inside all slabs but not the box
        // — already handled above, but kept for robustness)
        float hit = (tMin >= 0f) ? tMin : tMax;
        if (hit > maxDistance) return Float.POSITIVE_INFINITY;
        return hit;
    }

    @Override
    public String toString() {
        return "AABB(min=" + min + ", max=" + max + ")";
    }
}
