package com.recall.ui;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import java.nio.FloatBuffer;
import java.util.List;
import com.recall.entity.Entity;
import com.recall.entity.Obstacle;
import com.recall.graphics.Camera;
import com.recall.graphics.Shader;
import com.recall.util.Logger;

/**
 * WorldHealthBar - Billboard health bars rendered above world entities.
 *
 * Purpose: Renders a two-layer health bar (dark background + colored fill)
 * above every damageable entity. Each bar faces the player camera (cylindrical
 * billboard — rotates around world-Y only so bars stay level).
 *
 * Rendering strategy:
 *   - All bar geometry is built on the CPU each frame using billboard math
 *   - Pre-transformed vertices go into a single dynamic VBO
 *   - One glDrawArrays call draws every bar — no per-entity mesh or model matrix
 *   - Uses the same 3D world shader (projection + view already set by caller)
 *   - Model matrix set to identity before drawing
 *
 * Billboard math (cylindrical, Y-axis only):
 *   toCam  = normalize(camera.xz - bar.xz)
 *   right  = cross(worldUp, toCam) = (toCam.z, 0, -toCam.x)
 *   up     = (0, 1, 0)
 *   corners = center ± right*halfWidth ± up*halfHeight
 *
 * Health color:
 *   pct > 0.5  green → yellow
 *   pct ≤ 0.5  yellow → red
 */
public class WorldHealthBar {

    private int vao;
    private int vbo;

    /** CPU-side vertex buffer, grows on demand. */
    private float[] verts = new float[4096];
    private int     floatCount;

    // ── Bar appearance ───────────────────────────────────────────────────────
    /** Total width of one health bar in world units. */
    private static final float BAR_W      = 0.9f;
    /** Height of one health bar in world units. */
    private static final float BAR_H      = 0.08f;
    /** Gap between the top of the entity AABB and the bottom of the bar. */
    private static final float BAR_OFFSET = 0.3f;

    // ── GPU layout (must match world 3D shader: vec3 pos @ 0, vec3 col @ 1) ─
    private static final int FLOATS_PER_VERT = 6;   // x, y, z, r, g, b
    private static final int STRIDE_BYTES    = 24;  // 6 * 4

    /** Pre-baked column-major identity matrix for the model uniform. */
    private static final float[] IDENTITY = {
        1f,0f,0f,0f,  0f,1f,0f,0f,  0f,0f,1f,0f,  0f,0f,0f,1f
    };

    // =========================================================================
    // Init / cleanup
    // =========================================================================

    public void init() {
        vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);

