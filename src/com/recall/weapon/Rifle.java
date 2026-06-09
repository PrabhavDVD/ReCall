package com.recall.weapon;

/**
 * Rifle — Automatic assault rifle, high fire rate.
 *
 * Stats:
 *   40 body damage  → 3-shot kill (100 HP enemy)
 *   3× headshot     → 120 damage → 1-shot kill
 *   10 shots/sec    → 100ms between shots
 *
 * Bullet behaviour:
 *   Speed: 150 u/s (fastest standard weapon)
 *   Penetration: yes, within 5 units of spawn origin.
 *     Bullets pierce entities at close range; damage degrades 35–45% per pierce.
 */
public class Rifle extends Weapon {
    public Rifle() {
        super(40f, 3f, 1f / 0.1f);
        bulletSpeed          = 150f;
        penetrationThreshold = 5f;
    }

    @Override
    public String getAmmoType() {
        return "5.56MM";
    }
}
