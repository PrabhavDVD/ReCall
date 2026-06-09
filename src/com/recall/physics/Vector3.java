package com.recall.physics;

/**
 * Vector3 - 3D vector with common operations
 *
 * Purpose: Simple 3D vector for position, velocity, direction, etc.
 * Note: For more complex math, JOML would be used in later phases.
 *
 * Example:
 *   Vector3 pos = new Vector3(0, 1, 0);
 *   Vector3 vel = new Vector3(1, 0, 0);
 *   Vector3 newPos = pos.add(vel);
 */
public class Vector3 {
    public float x, y, z;

    public Vector3(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public Vector3(Vector3 other) {
        this.x = other.x;
        this.y = other.y;
        this.z = other.z;
    }

    public Vector3() {
        this.x = 0;
        this.y = 0;
        this.z = 0;
    }

    // ===== Basic Operations =====

    /**
     * Add another vector to this one
     */
    public Vector3 add(Vector3 other) {
        return new Vector3(x + other.x, y + other.y, z + other.z);
    }

    /**
     * Subtract another vector from this one
     */
    public Vector3 sub(Vector3 other) {
        return new Vector3(x - other.x, y - other.y, z - other.z);
    }

    /**
     * Multiply by scalar
     */
    public Vector3 mul(float scalar) {
        return new Vector3(x * scalar, y * scalar, z * scalar);
    }

    /**
     * Divide by scalar
     */
    public Vector3 div(float scalar) {
        return new Vector3(x / scalar, y / scalar, z / scalar);
    }

    // ===== Vector Operations =====

    /**
     * Get length (magnitude) of vector
     */
    public float length() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    /**
     * Get normalized (unit) vector
     */
    public Vector3 normalize() {
        float len = length();
        if (len == 0) return new Vector3(0, 0, 0);
        return new Vector3(x / len, y / len, z / len);
    }

    /**
     * Dot product with another vector
     */
    public float dot(Vector3 other) {
        return x * other.x + y * other.y + z * other.z;
    }

    /**
     * Cross product with another vector
     */
    public Vector3 cross(Vector3 other) {
        return new Vector3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        );
    }

    /**
     * Distance to another point
     */
    public float distance(Vector3 other) {
        return sub(other).length();
    }

    // ===== In-place Operations =====

    /**
     * Add to this vector (modifies this)
     */
    public void addInPlace(Vector3 other) {
        this.x += other.x;
        this.y += other.y;
        this.z += other.z;
    }

    /**
     * Multiply this vector (modifies this)
     */
    public void mulInPlace(float scalar) {
        this.x *= scalar;
        this.y *= scalar;
        this.z *= scalar;
    }

    @Override
    public String toString() {
        return String.format("Vector3(%.2f, %.2f, %.2f)", x, y, z);
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Vector3)) return false;
        Vector3 other = (Vector3) obj;
        return x == other.x && y == other.y && z == other.z;
    }

    // Common vectors
    public static final Vector3 ZERO = new Vector3(0, 0, 0);
    public static final Vector3 ONE = new Vector3(1, 1, 1);
    public static final Vector3 UP = new Vector3(0, 1, 0);
    public static final Vector3 DOWN = new Vector3(0, -1, 0);
    public static final Vector3 RIGHT = new Vector3(1, 0, 0);
    public static final Vector3 LEFT = new Vector3(-1, 0, 0);
    public static final Vector3 FORWARD = new Vector3(0, 0, 1);
    public static final Vector3 BACK = new Vector3(0, 0, -1);
}
