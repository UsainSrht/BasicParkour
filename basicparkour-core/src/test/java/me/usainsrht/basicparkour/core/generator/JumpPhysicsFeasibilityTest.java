package me.usainsrht.basicparkour.core.generator;

import me.usainsrht.basicparkour.api.course.DieMode;
import me.usainsrht.basicparkour.api.generator.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class JumpPhysicsFeasibilityTest {

    private GeneratorTemplate createTemplate(GeneratorDifficulty difficulty, String shape) {
        GeneratorTheme theme = new GeneratorTheme(
            "Test",
            List.of(Material.STONE),
            List.of(Material.SMOOTH_STONE),
            List.of(Material.OAK_SLAB)
        );
        return new GeneratorTemplate(
            "test_" + difficulty.name().toLowerCase(),
            "<white>Test " + difficulty.name() + "</white>",
            difficulty,
            theme,
            LengthType.INFINITE,
            30,
            BlockMode.SLIDING_WINDOW,
            -1,
            30,
            EntryMode.DEDICATED_WORLD,
            "world",
            10000,
            false,
            null,
            "test_course",
            null,
            List.of(shape),
            -1, -1,
            Integer.MIN_VALUE, Integer.MAX_VALUE,
            -1, -1,
            -1.0, -1.0,
            Map.of(),
            DieMode.FAIL_DIE,
            BlockAppearAnimation.NONE,
            SlidingWindowMode.DUAL,
            0.0,
            null,
            null,
            90.0,
            null,
            0,
            15
        );
    }

    @Test
    public void testPhysicsDistanceLimitsConformToVanillaMechanics() {
        // Upward (+1) jump limits:
        // Straight line sprint jump up 1 block: max ~4.0 blocks
        assertTrue(JumpToJumpAlgorithm.getMaxAllowedDistance(1, 0.0) <= 4.10);
        assertTrue(JumpToJumpAlgorithm.getMaxAllowedDistance(1, 0.0) >= 4.0);
        // Turns severely reduce upward jump distance
        assertTrue(JumpToJumpAlgorithm.getMaxAllowedDistance(1, 45.0) < 2.5);

        // Flat jump limits:
        // Straight line 4-block jump (center distance 5.0):
        assertTrue(JumpToJumpAlgorithm.getMaxAllowedDistance(0, 0.0) >= 5.0);
        assertTrue(JumpToJumpAlgorithm.getMaxAllowedDistance(0, 0.0) <= 5.15);
        // Turn of 45 degrees caps distance to ~3.7
        assertTrue(JumpToJumpAlgorithm.getMaxAllowedDistance(0, 45.0) <= 3.8);
        // Sharp turn (90 degrees) caps distance to walking step (~2.45)
        assertTrue(JumpToJumpAlgorithm.getMaxAllowedDistance(0, 85.0) <= 2.5);

        // Downward jump limits: increased air time extends distance
        assertTrue(JumpToJumpAlgorithm.getMaxAllowedDistance(-1, 0.0) > JumpToJumpAlgorithm.getMaxAllowedDistance(0, 0.0));
        assertTrue(JumpToJumpAlgorithm.getMaxAllowedDistance(-2, 0.0) > JumpToJumpAlgorithm.getMaxAllowedDistance(-1, 0.0));
        assertTrue(JumpToJumpAlgorithm.getMaxAllowedDistance(-3, 0.0) >= 6.0);
    }

    @Test
    public void test1000JumpsPerDifficultyAre100PercentPossible() {
        Random rng = new Random(2026);
        World mockWorld = null;

        for (GeneratorDifficulty diff : GeneratorDifficulty.values()) {
            GeneratorTemplate template = createTemplate(diff, "1x1");
            Location start = new Location(mockWorld, 0, 100, 0);
            float baseYaw = 0f;

            JumpToJumpAlgorithm.GeneratedPlatform current =
                JumpToJumpAlgorithm.generateStart(template, mockWorld, start, baseYaw, rng);

            Location lastCenter = current.centerLocation();
            float lastYaw = current.outgoingYawDeg();

            List<Location> recentCenters = new ArrayList<>();
            recentCenters.add(lastCenter);

            for (int i = 1; i <= 500; i++) {
                JumpToJumpAlgorithm.GeneratedPlatform next =
                    JumpToJumpAlgorithm.generateNext(
                        template, mockWorld, lastCenter, lastYaw, baseYaw, start, recentCenters, i, rng);

                Location center = next.centerLocation();
                int dy = center.getBlockY() - lastCenter.getBlockY();
                double horizDist = Math.hypot(center.getX() - lastCenter.getX(), center.getZ() - lastCenter.getZ());
                float turnAngle = Math.abs(JumpToJumpAlgorithm.normalizeDegrees(next.outgoingYawDeg() - lastYaw));

                // 1. Height delta must strictly be <= 1 (vanilla jump limit is 1.25)
                assertTrue(dy <= 1,
                    "[" + diff + "] Platform " + i + " height delta was " + dy + " (impossible in vanilla)");

                // 2. Minimum distance must prevent block overlap (>= 1.8)
                assertTrue(horizDist >= 1.8,
                    "[" + diff + "] Platform " + i + " distance was too small: " + horizDist);

                // 3. Maximum distance must strictly satisfy vanilla jump physics for the given height and turn angle
                double maxAllowed = JumpToJumpAlgorithm.getMaxAllowedDistance(dy, turnAngle);
                assertTrue(horizDist <= maxAllowed + 0.01,
                    "[" + diff + "] Platform " + i + " distance " + horizDist +
                    " exceeded maximum physically possible jump " + maxAllowed + " (dy=" + dy + ", turn=" + turnAngle + ")");

                // 4. Overlap check with recent platforms
                for (Location rc : recentCenters) {
                    double dSq = Math.pow(center.getX() - rc.getX(), 2) + Math.pow(center.getZ() - rc.getZ(), 2);
                    int absDy = Math.abs(center.getBlockY() - rc.getBlockY());
                    assertFalse(dSq < 2.25 && absDy <= 2,
                        "[" + diff + "] Platform " + i + " collided with previous platform at " + rc);
                }

                recentCenters.add(center);
                if (recentCenters.size() > 30) recentCenters.remove(0);
                lastCenter = center;
                lastYaw = next.outgoingYawDeg();
            }
        }
    }

    @Test
    public void testDifficultyDifferentiatesPacingGapsAndTurns() {
        Random rng = new Random(42);
        World mockWorld = null;
        Location start = new Location(mockWorld, 0, 100, 0);

        // Run EASY
        GeneratorTemplate easyTpl = createTemplate(GeneratorDifficulty.EASY, "1x1");
        double easyTotalGap = 0;
        int easyClimbCount = 0;
        JumpToJumpAlgorithm.GeneratedPlatform prev =
            JumpToJumpAlgorithm.generateStart(easyTpl, mockWorld, start, 0f, rng);
        for (int i = 1; i <= 200; i++) {
            JumpToJumpAlgorithm.GeneratedPlatform next =
                JumpToJumpAlgorithm.generateNext(easyTpl, mockWorld, prev.centerLocation(), prev.outgoingYawDeg(), 0f, start, null, i, rng);
            double dist = Math.hypot(next.centerLocation().getX() - prev.centerLocation().getX(),
                                     next.centerLocation().getZ() - prev.centerLocation().getZ());
            easyTotalGap += dist;
            if (next.centerLocation().getBlockY() > prev.centerLocation().getBlockY()) easyClimbCount++;
            prev = next;
        }
        double easyAvgGap = easyTotalGap / 200.0;

        // Run EXPERT
        GeneratorTemplate expertTpl = createTemplate(GeneratorDifficulty.EXPERT, "1x1");
        double expertTotalGap = 0;
        int expertClimbCount = 0;
        double expertMaxTurn = 0;
        prev = JumpToJumpAlgorithm.generateStart(expertTpl, mockWorld, start, 0f, rng);
        for (int i = 1; i <= 200; i++) {
            float lastYaw = prev.outgoingYawDeg();
            JumpToJumpAlgorithm.GeneratedPlatform next =
                JumpToJumpAlgorithm.generateNext(expertTpl, mockWorld, prev.centerLocation(), lastYaw, 0f, start, null, i, rng);
            double dist = Math.hypot(next.centerLocation().getX() - prev.centerLocation().getX(),
                                     next.centerLocation().getZ() - prev.centerLocation().getZ());
            expertTotalGap += dist;
            if (next.centerLocation().getBlockY() > prev.centerLocation().getBlockY()) expertClimbCount++;
            float turn = Math.abs(JumpToJumpAlgorithm.normalizeDegrees(next.outgoingYawDeg() - lastYaw));
            if (turn > expertMaxTurn) expertMaxTurn = turn;
            prev = next;
        }
        double expertAvgGap = expertTotalGap / 200.0;

        // EASY has shorter average gap than EXPERT
        assertTrue(easyAvgGap < expertAvgGap,
            "EASY avg gap (" + easyAvgGap + ") should be significantly less than EXPERT avg gap (" + expertAvgGap + ")");

        // EASY has 0 climbs (+1 height), while EXPERT introduces technical climbs
        assertEquals(0, easyClimbCount, "EASY parkour should have 0 upward (+1) climbs");
        assertTrue(expertClimbCount > 0, "EXPERT parkour must feature upward climbs");

        // EXPERT exhibits sharp agility turns
        assertTrue(expertMaxTurn > 35.0, "EXPERT should feature sharp turns, found max turn: " + expertMaxTurn);
    }
}
