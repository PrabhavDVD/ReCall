package com.recall.graphics;

import org.joml.Matrix4f;
import com.recall.Config;
import com.recall.physics.Vector3;
import com.recall.util.Logger;

/**
 * Camera - First-person perspective camera
 *
 * Purpose: Manages world-space position and orientation (yaw/pitch).
 * Builds the view matrix that transforms world coordinates into
 * camera-relative (eye) space, which the vertex shader multiplies
 * against projection to produce clip coordinates.
 *
 * Coordinate convention:
 *   Yaw  0°  → facing +X
 *   Yaw -90° → facing -Z  (default, into the scene)
 *   Pitch +° → looking up
 *   Pitch -° → looking down
 *
 * Movement is XZ-plane locked (FPS-style): WASD ignores pitch so
 * the player doesn't fly when looking up/down. Space/Shift handle
 * vertical position for this free-fly phase.
 *
 * Example:
 *   Camera cam = new Camera(0, 2, 8);
 *   cam.processMouseMovement(dx, dy);
 *   cam.move(w, s, a, d, space, shift, deltaTime);
 *   shader.setMat4("view", cam.getViewMatrix());
 */
public class Camera {
    private Vector3 position;
    private float yaw;    // Horizontal rotation (degrees)
    private float pitch;  // Vertical rotation (degrees)

    // Recoil — temporary camera rotation from weapon fire (decays each frame)
    private float recoilYaw   = 0f;
    private float recoilPitch = 0f;
    private static final float RECOIL_DECAY = 0.92f;  // exponential decay per frame

    /** Maximum pitch angle — prevents the camera from flipping upside-down */
    private static final float PITCH_LIMIT = 89.0f;

    /**
     * Create a camera at the given world position.
     * Default orientation: yaw -90° (facing -Z into the scene),
     * pitch -15° (slight downward tilt so the plane is immediately visible).
     */
    public Camera(float x, float y, float z) {
        position = new Vector3(x, y, z);
        yaw   = -90.0f;
        pitch = -15.0f;
        Logger.debug("Camera created at " + position + " yaw=" + yaw + " pitch=" + pitch);
    }

    // ===== Public Interface =====

    /**
     * Build the view matrix for the current camera orientation.
     * Call every frame — cheap (just trig + one matrix multiply).
     *
     * @return 16-float column-major matrix ready for glUniformMatrix4fv
     */
    public float[] getViewMatrix() {
        Vector3 fwd = getForwardVector();
        float[] view = new float[16];
        new Matrix4f().lookAt(
            position.x, position.y, position.z,
            position.x + fwd.x, position.y + fwd.y, position.z + fwd.z,
            0f, 1f, 0f
        ).get(view);
        return view;
    }

    /**
     * Apply mouse movement to update orientation.
     *
     * @param dx Mouse delta X (positive = moved right → yaw increases)
     * @param dy Mouse delta Y (positive = moved up   → pitch increases)
     */
    public void processMouseMovement(float dx, float dy) {
        yaw   += dx * Config.CAMERA_SENSITIVITY;
        pitch += dy * Config.CAMERA_SENSITIVITY;

        // Clamp pitch so the camera never flips past vertical
        if (pitch >  PITCH_LIMIT) pitch =  PITCH_LIMIT;
        if (pitch < -PITCH_LIMIT) pitch = -PITCH_LIMIT;

        // Keep yaw in [-360, 360] to prevent float precision loss over long sessions
        if (yaw >  360f) yaw -= 360f;
        if (yaw < -360f) yaw += 360f;
    }

