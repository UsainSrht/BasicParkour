package me.usainsrht.basicparkour.core.course;

import me.usainsrht.basicparkour.api.course.BlockListMode;
import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.course.CourseRegion;
import me.usainsrht.basicparkour.api.course.DieMode;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable implementation of {@link ParkourCourse}, built from YAML configuration.
 */
public final class ParkourCourseImpl implements ParkourCourse {

    private final String id;
    private final Component displayName;
    private final String worldName;
    private final CourseRegion startRegion;
    private final CourseRegion endRegion;
    private final List<Location> startLocations;
    private final CourseRegion portalRegion;
    private final boolean portalTeleport;
    private final Set<Material> blockList;
    private final BlockListMode blockListMode;
    private final Integer minY;
    private final DieMode dieMode;
    private final List<Checkpoint> checkpoints;
    private final Map<Material, String> modifierOverrides;
    private final List<String> rewardCommands;
    private final long timeLimitMs;
    private final Location failLocation;
    private final Location completeLocation;

    public ParkourCourseImpl(
        @NotNull String id,
        @NotNull Component displayName,
        @NotNull String worldName,
        @NotNull CourseRegion startRegion,
        @NotNull CourseRegion endRegion,
        @NotNull List<Location> startLocations,
        @Nullable CourseRegion portalRegion,
        boolean portalTeleport,
        @NotNull Set<Material> blockList,
        @NotNull BlockListMode blockListMode,
        @Nullable Integer minY,
        @NotNull DieMode dieMode,
        @NotNull List<Checkpoint> checkpoints,
        @NotNull Map<Material, String> modifierOverrides,
        @NotNull List<String> rewardCommands,
        long timeLimitMs
    ) {
        this(id, displayName, worldName, startRegion, endRegion, startLocations, portalRegion,
            portalTeleport, blockList, blockListMode, minY, dieMode, checkpoints, modifierOverrides,
            rewardCommands, timeLimitMs, null, null);
    }

    public ParkourCourseImpl(
        @NotNull String id,
        @NotNull Component displayName,
        @NotNull String worldName,
        @NotNull CourseRegion startRegion,
        @NotNull CourseRegion endRegion,
        @NotNull List<Location> startLocations,
        @Nullable CourseRegion portalRegion,
        boolean portalTeleport,
        @NotNull Set<Material> blockList,
        @NotNull BlockListMode blockListMode,
        @Nullable Integer minY,
        @NotNull DieMode dieMode,
        @NotNull List<Checkpoint> checkpoints,
        @NotNull Map<Material, String> modifierOverrides,
        @NotNull List<String> rewardCommands,
        long timeLimitMs,
        @Nullable Location failLocation,
        @Nullable Location completeLocation
    ) {
        this.id = id;
        this.displayName = displayName;
        this.worldName = worldName;
        this.startRegion = startRegion;
        this.endRegion = endRegion;
        this.startLocations = List.copyOf(startLocations);
        this.portalRegion = portalRegion;
        this.portalTeleport = portalTeleport;
        this.blockList = Set.copyOf(blockList);
        this.blockListMode = blockListMode;
        this.minY = minY;
        this.dieMode = dieMode;
        this.checkpoints = List.copyOf(checkpoints);
        this.modifierOverrides = Map.copyOf(modifierOverrides);
        this.rewardCommands = List.copyOf(rewardCommands);
        this.timeLimitMs = timeLimitMs;
        this.failLocation = failLocation;
        this.completeLocation = completeLocation;
    }

    @Override @NotNull public String getId() { return id; }
    @Override @NotNull public Component getDisplayName() { return displayName; }
    @Override @NotNull public String getWorldName() { return worldName; }
    @Override @NotNull public CourseRegion getStartRegion() { return startRegion; }
    @Override @NotNull public CourseRegion getEndRegion() { return endRegion; }
    @Override @NotNull @Unmodifiable public List<Location> getStartLocations() { return startLocations; }
    @Override @NotNull public Optional<CourseRegion> getPortalRegion() { return Optional.ofNullable(portalRegion); }
    @Override public boolean isPortalTeleport() { return portalTeleport; }
    @Override @NotNull @Unmodifiable public Set<Material> getBlockList() { return blockList; }
    @Override @NotNull public BlockListMode getBlockListMode() { return blockListMode; }
    @Override @NotNull public Optional<Integer> getMinY() { return Optional.ofNullable(minY); }
    @Override @NotNull public DieMode getDieMode() { return dieMode; }
    @Override @NotNull @Unmodifiable public List<Checkpoint> getCheckpoints() { return checkpoints; }
    @Override @NotNull @Unmodifiable public Map<Material, String> getModifierOverrides() { return modifierOverrides; }
    @Override @NotNull @Unmodifiable public List<String> getRewardCommands() { return rewardCommands; }
    @Override public long getTimeLimitMs() { return timeLimitMs; }
    @Override @NotNull public Optional<Location> getFailLocation() { return Optional.ofNullable(failLocation); }
    @Override @NotNull public Optional<Location> getCompleteLocation() { return Optional.ofNullable(completeLocation); }

    @Override
    public String toString() {
        return "ParkourCourse{id='%s', checkpoints=%d}".formatted(id, checkpoints.size());
    }
}
