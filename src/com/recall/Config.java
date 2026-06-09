package com.recall;

/**
 * Config - Centralized configuration constants
 *
 * Purpose: Single source of truth for all game configuration values.
 * This makes it easy to tweak settings without searching through code.
 */
public class Config {
    // Window Configuration
    public static final int WINDOW_WIDTH = 1280;
    public static final int WINDOW_HEIGHT = 720;
    public static final String WINDOW_TITLE = "ReCall";
    public static final int TARGET_FPS = 60;
    public static final boolean VSYNC_ENABLED = true;

    // Camera Configuration
    public static final float CAMERA_FOV = 60f;
    public static final float CAMERA_SENSITIVITY = 0.1f;
    public static final float NEAR_PLANE = 0.1f;
    public static final float FAR_PLANE = 100f;

    // Player Configuration
    public static final float PLAYER_WALK_SPEED = 5f;
    public static final float PLAYER_SPRINT_SPEED = 8f;
    public static final float PLAYER_CROUCH_SPEED = 2.5f;
    public static final float PLAYER_JUMP_FORCE = 5f;
    public static final float PLAYER_JUMP_COOLDOWN = 0.5f;
    public static final float GRAVITY = 12.25f;

    // AI Configuration
    public static final float AI_DETECTION_RANGE = 50f;
    public static final float AI_SHOOT_RANGE = 30f;
    public static final float AI_MOVE_SPEED = 4f;

    // Network Configuration (Phase 2.5 — basic 1v1 UDP)
    /** Default UDP port the host binds and the client targets. */
    public static final int   NET_DEFAULT_PORT = 7777;
    /** How many player-state snapshots to broadcast per second. */
    public static final int   NET_SEND_RATE_HZ = 30;
    /** Seconds without any packet before the peer is treated as disconnected. */
    public static final float NET_TIMEOUT_SEC  = 5f;
    /** Receive buffer size in bytes (one datagram = one message; messages are tiny). */
    public static final int   NET_PACKET_BYTES = 512;

    // Debug
    public static final boolean DEBUG_MODE = true;
    public static final boolean SHOW_FPS = true;
}
