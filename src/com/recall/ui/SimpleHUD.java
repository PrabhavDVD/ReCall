package com.recall.ui;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.BufferUtils;
import org.joml.Matrix4f;
import java.nio.FloatBuffer;
import com.recall.GameState;
import com.recall.GameTeam;
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

    /** Floats per vertex: 2 pos + 4 rgba. */
    private static final int STRIDE_FLOATS = 6;
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
            "layout(location = 1) in vec4 color;\n" +
            "uniform mat4 projection;\n" +
            "out VS_OUT { vec4 color; } vs_out;\n" +
            "void main() {\n" +
            "    gl_Position = projection * vec4(position, 0.0, 1.0);\n" +
            "    vs_out.color = color;\n" +
            "}\n";

        String fragmentSrc =
            "#version 330 core\n" +
            "in VS_OUT { vec4 color; } fs_in;\n" +
            "out vec4 FragColor;\n" +
            "void main() {\n" +
            "    FragColor = fs_in.color;\n" +
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

        // layout: 2 floats pos + 4 floats rgba = 6 floats = 24 bytes stride
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 24, 0);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, 24, 8);
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
    /**
     * Render the HUD for one frame. Returns an action string if a button was
     * clicked (or null if nothing was activated). The caller (Game) dispatches
     * the action via handleMenuAction().
     */
    public String render(int fps, RaycastResult aim, String notification,
                         float playerHealth, float playerMaxHealth,
                         String[] weaponNames, String[] ammoTypes, int currentSlot,
                         int previewSlot, String[] previewStats,
                         GameState gameState, String netStatus,
                         int mapPreviewSlot, String[] mapNames,
                         String[] mapDescriptions, boolean[] mapAvailable,
                         float duelDummyHp,
                         GameTeam playerTeam,
                         int playerScore, int opponentScore, int winScore, boolean playerWon,
                         int playerRounds, int opponentRounds, int roundsToWin,
                         float roundTimer, boolean playerWonRound,
                         float hitMarkerAlpha, boolean hitMarkerKill,
                         float damageFlashAlpha, float crosshairSpread,
                         float mouseX, float mouseY, boolean mouseClicked,
                         int windowWidth, int windowHeight) {
        floatCount = 0;
        String action = null;

        if (gameState == GameState.MENU) {
            action = drawMenuScreen(mouseX, mouseY, mouseClicked, windowWidth, windowHeight);

        } else if (gameState == GameState.MAP_SELECT) {
            action = drawMapSelectScreen(mapPreviewSlot, mapNames, mapDescriptions, mapAvailable,
                                         mouseX, mouseY, mouseClicked, windowWidth, windowHeight);

        } else if (gameState == GameState.TEAM_SELECT) {
            action = drawTeamSelectScreen(mouseX, mouseY, mouseClicked, windowWidth, windowHeight);

        } else if (gameState == GameState.WEAPON_SELECT) {
            action = drawWeaponSelectScreen(weaponNames, previewSlot,
                                            mouseX, mouseY, mouseClicked, windowWidth, windowHeight);

        } else if (gameState == GameState.PAUSED) {
            action = drawPauseMenu(mouseX, mouseY, mouseClicked, windowWidth, windowHeight);

        } else if (gameState == GameState.DEAD) {
            action = drawDeathScreen(mouseX, mouseY, mouseClicked,
                                     playerScore, opponentScore, windowWidth, windowHeight);

        } else if (gameState == GameState.ROUND_END) {
            action = drawRoundEndScreen(playerWonRound, playerRounds, opponentRounds,
                                        roundsToWin, playerScore, opponentScore,
                                        mouseX, mouseY, mouseClicked, windowWidth, windowHeight);

        } else if (gameState == GameState.MATCH_END) {
            action = drawMatchEndScreen(playerWon, playerScore, opponentScore, winScore,
                                        playerRounds, opponentRounds,
                                        mouseX, mouseY, mouseClicked, windowWidth, windowHeight);

        } else {
            // ── PLAYING ──────────────────────────────────────────────────────

            // Damage flash — translucent red bands at the screen edges when hit
            if (damageFlashAlpha > 0f) {
                float a    = Math.min(0.55f, damageFlashAlpha * 0.55f);
                float band = 70f;   // thickness of the edge vignette
                drawRect(0,                       0,                        windowWidth, band,         0.85f, 0.05f, 0.05f, a);  // top
                drawRect(0,                       windowHeight - band,      windowWidth, band,         0.85f, 0.05f, 0.05f, a);  // bottom
                drawRect(0,                       0,                        band,        windowHeight, 0.85f, 0.05f, 0.05f, a);  // left
                drawRect(windowWidth - band,      0,                        band,        windowHeight, 0.85f, 0.05f, 0.05f, a);  // right
            }

            // FPS readout (top-right, white)
            String fpsText = "FPS: " + fps;
            float fpsX = windowWidth - textWidth(fpsText, SCALE) - 10f;
            drawText(fpsText, fpsX, 10f, 1f, 1f, 1f, SCALE);

            // Score, round info, and timer — Arena only (mapPreviewSlot == 1).
            // Training Ground is a practice range with no opponent or rounds.
            if (mapPreviewSlot == 1) {
                boolean isAlphaTeam = (playerTeam == null || playerTeam == GameTeam.ALPHA);
                float pR = isAlphaTeam ? 0.35f : 1.00f;
                float pG = isAlphaTeam ? 0.60f : 0.30f;
                float pB = isAlphaTeam ? 1.00f : 0.30f;
                float oR = isAlphaTeam ? 1.00f : 0.35f;
                float oG = isAlphaTeam ? 0.30f : 0.60f;
                float oB = isAlphaTeam ? 0.30f : 1.00f;
                int sc = 3;
                String ps  = String.valueOf(playerScore);
                String os2 = String.valueOf(opponentScore);
                String sep = " - ";
                float psW  = textWidth(ps,  sc);
                float sepW = textWidth(sep, sc);
                float osW  = textWidth(os2, sc);
                float totalScoreW = psW + sepW + osW;
                float scoreX = (windowWidth - totalScoreW) / 2f;
                float scoreY = 10f;
                drawText(ps,  scoreX,              scoreY, pR,   pG,   pB,   sc);
                drawText(sep, scoreX + psW,        scoreY, 0.7f, 0.7f, 0.7f, sc);
                drawText(os2, scoreX + psW + sepW, scoreY, oR,   oG,   oB,   sc);

                // Round + series indicator below kill score
                int    roundNum = playerRounds + opponentRounds + 1;
                String rdLine   = "ROUND " + roundNum + "   " + playerRounds + "-" + opponentRounds + "  SERIES";
                float  rdLineW  = textWidth(rdLine, 1);
                drawText(rdLine, (windowWidth - rdLineW) / 2f, scoreY + CHAR_H * sc + 4f,
                         0.45f, 0.45f, 0.50f, 1);

                // Round timer — top-right, below FPS
                if (roundTimer >= 0f) {
                    int    tMins    = (int)(roundTimer / 60f);
                    int    tSecs    = (int)(roundTimer % 60f);
                    String timerStr = tMins + ":" + (tSecs < 10 ? "0" : "") + tSecs;
                    float  timerX   = windowWidth - textWidth(timerStr, SCALE) - 10f;
                    float  timerY   = 10f + CHAR_H * SCALE + 8f;
                    float  tmR, tmG, tmB;
                    if      (roundTimer > 60f) { tmR = 1f;    tmG = 1f;    tmB = 1f;    }
                    else if (roundTimer > 30f) { tmR = 1f;    tmG = 0.85f; tmB = 0.15f; }
                    else                       { tmR = 1f;    tmG = 0.20f; tmB = 0.20f; }
                    drawText(timerStr, timerX, timerY, tmR, tmG, tmB, SCALE);
                }
            }

            // Network status (top-left) — only when networking is active.
            // Colour reflects state: green=connected, yellow=pending, red=lost.
            float topLeftY = 10f;   // running y for stacked top-left indicators
            if (netStatus != null && !netStatus.isEmpty()) {
                float nr = 1f, ng = 0.85f, nb = 0.2f;        // default: yellow (pending)
                if (netStatus.contains("CONNECTED"))      { nr = 0.3f; ng = 1f;   nb = 0.3f; }
                else if (netStatus.contains("LOST"))      { nr = 1f;   ng = 0.3f; nb = 0.3f; }
                drawText(netStatus, 10f, topLeftY, nr, ng, nb, SCALE);
                topLeftY += CHAR_H * SCALE + 6f;
            }

            // Duel dummy HP (Phase 2.4) — shown top-left when the dummy is alive.
            if (duelDummyHp >= 0f) {
                float pct = duelDummyHp / 100f;
                float[] dc = hpColor(pct);
                drawText("OPPONENT HP: " + (int) duelDummyHp + "/100",
                         10f, topLeftY, dc[0], dc[1], dc[2], SCALE);
                topLeftY += CHAR_H * SCALE + 6f;
            }

            // Team indicator (Phase 2.7) — Arena only; Training Ground has no teams
            if (mapPreviewSlot == 1) {
                boolean isAlpha = (playerTeam == null || playerTeam == GameTeam.ALPHA);
                float tR = isAlpha ? 0.35f : 1.00f;
                float tG = isAlpha ? 0.60f : 0.30f;
                float tB = isAlpha ? 1.00f : 0.30f;
                drawText("TEAM: " + (isAlpha ? "ALPHA" : "BRAVO"),
                         10f, topLeftY, tR, tG, tB, SCALE);
            }

            // Crosshair (center) — 4 arms with a center gap that blooms with recoil.
            // Color reflects aim state: white=none, green=body, red=headshot.
            int cx = windowWidth  / 2;
            int cy = windowHeight / 2;
            float cr = 1f, cg = 1f, cb = 1f;
            if (aim != null) {
                if (aim.isHeadshot) { cr = 1f;   cg = 0.3f; cb = 0.3f; }
                else                { cr = 0.3f; cg = 1f;   cb = 0.3f; }
            }
            float gap   = 4f + Math.max(0f, crosshairSpread);   // px from center to each arm
            float arm   = CROSSHAIR_ARM;
            float thick = CROSSHAIR_THICK;
            // Left / Right arms
            drawRect(cx - gap - arm,    cy - thick / 2f, arm, thick, cr, cg, cb);
            drawRect(cx + gap,          cy - thick / 2f, arm, thick, cr, cg, cb);
            // Top / Bottom arms
            drawRect(cx - thick / 2f,   cy - gap - arm,  thick, arm, cr, cg, cb);
            drawRect(cx - thick / 2f,   cy + gap,        thick, arm, cr, cg, cb);

            // Hitmarker — 4 diagonal ticks that flash when a shot lands (red on kill)
            if (hitMarkerAlpha > 0f) {
                float hmR = 1.0f;
                float hmG = hitMarkerKill ? 0.2f : 1.0f;
                float hmB = hitMarkerKill ? 0.2f : 1.0f;
                float a   = Math.min(1f, hitMarkerAlpha);
                float d   = 9f;    // distance from center to inner end of each tick
                float len = 6f;    // tick length
                float tk  = 2f;    // tick thickness
                // Draw as small squares stepped along the diagonal (axis-aligned approximation)
                for (int i = 0; i < (int) len; i++) {
                    float off = d + i;
                    // top-left, top-right, bottom-left, bottom-right
                    drawRect(cx - off - tk, cy - off - tk, tk, tk, hmR, hmG, hmB, a);
                    drawRect(cx + off,      cy - off - tk, tk, tk, hmR, hmG, hmB, a);
                    drawRect(cx - off - tk, cy + off,      tk, tk, hmR, hmG, hmB, a);
                    drawRect(cx + off,      cy + off,      tk, tk, hmR, hmG, hmB, a);
                }
            }

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
        if (floatCount > 0) {
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
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
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }

        return action;
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
        // Low-health pulse — fill brightness oscillates when below 30%
        if (pct <= 0.3f && pct > 0f) {
            float t     = (float)(0.5 + 0.5 * Math.sin(System.nanoTime() / 1.4e8));  // ~0..1
            float boost = 0.55f + 0.45f * t;
            c = new float[]{ c[0] * boost + (1f - boost), c[1] * boost, c[2] * boost };
        }
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

    // ===== Button =====

    /**
     * Draw a bordered button with hover highlight.
     *
     * @param label       Text displayed inside the button (A-Z 0-9 only)
     * @param actionId    String returned when the button is clicked
     * @param x, y        Top-left corner of the button
     * @param w, h        Button dimensions
     * @param mouseX/Y    Current cursor position for hover detection
     * @param clicked     True if the left mouse button was pressed this frame
     * @param r, g, b     Accent colour (border + hover background tint)
     * @return actionId if clicked, null otherwise
     */
    private String drawButton(String label, String actionId,
                              float x, float y, float w, float h,
                              float mouseX, float mouseY, boolean clicked,
                              float r, float g, float b) {
        boolean hovered = mouseX >= x && mouseX <= x + w
                       && mouseY >= y && mouseY <= y + h;

        // Background — dark base, tinted on hover
        float bgR = hovered ? r * 0.20f : 0.07f;
        float bgG = hovered ? g * 0.20f : 0.07f;
        float bgB = hovered ? b * 0.20f : 0.10f;
        drawRect(x, y, w, h, bgR, bgG, bgB);

        // Border — bright accent on hover, dim otherwise
        float bR = hovered ? r        : r * 0.40f;
        float bG = hovered ? g        : g * 0.40f;
        float bB = hovered ? b        : b * 0.40f;
        float bw = hovered ? 2f : 1.5f;
        drawRect(x,          y,          w,  bw, bR, bG, bB);   // top
        drawRect(x,          y + h - bw, w,  bw, bR, bG, bB);   // bottom
        drawRect(x,          y,          bw, h,  bR, bG, bB);   // left
        drawRect(x + w - bw, y,          bw, h,  bR, bG, bB);   // right

        // Label centered in button
        float tw = textWidth(label, SCALE);
        float tx = x + (w - tw) / 2f;
        float ty = y + (h - CHAR_H * SCALE) / 2f;
        float tr = hovered ? 1.0f : 0.85f;
        drawText(label, tx, ty, tr, tr, tr, SCALE);

        return (hovered && clicked) ? actionId : null;
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
    private String drawMenuScreen(float mouseX, float mouseY, boolean clicked, int w, int h) {
        // Dark blue-black overlay
        drawRect(0, 0, w, h, 0.02f, 0.02f, 0.06f);

        // "RECALL" title
        int    ts     = 4;
        String title  = "RECALL";
        float  titleW = textWidth(title, ts);
        float  titleX = (w - titleW) / 2f;
        float  titleY = h / 3f - CHAR_H * ts;
        drawText(title, titleX, titleY, 0.85f, 0.85f, 1.0f, ts);

        // Subtitle
        String sub  = "A 1V1 FPS GAME";
        float  subW = textWidth(sub, SCALE);
        float  subY = titleY + CHAR_H * ts + 14f;
        drawText(sub, (w - subW) / 2f, subY, 0.55f, 0.55f, 0.75f, SCALE);

        // Buttons — centered, stacked
        float btnW = 220f, btnH = 32f, btnGap = 10f;
        float btnX = (w - btnW) / 2f;
        float btnY = h * 0.50f;

        String action = null;
        String r;

        r = drawButton("PLAY", "START",
                       btnX, btnY, btnW, btnH, mouseX, mouseY, clicked,
                       0.90f, 0.90f, 0.90f);
        if (r != null) action = r;

        r = drawButton("LOADOUT", "WEAPON_SELECT",
                       btnX, btnY + (btnH + btnGap), btnW, btnH, mouseX, mouseY, clicked,
                       0.60f, 0.80f, 1.00f);
        if (r != null) action = r;

        // Navigation hint just below the buttons
        String navHint  = "ENTER - PLAY    V - LOADOUT";
        float  navHintW = textWidth(navHint, 1);
        drawText(navHint, (w - navHintW) / 2f, btnY + (btnH + btnGap) * 2f + 6f,
                 0.40f, 0.40f, 0.45f, 1);

        // Controls strip — bottom, dim
        String[] ctrlLines = {
            "WASD - MOVE    SHIFT - SPRINT    CTRL - CROUCH",
            "MOUSE - LOOK   LMB - FIRE        SPACE - JUMP",
            "1-5 - SWITCH WEAPON"
        };
        float ctrlY = h - ctrlLines.length * (CHAR_H + 6) - 22f;
        for (String line : ctrlLines) {
            float lineW = textWidth(line, 1);
            drawText(line, (w - lineW) / 2f, ctrlY, 0.40f, 0.40f, 0.40f, 1);
            ctrlY += CHAR_H + 6f;
        }

        return action;
    }

    // ===== Weapon Select Screen =====

    /**
     * Weapon select screen — shows the 3D weapon model with mouse-clickable navigation.
     * Weapon name at top-center; PREV/NEXT/SELECT/BACK buttons at bottom.
     */
    private String drawWeaponSelectScreen(String[] weaponNames, int slot,
                                          float mouseX, float mouseY, boolean clicked,
                                          int w, int h) {
        // Weapon name — large, centered near top
        int    ns   = 3;
        String cur  = weaponNames[slot];
        float  curW = textWidth(cur, ns);
        drawText(cur, (w - curW) / 2f, 38f, 1.0f, 1.0f, 1.0f, ns);

        // Slot indicator (e.g. "2/5") — dim, centered below name
        String ind  = (slot + 1) + "/" + weaponNames.length;
        float  indW = textWidth(ind, 1);
        drawText(ind, (w - indW) / 2f, 38f + CHAR_H * ns + 10f, 0.45f, 0.45f, 0.45f, 1);

        // Buttons — row near bottom
        float btnH   = 32f;
        float btnW   = 150f;
        float btnGap = 10f;
        float totalW = btnW * 4 + btnGap * 3;
        float startX = (w - totalW) / 2f;
        float btnY   = h - btnH - 48f;

        String action = null;
        String r;

        r = drawButton("PREV", "WEAPON_PREV",
                       startX, btnY, btnW, btnH, mouseX, mouseY, clicked,
                       0.70f, 0.70f, 0.70f);
        if (r != null) action = r;

        r = drawButton("NEXT", "WEAPON_NEXT",
                       startX + (btnW + btnGap), btnY, btnW, btnH, mouseX, mouseY, clicked,
                       0.70f, 0.70f, 0.70f);
        if (r != null) action = r;

        r = drawButton("SELECT", "WEAPON_CONFIRM",
                       startX + (btnW + btnGap) * 2f, btnY, btnW, btnH, mouseX, mouseY, clicked,
                       0.30f, 1.00f, 0.40f);
        if (r != null) action = r;

        r = drawButton("BACK", "MENU",
                       startX + (btnW + btnGap) * 3f, btnY, btnW, btnH, mouseX, mouseY, clicked,
                       0.90f, 0.40f, 0.40f);
        if (r != null) action = r;

        return action;
    }

    // ===== Map Select Screen =====

    /**
     * Map select screen — dark background with map name, description, availability,
     * and mouse-clickable PREV/NEXT/SELECT/BACK buttons.
     */
    private String drawMapSelectScreen(int slot, String[] names, String[] descriptions,
                                       boolean[] available,
                                       float mouseX, float mouseY, boolean clicked,
                                       int w, int h) {
        // Dark overlay
        drawRect(0, 0, w, h, 0.02f, 0.02f, 0.06f);

        // "MAP SELECT" header
        String header  = "MAP SELECT";
        float  headerW = textWidth(header, SCALE);
        drawText(header, (w - headerW) / 2f, 32f, 0.55f, 0.55f, 0.75f, SCALE);

        // Map name — large, centered; white if available, dim if locked
        int     ns    = 3;
        String  name  = names[slot];
        boolean avail = available[slot];
        float   nameW = textWidth(name, ns);
        float   nameR = avail ? 1.0f : 0.45f;
        float   nameG = avail ? 1.0f : 0.45f;
        float   nameB = avail ? 1.0f : 0.45f;
        float   nameY = h / 3f - CHAR_H * ns;
        drawText(name, (w - nameW) / 2f, nameY, nameR, nameG, nameB, ns);

        // Description
        String desc  = descriptions[slot];
        float  descW = textWidth(desc, 1);
        float  descY = nameY + CHAR_H * ns + 18f;
        drawText(desc, (w - descW) / 2f, descY, 0.50f, 0.50f, 0.55f, 1);

        // Status line
        String status  = avail ? "STATUS: AVAILABLE" : "STATUS: COMING SOON";
        float  statusW = textWidth(status, SCALE);
        float  statusY = h / 2f;
        float  sr      = avail ? 0.30f : 0.90f;
        float  sg      = avail ? 1.00f : 0.35f;
        float  sb      = avail ? 0.30f : 0.10f;
        drawText(status, (w - statusW) / 2f, statusY, sr, sg, sb, SCALE);

        // Slot indicator
        String ind  = (slot + 1) + "/" + names.length;
        float  indW = textWidth(ind, 1);
        drawText(ind, (w - indW) / 2f, statusY + CHAR_H * SCALE + 12f, 0.40f, 0.40f, 0.40f, 1);

        // Buttons
        float btnH   = 32f;
        float btnW   = 150f;
        float btnGap = 10f;
        float totalW = btnW * 4 + btnGap * 3;
        float startX = (w - totalW) / 2f;
        float btnY   = h - btnH - 48f;

        String action = null;
        String r;

        r = drawButton("PREV", "MAP_PREV",
                       startX, btnY, btnW, btnH, mouseX, mouseY, clicked,
                       0.70f, 0.70f, 0.70f);
        if (r != null) action = r;

        r = drawButton("NEXT", "MAP_NEXT",
                       startX + (btnW + btnGap), btnY, btnW, btnH, mouseX, mouseY, clicked,
                       0.70f, 0.70f, 0.70f);
        if (r != null) action = r;

        // SELECT is dimmed and non-functional when map is locked
        if (avail) {
            r = drawButton("SELECT", "MAP_CONFIRM",
                           startX + (btnW + btnGap) * 2f, btnY, btnW, btnH, mouseX, mouseY, clicked,
                           0.30f, 1.00f, 0.40f);
            if (r != null) action = r;
        } else {
            // Draw a disabled SELECT button (no click response)
            drawButton("SELECT", null,
                       startX + (btnW + btnGap) * 2f, btnY, btnW, btnH, -1f, -1f, false,
                       0.30f, 0.30f, 0.30f);
        }

        r = drawButton("BACK", "MENU",
                       startX + (btnW + btnGap) * 3f, btnY, btnW, btnH, mouseX, mouseY, clicked,
                       0.90f, 0.40f, 0.40f);
        if (r != null) action = r;

        return action;
    }

    // ===== Team Select Screen =====

    /**
     * Full-screen team selection — two large colored cards (ALPHA/BRAVO) side by side.
     * Clicking a card sets the team and starts the match immediately.
     */
    private String drawTeamSelectScreen(float mouseX, float mouseY, boolean clicked, int w, int h) {
        drawRect(0, 0, w, h, 0.02f, 0.02f, 0.06f);

        // Header
        String header  = "SELECT YOUR TEAM";
        float  headerW = textWidth(header, SCALE);
        drawText(header, (w - headerW) / 2f, 38f, 0.55f, 0.55f, 0.75f, SCALE);

        // Two large team cards side by side
        float cardW = 240f, cardH = 140f, cardGap = 40f;
        float startX = (w - (cardW * 2 + cardGap)) / 2f;
        float cardY  = h / 2f - cardH / 2f - 20f;

        String action = null;
        String r;

        // ALPHA card — blue
        r = drawButton("ALPHA", "TEAM_ALPHA",
                       startX, cardY, cardW, cardH,
                       mouseX, mouseY, clicked,
                       0.35f, 0.60f, 1.00f);
        if (r != null) action = r;
        // Subtext below the card label
        String aSub  = "BLUE TEAM";
        float  aSubW = textWidth(aSub, 1);
        drawText(aSub, startX + (cardW - aSubW) / 2f, cardY + cardH * 0.70f,
                 0.35f, 0.60f, 1.00f, 1);

        // BRAVO card — red
        float cardX2 = startX + cardW + cardGap;
        r = drawButton("BRAVO", "TEAM_BRAVO",
                       cardX2, cardY, cardW, cardH,
                       mouseX, mouseY, clicked,
                       1.00f, 0.30f, 0.30f);
        if (r != null) action = r;
        String bSub  = "RED TEAM";
        float  bSubW = textWidth(bSub, 1);
        drawText(bSub, cardX2 + (cardW - bSubW) / 2f, cardY + cardH * 0.70f,
                 1.00f, 0.30f, 0.30f, 1);

        // Back button
        float backW = 150f, backH = 32f;
        float backX = (w - backW) / 2f;
        float backY = cardY + cardH + 24f;
        r = drawButton("BACK", "TEAM_BACK",
                       backX, backY, backW, backH,
                       mouseX, mouseY, clicked,
                       0.70f, 0.70f, 0.70f);
        if (r != null) action = r;

        // Keyboard hint
        String hint  = "1 - ALPHA   2 - BRAVO   ESC - BACK";
        float  hintW = textWidth(hint, 1);
        drawText(hint, (w - hintW) / 2f, h - 30f, 0.35f, 0.35f, 0.35f, 1);

        return action;
    }

    // ===== Match End Screen =====

    /**
     * Full-screen match result overlay — YOU WIN (green) or YOU LOSE (red).
     * Shows final score and offers PLAY AGAIN / MAIN MENU.
     */
    private String drawMatchEndScreen(boolean playerWon, int playerScore, int opponentScore,
                                      int winScore,
                                      int playerRounds, int opponentRounds,
                                      float mouseX, float mouseY, boolean clicked,
                                      int w, int h) {
        // Overlay tint — dark green if win, dark red if loss
        if (playerWon) drawRect(0, 0, w, h, 0.02f, 0.12f, 0.02f);
        else           drawRect(0, 0, w, h, 0.12f, 0.02f, 0.02f);

        // Main result text
        int    ts     = 4;
        String result = playerWon ? "YOU WIN" : "YOU LOSE";
        float  rR     = playerWon ? 0.30f : 1.00f;
        float  rG     = playerWon ? 1.00f : 0.20f;
        float  rB     = playerWon ? 0.30f : 0.20f;
        float  resW   = textWidth(result, ts);
        float  resY   = h / 2f - CHAR_H * ts - 50f;
        drawText(result, (w - resW) / 2f, resY, rR, rG, rB, ts);

        // Series score
        String score  = "SERIES  " + playerRounds + " - " + opponentRounds + "  ROUNDS";
        float  scoreW = textWidth(score, SCALE);
        float  scoreY = resY + CHAR_H * ts + 20f;
        drawText(score, (w - scoreW) / 2f, scoreY, 0.85f, 0.85f, 0.85f, SCALE);

        // Round kill context
        String sub  = "LAST ROUND  " + playerScore + " - " + opponentScore + "  KILLS";
        float  subW = textWidth(sub, 1);
        drawText(sub, (w - subW) / 2f, scoreY + CHAR_H * SCALE + 10f,
                 0.45f, 0.45f, 0.45f, 1);

        // Buttons
        float btnW = 200f, btnH = 32f, btnGap = 12f;
        float totalW = btnW * 2 + btnGap;
        float btnY   = h / 2f + 30f;
        float leftX  = (w - totalW) / 2f;
        float rightX = leftX + btnW + btnGap;

        String action = null;
        String r;

        r = drawButton("PLAY AGAIN", "PLAY_AGAIN",
                       leftX, btnY, btnW, btnH, mouseX, mouseY, clicked,
                       0.30f, 1.00f, 0.40f);
        if (r != null) action = r;

        r = drawButton("MAIN MENU", "MENU",
                       rightX, btnY, btnW, btnH, mouseX, mouseY, clicked,
                       0.70f, 0.70f, 0.70f);
        if (r != null) action = r;

        // Keyboard hint
        String hint  = "ENTER - PLAY AGAIN   ESC - MENU";
        float  hintW = textWidth(hint, 1);
        drawText(hint, (w - hintW) / 2f, h - 30f, 0.35f, 0.35f, 0.35f, 1);

        return action;
    }

    // ===== Round End Screen =====

    /**
     * Full-screen round result overlay — ROUND WIN (blue) or ROUND LOST (red).
     * Shows kill score this round, series progress (P-O rounds), and NEXT ROUND / MAIN MENU.
     */
    private String drawRoundEndScreen(boolean playerWonRound,
                                      int playerRounds, int opponentRounds, int roundsToWin,
                                      int playerScore, int opponentScore,
                                      float mouseX, float mouseY, boolean clicked, int w, int h) {
        // Overlay tint
        if (playerWonRound) drawRect(0, 0, w, h, 0.02f, 0.07f, 0.18f);
        else                drawRect(0, 0, w, h, 0.14f, 0.02f, 0.07f);

        // Round number (rounds played = playerRounds + opponentRounds after increment)
        int    roundNum  = playerRounds + opponentRounds;
        String rdLabel   = "ROUND " + roundNum + " COMPLETE";
        float  rdLabelW  = textWidth(rdLabel, SCALE);
        float  rdLabelY  = h / 2f - CHAR_H * 4 - 90f;
        drawText(rdLabel, (w - rdLabelW) / 2f, rdLabelY, 0.55f, 0.55f, 0.75f, SCALE);

        // ROUND WIN / ROUND LOST
        int    ts     = 4;
        String result = playerWonRound ? "ROUND WIN" : "ROUND LOST";
        float  rR     = playerWonRound ? 0.30f : 1.00f;
        float  rG     = playerWonRound ? 1.00f : 0.25f;
        float  rB     = playerWonRound ? 0.70f : 0.25f;
        float  resW   = textWidth(result, ts);
        float  resY   = rdLabelY + CHAR_H * SCALE + 18f;
        drawText(result, (w - resW) / 2f, resY, rR, rG, rB, ts);

        // Kill score this round
        String killLine = "KILLS  " + playerScore + " - " + opponentScore;
        float  killW    = textWidth(killLine, SCALE);
        float  killY    = resY + CHAR_H * ts + 18f;
        drawText(killLine, (w - killW) / 2f, killY, 0.75f, 0.75f, 0.75f, SCALE);

        // "SERIES" label
        String serLabel = "SERIES";
        float  serLW    = textWidth(serLabel, 1);
        float  serLY    = killY + CHAR_H * SCALE + 22f;
        drawText(serLabel, (w - serLW) / 2f, serLY, 0.45f, 0.45f, 0.55f, 1);

        // Series score — player in blue, opponent in red
        int    ss    = 3;
        String pRd   = String.valueOf(playerRounds);
        String oRd   = String.valueOf(opponentRounds);
        String sSep  = "  -  ";
        float  pRdW  = textWidth(pRd,  ss);
        float  sSpW  = textWidth(sSep, ss);
        float  oRdW  = textWidth(oRd,  ss);
        float  totW  = pRdW + sSpW + oRdW;
        float  srdX  = (w - totW) / 2f;
        float  srdY  = serLY + CHAR_H + 8f;
        drawText(pRd,  srdX,              srdY, 0.35f, 0.65f, 1.00f, ss);
        drawText(sSep, srdX + pRdW,       srdY, 0.60f, 0.60f, 0.60f, ss);
        drawText(oRd,  srdX + pRdW + sSpW, srdY, 1.00f, 0.30f, 0.30f, ss);

        // "FIRST TO X ROUNDS WINS"
        String ftR  = "FIRST TO " + roundsToWin + " ROUNDS WINS";
        float  ftRW = textWidth(ftR, 1);
        drawText(ftR, (w - ftRW) / 2f, srdY + CHAR_H * ss + 10f,
                 0.38f, 0.38f, 0.42f, 1);

        // Buttons
        float btnW   = 200f, btnH = 32f, btnGap = 12f;
        float totalB = btnW * 2 + btnGap;
        float btnY   = srdY + CHAR_H * ss + 44f;
        float leftX  = (w - totalB) / 2f;
        float rightX = leftX + btnW + btnGap;

        String action = null;
        String r;

        r = drawButton("NEXT ROUND", "NEXT_ROUND",
                       leftX, btnY, btnW, btnH, mouseX, mouseY, clicked,
                       0.30f, 1.00f, 0.40f);
        if (r != null) action = r;

        r = drawButton("MAIN MENU", "MENU",
                       rightX, btnY, btnW, btnH, mouseX, mouseY, clicked,
                       0.70f, 0.70f, 0.70f);
        if (r != null) action = r;

        String hint  = "ENTER - NEXT ROUND   ESC - MENU";
        float  hintW = textWidth(hint, 1);
        drawText(hint, (w - hintW) / 2f, h - 30f, 0.35f, 0.35f, 0.35f, 1);

        return action;
    }

    // ===== Death Screen =====

    /**
     * Full-screen dark-red death overlay with a RESPAWN button.
     */
    private String drawDeathScreen(float mouseX, float mouseY, boolean clicked,
                                   int playerScore, int opponentScore,
                                   int w, int h) {
        // Translucent red so the frozen scene shows through behind the overlay
        drawRect(0, 0, w, h, 0.30f, 0.0f, 0.0f, 0.55f);

        // "YOU DIED"
        int    ds    = 4;
        String died  = "YOU DIED";
        float  diedW = textWidth(died, ds);
        float  diedX = (w - diedW) / 2f;
        float  diedY = h / 2f - CHAR_H * ds - 40f;
        drawText(died, diedX, diedY, 1.0f, 0.15f, 0.15f, ds);

        // Current score below title
        String score  = "SCORE  " + playerScore + " - " + opponentScore;
        float  scoreW = textWidth(score, SCALE);
        drawText(score, (w - scoreW) / 2f, diedY + CHAR_H * ds + 16f,
                 0.80f, 0.80f, 0.80f, SCALE);

        // RESPAWN button
        float btnW = 200f, btnH = 32f;
        float btnX = (w - btnW) / 2f;
        float btnY = diedY + CHAR_H * ds + 16f + CHAR_H * SCALE + 20f;
        String action = drawButton("RESPAWN", "RESPAWN",
                          btnX, btnY, btnW, btnH, mouseX, mouseY, clicked,
                          1.0f, 0.35f, 0.35f);

        // Keyboard hint
        String hint  = "ENTER - RESPAWN";
        float  hintW = textWidth(hint, 1);
        drawText(hint, (w - hintW) / 2f, btnY + btnH + 16f, 0.40f, 0.40f, 0.40f, 1);

        return action;
    }

    // ===== Pause Menu Screen =====

    /**
     * Semi-transparent dark overlay with pause menu buttons.
     */
    private String drawPauseMenu(float mouseX, float mouseY, boolean clicked, int w, int h) {
        // Translucent dim so the frozen world shows through behind the menu
        drawRect(0, 0, w, h, 0.05f, 0.05f, 0.09f, 0.78f);

        // "PAUSED" title
        int    ps     = 4;
        String pause  = "PAUSED";
        float  pauseW = textWidth(pause, ps);
        float  pauseX = (w - pauseW) / 2f;
        float  pauseY = h / 3f - CHAR_H * ps;
        drawText(pause, pauseX, pauseY, 1.0f, 1.0f, 1.0f, ps);

        // Buttons — centered, stacked
        float btnW = 220f, btnH = 32f, btnGap = 10f;
        float btnX = (w - btnW) / 2f;
        float btnY = h / 2f;

        String action = null;
        String r;

        r = drawButton("RESUME GAME", "RESUME",
                       btnX, btnY, btnW, btnH, mouseX, mouseY, clicked,
                       0.40f, 1.00f, 0.40f);
        if (r != null) action = r;

        r = drawButton("MAIN MENU", "LEAVE",
                       btnX, btnY + (btnH + btnGap), btnW, btnH, mouseX, mouseY, clicked,
                       1.00f, 1.00f, 0.40f);
        if (r != null) action = r;

        r = drawButton("EXIT TO DESKTOP", "EXIT",
                       btnX, btnY + (btnH + btnGap) * 2f, btnW, btnH, mouseX, mouseY, clicked,
                       1.00f, 0.40f, 0.40f);
        if (r != null) action = r;

        // Footer hint
        String hint  = "ESC / R - RESUME    L - MAIN MENU    E - EXIT";
        float  hintW = textWidth(hint, 1);
        drawText(hint, (w - hintW) / 2f, h - 40f, 0.35f, 0.35f, 0.35f, 1);

        return action;
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

    /** Draw a solid (opaque) axis-aligned rectangle as two triangles. */
    private void drawRect(float x, float y, float w, float h, float r, float g, float b) {
        drawRect(x, y, w, h, r, g, b, 1f);
    }

    /** Draw an axis-aligned rectangle with explicit alpha (requires GL blending). */
    private void drawRect(float x, float y, float w, float h,
                          float r, float g, float b, float a) {
        ensureCapacity(QUAD_FLOATS);
        // Tri 1: TL, BL, TR
        put(x,     y,     r, g, b, a);
        put(x,     y + h, r, g, b, a);
        put(x + w, y,     r, g, b, a);
        // Tri 2: BL, BR, TR
        put(x,     y + h, r, g, b, a);
        put(x + w, y + h, r, g, b, a);
        put(x + w, y,     r, g, b, a);
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

    private void put(float x, float y, float r, float g, float b, float a) {
        verts[floatCount++] = x;
        verts[floatCount++] = y;
        verts[floatCount++] = r;
        verts[floatCount++] = g;
        verts[floatCount++] = b;
        verts[floatCount++] = a;
    }

    // ===== Cleanup =====

    public void cleanup() {
        Logger.info("Cleaning up SimpleHUD");
        if (glyphVAO != 0) GL30.glDeleteVertexArrays(glyphVAO);
        if (glyphVBO != 0) GL30.glDeleteBuffers(glyphVBO);
        if (orthoShader != null) orthoShader.delete();
    }
}
