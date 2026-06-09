package com.recall.net;

import com.recall.physics.Vector3;
import java.util.Locale;

/**
 * PlayerState — immutable snapshot of one player's networked state.
 *
 * This is the payload that each peer broadcasts ~30x/second so the other side can
 * draw it. It carries only what the remote machine needs to render and reason about
 * the opponent: feet position, look angles, current health, and a liveness flag.
 *
 * Wire format (the "body", with no message tag — {@link NetworkManager} prepends
 * "STATE|"):
 *
 *   x|y|z|yaw|pitch|health|alive
 *
 * Numbers are formatted with {@link Locale#US} so the decimal separator is always
 * a dot, regardless of the host machine's locale (parsing with Float.parseFloat is
 * locale-independent, but formatting is not).
 */
public final class PlayerState {
    /** Feet position in world space (y=0 is the ground plane). */
    public final float x, y, z;
    /** Look angles in degrees (camera yaw/pitch). */
    public final float yaw, pitch;
    /** Current health in HP. */
    public final float health;
    /** True while the player is alive (health > 0 and not on the death screen). */
    public final boolean alive;

    public PlayerState(float x, float y, float z,
                       float yaw, float pitch,
                       float health, boolean alive) {
        this.x = x; this.y = y; this.z = z;
        this.yaw = yaw; this.pitch = pitch;
        this.health = health;
        this.alive = alive;
    }

    /** Serialize the 7 fields into a pipe-delimited body (no message tag). */
    public String toBody() {
        return String.format(Locale.US, "%.3f|%.3f|%.3f|%.2f|%.2f|%.1f|%d",
                x, y, z, yaw, pitch, health, alive ? 1 : 0);
    }

    /**
     * Parse a 7-field body produced by {@link #toBody()}.
     *
     * @param f   the message already split on '|'
     * @param off index of the first field (1 when the tag occupies index 0)
     * @return    the parsed state, or null if the fields are missing/malformed
     */
    public static PlayerState fromBody(String[] f, int off) {
        if (f == null || f.length < off + 7) return null;
        try {
            return new PlayerState(
                    Float.parseFloat(f[off]),
                    Float.parseFloat(f[off + 1]),
                    Float.parseFloat(f[off + 2]),
                    Float.parseFloat(f[off + 3]),
                    Float.parseFloat(f[off + 4]),
                    Float.parseFloat(f[off + 5]),
                    Integer.parseInt(f[off + 6]) != 0);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Convenience: feet position as a Vector3. */
    public Vector3 position() {
        return new Vector3(x, y, z);
    }
}
