package com.recall.util;

/**
 * Timer - Frame timing and delta time calculation
 *
 * Purpose: Provides frame-rate independent timing. Delta time (time since last frame)
 * is essential for smooth movement and physics regardless of frame rate.
 *
 * Example:
 *   timer.update();
 *   float deltaTime = timer.getDeltaTime();
 *   position += velocity * deltaTime; // Movement independent of FPS
 */
public class Timer {
    private double lastTime;
    private double currentTime;
    private double deltaTime;
    private int frameCount;
    private double fpsUpdateTime;
    private int fps;

    public Timer() {
        lastTime = System.nanoTime() / 1_000_000_000.0;
        currentTime = lastTime;
        deltaTime = 0;
        frameCount = 0;
        fpsUpdateTime = 0;
        fps = 0;
    }

    /**
     * Update timer and calculate delta time
     * Call this once per frame, ideally at the start of the game loop
     */
    public void update() {
        currentTime = System.nanoTime() / 1_000_000_000.0;
        deltaTime = currentTime - lastTime;
        lastTime = currentTime;

        // Clamp deltaTime to 100ms max
        // Prevents physics explosion on first frame or after a lag spike
        // (e.g. window dragging, alt-tab, loading)
        if(deltaTime > 0.1) {
            deltaTime = 0.1;
        }

        frameCount++;
        fpsUpdateTime += deltaTime;

        // Update FPS every second
        if(fpsUpdateTime >= 1.0) {
            fps = frameCount;
            frameCount = 0;
            fpsUpdateTime -= 1.0; // Subtract instead of reset to stay accurate
        }
    }

    /**
     * Get time since last frame in seconds
     * Use this to make movement frame-rate independent
     */
    public double getDeltaTime() {
        return deltaTime;
    }

    /**
     * Get current time in seconds since timer creation
     */
    public double getCurrentTime() {
        return currentTime;
    }

    /**
     * Get frames per second (updated every 1 second)
     */
    public int getFPS() {
        return fps;
    }
}
