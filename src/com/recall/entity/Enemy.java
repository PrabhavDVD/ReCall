package com.recall.entity;

import com.recall.graphics.Mesh;
import com.recall.graphics.Vertex;
import com.recall.physics.AABB;
import com.recall.physics.Vector3;
import java.util.ArrayList;
import java.util.List;

/**
 * Enemy - AI-controlled hostile entity that walks toward and attacks the player.
 *
 * Behavior:
 *   - Walks toward player at constant speed (3.5 u/s)
 *   - Attacks when within 1.5 units of player (15 HP damage, 1 sec cooldown)
 *   - Takes damage from weapon raycasts (100 HP default)
 *   - Respawns at spawn point when killed
 *
 * Mesh: Red capsule (6-sided "bean" shape, 32 vertices) to distinguish from dummies.
 */
public class Enemy extends Entity {

    private Player targetPlayer;

    // Movement
    private static final float WALK_SPEED = 3.5f;  // units/second toward player

    // Attack
    private static final float ATTACK_RANGE     = 1.5f;  // distance to start attacking
    private static final float ATTACK_DAMAGE    = 15f;   // HP per attack
    private static final float ATTACK_COOLDOWN  = 1.0f;  // seconds between attacks
    private float attackTimer = 0f;

    public Enemy(String name, Vector3 position, Player target) {
        super(name, position);
        this.targetPlayer = target;

        // AABB: same as Dummy (0.8×1.8×0.8)
        float x0 = position.x - 0.4f;
        float x1 = position.x + 0.4f;
        float y0 = position.y;
        float y1 = position.y + 1.8f;
        float z0 = position.z - 0.4f;
        float z1 = position.z + 0.4f;

        bounds = new AABB(
            new Vector3(x0, y0, z0),
            new Vector3(x1, y1, z1));

        // Red capsule mesh built at ORIGIN — translated at render time via model matrix
        Vector3 redHostile = new Vector3(0.9f, 0.2f, 0.2f);
        mesh = buildCapsuleMesh(new Vector3(0f, 0f, 0f), 0.4f, 1.8f, 6, redHostile);
    }

    // -----------------------------------------------------------------------
    // Update behavior
    // -----------------------------------------------------------------------

    /**
     * Update enemy AI — walk toward player, attack when close.
     * Call once per frame from Game.update().
     *
     * @param dt Delta time (seconds)
     */
    public void update(float dt) {
        // --- Walk toward player ---
        Vector3 dirToPlayer = targetPlayer.getPosition().sub(position);
        float distToPlayer = dirToPlayer.length();

        if (distToPlayer > 0.1f) {
            // Move toward player
            Vector3 moveDir = dirToPlayer.normalize();
            position.addInPlace(moveDir.mul(WALK_SPEED * dt));
            // Keep AABB in sync with new position
            updateBounds();
        }

        // --- Attack when in range ---
        if (distToPlayer < ATTACK_RANGE) {
            if (attackTimer <= 0f) {
                // Deal damage to player
                targetPlayer.takeDamage(ATTACK_DAMAGE);
                attackTimer = ATTACK_COOLDOWN;
            }
        }

        // --- Tick attack cooldown ---
        if (attackTimer > 0f) {
            attackTimer -= dt;
        }
    }

    // -----------------------------------------------------------------------
    // Health (inherited behavior, but document it)
    // -----------------------------------------------------------------------

    @Override
    public boolean isDead() {
        // Enemy is dead when health <= 0 (inherited from Entity)
        return super.isDead();
    }

    // -----------------------------------------------------------------------
    // Mesh generation (copied from Dummy, red color)
    // -----------------------------------------------------------------------

    private Mesh buildCapsuleMesh(Vector3 center, float radius, float height, int sides, Vector3 color) {
        List<Vertex> vertexList = new ArrayList<>();
        List<Integer> indexList = new ArrayList<>();

        Vector3 cTop    = color.mul(1.00f);
        Vector3 cBot    = color.mul(0.35f);
        Vector3 cSide   = color.mul(0.75f);

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

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /** Recalculate AABB from current position — call after every move. */
    private void updateBounds() {
        bounds = new AABB(
            new Vector3(position.x - 0.4f, position.y,        position.z - 0.4f),
            new Vector3(position.x + 0.4f, position.y + 1.8f, position.z + 0.4f));
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    public float getDistanceToPlayer() {
        return position.distance(targetPlayer.getPosition());
    }
}
