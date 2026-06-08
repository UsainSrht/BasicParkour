package me.usainsrht.basicparkour.core.course;

import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.course.CourseRegion;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;

/**
 * Immutable implementation of {@link ParkourCourse}, built from YAML configuration.
 */
public final class ParkourCourseImpl implements ParkourCourse {

    private final String id;
    private final Component displayName;
    private final String worldName;
    private final CourseRegion startRegion;
    private final CourseRegion endRegion;
    private final List<Checkpoint> checkpoints;
    private final Map<Material, String> modifierOverrides;
    private final List<String> rewardCommands;
    private final long timeLimitMs;

    public ParkourCourseImpl(
        @NotNull String id,
        @NotNull Component displayName,
        @NotNull String worldName,
        @NotNull CourseRegion startRegion,
        @NotNull CourseRegion endRegion,
        @NotNull List<Checkpoint> checkpoints,
        @NotNull Map<Material, String> modifierOverrides,
        @NotNull List<String> rewardCommands,
        long timeLimitMs
    ) {
        this.id = id;
        this.displayName = displayName;
        this.worldName = worldName;
        this.startRegion = startRegion;
        this.endRegion = endRegion;
        this.checkpoints = List.copyOf(checkpoints);
        this.modifierOverrides = Map.copyOf(modifierOverrides);
        this.rewardCommands = List.copyOf(rewardCommands);
        this.timeLimitMs = timeLimitMs;
    }

    @Override @NotNull public String getId() { return id; }
    @Override @NotNull public Component getDisplayName() { return displayName; }
    @Override @NotNull public String getWorldName() { return worldName; }
    @Override @NotNull public CourseRegion getStartRegion() { return startRegion; }
    @Override @NotNull public CourseRegion getEndRegion() { return endRegion; }
    @Override @NotNull @Unmodifiable public List<Checkpoint> getCheckpoints() { return checkpoints; }
    @Override @NotNull @Unmodifiable public Map<Material, String> getModifierOverrides() { return modifierOverrides; }
    @Override @NotNull @Unmodifiable public List<String> getRewardCommands() { return rewardCommands; }
    @Override public long getTimeLimitMs() { return timeLimitMs; }

    @Override
    public String toString() {
        return "ParkourCourse{id='%s', checkpoints=%d}".formatted(id, checkpoints.size());
    }
}
