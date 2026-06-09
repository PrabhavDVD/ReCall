package com.recall.entity;

import com.recall.graphics.Mesh;
import com.recall.graphics.Vertex;
import com.recall.net.PlayerState;
import com.recall.physics.AABB;
import com.recall.physics.Vector3;
import java.util.ArrayList;
import java.util.List;

/**
 * RemotePlayer — a networked opponent, rendered from STATE packets.
 *
 * Unlike {@link Dummy} or {@link Enemy}, this entity is driven entirely by the
 * network. Its position, look angles, health, and liveness all come from
 * {@link #setState(PlayerState)} every time a snapshot arrives.
 *
 * <h2>Damage flow (important)</h2>
 * Each peer is authoritative over its <em>own</em> health (Phase 2.5 model). So when a
 * local bullet strikes this entity, we must NOT change its health here — the owning
 * machine does that. Instead {@link #takeDamage(float)} records the amount in
 * {@link #consumePendingDamage()}, which {@code Game} drains each frame and forwards as a
 * {@code HIT} packet. The opponent applies it to its real player and sends its reduced
 * health back, which we display via {@link #setState}.
 *
 * <h2>Lifecycle</h2>
 * The object lives for the whole session — it is never freed by Game's dead-entity
 * reaper ({@link #isDead()} always returns false). Visibility and collision are gated by
 * {@link #isAliveRemote()} / {@link #isCollidable()} instead, both driven by the
 * networked liveness flag. Before the first snapshot arrives the opponent is hidden and
 * non-collidable.
 *
 * Mesh: blue capsule built at the origin and translated each frame (same approach as
 * {@link Enemy}, so {@code Game} positions it via the model matrix at render time).
 */
public class RemotePlayer extends Entity {

    /** Opponent colour — blue, distinct from red enemies and the practice dummies. */
    private static final Vector3 OPPONENT_COLOR = new Vector3(0.25f, 0.55f, 0.95f);

    private float   yaw;
    private float   pitch;
    /** Networked liveness — false until the first snapshot and whenever the peer is dead. */
    private boolean aliveRemote = false;
    /** Damage our local bullets dealt this frame, awaiting a HIT packet. */
    private float   pendingDamage = 0f;

    public RemotePlayer(String name, Vector3 startPos) {
        super(name, startPos);
        this.maxHealth  = 100f;
        this.health     = 100f;
        this.collidable = false;   // no collision/raycast until the first snapshot arrives
        updateBounds();

        // Capsule baked at the origin; Game translates it to `position` via the model matrix.
        mesh = buildCapsuleMesh(new Vector3(0f, 0f, 0f), 0.4f, 1.8f, 6, OPPONENT_COLOR);
    }

    // -----------------------------------------------------------------------
    // Network state
    // -----------------------------------------------------------------------

    /** Apply a freshly received snapshot from the opponent. */
    public void setState(PlayerState s) {
        position.x  = s.x;
        position.y  = s.y;
        position.z  = s.z;
        this.yaw    = s.yaw;
        this.pitch  = s.pitch;
        this.health = s.health;

        aliveRemote      = s.alive && s.health > 0f;
        this.collidable  = aliveRemote;   // dead opponents block neither bullets nor movement
        updateBounds();
    }

    /**
     * Bullets call this on hit. We deliberately do NOT change health — we queue the
     * damage so {@code Game} can forward it to the owning peer as a HIT packet.
     */
    @Override
    public void takeDamage(float amount) {
        pendingDamage += amount;
    }

    /** Drain damage dealt since the last call (0 if none). Game sends this as a HIT. */
    public float consumePendingDamage() {
        float d = pendingDamage;
        pendingDamage = 0f;
        return d;
    }

    /** Networked liveness — used by Game to gate rendering and collision. */
    public boolean isAliveRemote() { return aliveRemote; }

    /**
     * Always false: the opponent entity must never be freed by Game's dead-entity reaper,
     * even when its networked health is 0 (it can respawn over the wire). Use
     * {@link #isAliveRemote()} for render/collision decisions.
     */
    @Override
    public boolean isDead() { return false; }

    public float getYaw()   { return yaw; }
    public float getPitch() { return pitch; }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Recalculate the AABB from the current position. Call after every state update. */
    private void updateBounds() {
        bounds = new AABB(
                new Vector3(position.x - 0.4f, position.y,        position.z - 0.4f),
                new Vector3(position.x + 0.4f, position.y + 1.8f, position.z + 0.4f));
    }

