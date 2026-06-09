package com.recall.physics;

import com.recall.entity.Entity;
import com.recall.entity.Obstacle;
import com.recall.graphics.Mesh;
import com.recall.graphics.Vertex;
import org.joml.Matrix4f;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Bullet — Physics-based projectile.
 *
 * Travels at constant speed in a fixed direction. Each frame a sweep collision
 * test (short raycast from old to new position) is performed to prevent tunnelling
 * at high speeds.
 *
 * Penetration rules:
 *   Obstacles (walls)  → always stopped; creates an impact marker at hit point.
 *   Entities (Dummy/Enemy) → penetrated only when distanceTraveled < penetrationThreshold.
 *                            Passing through costs 35–45 % of current damage (random).
 *                            Pistol: threshold 0 (never pierces).
 *                            Rifle:  threshold 5u (close-range pierce only).
 *                            Shotgun:threshold 3u (very close-range pierce only).
 *                            Sniper (future): Float.MAX_VALUE (always pierces).
 *
 * Visual: one shared GPU mesh — a thin yellow-orange box (0.018 × 0.018 × 0.25 u)
 *         built along the -Z axis in model space. Each bullet applies a rotation at
 *         render time to align the mesh with its travel direction. Call
 *         initSharedMesh() once after the OpenGL context is ready and
 *         destroySharedMesh() at shutdown.
 */
public class Bullet {

    // ── Shared GPU mesh (built once, used by every bullet) ───────────────────
    private static Mesh sharedMesh;

    // Tracer box half-width and length in model space (along -Z)
    private static final float W   = 0.009f;
    private static final float LEN = 0.25f;

    // ── Physics state ─────────────────────────────────────────────────────────
    private final Vector3 position;
    private final Vector3 direction;          // normalized, constant
    private final float   speed;             // world units / second
    private       float   damage;            // degrades on entity pierce
    private final float   headshotMult;
    private final float   penetrationThreshold;
    private       float   distanceTraveled;
    private       boolean alive;

    /** Entities this bullet has already damaged — prevents multi-hit in same AABB. */
    private final Set<Entity> alreadyHit = new HashSet<>();

    // Maximum distance this bullet can travel before dying (per-instance, set by weapon)
    private final float maxRange;

    // ── Results read by Game.java each tick ───────────────────────────────────
    /** World-space hit point on an obstacle; non-null for one frame after obstacle hit. */
    private Vector3 lastImpactPoint;
    /** Kill string for HUD; non-null for one frame after killing an entity. */
    private String  lastKillNotification;

    // ── Rotation cache (direction is constant → compute once) ────────────────
    // We pre-compute the axis/angle that rotates the mesh from its default
    // "pointing along -Z" orientation to the bullet's actual travel direction.
    private final float rotAngle;
    private final float rotAxisX;
    private final float rotAxisY;   // axis is always (x, y, 0)

    // ─────────────────────────────────────────────────────────────────────────

    public Bullet(Vector3 origin, Vector3 direction,
                  float damage, float headshotMult,
                  float speed, float penetrationThreshold) {
        this(origin, direction, damage, headshotMult, speed, penetrationThreshold, 100f);
    }

    public Bullet(Vector3 origin, Vector3 direction,
                  float damage, float headshotMult,
                  float speed, float penetrationThreshold, float maxRange) {
        this.position             = new Vector3(origin);
        this.direction            = direction.normalize();
        this.damage               = damage;
        this.headshotMult         = headshotMult;
        this.speed                = speed;
        this.penetrationThreshold = penetrationThreshold;
        this.maxRange             = maxRange;
        this.alive                = true;
        this.distanceTraveled     = 0f;

        // Pre-compute rotation: model default is (0,0,-1); we rotate to direction.
        //   dot(default, dir)  = -dir.z
        //   axis = cross((0,0,-1), dir) = (dir.y, -dir.x, 0)
        Vector3 dir   = this.direction;
        float   dot   = Math.max(-1f, Math.min(1f, -dir.z));
        float   axisX = dir.y;
        float   axisY = -dir.x;
        float   axisLen = (float) Math.sqrt(axisX * axisX + axisY * axisY);

        if (axisLen < 1e-6f) {
            // Bullet travels exactly along ±Z
            this.rotAngle = dir.z > 0 ? (float) Math.PI : 0f;
            this.rotAxisX = 0f;
            this.rotAxisY = 1f;
        } else {
            this.rotAngle = (float) Math.acos(dot);
            this.rotAxisX = axisX / axisLen;
            this.rotAxisY = axisY / axisLen;
        }
    }

    // ── Physics update ────────────────────────────────────────────────────────