        vbo = GL15.glGenBuffers();
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);

        // Vertex layout matches Mesh.java: pos(vec3)@loc0, color(vec3)@loc1
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, STRIDE_BYTES, 0);
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, STRIDE_BYTES, 12);
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);

        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);

        Logger.info("WorldHealthBar initialized");
    }

    public void cleanup() {
        if (vao != 0) GL30.glDeleteVertexArrays(vao);
        if (vbo != 0) GL15.glDeleteBuffers(vbo);
    }

    // =========================================================================
    // Per-frame render
    // =========================================================================

    /**
     * Build and draw health bars for all damageable entities.
     *
     * Must be called while the 3D world shader is active and projection + view
     * uniforms are already set for the current frame.
     *
     * @param entities All world entities (Obstacles are skipped automatically)
     * @param camera   Camera used for billboard facing direction
     * @param shader   Active 3D world shader
     */
    public void render(List<Entity> entities, Camera camera, Shader shader) {
        floatCount = 0;

        for (Entity e : entities) {
            if (e instanceof Obstacle) continue;  // walls/crates have no health
            if (e.getBounds() == null)  continue;
            if (!e.isCollidable())      continue;  // hidden/downed (e.g. unconnected RemotePlayer)
            buildBarGeometry(e, camera);
        }

        if (floatCount == 0) return;

        // Identity model: geometry is already in world space
        shader.setMat4("model", IDENTITY);

        // Upload dynamic geometry and draw
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vbo);
        FloatBuffer fb = BufferUtils.createFloatBuffer(floatCount);
        fb.put(verts, 0, floatCount).flip();
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, fb, GL15.GL_DYNAMIC_DRAW);

        GL30.glBindVertexArray(vao);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, floatCount / FLOATS_PER_VERT);
        GL30.glBindVertexArray(0);
    }

    // =========================================================================
    // Geometry helpers
    // =========================================================================

    /**
     * Append two quads (background + health fill) for one entity to the vertex buffer.
     */
    private void buildBarGeometry(Entity e, Camera camera) {
        float maxHp = e.getMaxHealth();
        if (maxHp <= 0f) return;
        float pct = Math.max(0f, Math.min(1f, e.getHealth() / maxHp));

        // ── Bar center position (above entity bounding box) ───────────────────
        float bx = e.getPosition().x;
        float by = e.getBounds().max.y + BAR_OFFSET + BAR_H * 0.5f;
        float bz = e.getPosition().z;

        // ── Cylindrical billboard: camera-facing right axis in XZ ─────────────
        // Direction from bar center to camera, projected onto XZ plane
        float toCamX = camera.getPosition().x - bx;
        float toCamZ = camera.getPosition().z - bz;
        float dist   = (float) Math.sqrt(toCamX * toCamX + toCamZ * toCamZ);
        if (dist < 0.001f) return;  // camera directly above — skip

        // right = cross(worldUp, toCamNorm)  → (toCam.z, 0, -toCam.x)
        float rx = toCamZ / dist;
        float rz = -toCamX / dist;

        float hw = BAR_W * 0.5f;
        float hh = BAR_H * 0.5f;

        // ── Background quad (dark red, full width) ────────────────────────────
        emitQuad(bx, by, bz, rx, rz, hw, hh,
                 0.28f, 0.04f, 0.04f);

        // ── Fill quad (health-colored, left-aligned inside background) ────────
        // Fill center shifts left from background center by (hw - fillHw)
        float fillHw    = hw * pct;
        float shiftX    = rx * (hw - fillHw);
        float shiftZ    = rz * (hw - fillHw);
        float fillHh    = hh - 0.006f;  // 1px inset so background shows as border

        float[] col = healthColor(pct);
        emitQuad(bx - shiftX, by, bz - shiftZ,
                 rx, rz, fillHw, fillHh,
                 col[0], col[1], col[2]);
    }

    /**
     * Emit a world-space billboard quad centered at (cx, cy, cz).
     *
     * @param rx, rz  Camera-facing right axis (XZ only, pre-normalized)
     * @param hw      Half-width along the right axis
     * @param hh      Half-height along world-Y
     */
    private void emitQuad(float cx, float cy, float cz,
                          float rx, float rz,
                          float hw, float hh,
                          float r, float g, float b) {
        if (hw <= 0f) return;  // zero-width quad (0% health fill) — skip

        // Four corners in world space
        float tlX = cx - rx*hw;  float tlY = cy + hh;  float tlZ = cz - rz*hw;
        float trX = cx + rx*hw;  float trY = cy + hh;  float trZ = cz + rz*hw;
        float blX = cx - rx*hw;  float blY = cy - hh;  float blZ = cz - rz*hw;
        float brX = cx + rx*hw;  float brY = cy - hh;  float brZ = cz + rz*hw;

        // Triangle 1: top-left, bottom-left, top-right
        put(tlX, tlY, tlZ, r, g, b);
        put(blX, blY, blZ, r, g, b);
        put(trX, trY, trZ, r, g, b);
        // Triangle 2: bottom-left, bottom-right, top-right
        put(blX, blY, blZ, r, g, b);
        put(brX, brY, brZ, r, g, b);
        put(trX, trY, trZ, r, g, b);
    }

    /**
     * Map a health fraction to an RGB color.
     * green (1.0) → yellow (0.5) → red (0.0)
     *
     * @param pct health fraction [0..1]
     * @return float[3] {r, g, b}
     */
    private float[] healthColor(float pct) {
        if (pct > 0.5f) {
            // Green → Yellow: R rises as health falls
            float t = (1f - pct) * 2f;  // 0 at full health, 1 at 50%
            return new float[]{ t, 1f, 0f };
        } else {
            // Yellow → Red: G drops as health falls
            float t = pct * 2f;          // 1 at 50% health, 0 at empty
            return new float[]{ 1f, t, 0f };
        }
    }

    // =========================================================================
    // Vertex buffer helpers
    // =========================================================================

    private void put(float x, float y, float z, float r, float g, float b) {
        ensureCapacity(FLOATS_PER_VERT);
        verts[floatCount++] = x;
        verts[floatCount++] = y;
        verts[floatCount++] = z;
        verts[floatCount++] = r;
        verts[floatCount++] = g;
        verts[floatCount++] = b;
    }

    private void ensureCapacity(int n) {
        if (floatCount + n <= verts.length) return;
        float[] bigger = new float[verts.length * 2];
        System.arraycopy(verts, 0, bigger, 0, floatCount);
        verts = bigger;
    }
}
