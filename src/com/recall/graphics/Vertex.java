package com.recall.graphics;

import com.recall.physics.Vector3;

/**
 * Vertex - Single vertex data structure
 *
 * Purpose: Represents a single vertex with position and color.
 * Can be extended to include normals, texture coordinates, etc.
 *
 * Example:
 *   Vertex v1 = new Vertex(new Vector3(0, 0, 0), new Vector3(1, 1, 1));
 *   float[] data = v1.toArray(); // For GPU upload
 */
public class Vertex {
    public Vector3 position;
    public Vector3 color;

    public Vertex(Vector3 position, Vector3 color) {
        this.position = new Vector3(position);
        this.color = new Vector3(color);
    }

    /**
     * Convert vertex to float array for GPU upload
     * Format: [x, y, z, r, g, b] (6 floats)
     */
    public float[] toArray() {
        return new float[]{
            position.x, position.y, position.z,
            color.x, color.y, color.z
        };
    }

    @Override
    public String toString() {
        return String.format("Vertex(pos=%s, color=%s)", position, color);
    }
}
