package me.usainsrht.basicparkour.core.generator;

import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.course.DieMode;
import me.usainsrht.basicparkour.api.generator.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class PlatformCheckpointTriggerTest {

    private GeneratorTemplate createTemplate(String shape) {
        GeneratorTheme theme = new GeneratorTheme(
            "Test",
            List.of(Material.OAK_PLANKS),
            List.of(),
            List.of()
        );
        return new GeneratorTemplate(
            "test_trigger",
            "<white>Test</white>",
            GeneratorDifficulty.MEDIUM,
            theme,
            LengthType.INFINITE,
            30,
            BlockMode.SLIDING_WINDOW,
            15,
            30,
            EntryMode.DEDICATED_WORLD,
            "world",
            10000,
            false,
            null,
            "test_trigger",
            null,
            List.of(shape),
            2,
            4,
            -1,
            1,
            1,
            1,
            0.0,
            0.0,
            Map.of(),
            DieMode.FAIL_DIE,
            BlockAppearAnimation.NONE,
            SlidingWindowMode.DUAL,
            15.0,
            null,
            null,
            90.0,
            null,
            0,
            15
        );
    }

    @Test
    public void testSingleBlockTriggerRegionBounds() {
        GeneratorTemplate template = createTemplate("1x1");
        Random rng = new Random(42);
        Location startLoc = new Location(null, 10, 64, 10);

        JumpToJumpAlgorithm.GeneratedPlatform platform =
            JumpToJumpAlgorithm.generateStart(template, null, startLoc, 0f, rng);

        Checkpoint cp = platform.checkpoint();
        Location min = cp.getTriggerRegion().getMin();
        Location max = cp.getTriggerRegion().getMax();

        // 1x1 platform at (10, 64, 10):
        // Horizontal bounds must strictly match [10.0, 11.0] and [10.0, 11.0], not inflated to [9.0, 12.0]!
        assertEquals(10.0, min.getX(), 1e-4);
        assertEquals(11.0, max.getX(), 1e-4);
        assertEquals(10.0, min.getZ(), 1e-4);
        assertEquals(11.0, max.getZ(), 1e-4);

        // Vertical bounds must sit above the block top (Y = 65.0 to 67.0)
        assertEquals(65.0, min.getY(), 1e-4);
        assertEquals(67.0, max.getY(), 1e-4);

        // Player standing on the platform (center at 10.5, 65.0, 10.5)
        BoundingBox standingBox = new BoundingBox(10.2, 65.0, 10.2, 10.8, 66.8, 10.8);
        assertTrue(cp.getTriggerRegion().overlaps(standingBox),
            "Player standing on platform must overlap the trigger region");

        // Player standing on previous platform (e.g. at X = 7.0, with gap of 2 blocks at X=8, 9)
        BoundingBox previousPlatformBox = new BoundingBox(7.2, 65.0, 10.2, 7.8, 66.8, 10.8);
        assertFalse(cp.getTriggerRegion().overlaps(previousPlatformBox),
            "Player on previous platform must NOT overlap next platform trigger region");

        // Player in mid-air leaving previous platform (at X = 8.5)
        BoundingBox midAirLeavingBox = new BoundingBox(8.2, 65.5, 10.2, 8.8, 67.3, 10.8);
        assertFalse(cp.getTriggerRegion().overlaps(midAirLeavingBox),
            "Player jumping in mid-air over the gap must NOT trigger the next platform");
    }

    @Test
    public void testMultiBlockTriggerRegionBounds() {
        GeneratorTemplate template = createTemplate("2x2");
        Random rng = new Random(42);
        Location startLoc = new Location(null, 10, 64, 10);

        JumpToJumpAlgorithm.GeneratedPlatform platform =
            JumpToJumpAlgorithm.generateStart(template, null, startLoc, 0f, rng);

        Checkpoint cp = platform.checkpoint();
        Location min = cp.getTriggerRegion().getMin();
        Location max = cp.getTriggerRegion().getMax();

        // 2x2 platform occupies [10.0, 12.0] in X and Z
        assertEquals(10.0, min.getX(), 1e-4);
        assertEquals(12.0, max.getX(), 1e-4);
        assertEquals(10.0, min.getZ(), 1e-4);
        assertEquals(12.0, max.getZ(), 1e-4);
        assertEquals(65.0, min.getY(), 1e-4);
        assertEquals(67.0, max.getY(), 1e-4);

        // Player standing on opposite corner of 2x2
        BoundingBox cornerBox = new BoundingBox(11.2, 65.0, 11.2, 11.8, 66.8, 11.8);
        assertTrue(cp.getTriggerRegion().overlaps(cornerBox));
    }
}
