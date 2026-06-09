package com.recall.weapon;

import com.recall.graphics.Mesh;
import com.recall.graphics.Vertex;
import com.recall.physics.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * RifleModel - Procedural first-person rifle mesh built from simple boxes.
 *
 * Parts:
 *   - Barrel:   thin box pointing forward (−Z axis), dark gray
 *   - Receiver: wider body behind barrel, dark gray
 *   - Stock:    wooden rear section, brown
 *   - Grip:     handle below receiver, dark gray
 *   - Magazine: box below barrel/receiver junction, near-black
 *
 * All vertices are relative to (0,0,0).  The barrel tip is at z = −0.55.
 * WeaponVisuals applies a model matrix each frame to position and orient the
 * rifle so the barrel points along the camera's forward direction.
 *
 * Also builds a small orange unit-cube used for the muzzle flash effect
 * (WeaponVisuals scales it at render time).
 */
public class RifleModel implements WeaponModel {

    /** Distance from model origin to barrel tip along −Z. */
    private static final float BARREL_LEN = 0.55f;

    private Mesh rifleMesh;
    private Mesh flashMesh;

    public RifleModel() {
        rifleMesh = buildRifleMesh();
        flashMesh = buildFlashMesh();
    }

    // -----------------------------------------------------------------------
    // Mesh builders
    // -----------------------------------------------------------------------

    private Mesh buildRifleMesh() {
        List<Vertex>  verts   = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        Vector3 darkGray = new Vector3(0.22f, 0.22f, 0.24f);
        Vector3 brown    = new Vector3(0.48f, 0.30f, 0.14f);
        Vector3 black    = new Vector3(0.12f, 0.12f, 0.15f);

        // Barrel — points forward (−Z), root at z=0, tip at z=−0.55
        addBox(verts, indices,
            -0.025f, -0.025f, -0.55f,
             0.025f,  0.025f,  0.00f,
            darkGray);

        // Receiver — main body, behind barrel
        addBox(verts, indices,
            -0.040f, -0.055f,  0.00f,
             0.040f,  0.045f,  0.22f,
            darkGray);

        // Stock — wooden rear section
        addBox(verts, indices,
            -0.040f, -0.100f,  0.18f,
             0.040f,  0.035f,  0.45f,
            brown);

        // Grip — handle below receiver
        addBox(verts, indices,
            -0.030f, -0.200f,  0.04f,
             0.030f,  0.000f,  0.13f,
            darkGray);

        // Magazine — below barrel/receiver junction
        addBox(verts, indices,
            -0.025f, -0.180f, -0.05f,
             0.025f,  0.000f,  0.05f,
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
    // Box helper
    // -----------------------------------------------------------------------

    /**
     * Append a colored box (24 vertices, 36 indices) to the accumulator lists.
     *
     * Each face gets its own four vertices with a brightness multiplier so the
     * box looks three-dimensional without lighting:
     *   top=1.00  front=0.85  right=0.78  left=0.72  back=0.60  bottom=0.40
     *
     * @param verts   Vertex accumulator (appended in-place)
     * @param indices Index accumulator (appended in-place)
     * @param x0,y0,z0  Min corner
     * @param x1,y1,z1  Max corner
     * @param color     Base colour (0-1 floats); brightness applied per-face
     */
    private void addBox(List<Vertex> verts, List<Integer> indices,
                        float x0, float y0, float z0,
                        float x1, float y1, float z1,
                        Vector3 color) {
        int base = verts.size();

        Vector3 cTop   = color.mul(1.00f);
        Vector3 cBot   = color.mul(0.40f);
        Vector3 cFront = color.mul(0.85f);  // low-z face (toward barrel tip)
        Vector3 cBack  = color.mul(0.60f);  // high-z face
        Vector3 cRight = color.mul(0.78f);  // +X face
        Vector3 cLeft  = color.mul(0.72f);  // −X face

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

        // Front (z0 — toward −Z = barrel tip direction)
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

        // Two triangles per face — indices into the just-added 24 vertices
        for (int face = 0; face < 6; face++) {
            int b = base + face * 4;
            indices.add(b);     indices.add(b + 1); indices.add(b + 2);
            indices.add(b + 2); indices.add(b + 3); indices.add(b);
        }
    }

    // -----------------------------------------------------------------------
    // Getters / cleanup
    // -----------------------------------------------------------------------

    @Override public Mesh  getMesh()         { return rifleMesh; }
    @Override public Mesh  getFlashMesh()    { return flashMesh; }
    @Override public float getBarrelLength() { return BARREL_LEN; }

    // Showcase view — rifle is the reference model; values derived from geometry.
    // Body Z center = (−0.55 + 0.45)/2 = −0.05; body Y center = (−0.20 + 0.045)/2 = −0.0775.
    // After scale(3) → rotX(−15°) → rotY(90°), the visual center displaces by (0.084, −0.265).
    @Override public float getShowcaseScale()   { return 3.0f;  }
    @Override public float getShowcaseCenterX() { return 0.084f; }
    @Override public float getShowcaseCenterY() { return 0.265f; }

    @Override
    public void cleanup() {
        if (rifleMesh != null) rifleMesh.delete();
        if (flashMesh != null) flashMesh.delete();
    }
}
