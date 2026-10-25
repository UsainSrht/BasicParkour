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

    /**
     * Creates a new Checkpoint instance.
     *
     * @param index         zero-based index
     * @param spawnLocation spawn location on respawn
     * @param triggerRegion trigger region bounding box
     * @return a new Checkpoint
     */
    @NotNull
    static Checkpoint of(int index, @NotNull Location spawnLocation, @NotNull CourseRegion triggerRegion) {
        Location spawn = spawnLocation.clone();
        return new Checkpoint() {
            @Override
            public int getIndex() {
                return index;
            }

            @Override
            @NotNull
            public Location getSpawnLocation() {
                return spawn.clone();
            }

            @Override
            @NotNull
            public CourseRegion getTriggerRegion() {
                return triggerRegion;
            }

            @Override
            public String toString() {
                return "Checkpoint{index=" + index + ", spawn=" + spawn + "}";
            }
        };
    }
}
