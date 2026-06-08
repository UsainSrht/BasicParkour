package me.usainsrht.basicparkour.core.course;

import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.course.CourseRegion;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * Immutable implementation of {@link Checkpoint}.
 */
public final class CheckpointImpl implements Checkpoint {

    private final int index;
    private final Location spawnLocation;
    private final CourseRegion triggerRegion;

    public CheckpointImpl(int index, @NotNull Location spawnLocation, @NotNull CourseRegion triggerRegion) {
        this.index = index;
        // Defensive clone to ensure immutability
        this.spawnLocation = spawnLocation.clone();
        this.triggerRegion = triggerRegion;
    }

    @Override
    public int getIndex() { return index; }

    @Override
    @NotNull
    public Location getSpawnLocation() { return spawnLocation.clone(); }

    @Override
    @NotNull
    public CourseRegion getTriggerRegion() { return triggerRegion; }

    @Override
    public String toString() {
        return "Checkpoint{index=%d, spawn=%s}".formatted(index, spawnLocation);
    }
}
