package com.recall.weapon;

/**
 * Knife — Melee weapon, infinite ammo, 2-unit range.
 *
 * Stats: 50 damage, 2× headshot → 100 HP (1-shot kill), 2.5 swings/sec
 * Recoil: none (applied by Game.java as 0° pitch, 0° yaw)
 * Use case: Silent close-range finisher; never runs out of ammo
 *
 * Bullet behaviour:
 *   Speed: 2000 u/s (near-instant — effectively melee)
 *   Max range: 2 units (blade reach)
 *   Penetration: none
 *
 * Kill counts:
 *   Body:     2 swings (2 × 50 = 100 HP)
 *   Headshot: 1 swing  (1 × 100 = 100 HP)
 */
public class Knife extends Weapon {
    public Knife() {
        super(50f, 2f, 1f / 0.4f);  // 2.5 swings/sec
        bulletSpeed          = 2000f;
        penetrationThreshold = 0f;
        maxRange             = 2f;   // blade reach
    }

    @Override
    public String getAmmoType() {
        return "MELEE";
    }
}
