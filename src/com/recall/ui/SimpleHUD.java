package com.recall.ui;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.BufferUtils;
import org.joml.Matrix4f;
import java.nio.FloatBuffer;
import com.recall.GameState;
import com.recall.graphics.Shader;
import com.recall.physics.RaycastResult;
import com.recall.util.Logger;

/**
 * SimpleHUD — On-screen HUD with game state overlays, weapon inventory, and health bar.
 *
 * Supports three rendering modes driven by GameState:
 *   MENU    → full-screen title screen (dark overlay, RECALL title, controls)
 *   PLAYING → full HUD (FPS, crosshair, aim info, health bar, weapon inventory)
 *   DEAD    → full-screen death screen (dark-red overlay, YOU DIED, respawn prompt)
 *
 * All geometry is batched into a single VBO upload + glDrawArrays per frame.
 *
 * Text rendering: embedded 5×7 bitmap font, scale-aware. Each font pixel is
 * rendered as a scale×scale screen-pixel quad. Default scale=2 (12px wide chars).
 * Menu/death titles use scale=4 (22px wide chars) for large readable text.
 */
public class SimpleHUD {
    private Shader orthoShader;
    private int glyphVAO;
    private int glyphVBO;

    // Default font scale — each pixel is SCALE×SCALE screen pixels
    private static final int SCALE    = 2;
    private static final int CHAR_W   = 5;   // pixel columns per glyph
    private static final int CHAR_H   = 7;   // pixel rows per glyph
    private static final int CHAR_GAP = 2;   // screen pixels between glyphs

    // Crosshair dimensions
    private static final int CROSSHAIR_ARM   = 8;
    private static final int CROSSHAIR_THICK = 2;

    /** Floats per vertex: 2 pos + 3 color. */
    private static final int STRIDE_FLOATS = 5;
    /** Vertices per quad (two triangles). */
    private static final int QUAD_VERTS    = 6;
    /** Floats per quad. */
    private static final int QUAD_FLOATS   = STRIDE_FLOATS * QUAD_VERTS;

    // Weapon inventory slot dimensions
    private static final float SLOT_W   = 130f;
    private static final float SLOT_H   = 68f;
    private static final float SLOT_GAP = 5f;

    /**
     * 5×7 bitmap font. Each entry is 7 bytes (rows top→bottom).
     * Within each byte, bit 4 = leftmost pixel, bit 0 = rightmost pixel.
     * Indexed by ASCII code — unassigned characters render as blank space.
     */
    private static final byte[][] FONT = new byte[128][];

