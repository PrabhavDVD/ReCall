package com.recall.graphics;

import org.lwjgl.opengl.GL11;
import com.recall.util.Logger;

/**
 * Renderer - OpenGL rendering pipeline manager
 *
 * Purpose: Central rendering class that manages shaders, viewport,
 * and frame clearing. All drawing happens through this class.
 *
 * Design Pattern: Encapsulates OpenGL rendering state
 *
 * Example:
 *   renderer.init(1280, 720);
 *   renderer.beginFrame();
 *   // ... render objects ...
 *   renderer.endFrame();
 */
public class Renderer {
    private Shader shader;
    private Color clearColor;
    private int width;
    private int height;

    public Renderer() {
        clearColor = Color.GRAY;
    }

    /**
     * Initialize renderer with window dimensions
     * Creates shaders and sets up OpenGL state
     */
    public void init(int width, int height) {
        this.width = width;
        this.height = height;

        Logger.info("Initializing renderer: " + width + "x" + height);

        // Set clear color
        GL11.glClearColor(clearColor.r, clearColor.g, clearColor.b, clearColor.a);

        // Enable depth testing
        GL11.glEnable(GL11.GL_DEPTH_TEST);

        // Create shaders
        String vertexSource = getVertexShaderSource();
        String fragmentSource = getFragmentShaderSource();
        shader = new Shader(vertexSource, fragmentSource);

        // Set viewport
        GL11.glViewport(0, 0, width, height);

        Logger.info("Renderer initialized");
    }

    /**
     * Clear the frame buffer
     * Call at start of each frame before rendering
     */
    public void clear() {
        GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
    }

    /**
     * Begin rendering frame
     */
    public void beginFrame() {
        clear();
        shader.use();
    }

    /**
     * End rendering frame
     */
    public void endFrame() {
        shader.unuse();
    }

    /**
     * Set clear color
     */
    public void setClearColor(Color color) {
        clearColor = color;
        GL11.glClearColor(color.r, color.g, color.b, color.a);
    }

    /**
     * Get vertex shader source code
     * Returns basic pass-through vertex shader for Phase 1
     */
    private String getVertexShaderSource() {
        return "#version 330 core\n" +
            "layout(location = 0) in vec3 position;\n" +
            "layout(location = 1) in vec3 color;\n" +
            "\n" +
            "out VS_OUT {\n" +
            "    vec3 color;\n" +
            "} vs_out;\n" +
            "\n" +
            "uniform mat4 projection;\n" +
            "uniform mat4 view;\n" +
            "uniform mat4 model;\n" +
            "\n" +
            "void main() {\n" +
            "    gl_Position = projection * view * model * vec4(position, 1.0);\n" +
            "    vs_out.color = color;\n" +
            "}\n";
    }

    /**
     * Get fragment shader source code
     * Returns basic color output fragment shader
     */
    private String getFragmentShaderSource() {
        return "#version 330 core\n" +
            "in VS_OUT {\n" +
            "    vec3 color;\n" +
            "} fs_in;\n" +
            "\n" +
            "out vec4 FragColor;\n" +
            "\n" +
            "void main() {\n" +
            "    FragColor = vec4(fs_in.color, 1.0);\n" +
            "}\n";
    }

    /**
     * Clean up renderer resources
     */
    public void cleanup() {
        Logger.info("Cleaning up renderer");
        if(shader != null) {
            shader.delete();
        }
    }

    // Getters
    public Shader getShader() { return shader; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
}
