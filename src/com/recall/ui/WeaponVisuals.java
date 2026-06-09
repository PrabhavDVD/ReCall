package com.recall.ui;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import com.recall.graphics.Camera;
import com.recall.graphics.Shader;
import com.recall.physics.Vector3;
import com.recall.weapon.WeaponModel;

/**
 * WeaponVisuals - First-person rifle rendering and muzzle flash effect.
 *
 * Render order:
 *   1. World geometry  (handled by Game)
 *   2. Rifle model     ← this class (depth cleared so rifle is always on top)
 *   3. 2D HUD text     (handled by SimpleHUD)
 *
 * Rifle positioning:
 *   Camera-relative offset (FORWARD, RIGHT, DOWN) so the rifle always appears
 *   in the lower-right of the player's view regardless of where they look.
 *
 * Rotation:
 *   The rifle barrel points along −Z in model space.  A rotateY + rotateX
 *   transform aligns it with the camera's actual forward direction so the
 *   barrel tracks the crosshair perfectly.
 *
 * Muzzle flash:
 *   A small orange cube at the barrel tip, visible for FLASH_DURATION seconds.
 *   Shrinks from FLASH_MAX_SIZE to FLASH_MIN_SIZE as it fades.
 *   Call notifyFired() each time the weapon successfully fires.
 */
public class WeaponVisuals {

    /** The weapon's mesh provider — set via init(WeaponModel). */
    private WeaponModel model;

    // --- Muzzle flash ---
    private static final float FLASH_DURATION = 0.05f;   // 50 ms
    private static final float FLASH_MAX_SIZE = 0.10f;   // world units at start
    private static final float FLASH_MIN_SIZE = 0.04f;   // world units at end
    private float muzzleFlashTimer = 0f;

    // --- Camera-relative weapon offsets (world units) ---
    private static final float FORWARD_OFFSET = 0.65f;   // in front of eye
    private static final float RIGHT_OFFSET   = 0.30f;   // to the right
    private static final float DOWN_OFFSET    = 0.28f;   // below eye level

    // -----------------------------------------------------------------------

    /**
     * Initialize with the given weapon mesh provider.
     * WeaponVisuals takes ownership of the model and cleans it up on cleanup().
     *
     * @param weaponModel The model to render (RifleModel, PistolModel, ShotgunModel, …)
     */
    public void init(WeaponModel weaponModel) {
        this.model = weaponModel;
    }

    /** Call each time a shot is successfully fired to trigger the muzzle flash. */
    public void notifyFired() {
        muzzleFlashTimer = FLASH_DURATION;
    }

    /** Tick the muzzle flash countdown.  Call once per frame in Game.update(). */
    public void update(float dt) {
        if (muzzleFlashTimer > 0f) {
            muzzleFlashTimer = Math.max(0f, muzzleFlashTimer - dt);
        }
    }

