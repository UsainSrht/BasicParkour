package me.usainsrht.basicparkour.api.course;

import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a single checkpoint within a {@link ParkourCourse}.
 *
 * <p>Checkpoints are reached in order. When a player reaches a checkpoint,
 * their respawn location is updated so that upon failing, they are teleported
 * to this checkpoint's spawn location — preserving the exact yaw and pitch.</p>
 */
public interface Checkpoint {

    /**
     * The zero-based index of this checkpoint in its parent course.
     *
     * @return checkpoint index (0 = first checkpoint)
     */
    int getIndex();

    /**
     * The exact spawn location a player is teleported to when respawning at this checkpoint.
     * This includes precise {@link Location#getYaw()} and {@link Location#getPitch()} values
     * so the player faces the correct direction upon respawn.
     *
     * @return the spawn location
     */
    @NotNull
    Location getSpawnLocation();

    /**
     * The trigger region for this checkpoint. When a player's position enters this
     * axis-aligned bounding box, the checkpoint is considered reached.
     *
     * @return the trigger region
     */
    @NotNull
    CourseRegion getTriggerRegion();
}