    // -----------------------------------------------------------------------
    // Mesh generation (capsule — same construction as Enemy/Dummy, blue colour)
    // -----------------------------------------------------------------------

    private Mesh buildCapsuleMesh(Vector3 center, float radius, float height, int sides, Vector3 color) {
        List<Vertex> vertexList = new ArrayList<>();
        List<Integer> indexList = new ArrayList<>();

        Vector3 cTop  = color.mul(1.00f);
        Vector3 cBot  = color.mul(0.35f);
        Vector3 cSide = color.mul(0.75f);

        // Bottom pole
        int botPoleIdx = vertexList.size();
        vertexList.add(new Vertex(new Vector3(center.x, center.y, center.z), cBot));

        // Bottom ring (y = 0.2)
        int[] botRing = new int[sides];
        for (int i = 0; i < sides; i++) {
            float angle = (float) (2.0 * Math.PI * i / sides);
            float x = center.x + radius * (float) Math.cos(angle);
            float z = center.z + radius * (float) Math.sin(angle);
            botRing[i] = vertexList.size();
            vertexList.add(new Vertex(new Vector3(x, center.y + 0.2f, z), cBot));
        }

        // Cylinder rings (bottom, middle, top)
        int[][] cylRings = new int[3][sides];
        float[] yPos = {0.4f, 0.9f, 1.4f};
        for (int ring = 0; ring < 3; ring++) {
            for (int i = 0; i < sides; i++) {
                float angle = (float) (2.0 * Math.PI * i / sides);
                float x = center.x + radius * (float) Math.cos(angle);
                float z = center.z + radius * (float) Math.sin(angle);
                cylRings[ring][i] = vertexList.size();
                vertexList.add(new Vertex(new Vector3(x, center.y + yPos[ring], z), cSide));
            }
        }

        // Top ring (y = 1.6)
        int[] topRing = new int[sides];
        for (int i = 0; i < sides; i++) {
            float angle = (float) (2.0 * Math.PI * i / sides);
            float x = center.x + radius * (float) Math.cos(angle);
            float z = center.z + radius * (float) Math.sin(angle);
            topRing[i] = vertexList.size();
            vertexList.add(new Vertex(new Vector3(x, center.y + 1.6f, z), cTop));
        }

        // Top pole
        int topPoleIdx = vertexList.size();
        vertexList.add(new Vertex(new Vector3(center.x, center.y + height, center.z), cTop));

        // Bottom hemisphere: pole to ring
        for (int i = 0; i < sides; i++) {
            int next = (i + 1) % sides;
            indexList.add(botPoleIdx);
            indexList.add(botRing[i]);
            indexList.add(botRing[next]);
        }

        // Cylinder quads (2 triangles per quad)
        for (int ring = 0; ring < 2; ring++) {
            for (int i = 0; i < sides; i++) {
                int next = (i + 1) % sides;
                int v0 = cylRings[ring][i];
                int v1 = cylRings[ring][next];
                int v2 = cylRings[ring + 1][i];
                int v3 = cylRings[ring + 1][next];

                indexList.add(v0);
                indexList.add(v1);
                indexList.add(v2);
                indexList.add(v1);
                indexList.add(v3);
                indexList.add(v2);
            }
        }

        // Top hemisphere: ring to pole
        for (int i = 0; i < sides; i++) {
            int next = (i + 1) % sides;
            indexList.add(topRing[i]);
            indexList.add(topRing[next]);
            indexList.add(topPoleIdx);
        }

        // Connect bottom ring to cylinder bottom
        for (int i = 0; i < sides; i++) {
            int next = (i + 1) % sides;
            indexList.add(botRing[i]);
            indexList.add(cylRings[0][i]);
            indexList.add(botRing[next]);
            indexList.add(botRing[next]);
            indexList.add(cylRings[0][i]);
            indexList.add(cylRings[0][next]);
        }

        // Connect top ring to cylinder top
        for (int i = 0; i < sides; i++) {
            int next = (i + 1) % sides;
            indexList.add(cylRings[2][i]);
            indexList.add(topRing[i]);
            indexList.add(cylRings[2][next]);
            indexList.add(cylRings[2][next]);
            indexList.add(topRing[i]);
            indexList.add(topRing[next]);
        }

        // Convert to arrays
        Vertex[] verts = vertexList.toArray(new Vertex[0]);
        int[] indices = indexList.stream().mapToInt(Integer::intValue).toArray();

        return new Mesh(verts, indices);
    }
}
