package com.recall.weapon;

import com.recall.graphics.Mesh;

/**
 * WeaponModel - Interface for first-person weapon mesh providers.
 *
 * Implementations provide the main weapon mesh, muzzle flash mesh,
 * and barrel length used to position the flash effect at the muzzle.
 *
 * Three additional default methods support the weapon-select showcase view:
 *   getShowcaseScale()   — uniform scale to fill ~65% of screen width at dist=2
 *   getShowcaseCenterX() — X offset (world units) to center the gun body horizontally
 *   getShowcaseCenterY() — Y offset (world units) to center the gun body vertically
 *
 * The X/Y offsets compensate for the fact that after rotateY(90°) + rotateX(−15°),
 * the gun's visual center is displaced from the model origin.  Defaults match the
 * Rifle geometry; other models override to precise computed values.
 *
 * Ownership: each WeaponVisuals instance owns one WeaponModel and is
 * responsible for calling cleanup() during its own cleanup phase.
 */
public interface WeaponModel {
    /** The main weapon geometry mesh. */
    Mesh getMesh();

    /** The muzzle flash cube mesh (unit cube, scaled by WeaponVisuals). */
    Mesh getFlashMesh();

    /**
     * Distance from the weapon's model-space origin to the barrel tip,
     * along the −Z axis. WeaponVisuals uses this to place the muzzle flash.
     */
    float getBarrelLength();

    /** Release all GPU resources held by this model. */
    void cleanup();

    // ── Weapon-select showcase parameters ──────────────────────────────────

    /**
     * Uniform scale to apply when rendering in the weapon-select showcase.
     * Chosen so each weapon fills roughly 60-70% of the screen width.
     */
    default float getShowcaseScale()   { return 3.0f; }

    /**
     * World-space X offset added to the showcase position so the gun body
     * appears horizontally centered on screen.  Positive = shift right.
     *
     * Derived from the gun's Z-axis body center (which maps to world X after
     * rotateY(90°) + rotateX(−15°)).
     */
    default float getShowcaseCenterX() { return 0.084f; }

    /**
     * World-space Y offset added to the showcase position so the gun body
     * appears vertically centered on screen.  Positive = shift up.
     *
     * Derived from the gun's Y-axis body center after the showcase rotations.
     */
    default float getShowcaseCenterY() { return 0.265f; }
}
