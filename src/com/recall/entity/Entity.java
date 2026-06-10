package com.recall.entity;

import com.recall.graphics.Mesh;
import com.recall.physics.AABB;
import com.recall.physics.Vector3;

/**
 * Entity - Base class for every collidable / rendered thing in the world.
 *
 * Purpose: A shared interface between the collision system (which only needs
 * an AABB + a name) and the renderer (which needs a mesh + a position). Keeping
 * both on one class avoids a parallel "physics object" / "visual object" split
 * for a game this small.
 *
 * Coordinate contract:
 *   position = the "anchor" of the entity (usually feet, i.e. the bottom-center
 *   of the AABB). Subclasses decide what the anchor means for them.
 *
 * Subclasses must initialize `bounds` and `mesh` in their constructor. `mesh`
 * may be null for purely invisible colliders (triggers, hit boxes inside a
 * composite model, etc.).
 */
public abstract class Entity {
    protected Vector3 position;
    protected AABB    bounds;
    protected Mesh    mesh;
    protected String  name;
    /** True if this entity should be included in raycasts / physics queries */
    protected boolean collidable;

    protected float health    = 100f;
    protected float maxHealth = 100f;

    protected Entity(String name, Vector3 position) {
        this.name       = name;
        this.position   = new Vector3(position);
        this.collidable = true;
    }

    /**
     * Draw this entity. Caller must have the 3D shader bound and
     * projection/view uniforms set for the current frame. Subclasses are
     * responsible for setting the per-entity `model` matrix before drawing.
     * Default implementation just calls mesh.render() with an identity model —
     * override if your entity needs translation / rotation / scale.
     */
    public void render() {
        if (mesh != null) mesh.render();
    }

    /** Free GPU resources held by this entity. Safe to call more than once. */
    public void cleanup() {
        if (mesh != null) {
            mesh.delete();
            mesh = null;
        }
    }

    public void takeDamage(float amount) {
        health = Math.max(0f, health - amount);
    }

    public boolean isDead()       { return health <= 0f; }

    /** Restore health to maximum. */
    public void resetHealth()    { health = maxHealth; }

    // ===== Getters =====
    public Vector3 getPosition() { return position; }
    public AABB    getBounds()   { return bounds; }
    public String  getName()     { return name; }
    public boolean isCollidable(){ return collidable; }
    public float   getHealth()   { return health; }
    public float   getMaxHealth(){ return maxHealth; }
}
