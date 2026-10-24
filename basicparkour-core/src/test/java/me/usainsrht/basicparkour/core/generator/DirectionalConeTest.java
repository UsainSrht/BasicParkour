package me.usainsrht.basicparkour.core.generator;

import me.usainsrht.basicparkour.api.course.DieMode;
import me.usainsrht.basicparkour.api.generator.BlockAppearAnimation;
import me.usainsrht.basicparkour.api.generator.BlockMode;
import me.usainsrht.basicparkour.api.generator.EntryMode;
import me.usainsrht.basicparkour.api.generator.GeneratorDifficulty;
import me.usainsrht.basicparkour.api.generator.GeneratorTemplate;
import me.usainsrht.basicparkour.api.generator.GeneratorTheme;
import me.usainsrht.basicparkour.api.generator.LengthType;
import me.usainsrht.basicparkour.api.generator.SlidingWindowMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class DirectionalConeTest {

    private GeneratorTemplate createTemplate(double directionCone, int corridorWidth, int maxHeightDev) {
        GeneratorTheme theme = new GeneratorTheme(
            "Test",
            List.of(Material.STONE),
            List.of(),
            List.of()
        );
        return new GeneratorTemplate(
            "test_cone",
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
            "test_cone",
            null,
            List.of("1x1"),
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
            SlidingWindowMode.BLOCKS,
            0.0,
            null,
            null,
            directionCone,
            null,
            corridorWidth,
            maxHeightDev
        );
    }

    @Test
    public void testNormalizeDegrees() {
        assertEquals(0f, JumpToJumpAlgorithm.normalizeDegrees(0f), 0.001f);
        assertEquals(90f, JumpToJumpAlgorithm.normalizeDegrees(90f), 0.001f);
        assertEquals(180f, JumpToJumpAlgorithm.normalizeDegrees(180f), 0.001f);
        assertEquals(-90f, JumpToJumpAlgorithm.normalizeDegrees(270f), 0.001f);
        assertEquals(0f, JumpToJumpAlgorithm.normalizeDegrees(360f), 0.001f);
        assertEquals(170f, JumpToJumpAlgorithm.normalizeDegrees(-190f), 0.001f);
        assertEquals(-170f, JumpToJumpAlgorithm.normalizeDegrees(190f), 0.001f);
    }

    @Test
    public void test90DegreeConeGoesStrictlyForwardAndNeverLoops() {
        // Base yaw 0 = South (+Z). Cone 90 degrees means yaw must stay in [-45, +45].
        GeneratorTemplate template = createTemplate(90.0, 0, 0);
        Random rng = new Random(42);

        Location start = new Location(null, 100, 64, 100);
        float baseYaw = 0f;

        JumpToJumpAlgorithm.GeneratedPlatform current =
            JumpToJumpAlgorithm.generateStart(template, null, start, baseYaw, rng);

        Location lastCenter = current.centerLocation();
        float lastYaw = current.outgoingYawDeg();

        List<Location> recentCenters = new ArrayList<>();
        recentCenters.add(lastCenter);

        for (int i = 1; i <= 200; i++) {
            JumpToJumpAlgorithm.GeneratedPlatform next =
                JumpToJumpAlgorithm.generateNext(
                    template, null, lastCenter, lastYaw, baseYaw, start, recentCenters, i, rng);

            // 1. Outgoing yaw must stay inside [-45, +45]
            float yawDiff = Math.abs(JumpToJumpAlgorithm.normalizeDegrees(next.outgoingYawDeg() - baseYaw));
            assertTrue(yawDiff <= 45.001f,
                "Platform " + i + " yaw " + next.outgoingYawDeg() + " exceeded cone (diff=" + yawDiff + ")");

            // 2. Forward Z displacement must strictly increase on every jump
            assertTrue(next.centerLocation().getZ() > lastCenter.getZ(),
                "Platform " + i + " did not move forward in Z: prev=" + lastCenter.getZ() + " new=" + next.centerLocation().getZ());

            // 3. Overall displacement from start must stay inside the 90 degree spatial cone
            double dxTotal = next.centerLocation().getX() - start.getX();
            double dzTotal = next.centerLocation().getZ() - start.getZ();
            assertTrue(dzTotal > 0, "Platform " + i + " dzTotal was not positive");
            assertTrue(Math.abs(dxTotal) <= dzTotal + 2.0, // |dx/dz| <= tan(45) = 1.0 (with 2 block discretization buffer)
                "Platform " + i + " drifted outside spatial cone: dx=" + dxTotal + " dz=" + dzTotal);

            recentCenters.add(next.centerLocation());
            if (recentCenters.size() > 30) recentCenters.remove(0);
            lastCenter = next.centerLocation();
            lastYaw = next.outgoingYawDeg();
        }
    }

    @Test
    public void testCorridorWidthKeepsParkourWithinBounds() {
        // Corridor width = 20 blocks.
        GeneratorTemplate template = createTemplate(90.0, 20, 0);
        Random rng = new Random(12345);

        Location start = new Location(null, 0, 64, 0);
        float baseYaw = 0f;

        JumpToJumpAlgorithm.GeneratedPlatform current =
            JumpToJumpAlgorithm.generateStart(template, null, start, baseYaw, rng);

        Location lastCenter = current.centerLocation();
        float lastYaw = current.outgoingYawDeg();

        List<Location> recentCenters = new ArrayList<>();
        recentCenters.add(lastCenter);

        for (int i = 1; i <= 200; i++) {
            JumpToJumpAlgorithm.GeneratedPlatform next =
                JumpToJumpAlgorithm.generateNext(
                    template, null, lastCenter, lastYaw, baseYaw, start, recentCenters, i, rng);

            // Lateral offset along X must stay within corridor/2 (with slight discretization buffer)
            double lateralOffset = Math.abs(next.centerLocation().getX() - start.getX());
            assertTrue(lateralOffset <= 12.0,
                "Platform " + i + " breached corridor: lateral offset " + lateralOffset + " > 12.0");

            recentCenters.add(next.centerLocation());
            if (recentCenters.size() > 30) recentCenters.remove(0);
            lastCenter = next.centerLocation();
            lastYaw = next.outgoingYawDeg();
        }
    }

    @Test
    public void testHeightTetheringPreventsInfiniteVerticalDrift() {
        // Max height deviation = 10 blocks.
        GeneratorTemplate template = createTemplate(90.0, 0, 10);
        Random rng = new Random(999);

        Location start = new Location(null, 0, 64, 0);
        float baseYaw = 0f;

        JumpToJumpAlgorithm.GeneratedPlatform current =
            JumpToJumpAlgorithm.generateStart(template, null, start, baseYaw, rng);

        Location lastCenter = current.centerLocation();
        float lastYaw = current.outgoingYawDeg();

        List<Location> recentCenters = new ArrayList<>();
        recentCenters.add(lastCenter);

        for (int i = 1; i <= 200; i++) {
            JumpToJumpAlgorithm.GeneratedPlatform next =
                JumpToJumpAlgorithm.generateNext(
                    template, null, lastCenter, lastYaw, baseYaw, start, recentCenters, i, rng);

            int yOffset = Math.abs(next.centerLocation().getBlockY() - start.getBlockY());
            assertTrue(yOffset <= 11,
                "Platform " + i + " breached max height deviation: yOffset=" + yOffset);

            recentCenters.add(next.centerLocation());
            if (recentCenters.size() > 30) recentCenters.remove(0);
            lastCenter = next.centerLocation();
            lastYaw = next.outgoingYawDeg();
        }
    }

    @Test
    public void test360DegreeUnconstrainedTurnsFreelyWithoutColliding() {
        // 360 degrees allows turning in any direction
        GeneratorTemplate template = createTemplate(360.0, 0, 0);
        Random rng = new Random(777);

        Location start = new Location(null, 500, 64, 500);
        float baseYaw = 0f;

        JumpToJumpAlgorithm.GeneratedPlatform current =
            JumpToJumpAlgorithm.generateStart(template, null, start, baseYaw, rng);

        Location lastCenter = current.centerLocation();
        float lastYaw = current.outgoingYawDeg();

        List<Location> recentCenters = new ArrayList<>();
        recentCenters.add(lastCenter);

        boolean turnedNegative = false;
        boolean turnedPositive = false;

        for (int i = 1; i <= 150; i++) {
            JumpToJumpAlgorithm.GeneratedPlatform next =
                JumpToJumpAlgorithm.generateNext(
                    template, null, lastCenter, lastYaw, baseYaw, start, recentCenters, i, rng);

            if (next.outgoingYawDeg() < -60) turnedNegative = true;
            if (next.outgoingYawDeg() > 60) turnedPositive = true;

            // Check no collision with recent platforms
            for (Location rc : recentCenters) {
                double distSq = Math.pow(next.centerLocation().getX() - rc.getX(), 2) +
                                Math.pow(next.centerLocation().getZ() - rc.getZ(), 2);
                int dy = Math.abs(next.centerLocation().getBlockY() - rc.getBlockY());
                assertFalse(distSq < 2.25 && dy <= 2,
                    "Platform " + i + " overlapped with recent platform at " + rc);
            }

            recentCenters.add(next.centerLocation());
            if (recentCenters.size() > 30) recentCenters.remove(0);
            lastCenter = next.centerLocation();
            lastYaw = next.outgoingYawDeg();
        }

        assertTrue(turnedNegative || turnedPositive, "360 cone did not turn freely");
    }
}
