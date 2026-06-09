package com.recall.weapon;

import com.recall.graphics.Mesh;
import com.recall.graphics.Vertex;
import com.recall.physics.Vector3;

import java.util.ArrayList;
import java.util.List;

/**
 * SniperModel — Procedural first-person sniper rifle mesh.
 *
 * Parts:
 *   - Barrel:   long thin tube pointing forward (−Z), dark gray; tip at z=−0.80
 *   - Receiver: main body block behind barrel, medium gray
 *   - Scope:    rectangular box mounted on top of receiver, near-black
 *   - Stock:    long wooden rear section, brown
 *   - Grip:     handle below receiver, near-black
 */
public class SniperModel implements WeaponModel {

    private static final float BARREL_LEN = 0.80f;

    private Mesh sniperMesh;
    private Mesh flashMesh;

    public SniperModel() {
        sniperMesh = buildSniperMesh();
        flashMesh  = buildFlashMesh();
    }

    private Mesh buildSniperMesh() {
        List<Vertex>  verts   = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        Vector3 darkGray = new Vector3(0.22f, 0.22f, 0.24f);
        Vector3 gray     = new Vector3(0.32f, 0.32f, 0.34f);
        Vector3 black    = new Vector3(0.10f, 0.10f, 0.12f);
        Vector3 brown    = new Vector3(0.42f, 0.28f, 0.14f);

        // Barrel — long thin tube, tip at z=−BARREL_LEN
        addBox(verts, indices,
            -0.025f, -0.025f, -BARREL_LEN,
             0.025f,  0.025f,  0.00f,
            darkGray);

        // Receiver — main body block
        addBox(verts, indices,
            -0.040f, -0.050f,  0.00f,
             0.040f,  0.045f,  0.22f,
            gray);

        // Scope — mounted on top of receiver+barrel
        addBox(verts, indices,
            -0.025f,  0.045f, -0.50f,
             0.025f,  0.090f, -0.10f,
            black);

        // Stock — long wooden rear
        addBox(verts, indices,
            -0.038f, -0.100f,  0.20f,
             0.038f,  0.038f,  0.60f,
            brown);

        // Grip — handle below receiver
        addBox(verts, indices,
            -0.025f, -0.200f,  0.04f,
             0.025f,  0.000f,  0.13f,
            black);

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

    @Override public Mesh  getMesh()         { return sniperMesh; }
    @Override public Mesh  getFlashMesh()    { return flashMesh; }
    @Override public float getBarrelLength() { return BARREL_LEN; }

    @Override public float getShowcaseScale()   { return 2.5f;  }
    @Override public float getShowcaseCenterX() { return 0.12f; }
    @Override public float getShowcaseCenterY() { return 0.30f; }

    @Override
    public void cleanup() {
        if (sniperMesh != null) sniperMesh.delete();
        if (flashMesh  != null) flashMesh.delete();
    }
}
