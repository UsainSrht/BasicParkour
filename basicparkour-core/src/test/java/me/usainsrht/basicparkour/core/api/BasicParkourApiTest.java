package me.usainsrht.basicparkour.core.api;

import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.course.CourseRegion;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.event.*;
import me.usainsrht.basicparkour.api.generator.GeneratorTemplate;
import me.usainsrht.basicparkour.api.generator.LengthType;
import me.usainsrht.basicparkour.api.modifier.BlockModifier;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.api.session.SessionState;
import me.usainsrht.basicparkour.api.storage.PlayerRecord;
import me.usainsrht.basicparkour.core.session.ParkourSessionImpl;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.util.BoundingBox;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class BasicParkourApiTest {

    private ParkourCourse createDummyCourse(String id) {
        Location minStart = new Location(null, 10, 64, 10);
        Location maxStart = new Location(null, 12, 66, 12);
        CourseRegion startRegion = CourseRegion.of(minStart, maxStart);

        Location minEnd = new Location(null, 50, 70, 50);
        Location maxEnd = new Location(null, 52, 72, 52);
        CourseRegion endRegion = CourseRegion.of(minEnd, maxEnd);

        Location cpSpawn = new Location(null, 25, 66, 25);
        CourseRegion cpRegion = CourseRegion.of(new Location(null, 24, 65, 24), new Location(null, 26, 68, 26));
        Checkpoint checkpoint = Checkpoint.of(0, cpSpawn, cpRegion);

        return ParkourCourse.builder(id)
                .displayName("Custom Course")
                .worldName("world")
                .startRegion(startRegion)
                .endRegion(endRegion)
                .addStartLocation(minStart)
                .minY(40)
                .addCheckpoint(checkpoint)
                .addRewardCommand("give %player% diamond 5")
                .preventDamage(true)
                .fallDamageFailsSession(false)
                .build();
    }

    @Test
    public void testParkourCourseBuilder() {
        ParkourCourse course = createDummyCourse("custom_course");

        assertEquals("custom_course", course.getId());
        assertEquals("world", course.getWorldName());
        assertEquals(Integer.valueOf(40), course.getMinY().orElse(null));
        assertEquals(1, course.getCheckpoints().size());
        assertEquals(List.of("give %player% diamond 5"), course.getRewardCommands());
        assertTrue(course.isPreventDamage().orElse(false));
        assertFalse(course.isFallDamageFailsSession().orElse(true));

        // Check unmodifiable collections
        Checkpoint dummyCp = Checkpoint.of(1, new Location(null, 0, 64, 0), CourseRegion.of(new Location(null, 0, 64, 0), new Location(null, 1, 65, 1)));
        assertThrows(UnsupportedOperationException.class, () ->
                course.getCheckpoints().add(dummyCp));
        assertThrows(UnsupportedOperationException.class, () ->
                course.getRewardCommands().add("test"));
    }

    @Test
    public void testBlockModifierBuilderAndFactory() {
        NamespacedKey speedKey = new NamespacedKey("customplugin", "speed_pad");
        AtomicBoolean onApplyCalled = new AtomicBoolean(false);

        BlockModifier modifier = BlockModifier.builder(speedKey)
                .displayName("Speed Pad")
                .matchMaterial(Material.GOLD_BLOCK)
                .onApply((player, session) -> onApplyCalled.set(true))
                .build();

        assertEquals(speedKey, modifier.getKey());
        assertEquals("Speed Pad", modifier.getDisplayName());

        modifier.apply(null, null);
        assertTrue(onApplyCalled.get());

        // Test static factory method
        NamespacedKey slimeKey = new NamespacedKey("customplugin", "bouncy_slime");
        BlockModifier slimeModifier = BlockModifier.of(slimeKey, Material.SLIME_BLOCK, (player, session) -> {});
        assertEquals(slimeKey, slimeModifier.getKey());
        assertEquals("customplugin:bouncy_slime", slimeModifier.getDisplayName());
    }

    @Test
    public void testCourseRegionFactoryAndNormalization() {
        // Test that coordinates are automatically normalized regardless of order
        Location cornerA = new Location(null, 100, 80, 50);
        Location cornerB = new Location(null, 20, 60, 200);

        CourseRegion region = CourseRegion.of(cornerA, cornerB);
        assertEquals(20.0, region.getMin().getX(), 0.001);
        assertEquals(60.0, region.getMin().getY(), 0.001);
        assertEquals(50.0, region.getMin().getZ(), 0.001);

        assertEquals(100.0, region.getMax().getX(), 0.001);
        assertEquals(80.0, region.getMax().getY(), 0.001);
        assertEquals(200.0, region.getMax().getZ(), 0.001);

        assertTrue(region.contains(new Location(null, 50, 70, 100)));
        assertFalse(region.contains(new Location(null, 10, 70, 100)));

        BoundingBox insideBox = new BoundingBox(25, 65, 55, 30, 70, 60);
        assertTrue(region.overlaps(insideBox));
    }

    @Test
    public void testGeneratorTemplateBuilder() {
        GeneratorTemplate template = GeneratorTemplate.builder("infinite_challenge")
                .lengthType(LengthType.INFINITE)
                .fixedLength(0)
                .directionCone(60.0)
                .modifierOverrides(Map.of(Material.EMERALD_BLOCK, "speed_pad"))
                .build();

        assertEquals("infinite_challenge", template.id());
        assertEquals(LengthType.INFINITE, template.lengthType());
        assertEquals(60.0, template.directionCone(), 0.001);
        assertEquals("speed_pad", template.modifierOverrides().get(Material.EMERALD_BLOCK));
    }

    @Test
    public void testCoursePreCompleteEventMutation() {
        ParkourCourse course = createDummyCourse("course1");
        Location joinLoc = new Location(null, 10, 64, 10);
        ParkourSession session = new ParkourSessionImpl(null, joinLoc, course, null, System.currentTimeMillis());

        List<String> commands = new ArrayList<>();
        commands.add("give %player% iron_ingot 1");

        CoursePreCompleteEvent event = new CoursePreCompleteEvent(null, session, 15000L, commands);
        assertEquals(15000L, event.getElapsedMs());
        assertTrue(event.isRecordBestTime());
        assertFalse(event.isCancelled());

        // Modify elapsed time (e.g., custom penalty or bonus by another plugin)
        event.setElapsedMs(12000L);
        assertEquals(12000L, event.getElapsedMs());

        // Modify record saving toggle
        event.setRecordBestTime(false);
        assertFalse(event.isRecordBestTime());

        // Modify reward commands
        event.getRewardCommands().add("give %player% gold_ingot 2");
        assertEquals(2, event.getRewardCommands().size());

        // Test cancellation
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    public void testCoursePreFailEventMutation() {
        ParkourCourse course = createDummyCourse("course1");
        Location joinLoc = new Location(null, 10, 64, 10);
        ParkourSession session = new ParkourSessionImpl(null, joinLoc, course, null, System.currentTimeMillis());

        Location defaultRespawn = new Location(null, 5, 64, 5);
        Location customRespawn = new Location(null, 0, 64, 0);

        CoursePreFailEvent event = new CoursePreFailEvent(null, session, ParkourSession.FailReason.VOID, defaultRespawn);
        assertEquals(ParkourSession.FailReason.VOID, event.getReason());
        assertEquals(defaultRespawn, event.getRespawnLocation());
        assertFalse(event.isCancelled());

        // Override reason and respawn location
        event.setReason(ParkourSession.FailReason.KILL_BLOCK);
        assertEquals(ParkourSession.FailReason.KILL_BLOCK, event.getReason());

        event.setRespawnLocation(customRespawn);
        assertEquals(customRespawn, event.getRespawnLocation());

        // Cancel (e.g., third-party shield or extra lives system)
        event.setCancelled(true);
        assertTrue(event.isCancelled());
    }

    @Test
    public void testCourseLeaveAndRestartEvents() {
        ParkourCourse course = createDummyCourse("course1");
        Location joinLoc = new Location(null, 10, 64, 10);
        ParkourSession session = new ParkourSessionImpl(null, joinLoc, course, null, System.currentTimeMillis());

        Location exitLoc = new Location(null, 100, 70, 100);
        CourseLeaveEvent leaveEvent = new CourseLeaveEvent(null, session, CourseLeaveEvent.LeaveReason.COMMAND, true, exitLoc);
        assertEquals(CourseLeaveEvent.LeaveReason.COMMAND, leaveEvent.getReason());
        assertTrue(leaveEvent.isTeleport());
        assertEquals(exitLoc, leaveEvent.getExitLocation());

        leaveEvent.setTeleport(false);
        assertFalse(leaveEvent.isTeleport());

        Location lobbyLoc = new Location(null, 0, 100, 0);
        leaveEvent.setExitLocation(lobbyLoc);
        assertEquals(lobbyLoc, leaveEvent.getExitLocation());

        leaveEvent.setCancelled(true);
        assertTrue(leaveEvent.isCancelled());

        Location startLoc = new Location(null, 10, 64, 10);
        CourseRestartEvent restartEvent = new CourseRestartEvent(null, session, CourseRestartEvent.RestartReason.HOTBAR, startLoc);
        assertEquals(CourseRestartEvent.RestartReason.HOTBAR, restartEvent.getReason());
        assertEquals(startLoc, restartEvent.getStartLocation());
        assertFalse(restartEvent.isCancelled());
        restartEvent.setCancelled(true);
        assertTrue(restartEvent.isCancelled());
    }

    @Test
    public void testCourseCheckpointTeleportAndVisibilityEvents() {
        ParkourCourse course = createDummyCourse("course1");
        Location joinLoc = new Location(null, 10, 64, 10);
        ParkourSession session = new ParkourSessionImpl(null, joinLoc, course, null, System.currentTimeMillis());

        Location cpLoc = new Location(null, 15, 65, 15);
        CourseCheckpointTeleportEvent cpEvent = new CourseCheckpointTeleportEvent(null, session, cpLoc);
        assertEquals(cpLoc, cpEvent.getTargetLocation());

        Location customTarget = new Location(null, 16, 65, 16);
        cpEvent.setTargetLocation(customTarget);
        assertEquals(customTarget, cpEvent.getTargetLocation());
        cpEvent.setCancelled(true);
        assertTrue(cpEvent.isCancelled());

        PlayerVisibilityModeChangeEvent visEvent = new PlayerVisibilityModeChangeEvent(null, "ALL", "TRANSLUCENT");
        assertEquals("ALL", visEvent.getOldMode());
        assertEquals("TRANSLUCENT", visEvent.getNewMode());
        visEvent.setNewMode("NONE");
        assertEquals("NONE", visEvent.getNewMode());
        visEvent.setCancelled(true);
        assertTrue(visEvent.isCancelled());
    }

    @Test
    public void testBlockModifierTriggerAndRecordSaveEvents() {
        NamespacedKey key = new NamespacedKey("custom", "jump_boost");
        BlockModifier modifier = BlockModifier.of(key, Material.SLIME_BLOCK, (p, b) -> {});

        // BlockModifierTriggerEvent
        BlockModifierTriggerEvent modEvent = new BlockModifierTriggerEvent(null, null, null, modifier);
        assertEquals(modifier, modEvent.getModifier());
        assertFalse(modEvent.isCancelled());
        modEvent.setCancelled(true);
        assertTrue(modEvent.isCancelled());

        PlayerRecord record = PlayerRecord.of(UUID.randomUUID(), "Player1", "course1", 8500L);
        CourseRecordSaveEvent saveEvent = new CourseRecordSaveEvent(null, record, true);
        assertEquals(record, saveEvent.getRecord());
        assertTrue(saveEvent.isPersonalBest());
        assertFalse(saveEvent.isCancelled());
        saveEvent.setCancelled(true);
        assertTrue(saveEvent.isCancelled());
    }

    @Test
    public void testParkourSessionMetadataAndCustomLocations() {
        ParkourCourse course = createDummyCourse("course1");
        Location joinLoc = new Location(null, 100, 70, 100);
        long start = System.currentTimeMillis() - 5000;
        ParkourSession session = new ParkourSessionImpl(null, joinLoc, course, null, start);

        // Metadata Store
        assertFalse(session.hasMetadata("plugin.shield"));
        assertNull(session.getMetadata("plugin.shield"));

        session.setMetadata("plugin.shield", 3);
        assertTrue(session.hasMetadata("plugin.shield"));
        assertEquals(3, session.getMetadata("plugin.shield"));
        assertEquals(3, session.getMetadata("plugin.shield", Integer.class).orElse(null));

        session.setMetadata("plugin.data", "custom_val");
        assertEquals("custom_val", session.getMetadata("plugin.data", String.class).orElse(null));
        assertEquals(2, session.getMetadataMap().size());

        assertEquals(3, session.removeMetadata("plugin.shield"));
        assertFalse(session.hasMetadata("plugin.shield"));

        // Custom Fail and Complete Locations
        assertTrue(session.getCustomFailLocation().isEmpty());
        assertTrue(session.getCustomCompleteLocation().isEmpty());

        Location failLoc = new Location(null, 1, 2, 3);
        session.setCustomFailLocation(failLoc);
        assertTrue(session.getCustomFailLocation().isPresent());
        assertEquals(failLoc, session.getCustomFailLocation().get());

        Location compLoc = new Location(null, 4, 5, 6);
        session.setCustomCompleteLocation(compLoc);
        assertTrue(session.getCustomCompleteLocation().isPresent());
        assertEquals(compLoc, session.getCustomCompleteLocation().get());

        session.setCustomFailLocation(null);
        assertTrue(session.getCustomFailLocation().isEmpty());

        // Checkpoint manipulation
        assertEquals(-1, session.getCurrentCheckpointIndex());
        session.setCurrentCheckpointIndex(3);
        assertEquals(3, session.getCurrentCheckpointIndex());
    }

    @Test
    public void testParkourSessionTimePenaltiesAndPauseResume() throws InterruptedException {
        ParkourCourse course = createDummyCourse("course1");
        Location joinLoc = new Location(null, 100, 70, 100);
        long start = System.currentTimeMillis();
        ParkourSession session = new ParkourSessionImpl(null, joinLoc, course, null, start);

        assertEquals(0L, session.getTimePenalty());
        session.addTimePenalty(2500L);
        assertEquals(2500L, session.getTimePenalty());

        // Elapsed time includes the penalty
        long elapsed = session.getElapsedMillis();
        assertTrue(elapsed >= 2500L, "Elapsed time should include 2500ms penalty");

        // Pause & Resume
        assertEquals(SessionState.ACTIVE, session.getState());
        assertFalse(session.isPaused());

        assertTrue(session.pause());
        assertEquals(SessionState.PAUSED, session.getState());
        assertTrue(session.isPaused());

        // Second pause call returns false
        assertFalse(session.pause());

        Thread.sleep(50);

        assertTrue(session.resume());
        assertEquals(SessionState.ACTIVE, session.getState());
        assertFalse(session.isPaused());

        // Second resume call returns false
        assertFalse(session.resume());

        assertTrue(session.getPausedDurationMillis() >= 40, "Paused duration should be tracked");
    }
}
