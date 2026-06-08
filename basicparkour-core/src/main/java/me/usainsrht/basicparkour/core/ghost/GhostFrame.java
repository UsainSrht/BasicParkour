package me.usainsrht.basicparkour.core.ghost;

/**
 * An immutable snapshot of a player's position at a point in time.
 * Used by {@link GhostRecorderImpl} to record movement paths.
 *
 * @param x         world X coordinate
 * @param y         world Y coordinate
 * @param z         world Z coordinate
 * @param yaw       player yaw in degrees
 * @param pitch     player pitch in degrees
 * @param timestamp epoch millisecond timestamp of this frame
 */
public record GhostFrame(double x, double y, double z, float yaw, float pitch, long timestamp) {

    /** Approximate byte size per frame when serialised (for buffer estimation). */
    public static final int BYTES_ESTIMATE = Double.BYTES * 3 + Float.BYTES * 2 + Long.BYTES;
}
