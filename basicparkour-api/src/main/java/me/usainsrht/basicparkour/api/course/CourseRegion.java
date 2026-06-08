package me.usainsrht.basicparkour.api.course;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * An axis-aligned bounding box (AABB) used to define spatial regions within a course.
 *
 * <p>Regions are defined by two corner points — {@code min} and {@code max} —
 * in world-space coordinates. They are used to define start zones, end zones,
 * and checkpoint trigger areas.</p>
 */
public interface CourseRegion {

    /**
     * Returns the minimum corner of the bounding box.
     * All coordinates of {@code min} are less than or equal to the corresponding
     * coordinates of {@code max}.
     *
     * @return the min corner location
     */
    @NotNull
    Location getMin();

    /**
     * Returns the maximum corner of the bounding box.
     *
     * @return the max corner location
     */
    @NotNull
    Location getMax();

    /**
     * Tests whether the given location falls within this region (inclusive).
     *
     * @param location the location to test
     * @return {@code true} if the location is inside the region
     */
    default boolean contains(@NotNull Location location) {
        if (!location.getWorld().equals(getMin().getWorld())) return false;
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        return x >= getMin().getX() && x <= getMax().getX()
            && y >= getMin().getY() && y <= getMax().getY()
            && z >= getMin().getZ() && z <= getMax().getZ();
    }

    /**
     * Returns the center point of this region.
     *
     * @return center location
     */
    @NotNull
    default Location getCenter() {
        return new Location(
            getMin().getWorld(),
            (getMin().getX() + getMax().getX()) / 2.0,
            (getMin().getY() + getMax().getY()) / 2.0,
            (getMin().getZ() + getMax().getZ()) / 2.0
        );
    }
}
