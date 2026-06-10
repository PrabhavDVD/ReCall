package com.recall;

import org.joml.Matrix4f;
import static org.lwjgl.glfw.GLFW.*;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

import com.recall.GameState;
import com.recall.GameTeam;
import com.recall.physics.Bullet;

import com.recall.entity.Dummy;
import com.recall.entity.Entity;
import com.recall.entity.Obstacle;
import com.recall.entity.Player;
import com.recall.entity.RemotePlayer;
import com.recall.net.NetworkManager;
import com.recall.net.PlayerState;
import com.recall.weapon.Knife;
import com.recall.weapon.KnifeModel;
import com.recall.weapon.Pistol;
import com.recall.weapon.PistolModel;
import com.recall.weapon.Rifle;
import com.recall.weapon.RifleModel;
import com.recall.weapon.Shotgun;
import com.recall.weapon.ShotgunModel;
import com.recall.weapon.Sniper;
import com.recall.weapon.SniperModel;
import com.recall.weapon.Weapon;
import com.recall.graphics.Window;
import com.recall.graphics.Renderer;
import com.recall.graphics.Camera;
import com.recall.graphics.Map;
import com.recall.graphics.Mesh;
import com.recall.graphics.Vertex;
import com.recall.input.Input;
import com.recall.physics.Collider;
import com.recall.physics.RaycastResult;
import com.recall.physics.Vector3;
import com.recall.ui.SimpleHUD;
import com.recall.ui.WeaponVisuals;
import com.recall.ui.WorldHealthBar;
import com.recall.util.Logger;
import com.recall.util.Timer;

/**
 * Game - Main game orchestrator and loop
 *
 * Purpose: Central game class that manages the main game loop.
 * Follows pattern: init() → while(running) { update() → render() } → cleanup()
 *
 * Responsibility split (current):
 *   Player    → position, velocity, gravity, jump, movement direction
 *   Camera    → yaw/pitch (mouse look) + view matrix; position set by Player
 *   Input     → raw key/mouse state
 *   Collider  → raycast queries against the entity list (Phase 1.7)
 *   Game      → wires them together, owns the loop
 */
public class Game {
    private Window   window;
    private Renderer renderer;
    private Camera   camera;
    private Player   player;
    private Input    input;
    private Timer    timer;
    private Map      map;
    private SimpleHUD hud;
    private boolean  running;

    /** Everything in the world that can be rendered and/or shot. */
    private List<Entity> entities;
    /** Aim raycast result from the most recent frame — null when aiming at nothing. */
    private RaycastResult aimHit;

    // ── Weapons ──────────────────────────────────────────────────────────────
    private Weapon pistol, rifle, shotgun, sniper, knife;
    private Weapon currentWeapon;

    // ── Weapon visuals (one instance per weapon, owns its model) ─────────────
    private WeaponVisuals pistolVisuals, rifleVisuals, shotgunVisuals, sniperVisuals, knifeVisuals;
    private WeaponVisuals currentWeaponVisuals;

    private WorldHealthBar worldHealthBar;
    private Mesh impactMarkerMesh;  // small dark cube for bullet hole visuals

    /** Current game state — drives which screen is active. Starts on MENU. */
    private GameState gameState = GameState.MENU;

    // ── Phase 2.4 — Duel dummy ───────────────────────────────────────────────
    /** Static opponent dummy for local damage / combat-loop testing. */
    private Dummy duelDummy;
    /** Edge detection for F1 (reset / respawn duel dummy). */
    private boolean lastF1KeyPressed = false;

    // ── Phase 2.4 / 2.6 — Map selection ──────────────────────────────────────
    /** Display names for each selectable map. */
    private static final String[]  MAP_NAMES        = { "TRAINING GROUND", "ALPHA BRAVO ARENA" };
    /** Short description line shown on the map-select screen. */
    private static final String[]  MAP_DESCRIPTIONS = {
        "TEST MAP - CRATES - WALLS - DUMMIES",
        "SYMMETRICAL 1V1 MAP - PHASE 2.6"
    };
    /** Whether each map can actually be played yet. */
    private static final boolean[] MAP_AVAILABLE    = { true, true };
    /** Currently selected map (0 = Training Ground by default). */
    private int     selectedMapIndex  = 0;

    // ── Phase 2.9 — Scoring ───────────────────────────────────────────────────
    /** Player kill count this round. */
    private int playerScore   = 0;
    /** Opponent kill count this round (incremented each time the player dies). */
    private int opponentScore = 0;
    /** First to this many kills wins a round. */
    private static final int WIN_SCORE = 5;
    /** True when the player won the series (drives MATCH_END overlay). */
    private boolean playerWon = false;
    /** Countdown until the duel dummy auto-respawns after being killed. */
    private float dummyRespawnTimer = 0f;
    /** Seconds before the dummy comes back after death. */
    private static final float DUMMY_RESPAWN_DELAY = 3f;

    // ── Phase 2.10 — Rounds ────────────────────────────────────────────────────
    /** Rounds won by the player — first to ROUNDS_TO_WIN takes the series. */
    private int   playerRounds   = 0;
    /** Rounds won by the opponent. */
    private int   opponentRounds = 0;
    /** Number of rounds needed to win the series (best of 3). */
    private static final int   ROUNDS_TO_WIN = 2;
    /** Remaining seconds in the current round (counts down to 0; Arena only). */
    private float roundTimer     = 180f;
    /** Full round duration in seconds (3 minutes). */
    private static final float ROUND_TIME    = 180f;
    /** Whether the player won the most recently completed round (shown on ROUND_END). */
    private boolean playerWonRound = false;

    // ── Combat feedback (hitmarker, damage flash) ──────────────────────────────
    /** Counts down after a landed shot; drives the crosshair hitmarker. */
    private float   hitMarkerTimer = 0f;
    private static final float HITMARKER_DURATION = 0.18f;
    /** True when the most recent hitmarker was a kill (renders red instead of white). */
    private boolean hitMarkerKill  = false;
    /** Counts down after the player takes damage; drives the red screen-edge flash. */
    private float   damageFlashTimer = 0f;
    private static final float DAMAGE_FLASH_DURATION = 0.5f;
    /** Player health last frame — compared each frame to detect incoming damage. */
    private float   lastPlayerHealth = 100f;

    // ── Training Ground dummy respawn (Phase 2.4 practice range) ───────────────
    /** Fixed spawn points for the three practice dummies. */
    private static final Vector3[] TRAINING_DUMMY_SPAWNS = {
        new Vector3( 0f, 0f, -12f),
        new Vector3(-5f, 0f,  -8f),
        new Vector3( 5f, 0f,  -8f)
    };
    /** Yellow practice-dummy colour, shared by initial build and respawns. */
    private static final Vector3 TRAINING_DUMMY_COLOR = new Vector3(0.90f, 0.85f, 0.20f);
    /** Pending training respawns: each entry is {x, y, z, timeLeft}. */
    private final List<float[]> trainingRespawns = new ArrayList<>();

    // ── Phase 2.7 — Team assignment ───────────────────────────────────────────
    /** Player's chosen team for the current match. Defaults to ALPHA. */
    private GameTeam playerTeam = GameTeam.ALPHA;
    /** Blue accent color used for ALPHA team entities and HUD. */
    private static final Vector3 TEAM_ALPHA_COLOR = new Vector3(0.35f, 0.60f, 1.00f);
    /** Red accent color used for BRAVO team entities and HUD. */
    private static final Vector3 TEAM_BRAVO_COLOR = new Vector3(1.00f, 0.30f, 0.30f);

    // ── Networking (Phase 2.5) — null fields mean singleplayer ────────────────
    /** "host" | "join" | null. Set by configureNetwork() before run(). */
    private String netMode   = null;
    private String netHostIp = null;
    private int    netPort   = Config.NET_DEFAULT_PORT;
    /** Active UDP peer, or null in singleplayer. */
    private NetworkManager net;
    /** The networked opponent entity, or null in singleplayer. */
    private RemotePlayer remotePlayer;
    /** Tracks the opponent's liveness across frames to fire a one-shot kill notice. */
    private boolean remoteWasAlive = false;

