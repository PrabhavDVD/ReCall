package com.recall.net;

import com.recall.Config;
import com.recall.util.Logger;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * NetworkManager — minimal UDP peer for basic 1v1 play and phase testing.
 *
 * <h2>Topology</h2>
 * Symmetric peer-to-peer over UDP. One side {@link #host(int, String) HOSTs} (binds a
 * known port and waits to learn the client's address from its first packet). The other
 * side {@link #join(String, int, String) JOINs} by IP and starts sending immediately.
 * After the first exchange both sides are equivalent: each broadcasts its own player
 * snapshot {@code NET_SEND_RATE_HZ} times per second, and reports any hit it lands on
 * the opponent.
 *
 * <h2>Authority model (Phase 2.5 — intentionally simple)</h2>
 * Each peer owns its <em>own</em> health. When you shoot the opponent locally you send
 * a {@code HIT} packet; the opponent applies that damage to its own player and broadcasts
 * the new health back in its next {@code STATE}. There is no anti-cheat or server-side
 * validation yet — that authority model arrives in Phase 4.
 *
 * <h2>Protocol</h2>
 * UTF-8 text, pipe-delimited, exactly one message per datagram:
 * <pre>
 *   HELLO|name                              handshake / address discovery
 *   STATE|x|y|z|yaw|pitch|health|alive      player snapshot (~NET_SEND_RATE_HZ)
 *   HIT|damage                              "I hit you for &lt;damage&gt;" (headshot already applied)
 *   BYE                                     graceful disconnect
 * </pre>
 *
 * <h2>Threading</h2>
 * A daemon thread blocks on {@code socket.receive()} and publishes results to volatile
 * fields / a concurrent queue. The game thread reads those each frame and does all
 * sending. {@link #shutdown()} closes the socket, which unblocks the receive thread.
 */
public class NetworkManager {

    /** Which side opened the session. After connect, behaviour is symmetric. */
    public enum Role { HOST, CLIENT }

    private final Role   role;
    private final String localName;

    private DatagramSocket socket;
    private volatile InetAddress remoteAddr;   // learned (host) or set (client)
    private volatile int         remotePort;

    private Thread          recvThread;
    private volatile boolean running;

    // ── State published by the receive thread, read by the game thread ──────────
    private volatile PlayerState remoteState;       // latest opponent snapshot (newest wins)
    private volatile long        lastRecvNanos;     // timestamp of last packet
    private volatile boolean      remoteHandshook;  // have we ever heard from the peer?
    private final ConcurrentLinkedQueue<Float> incomingHits = new ConcurrentLinkedQueue<>();

    // ── Send pacing (game thread) ───────────────────────────────────────────────
    private float sendAccumulator;
    private static final float SEND_INTERVAL = 1f / Config.NET_SEND_RATE_HZ;

    private NetworkManager(Role role, String name) {
        this.role      = role;
        this.localName = name;
    }

    // =========================================================================
    // Construction
    // =========================================================================

    /** Start hosting: bind the given UDP port and wait for a client to say HELLO. */
    public static NetworkManager host(int port, String name) throws Exception {
        NetworkManager nm = new NetworkManager(Role.HOST, name);
        nm.socket = new DatagramSocket(port);
        nm.startReceiveThread();
        Logger.info("[NET] Hosting on UDP port " + port + " — waiting for a player to join");
        return nm;
    }

    /** Start as client: target host {@code ip:port} and send a HELLO immediately. */
    public static NetworkManager join(String hostIp, int port, String name) throws Exception {
        NetworkManager nm = new NetworkManager(Role.CLIENT, name);
        nm.socket     = new DatagramSocket();             // ephemeral local port
        nm.remoteAddr = InetAddress.getByName(hostIp);    // may throw if IP unparseable
        nm.remotePort = port;
        nm.startReceiveThread();
        nm.sendRaw("HELLO|" + name);
        Logger.info("[NET] Joining " + hostIp + ":" + port + " as " + name);
        return nm;
    }

    private void startReceiveThread() {
        running = true;
        recvThread = new Thread(this::receiveLoop, "recall-net-recv");
        recvThread.setDaemon(true);
        recvThread.start();
    }

    // =========================================================================
    // Receive thread
    // =========================================================================

    private void receiveLoop() {
        byte[] buf = new byte[Config.NET_PACKET_BYTES];
        while (running) {
            try {
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                socket.receive(pkt);   // blocks until a datagram arrives or socket closes

                // The host discovers the client's return address from the first packet.
                if (remoteAddr == null) {
                    remoteAddr = pkt.getAddress();
                    remotePort = pkt.getPort();
                    Logger.info("[NET] Peer connected from "
                            + remoteAddr.getHostAddress() + ":" + remotePort);
                }

                String msg = new String(pkt.getData(), 0, pkt.getLength(), StandardCharsets.UTF_8);
                handle(msg);

            } catch (Exception e) {
                // During shutdown socket.close() makes receive() throw — that's expected.
                if (running) Logger.error("[NET] receive error: " + e.getMessage());
            }
        }
    }

    private void handle(String msg) {
        lastRecvNanos   = System.nanoTime();
        remoteHandshook = true;

        String[] f = msg.split("\\|");
        if (f.length == 0) return;

        switch (f[0]) {
            case "STATE": {
                PlayerState s = PlayerState.fromBody(f, 1);
                if (s != null) remoteState = s;
                break;
            }
            case "HIT": {
                try {
                    incomingHits.add(Float.parseFloat(f[1]));
                } catch (Exception ignored) { /* malformed hit — drop it */ }
                break;
            }
            case "HELLO":
                // Address was captured above; nothing more to do.
                break;
            case "BYE":
                remoteHandshook = false;
                remoteState     = null;
                Logger.info("[NET] Peer disconnected (BYE)");
                break;
            default:
                // Unknown message — ignore (forward compatibility).
                break;
        }
    }

    // =========================================================================
    // Send (game thread)
    // =========================================================================

    /**
     * Broadcast the local snapshot at the configured tick rate. Call every frame;
     * the method paces itself internally so it only sends ~{@code NET_SEND_RATE_HZ}/sec.
     */
    public void update(float dt, PlayerState local) {
        if (remoteAddr == null) return;   // no peer address yet — nothing to send to
        sendAccumulator += dt;
        if (sendAccumulator >= SEND_INTERVAL) {
            sendAccumulator = 0f;
            sendRaw("STATE|" + local.toBody());
        }
    }

    /**
     * Report a hit we just landed on the opponent. The damage value already includes
     * any headshot multiplier (it is the amount our local bullet computed).
     */
    public void sendHit(float damage) {
        sendRaw(String.format(Locale.US, "HIT|%.1f", damage));
    }

    private void sendRaw(String msg) {
        if (remoteAddr == null || socket == null) return;
        try {
            byte[] data = msg.getBytes(StandardCharsets.UTF_8);
            socket.send(new DatagramPacket(data, data.length, remoteAddr, remotePort));
        } catch (Exception e) {
            Logger.error("[NET] send error: " + e.getMessage());
        }
    }

    // =========================================================================
    // Game-thread accessors
    // =========================================================================

    /** Latest opponent snapshot, or null if none received yet. */
    public PlayerState getRemoteState() { return remoteState; }

    /** Drain the next pending incoming hit's damage, or null if the queue is empty. */
    public Float pollHit() { return incomingHits.poll(); }

    /** True once we have a peer address and have heard from it within the timeout. */
    public boolean isConnected() {
        if (!remoteHandshook) return false;
        long ageNanos = System.nanoTime() - lastRecvNanos;
        return ageNanos < (long) (Config.NET_TIMEOUT_SEC * 1_000_000_000L);
    }

    public Role getRole() { return role; }

    /**
     * Short, font-safe status line for the HUD (uppercase + digits + ':' '.' '-' ' ').
     */
    public String statusLine() {
        if (role == Role.HOST && remoteAddr == null) {
            return "NET: HOSTING - WAITING FOR PLAYER";
        }
        if (isConnected()) {
            String ip = (remoteAddr != null) ? remoteAddr.getHostAddress() : "?";
            return "NET: CONNECTED " + ip;
        }
        return remoteHandshook ? "NET: CONNECTION LOST" : "NET: CONNECTING";
    }

    // =========================================================================
    // Shutdown
    // =========================================================================

    /** Send a BYE, stop the receive thread, and close the socket. Safe to call once. */
    public void shutdown() {
        running = false;
        try { sendRaw("BYE"); } catch (Exception ignored) { }
        if (socket != null && !socket.isClosed()) {
            socket.close();   // unblocks receiveLoop()'s socket.receive()
        }
        Logger.info("[NET] Shut down");
    }
}
