package com.recall.weapon;

import com.recall.graphics.Mesh;
import com.recall.graphics.Vertex;
import com.recall.physics.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * PistolModel - Procedural first-person pistol mesh built from simple boxes.
 *
 * Parts:
 *   - Barrel:        thin, short box pointing forward (−Z), dark gray
 *   - Slide:         slightly wider top portion of the frame (cycling action), slate gray
 *   - Frame:         compact body below the slide, dark gray
 *   - Grip:          handle angled below frame, near-black
 *   - Trigger guard: thin protective bar in front of grip, dark gray
 *
 * All vertices are relative to (0,0,0). Barrel tip at z = −0.25.
 * WeaponVisuals applies a model matrix each frame to position and orient the
 * pistol along the camera's forward direction.
 *
 * Implements WeaponModel so WeaponVisuals can be model-agnostic.
 */
public class PistolModel implements WeaponModel {

    private static final float BARREL_LEN = 0.25f;

    private Mesh pistolMesh;
    private Mesh flashMesh;

    public PistolModel() {
        pistolMesh = buildPistolMesh();
        flashMesh  = buildFlashMesh();
    }

    // -----------------------------------------------------------------------
    // Mesh builders
    // -----------------------------------------------------------------------

    private Mesh buildPistolMesh() {
        List<Vertex>  verts   = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        Vector3 darkGray  = new Vector3(0.22f, 0.22f, 0.24f);
        Vector3 black     = new Vector3(0.12f, 0.12f, 0.15f);
        Vector3 slateGray = new Vector3(0.30f, 0.30f, 0.32f);

        // Barrel — thin, short, points forward (−Z), tip at z=−BARREL_LEN
        addBox(verts, indices,
            -0.020f, -0.020f, -BARREL_LEN,
             0.020f,  0.020f,  0.00f,
            darkGray);

        // Slide — wider top of frame (the reciprocating action piece)
        addBox(verts, indices,
            -0.030f,  0.000f,  0.00f,
             0.030f,  0.040f,  0.12f,
            slateGray);

        // Frame — main compact body below slide
        addBox(verts, indices,
            -0.025f, -0.030f,  0.00f,
             0.025f,  0.000f,  0.10f,
            darkGray);

        // Grip — handle below frame, slightly angled back for visual interest
        addBox(verts, indices,
            -0.022f, -0.160f,  0.02f,
             0.022f,  0.000f,  0.09f,
            black);

        // Trigger guard — thin protective bar in front of grip
        addBox(verts, indices,
            -0.015f, -0.060f, -0.02f,
             0.015f, -0.040f,  0.04f,
            darkGray);

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

    @Override public Mesh  getMesh()         { return pistolMesh; }
    @Override public Mesh  getFlashMesh()    { return flashMesh; }
    @Override public float getBarrelLength() { return BARREL_LEN; }

    // Showcase view.
    // Body Z center = (−0.25 + 0.12)/2 = −0.065; body Y center = (−0.16 + 0.04)/2 = −0.06.
    // After scale(5.5) → rotX(−15°) → rotY(90°), visual center displaces by (0.260, −0.412).
    // Pistol scaled up to 5.5× so it reads clearly at the same display distance as the rifle.
    @Override public float getShowcaseScale()   { return 5.5f;  }
    @Override public float getShowcaseCenterX() { return 0.260f; }
    @Override public float getShowcaseCenterY() { return 0.412f; }

    @Override
    public void cleanup() {
        if (pistolMesh != null) pistolMesh.delete();
        if (flashMesh  != null) flashMesh.delete();
    }
}