    /** Kill notification shown in HUD for KILL_NOTIF_DURATION seconds. */
    private String killNotification      = "";
    private float  killNotificationTimer = 0f;
    private static final float KILL_NOTIF_DURATION = 2f;

    /** How far the aim ray reaches (world units). */
    private static final float AIM_RAY_DISTANCE = 100f;

    /**
     * Per-weapon recoil: {pitchDegrees (upward kick), yawRange (random ±X kick)}.
     * Applied by Game on every confirmed shot.
     * Future: sniper 4.5° pitch / 1.2° yaw
     */
    private static final float[] RECOIL_PISTOL  = { 0.8f, 0.3f };
    private static final float[] RECOIL_RIFLE   = { 1.8f, 0.6f };
    private static final float[] RECOIL_SHOTGUN = { 3.0f, 2.0f };
    private static final float[] RECOIL_SNIPER  = { 4.5f, 1.2f };
    private static final float[] RECOIL_KNIFE   = { 0.0f, 0.0f };

    /** Edge detection for ESC — prevents held ESC from rapidly cycling pause/unpause. */
    private boolean lastEscKeyPressed = false;

    /** Which weapon slot (0=pistol,1=rifle,2=shotgun,3=sniper,4=knife) is shown on the weapon-select screen. */
    private int previewWeaponIndex = 1;  // default to rifle
    /** Edge detection for LEFT/RIGHT nav keys on the weapon-select screen. */
    private boolean lastLeftKeyPressed  = false;
    private boolean lastRightKeyPressed = false;

    /** Impact markers (bullet holes on obstacles). */
    private List<ImpactMarker> impacts = new ArrayList<>();

    /** All live projectile bullets currently in flight. */
    private final List<Bullet> activeBullets = new ArrayList<>();

    // Cached projection matrix — constant until window resize
    private final float[] projectionMatrix = new float[16];
    // Identity model matrix — map plane and world-baked entity meshes both use this
    private final float[] modelMatrix      = new float[16];

    /**
     * Bullet impact marker (small dark indicator on obstacle surfaces).
     * Fades out and disappears after DURATION seconds.
     */
    private static class ImpactMarker {
        Vector3 position;
        float lifetime;
        static final float DURATION = 2.0f;

        ImpactMarker(Vector3 pos) {
            this.position = new Vector3(pos);
            this.lifetime = DURATION;
        }
    }

    public Game(String title, int width, int height) {
        window   = new Window(width, height, title);
        renderer = new Renderer();
        timer    = new Timer();
        running  = false;
    }

    /**
     * Enable networking for this session. Call before {@link #run()}.
     * The socket itself is opened later in {@link #initNetwork()} (after the GL context
     * and entity list exist), so failures are logged in context and cleanly degrade to
     * singleplayer.
     *
     * @param mode   "host" or "join" (any other value is ignored → singleplayer)
     * @param hostIp host IP to connect to (join mode only; null for host mode)
     * @param port   UDP port to bind (host) or target (join)
     */
    public void configureNetwork(String mode, String hostIp, int port) {
        this.netMode   = mode;
        this.netHostIp = hostIp;
        this.netPort   = port;
    }

    /**
     * Initialize all systems in dependency order
     */
    private void init() {
        Logger.info("Initializing game");

        // 1. Window + OpenGL context
        window.create();

        // 2. Renderer — compile shaders, set up GL state
        renderer.init(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);

        // 3. Map — upload ground-plane mesh to GPU
        map = new Map();
        map.init();

        // 4. Camera — initial orientation; position will be overridden by Player each frame
        camera = new Camera(0f, 1.7f, 14f);

        // 5. Player — ALPHA spawn at (0, 0, 14), facing north (-Z) toward BRAVO side
        player = new Player(0f, 0f, 14f);

        // 6. Input — register callbacks. Cursor starts UNLOCKED (game opens in MENU).
        //    Cursor is locked in enterState(PLAYING) and unlocked in all other states.
        input = new Input(window.getWindowHandle());
        input.init();

        // 7. Projection matrix (FOV + aspect ratio + clip planes)
        float aspect = (float) Config.WINDOW_WIDTH / Config.WINDOW_HEIGHT;
        new Matrix4f()
            .perspective(
                (float) Math.toRadians(Config.CAMERA_FOV),
                aspect,
                Config.NEAR_PLANE,
                Config.FAR_PLANE)
            .get(projectionMatrix);

        // 8. Identity model matrix (all Phase 1.7 meshes are baked in world space)
        new Matrix4f().get(modelMatrix);

        // 9. HUD — on-screen display for FPS, crosshair, and aim target
        hud = new SimpleHUD();
        hud.init(Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);

        // 10. Weapons — all five created; default is rifle
        pistol  = new Pistol();
        rifle   = new Rifle();
        shotgun = new Shotgun();
        sniper  = new Sniper();
        knife   = new Knife();
        currentWeapon = rifle;

        // 11a. Weapon visuals — one WeaponVisuals per weapon, each owns its model.
        //      Keys 1-5 switch currentWeapon + currentWeaponVisuals together.
        pistolVisuals  = new WeaponVisuals(); pistolVisuals.init(new PistolModel());
        rifleVisuals   = new WeaponVisuals(); rifleVisuals.init(new RifleModel());
        shotgunVisuals = new WeaponVisuals(); shotgunVisuals.init(new ShotgunModel());
        sniperVisuals  = new WeaponVisuals(); sniperVisuals.init(new SniperModel());
        knifeVisuals   = new WeaponVisuals(); knifeVisuals.init(new KnifeModel());
        currentWeaponVisuals = rifleVisuals;

        // 11b. World-space health bars (billboard quads above entities)
        worldHealthBar = new WorldHealthBar();
        worldHealthBar.init();

        // 11. Impact marker mesh (small dark cube for bullet hole visuals)
        impactMarkerMesh = createImpactMarkerMesh();

        // 11c. Bullet shared tracer mesh — one GPU mesh reused by every bullet in flight
        Bullet.initSharedMesh();

        // 12. Entity list — map is built by startMatch() when a game actually begins
        entities = new ArrayList<>();

        // 13. Networking — open the UDP peer and spawn the opponent entity (if configured)
        if (netMode != null) initNetwork();

        Logger.info("Game initialized");
    }

    /**
     * Open the UDP peer and add the {@link RemotePlayer} entity to the world.
     * On any failure (port in use, bad IP, …) we log and silently fall back to
     * singleplayer so a networking problem never prevents the game from running.
     */
    private void initNetwork() {
        try {
            if ("host".equals(netMode)) {
                net = NetworkManager.host(netPort, "HOST");
            } else if ("join".equals(netMode)) {
                net = NetworkManager.join(netHostIp, netPort, "CLIENT");
            } else {
                Logger.warn("[NET] Unknown net mode '" + netMode + "' — running singleplayer");
                return;
            }

            // Opponent starts hidden + non-collidable until its first STATE packet arrives.
            remotePlayer = new RemotePlayer("OPPONENT", new Vector3(0f, 0f, -10f));
            entities.add(remotePlayer);

        } catch (Exception e) {
            Logger.error("[NET] Failed to start networking (" + e.getMessage()
                    + ") — falling back to singleplayer");
            net = null;
            remotePlayer = null;
        }
    }

