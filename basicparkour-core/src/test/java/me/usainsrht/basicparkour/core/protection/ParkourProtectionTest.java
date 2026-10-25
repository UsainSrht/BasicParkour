package me.usainsrht.basicparkour.core.protection;

import me.usainsrht.basicparkour.api.course.BlockListMode;
import me.usainsrht.basicparkour.api.course.CourseRegion;
import me.usainsrht.basicparkour.api.course.DieMode;
import me.usainsrht.basicparkour.api.generator.BlockAppearAnimation;
import me.usainsrht.basicparkour.api.generator.BlockMode;
import me.usainsrht.basicparkour.api.generator.EntryMode;
import me.usainsrht.basicparkour.api.generator.GeneratorDifficulty;
import me.usainsrht.basicparkour.api.generator.GeneratorTemplate;
import me.usainsrht.basicparkour.api.generator.GeneratorTheme;
import me.usainsrht.basicparkour.api.generator.LengthType;
import me.usainsrht.basicparkour.api.generator.SlidingWindowMode;
import me.usainsrht.basicparkour.core.course.CourseRegionImpl;
import me.usainsrht.basicparkour.core.course.ParkourCourseImpl;
import me.usainsrht.basicparkour.core.generator.GeneratedParkourCourseImpl;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class ParkourProtectionTest {

    @Test
    public void testParkourCourseProtectionOverrides() {
        Location l1 = new Location(null, 0, 0, 0);
        Location l2 = new Location(null, 1, 1, 1);
        CourseRegion region = new CourseRegionImpl(l1, l2);
        ParkourCourseImpl course = new ParkourCourseImpl(
            "test_course",
            Component.text("Test"),
            "world",
            region,
            region,
            List.of(),
            null,
            false,
            Set.of(),
            BlockListMode.BLACKLIST,
            0,
            DieMode.RESTART_DIE,
            List.of(),
            Map.of(),
            List.of(),
            -1L,
            null,
            null,
            true,
            false
        );

        assertTrue(course.isPreventDamage().isPresent());
        assertTrue(course.isPreventDamage().get());
        assertTrue(course.isFallDamageFailsSession().isPresent());
        assertFalse(course.isFallDamageFailsSession().get());

        // Default / empty constructor
        ParkourCourseImpl defaultCourse = new ParkourCourseImpl(
            "default_course",
            Component.text("Default"),
            "world",
            region,
            region,
            List.of(),
            null,
            false,
            Set.of(),
            BlockListMode.BLACKLIST,
            0,
            DieMode.RESTART_DIE,
            List.of(),
            Map.of(),
            List.of(),
            -1L
        );

        assertTrue(defaultCourse.isPreventDamage().isEmpty());
        assertTrue(defaultCourse.isFallDamageFailsSession().isEmpty());
        assertTrue(defaultCourse.isPreventKnockback().isEmpty());
        assertTrue(defaultCourse.isPreventProjectileLaunch().isEmpty());
        assertTrue(defaultCourse.isPreventElytra().isEmpty());
        assertTrue(defaultCourse.isTeleportLeavesSession().isEmpty());
    }

    @Test
    public void testParkourCourseNewOptionsAndAliases() {
        Location l1 = new Location(null, 0, 0, 0);
        Location l2 = new Location(null, 1, 1, 1);
        CourseRegion region = new CourseRegionImpl(l1, l2);

        // Test constructor override
        ParkourCourseImpl course = new ParkourCourseImpl(
            "custom_options_course",
            Component.text("Custom Options"),
            "world",
            region,
            region,
            List.of(),
            null,
            false,
            Set.of(),
            BlockListMode.BLACKLIST,
            0,
            DieMode.RESTART_DIE,
            List.of(),
            Map.of(),
            List.of(),
            -1L,
            null,
            null,
            true,
            false,
            true,   // preventKnockback
            true,   // preventProjectileLaunch
            true,   // preventElytra
            false   // teleportLeavesSession
        );

        assertTrue(course.isPreventKnockback().orElse(false));
        assertTrue(course.isCancelKnockback().orElse(false));

        assertTrue(course.isPreventProjectileLaunch().orElse(false));
        assertTrue(course.isDisableProjectileLaunch().orElse(false));

        assertTrue(course.isPreventElytra().orElse(false));
        assertTrue(course.isDisableElytra().orElse(false));

        assertFalse(course.isTeleportLeavesSession().orElse(true));
        assertFalse(course.isCancelOnExternalTeleport().orElse(true));

        // Test fluent Builder
        me.usainsrht.basicparkour.api.course.ParkourCourse builtCourse = me.usainsrht.basicparkour.api.course.ParkourCourse.builder("builder_course")
            .worldName("world")
            .startRegion(region)
            .endRegion(region)
            .preventKnockback(false)
            .preventProjectileLaunch(false)
            .preventElytra(false)
            .teleportLeavesSession(true)
            .build();

        assertFalse(builtCourse.isPreventKnockback().orElse(true));
        assertFalse(builtCourse.isCancelKnockback().orElse(true));

        assertFalse(builtCourse.isPreventProjectileLaunch().orElse(true));
        assertFalse(builtCourse.isDisableProjectileLaunch().orElse(true));

        assertFalse(builtCourse.isPreventElytra().orElse(true));
        assertFalse(builtCourse.isDisableElytra().orElse(true));

        assertTrue(builtCourse.isTeleportLeavesSession().orElse(false));
        assertTrue(builtCourse.isCancelOnExternalTeleport().orElse(false));
    }

    @Test
    public void testGeneratedCourseProtectionDelegation() {
        GeneratorTheme theme = new GeneratorTheme("Test", List.of(Material.STONE), List.of(), List.of());
        GeneratorTemplate template = new GeneratorTemplate(
            "gen_test",
            "<white>Gen</white>",
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
            "gen_test",
            null,
            List.of("1x1"),
            2, 4, -1, 1, 1, 1, 0.0, 0.0,
            Map.of(),
            DieMode.FAIL_DIE,
            BlockAppearAnimation.DEFAULT,
            SlidingWindowMode.BLOCKS,
            0.0,
            null,
            null,
            90.0,
            null,
            0,
            15,
            true,
            false
        );

        CourseRegion region = new CourseRegionImpl(new Location(null, 0, 0, 0), new Location(null, 1, 1, 1));
        GeneratedParkourCourseImpl generatedCourse = new GeneratedParkourCourseImpl(
            "gen_test_instance",
            template,
            12345L,
            "world",
            region,
            region,
            List.of()
        );

        assertTrue(generatedCourse.isPreventDamage().isPresent());
        assertTrue(generatedCourse.isPreventDamage().get());
        assertTrue(generatedCourse.isFallDamageFailsSession().isPresent());
        assertFalse(generatedCourse.isFallDamageFailsSession().get());
    }
}
