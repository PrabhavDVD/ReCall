package com.recall.graphics;

/**
 * Color - RGBA color helper
 *
 * Purpose: Simple wrapper for RGBA color values (0-1 range for OpenGL).
 * Makes it easier to work with colors than raw float arrays.
 *
 * Example:
 *   Color white = new Color(1.0f, 1.0f, 1.0f, 1.0f);
 *   Color red = new Color(1.0f, 0.0f, 0.0f, 1.0f);
 */
public class Color {
    public float r, g, b, a;

    public Color(float r, float g, float b, float a) {
        this.r = r;
        this.g = g;
        this.b = b;
        this.a = a;
    }

    public Color(float r, float g, float b) {
        this(r, g, b, 1.0f);
    }

    // Predefined colors
    public static final Color WHITE = new Color(1.0f, 1.0f, 1.0f, 1.0f);
    public static final Color BLACK = new Color(0.0f, 0.0f, 0.0f, 1.0f);
    public static final Color RED = new Color(1.0f, 0.0f, 0.0f, 1.0f);
    public static final Color GREEN = new Color(0.0f, 1.0f, 0.0f, 1.0f);
    public static final Color BLUE = new Color(0.0f, 0.0f, 1.0f, 1.0f);
    public static final Color GRAY = new Color(0.5f, 0.5f, 0.5f, 1.0f);

    @Override
    public String toString() {
        return String.format("Color(%.2f, %.2f, %.2f, %.2f)", r, g, b, a);
    }
}
