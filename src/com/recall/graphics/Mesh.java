package com.recall.graphics;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL11;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;

import com.recall.util.Logger;

/**
 * Mesh - 3D mesh data and rendering
 *
 * Purpose: Manages VAO, VBO, EBO for 3D geometry.
 * Vertices are uploaded to GPU and rendered with indices.
 *
 * Design Pattern: Encapsulates OpenGL mesh complexity
 *
 * Example:
 *   Mesh mesh = new Mesh(vertices, indices);
 *   mesh.render();
 *   mesh.delete();
 */
public class Mesh {
    private int VAO; // Vertex Array Object
    private int VBO; // Vertex Buffer Object
    private int EBO; // Element Buffer Object
    private int vertexCount;

    /**
     * Create mesh from vertex and index data
     *
     * @param vertices Array of Vertex objects
     * @param indices Array of triangle indices
     */
    public Mesh(Vertex[] vertices, int[] indices) {
        vertexCount = indices.length;

        Logger.debug("Creating mesh with " + vertices.length + " vertices and " + vertexCount + " indices");

        // Convert vertices to float array
        float[] vertexData = new float[vertices.length * 6]; // 6 floats per vertex (x,y,z,r,g,b)
        for (int i = 0; i < vertices.length; i++) {
            float[] vArray = vertices[i].toArray();
            System.arraycopy(vArray, 0, vertexData, i * 6, 6);
        }

        // Create VAO
        VAO = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(VAO);

        // Create VBO
        VBO = GL15.glGenBuffers();
        FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(vertexData.length);
        vertexBuffer.put(vertexData);
        vertexBuffer.flip();

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, VBO);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vertexBuffer, GL15.GL_STATIC_DRAW);

        // Create EBO
        EBO = GL15.glGenBuffers();
        IntBuffer indexBuffer = BufferUtils.createIntBuffer(indices.length);
        indexBuffer.put(indices);
        indexBuffer.flip();

        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, EBO);
        GL15.glBufferData(GL15.GL_ELEMENT_ARRAY_BUFFER, indexBuffer, GL15.GL_STATIC_DRAW);

        // Vertex attribute pointers
        // Position (location = 0)
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, 24, 0); // 6 floats * 4 bytes = 24 bytes stride
        GL20.glEnableVertexAttribArray(0);

        // Color (location = 1)
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 24, 12); // Offset 12 bytes (3 floats)
        GL20.glEnableVertexAttribArray(1);

        // Unbind VAO
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);

        Logger.info("Mesh created successfully");
    }

    /**
     * Render this mesh
     * VAO and shader program should be set up before calling this
     */
    public void render() {
        GL30.glBindVertexArray(VAO);
        GL11.glDrawElements(GL11.GL_TRIANGLES, vertexCount, GL11.GL_UNSIGNED_INT, 0);
        GL30.glBindVertexArray(0);
    }

    /**
     * Delete mesh and free GPU memory
     */
    public void delete() {
        Logger.debug("Deleting mesh");
        GL15.glDeleteBuffers(VBO);
        GL15.glDeleteBuffers(EBO);
        GL30.glDeleteVertexArrays(VAO);
    }

    // Getters
    public int getVertexCount() { return vertexCount; }
    public int getVAO() { return VAO; }
}
