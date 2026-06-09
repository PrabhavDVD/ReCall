package com.recall.entity;

import com.recall.graphics.Mesh;
import com.recall.graphics.Vertex;
import com.recall.physics.AABB;
import com.recall.physics.Vector3;
import java.util.ArrayList;
import java.util.List;

/**
 * Dummy - Damageable practice target (capsule/"bean" shape for headshot training).
 *
 * Purpose: A humanoid-shaped target that takes damage and can be destroyed.
 * Inherits health/damage from Entity. Height fixed at 1.8 (player-like).
 * Footprint 0.8×0.8 (thin).
 *
 * Mesh: Procedural capsule with 6 sides (simpler than 8).
 *   - Cylinder body (y: 0.4 to 1.4, radius 0.4)
 *   - Bottom hemisphere (y: 0 to 0.4, radius 0.4)
 *   - Top hemisphere (y: 1.4 to 1.8, radius 0.4)
 */
public class Dummy extends Entity {
    public Dummy(String name, Vector3 position, Vector3 color) {
        super(name, position);

        // AABB: 0.8×1.8×0.8
        float x0 = position.x - 0.4f;
        float x1 = position.x + 0.4f;
        float y0 = position.y;
        float y1 = position.y + 1.8f;
        float z0 = position.z - 0.4f;
        float z1 = position.z + 0.4f;

        bounds = new AABB(
            new Vector3(x0, y0, z0),
            new Vector3(x1, y1, z1));

        // Build capsule mesh
        mesh = buildCapsuleMesh(position, 0.4f, 1.8f, 6, color);
    }

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
}
