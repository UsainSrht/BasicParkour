package me.usainsrht.basicparkour.core.generator;

import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.course.DieMode;
import me.usainsrht.basicparkour.api.generator.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class StartingBlockAndFirstJumpTest {

    private GeneratorTemplate createTemplate(GeneratorDifficulty difficulty, String shape, double directionCone, Float directionYaw) {
        GeneratorTheme theme = new GeneratorTheme(
            "TestTheme",
            List.of(Material.OAK_PLANKS),
            List.of(Material.STRIPPED_OAK_LOG),
            List.of()
        );
        return new GeneratorTemplate(
            "test_walkin",
            "<white>Test Walk-In</white>",
            difficulty,
            theme,
            LengthType.INFINITE,
            30,
            BlockMode.SLIDING_WINDOW,
            15,
            30,
            EntryMode.WALK_IN,
            "world",
            10000,
            false,
            null,
            "test_walkin",
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
            BlockAppearAnimation.GROW,
            SlidingWindowMode.DUAL,
            7.0,
            null,
            null,
            directionCone,
            directionYaw,
            0,
            15
        );
    }

    @Test
    public void testGenerateExistingStartHasNoPlacedBlocksAndAccurateCheckpoint() {
        Location startLoc = new Location(null, 100, 64, 200);
        float yaw = 90.0f; // Facing West

        JumpToJumpAlgorithm.GeneratedPlatform startPlatform =
            JumpToJumpAlgorithm.generateExistingStart(null, startLoc, yaw);

        assertEquals(0, startPlatform.platformIndex());
        assertEquals(startLoc, startPlatform.centerLocation());
        assertEquals(yaw, startPlatform.outgoingYawDeg());

        // Must have ZERO placed blocks (pre-existing starting block, no placement in world)
        assertTrue(startPlatform.placedBlocks().isEmpty(),
            "0th platform for existing starting block must not contain any placed blocks");

        // Checkpoint 0 must be centered right on the starting block
        Checkpoint cp = startPlatform.checkpoint();
        assertEquals(0, cp.getIndex());
        assertEquals(100.5, cp.getSpawnLocation().getX(), 1e-4);
        assertEquals(65.0, cp.getSpawnLocation().getY(), 1e-4); // On top of block y=64
        assertEquals(200.5, cp.getSpawnLocation().getZ(), 1e-4);
        assertEquals(yaw, cp.getSpawnLocation().getYaw(), 1e-4);

        // Trigger region must tightly cover the starting block surface
        assertEquals(100.0, cp.getTriggerRegion().getMin().getX(), 1e-4);
        assertEquals(101.0, cp.getTriggerRegion().getMax().getX(), 1e-4);
        assertEquals(65.0, cp.getTriggerRegion().getMin().getY(), 1e-4);
        assertEquals(67.0, cp.getTriggerRegion().getMax().getY(), 1e-4);
        assertEquals(200.0, cp.getTriggerRegion().getMin().getZ(), 1e-4);
        assertEquals(201.0, cp.getTriggerRegion().getMax().getZ(), 1e-4);
    }

    @Test
    public void testFirstJumpGeneratesStrictlyForwardAndNeverBehind() {
        // Test all 4 cardinal base headings: 0 (South), 90 (West), 180 (North), -90 (East)
        float[] baseHeadings = {0f, 90f, 180f, -90f, 45f, 135f};
        Location startLoc = new Location(null, 50, 70, 50);

        for (float heading : baseHeadings) {
            double radBase = Math.toRadians(-heading);
            double fwdX = Math.sin(radBase);
            double fwdZ = Math.cos(radBase);

            for (GeneratorDifficulty diff : GeneratorDifficulty.values()) {
                GeneratorTemplate template = createTemplate(diff, "1x1", 90.0, heading);

                for (int seed = 1; seed <= 50; seed++) {
                    Random rng = new Random(seed * 31L);
                    JumpToJumpAlgorithm.GeneratedPlatform startPlat =
                        JumpToJumpAlgorithm.generateExistingStart(null, startLoc, heading);

                    JumpToJumpAlgorithm.GeneratedPlatform firstJump =
                        JumpToJumpAlgorithm.generateNext(
                            template, null, startPlat.centerLocation(), heading, heading, startLoc,
                            List.of(startPlat.centerLocation()), 1, rng);

                    Location firstLoc = firstJump.centerLocation();
                    double dx = firstLoc.getX() - startLoc.getX();
                    double dz = firstLoc.getZ() - startLoc.getZ();

                    // 1. Forward displacement projection must be strictly positive (never behind!)
                    double stepFwd = dx * fwdX + dz * fwdZ;
                    assertTrue(stepFwd >= 0.8,
                        "[" + diff + " heading=" + heading + " seed=" + seed + "] First jump generated behind or with non-positive forward progress: " + stepFwd);

                    // 2. Must not be touching or corner-adjacent (max(absDx, absDz) >= 2)
                    int absDx = (int) Math.round(Math.abs(dx));
                    int absDz = (int) Math.round(Math.abs(dz));
                    assertTrue(Math.max(absDx, absDz) >= 2,
                        "[" + diff + " heading=" + heading + " seed=" + seed + "] First jump too close/touching starting block: dx=" + dx + ", dz=" + dz);

                    // 3. Height delta must obey difficulty limits (never > 1)
                    int dy = firstLoc.getBlockY() - startLoc.getBlockY();
                    assertTrue(dy <= 1, "Height delta " + dy + " exceeds vanilla jump limit");
                }
            }
        }
    }

    @Test
    public void testMultiBlockFirstJumpDoesNotTouchStartingBlock() {
        Location startLoc = new Location(null, 0, 64, 0);
        float heading = 0f; // South (+Z)

        // Test with multi-block shapes like 2x2 and 3x3
        String[] multiShapes = {"2x2", "3x3", "CROSS"};

        for (String shape : multiShapes) {
            GeneratorTemplate template = createTemplate(GeneratorDifficulty.EASY, shape, 90.0, heading);

            for (int seed = 1; seed <= 30; seed++) {
                Random rng = new Random(seed * 73L);
                JumpToJumpAlgorithm.GeneratedPlatform startPlat =
                    JumpToJumpAlgorithm.generateExistingStart(null, startLoc, heading);

                JumpToJumpAlgorithm.GeneratedPlatform firstJump =
                    JumpToJumpAlgorithm.generateNext(
                        template, null, startPlat.centerLocation(), heading, heading, startLoc,
                        List.of(startPlat.centerLocation()), 1, rng);

                // Verify that NO block in firstJump touches or overlaps startLoc at the same level
                for (PlacedBlock pb : firstJump.placedBlocks()) {
                    int bdx = Math.abs(pb.location().getBlockX() - startLoc.getBlockX());
                    int bdz = Math.abs(pb.location().getBlockZ() - startLoc.getBlockZ());
                    if (pb.location().getBlockY() == startLoc.getBlockY()) {
                        assertFalse(Math.max(bdx, bdz) < 2,
                            "Shape " + shape + " placed block " + pb.location() + " touches starting block " + startLoc);
                    }
                }
            }
        }
    }
}
