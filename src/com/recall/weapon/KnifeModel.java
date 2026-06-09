package com.recall.weapon;

import com.recall.graphics.Mesh;
import com.recall.graphics.Vertex;
import com.recall.physics.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * KnifeModel — Procedural first-person knife mesh.
 *
 * Parts:
 *   - Blade:  thin flat box pointing forward (−Z), silver; tip at z=−0.30
 *   - Guard:  wide crossguard separating blade from handle, dark gray
 *   - Handle: thick grip behind guard, brown
 */
public class KnifeModel implements WeaponModel {

    private static final float BARREL_LEN = 0.30f;  // blade tip

    private Mesh knifeMesh;
    private Mesh flashMesh;

    public KnifeModel() {
        knifeMesh = buildKnifeMesh();
        flashMesh = buildFlashMesh();
    }

    private Mesh buildKnifeMesh() {
        List<Vertex>  verts   = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        Vector3 silver   = new Vector3(0.75f, 0.75f, 0.78f);
        Vector3 darkGray = new Vector3(0.22f, 0.22f, 0.24f);
        Vector3 brown    = new Vector3(0.38f, 0.22f, 0.10f);

        // Blade — thin flat box, tip at z=−BARREL_LEN
        addBox(verts, indices,
            -0.006f,  0.000f, -BARREL_LEN,
             0.006f,  0.022f,  0.00f,
            silver);

        // Guard — crossguard
        addBox(verts, indices,
            -0.038f,  0.000f, -0.018f,
             0.038f,  0.022f,  0.018f,
            darkGray);

        // Handle — thick grip
        addBox(verts, indices,
            -0.018f, -0.005f,  0.018f,
             0.018f,  0.028f,  0.160f,
            brown);

        return new Mesh(
            verts.toArray(new Vertex[0]),
            indices.stream().mapToInt(Integer::intValue).toArray());
    }

    private Mesh buildFlashMesh() {
        List<Vertex>  verts   = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();
        Vector3 orange = new Vector3(1.0f, 0.75f, 0.10f);
        addBox(verts, indices, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, orange);
        return new Mesh(
            verts.toArray(new Vertex[0]),
            indices.stream().mapToInt(Integer::intValue).toArray());
    }

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

        verts.add(new Vertex(new Vector3(x0, y1, z0), cTop));
        verts.add(new Vertex(new Vector3(x1, y1, z0), cTop));
        verts.add(new Vertex(new Vector3(x1, y1, z1), cTop));
        verts.add(new Vertex(new Vector3(x0, y1, z1), cTop));
        verts.add(new Vertex(new Vector3(x0, y0, z0), cBot));
        verts.add(new Vertex(new Vector3(x1, y0, z0), cBot));
        verts.add(new Vertex(new Vector3(x1, y0, z1), cBot));
        verts.add(new Vertex(new Vector3(x0, y0, z1), cBot));
        verts.add(new Vertex(new Vector3(x0, y0, z0), cFront));
        verts.add(new Vertex(new Vector3(x1, y0, z0), cFront));
        verts.add(new Vertex(new Vector3(x1, y1, z0), cFront));
        verts.add(new Vertex(new Vector3(x0, y1, z0), cFront));
        verts.add(new Vertex(new Vector3(x0, y0, z1), cBack));
        verts.add(new Vertex(new Vector3(x1, y0, z1), cBack));
        verts.add(new Vertex(new Vector3(x1, y1, z1), cBack));
        verts.add(new Vertex(new Vector3(x0, y1, z1), cBack));
        verts.add(new Vertex(new Vector3(x1, y0, z0), cRight));
        verts.add(new Vertex(new Vector3(x1, y0, z1), cRight));
        verts.add(new Vertex(new Vector3(x1, y1, z1), cRight));
        verts.add(new Vertex(new Vector3(x1, y1, z0), cRight));
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

    @Override public Mesh  getMesh()         { return knifeMesh; }
    @Override public Mesh  getFlashMesh()    { return flashMesh; }
    @Override public float getBarrelLength() { return BARREL_LEN; }

    @Override public float getShowcaseScale()   { return 7.0f;  }
    @Override public float getShowcaseCenterX() { return 0.00f; }
    @Override public float getShowcaseCenterY() { return 0.20f; }

    @Override
    public void cleanup() {
        if (knifeMesh != null) knifeMesh.delete();
        if (flashMesh != null) flashMesh.delete();
    }
}