    /**
     * Create a small dark cube mesh used for bullet hole markers.
     * Unit cube from (−0.5, −0.5, −0.5) to (0.5, 0.5, 0.5), scaled at render time.
     */
    private Mesh createImpactMarkerMesh() {
        Vector3 darkGray = new Vector3(0.15f, 0.15f, 0.15f);  // very dark for bullet holes
        Vertex[] verts = new Vertex[24];
        float s = 0.5f;  // half-size

        // 6 faces × 4 vertices each
        int i = 0;
        // Top
        verts[i++] = new Vertex(new Vector3(-s,  s, -s), darkGray);
        verts[i++] = new Vertex(new Vector3( s,  s, -s), darkGray);
        verts[i++] = new Vertex(new Vector3( s,  s,  s), darkGray);
        verts[i++] = new Vertex(new Vector3(-s,  s,  s), darkGray);
        // Bottom
        verts[i++] = new Vertex(new Vector3(-s, -s, -s), darkGray);
        verts[i++] = new Vertex(new Vector3( s, -s, -s), darkGray);
        verts[i++] = new Vertex(new Vector3( s, -s,  s), darkGray);
        verts[i++] = new Vertex(new Vector3(-s, -s,  s), darkGray);
        // Front
        verts[i++] = new Vertex(new Vector3(-s, -s, -s), darkGray);
        verts[i++] = new Vertex(new Vector3( s, -s, -s), darkGray);
        verts[i++] = new Vertex(new Vector3( s,  s, -s), darkGray);
        verts[i++] = new Vertex(new Vector3(-s,  s, -s), darkGray);
        // Back
        verts[i++] = new Vertex(new Vector3(-s, -s,  s), darkGray);
        verts[i++] = new Vertex(new Vector3( s, -s,  s), darkGray);
        verts[i++] = new Vertex(new Vector3( s,  s,  s), darkGray);
        verts[i++] = new Vertex(new Vector3(-s,  s,  s), darkGray);
        // Right
        verts[i++] = new Vertex(new Vector3( s, -s, -s), darkGray);
        verts[i++] = new Vertex(new Vector3( s, -s,  s), darkGray);
        verts[i++] = new Vertex(new Vector3( s,  s,  s), darkGray);
        verts[i++] = new Vertex(new Vector3( s,  s, -s), darkGray);
        // Left
        verts[i++] = new Vertex(new Vector3(-s, -s, -s), darkGray);
        verts[i++] = new Vertex(new Vector3(-s, -s,  s), darkGray);
        verts[i++] = new Vertex(new Vector3(-s,  s,  s), darkGray);
        verts[i++] = new Vertex(new Vector3(-s,  s, -s), darkGray);

        int[] indices = new int[36];
        int idx = 0;
        // 2 triangles per face × 6 faces = 36 indices
        for (int face = 0; face < 6; face++) {
            int b = face * 4;
            indices[idx++] = b;     indices[idx++] = b + 1; indices[idx++] = b + 2;
            indices[idx++] = b + 2; indices[idx++] = b + 3; indices[idx++] = b;
        }

        return new Mesh(verts, indices);
    }

    /**
     * Symmetrical 1v1 arena (ALPHA BRAVO ARENA).
     * Dummy is colored to the opposing team based on playerTeam.
     *
     *   ALPHA spawn  z = +14   BRAVO spawn  z = -14
     *   cover-a1/a2  z = +8    cover-b1/b2  z = -8
     *   mid-box1/2 ±3  mid-wall center
     *   Boundary walls ±11x / ±18z
     */
    private void buildAlphaBravoArena() {
        Vector3 stone     = new Vector3(0.55f, 0.55f, 0.58f);
        Vector3 darkStone = new Vector3(0.30f, 0.30f, 0.33f);
        Vector3 orange    = new Vector3(0.95f, 0.55f, 0.15f);

        entities.add(new Obstacle("cover-a1",
            new Vector3(-6f, 0f,  8f), new Vector3(3f, 1.5f, 1f), stone));
        entities.add(new Obstacle("cover-a2",
            new Vector3( 6f, 0f,  8f), new Vector3(3f, 1.5f, 1f), stone));
        entities.add(new Obstacle("mid-box1",
            new Vector3(-3f, 0f, 0f), new Vector3(2f, 2f, 2f), orange));
        entities.add(new Obstacle("mid-box2",
            new Vector3( 3f, 0f, 0f), new Vector3(2f, 2f, 2f), orange));
        entities.add(new Obstacle("mid-wall",
            new Vector3( 0f, 0f, 0f), new Vector3(1f, 3f, 6f), stone));
        entities.add(new Obstacle("cover-b1",
            new Vector3(-6f, 0f, -8f), new Vector3(3f, 1.5f, 1f), stone));
        entities.add(new Obstacle("cover-b2",
            new Vector3( 6f, 0f, -8f), new Vector3(3f, 1.5f, 1f), stone));
        entities.add(new Obstacle("wall-n",
            new Vector3( 0f, 0f, -18f), new Vector3(22f, 4f,  1f), darkStone));
        entities.add(new Obstacle("wall-s",
            new Vector3( 0f, 0f,  18f), new Vector3(22f, 4f,  1f), darkStone));
        entities.add(new Obstacle("wall-e",
            new Vector3( 11f, 0f,  0f), new Vector3(1f,  4f, 36f), darkStone));
        entities.add(new Obstacle("wall-w",
            new Vector3(-11f, 0f,  0f), new Vector3(1f,  4f, 36f), darkStone));

        // In networked play the opponent is the live RemotePlayer — no static dummy.
        // Offline, spawn a colored practice dummy at the opposing team's spawn.
        if (net == null) {
            Vector3 dummyColor = (playerTeam == GameTeam.ALPHA) ? TEAM_BRAVO_COLOR : TEAM_ALPHA_COLOR;
            float   dummyZ     = (playerTeam == GameTeam.ALPHA) ? -14f : 14f;
            duelDummy = new Dummy("OPPONENT", new Vector3(0f, 0f, dummyZ), dummyColor);
            entities.add(duelDummy);
        }

        Logger.info("Arena built (" + entities.size() + " entities)  player=" + playerTeam.name()
                    + (net != null ? "  [networked opponent]" : ""));
    }

    /**
     * Training Ground — asymmetric crate layout with three practice dummies.
     * Offline only, no team or network context.
     */
    private void buildTrainingGround() {
        Vector3 wood     = new Vector3(0.58f, 0.38f, 0.18f);  // brown crates
        Vector3 concrete = new Vector3(0.48f, 0.48f, 0.46f);  // concrete walls

        // Crates — intentionally asymmetric to feel less formal than the arena
        entities.add(new Obstacle("crate-1",
            new Vector3(-3f, 0f, -3f), new Vector3(2f, 2f, 2f), wood));
        entities.add(new Obstacle("crate-2",
            new Vector3( 4f, 0f, -5f), new Vector3(2f, 2f, 2f), wood));
        entities.add(new Obstacle("crate-3",
            new Vector3(-5f, 0f,  3f), new Vector3(2f, 2f, 2f), wood));
        entities.add(new Obstacle("crate-4",
            new Vector3( 2f, 0f,  5f), new Vector3(2f, 2f, 2f), wood));
        entities.add(new Obstacle("crate-wall",
            new Vector3( 0f, 0f, -8f), new Vector3(5f, 1.5f, 1f), wood));

        // Low concrete walls
        entities.add(new Obstacle("wall-l",
            new Vector3(-7f, 0f, 0f), new Vector3(1f, 2f, 8f), concrete));
        entities.add(new Obstacle("wall-r",
            new Vector3( 7f, 0f, -2f), new Vector3(1f, 2f, 6f), concrete));

        // Three practice dummies — auto-respawn a few seconds after being killed
        for (int i = 0; i < TRAINING_DUMMY_SPAWNS.length; i++) {
            entities.add(new Dummy("DUMMY-" + (i + 1),
                         new Vector3(TRAINING_DUMMY_SPAWNS[i]), TRAINING_DUMMY_COLOR));
        }
        duelDummy = null;  // no single opponent HP bar in training

        Logger.info("Training Ground built (" + entities.size() + " entities)");
    }

    /**
     * Start a brand-new series — resets round scores then kicks off Round 1.
     * Called by PLAY_AGAIN and the initial MAP_CONFIRM / TEAM_CONFIRM flow.
     */
    private void startMatch() {
        playerRounds   = 0;
        opponentRounds = 0;
        startRound();
    }