    /**
     * Move camera based on directional key states.
     *
     * WASD moves on the XZ plane (pitch-independent, FPS feel).
     * Space / Left-Shift move vertically for free-fly.
     *
     * @param forward  W key held
     * @param backward S key held
     * @param left     A key held
     * @param right    D key held
     * @param up       Space key held
     * @param down     Shift key held
     * @param deltaTime Seconds since last frame (for frame-rate independence)
     */
    public void move(boolean forward, boolean backward, boolean left, boolean right,
                     boolean up, boolean down, float deltaTime) {
        float dist = Config.PLAYER_WALK_SPEED * deltaTime;

        // Flatten forward onto the XZ plane so WASD never changes Y on its own
        Vector3 fwd = getForwardVector();
        Vector3 flatForward = new Vector3(fwd.x, 0f, fwd.z).normalize();

        // Right vector: flatForward × worldUp  (right-hand rule → +X when facing -Z)
        Vector3 rightVec = flatForward.cross(new Vector3(0f, 1f, 0f)).normalize();

        if (forward)  position.addInPlace(flatForward.mul( dist));
        if (backward) position.addInPlace(flatForward.mul(-dist));
        if (right)    position.addInPlace(rightVec.mul( dist));
        if (left)     position.addInPlace(rightVec.mul(-dist));

        // Vertical fly
        if (up)   position.y += dist;
        if (down) position.y -= dist;
    }

    // ===== Internal Helpers =====

    /**
     * Compute normalized forward direction from current yaw and pitch.
     *
     * Standard spherical → Cartesian conversion:
     *   x = cos(pitch) * cos(yaw)
     *   y = sin(pitch)
     *   z = cos(pitch) * sin(yaw)
     *
     * Includes recoil rotation applied by applyRecoil().
     * Public so the collision system can raycast along the camera's aim.
     */
    public Vector3 getForwardVector() {
        // Effective yaw and pitch include recoil (which decays each frame)
        float effectiveYaw   = yaw   + recoilYaw;
        float effectivePitch = pitch + recoilPitch;

        float yawRad   = (float) Math.toRadians(effectiveYaw);
        float pitchRad = (float) Math.toRadians(effectivePitch);
        return new Vector3(
            (float)(Math.cos(pitchRad) * Math.cos(yawRad)),
            (float)(Math.sin(pitchRad)),
            (float)(Math.cos(pitchRad) * Math.sin(yawRad))
        ).normalize();
    }

    // ===== Recoil =====

    /**
     * Apply recoil to the camera (temporary rotation from weapon fire).
     * The recoil decays automatically each frame via decayRecoil().
     *
     * @param yawKick   Side-to-side rotation (degrees). Positive = right.
     * @param pitchKick Up-down rotation (degrees). Positive = up.
     */
    public void applyRecoil(float yawKick, float pitchKick) {
        recoilYaw   += yawKick;
        recoilPitch += pitchKick;
    }

    /**
     * Decay recoil toward zero (exponential falloff).
     * Call once per frame in Game.update() to smooth out recoil.
     */
    public void decayRecoil() {
        recoilYaw   *= RECOIL_DECAY;
        recoilPitch *= RECOIL_DECAY;

        // Snap to zero when nearly zero (prevents infinite tail)
        if (Math.abs(recoilYaw)   < 0.01f) recoilYaw   = 0f;
        if (Math.abs(recoilPitch) < 0.01f) recoilPitch = 0f;
    }

    // ===== Getters / Setters =====

    /**
     * Teleport camera to a new world position.
     * Called every frame by Player to keep the view at eye height.
     */
    public void setPosition(float x, float y, float z) {
        position.x = x;
        position.y = y;
        position.z = z;
    }

    public Vector3 getPosition() { return position; }
    public float getYaw()        { return yaw; }
    public float getPitch()      { return pitch; }

    /**
     * Current recoil magnitude in degrees (combined yaw + pitch kick).
     * Used by the HUD to bloom the crosshair after firing; decays to 0.
     */
    public float getRecoilMagnitude() {
        return (float) Math.sqrt(recoilYaw * recoilYaw + recoilPitch * recoilPitch);
    }

    /** Override yaw directly — used to fix orientation for menu/preview states. */
    public void setYaw(float yaw)     { this.yaw = yaw; }
    /** Override pitch directly — used to fix orientation for menu/preview states. */
    public void setPitch(float pitch) { this.pitch = pitch; }
}
