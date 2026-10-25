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
    private final Boolean preventDamage;
    private final Boolean fallDamageFailsSession;
    private final Boolean preventKnockback;
    private final Boolean preventProjectileLaunch;
    private final Boolean preventElytra;
    private final Boolean teleportLeavesSession;

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
            rewardCommands, timeLimitMs, null, null, null, null, null, null, null, null);
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
        this(id, displayName, worldName, startRegion, endRegion, startLocations, portalRegion,
            portalTeleport, blockList, blockListMode, minY, dieMode, checkpoints, modifierOverrides,
            rewardCommands, timeLimitMs, failLocation, completeLocation, null, null, null, null, null, null);
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
        @Nullable Location completeLocation,
        @Nullable Boolean preventDamage,
        @Nullable Boolean fallDamageFailsSession
    ) {
        this(id, displayName, worldName, startRegion, endRegion, startLocations, portalRegion,
            portalTeleport, blockList, blockListMode, minY, dieMode, checkpoints, modifierOverrides,
            rewardCommands, timeLimitMs, failLocation, completeLocation, preventDamage, fallDamageFailsSession,
            null, null, null, null);
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
        @Nullable Location completeLocation,
        @Nullable Boolean preventDamage,
        @Nullable Boolean fallDamageFailsSession,
        @Nullable Boolean preventKnockback,
        @Nullable Boolean preventProjectileLaunch,
        @Nullable Boolean preventElytra,
        @Nullable Boolean teleportLeavesSession
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
        this.preventDamage = preventDamage;
        this.fallDamageFailsSession = fallDamageFailsSession;
        this.preventKnockback = preventKnockback;
        this.preventProjectileLaunch = preventProjectileLaunch;
        this.preventElytra = preventElytra;
        this.teleportLeavesSession = teleportLeavesSession;
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
    @Override @NotNull public Optional<Boolean> isPreventDamage() { return Optional.ofNullable(preventDamage); }
    @Override @NotNull public Optional<Boolean> isFallDamageFailsSession() { return Optional.ofNullable(fallDamageFailsSession); }
    @Override @NotNull public Optional<Boolean> isPreventKnockback() { return Optional.ofNullable(preventKnockback); }
    @Override @NotNull public Optional<Boolean> isPreventProjectileLaunch() { return Optional.ofNullable(preventProjectileLaunch); }
    @Override @NotNull public Optional<Boolean> isPreventElytra() { return Optional.ofNullable(preventElytra); }
    @Override @NotNull public Optional<Boolean> isTeleportLeavesSession() { return Optional.ofNullable(teleportLeavesSession); }

    @Override
    public String toString() {
        return "ParkourCourse{id='%s', checkpoints=%d}".formatted(id, checkpoints.size());
    }
}
