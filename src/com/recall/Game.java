package com.recall;

import org.joml.Matrix4f;
import static org.lwjgl.glfw.GLFW.*;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

import com.recall.GameState;
import com.recall.physics.Bullet;

import com.recall.entity.Box;
import com.recall.entity.Dummy;
import com.recall.entity.Enemy;
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

    /** AI-controlled enemy entity */
    private Enemy enemy;
    /** Where the enemy respawns (set once in spawnTestEntities) */
    private Vector3 enemySpawnPoint;
    /** Counts down to enemy respawn after death; -1 = not pending */
    private float enemyRespawnTimer = -1f;
    private static final float ENEMY_RESPAWN_DELAY = 3.0f;  // seconds

    /** Current game state — drives which screen is active. Starts on MENU. */
    private GameState gameState = GameState.MENU;

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

    /** Dummy spawn data for respawning. */
    private List<DummySpawn> dummySpawns = new ArrayList<>();
    private boolean lastRKeyPressed   = false;
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
     * Stores spawn data for a dummy so it can be respawned later.
     */
    private static class DummySpawn {
        String name;
        Vector3 position;
        Vector3 color;

        DummySpawn(String name, Vector3 position, Vector3 color) {
            this.name = name;
            this.position = new Vector3(position);  // Copy to avoid mutation
            this.color = new Vector3(color);
        }
    }

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
        camera = new Camera(0f, 1.7f, 5f);

        // 5. Player — feet at (0, 0, 5), on the ground plane
        player = new Player(0f, 0f, 5f);

        // 6. Input — register callbacks, lock cursor for FPS mouse look
        input = new Input(window.getWindowHandle());
        input.init();
        window.lockCursor();

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

        // 12. Test entities — scatter crates, dummies, and a small building for raycast testing
        entities = new ArrayList<>();
        spawnTestEntities();

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
     * Populate the world with practice targets and simple structures.
     *
     * Layout (player spawns at (0,0,5) facing -Z, so everything is placed
     * in front of the spawn point):
     *   - Three crates at varying distances and sides for range testing
     *   - Two tall thin "dummies" for headshot testing (top 25% of height)
     *   - A small enclosure (back wall + two side walls) behind the crates
     *   - Two tall pillars as landmarks
     */
    private void spawnTestEntities() {
        Vector3 red       = new Vector3(0.85f, 0.25f, 0.20f);
        Vector3 green     = new Vector3(0.25f, 0.75f, 0.30f);
        Vector3 blue      = new Vector3(0.25f, 0.45f, 0.85f);
        Vector3 yellow    = new Vector3(0.90f, 0.80f, 0.15f);
        Vector3 orange    = new Vector3(0.95f, 0.55f, 0.15f);
        Vector3 stone     = new Vector3(0.55f, 0.55f, 0.58f);
        Vector3 darkStone = new Vector3(0.35f, 0.35f, 0.38f);

        // --- Obstacles: non-damageable world geometry ---
        entities.add(new Obstacle("crate-near",
            new Vector3( 3f, 0f,  -2f), new Vector3(1.5f, 1.5f, 1.5f), orange));
        entities.add(new Obstacle("crate-mid",
            new Vector3(-3f, 0f,  -8f), new Vector3(2f,   2f,   2f),   green));
        entities.add(new Obstacle("crate-far",
            new Vector3( 5f, 0f, -15f), new Vector3(3f,   3f,   3f),   blue));

        entities.add(new Obstacle("wall-back",
            new Vector3( 0f,  0f, -20f), new Vector3(10f, 4f, 1f), stone));
        entities.add(new Obstacle("wall-left",
            new Vector3(-4.5f, 0f, -17f), new Vector3(1f, 4f, 5f), stone));
        entities.add(new Obstacle("wall-right",
            new Vector3( 4.5f, 0f, -17f), new Vector3(1f, 4f, 5f), stone));

        entities.add(new Obstacle("pillar-left",
            new Vector3(-8f, 0f, -10f), new Vector3(1f, 6f, 1f), darkStone));
        entities.add(new Obstacle("pillar-right",
            new Vector3( 8f, 0f, -10f), new Vector3(1f, 6f, 1f), darkStone));

        // --- Dummies: damageable practice targets ---
        spawnDummies(red, yellow);

        // --- Enemy: AI-controlled hostile entity ---
        enemySpawnPoint = new Vector3(5f, 0f, -15f);
        enemy = new Enemy("grunt-1", new Vector3(enemySpawnPoint), player);
        entities.add(enemy);

        Logger.info("Spawned " + entities.size() + " test entities (2 dummies, 1 enemy, 7 obstacles)");
    }

    /**
     * Spawn (or respawn) the dummy practice targets.
     */
    private void spawnDummies(Vector3 redColor, Vector3 yellowColor) {
        // Store spawn data for respawning later
        dummySpawns.clear();
        dummySpawns.add(new DummySpawn("dummy-close", new Vector3(0f, 0f, -5f), redColor));
        dummySpawns.add(new DummySpawn("dummy-far", new Vector3(-2f, 0f, -12f), yellowColor));

        // Create dummy entities
        for (DummySpawn spawn : dummySpawns) {
            entities.add(new Dummy(spawn.name, spawn.position, spawn.color));
        }
    }

    /**
     * Respawn all dummy practice targets with full health at their original spawn points.
     * Removes dead dummies first, then recreates them.
     */
    private void respawnDummies() {
        // Remove dead dummies from the entity list
        entities.removeIf(e -> e instanceof Dummy && e.isDead());

        // Recreate dummies from spawn data
        for (DummySpawn spawn : dummySpawns) {
            entities.add(new Dummy(spawn.name, spawn.position, spawn.color));
        }

        Logger.info("Dummies respawned (press R)");
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

    /**
     * Update game logic — called once per frame before render
     */
    private void update(double deltaTime) {
        // Poll GLFW events (fills key state, fires mouse callback)
        window.pollEvents();

        float dt = (float) deltaTime;

        // ── MENU state — world frozen, wait for ENTER/SPACE to start ────────
        if (gameState == GameState.MENU) {
            input.consumeMouseDeltaX();  // discard to prevent camera snap on entry
            input.consumeMouseDeltaY();
            if (input.isKeyDown(GLFW_KEY_ENTER) || input.isKeyDown(GLFW_KEY_SPACE)) {
                gameState = GameState.PLAYING;
                Logger.info("Game started — entering PLAYING state");
            } else if (input.isKeyDown(GLFW_KEY_V)) {
                // Enter weapon-select preview: fix camera to a clean display angle
                camera.setPosition(0f, 1.7f, 5f);
                camera.setYaw(-90f);
                camera.setPitch(-15f);
                previewWeaponIndex = 1;        // start on rifle
                switchWeapon(rifle, rifleVisuals);
                gameState = GameState.WEAPON_SELECT;
                Logger.info("Entering weapon select screen");
            }
            return;  // world frozen on menu
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

            // ENTER → start playing with the selected weapon
            if (input.isKeyDown(GLFW_KEY_ENTER)) {
                gameState = GameState.PLAYING;
                Logger.info("Starting game with " + currentWeaponName());
            }

            // ESC / BACKSPACE → back to main menu
            boolean escDown = input.isKeyDown(GLFW_KEY_ESCAPE) || input.isKeyDown(GLFW_KEY_BACKSPACE);
            if (escDown && !lastEscKeyPressed) {
                gameState = GameState.MENU;
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
                // ESC (edge) or R → resume game.
                // Set lastRKeyPressed=true so the PLAYING dummy-respawn edge detector
                // does not immediately fire if R is still held on the next frame.
                lastRKeyPressed = true;
                gameState = GameState.PLAYING;
                Logger.info("Game resumed — entering PLAYING state");
            } else if (input.isKeyDown(GLFW_KEY_L)) {
                gameState = GameState.MENU;
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
            input.consumeMouseDeltaX();  // discard to prevent camera snap on respawn
            input.consumeMouseDeltaY();
            if (input.isKeyDown(GLFW_KEY_ENTER)) {
                resetGame();
                gameState = GameState.PLAYING;
                Logger.info("Player respawned — entering PLAYING state");
            }
            return;  // world frozen on death screen
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
            gameState = GameState.PAUSED;
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

        // --- Enemy AI update ---
        if (enemy != null && !enemy.isDead()) {
            enemy.update(dt);
        }

        // --- Respawn dummies on R key press (edge detection) ---
        boolean rKeyDown = input.isKeyDown(GLFW_KEY_R);
        if (rKeyDown && !lastRKeyPressed) {
            respawnDummies();
        }
        lastRKeyPressed = rKeyDown;

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

                // One-shot kill notice when the opponent transitions alive → dead
                boolean nowAlive = remotePlayer.isAliveRemote();
                if (remoteWasAlive && !nowAlive) {
                    killNotification      = "ELIMINATED OPPONENT";
                    killNotificationTimer = KILL_NOTIF_DURATION;
                    Logger.info("Opponent eliminated");
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

        // --- Remove dead entities, freeing GPU resources ---
        entities.removeIf(e -> {
            if (e.isDead()) {
                // If the enemy just died, start the respawn countdown
                if (e == enemy && enemyRespawnTimer < 0f) {
                    enemyRespawnTimer = ENEMY_RESPAWN_DELAY;
                    enemy = null;
                    Logger.info("Enemy killed — respawning in " + (int)ENEMY_RESPAWN_DELAY + "s");
                }
                e.cleanup();
                return true;
            }
            return false;
        });

        // --- Enemy respawn countdown ---
        if (enemyRespawnTimer > 0f) {
            enemyRespawnTimer -= dt;
            if (enemyRespawnTimer <= 0f) {
                enemyRespawnTimer = -1f;
                enemy = new Enemy("grunt-1", new Vector3(enemySpawnPoint), player);
                entities.add(enemy);
                Logger.info("Enemy respawned at " + enemySpawnPoint);
            }
        }

        // --- Tick impact markers (bullet holes) ---
        impacts.removeIf(m -> {
            m.lifetime -= dt;
            return m.lifetime <= 0f;
        });

        // --- Tick kill notification ---
        if (killNotificationTimer > 0f) killNotificationTimer -= dt;

        // --- Player death check — transition to DEAD state ---
        if (player.isDead()) {
            gameState = GameState.DEAD;
            Logger.info("Player died — entering DEAD state");
        }
    }

    /**
     * Reset world state for a fresh round after player death.
     * Restores health, clears bullets and notifications, respawns enemy and dummies.
     */
    private void resetGame() {
        // 1. Heal player
        player.resetHealth();

        // 2. Clear all projectiles in flight
        activeBullets.clear();

        // 3. Clear kill notification
        killNotification      = "";
        killNotificationTimer = 0f;

        // 4. Remove current enemy (force-kill) and schedule immediate respawn
        if (enemy != null) {
            entities.remove(enemy);
            enemy.cleanup();
            enemy = null;
        }
        enemyRespawnTimer = 0.1f;  // respawn almost immediately

        // 5. Remove all dummies (alive or dead) and recreate from spawn data
        entities.removeIf(e -> e instanceof Dummy);
        for (DummySpawn spawn : dummySpawns) {
            entities.add(new Dummy(spawn.name, spawn.position, spawn.color));
        }

        // 6. Clear bullet-hole impact markers
        impacts.clear();

        Logger.info("Game reset — player health restored, world reloaded");
    }

    /**
     * Render frame — called once per frame after update
     */
    private void render() {
        // Weapon-select shows the gun against a pure dark background;
        // all other states use the default mid-gray sky color.
        if (gameState == GameState.WEAPON_SELECT) {
            GL11.glClearColor(0.04f, 0.04f, 0.07f, 1f);
        } else {
            GL11.glClearColor(0.5f, 0.5f, 0.5f, 1f);
        }

        renderer.beginFrame();

        renderer.getShader().setMat4("projection", projectionMatrix);
        renderer.getShader().setMat4("view",       camera.getViewMatrix());
        renderer.getShader().setMat4("model",      modelMatrix);

        // Ground plane — hidden during weapon-select (clean dark background only)
        if (gameState != GameState.WEAPON_SELECT) {
            map.render();
        }

        // World entities — only shown during PLAYING / PAUSED.
        // WEAPON_SELECT and MENU/DEAD show a clean floor-only background.
        if (gameState == GameState.PLAYING || gameState == GameState.PAUSED) {
            for (Entity e : entities) {
                // Hidden until first packet / downed opponents are not drawn
                if (e instanceof RemotePlayer && !((RemotePlayer) e).isAliveRemote()) {
                    continue;
                }
                // Enemy and RemotePlayer meshes are baked at the origin → translate per frame
                if (e instanceof Enemy || e instanceof RemotePlayer) {
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
        hud.render(timer.getFPS(), aimHit, notif,
                   player.getHealth(), 100f,
                   weaponNames, ammoTypes, currentSlot,
                   previewWeaponIndex, previewStats,
                   gameState, netStatus,
                   Config.WINDOW_WIDTH, Config.WINDOW_HEIGHT);

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