    static {
        for (int i = 0; i < 128; i++) FONT[i] = new byte[7]; // default = space

        // Digits
        FONT['0'] = new byte[]{ 0x0E, 0x11, 0x13, 0x15, 0x19, 0x11, 0x0E };
        FONT['1'] = new byte[]{ 0x04, 0x0C, 0x04, 0x04, 0x04, 0x04, 0x0E };
        FONT['2'] = new byte[]{ 0x0E, 0x11, 0x01, 0x02, 0x04, 0x08, 0x1F };
        FONT['3'] = new byte[]{ 0x0E, 0x11, 0x01, 0x06, 0x01, 0x11, 0x0E };
        FONT['4'] = new byte[]{ 0x02, 0x06, 0x0A, 0x12, 0x1F, 0x02, 0x02 };
        FONT['5'] = new byte[]{ 0x1F, 0x10, 0x1E, 0x01, 0x01, 0x11, 0x0E };
        FONT['6'] = new byte[]{ 0x06, 0x08, 0x10, 0x1E, 0x11, 0x11, 0x0E };
        FONT['7'] = new byte[]{ 0x1F, 0x01, 0x02, 0x04, 0x08, 0x08, 0x08 };
        FONT['8'] = new byte[]{ 0x0E, 0x11, 0x11, 0x0E, 0x11, 0x11, 0x0E };
        FONT['9'] = new byte[]{ 0x0E, 0x11, 0x11, 0x0F, 0x01, 0x02, 0x0C };

        // Uppercase A–Z
        FONT['A'] = new byte[]{ 0x0E, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11 };
        FONT['B'] = new byte[]{ 0x1E, 0x11, 0x11, 0x1E, 0x11, 0x11, 0x1E };
        FONT['C'] = new byte[]{ 0x0E, 0x11, 0x10, 0x10, 0x10, 0x11, 0x0E };
        FONT['D'] = new byte[]{ 0x1E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x1E };
        FONT['E'] = new byte[]{ 0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x1F };
        FONT['F'] = new byte[]{ 0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x10 };
        FONT['G'] = new byte[]{ 0x0E, 0x11, 0x10, 0x17, 0x11, 0x11, 0x0E };
        FONT['H'] = new byte[]{ 0x11, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11 };
        FONT['I'] = new byte[]{ 0x0E, 0x04, 0x04, 0x04, 0x04, 0x04, 0x0E };
        FONT['J'] = new byte[]{ 0x07, 0x02, 0x02, 0x02, 0x02, 0x12, 0x0C };
        FONT['K'] = new byte[]{ 0x11, 0x12, 0x14, 0x18, 0x14, 0x12, 0x11 };
        FONT['L'] = new byte[]{ 0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x1F };
        FONT['M'] = new byte[]{ 0x11, 0x1B, 0x15, 0x15, 0x11, 0x11, 0x11 };
        FONT['N'] = new byte[]{ 0x11, 0x11, 0x19, 0x15, 0x13, 0x11, 0x11 };
        FONT['O'] = new byte[]{ 0x0E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E };
        FONT['P'] = new byte[]{ 0x1E, 0x11, 0x11, 0x1E, 0x10, 0x10, 0x10 };
        FONT['Q'] = new byte[]{ 0x0E, 0x11, 0x11, 0x11, 0x15, 0x12, 0x0D };
        FONT['R'] = new byte[]{ 0x1E, 0x11, 0x11, 0x1E, 0x14, 0x12, 0x11 };
        FONT['S'] = new byte[]{ 0x0F, 0x10, 0x10, 0x0E, 0x01, 0x01, 0x1E };
        FONT['T'] = new byte[]{ 0x1F, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04 };
        FONT['U'] = new byte[]{ 0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E };
        FONT['V'] = new byte[]{ 0x11, 0x11, 0x11, 0x11, 0x11, 0x0A, 0x04 };
        FONT['W'] = new byte[]{ 0x11, 0x11, 0x11, 0x15, 0x15, 0x15, 0x0A };
        FONT['X'] = new byte[]{ 0x11, 0x11, 0x0A, 0x04, 0x0A, 0x11, 0x11 };
        FONT['Y'] = new byte[]{ 0x11, 0x11, 0x11, 0x0A, 0x04, 0x04, 0x04 };
        FONT['Z'] = new byte[]{ 0x1F, 0x01, 0x02, 0x04, 0x08, 0x10, 0x1F };

        // Punctuation
        FONT[':'] = new byte[]{ 0x00, 0x04, 0x04, 0x00, 0x04, 0x04, 0x00 };
        FONT['.'] = new byte[]{ 0x00, 0x00, 0x00, 0x00, 0x00, 0x0C, 0x0C };
        FONT['-'] = new byte[]{ 0x00, 0x00, 0x00, 0x1F, 0x00, 0x00, 0x00 };
        FONT['/'] = new byte[]{ 0x01, 0x01, 0x02, 0x04, 0x08, 0x10, 0x10 };
        FONT[' '] = new byte[]{ 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
    }

    // Vertex accumulator — grows on demand, reused across frames
    private float[] verts     = new float[16384];
    private int     floatCount;

    // ===== Init =====

    public void init(int windowWidth, int windowHeight) {
        Logger.info("Initializing SimpleHUD");

        // 2D ortho shader
        String vertexSrc =
            "#version 330 core\n" +
            "layout(location = 0) in vec2 position;\n" +
            "layout(location = 1) in vec3 color;\n" +
            "uniform mat4 projection;\n" +
            "out VS_OUT { vec3 color; } vs_out;\n" +
            "void main() {\n" +
            "    gl_Position = projection * vec4(position, 0.0, 1.0);\n" +
            "    vs_out.color = color;\n" +
            "}\n";

        String fragmentSrc =
            "#version 330 core\n" +
            "in VS_OUT { vec3 color; } fs_in;\n" +
            "out vec4 FragColor;\n" +
            "void main() {\n" +
            "    FragColor = vec4(fs_in.color, 1.0);\n" +
            "}\n";

        orthoShader = new Shader(vertexSrc, fragmentSrc);

        // Ortho projection: (0,0) = top-left, (width,height) = bottom-right
        float[] orthoProj = new float[16];
        new Matrix4f()
            .ortho(0, windowWidth, windowHeight, 0, -1, 1)
            .get(orthoProj);
        orthoShader.use();
        orthoShader.setMat4("projection", orthoProj);
        orthoShader.unuse();

        // VAO/VBO for dynamic geometry
        glyphVAO = GL30.glGenVertexArrays();
        glyphVBO = GL30.glGenBuffers();

        GL30.glBindVertexArray(glyphVAO);
        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, glyphVBO);

        // layout: 2 floats pos + 3 floats color = 5 floats = 20 bytes stride
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 20, 0);
        GL20.glVertexAttribPointer(1, 3, GL11.GL_FLOAT, false, 20, 8);
        GL20.glEnableVertexAttribArray(0);
        GL20.glEnableVertexAttribArray(1);