    /**
     * Start (or restart) the current round — resets per-round state and rebuilds the map.
     * Round scores (playerRounds / opponentRounds) are preserved across calls.
     * Called by startMatch() for round 1, and by NEXT_ROUND for subsequent rounds.
     */
    private void startRound() {
        // Clean up existing map entities but keep the network opponent alive
        for (Entity e : entities) {
            if (!(e instanceof RemotePlayer)) e.cleanup();
        }
        entities.removeIf(e -> !(e instanceof RemotePlayer));
        duelDummy = null;

        // Build the correct map
        if (selectedMapIndex == 0) buildTrainingGround();
        else                       buildAlphaBravoArena();

        // Teleport player to the correct spawn for this map + team
        applySpawn();

        // Reset per-round combat state
        player.resetHealth();
        activeBullets.clear();
        impacts.clear();
        trainingRespawns.clear();
        killNotification      = "";
        killNotificationTimer = 0f;
        playerScore           = 0;
        opponentScore         = 0;
        playerWonRound        = false;
        dummyRespawnTimer     = 0f;
        roundTimer            = ROUND_TIME;
        hitMarkerTimer        = 0f;
        damageFlashTimer      = 0f;
        lastPlayerHealth      = player.getHealth();

        int roundNum = playerRounds + opponentRounds + 1;
        Logger.info("[ROUND " + roundNum + "] Start — series "
                    + playerRounds + "-" + opponentRounds
                    + "  map=" + MAP_NAMES[selectedMapIndex]);
    }

    /**
     * Teleport the player to the correct spawn point for the active map and team,
     * and reset camera orientation to face the opponent side.
     *
     * Training Ground : z=+10, facing -Z (toward the practice dummies)
     * Arena ALPHA     : z=+14, facing -Z (toward BRAVO at z=-14)
     * Arena BRAVO     : z=-14, facing +Z (toward ALPHA at z=+14)
     *
     * Camera yaw convention: -90° = facing -Z,  +90° = facing +Z.
     */
    private void applySpawn() {
        if (selectedMapIndex == 0) {
            // Training Ground — south end, looking north at the dummies
            player.teleport(0f, 0f, 10f);
            camera.setYaw(-90f);
            camera.setPitch(0f);
        } else if (playerTeam == GameTeam.BRAVO) {
            // Arena — BRAVO side: north end, looking south toward ALPHA spawn
            player.teleport(0f, 0f, -14f);
            camera.setYaw(90f);
            camera.setPitch(0f);
        } else {
            // Arena — ALPHA side (default): south end, looking north toward BRAVO spawn
            player.teleport(0f, 0f, 14f);
            camera.setYaw(-90f);
            camera.setPitch(0f);
        }
    }

    // =========================================================================
    // Weapon helpers
    // =========================================================================

    /**
     * Switch to the given weapon + visuals pair if not already active.
     * No-op if the requested weapon is already the current one.
     */
    private void switchWeapon(Weapon w, WeaponVisuals v) {
        if (currentWeapon == w) return;
        currentWeapon        = w;
        currentWeaponVisuals = v;
        Logger.info("Switched weapon to " + currentWeaponName());
    }

    /**
     * Return the recoil array for the currently active weapon.
     * Format: {pitchDegrees, yawRange}
     */
    private float[] currentRecoil() {
        if (currentWeapon == pistol) return RECOIL_PISTOL;
        if (currentWeapon == rifle)  return RECOIL_RIFLE;
        if (currentWeapon == shotgun)return RECOIL_SHOTGUN;
        if (currentWeapon == sniper) return RECOIL_SNIPER;
        return RECOIL_KNIFE;
    }

    /** Return the display name of the currently active weapon. */
    private String currentWeaponName() {
        if (currentWeapon == pistol) return "PISTOL";
        if (currentWeapon == rifle)  return "RIFLE";
        if (currentWeapon == shotgun)return "SHOTGUN";
        if (currentWeapon == sniper) return "SNIPER";
        return "KNIFE";
    }

    /**
     * Apply the previewed weapon slot to currentWeapon + currentWeaponVisuals.
     * Called when navigating the weapon-select screen.
     */
    private void applyPreviewWeapon() {
        switch (previewWeaponIndex) {
            case 0: switchWeapon(pistol,  pistolVisuals);  break;
            case 1: switchWeapon(rifle,   rifleVisuals);   break;
            case 2: switchWeapon(shotgun, shotgunVisuals); break;
            case 3: switchWeapon(sniper,  sniperVisuals);  break;
            default: switchWeapon(knife,  knifeVisuals);   break;
        }
    }

    /**
     * Return stat lines for the currently previewed weapon.
     * All text uses only characters defined in the SimpleHUD bitmap font (A-Z, 0-9, :, ., /, -, space).
     */
    private String[] currentPreviewStats() {
        switch (previewWeaponIndex) {
            case 0: return new String[]{   // Pistol
                "DAMAGE: 35 HP   HEADSHOT: 3X",
                "FIRE RATE: 6.67/SEC   SPEED: 120 U/S",
                "PENETRATION: NONE"
            };
            case 1: return new String[]{   // Rifle
                "DAMAGE: 40 HP   HEADSHOT: 3X",
                "FIRE RATE: 10/SEC   SPEED: 150 U/S",
                "PENETRATION: UP TO 5 UNITS"
            };
            case 2: return new String[]{   // Shotgun
                "DAMAGE: 80 HP X 6 PELLETS",
                "FIRE RATE: 1.67/SEC   SPEED: 80 U/S",
                "PENETRATION: UP TO 3 UNITS"
            };
            case 3: return new String[]{   // Sniper
                "DAMAGE: 100 HP   HEADSHOT: 1.5X",
                "FIRE RATE: 1.0/SEC   SPEED: 300 U/S",
                "PENETRATION: UNLIMITED"
            };
            default: return new String[]{ // Knife
                "DAMAGE: 50 HP   HEADSHOT: 2X",
                "SWING: 2.5/SEC   RANGE: 2 UNITS",
                "AMMO: INFINITE"
            };
        }
    }

    /** Names of the five weapons in slot order — used by weapon-select HUD. */
    private static final String[] ALL_WEAPON_NAMES = { "PISTOL", "RIFLE", "SHOTGUN", "SNIPER", "KNIFE" };

    // =========================================================================
    // State management helpers
    // =========================================================================

    /**
     * Transition to a new game state, managing cursor lock automatically.
     *   PLAYING  → cursor locked (FPS mode)
     *   anything else → cursor unlocked (mouse-driven UI)
     */
    private void enterState(GameState newState) {
        gameState = newState;
        if (newState == GameState.PLAYING) {
            input.resetFirstMouse();   // prevent camera snap on re-lock
            window.lockCursor();
        } else {
            window.unlockCursor();
        }
    }

    /** Set camera to the fixed showcase angle used by the weapon-select screen. */
    private void setupWeaponSelectCamera() {
        camera.setPosition(0f, 1.7f, 5f);
        camera.setYaw(-90f);
        camera.setPitch(-15f);
        previewWeaponIndex = 1;
        switchWeapon(rifle, rifleVisuals);
    }

