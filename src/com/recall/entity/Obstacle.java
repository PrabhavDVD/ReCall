package com.recall.entity;

import com.recall.graphics.Mesh;
import com.recall.graphics.Vertex;
import com.recall.physics.AABB;
import com.recall.physics.Vector3;

/**
 * Obstacle - Non-damageable static obstacle (crates, walls, pillars).
 *
 * Purpose: World geometry that blocks raycasts and movement but never takes damage.
 * Overrides takeDamage() and isDead() to prevent destruction.
 * Persists in the world indefinitely.
 */
public class Obstacle extends Entity {
    public Obstacle(String name, Vector3 position, Vector3 size, Vector3 color) {
        super(name, position);
        this.collidable = true;  // Obstacles block raycasts (but don't take damage)

        float hx = size.x * 0.5f;
        float hz = size.z * 0.5f;

        float x0 = position.x - hx;
        float x1 = position.x + hx;
        float y0 = position.y;
        float y1 = position.y + size.y;
        float z0 = position.z - hz;
        float z1 = position.z + hz;

        Vector3 cTop    = color.mul(1.00f);
        Vector3 cBottom = color.mul(0.35f);
        Vector3 cFront  = color.mul(0.70f);
        Vector3 cBack   = color.mul(0.55f);
        Vector3 cRight  = color.mul(0.85f);
        Vector3 cLeft   = color.mul(0.65f);

        Vertex[] verts = new Vertex[] {
            new Vertex(new Vector3(x0, y1, z0), cTop),
            new Vertex(new Vector3(x1, y1, z0), cTop),
            new Vertex(new Vector3(x1, y1, z1), cTop),
            new Vertex(new Vector3(x0, y1, z1), cTop),
            new Vertex(new Vector3(x0, y0, z0), cBottom),
            new Vertex(new Vector3(x1, y0, z0), cBottom),
            new Vertex(new Vector3(x1, y0, z1), cBottom),
            new Vertex(new Vector3(x0, y0, z1), cBottom),
            new Vertex(new Vector3(x0, y0, z0), cFront),
            new Vertex(new Vector3(x1, y0, z0), cFront),
            new Vertex(new Vector3(x1, y1, z0), cFront),
            new Vertex(new Vector3(x0, y1, z0), cFront),
            new Vertex(new Vector3(x0, y0, z1), cBack),
            new Vertex(new Vector3(x1, y0, z1), cBack),
            new Vertex(new Vector3(x1, y1, z1), cBack),
            new Vertex(new Vector3(x0, y1, z1), cBack),
            new Vertex(new Vector3(x1, y0, z0), cRight),
            new Vertex(new Vector3(x1, y0, z1), cRight),
            new Vertex(new Vector3(x1, y1, z1), cRight),
            new Vertex(new Vector3(x1, y1, z0), cRight),
            new Vertex(new Vector3(x0, y0, z0), cLeft),
            new Vertex(new Vector3(x0, y0, z1), cLeft),
            new Vertex(new Vector3(x0, y1, z1), cLeft),
            new Vertex(new Vector3(x0, y1, z0), cLeft),
        };

        int[] indices = new int[] {
            0, 1, 2,   0, 2, 3,
            4, 6, 5,   4, 7, 6,
            8, 10, 9,   8, 11, 10,
            12, 13, 14,  12, 14, 15,
            16, 17, 18,  16, 18, 19,
            20, 22, 21,  20, 23, 22,
        };

        mesh = new Mesh(verts, indices);

        bounds = new AABB(
            new Vector3(x0, y0, z0),
            new Vector3(x1, y1, z1));
    }

    @Override
    public void takeDamage(float amount) {
        // No-op: obstacles don't take damage
    }

    @Override
    public boolean isDead() {
        return false; // Obstacles never die
    }
}
