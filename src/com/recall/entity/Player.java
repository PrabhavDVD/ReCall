package com.recall.entity;

import com.recall.Config;
import com.recall.graphics.Camera;
import com.recall.physics.AABB;
import com.recall.physics.Vector3;
import com.recall.util.Logger;
import java.util.List;
import com.recall.entity.Entity;

/**
 * Player - First-person player controller with physics
 *
 * Purpose: Owns the player's feet position, velocity, and ground state.
 * Applies gravity, handles jumping, sprinting, crouching, and horizontal
 * movement derived from the camera's yaw. Syncs the camera to the player's
 * eye position every frame so the view follows the body.
 *
 * Coordinate contract:
 *   position = feet (y=0 means standing on the ground plane)
 *   camera   = eyes (position.y + EYE_HEIGHT / CROUCH_EYE_HEIGHT)
 *
 * Controls fed in from Game.update():
 *   WASD     → horizontal movement (yaw-relative)
 *   Space    → jump (only when grounded, not crouching)
 *   Shift    → sprint (faster walk speed)
 *   Ctrl     → crouch (slower speed, lower eye height)
 *
 * Example:
 *   Player player = new Player(0, 0, 5);
 *   player.update(w, s, a, d, jump, sprint, crouch, camera, deltaTime);
 */
public class Player {
    /** Feet position in world space */
    private Vector3 position;
    /** Current velocity in units/second */
    private Vector3 velocity;
    /** True when standing on solid ground */
    private boolean isGrounded;
    /** Counts down after a jump to prevent immediate re-jump on landing */
    private float jumpCooldown;
    /** Player health in HP — takes damage from enemies */
    private float health;

    // ===== Constants =====

    /** Camera offset above feet while standing */
    private static final float EYE_HEIGHT = 1.7f;
    /** Camera offset above feet while crouching */
    private static final float CROUCH_EYE_HEIGHT = 0.9f;
    /** Y coordinate of the ground plane — must match Map plane */
    private static final float GROUND_Y = 0f;

    // =====  Constructor =====

    /**
     * Spawn player with feet at (x, y, z).
     * Pass y=0 to start on the ground plane.
     */
    public Player(float x, float y, float z) {
        position    = new Vector3(x, y, z);
        velocity    = new Vector3(0f, 0f, 0f);
        isGrounded  = (y <= GROUND_Y);
        jumpCooldown = 0f;
        health      = 100f;  // Start with 100 HP
        Logger.info("Player spawned at " + position);
    }

    // ===== Main Update =====