    /**
     * Dispatch an action string returned by the HUD (from a mouse click or
     * keyboard shortcut routed through the same path). No-op for null/unknown.
     */
    private void handleMenuAction(String action) {
        if (action == null) return;
        switch (action) {
            // Main menu — PLAY goes to map select, not directly to game
            case "START":          selectedMapIndex = 0;
                                   enterState(GameState.MAP_SELECT);                       break;
            case "WEAPON_SELECT":  setupWeaponSelectCamera();
                                   enterState(GameState.WEAPON_SELECT);                    break;
            case "MENU":           enterState(GameState.MENU);                             break;
            case "RESUME":         enterState(GameState.PLAYING);                          break;
            case "LEAVE":          enterState(GameState.MENU);                             break;
            case "EXIT":           running = false;                                        break;
            case "RESPAWN":        resetGame(); enterState(GameState.PLAYING);             break;
            case "PLAY_AGAIN":     startMatch(); enterState(GameState.PLAYING);            break;
            case "NEXT_ROUND":     startRound(); enterState(GameState.PLAYING);            break;
            // Weapon select — SELECT confirms weapon then continues to map select
            case "WEAPON_PREV":    previewWeaponIndex = (previewWeaponIndex + 4) % 5;
                                   applyPreviewWeapon();                                   break;
            case "WEAPON_NEXT":    previewWeaponIndex = (previewWeaponIndex + 1) % 5;
                                   applyPreviewWeapon();                                   break;
            case "WEAPON_CONFIRM": selectedMapIndex = 0;
                                   enterState(GameState.MAP_SELECT);                       break;
            // Map select
            case "MAP_PREV":       selectedMapIndex =
                                       (selectedMapIndex + MAP_NAMES.length - 1) % MAP_NAMES.length; break;
            case "MAP_NEXT":       selectedMapIndex =
                                       (selectedMapIndex + 1) % MAP_NAMES.length;         break;
            case "MAP_CONFIRM":
                // Training Ground is offline — skip team select, start immediately
                if (selectedMapIndex == 0) { startMatch(); enterState(GameState.PLAYING); }
                // Arena requires team selection first
                else                       { enterState(GameState.TEAM_SELECT); }
                break;
            // Team select — set team, rebuild arena with correct colors, start
            case "TEAM_ALPHA":     playerTeam = GameTeam.ALPHA;
                                   startMatch(); enterState(GameState.PLAYING);            break;
            case "TEAM_BRAVO":     playerTeam = GameTeam.BRAVO;
                                   startMatch(); enterState(GameState.PLAYING);            break;
            case "TEAM_BACK":      enterState(GameState.MAP_SELECT);                       break;
            default: break;
        }
    }

