package com.recall.input;

import static org.lwjgl.glfw.GLFW.*;
import com.recall.util.Logger;

/**
 * Input - Keyboard polling and mouse-delta accumulator
 *
 * Purpose: Single place for all raw GLFW input queries.
 *   - Keys are polled directly via glfwGetKey (no buffering needed for held keys).
 *   - Mouse movement is accumulated via a cursor-position callback and then
 *     consumed once per frame by the camera, preventing double-reads.
 *
 * Thread safety: GLFW callbacks fire on the same thread that calls
 * glfwPollEvents(), so no synchronization is needed.
 *
 * Usage:
 *   Input input = new Input(window.getWindowHandle());
 *   input.init();
 *   // --- each frame ---
 *   float dx = input.consumeMouseDeltaX();
 *   float dy = input.consumeMouseDeltaY();
 *   boolean w = input.isKeyDown(GLFW_KEY_W);
 */
public class Input {
    private final long windowHandle;

    // Mouse state
    private float   lastMouseX;
    private float   lastMouseY;
    private float   mouseDeltaX;
    private float   mouseDeltaY;

    // Left mouse button — held state + edge-detection for single-shot fire
    private boolean leftMouseHeld;
    private boolean leftMouseJustPressed;

    /**
     * True until the first cursor-position event fires.
     * Prevents a large jump on the first frame when the cursor is locked.
     */
    private boolean firstMouse;

    public Input(long windowHandle) {
        this.windowHandle = windowHandle;
        this.firstMouse   = true;
        this.mouseDeltaX  = 0f;
        this.mouseDeltaY  = 0f;
    }

    /**
     * Register GLFW callbacks.
     * Must be called AFTER the OpenGL context is current (after window.create()).
     */
    public void init() {
        glfwSetCursorPosCallback(windowHandle, (window, xpos, ypos) -> {
            float x = (float) xpos;
            float y = (float) ypos;

            if (firstMouse) {
                // Seed last position without generating a delta
                lastMouseX = x;
                lastMouseY = y;
                firstMouse = false;
                return;
            }

            // Accumulate — multiple events may fire between frames
            mouseDeltaX += x - lastMouseX;
            mouseDeltaY += lastMouseY - y;  // Invert Y: screen coords increase downward

            lastMouseX = x;
            lastMouseY = y;
        });

        glfwSetMouseButtonCallback(windowHandle, (window, button, action, mods) -> {
            if (button == GLFW_MOUSE_BUTTON_LEFT) {
                leftMouseHeld = (action != GLFW_RELEASE);
                if (action == GLFW_PRESS) leftMouseJustPressed = true;
            }
        });

        Logger.debug("Input callbacks registered");
    }

    // ===== Key Polling =====

    /**
     * Returns true while a key is physically held down.
     * @param key Any GLFW_KEY_* constant
     */
    public boolean isKeyDown(int key) {
        return glfwGetKey(windowHandle, key) == GLFW_PRESS;
    }

    // ===== Mouse Buttons =====

    /** True if left mouse button was pressed this frame. Resets after reading. */
    public boolean consumeLeftMousePressed() {
        boolean was = leftMouseJustPressed;
        leftMouseJustPressed = false;
        return was;
    }

    // ===== Mouse Delta =====

    /**
     * Consume and return accumulated X mouse movement since last call.
     * Positive = moved right.
     * Resets to 0 after reading to prevent double-consumption.
     */
    public float consumeMouseDeltaX() {
        float dx = mouseDeltaX;
        mouseDeltaX = 0f;
        return dx;
    }

    /**
     * Consume and return accumulated Y mouse movement since last call.
     * Positive = moved up (already inverted from screen coordinates).
     * Resets to 0 after reading.
     */
    public float consumeMouseDeltaY() {
        float dy = mouseDeltaY;
        mouseDeltaY = 0f;
        return dy;
    }
}