    /**
     * Render the rifle (and muzzle flash if active).
     *
     * Must be called while the world shader is still bound — i.e. between
     * renderer.beginFrame() and renderer.endFrame().  The method clears the
     * depth buffer before drawing so the rifle always appears in front of
     * world geometry (standard FPS behaviour).
     *
     * @param shader     Active world shader (projection + view already set)
     * @param camera     Current camera (position + orientation)
     * @param projection Projection matrix — not used here but kept for clarity
     */
    public void render(Shader shader, Camera camera, float[] projection) {

        // --- Camera vectors ------------------------------------------------
        Vector3 camPos  = camera.getPosition();
        Vector3 forward = camera.getForwardVector();       // normalised, includes pitch

        // Flat (XZ) right vector: keeps the horizontal offset constant as the
        // player looks up/down (prevents the rifle from swinging sideways).
        Vector3 flatFwd = new Vector3(forward.x, 0f, forward.z).normalize();
        Vector3 right   = flatFwd.cross(new Vector3(0f, 1f, 0f)).normalize();

        // --- Rifle world position ------------------------------------------
        // Follow forward (with pitch) for depth, flat-right for side offset,
        // fixed world-down for the vertical drop below eye level.
        Vector3 riflePos = new Vector3(
            camPos.x + forward.x * FORWARD_OFFSET + right.x * RIGHT_OFFSET,
            camPos.y + forward.y * FORWARD_OFFSET - DOWN_OFFSET,
            camPos.z + forward.z * FORWARD_OFFSET + right.z * RIGHT_OFFSET
        );

        // --- Rotation: align barrel (model −Z) with camera forward ---------
        //
        // Derivation:
        //   Camera forward at yaw Y, pitch P:
        //     f = (cos P · cos Y,  sin P,  cos P · sin Y)
        //   After Matrix4f.translate().rotateY(yRad).rotateX(pRad), a model-space
        //   point (0,0,−1) maps to the world-space direction (cos P·cos Y, sin P, cos P·sin Y)
        //   when:
        //     yRad   = −(yaw + 90°) × π/180
        //     pRad   =   pitch       × π/180
        //
        float yawRad   = (float) Math.toRadians(-(camera.getYaw()   + 90f));
        float pitchRad = (float) Math.toRadians(  camera.getPitch());

        float[] rifleMatrix = new float[16];
        new Matrix4f()
            .translate(riflePos.x, riflePos.y, riflePos.z)
            .rotateY(yawRad)
            .rotateX(pitchRad)
            .get(rifleMatrix);

        // --- Clear depth so rifle renders in front of all world geometry ---
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        // --- Draw weapon ---------------------------------------------------
        shader.setMat4("model", rifleMatrix);
        model.getMesh().render();

        // --- Draw muzzle flash (if active) ---------------------------------
        if (muzzleFlashTimer > 0f) {
            // progress 0 = just fired (big/bright), 1 = fading out (small)
            float progress = 1f - (muzzleFlashTimer / FLASH_DURATION);
            float scale    = FLASH_MAX_SIZE + (FLASH_MIN_SIZE - FLASH_MAX_SIZE) * progress;

            // Barrel tip: rotation maps model −Z to world forward, so
            //   barrelTip = weaponPos + forward × barrelLength
            float barrelLength = model.getBarrelLength();
            Vector3 barrelTip = new Vector3(
                riflePos.x + forward.x * barrelLength,
                riflePos.y + forward.y * barrelLength,
                riflePos.z + forward.z * barrelLength
            );

            float[] flashMatrix = new float[16];
            new Matrix4f()
                .translate(barrelTip.x, barrelTip.y, barrelTip.z)
                .scale(scale)
                .get(flashMatrix);

            shader.setMat4("model", flashMatrix);
            model.getFlashMesh().render();
        }
    }

    /**
     * Render the weapon in a centered showcase pose for the weapon-select screen.
     *
     * The gun is placed 2 world units in front of the fixed showcase camera,
     * rotated so the barrel faces left (−X in world space) with a gentle
     * −15° pitch tilt — the same side-profile angle used in Valorant's
     * weapon inspect view.  Per-model centering offsets correct for each
     * weapon's non-centered bounding box so the gun sits at screen center.
     *
     * Must be called while the world shader is active (between beginFrame /
     * endFrame), exactly like render().  The depth buffer is cleared first so
     * the model renders in front of any remaining world geometry.
     *
     * @param shader     Active world shader (projection + view already set)
     * @param camera     Fixed showcase camera (position + orientation)
     * @param projection Projection matrix (kept for parity with render())
     */
    public void renderShowcase(Shader shader, Camera camera, float[] projection) {
        float scale = model.getShowcaseScale();
        float cx    = model.getShowcaseCenterX();   // right shift to center horizontally
        float cy    = model.getShowcaseCenterY();   // up shift to center vertically

        Vector3 camPos  = camera.getPosition();
        Vector3 forward = camera.getForwardVector();

        // World-space position: 2 units along the camera's line of sight, then
        // compensated so the gun body (not the model origin) sits at screen center.
        float   dist        = 2.0f;
        Vector3 showcasePos = new Vector3(
            camPos.x + forward.x * dist + cx,
            camPos.y + forward.y * dist + cy,
            camPos.z + forward.z * dist
        );

        // Build matrix:
        //   scale(s)          — enlarge to fill ~65% of screen width
        //   rotateX(−15°)     — tilt barrel slightly upward (3/4 showcase angle)
        //   rotateY(+90°)     — barrel (model −Z) → world −X (left on screen)
        //   translate(pos)    — move to showcase position
        //
        // Applied right-to-left to vertices: scale → rotX → rotY → translate.
        float[] showcaseMatrix = new float[16];
        new Matrix4f()
            .translate(showcasePos.x, showcasePos.y, showcasePos.z)
            .rotateY((float)  Math.PI / 2f)
            .rotateX((float) Math.toRadians(-15.0))
            .scale(scale)
            .get(showcaseMatrix);

        // Clear depth — gun must appear in front of any residual world geometry
        GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);

        shader.setMat4("model", showcaseMatrix);
        model.getMesh().render();
    }

    public void cleanup() {
        if (model != null) model.cleanup();
    }
}
