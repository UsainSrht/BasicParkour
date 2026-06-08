package me.usainsrht.basicparkour.api.course;

import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.modifier.BlockModifier;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents an immutable definition of a parkour course.
 *
 * <p>A course definition is typically loaded from a YAML file inside
 * {@code plugins/BasicParkour/courses/}. Third-party plugins may also register
 * custom courses programmatically through {@link BasicParkourAPI.CourseRegistry}.</p>
 */
public interface ParkourCourse {

    /**
     * The unique, case-insensitive identifier for this course.
     * Used as the primary key in storage and configuration files.
     *
     * @return the course ID
     */
    @NotNull
    String getId();

    /**
     * The MiniMessage-formatted display name shown to players.
     *
     * @return the display name component
     */
    @NotNull
    Component getDisplayName();

    /**
     * The name of the world this course resides in.
     *
     * @return world name
     */
    @NotNull
    String getWorldName();

    /**
     * The bounding box region that triggers the start of a session.
     * Walking into this region while no session is active starts a new run.
     *
     * @return the start region
     */
    @NotNull
    CourseRegion getStartRegion();

    /**
     * The bounding box region that triggers the completion of a session.
     * Walking into this region while a session is active completes the run.
     *
     * @return the end region
     */
    @NotNull
    CourseRegion getEndRegion();

    /**
     * An ordered list of checkpoints on this course.
     * The player must reach them sequentially.
     *
     * @return immutable ordered list of checkpoints
     */
    @NotNull
    @Unmodifiable
    List<Checkpoint> getCheckpoints();

    /**
     * Returns the checkpoint at the given zero-based index.
     *
     * @param index the checkpoint index
     * @return the checkpoint wrapped in an Optional, or empty if index is out of range
     */
    @NotNull
    default Optional<Checkpoint> getCheckpoint(int index) {
        List<Checkpoint> checkpoints = getCheckpoints();
        if (index < 0 || index >= checkpoints.size()) return Optional.empty();
        return Optional.of(checkpoints.get(index));
    }

    /**
     * Course-specific block modifier overrides.
     * Keyed by {@link Material}, mapped to a {@link BlockModifier} key ({@link org.bukkit.NamespacedKey}).
     *
     * <p>These overrides take precedence over globally registered modifiers.
     * For example, a course might map {@code RED_CONCRETE} to a kill block
     * while another course maps it to a jump pad.</p>
     *
     * @return an immutable map of material → modifier key string
     */
    @NotNull
    @Unmodifiable
    Map<Material, String> getModifierOverrides();

    /**
     * A list of commands to execute upon course completion.
     * The placeholder {@code {player}} is replaced with the player's name.
     * Commands are run on the main thread as console.
     *
     * @return immutable list of reward commands
     */
    @NotNull
    @Unmodifiable
    List<String> getRewardCommands();

    /**
     * Whether this course has a time limit (in milliseconds).
     * Returns {@code -1} if there is no limit.
     *
     * @return time limit in ms, or -1
     */
    default long getTimeLimitMs() {
        return -1L;
    }
}
