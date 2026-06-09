package com.recall.weapon;

/**
 * Sniper — Bolt-action, one-shot-kill at any range.
 *
 * Stats: 100 body damage (1-shot kill), 1.5× headshot → 150 HP, 1.0/sec
 * Recoil: 4.5° pitch up, ±1.2° yaw (applied by Game.java)
 * Use case: Long-range precision; full commitment per shot
 *
 * Bullet behaviour:
 *   Speed: 300 u/s (fastest weapon)
 *   Penetration: unlimited (Float.MAX_VALUE) — shoots through all entities
 *
 * Kill counts:
 *   Body:     1 shot  (100 HP)
 *   Headshot: 1 shot  (150 HP)
 */
public class Sniper extends Weapon {
    public Sniper() {
        super(100f, 1.5f, 1.0f);
        bulletSpeed          = 300f;
        penetrationThreshold = Float.MAX_VALUE;  // always pierce
    }

    @Override
    public String getAmmoType() {
        return "7.62MM";
    }
}
