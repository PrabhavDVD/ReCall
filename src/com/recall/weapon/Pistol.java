package com.recall.weapon;

/**
 * Pistol — Very high fire rate semi-auto sidearm.
 *
 * Stats: 35 damage, 3× headshot multiplier, ~6.67 shots/sec (150ms interval)
 * Recoil: 0.8° pitch up, ±0.3° yaw (applied by Game.java)
 * Use case: Rapid follow-up shots, secondary weapon
 *
 * Bullet behaviour:
 *   Speed: 120 u/s
 *   Penetration: none — stops at first entity hit
 *
 * Kill counts:
 *   Body:     3 shots (3 × 35 = 105 HP)
 *   Headshot: 1 shot  (1 × 105 = 105 HP)
 */
public class Pistol extends Weapon {
    public Pistol() {
        super(35f, 3f, 1f / 0.15f);
        bulletSpeed          = 120f;
        penetrationThreshold = 0f;
    }

    @Override
    public String getAmmoType() {
        return "9MM";
    }
}
