package me.usainsrht.basicparkour.core.region;

import me.usainsrht.basicparkour.api.course.CourseRegion;
import me.usainsrht.basicparkour.core.course.CourseRegionImpl;
import org.bukkit.Location;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CourseRegionTest {

    @Test
    public void testCourseRegionOverlaps() {
        Location min = new Location(null, 10, 64, 20);
        Location max = new Location(null, 12, 66, 22);
        CourseRegion region = new CourseRegionImpl(min, max);

        // Player standing on a path block (Y = 63.9375) with bounding box height 1.8
        BoundingBox playerBoxOnPathBlock = new BoundingBox(
            9.7, 63.9375, 19.7,
            10.3, 65.7375, 20.3
        );
        assertTrue(region.overlaps(playerBoxOnPathBlock), "Player bounding box on path block should overlap portal/region");

        // Player completely outside
        BoundingBox playerBoxOutside = new BoundingBox(
            0, 64, 0,
            0.6, 65.8, 0.6
        );
        assertFalse(region.overlaps(playerBoxOutside), "Player far away should not overlap");
    }

    @Test
    public void testCourseRegionContainsWithVerticalTolerance() {
        Location min = new Location(null, 10, 64, 20);
        Location max = new Location(null, 12, 66, 22);
        CourseRegion region = new CourseRegionImpl(min, max);

        // Feet location on a path block at Y = 63.9375
        Location pathBlockFeet = new Location(null, 11.0, 63.9375, 21.0);
        assertTrue(region.contains(pathBlockFeet), "Feet on path block should be contained within region due to vertical tolerance");

        // Feet location on standard block Y = 64.0
        Location standardFeet = new Location(null, 11.0, 64.0, 21.0);
        assertTrue(region.contains(standardFeet), "Standard feet position should be contained");

        // Location way below min Y
        Location tooLow = new Location(null, 11.0, 63.0, 21.0);
        assertFalse(region.contains(tooLow), "Location too low should not be contained");
    }
}
