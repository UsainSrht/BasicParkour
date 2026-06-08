package me.usainsrht.basicparkour.core.util;

import me.usainsrht.basicparkour.core.modifier.BoostBlockModifier;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

/**
 * Utility methods for computing direction and velocity vectors used by block modifiers.
 */
public final class VectorUtil {

    private VectorUtil() {}

    /**
     * Computes a unit vector pointing in the direction the player is facing,
     * derived from the player's yaw (horizontal rotation), ignoring pitch.
     *
     * <p>This is used by {@link BoostBlockModifier} to push
     * the player forward regardless of where they are looking vertically.</p>
     *
     * @param yawDegrees the player's yaw in degrees (0 = south, 90 = west, ±180 = north, -90 = east)
     * @return a unit direction vector with Y=0
     */
    @NotNull
    public static Vector forwardVector(float yawDegrees) {
        double radians = Math.toRadians(yawDegrees);
        double x = -Math.sin(radians);
        double z =  Math.cos(radians);
        return new Vector(x, 0, z).normalize();
    }

    /**
     * Clamps a double value between a minimum and maximum.
     *
     * @param value the value to clamp
     * @param min   minimum bound
     * @param max   maximum bound
     * @return clamped value
     */
    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
