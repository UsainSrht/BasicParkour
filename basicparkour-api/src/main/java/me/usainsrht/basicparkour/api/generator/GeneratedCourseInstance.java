package me.usainsrht.basicparkour.api.generator;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;

/**
 * Represents a single active instance of a generated parkour run.
 *
 * <p>One instance corresponds to one set of placed blocks in the world plus the players
 * currently running on it. In solo mode there is exactly one player; in shared mode
 * there may be many.</p>
 */
public interface GeneratedCourseInstance {

    /**
     * Returns the generated course associated with this instance.
     *
     * @return the generated course
     */
    @NotNull
    GeneratedParkourCourse getCourse();

    /**
     * Returns the set of players currently on this instance.
     *
     * @return immutable snapshot of players
     */
    @NotNull
    @Unmodifiable
    Set<Player> getPlayers();

    /**
     * Returns the total number of platforms that have been placed so far.
     *
     * @return placed platform count
     */
    int getPlacedPlatformCount();

    /**
     * Returns the seed used by this instance.
     *
     * @return seed
     */
    long getSeed();

    /**
     * Returns {@code true} if this instance is still active (blocks are in the world
     * and at least one session is running).
     *
     * @return active flag
     */
    boolean isActive();
}
