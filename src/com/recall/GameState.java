package com.recall;

/**
 * GameState — Game state enum for state machine transitions.
 *
 * MENU:    Title screen before gameplay. World frozen, menu HUD shown.
 * PLAYING: Normal gameplay. Physics, bullets, and HUD running.
 * PAUSED:  Gameplay paused via ESC key. World frozen, pause menu shown.
 * DEAD:    Player dead. Dark-red death overlay shown, world frozen.
 */
public enum GameState {
    MENU,
    WEAPON_SELECT,
    MAP_SELECT,
    TEAM_SELECT,
    PLAYING,
    PAUSED,
    DEAD,
    ROUND_END,
    MATCH_END
}
