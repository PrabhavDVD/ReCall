package com.recall.weapon;

import com.recall.physics.Bullet;
import com.recall.physics.Vector3;

import java.util.Collections;
import java.util.List;

/**
 * Weapon — Base class for projectile weapons.
 *
 * Fire model: on click the weapon spawns one or more {@link Bullet} objects
 * that travel through the world at a fixed speed. Bullets handle their own
 * collision detection and damage application; the weapon no longer performs
 * instant hitscan raycasts.
 *
 * Subclasses configure:
 *   - damage / headshotMultiplier / fireRate (via super constructor)
 *   - bulletSpeed          — how fast bullets travel (units/second)
 *   - penetrationThreshold — max distance from spawn at which a bullet may
 *                            pierce through entities (0 = never pierce).
 *
 * Subclasses may override {@link #spawnBullets} to return multiple bullets
 * (e.g. Shotgun) or to add custom spread logic.
 *
 * Cooldown is tracked internally via {@link #update(float)}.
 */
public abstract class Weapon {

    protected final float damage;
    protected final float headshotMultiplier;
    private   final float fireInterval;  // seconds between shots (1 / fireRate)
    private         float cooldown;      // counts down to 0

    /**
     * Travel speed for bullets spawned by this weapon (world units / second).
     * Subclasses should set this in their constructor.
     * Default: 120 u/s.
     */
    protected float bulletSpeed = 120f;

    /**
     * Maximum distance from spawn origin at which this weapon's bullets may
     * pierce through (not stop at) entities. 0 = never pierce. Use
     * Float.MAX_VALUE for always-pierce (e.g. sniper).
     * Default: 0 (no penetration).
     */
    protected float penetrationThreshold = 0f;

    /**
     * Maximum travel distance for bullets spawned by this weapon (world units).
     * Knife uses 2f for melee range. Default: 100f (effectively unlimited).
     */
    protected float maxRange = 100f;

    protected Weapon(float damage, float headshotMultiplier, float fireRate) {
        this.damage             = damage;
        this.headshotMultiplier = headshotMultiplier;
        this.fireInterval       = 1f / fireRate;
        this.cooldown           = 0f;
    }

    /** Tick the fire cooldown — call once per frame before firing. */
    public void update(float dt) {
        if (cooldown > 0f) cooldown = Math.max(0f, cooldown - dt);
    }

    /** True when the weapon is off cooldown and ready to shoot. */
    public boolean canFire() { return cooldown <= 0f; }

    /**
     * Attempt to fire. If off cooldown, resets the cooldown and returns a
     * list of {@link Bullet} objects to be added to the world. Returns an
     * empty list if still on cooldown.
     *
     * @param origin    Spawn origin for bullets (typically the camera eye)
     * @param direction Aim direction (normalized internally)
     * @return Bullets to add to the active bullet list, or empty if on cooldown
     */
    public List<Bullet> fire(Vector3 origin, Vector3 direction) {
        if (!canFire()) return Collections.emptyList();
        cooldown = fireInterval;
        return spawnBullets(origin, direction);
    }

    /**
     * Create the bullet(s) for one shot. Called by {@link #fire} after the
     * cooldown check. Subclasses override this to change bullet count or spread.
     *
     * @param origin    Bullet spawn origin
     * @param direction Normalized aim direction
     * @return List of bullet objects (never null)
     */
    protected List<Bullet> spawnBullets(Vector3 origin, Vector3 direction) {
        return Collections.singletonList(
            new Bullet(origin, direction.normalize(),
                       damage, headshotMultiplier,
                       bulletSpeed, penetrationThreshold, maxRange)
        );
    }

    /**
     * Get the ammunition type name for HUD display.
     * Subclasses override to return specific calibre names.
     * @return Ammo type string (e.g. "9MM", "5.56MM", "12 GAUGE")
     */
    public String getAmmoType() {
        return "AMMO";
    }
}