    /**
     * Update game logic — called once per frame before render
     */
    private void update(double deltaTime) {
        // Poll GLFW events (fills key state, fires mouse callback)
        window.pollEvents();

        float dt = (float) deltaTime;

        // ── MENU state — world frozen, wait for ENTER/SPACE to begin setup ─────
        if (gameState == GameState.MENU) {
            input.consumeMouseDeltaX();
            input.consumeMouseDeltaY();
            if (input.isKeyDown(GLFW_KEY_ENTER) || input.isKeyDown(GLFW_KEY_SPACE)) {
                selectedMapIndex = 0;
                enterState(GameState.MAP_SELECT);
                Logger.info("Menu: entering map select");
            } else if (input.isKeyDown(GLFW_KEY_V)) {
                setupWeaponSelectCamera();
                enterState(GameState.WEAPON_SELECT);
                Logger.info("Menu: entering weapon select");
            }
            return;
        }

        // ── MAP_SELECT state — browse available maps ─────────────────────────
        if (gameState == GameState.MAP_SELECT) {
            input.consumeMouseDeltaX();
            input.consumeMouseDeltaY();

            boolean leftDown  = input.isKeyDown(GLFW_KEY_LEFT)  || input.isKeyDown(GLFW_KEY_A);
            boolean rightDown = input.isKeyDown(GLFW_KEY_RIGHT) || input.isKeyDown(GLFW_KEY_D);

            if (leftDown && !lastLeftKeyPressed) {
                selectedMapIndex = (selectedMapIndex + MAP_NAMES.length - 1) % MAP_NAMES.length;
            }
            if (rightDown && !lastRightKeyPressed) {
                selectedMapIndex = (selectedMapIndex + 1) % MAP_NAMES.length;
            }
            lastLeftKeyPressed  = leftDown;
            lastRightKeyPressed = rightDown;

            // ENTER — Training Ground starts directly; Arena goes to team select
            if (input.isKeyDown(GLFW_KEY_ENTER)) {
                if (selectedMapIndex == 0) { startMatch(); enterState(GameState.PLAYING); }
                else                       { enterState(GameState.TEAM_SELECT); }
                Logger.info("Map confirmed: " + MAP_NAMES[selectedMapIndex]);
            }

            // ESC / BACKSPACE — return to main menu
            boolean escDown = input.isKeyDown(GLFW_KEY_ESCAPE) || input.isKeyDown(GLFW_KEY_BACKSPACE);
            if (escDown && !lastEscKeyPressed) {
                enterState(GameState.MENU);
                Logger.info("Returning to menu from map select");
            }
            lastEscKeyPressed = escDown;
            return;  // world frozen on map select
        }

        // ── TEAM_SELECT state — pick ALPHA or BRAVO before the match ────────
        if (gameState == GameState.TEAM_SELECT) {
            input.consumeMouseDeltaX();
            input.consumeMouseDeltaY();
            // 1 = ALPHA, 2 = BRAVO, ENTER = keep current selection (default ALPHA)
            if (input.isKeyDown(GLFW_KEY_1)) {
                playerTeam = GameTeam.ALPHA;
                startMatch(); enterState(GameState.PLAYING);
            } else if (input.isKeyDown(GLFW_KEY_2)) {
                playerTeam = GameTeam.BRAVO;
                startMatch(); enterState(GameState.PLAYING);
            } else if (input.isKeyDown(GLFW_KEY_ENTER)) {
                startMatch(); enterState(GameState.PLAYING);
            }
            boolean escDown = input.isKeyDown(GLFW_KEY_ESCAPE);
            if (escDown && !lastEscKeyPressed) {
                enterState(GameState.MAP_SELECT);
                Logger.info("Team select: back to map select");
            }
            lastEscKeyPressed = escDown;
            return;
        }

        // ── WEAPON_SELECT state — browse weapon models before playing ────────
        if (gameState == GameState.WEAPON_SELECT) {
            input.consumeMouseDeltaX();  // discard — camera is fixed during preview
            input.consumeMouseDeltaY();

            boolean leftDown  = input.isKeyDown(GLFW_KEY_LEFT)  || input.isKeyDown(GLFW_KEY_A);
            boolean rightDown = input.isKeyDown(GLFW_KEY_RIGHT) || input.isKeyDown(GLFW_KEY_D);

            // Cycle left (edge-detected)
            if (leftDown && !lastLeftKeyPressed) {
                previewWeaponIndex = (previewWeaponIndex + 4) % 5;  // -1 mod 5
                applyPreviewWeapon();
            }
            // Cycle right (edge-detected)
            if (rightDown && !lastRightKeyPressed) {
                previewWeaponIndex = (previewWeaponIndex + 1) % 5;
                applyPreviewWeapon();
            }
            lastLeftKeyPressed  = leftDown;
            lastRightKeyPressed = rightDown;

            // ENTER → weapon chosen, continue to map select
            if (input.isKeyDown(GLFW_KEY_ENTER)) {
                selectedMapIndex = 0;
                enterState(GameState.MAP_SELECT);
                Logger.info("Weapon confirmed: " + currentWeaponName());
            }

            // ESC / BACKSPACE → back to main menu
            boolean escDown = input.isKeyDown(GLFW_KEY_ESCAPE) || input.isKeyDown(GLFW_KEY_BACKSPACE);
            if (escDown && !lastEscKeyPressed) {
                enterState(GameState.MENU);
                Logger.info("Returning to menu from weapon select");
            }
            lastEscKeyPressed = escDown;
            return;  // world frozen on weapon select
        }

        // ── PAUSED state — world frozen, wait for ESC/R/L/E to act ─────────────
        if (gameState == GameState.PAUSED) {
            input.consumeMouseDeltaX();  // discard to prevent camera snap on unpause
            input.consumeMouseDeltaY();
            boolean escDown = input.isKeyDown(GLFW_KEY_ESCAPE);

            if ((escDown && !lastEscKeyPressed) || input.isKeyDown(GLFW_KEY_R)) {
                enterState(GameState.PLAYING);
                Logger.info("Game resumed — entering PLAYING state");
            } else if (input.isKeyDown(GLFW_KEY_L)) {
                enterState(GameState.MENU);
                Logger.info("Returning to menu — entering MENU state");
            } else if (input.isKeyDown(GLFW_KEY_E)) {
                running = false;
                Logger.info("Exiting game — closing window");
            }
            lastEscKeyPressed = escDown;  // always track ESC edge
            return;  // world frozen on pause screen
        }

        // ── DEAD state — world frozen, wait for ENTER to respawn ─────────────
        if (gameState == GameState.DEAD) {
            input.consumeMouseDeltaX();
            input.consumeMouseDeltaY();
            if (input.isKeyDown(GLFW_KEY_ENTER)) {
                resetGame();
                enterState(GameState.PLAYING);
                Logger.info("Player respawned — entering PLAYING state");
            }
            return;
        }

        // ── ROUND_END state — brief result screen between rounds ─────────────
        if (gameState == GameState.ROUND_END) {
            input.consumeMouseDeltaX();
            input.consumeMouseDeltaY();
            boolean escDown = input.isKeyDown(GLFW_KEY_ESCAPE);
            if (escDown && !lastEscKeyPressed) {
                enterState(GameState.MENU);
                Logger.info("[ROUND] Returning to menu from round end");
            }
            if (input.isKeyDown(GLFW_KEY_ENTER)) {
                startRound();
                enterState(GameState.PLAYING);
                Logger.info("[ROUND] Starting next round via ENTER");
            }
            lastEscKeyPressed = escDown;
            return;
        }

        // ── MATCH_END state — series over, show result + options ─────────────
        if (gameState == GameState.MATCH_END) {
            input.consumeMouseDeltaX();
            input.consumeMouseDeltaY();
            boolean escDown = input.isKeyDown(GLFW_KEY_ESCAPE);
            if (escDown && !lastEscKeyPressed) {
                enterState(GameState.MENU);
            }
            if (input.isKeyDown(GLFW_KEY_ENTER)) {
                startMatch(); enterState(GameState.PLAYING);
            }
            lastEscKeyPressed = escDown;
            return;
        }

        // ── PLAYING state — normal game update ────────────────────────────────

        // --- Mouse look (camera rotation only) ---
        float dx = input.consumeMouseDeltaX();
        float dy = input.consumeMouseDeltaY();
        if (dx != 0f || dy != 0f) {
            camera.processMouseMovement(dx, dy);
        }

        // --- Movement keys ---
        boolean w      = input.isKeyDown(GLFW_KEY_W);
        boolean s      = input.isKeyDown(GLFW_KEY_S);
        boolean a      = input.isKeyDown(GLFW_KEY_A);
        boolean d      = input.isKeyDown(GLFW_KEY_D);
        boolean jump   = input.isKeyDown(GLFW_KEY_SPACE);
        boolean sprint = input.isKeyDown(GLFW_KEY_LEFT_SHIFT)
                      || input.isKeyDown(GLFW_KEY_RIGHT_SHIFT);
        boolean crouch = input.isKeyDown(GLFW_KEY_LEFT_CONTROL)
                      || input.isKeyDown(GLFW_KEY_RIGHT_CONTROL);

        // Player handles gravity, jump, movement, obstacle collision, and repositions camera
        player.update(w, s, a, d, jump, sprint, crouch, camera, dt, entities);

        // --- Decay recoil from previous shots ---
        camera.decayRecoil();

        // --- Pause game (ESC key — edge detected so holding ESC doesn't spam-pause) ---
        boolean escDown = input.isKeyDown(GLFW_KEY_ESCAPE);
        if (escDown && !lastEscKeyPressed) {
            enterState(GameState.PAUSED);
            Logger.info("Game paused — entering PAUSED state");
        }
        lastEscKeyPressed = escDown;

        // --- Weapon switching (1=pistol, 2=rifle, 3=shotgun, 4=sniper, 5=knife) ---
        if (input.isKeyDown(GLFW_KEY_1)) switchWeapon(pistol,  pistolVisuals);
        if (input.isKeyDown(GLFW_KEY_2)) switchWeapon(rifle,   rifleVisuals);
        if (input.isKeyDown(GLFW_KEY_3)) switchWeapon(shotgun, shotgunVisuals);
        if (input.isKeyDown(GLFW_KEY_4)) switchWeapon(sniper,  sniperVisuals);
        if (input.isKeyDown(GLFW_KEY_5)) switchWeapon(knife,   knifeVisuals);

        // --- Weapon cooldown tick ---
        currentWeapon.update(dt);
        currentWeaponVisuals.update(dt);

        // --- F1 — reset / respawn duel dummy (offline Arena only, edge detected) ---
        boolean f1Down = input.isKeyDown(GLFW_KEY_F1);
        if (f1Down && !lastF1KeyPressed && selectedMapIndex == 1 && net == null) {
            if (duelDummy != null && !duelDummy.isDead()) {
                duelDummy.resetHealth();
                Logger.info("[DUEL] Dummy HP reset to 100");
            } else {
                // Dummy was eliminated — clear stale ref (if removeIf hasn't yet) then respawn
                if (duelDummy != null) {
                    entities.remove(duelDummy);
                    duelDummy.cleanup();
                }
                Vector3 dummyColor = (playerTeam == GameTeam.ALPHA)
                                     ? TEAM_BRAVO_COLOR : TEAM_ALPHA_COLOR;
                float   dummyZ     = (playerTeam == GameTeam.ALPHA) ? -14f : 14f;
                duelDummy = new Dummy("OPPONENT", new Vector3(0f, 0f, dummyZ), dummyColor);
                entities.add(duelDummy);
                Logger.info("[DUEL] Dummy respawned at opponent spawn z=" + dummyZ);
            }
        }
        lastF1KeyPressed = f1Down;

        // --- Aim raycast (only Dummies, not obstacles) ---
        RaycastResult fullHit = Collider.raycast(
            camera.getPosition(),
            camera.getForwardVector(),
            entities,
            AIM_RAY_DISTANCE);
        // Filter to only show damageable targets as aim info (obstacles don't clutter HUD)
        aimHit = (fullHit != null
                  && (fullHit.entity instanceof Dummy || fullHit.entity instanceof RemotePlayer))
                 ? fullHit : null;

        // --- Fire on left click ---
        if (input.consumeLeftMousePressed()) {
            // Check canFire() BEFORE calling fire() so we know if the weapon
            // actually discharged (fire() returns an empty list on cooldown).
            boolean didFire = currentWeapon.canFire();
            List<Bullet> newBullets = currentWeapon.fire(
                camera.getPosition(),
                camera.getForwardVector());
            activeBullets.addAll(newBullets);

            if (didFire) {
                currentWeaponVisuals.notifyFired();   // trigger muzzle flash

                // Apply per-weapon recoil: random side kick + upward kick
                float[] recoil = currentRecoil();
                float randomYaw = (float)(Math.random() * recoil[1] * 2 - recoil[1]);
                camera.applyRecoil(randomYaw, recoil[0]);
            }
            // Impact markers and kill notifications are now handled asynchronously
            // in the bullet physics tick below — bullets travel and register hits
            // over subsequent frames rather than instantly.
        }

        // --- Bullet physics tick ---
        // Each live bullet moves, sweeps for collisions, and reports results.
        for (int bi = activeBullets.size() - 1; bi >= 0; bi--) {
            Bullet b = activeBullets.get(bi);
            b.update(dt, entities);

            // Obstacle hit → create impact marker (bullet hole)
            if (b.getLastImpactPoint() != null) {
                impacts.add(new ImpactMarker(b.getLastImpactPoint()));
            }

            // Entity killed → show HUD kill notification
            if (b.getLastKillNotification() != null) {
                killNotification      = b.getLastKillNotification();
                killNotificationTimer = KILL_NOTIF_DURATION;
                Logger.info(killNotification);
            }

            // Any landed shot → trigger the crosshair hitmarker (red on kill)
            if (b.didDamageEntity()) {
                hitMarkerTimer = HITMARKER_DURATION;
                hitMarkerKill  = (b.getLastKillNotification() != null);
            }

            b.clearResults();

            if (!b.isAlive()) activeBullets.remove(bi);
        }

        // --- Networking sync (Phase 2.5) ---
        // Order: apply what the peer did to us, mirror the peer's body, forward our
        // hits, then broadcast our (now up-to-date) snapshot.
        if (net != null) {
            // 1. Apply hits the opponent landed on us this frame
            Float incoming;
            while ((incoming = net.pollHit()) != null) {
                player.takeDamage(incoming);
            }

            // 2. Mirror the opponent's latest snapshot onto the RemotePlayer entity
            PlayerState rs = net.getRemoteState();
            if (rs != null && remotePlayer != null) {
                remotePlayer.setState(rs);

                // One-shot kill notice + score when the opponent transitions alive → dead
                boolean nowAlive = remotePlayer.isAliveRemote();
                if (remoteWasAlive && !nowAlive) {
                    killNotification      = "ELIMINATED OPPONENT";
                    killNotificationTimer = KILL_NOTIF_DURATION;
                    playerScore++;   // networked kills score the same as dummy kills
                    Logger.info("[SCORE] Opponent eliminated. Score: "
                                + playerScore + "-" + opponentScore);
                }
                remoteWasAlive = nowAlive;
            }

            // 3. Forward any damage our bullets dealt to the opponent as a HIT packet
            if (remotePlayer != null) {
                float dealt = remotePlayer.consumePendingDamage();
                if (dealt > 0f) net.sendHit(dealt);
            }

            // 4. Broadcast our own snapshot (self-paced to NET_SEND_RATE_HZ)
            PlayerState local = new PlayerState(
                    player.getPosition().x, player.getPosition().y, player.getPosition().z,
                    camera.getYaw(), camera.getPitch(),
                    player.getHealth(), !player.isDead());
            net.update(dt, local);
        }

        // --- Round timer countdown (Arena only — Training Ground is open-ended) ---
        if (selectedMapIndex == 1) {
            roundTimer -= dt;
            if (roundTimer <= 0f) {
                roundTimer = 0f;
                // Tie goes to the player
                endRound(playerScore >= opponentScore);
                return;
            }
        }

        // --- Remove dead entities, freeing GPU resources ---
        entities.removeIf(e -> {
            if (e.isDead()) {
                if (e == duelDummy) {
                    duelDummy = null;
                    // Score the kill and start auto-respawn countdown
                    playerScore++;
                    dummyRespawnTimer = DUMMY_RESPAWN_DELAY;
                    Logger.info("[SCORE] Player kill. Score: " + playerScore + "-" + opponentScore);
                } else if (selectedMapIndex == 0 && e instanceof Dummy) {
                    // Training practice dummy — schedule it to respawn where it stood
                    Vector3 p = e.getPosition();
                    trainingRespawns.add(new float[]{ p.x, p.y, p.z, DUMMY_RESPAWN_DELAY });
                }
                e.cleanup();
                return true;
            }
            return false;
        });

        // --- Check player win condition (round end by kill limit) ---
        if (playerScore >= WIN_SCORE) {
            endRound(true);
            return;
        }

        // --- Dummy auto-respawn (Arena only) ---
        if (selectedMapIndex == 1 && duelDummy == null && dummyRespawnTimer > 0f) {
            dummyRespawnTimer -= dt;
            if (dummyRespawnTimer <= 0f) {
                Vector3 dummyColor = (playerTeam == GameTeam.ALPHA) ? TEAM_BRAVO_COLOR : TEAM_ALPHA_COLOR;
                float   dummyZ     = (playerTeam == GameTeam.ALPHA) ? -14f : 14f;
                duelDummy = new Dummy("OPPONENT", new Vector3(0f, 0f, dummyZ), dummyColor);
                entities.add(duelDummy);
                Logger.info("[DUEL] Dummy auto-respawned");
            }
        }

        // --- Training Ground dummy respawns (count down, then re-add) ---
        if (!trainingRespawns.isEmpty()) {
            for (int i = trainingRespawns.size() - 1; i >= 0; i--) {
                float[] r = trainingRespawns.get(i);
                r[3] -= dt;
                if (r[3] <= 0f) {
                    entities.add(new Dummy("DUMMY",
                                 new Vector3(r[0], r[1], r[2]), TRAINING_DUMMY_COLOR));
                    trainingRespawns.remove(i);
                    Logger.info("[TRAINING] Practice dummy respawned");
                }
            }
        }

        // --- Tick impact markers (bullet holes) ---
        impacts.removeIf(m -> {
            m.lifetime -= dt;
            return m.lifetime <= 0f;
        });

        // --- Tick kill notification ---
        if (killNotificationTimer > 0f) killNotificationTimer -= dt;

        // --- Tick combat-feedback timers (hitmarker + damage flash) ---
        if (hitMarkerTimer   > 0f) hitMarkerTimer   -= dt;
        if (damageFlashTimer > 0f) damageFlashTimer -= dt;

        // --- Detect incoming damage → trigger the red screen flash ---
        float curHealth = player.getHealth();
        if (curHealth < lastPlayerHealth) {
            damageFlashTimer = DAMAGE_FLASH_DURATION;
        }
        lastPlayerHealth = curHealth;

        // --- Player death check ---
        if (player.isDead()) {
            opponentScore++;
            Logger.info("[SCORE] Opponent kill. Score: " + playerScore + "-" + opponentScore);
            if (opponentScore >= WIN_SCORE) {
                endRound(false);
            } else {
                enterState(GameState.DEAD);
                Logger.info("Player died (" + opponentScore + " opp kills) — respawning");
            }
            return;
        }
    }

