package com.recall.graphics;

import com.recall.physics.Vector3;
import com.recall.util.Logger;

/**
 * Map - Checkerboard ground plane mesh for the game world
 *
 * Purpose: Generate a large grid of alternating colored squares so you can
 * perceive distance traveled. Each square is 2×2 units. Colors alternate
 * to create a checkerboard pattern (dark gray and light gray).
 *
 * Design Pattern: Generates mesh once at init, then renders the same mesh
 * every frame. No dynamic updates (yet).
 *
 * Example:
 *   Map map = new Map();
 *   map.init();    // Generate 50×50 grid = 5000 triangles
 *   map.render();
 *   map.cleanup();
 */
public class Map {
    private Mesh mesh;

    /** Grid spans [-HALF_SIZE, HALF_SIZE] in both X and Z */
    private static final float HALF_SIZE = 50f;  // Total 100×100 units
    /** Size of each checkerboard square in world units */
    private static final float SQUARE_SIZE = 2f;

    // Checkerboard colors
    private static final Vector3 DARK_GRAY  = new Vector3(0.3f, 0.3f, 0.3f);
    private static final Vector3 LIGHT_GRAY = new Vector3(0.7f, 0.7f, 0.7f);

    /**
     * Build the checkerboard grid mesh and upload it to the GPU.
     * Must be called after an OpenGL context exists.
     */
    public void init() {
        Logger.info("Creating checkerboard grid (" + (int)(HALF_SIZE * 2) + "x"
                  + (int)(HALF_SIZE * 2) + " units, " + (int)SQUARE_SIZE + "×"
                  + (int)SQUARE_SIZE + " squares)");

        // Calculate grid dimensions
        int gridWidth  = (int)(HALF_SIZE * 2 / SQUARE_SIZE);
        int gridHeight = (int)(HALF_SIZE * 2 / SQUARE_SIZE);

        // Generate vertices and indices
        // We need separate vertices for each square so we can color them independently
        Vertex[] vertices = new Vertex[gridWidth * gridHeight * 4];
        int[] indices     = new int[gridWidth * gridHeight * 6];

        int vertexIndex = 0;
        int indexIndex  = 0;

        for (int z = 0; z < gridHeight; z++) {
            for (int x = 0; x < gridWidth; x++) {
                // Determine checkerboard color for this square
                boolean isDark = ((x + z) % 2) == 0;
                Vector3 color  = isDark ? DARK_GRAY : LIGHT_GRAY;

                // Four corners of this square
                float x0 = -HALF_SIZE + x * SQUARE_SIZE;
                float x1 = x0 + SQUARE_SIZE;
                float z0 = -HALF_SIZE + z * SQUARE_SIZE;
                float z1 = z0 + SQUARE_SIZE;

                // Add 4 vertices for this square (v0, v1, v2, v3)
                int v0 = vertexIndex;
                vertices[vertexIndex++] = new Vertex(new Vector3(x0, 0f, z0), color);
                int v1 = vertexIndex;
                vertices[vertexIndex++] = new Vertex(new Vector3(x1, 0f, z0), color);
                int v2 = vertexIndex;
                vertices[vertexIndex++] = new Vertex(new Vector3(x1, 0f, z1), color);
                int v3 = vertexIndex;
                vertices[vertexIndex++] = new Vertex(new Vector3(x0, 0f, z1), color);

                // Two triangles for this square
                // Triangle 1: v0 → v1 → v2
                indices[indexIndex++] = v0;
                indices[indexIndex++] = v1;
                indices[indexIndex++] = v2;

                // Triangle 2: v0 → v2 → v3
                indices[indexIndex++] = v0;
                indices[indexIndex++] = v2;
                indices[indexIndex++] = v3;
            }
        }

        mesh = new Mesh(vertices, indices);
        Logger.info("Map initialized — " + (gridWidth * gridHeight) + " squares, "
                  + (gridWidth * gridHeight * 2) + " triangles");
    }

    /**
     * Draw the ground plane.
     * Shader must already be bound (renderer.beginFrame handles this).
     */
    public void render() {
        mesh.render();
    }

    /**
     * Release GPU resources.
     */
    public void cleanup() {
        Logger.info("Cleaning up map");
        if (mesh != null) {
            mesh.delete();
        }
    }
}