    /**
     * Advance physics and movement by one frame.
     * Must be called after glfwPollEvents() so key states are fresh.
     *
     * @param forward  W key
     * @param backward S key
     * @param left     A key
     * @param right    D key
     * @param jump     Space key
     * @param sprint   Shift key
     * @param crouch   Ctrl key
     * @param camera   Camera whose position will be set to the player's eye
     * @param deltaTime Seconds since last frame
     */
    /**
     * Update with optional obstacle collision detection.
     * @param obstacles Can be null if no collision checks needed
     */
    public void update(boolean forward, boolean backward, boolean left, boolean right,
                       boolean jump, boolean sprint, boolean crouch,
                       Camera camera, float deltaTime, List<Entity> obstacles) {

        // ---- 1. Gravity ----
        // Only applied while airborne — landing zeroes velocity.y
        if (!isGrounded) {
            velocity.y -= Config.GRAVITY * deltaTime;
        }

        // ---- 2. Jump ----
        if (jumpCooldown > 0f) {
            jumpCooldown -= deltaTime;
        }

        // Jump condition: grounded + cooldown elapsed + not holding crouch
        if (jump && isGrounded && jumpCooldown <= 0f && !crouch) {
            velocity.y   = Config.PLAYER_JUMP_FORCE;
            isGrounded   = false;
            jumpCooldown = Config.PLAYER_JUMP_COOLDOWN;
            Logger.debug("Jump! y=" + String.format("%.2f", position.y));
        }

        // ---- 3. Horizontal movement ----
        // Speed tier: crouch < walk < sprint
        float speed = crouch ? Config.PLAYER_CROUCH_SPEED
                    : sprint ? Config.PLAYER_SPRINT_SPEED
                    :          Config.PLAYER_WALK_SPEED;

        // Derive XZ movement direction from camera yaw (pitch ignored — stays level)
        float yawRad     = (float) Math.toRadians(camera.getYaw());
        Vector3 flatFwd  = new Vector3(
            (float) Math.cos(yawRad),
            0f,
            (float) Math.sin(yawRad)
        ).normalize();
        // Right = flatFwd × worldUp  (right-hand rule gives +X when facing -Z)
        Vector3 rightVec = flatFwd.cross(new Vector3(0f, 1f, 0f)).normalize();

        float moveX = 0f;
        float moveZ = 0f;

        if (forward)  { moveX += flatFwd.x  * speed;  moveZ += flatFwd.z  * speed; }
        if (backward) { moveX -= flatFwd.x  * speed;  moveZ -= flatFwd.z  * speed; }
        if (right)    { moveX += rightVec.x * speed;  moveZ += rightVec.z * speed; }
        if (left)     { moveX -= rightVec.x * speed;  moveZ -= rightVec.z * speed; }

        // Overwrite horizontal velocity each frame (no horizontal drag/inertia for now)
        velocity.x = moveX;
        velocity.z = moveZ;

        // ---- 4. Integrate velocity into position (axis-separated for wall sliding) ----
        float dx = velocity.x * deltaTime;
        float dy = velocity.y * deltaTime;
        float dz = velocity.z * deltaTime;

        // Y always applied — ground collision handles landing
        position.y += dy;

        // X axis — revert only X if blocked, Z can still slide
        position.x += dx;
        if (obstacles != null && overlapsAnyObstacle(obstacles, crouch)) {
            position.x -= dx;
        }

        // Z axis — revert only Z if blocked, X can still slide
        position.z += dz;
        if (obstacles != null && overlapsAnyObstacle(obstacles, crouch)) {
            position.z -= dz;
        }

        // ---- 5. Ground collision ----
        if (position.y <= GROUND_Y) {
            position.y  = GROUND_Y;
            velocity.y  = 0f;
            isGrounded  = true;
        } else {
            isGrounded  = false;
        }

        // ---- 6. Sync camera to eye position ----
        float eyeY = position.y + (crouch ? CROUCH_EYE_HEIGHT : EYE_HEIGHT);
        camera.setPosition(position.x, eyeY, position.z);
    }

    // ===== Health & Damage =====

    /**
     * Reduce player health by the specified amount.
     * Called by enemies when they attack the player.
     *
     * @param amount Damage to apply (in HP)
     */
    public void takeDamage(float amount) {
        health -= amount;
        if (health < 0f) health = 0f;
        Logger.info("Player took " + amount + " damage, health now: " + health);
    }

    /**
     * Check if the player is dead.
     * @return true when health <= 0
     */
    public boolean isDead() {
        return health <= 0f;
    }

    /**
     * Get current player health (for HUD display).
     * @return health in HP
     */
    public float getHealth() {
        return health;
    }

    /**
     * Reset player health to 100 HP (used by respawn / resetGame).
     */
    public void resetHealth() {
        health = 100f;
    }

    /** Returns true if the player's current position overlaps any obstacle AABB. */
    private boolean overlapsAnyObstacle(List<Entity> obstacles, boolean crouch) {
        float eyeY = position.y + (crouch ? CROUCH_EYE_HEIGHT : EYE_HEIGHT);
        AABB playerBounds = new AABB(
            new Vector3(position.x - 0.4f, position.y, position.z - 0.4f),
            new Vector3(position.x + 0.4f, eyeY,       position.z + 0.4f)
        );
        for (Entity e : obstacles) {
            if (e == null || e.getBounds() == null || e.isDead() || !e.isCollidable()) continue;
            if (playerBounds.intersects(e.getBounds())) return true;
        }
        return false;
    }

    // ===== Getters =====
    public Vector3 getPosition()  { return position; }
    public Vector3 getVelocity()  { return velocity; }
    public boolean isGrounded()   { return isGrounded; }
    public float getJumpCooldown(){ return jumpCooldown; }
}
