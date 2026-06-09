package com.recall.physics;

import com.recall.entity.Entity;

/**
 * RaycastResult - Data for a single ray-vs-entity hit.
 *
 * Purpose: Lightweight immutable record describing what a raycast struck.
 * Returned by {@link Collider#raycast}. A `null` result means the ray
 * hit nothing within its maxDistance.
 *
 * Fields:
 *   entity    — the entity that was hit
 *   hitPoint  — world-space intersection point (origin + dir * distance)
 *   distance  — world-space distance from the ray origin to the hit
 *   isHeadshot — true when the hit point is in the top 25% of the entity's AABB
 *                (bodyshot otherwise). Used by the weapons system for damage
 *                multipliers in later phases.
 */
public class RaycastResult {
    public final Entity  entity;
    public final Vector3 hitPoint;
    public final float   distance;
    public final boolean isHeadshot;

    public RaycastResult(Entity entity, Vector3 hitPoint, float distance, boolean isHeadshot) {
        this.entity     = entity;
        this.hitPoint   = hitPoint;
        this.distance   = distance;
        this.isHeadshot = isHeadshot;
    }

    @Override
    public String toString() {
        return String.format("RaycastResult(%s @ %.2fm%s)",
            entity.getName(), distance, isHeadshot ? " HEADSHOT" : "");
    }
}
