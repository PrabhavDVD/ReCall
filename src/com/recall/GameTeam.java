package com.recall;

/**
 * GameTeam — Team identity for 1v1 matches.
 *
 * ALPHA: Blue team, spawns south at z = +14.
 * BRAVO: Red  team, spawns north at z = -14.
 *
 * Phase 2.7: used for color coding + HUD indicator.
 * Phase 2.8: spawn positions enforced per team.
 */
public enum GameTeam {
    ALPHA,
    BRAVO
}
