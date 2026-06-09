package com.recall.graphics;

import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL11;
import java.nio.FloatBuffer;
import org.lwjgl.BufferUtils;

import com.recall.util.Logger;

/**
 * Shader - OpenGL shader program compilation and management
 *
 * Purpose: Compiles vertex and fragment shaders, links them into a program,
 * and provides methods to use and set uniforms.
 *
 * Design Pattern: Encapsulates OpenGL shader complexity
 *
 * Example:
 *   Shader shader = new Shader(vertexSource, fragmentSource);
 *   shader.use();
 *   shader.setMat4("projection", projectionMatrix);
 */
public class Shader {
    private int program;

    /**
     * Create shader from vertex and fragment shader source code
     *
     * @param vertexSource Full GLSL vertex shader source code
     * @param fragmentSource Full GLSL fragment shader source code
     */
    public Shader(String vertexSource, String fragmentSource) {
        Logger.info("Compiling shaders...");

        // Compile vertex and fragment shaders (local - deleted after linking)
        int vertexShader = compileShader(vertexSource, GL20.GL_VERTEX_SHADER);
        if(vertexShader == -1) {
            throw new RuntimeException("Failed to compile vertex shader");
        }

        int fragmentShader = compileShader(fragmentSource, GL20.GL_FRAGMENT_SHADER);
        if(fragmentShader == -1) {
            GL20.glDeleteShader(vertexShader); // Clean up already compiled shader
            throw new RuntimeException("Failed to compile fragment shader");
        }

        // Link program
        program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);

        // Individual shaders no longer needed once linked
        GL20.glDeleteShader(vertexShader);
        GL20.glDeleteShader(fragmentShader);

        // Check for link errors
        if(GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            String error = GL20.glGetProgramInfoLog(program, 512);
            Logger.error("Shader link error: " + error);
            throw new RuntimeException("Failed to link shader program: " + error);
        }

        Logger.info("Shaders compiled and linked successfully");
    }

    /**
     * Compile a single shader
     *
     * @param source GLSL source code
     * @param type GL_VERTEX_SHADER or GL_FRAGMENT_SHADER
     * @return Compiled shader ID, or -1 on error
     */
    private int compileShader(String source, int type) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);

        // Check for compile errors
        if(GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String error = GL20.glGetShaderInfoLog(shader, 512);
            String shaderType = (type == GL20.GL_VERTEX_SHADER) ? "vertex" : "fragment";
            Logger.error("Shader compile error (" + shaderType + "): " + error);
            GL20.glDeleteShader(shader);
            return -1;
        }

        return shader;
    }

    /**
     * Use this shader program for rendering
     */
    public void use() {
        GL20.glUseProgram(program);
    }

    /**
     * Stop using this shader
     */
    public void unuse() {
        GL20.glUseProgram(0);
    }

    /**
     * Set a 4x4 matrix uniform
     *
     * @param name Uniform variable name in shader
     * @param value Matrix value (4x4 = 16 floats)
     */
    public void setMat4(String name, float[] value) {
        int location = GL20.glGetUniformLocation(program, name);
        FloatBuffer buffer = BufferUtils.createFloatBuffer(16);
        buffer.put(value);
        buffer.flip();
        GL20.glUniformMatrix4fv(location, false, buffer);
    }

    /**
     * Set a 3D vector uniform
     */
    public void setVec3(String name, float x, float y, float z) {
        int location = GL20.glGetUniformLocation(program, name);
        GL20.glUniform3f(location, x, y, z);
    }

    /**
     * Set a float uniform
     */
    public void setFloat(String name, float value) {
        int location = GL20.glGetUniformLocation(program, name);
        GL20.glUniform1f(location, value);
    }

    /**
     * Set an int uniform
     */
    public void setInt(String name, int value) {
        int location = GL20.glGetUniformLocation(program, name);
        GL20.glUniform1i(location, value);
    }

    /**
     * Delete shader program and free GPU memory
     */
    public void delete() {
        GL20.glDeleteProgram(program);
        Logger.info("Shader program deleted");
    }

    // Getters
    public int getProgram() { return program; }
}