    /**
     * Move bullet and test for collisions. Call once per frame.
     *
     * Results (lastImpactPoint, lastKillNotification) are populated during this
     * call and should be read by Game.java immediately after, then cleared via
     * clearResults() before the next tick.
     *
     * @param dt       Frame delta-time in seconds
     * @param entities All collidable world entities (not the player)
     */
    public void update(float dt, List<Entity> entities) {
        if (!alive) return;

        // How far to move this frame — clamped to remaining range
        float remainingRange = maxRange - distanceTraveled;
        float stepDist       = Math.min(speed * dt, remainingRange);

        // Sweep collision: cast a short ray along direction for this frame's step.
        // This prevents tunnelling through thin geometry at high bullet speeds.
        RaycastResult hit = Collider.raycast(position, direction, entities, stepDist);

        if (hit != null) {
            if (hit.entity instanceof Obstacle) {
                // Obstacles always stop bullets
                lastImpactPoint = new Vector3(hit.hitPoint);
                alive = false;
                return;

            } else if (!alreadyHit.contains(hit.entity)) {
                // Entity hit — apply damage, check penetration
                float dmg = hit.isHeadshot ? damage * headshotMult : damage;
                hit.entity.takeDamage(dmg);
                alreadyHit.add(hit.entity);

                if (hit.entity.isDead()) {
                    lastKillNotification = "KILLED " + hit.entity.getName().toUpperCase();
                }

                boolean canPierce = (penetrationThreshold > 0f)
                                 && (distanceTraveled < penetrationThreshold);

                if (!canPierce) {
                    alive = false;
                    return;
                }

                // Penetrating entity: lose 35–45 % of current damage
                float retain = 0.55f + (float) Math.random() * 0.10f;
                damage *= retain;
            }
        }

        // Advance bullet position
        position.addInPlace(direction.mul(stepDist));
        distanceTraveled += stepDist;

        if (distanceTraveled >= maxRange) alive = false;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    /**
     * Write this bullet's model matrix (translate + rotate) into {@code dest}.
     * {@code dest} must be a float[16] (column-major, matching JOML convention).
     * Call once per frame per bullet, immediately before rendering.
     */
    public void buildMatrix(float[] dest) {
        new Matrix4f()
            .translate(position.x, position.y, position.z)
            .rotate(rotAngle, rotAxisX, rotAxisY, 0f)
            .get(dest);
    }

    // ── Shared mesh lifecycle ─────────────────────────────────────────────────

    /**
     * Build the shared tracer mesh. Must be called once after the OpenGL context
     * is initialised (i.e. from Game.init(), not from a static initialiser).
     */
    public static void initSharedMesh() {
        Vector3 col = new Vector3(1.0f, 0.85f, 0.3f);  // bright yellow-orange

        // Eight corners of a box centred on the Z-axis, extending from z=0 to z=-LEN.
        // Model space: bullet's "nose" is at z=-LEN (forward), "tail" at z=0.
        Vertex[] v = {
            new Vertex(new Vector3(-W, -W,  0f  ), col),  // 0  front bottom-left
            new Vertex(new Vector3( W, -W,  0f  ), col),  // 1  front bottom-right
            new Vertex(new Vector3( W,  W,  0f  ), col),  // 2  front top-right
            new Vertex(new Vector3(-W,  W,  0f  ), col),  // 3  front top-left
            new Vertex(new Vector3(-W, -W, -LEN ), col),  // 4  back bottom-left
            new Vertex(new Vector3( W, -W, -LEN ), col),  // 5  back bottom-right
            new Vertex(new Vector3( W,  W, -LEN ), col),  // 6  back top-right
            new Vertex(new Vector3(-W,  W, -LEN ), col),  // 7  back top-left
        };

        int[] idx = {
            // Front  (z = 0,   normal +Z)
            0, 2, 1,  0, 3, 2,
            // Back   (z = -LEN, normal -Z)
            4, 5, 6,  4, 6, 7,
            // Top    (y = +W,  normal +Y)
            3, 7, 6,  3, 6, 2,
            // Bottom (y = -W,  normal -Y)
            0, 1, 5,  0, 5, 4,
            // Right  (x = +W,  normal +X)
            1, 2, 6,  1, 6, 5,
            // Left   (x = -W,  normal -X)
            0, 4, 7,  0, 7, 3,
        };

        sharedMesh = new Mesh(v, idx);
    }

    /** Render the shared tracer mesh. Caller is responsible for setting the model matrix. */
    public static void renderShared() {
        if (sharedMesh != null) sharedMesh.render();
    }

    /** Free shared GPU resources. Call once at game shutdown. */
    public static void destroySharedMesh() {
        if (sharedMesh != null) {
            sharedMesh.delete();
            sharedMesh = null;
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public boolean isAlive() { return alive; }

    public Vector3 getLastImpactPoint()      { return lastImpactPoint; }
    public String  getLastKillNotification() { return lastKillNotification; }

    /** Clear per-frame hit results — call after Game.java has read them. */
    public void clearResults() {
        lastImpactPoint      = null;
        lastKillNotification = null;
    }
}