    /**
     * Respawn the player mid-round — heals the player and re-applies the spawn
     * point without touching scores, round counts, or the round timer.
     * Called by RESPAWN (death screen) and ESC→resume-from-dead shortcuts.
     */
    private void resetGame() {
        player.resetHealth();
        applySpawn();
        activeBullets.clear();
        impacts.clear();
        killNotification      = "";
        killNotificationTimer = 0f;
        hitMarkerTimer        = 0f;
        damageFlashTimer      = 0f;
        lastPlayerHealth      = player.getHealth();
    }

    /**
     * Award a round win to the given side, then check whether the series is decided.
     * Transitions to ROUND_END (next round pending) or MATCH_END (series over).
     *
     * @param playerWonThis true if the player won this round
     */
    private void endRound(boolean playerWonThis) {
        playerWonRound = playerWonThis;
        if (playerWonThis) playerRounds++;
        else               opponentRounds++;

        Logger.info("[ROUND END] " + (playerWonThis ? "player" : "opponent")
                    + " wins round — series " + playerRounds + "-" + opponentRounds);

        if (playerRounds >= ROUNDS_TO_WIN) {
            playerWon = true;
            enterState(GameState.MATCH_END);
        } else if (opponentRounds >= ROUNDS_TO_WIN) {
            playerWon = false;
            enterState(GameState.MATCH_END);
        } else {
            enterState(GameState.ROUND_END);
        }
    }

