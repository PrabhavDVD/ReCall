package com.recall.weapon;

import com.recall.physics.Bullet;
import com.recall.physics.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * Shotgun — High damage per pellet, wide spread, slow fire rate.
 *
 * Stats: 80 damage per pellet, 2× headshot multiplier, ~1.67 shots/sec (600ms interval)
 * Recoil: 3.0° pitch up, ±2.0° yaw (applied by Game.java)
 * Use case: Devastating close-range burst, punishes poor positioning
 *
 * Bullet behaviour:
 *   Speed: 80 u/s (slowest — visible spread in flight)
 *   Penetration: yes, within 3 units of spawn origin (very close range only)
 *   Pellet count: 6 physical Bullet objects per shot
 *   Spread: 30° cone (±15° half-angle), centre-weighted distribution
 *
 * Kill counts:
 *   Body:     2 shots (2 × 80 = 160 HP, assuming ≥1 pellet lands)
 *   Headshot: 1 shot  (1 × 160 = 160 HP — 1-shot kill)
 *   All 6 pellets at point-blank: 480 HP total
 */
public class Shotgun extends Weapon {

    /** Half-angle of the pellet spread cone in degrees (30° total cone = ±15°). */
    private static final float MAX_SPREAD_DEG = 15.0f;

    /** Number of physical pellet bullets spawned per shot. */
    private static final int PELLET_COUNT = 6;

    public Shotgun() {
        super(80f, 2f, 1f / 0.6f);
        bulletSpeed          = 80f;
        penetrationThreshold = 3f;
    }

    /**
     * Spawn 12 pellets in a centre-weighted spread cone.
     *
     * Each pellet is an independent {@link Bullet} object that travels through
     * the world, detects its own collisions, and applies its own damage.
     *
     * Spread distribution: uses the average of two uniform randoms [0, 1] to
     * bias the radial distance toward 0 (centre). This gives a realistic
     * shotgun pattern — dense cluster at the aim point, sparse at the edge.
     *
     * @param origin    Spawn origin (camera eye position)
     * @param direction Aim direction (normalised internally)
     * @return List of 12 Bullet objects
     */
    @Override
    protected List<Bullet> spawnBullets(Vector3 origin, Vector3 direction) {
        List<Bullet> pellets = new ArrayList<>(PELLET_COUNT);

        Vector3 dir     = direction.normalize();
        Vector3 worldUp = new Vector3(0f, 1f, 0f);

        // Build an orthonormal basis (right, up) perpendicular to the aim direction.
        // Fall back to world-Z reference when aiming nearly straight up or down.
        Vector3 right;
        if (Math.abs(dir.dot(worldUp)) > 0.99f) {
            right = dir.cross(new Vector3(0f, 0f, 1f)).normalize();
        } else {
            right = dir.cross(worldUp).normalize();
        }
        Vector3 up = right.cross(dir).normalize();

        // tan(spread) = max lateral offset per unit of forward depth
        float maxOffset = (float) Math.tan(Math.toRadians(MAX_SPREAD_DEG));

        for (int i = 0; i < PELLET_COUNT; i++) {
            // Centre-weighted radial distance: average of two uniforms ∈ [0, 1].
            // This concentrates pellets near the aim point (mean = 0.5, but
            // the distribution is triangular, so most hits cluster centrally).
            float r     = ((float) Math.random() + (float) Math.random()) / 2.0f;
            float theta = (float) (Math.random() * 2.0 * Math.PI);

            float oR = (float) Math.sin(theta) * r * maxOffset;
            float oU = (float) Math.cos(theta) * r * maxOffset;

            Vector3 pelletDir = new Vector3(
                dir.x + right.x * oR + up.x * oU,
                dir.y + right.y * oR + up.y * oU,
                dir.z + right.z * oR + up.z * oU
            ).normalize();

            pellets.add(new Bullet(origin, pelletDir,
                                   damage, headshotMultiplier,
                                   bulletSpeed, penetrationThreshold, maxRange));
        }

        return pellets;
    }

    @Override
    public String getAmmoType() {
        return "12 GAUGE";
    }
}
