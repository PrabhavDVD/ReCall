package com.recall.entity;

import com.recall.graphics.Mesh;
import com.recall.graphics.Vertex;
import com.recall.physics.AABB;
import com.recall.physics.Vector3;
import com.recall.util.Logger;

/**
 * Box - A solid-colored rectangular prism you can see, walk up to, and shoot.
 *
 * Purpose: The simplest possible concrete Entity. Used for Phase 1.7 test
 * targets (crates, practice dummies) and for blocky world geometry (walls,
 * buildings). In later phases, enemies will share this same AABB contract.
 *
 * Coordinate contract:
 *   position = bottom-center of the box (same convention as Player.position)
 *   size     = (width, height, depth) in world units
 *
 * The mesh is baked in world space at construction — no per-frame model matrix
 * is needed, matching how Map renders. For entities that need to move, we will
 * later add a per-entity model matrix.
 *
 * Faces are drawn with slight per-face brightness variation so the cube reads
 * as 3D even without lighting (top brightest, sides mid, front/back darker).
 *
 * Example:
 *   // A 2×2×2 crate sitting on the ground at (5, 0, -3):
 *   Box crate = new Box("crate-1", new Vector3(5, 0, -3),
 *                       new Vector3(2, 2, 2), new Vector3(0.6f, 0.3f, 0.1f));
 */
public class Box extends Entity {
    private final Vector3 size;
    private final Vector3 color;

    public Box(String name, Vector3 position, Vector3 size, Vector3 color) {
        super(name, position);
        this.size  = new Vector3(size);
        this.color = new Vector3(color);

        // AABB: anchor is bottom-center, extends up by `size.y` and out by ±size/2 in XZ
        float hx = size.x * 0.5f;
        float hz = size.z * 0.5f;
        Vector3 min = new Vector3(position.x - hx, position.y,          position.z - hz);
        Vector3 max = new Vector3(position.x + hx, position.y + size.y, position.z + hz);
        this.bounds = new AABB(min, max);

        this.mesh = buildMesh();
        Logger.debug("Box '" + name + "' bounds=" + bounds);
    }

    /**
     * Build a 24-vertex / 36-index cube mesh in world space.
     *
     * Each face is a unique quad (4 vertices, 2 triangles). Per-face color
     * shading fakes lighting: top brightest, sides mid, front/back slightly
     * darker, bottom darkest.
     */
    private Mesh buildMesh() {
        float x0 = bounds.min.x, x1 = bounds.max.x;
        float y0 = bounds.min.y, y1 = bounds.max.y;
        float z0 = bounds.min.z, z1 = bounds.max.z;

        // Shaded colors per face for readability without actual lighting
        Vector3 cTop    = color.mul(1.00f);  // top:    brightest
        Vector3 cBottom = color.mul(0.35f);  // bottom: darkest
        Vector3 cFront  = color.mul(0.70f);  // -Z face
        Vector3 cBack   = color.mul(0.55f);  // +Z face
        Vector3 cRight  = color.mul(0.85f);  // +X face
        Vector3 cLeft   = color.mul(0.65f);  // -X face

        // 24 vertices: one set of 4 corners per face (no sharing, each face gets its own color)
        Vertex[] verts = new Vertex[] {
            // Top (+Y) — facing up, winding CCW when viewed from above
            new Vertex(new Vector3(x0, y1, z0), cTop),
            new Vertex(new Vector3(x1, y1, z0), cTop),
            new Vertex(new Vector3(x1, y1, z1), cTop),
            new Vertex(new Vector3(x0, y1, z1), cTop),
            // Bottom (-Y)
            new Vertex(new Vector3(x0, y0, z0), cBottom),
            new Vertex(new Vector3(x1, y0, z0), cBottom),
            new Vertex(new Vector3(x1, y0, z1), cBottom),
            new Vertex(new Vector3(x0, y0, z1), cBottom),
            // Front (-Z)
            new Vertex(new Vector3(x0, y0, z0), cFront),
            new Vertex(new Vector3(x1, y0, z0), cFront),
            new Vertex(new Vector3(x1, y1, z0), cFront),
            new Vertex(new Vector3(x0, y1, z0), cFront),
            // Back (+Z)
            new Vertex(new Vector3(x0, y0, z1), cBack),
            new Vertex(new Vector3(x1, y0, z1), cBack),
            new Vertex(new Vector3(x1, y1, z1), cBack),
            new Vertex(new Vector3(x0, y1, z1), cBack),
            // Right (+X)
            new Vertex(new Vector3(x1, y0, z0), cRight),
            new Vertex(new Vector3(x1, y0, z1), cRight),
            new Vertex(new Vector3(x1, y1, z1), cRight),
            new Vertex(new Vector3(x1, y1, z0), cRight),
            // Left (-X)
            new Vertex(new Vector3(x0, y0, z0), cLeft),
            new Vertex(new Vector3(x0, y0, z1), cLeft),
            new Vertex(new Vector3(x0, y1, z1), cLeft),
            new Vertex(new Vector3(x0, y1, z0), cLeft),
        };

        // Two triangles per face. Winding is not critical right now (no face culling),
        // but kept CCW-from-outside so enabling cull later just works.
        int[] indices = new int[] {
            // Top
             0,  1,  2,   0,  2,  3,
            // Bottom
             4,  6,  5,   4,  7,  6,
            // Front (-Z, viewed from -Z looking +Z)
             8, 10,  9,   8, 11, 10,
            // Back (+Z)
            12, 13, 14,  12, 14, 15,
            // Right (+X)
            16, 17, 18,  16, 18, 19,
            // Left (-X)
            20, 22, 21,  20, 23, 22,
        };

        return new Mesh(verts, indices);
    }

    public Vector3 getSize()  { return size; }
    public Vector3 getColor() { return color; }
}