        GL30.glBindVertexArray(0);
        Logger.info("SimpleHUD initialized");
    }

    // ===== Frame Render =====

    /**
     * Draw the HUD for one frame. Content depends on GameState:
     *   MENU    → title screen overlay
     *   PLAYING → full gameplay HUD
     *   DEAD    → death screen overlay
     *
     * @param fps             Current FPS value
     * @param aim             Latest raycast result, or null if aiming at nothing
     * @param notification    Kill notification text, or null/empty if none active
     * @param playerHealth    Player's current HP
     * @param playerMaxHealth Player's maximum HP
     * @param weaponNames     Display names for all 3 weapon slots (e.g. "PISTOL","RIFLE","SHOTGUN")
     * @param ammoTypes       Ammo labels for all 3 slots (e.g. "9MM","5.56MM","12 GAUGE")
     * @param currentSlot     Active weapon slot index (0/1/2)
     * @param gameState       Current game state (drives which overlay to draw)
     * @param windowWidth     Window width in pixels
     * @param windowHeight    Window height in pixels
     */
    public void render(int fps, RaycastResult aim, String notification,
                       float playerHealth, float playerMaxHealth,
                       String[] weaponNames, String[] ammoTypes, int currentSlot,
                       int previewSlot, String[] previewStats,
                       GameState gameState, String netStatus,
                       int windowWidth, int windowHeight) {
        floatCount = 0;

        if (gameState == GameState.MENU) {
            drawMenuScreen(windowWidth, windowHeight);

        } else if (gameState == GameState.WEAPON_SELECT) {
            drawWeaponSelectScreen(weaponNames, previewSlot, previewStats, windowWidth, windowHeight);

        } else if (gameState == GameState.PAUSED) {
            drawPauseMenu(windowWidth, windowHeight);

        } else if (gameState == GameState.DEAD) {
            drawDeathScreen(windowWidth, windowHeight);

        } else {
            // ── PLAYING ──────────────────────────────────────────────────────

            // FPS readout (top-right, white)
            String fpsText = "FPS: " + fps;
            float fpsX = windowWidth - textWidth(fpsText, SCALE) - 10f;
            drawText(fpsText, fpsX, 10f, 1f, 1f, 1f, SCALE);

            // Network status (top-left) — only when networking is active.
            // Colour reflects state: green=connected, yellow=pending, red=lost.
            if (netStatus != null && !netStatus.isEmpty()) {
                float nr = 1f, ng = 0.85f, nb = 0.2f;        // default: yellow (pending)
                if (netStatus.contains("CONNECTED"))      { nr = 0.3f; ng = 1f;   nb = 0.3f; }
                else if (netStatus.contains("LOST"))      { nr = 1f;   ng = 0.3f; nb = 0.3f; }
                drawText(netStatus, 10f, 10f, nr, ng, nb, SCALE);
            }

            // Crosshair (center) — color reflects aim state
            int cx = windowWidth  / 2;
            int cy = windowHeight / 2;
            float cr = 1f, cg = 1f, cb = 1f;
            if (aim != null) {
                if (aim.isHeadshot) { cr = 1f;   cg = 0.3f; cb = 0.3f; }
                else                { cr = 0.3f; cg = 1f;   cb = 0.3f; }
            }
            drawRect(cx - CROSSHAIR_ARM,        cy - CROSSHAIR_THICK / 2f,
                     CROSSHAIR_ARM * 2f,         CROSSHAIR_THICK,           cr, cg, cb);
            drawRect(cx - CROSSHAIR_THICK / 2f, cy - CROSSHAIR_ARM,
                     CROSSHAIR_THICK,            CROSSHAIR_ARM * 2f,        cr, cg, cb);

            // Kill notification (centered, above crosshair, yellow)
            if (notification != null && !notification.isEmpty()) {
                float nx = cx - textWidth(notification, SCALE) / 2f;
                float ny = cy - CROSSHAIR_ARM - 30f;
                drawText(notification, nx, ny, 1f, 0.9f, 0.1f, SCALE);
            }

            // Aim info (centered, below crosshair, white)
            String aimLine;
            if (aim == null) {
                aimLine = "---";
            } else {
                String name = aim.entity.getName().toUpperCase();
                String dist = String.format("%.1fM", aim.distance).replace(',', '.');
                String tag  = aim.isHeadshot ? "HEADSHOT" : "BODY";
                aimLine = name + "  " + dist + "  " + tag;
            }
            float aimX = cx - textWidth(aimLine, SCALE) / 2f;
            float aimY = cy + CROSSHAIR_ARM + 16f;
            drawText(aimLine, aimX, aimY, 1f, 1f, 1f, SCALE);

            // Health bar (bottom-left)
            drawHealthBar(playerHealth, playerMaxHealth,
                          10f, windowHeight - 30f, 200f, 18f);

            // Weapon inventory (bottom-right)
            float invTotalW = weaponNames.length * SLOT_W + (weaponNames.length - 1) * SLOT_GAP;
            float invX = windowWidth  - invTotalW - 10f;
            float invY = windowHeight - SLOT_H    - 12f;
            drawWeaponInventory(weaponNames, ammoTypes, currentSlot, invX, invY);
        }

        // Flush all batched geometry to GPU in one draw call
        if (floatCount == 0) return;

        GL11.glDisable(GL11.GL_DEPTH_TEST);
        orthoShader.use();

        GL30.glBindBuffer(GL30.GL_ARRAY_BUFFER, glyphVBO);
        FloatBuffer fb = BufferUtils.createFloatBuffer(floatCount);
        fb.put(verts, 0, floatCount);
        fb.flip();
        GL30.glBufferData(GL30.GL_ARRAY_BUFFER, fb, GL30.GL_DYNAMIC_DRAW);

        GL30.glBindVertexArray(glyphVAO);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, floatCount / STRIDE_FLOATS);
        GL30.glBindVertexArray(0);

        orthoShader.unuse();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    // ===== Health Bar =====

    /**
     * Draw enhanced health bar: [HP] [background/fill bar] [85/100]
     * Fill color: green (>60%), yellow-orange (30–60%), red (<30%).
     *
     * @param hp    Current health
     * @param maxHp Maximum health
     * @param x     Left edge (pixels from top-left)
     * @param y     Top edge
     * @param w     Bar width in pixels
     * @param h     Bar height in pixels
     */
    private void drawHealthBar(float hp, float maxHp, float x, float y, float w, float h) {
        float pct    = (maxHp > 0f) ? Math.max(0f, Math.min(1f, hp / maxHp)) : 0f;
        float labelH = CHAR_H * SCALE;
        float labelY = y + (h - labelH) / 2f;  // vertically center "HP" label

        // "HP" label (scale=2, white)
        drawText("HP", x, labelY, 1f, 1f, 1f, SCALE);
        float barLeft = x + textWidth("HP", SCALE) + 6f;

        // Dark background
        drawRect(barLeft, y, w, h, 0.2f, 0.2f, 0.2f);

        // Colored fill (1px inset from border)
        float fillW = Math.max(0f, (w - 2f) * pct);
        float[] c   = hpColor(pct);
        drawRect(barLeft + 1f, y + 1f, fillW, h - 2f, c[0], c[1], c[2]);

        // "85/100" text to the right of the bar (vertically centered)
        String hpText = (int) hp + "/" + (int) maxHp;
        drawText(hpText, barLeft + w + 6f, labelY, 1f, 1f, 1f, SCALE);
    }

    /** Returns {r,g,b} health bar fill color based on health fraction 0–1. */
    private float[] hpColor(float pct) {
        if (pct > 0.6f)  return new float[]{ 0.20f, 0.85f, 0.20f };   // green
        if (pct > 0.3f) {
            float t = (pct - 0.3f) / 0.3f;                             // 0→1 as 30%→60%
            return new float[]{ 1f, 0.4f + t * 0.45f, 0f };           // orange→yellow
        }
        return new float[]{ 1f, 0.15f, 0.05f };                        // red
    }

    // ===== Weapon Inventory Panel =====

    /**
     * Draw 3 weapon slots side by side at (startX, startY).
     *
     * @param names      Display names for slots 0/1/2
     * @param ammos      Ammo labels for slots 0/1/2
     * @param activeSlot Which slot is currently selected (0/1/2)
     * @param startX     Left edge of the panel
     * @param startY     Top edge of the panel
     */
    private void drawWeaponInventory(String[] names, String[] ammos, int activeSlot,
                                     float startX, float startY) {
        for (int i = 0; i < names.length; i++) {
            float sx = startX + i * (SLOT_W + SLOT_GAP);
            drawWeaponSlot(names[i], ammos[i], i + 1, i == activeSlot,
                           sx, startY, SLOT_W, SLOT_H);
        }
    }

    /**
     * Draw a single weapon slot box.
     *
     * Active slot:   2px gold border, dark-gray bg, white name, gold ammo label
     * Inactive slot: 1px gray border, darker bg, dim name, dark ammo label
     *
     * @param name    Weapon display name (e.g. "RIFLE")
     * @param ammo    Ammo type label (e.g. "5.56MM")
     * @param slotNum 1-based slot number shown in corner
     * @param active  Whether this slot is currently selected
     * @param x, y    Top-left corner of the slot box
     * @param w, h    Slot dimensions
     */
    private void drawWeaponSlot(String name, String ammo, int slotNum, boolean active,
                                float x, float y, float w, float h) {
        // Outer border
        if (active) {
            drawRect(x, y, w, h, 0.898f, 0.800f, 0.400f);  // gold  #E5CC66
        } else {
            drawRect(x, y, w, h, 0.35f,  0.35f,  0.35f);   // gray
        }

        // Background (inset from border)
        float inset = active ? 2f : 1f;
        float bg    = active ? 0.12f : 0.06f;
        drawRect(x + inset, y + inset, w - inset * 2f, h - inset * 2f, bg, bg, bg);

        // Slot number (top-left corner, scale=1)
        String numStr = String.valueOf(slotNum);
        if (active) {
            drawText(numStr, x + 5f, y + 5f, 0.90f, 0.85f, 0.30f, 1);  // gold-yellow
        } else {
            drawText(numStr, x + 5f, y + 5f, 0.40f, 0.40f, 0.40f, 1);  // dim gray
        }

        // Weapon name centered vertically at ~40% of slot height (scale=1)
        float nameW = textWidth(name, 1);
        float nameX = x + (w - nameW) / 2f;
        float nameY = y + h * 0.38f - (CHAR_H / 2f);
        if (active) {
            drawText(name, nameX, nameY, 1.0f, 1.0f, 1.0f, 1);          // white
        } else {
            drawText(name, nameX, nameY, 0.50f, 0.50f, 0.50f, 1);       // gray
        }

        // Ammo type centered below name (scale=1)
        float ammoW = textWidth(ammo, 1);
        float ammoX = x + (w - ammoW) / 2f;
        float ammoY = nameY + CHAR_H + 5f;
        if (active) {
            drawText(ammo, ammoX, ammoY, 0.898f, 0.800f, 0.400f, 1);    // gold
        } else {
            drawText(ammo, ammoX, ammoY, 0.30f, 0.30f, 0.30f, 1);       // dark gray
        }
    }

    // ===== Menu Screen =====

    /**
     * Full-screen dark-blue title screen.
     *
     * Layout:
     *   Top-third : "RECALL" (scale=4, light-blue/white)
     *   Below     : "A 1V1 FPS GAME" (scale=2, muted)
     *   Center    : "PRESS ENTER TO START" (scale=2, yellow)
     *   Bottom    : Controls reference (scale=1, dark gray)
     */
    private void drawMenuScreen(int w, int h) {
        // Dark blue-black full-screen overlay
        drawRect(0, 0, w, h, 0.02f, 0.02f, 0.06f);

        // "RECALL" title — large, centered, upper-third of screen
        int    ts     = 4;
        String title  = "RECALL";
        float  titleW = textWidth(title, ts);
        float  titleX = (w - titleW) / 2f;
        float  titleY = h / 3f - CHAR_H * ts;
        drawText(title, titleX, titleY, 0.85f, 0.85f, 1.0f, ts);

        // Subtitle below title
        String sub  = "A 1V1 FPS GAME";
        float  subW = textWidth(sub, SCALE);
        float  subY = titleY + CHAR_H * ts + 14f;
        drawText(sub, (w - subW) / 2f, subY, 0.55f, 0.55f, 0.75f, SCALE);

        // "PRESS ENTER TO START" — center screen, yellow
        String prompt  = "PRESS ENTER TO START";
        float  promptW = textWidth(prompt, SCALE);
        float  promptY = h / 2f;
        drawText(prompt, (w - promptW) / 2f, promptY, 1.0f, 1.0f, 0.30f, SCALE);

        // "V - VIEW WEAPONS" — below start prompt, blue-white
        String viewPrompt  = "V - VIEW WEAPONS";
        float  viewPromptW = textWidth(viewPrompt, SCALE);
        float  viewPromptY = promptY + CHAR_H * SCALE + 12f;
        drawText(viewPrompt, (w - viewPromptW) / 2f, viewPromptY, 0.60f, 0.80f, 1.0f, SCALE);

        // Controls reference — bottom strip, scale=1, dim gray
        String[] ctrlLines = {
            "WASD - MOVE    SHIFT - SPRINT",
            "MOUSE - LOOK   LMB - FIRE",
            "1/2/3 - WEAPON SPACE - JUMP"
        };
        float ctrlY = h - ctrlLines.length * (CHAR_H + 6) - 22f;
        for (String line : ctrlLines) {
            float lineW = textWidth(line, 1);
            drawText(line, (w - lineW) / 2f, ctrlY, 0.45f, 0.45f, 0.45f, 1);
            ctrlY += CHAR_H + 6f;
        }
    }

    // ===== Weapon Select Screen =====

    /**
     * Weapon select screen — shows ONLY the 3D weapon model on a dark background.
     * All visual focus is on the gun. Minimal floating text only: weapon name at
     * top-center, tiny navigation hints at bottom. No background panels.
     *
     * @param weaponNames All three weapon names (PISTOL/RIFLE/SHOTGUN)
     * @param slot        Currently selected slot (0/1/2)
     * @param stats       Unused — no stat panels shown (kept for signature compat)
     */
    private void drawWeaponSelectScreen(String[] weaponNames, int slot,
                                        String[] stats, int w, int h) {
        // Weapon name — large, centered near top, clear of the weapon model
        int    ns   = 3;
        String cur  = weaponNames[slot];
        float  curW = textWidth(cur, ns);
        drawText(cur, (w - curW) / 2f, 38f, 1.0f, 1.0f, 1.0f, ns);

        // Navigation hints — small, dim, bottom two lines
        String nav1 = "A / LEFT - PREV       D / RIGHT - NEXT";
        String nav2 = "ENTER - START   ESC - BACK";
        float  nav1W = textWidth(nav1, 1);
        float  nav2W = textWidth(nav2, 1);
        drawText(nav1, (w - nav1W) / 2f, h - 40f, 0.40f, 0.40f, 0.40f, 1);
        drawText(nav2, (w - nav2W) / 2f, h - 26f, 0.40f, 0.40f, 0.40f, 1);
    }

    // ===== Death Screen =====

    /**
     * Full-screen dark-red death overlay.
     *
     * Layout:
     *   Center        : "YOU DIED" (scale=4, bright red)
     *   Below center  : "PRESS ENTER TO RESPAWN" (scale=2, white)
     */
    private void drawDeathScreen(int w, int h) {
        // Dark red full-screen overlay
        drawRect(0, 0, w, h, 0.25f, 0.0f, 0.0f);

        // "YOU DIED" — centered, above middle
        int    ds    = 4;
        String died  = "YOU DIED";
        float  diedW = textWidth(died, ds);
        float  diedX = (w - diedW) / 2f;
        float  diedY = h / 2f - CHAR_H * ds - 10f;
        drawText(died, diedX, diedY, 1.0f, 0.15f, 0.15f, ds);

        // "PRESS ENTER TO RESPAWN" — centered, below "YOU DIED"
        String respawn  = "PRESS ENTER TO RESPAWN";
        float  respawnW = textWidth(respawn, SCALE);
        float  respawnY = diedY + CHAR_H * ds + 20f;
        drawText(respawn, (w - respawnW) / 2f, respawnY, 1.0f, 1.0f, 1.0f, SCALE);
    }

    // ===== Pause Menu Screen =====

    /**
     * Full-screen semi-transparent dark overlay with pause menu options.
     *
     * Layout:
     *   Center    : "PAUSED" (scale=4, white)
     *   Below     : Three menu options with key hints
     *   Bottom    : Footer hint about ESC to pause
     */
    private void drawPauseMenu(int w, int h) {
        // Dark semi-opaque overlay
        drawRect(0, 0, w, h, 0.1f, 0.1f, 0.15f);

        // "PAUSED" title — large, centered
        int    ps    = 4;
        String pause = "PAUSED";
        float  pauseW = textWidth(pause, ps);
        float  pauseX = (w - pauseW) / 2f;
        float  pauseY = h / 3f - CHAR_H * ps;
        drawText(pause, pauseX, pauseY, 1.0f, 1.0f, 1.0f, ps);

        // Menu options — centered, scale=2
        float optY = h / 2f;

        // "ESC / R - RESUME GAME"
        String opt1 = "ESC / R - RESUME GAME";
        float  opt1W = textWidth(opt1, SCALE);
        drawText(opt1, (w - opt1W) / 2f, optY, 0.5f, 1.0f, 0.5f, SCALE);
        optY += CHAR_H * SCALE + 20f;

        // "L - LEAVE GAME"
        String opt2 = "L - LEAVE GAME";
        float  opt2W = textWidth(opt2, SCALE);
        drawText(opt2, (w - opt2W) / 2f, optY, 1.0f, 1.0f, 0.5f, SCALE);
        optY += CHAR_H * SCALE + 20f;

        // "E - EXIT TO DESKTOP"
        String opt3 = "E - EXIT TO DESKTOP";
        float  opt3W = textWidth(opt3, SCALE);
        drawText(opt3, (w - opt3W) / 2f, optY, 1.0f, 0.5f, 0.5f, SCALE);

        // Footer hint (all uppercase — font only supports A-Z)
        String hint = "PRESS ESC TO PAUSE";
        float  hintW = textWidth(hint, 1);
        drawText(hint, (w - hintW) / 2f, h - 40f, 0.4f, 0.4f, 0.4f, 1);
    }

    // ===== Text & Layout Utilities =====

    /**
     * Width in screen pixels that {@code text} occupies at {@code scale}.
     * Accounts for character width + inter-character gap.
     */
    private float textWidth(String text, int scale) {
        return text.length() * (CHAR_W * scale + CHAR_GAP);
    }

    // ===== Drawing Primitives =====

    /**
     * Draw text at the given scale. Each font pixel renders as a scale×scale quad.
     *
     * @param scale 1 = tiny (7px tall), 2 = normal (14px), 4 = large (28px)
     */
    private void drawText(String text, float startX, float startY,
                          float r, float g, float b, int scale) {
        int advance = CHAR_W * scale + CHAR_GAP;
        for (int ci = 0; ci < text.length(); ci++) {
            char   c    = text.charAt(ci);
            byte[] rows = (c < 128) ? FONT[c] : FONT[' '];
            float  ox   = startX + ci * advance;

            for (int row = 0; row < CHAR_H; row++) {
                for (int col = 0; col < CHAR_W; col++) {
                    if ((rows[row] & (1 << (CHAR_W - 1 - col))) == 0) continue;
                    drawRect(ox + col * scale, startY + row * scale, scale, scale, r, g, b);
                }
            }
        }
    }

    /** Draw a solid-color axis-aligned rectangle as two triangles. */
    private void drawRect(float x, float y, float w, float h, float r, float g, float b) {
        ensureCapacity(QUAD_FLOATS);
        // Tri 1: TL, BL, TR
        put(x,     y,     r, g, b);
        put(x,     y + h, r, g, b);
        put(x + w, y,     r, g, b);
        // Tri 2: BL, BR, TR
        put(x,     y + h, r, g, b);
        put(x + w, y + h, r, g, b);
        put(x + w, y,     r, g, b);
    }

    // ===== Vertex Accumulator Helpers =====

    private void ensureCapacity(int additionalFloats) {
        int needed = floatCount + additionalFloats;
        if (needed <= verts.length) return;
        int newLen = verts.length;
        while (newLen < needed) newLen *= 2;
        float[] bigger = new float[newLen];
        System.arraycopy(verts, 0, bigger, 0, floatCount);
        verts = bigger;
    }

    private void put(float x, float y, float r, float g, float b) {
        verts[floatCount++] = x;
        verts[floatCount++] = y;
        verts[floatCount++] = r;
        verts[floatCount++] = g;
        verts[floatCount++] = b;
    }

    // ===== Cleanup =====

    public void cleanup() {
        Logger.info("Cleaning up SimpleHUD");
        if (glyphVAO != 0) GL30.glDeleteVertexArrays(glyphVAO);
        if (glyphVBO != 0) GL30.glDeleteBuffers(glyphVBO);
        if (orthoShader != null) orthoShader.delete();
    }
}
