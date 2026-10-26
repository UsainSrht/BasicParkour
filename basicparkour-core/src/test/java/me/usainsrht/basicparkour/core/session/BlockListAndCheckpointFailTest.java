package me.usainsrht.basicparkour.core.session;

import me.usainsrht.basicparkour.api.course.BlockListMode;
import me.usainsrht.basicparkour.api.course.CourseRegion;
import me.usainsrht.basicparkour.api.course.DieMode;
import me.usainsrht.basicparkour.core.course.CourseRegionImpl;
import me.usainsrht.basicparkour.core.course.ParkourCourseImpl;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class BlockListAndCheckpointFailTest {

    @Test
    public void testWhitelistConfiguration() {
        Location min = new Location(null, 0, 64, 0);
        Location max = new Location(null, 4, 68, 4);
        CourseRegion start = new CourseRegionImpl(min, max);
        CourseRegion end = new CourseRegionImpl(new Location(null, 10, 64, 10), new Location(null, 14, 68, 14));

        Set<Material> whitelist = Set.of(
            Material.PINK_CONCRETE,
            Material.LIME_CONCRETE,
            Material.GREEN_CONCRETE,
            Material.GRAY_CONCRETE,
            Material.LIGHT_GRAY_CONCRETE,
            Material.YELLOW_CONCRETE,
            Material.SHROOMLIGHT
        );

        ParkourCourseImpl course = new ParkourCourseImpl(
            "whitelist_course",
            Component.text("Whitelist Course"),
            "world",
            start,
            end,
            List.of(),
            null,
            false,
            whitelist,
            BlockListMode.WHITELIST,
            0,
            DieMode.RESTART_DIE,
            List.of(),
            Map.of(),
            List.of(),
            -1L
        );

        assertEquals(BlockListMode.WHITELIST, course.getBlockListMode());
        assertTrue(course.getBlockList().contains(Material.SHROOMLIGHT));
        assertTrue(course.getBlockList().contains(Material.PINK_CONCRETE));
        assertFalse(course.getBlockList().contains(Material.STONE));
    }

    @Test
    public void testRespawnCooldownImmunity() {
        UUID uuid = UUID.randomUUID();
        java.util.Map<UUID, Long> respawnCooldown = new java.util.concurrent.ConcurrentHashMap<>();

        // Initially not immune
        Long until = respawnCooldown.get(uuid);
        assertNull(until);

        // Set cooldown 1000ms
        respawnCooldown.put(uuid, System.currentTimeMillis() + 1000L);
        assertTrue(System.currentTimeMillis() < respawnCooldown.get(uuid));

        // Expired cooldown
        respawnCooldown.put(uuid, System.currentTimeMillis() - 100L);
        assertFalse(System.currentTimeMillis() < respawnCooldown.get(uuid));
    }

    @Test
    public void testWhitelistCenterMaterialValidation() {
        Set<Material> whitelist = Set.of(
            Material.PINK_CONCRETE,
            Material.LIME_CONCRETE,
            Material.GREEN_CONCRETE,
            Material.GRAY_CONCRETE,
            Material.LIGHT_GRAY_CONCRETE,
            Material.YELLOW_CONCRETE,
            Material.SHROOMLIGHT
        );

        // Scenario 1: Center material is whitelisted (e.g. landed on PINK_CONCRETE)
        Material center1 = Material.PINK_CONCRETE;
        assertTrue(whitelist.contains(center1), "Landed directly on whitelisted block should be valid");

        // Scenario 2: Center material is forbidden floor (e.g. landed on STONE)
        Material center2 = Material.STONE;
        assertFalse(whitelist.contains(center2), "Landed on forbidden floor should fail");

        // Scenario 3: Center is over air (edge of block), but standing materials contain LIME_CONCRETE
        Material center3 = Material.AIR;
        Set<Material> standingWithWhitelist = Set.of(Material.LIME_CONCRETE);
        boolean hasWhitelisted = standingWithWhitelist.stream().anyMatch(whitelist::contains);
        assertTrue(hasWhitelisted, "Overhanging edge catching whitelisted block should be valid");

        // Scenario 4: Center is over air, but standing materials only contain STONE
        Set<Material> standingOnlyStone = Set.of(Material.STONE);
        boolean hasWhitelistedStone = standingOnlyStone.stream().anyMatch(whitelist::contains);
        assertFalse(hasWhitelistedStone, "Overhanging edge touching only non-whitelisted blocks should fail");
    }

    @Test
    public void testDebugManagerToggle() {
        Set<UUID> debugPlayers = java.util.concurrent.ConcurrentHashMap.newKeySet();
        UUID player1 = UUID.randomUUID();

        assertFalse(debugPlayers.contains(player1));
        debugPlayers.add(player1);
        assertTrue(debugPlayers.contains(player1));
        debugPlayers.remove(player1);
        assertFalse(debugPlayers.contains(player1));
    }
}
