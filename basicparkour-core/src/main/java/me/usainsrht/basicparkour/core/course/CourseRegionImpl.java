package me.usainsrht.basicparkour.core.course;

import me.usainsrht.basicparkour.api.course.CourseRegion;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * Concrete AABB implementation of {@link CourseRegion}.
 * Min and max corners are normalised on construction so that
 * {@code min.x <= max.x}, {@code min.y <= max.y}, {@code min.z <= max.z}.
 */
public final class CourseRegionImpl implements CourseRegion {

    private final Location min;
    private final Location max;

    /**
     * Creates a region from two arbitrary corner points.
     * The corners are normalised automatically.
     *
     * @param a one corner
     * @param b the opposite corner
     */
    public CourseRegionImpl(@NotNull Location a, @NotNull Location b) {
        this.min = new Location(
            a.getWorld(),
            Math.min(a.getX(), b.getX()),
            Math.min(a.getY(), b.getY()),
            Math.min(a.getZ(), b.getZ())
        );
        this.max = new Location(
            a.getWorld(),
            Math.max(a.getX(), b.getX()),
            Math.max(a.getY(), b.getY()),
            Math.max(a.getZ(), b.getZ())
        );
    }

    @Override @NotNull public Location getMin() { return min.clone(); }
    @Override @NotNull public Location getMax() { return max.clone(); }

    @Override
    public String toString() {
        return "CourseRegion{min=%s, max=%s}".formatted(formatLoc(min), formatLoc(max));
    }

    private static String formatLoc(Location l) {
        return "(%.1f, %.1f, %.1f)".formatted(l.getX(), l.getY(), l.getZ());
    }
}
