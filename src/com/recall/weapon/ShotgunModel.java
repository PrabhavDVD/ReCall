package com.recall.weapon;

import com.recall.graphics.Mesh;
import com.recall.graphics.Vertex;
import com.recall.physics.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * ShotgunModel - Procedural first-person shotgun mesh built from simple boxes.
 *
 * Parts:
 *   - Barrel:   medium length, slightly wider than rifle, dark gray
 *   - Receiver: wide action body behind barrel, dark gray
 *   - Pump:     fore-end below barrel (pump-action mechanism), dark gray
 *   - Stock:    wide wooden rear section, brown
 *   - Grip:     handle below receiver, near-black
 *
 * All vertices are relative to (0,0,0). Barrel tip at z = −0.40.
 * WeaponVisuals applies a model matrix each frame to position and orient the
 * shotgun along the camera's forward direction.
 *
 * Implements WeaponModel so WeaponVisuals can be model-agnostic.
 */
public class ShotgunModel implements WeaponModel {

    private static final float BARREL_LEN = 0.40f;

    private Mesh shotgunMesh;
    private Mesh flashMesh;

    public ShotgunModel() {
        shotgunMesh = buildShotgunMesh();
        flashMesh   = buildFlashMesh();
    }

    // -----------------------------------------------------------------------
    // Mesh builders
    // -----------------------------------------------------------------------

    private Mesh buildShotgunMesh() {
        List<Vertex>  verts   = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        Vector3 darkGray = new Vector3(0.22f, 0.22f, 0.24f);
        Vector3 brown    = new Vector3(0.48f, 0.30f, 0.14f);
        Vector3 black    = new Vector3(0.12f, 0.12f, 0.15f);

        // Barrel — wider than rifle, medium length, tip at z=−BARREL_LEN
        addBox(verts, indices,
            -0.035f, -0.035f, -BARREL_LEN,
             0.035f,  0.035f,  0.00f,
            darkGray);

        // Receiver — wide action body behind barrel
        addBox(verts, indices,
            -0.050f, -0.060f,  0.00f,
             0.050f,  0.055f,  0.20f,
            darkGray);

        // Pump — sliding fore-end below barrel (distinguishing shotgun silhouette)
        addBox(verts, indices,
            -0.040f, -0.090f, -0.35f,
             0.040f, -0.040f, -0.10f,
            darkGray);

        // Stock — wide wooden rear section
        addBox(verts, indices,
            -0.045f, -0.100f,  0.18f,
             0.045f,  0.040f,  0.50f,
            brown);

        // Grip — handle below receiver
        addBox(verts, indices,
            -0.030f, -0.200f,  0.04f,
             0.030f,  0.000f,  0.14f,
            black);

        return new Mesh(
            verts.toArray(new Vertex[0]),
            indices.stream().mapToInt(Integer::intValue).toArray());
    }

    /** Unit cube centered at origin, bright orange — scaled by WeaponVisuals. */
    private Mesh buildFlashMesh() {
        List<Vertex>  verts   = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        Vector3 orange = new Vector3(1.0f, 0.75f, 0.10f);
        addBox(verts, indices, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, orange);

        return new Mesh(
            verts.toArray(new Vertex[0]),
            indices.stream().mapToInt(Integer::intValue).toArray());
    }

    // -----------------------------------------------------------------------
    // Box helper — identical pattern to RifleModel for consistency
    // -----------------------------------------------------------------------

    /**
     * Append a colored box (24 vertices, 36 indices) to the accumulator lists.
     * Each face gets a brightness multiplier for fake 3D shading (no lighting needed).
     */
    private void addBox(List<Vertex> verts, List<Integer> indices,
                        float x0, float y0, float z0,
                        float x1, float y1, float z1,
                        Vector3 color) {
        int base = verts.size();

        Vector3 cTop   = color.mul(1.00f);
        Vector3 cBot   = color.mul(0.40f);
        Vector3 cFront = color.mul(0.85f);
        Vector3 cBack  = color.mul(0.60f);
        Vector3 cRight = color.mul(0.78f);
        Vector3 cLeft  = color.mul(0.72f);

        // Top (y1)
        verts.add(new Vertex(new Vector3(x0, y1, z0), cTop));
        verts.add(new Vertex(new Vector3(x1, y1, z0), cTop));
        verts.add(new Vertex(new Vector3(x1, y1, z1), cTop));
        verts.add(new Vertex(new Vector3(x0, y1, z1), cTop));
        // Bottom (y0)
        verts.add(new Vertex(new Vector3(x0, y0, z0), cBot));
        verts.add(new Vertex(new Vector3(x1, y0, z0), cBot));
        verts.add(new Vertex(new Vector3(x1, y0, z1), cBot));
        verts.add(new Vertex(new Vector3(x0, y0, z1), cBot));
        // Front (z0 — toward barrel tip / −Z)
        verts.add(new Vertex(new Vector3(x0, y0, z0), cFront));
        verts.add(new Vertex(new Vector3(x1, y0, z0), cFront));
        verts.add(new Vertex(new Vector3(x1, y1, z0), cFront));
        verts.add(new Vertex(new Vector3(x0, y1, z0), cFront));
        // Back (z1)
        verts.add(new Vertex(new Vector3(x0, y0, z1), cBack));
        verts.add(new Vertex(new Vector3(x1, y0, z1), cBack));
        verts.add(new Vertex(new Vector3(x1, y1, z1), cBack));
        verts.add(new Vertex(new Vector3(x0, y1, z1), cBack));
        // Right (+X)
        verts.add(new Vertex(new Vector3(x1, y0, z0), cRight));
        verts.add(new Vertex(new Vector3(x1, y0, z1), cRight));
        verts.add(new Vertex(new Vector3(x1, y1, z1), cRight));
        verts.add(new Vertex(new Vector3(x1, y1, z0), cRight));
        // Left (−X)
        verts.add(new Vertex(new Vector3(x0, y0, z0), cLeft));
        verts.add(new Vertex(new Vector3(x0, y0, z1), cLeft));
        verts.add(new Vertex(new Vector3(x0, y1, z1), cLeft));
        verts.add(new Vertex(new Vector3(x0, y1, z0), cLeft));

        for (int face = 0; face < 6; face++) {
            int b = base + face * 4;
            indices.add(b);     indices.add(b + 1); indices.add(b + 2);
            indices.add(b + 2); indices.add(b + 3); indices.add(b);
        }
    }

    // -----------------------------------------------------------------------
    // WeaponModel interface
    // -----------------------------------------------------------------------

    @Override public Mesh  getMesh()         { return shotgunMesh; }
    @Override public Mesh  getFlashMesh()    { return flashMesh; }
    @Override public float getBarrelLength() { return BARREL_LEN; }

    // Showcase view.
    // Body Z center = (−0.40 + 0.50)/2 = +0.05; body Y center = (−0.20 + 0.055)/2 = −0.0725.
    // After scale(3) → rotX(−15°) → rotY(90°), visual center displaces by (−0.201, −0.171).
    // Negative X: shotgun stock is heavy so the center is right-of-origin before rotation.
    @Override public float getShowcaseScale()   { return 3.0f;   }
    @Override public float getShowcaseCenterX() { return -0.201f; }
    @Override public float getShowcaseCenterY() { return 0.171f;  }

    @Override
    public void cleanup() {
        if (shotgunMesh != null) shotgunMesh.delete();
        if (flashMesh   != null) flashMesh.delete();
    }
}
