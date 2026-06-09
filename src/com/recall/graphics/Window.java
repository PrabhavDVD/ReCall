package com.recall.graphics;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;

import com.recall.Config;
import com.recall.util.Logger;

/**
 * Window - LWJGL window management and OpenGL context creation
 *
 * Purpose: Encapsulates GLFW window creation, event handling, and cleanup.
 * All OpenGL rendering happens in the context created by this window.
 *
 * Example:
 *   Window window = new Window();
 *   window.create();
 *   while(!window.shouldClose()) {
 *       // Render...
 *       window.swapBuffers();
 *   }
 *   window.cleanup();
 */
public class Window {
    private long windowHandle;
    private int width;
    private int height;
    private String title;

    public Window(int width, int height, String title) {
        this.width = width;
        this.height = height;
        this.title = title;
        this.windowHandle = 0;
    }

    /**
     * Initialize GLFW and create the window
     */
    public void create() {
        // Set error callback BEFORE glfwInit so we catch early errors
        glfwSetErrorCallback((error, description) ->
            Logger.error("GLFW Error (" + error + "): " + GLFWErrorCallback.getDescription(description))
        );

        // Initialize GLFW
        if(!glfwInit()) {
            throw new RuntimeException("Failed to initialize GLFW");
        }

        Logger.info("GLFW initialized");

        // Configure GLFW window hints
        glfwDefaultWindowHints();
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);    // Don't show until ready
        glfwWindowHint(GLFW_RESIZABLE, GLFW_FALSE);  // Lock size - resize not handled yet

        // Create window
        windowHandle = glfwCreateWindow(width, height, title, 0, 0);
        if(windowHandle == 0) {
            glfwTerminate();
            throw new RuntimeException("Failed to create GLFW window");
        }

        Logger.info("Window created: " + width + "x" + height);

        // Make OpenGL context current
        glfwMakeContextCurrent(windowHandle);

        // Enable V-Sync if configured
        glfwSwapInterval(Config.VSYNC_ENABLED ? 1 : 0);

        // Create OpenGL capabilities (must be called after context is current)
        GL.createCapabilities();

        Logger.info("OpenGL context created - Version: " + glGetString(GL_VERSION));

        // Set up remaining input callbacks
        setupCallbacks();

        // Show window
        glfwShowWindow(windowHandle);
    }

    /**
     * Set up event callbacks for window
     */
    private void setupCallbacks() {
        // Window close callback
        glfwSetWindowCloseCallback(windowHandle, window -> {
            Logger.info("Window close requested");
        });
    }

    /**
     * Check if window should close (X button clicked or glfwSetWindowShouldClose called).
     * ESC key is handled by Game.update() to show the pause menu instead.
     */
    public boolean shouldClose() {
        return glfwWindowShouldClose(windowHandle);
    }

    /**
     * Lock cursor to window center and hide it (FPS mouse-look mode).
     * GLFW_CURSOR_DISABLED: cursor is hidden AND raw mouse deltas are reported
     * without clamping to window bounds — ideal for free-fly / FPS cameras.
     */
    public void lockCursor() {
        glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_DISABLED);
    }

    /**
     * Release cursor lock and make it visible again (e.g. for a pause menu).
     */
    public void unlockCursor() {
        glfwSetInputMode(windowHandle, GLFW_CURSOR, GLFW_CURSOR_NORMAL);
    }

    /**
     * Poll window events (mouse movement, key presses, etc.)
     */
    public void pollEvents() {
        glfwPollEvents();
    }

    /**
     * Swap front and back buffers to display rendered frame
     */
    public void swapBuffers() {
        glfwSwapBuffers(windowHandle);
    }

    /**
     * Clean up window and terminate GLFW
     */
    public void cleanup() {
        Logger.info("Cleaning up window");

        if(windowHandle != 0) {
            glfwDestroyWindow(windowHandle);
        }

        glfwTerminate();
        Logger.info("GLFW terminated");
    }

    // Getters
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public long getWindowHandle() { return windowHandle; }
}