    /**
     * Render frame — called once per frame after update
     */
    private void render() {
        // Weapon-select and map-select show content against a pure dark background;
        // all other states use the default mid-gray sky color.
        if (gameState == GameState.WEAPON_SELECT
                || gameState == GameState.MAP_SELECT
                || gameState == GameState.TEAM_SELECT
                || gameState == GameState.ROUND_END
                || gameState == GameState.MATCH_END) {
            GL11.glClearColor(0.04f, 0.04f, 0.07f, 1f);
        } else {
            GL11.glClearColor(0.5f, 0.5f, 0.5f, 1f);
        }

        renderer.beginFrame();

        renderer.getShader().setMat4("projection", projectionMatrix);
        renderer.getShader().setMat4("view",       camera.getViewMatrix());
        renderer.getShader().setMat4("model",      modelMatrix);

        // Ground plane — hidden during weapon-select and map-select (clean dark background)
        if (gameState != GameState.WEAPON_SELECT
                && gameState != GameState.MAP_SELECT
                && gameState != GameState.TEAM_SELECT
                && gameState != GameState.ROUND_END
                && gameState != GameState.MATCH_END) {
            map.render();
        }

        // World entities — shown during PLAYING / PAUSED / DEAD (frozen scene behind
        // the translucent death overlay). MENU and the menu screens stay clean.
        if (gameState == GameState.PLAYING || gameState == GameState.PAUSED
                || gameState == GameState.DEAD) {
            for (Entity e : entities) {
                // Hidden until first packet / downed opponents are not drawn
                if (e instanceof RemotePlayer && !((RemotePlayer) e).isAliveRemote()) {
                    continue;
                }
                // RemotePlayer mesh is baked at the origin → translate per frame
                if (e instanceof RemotePlayer) {
                    Vector3 p = e.getPosition();
                    float[] entityMatrix = new float[16];
                    new Matrix4f().translate(p.x, p.y, p.z).get(entityMatrix);
                    renderer.getShader().setMat4("model", entityMatrix);
                    e.render();
                    renderer.getShader().setMat4("model", modelMatrix);
                } else {
                    e.render();
                }
            }

            // Impact markers (bullet holes on obstacles)
            for (ImpactMarker m : impacts) {
                float scale = 0.05f;
                float[] markerMatrix = new float[16];
                new Matrix4f()
                    .translate(m.position.x, m.position.y, m.position.z)
                    .scale(scale, scale, scale)
                    .get(markerMatrix);
                renderer.getShader().setMat4("model", markerMatrix);
                impactMarkerMesh.render();
            }

            // Bullet tracers
            float[] bulletMatrix = new float[16];
            for (Bullet b : activeBullets) {
                b.buildMatrix(bulletMatrix);
                renderer.getShader().setMat4("model", bulletMatrix);
                Bullet.renderShared();
            }
            renderer.getShader().setMat4("model", modelMatrix);

            // World-space health bars
            GL11.glDisable(GL11.GL_DEPTH_TEST);
            worldHealthBar.render(entities, camera, renderer.getShader());
            GL11.glEnable(GL11.GL_DEPTH_TEST);
        }

        // Weapon model rendering — mode depends on state.
        //   PLAYING / PAUSED    → first-person corner position (normal gameplay view)
        //   WEAPON_SELECT       → centered showcase pose (full side-profile of the gun)
        //   MENU / DEAD         → not shown
        if (gameState == GameState.PLAYING || gameState == GameState.PAUSED) {
            currentWeaponVisuals.render(renderer.getShader(), camera, projectionMatrix);
        } else if (gameState == GameState.WEAPON_SELECT) {
            currentWeaponVisuals.renderShowcase(renderer.getShader(), camera, projectionMatrix);
        }

        renderer.endFrame();

        // HUD — state-driven: MENU / WEAPON_SELECT / PLAYING / PAUSED / DEAD
        String notif = killNotificationTimer > 0f ? killNotification : null;
        String[] weaponNames = { "PISTOL", "RIFLE", "SHOTGUN", "SNIPER", "KNIFE" };
        String[] ammoTypes   = {
            pistol.getAmmoType(),
            rifle.getAmmoType(),
            shotgun.getAmmoType(),
            sniper.getAmmoType(),
            knife.getAmmoType()
        };
        int currentSlot = (currentWeapon == pistol) ? 0
                        : (currentWeapon == rifle)  ? 1
                        : (currentWeapon == shotgun)? 2
                        : (currentWeapon == sniper) ? 3
                        :                             4;
        // Build weapon-select preview data (only needed when in WEAPON_SELECT state)
        String[] previewStats = (gameState == GameState.WEAPON_SELECT)
                                ? currentPreviewStats() : null;
        // Network status line (null in singleplayer → HUD draws nothing)
        String netStatus = (net != null) ? net.statusLine() : null;
        // Duel dummy HP: passed to HUD only when alive (-1 = not present / hide)
        float duelDummyHp = (duelDummy != null && !duelDummy.isDead())
                            ? duelDummy.getHealth() : -1f;
        // Mouse state for UI hit-testing.
        // consumeLeftMousePressed() is safe here: weapon fire in update() already
        // consumed the click for PLAYING state, so this only returns true in menu states.
        float uiMouseX = input.getMouseX();
        float uiMouseY = input.getMouseY();
        boolean uiClick = input.consumeLeftMousePressed();
        // Round timer: only meaningful for Arena; pass -1 for Training Ground (HUD hides it)
        float hudRoundTimer = (selectedMapIndex == 1) ? roundTimer : -1f;
        // Combat feedback: normalize timers to 0..1 intensities for the HUD
        float hitMarkerAlpha   = (HITMARKER_DURATION    > 0f) ? hitMarkerTimer   / HITMARKER_DURATION    : 0f;
        float damageFlashAlpha = (DAMAGE_FLASH_DURATION > 0f) ? damageFlashTimer / DAMAGE_FLASH_DURATION : 0f;
        // Crosshair bloom from recent recoil (degrees → pixels, capped)
        float crosshairSpread  = Math.min(18f, camera.getRecoilMagnitude() * 6f);
        String menuAction = hud.render(timer.getFPS(), aimHit, notif,
                   player.getHealth(), 100f,
                   weaponNames, ammoTypes, currentSlot,
                   previewWeaponIndex, previewStats,
                   gameState, netStatus,
                   selectedMapIndex, MAP_NAMES, MAP_DESCRIPTIONS, MAP_AVAILABLE,
                   duelDummyHp,
                   playerTeam,
                   playerScore, opponentScore, WIN_SCORE, playerWon,
                   playerRounds, opponentRounds, ROUNDS_TO_WIN, hudRoundTimer, playerWonRound,
                   hitMarkerAlpha, hitMarkerKill, damageFlashAlpha, crosshairSpread,
                   uiMouseX, uiMouseY, uiClick,
                   Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);
        handleMenuAction(menuAction);

        window.swapBuffers();
    }

    /**
     * Release all resources in reverse init order
     */
    private void cleanup() {
        Logger.info("Cleaning up game");
        if (net != null) {
            net.shutdown();   // BYE + close socket + stop receive thread
            net = null;
        }
        if (entities != null) {
            for (Entity e : entities) e.cleanup();
        }
        pistolVisuals.cleanup();
        rifleVisuals.cleanup();
        shotgunVisuals.cleanup();
        sniperVisuals.cleanup();
        knifeVisuals.cleanup();
        worldHealthBar.cleanup();
        if (impactMarkerMesh != null) impactMarkerMesh.delete();
        Bullet.destroySharedMesh();
        hud.cleanup();
        map.cleanup();
        renderer.cleanup();
        window.cleanup();
        Logger.info("Game cleaned up");
    }

    /**
     * Main game loop — runs until ESC is pressed or window is closed
     */
    public void run() {
        init();
        running = true;

        Logger.info("Starting game loop");

        int lastPrintedFps = 0;

        while (running && !window.shouldClose()) {
            timer.update();
            double deltaTime = timer.getDeltaTime();

            update(deltaTime);
            render();

            int currentFps = timer.getFPS();
            if (Config.SHOW_FPS && currentFps > 0 && currentFps != lastPrintedFps) {
                Logger.info("FPS: " + currentFps);
                lastPrintedFps = currentFps;
            }
        }

        running = false;
        cleanup();
    }

    // Getters
    public Window   getWindow()   { return window; }
    public Renderer getRenderer() { return renderer; }
    public Camera   getCamera()   { return camera; }
    public Player   getPlayer()   { return player; }
    public Timer    getTimer()    { return timer; }
    public Map      getMap()      { return map; }
    public SimpleHUD getHUD()     { return hud; }
    public List<Entity> getEntities() { return entities; }
    public RaycastResult getAimHit()  { return aimHit; }
}
