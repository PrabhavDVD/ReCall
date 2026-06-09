package com.recall.physics;

import com.recall.entity.Entity;
import java.util.List;

/**
 * Collider - Stateless collision query utility.
 *
 * Purpose: Given a ray and a list of entities, returns the closest hit
 * (or null on miss). Built on AABB.rayIntersect, so all cost is the
 * slab-method test per entity. For the small entity counts in this game
 * (tens at most), a linear scan is fine — spatial indexing can be added
 * later if profiling demands it.
 *
 * Headshot rule:
 *   The top 25% of an entity's AABB counts as "head". Concretely, any hit
 *   whose world-space Y is at or above (min.y + height * 0.75) is a
 *   headshot. This is a gameplay convention — easy to reason about, easy
 *   to visualize, and cheap to compute.
 *
 * Example:
 *   RaycastResult hit = Collider.raycast(
 *       camera.getPosition(),
 *       camera.getForwardVector(),
 *       entities,
 *       100f);
 *   if (hit != null) Logger.info(hit.toString());
 */
public final class Collider {
    /** Fraction of AABB height (measured from the top down) that counts as head. */
    private static final float HEADSHOT_FRACTION = 0.25f;

    private Collider() { /* static utility, no instances */ }

    /**
     * Cast a ray and return the first entity it strikes.
     *
     * Filters out entities whose {@code isCollidable()} is false. Ignores
     * entities whose bounds are null (unusual but possible for
     * render-only entities).
     *
     * @param origin      Ray start point in world space
     * @param direction   Ray direction — will be normalized internally, so
     *                    it is safe to pass a non-unit vector (e.g. a raw
     *                    forward vector that may have been scaled). If the
     *                    direction has zero length, returns null.
     * @param entities    Candidates to test against
     * @param maxDistance Hits farther than this are discarded (world units)
     * @return The closest RaycastResult, or null if nothing was hit within range
     */
    public static RaycastResult raycast(Vector3 origin, Vector3 direction,
                                        List<? extends Entity> entities,
                                        float maxDistance) {
        // Guard against zero-length direction (would produce NaNs in the slab test)
        float dirLen = direction.length();
        if (dirLen < 1e-8f) return null;
        Vector3 dir = direction.div(dirLen);  // normalized copy

        Entity  bestEntity   = null;
        float   bestDistance = Float.POSITIVE_INFINITY;

        for (Entity e : entities) {
            if (e == null || !e.isCollidable()) continue;
            AABB b = e.getBounds();
            if (b == null) continue;

            float t = b.rayIntersect(origin, dir, maxDistance);
            if (t < bestDistance) {
                bestDistance = t;
                bestEntity   = e;
            }
        }

        if (bestEntity == null || Float.isInfinite(bestDistance)) return null;

        // Hit point = origin + dir * t
        Vector3 hitPoint = origin.add(dir.mul(bestDistance));

        // Headshot: top HEADSHOT_FRACTION of AABB height
        AABB b = bestEntity.getBounds();
        float height         = b.max.y - b.min.y;
        float headThresholdY = b.min.y + height * (1f - HEADSHOT_FRACTION);
        boolean isHeadshot   = hitPoint.y >= headThresholdY;

        return new RaycastResult(bestEntity, hitPoint, bestDistance, isHeadshot);
    }
}
