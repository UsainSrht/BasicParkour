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
import java.util.Set;
import org.bukkit.Location;

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
     * A list of custom start locations for this course.
     * When a player joins, they are teleported to a random location in this list.
     * If the list is empty, a fallback location is used.
     *
     * @return immutable list of start locations
     */
    @NotNull
    @Unmodifiable
    List<Location> getStartLocations();

    /**
     * An optional portal bounding box. Walking into this box will start the course.
     *
     * @return the portal region, or empty if not configured
     */
    @NotNull
    Optional<CourseRegion> getPortalRegion();

    /**
     * Whether joining the course through the portal region automatically teleports
     * the player to the course's start location.
     *
     * @return true if the player should be teleported
     */
    boolean isPortalTeleport();

    /**
     * The list of blocks used for blacklist or whitelist modes.
     *
     * @return immutable set of materials
     */
    @NotNull
    @Unmodifiable
    Set<Material> getBlockList();

    /**
     * The blocklist mode (BLACKLIST or WHITELIST).
     *
     * @return the blocklist mode
     */
    @NotNull
    BlockListMode getBlockListMode();

    /**
     * The minimum Y coordinate before a player is considered to have fallen out
     * of the course, failing the session.
     *
     * @return the minimum Y level, or empty if disabled
     */
    @NotNull
    Optional<Integer> getMinY();

    /**
     * The die mode (how failures are handled).
     *
     * @return the die mode
     */
    @NotNull
    DieMode getDieMode();

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

    /**
     * Optional location to teleport the player to when they fail a session.
     * If not present, default fallback behavior (join location or start location) is used.
     *
     * @return the optional fail location
     */
    @NotNull
    default Optional<Location> getFailLocation() {
        return Optional.empty();
    }

    /**
     * Optional location to teleport the player to when they complete a course.
     * If not present, default fallback behavior is used.
     *
     * @return the optional complete location
     */
    @NotNull
    default Optional<Location> getCompleteLocation() {
        return Optional.empty();
    }

    /**
     * Whether taking damage is prevented for players doing this course.
     * If empty, the global configuration (protections.prevent-damage) is used.
     *
     * @return optional boolean override
     */
    @NotNull
    default Optional<Boolean> isPreventDamage() {
        return Optional.empty();
    }

    /**
     * Whether taking fall damage fails the session for players doing this course.
     * If empty, the global configuration (protections.fall-damage-fails-session) is used.
     *
     * @return optional boolean override
     */
    @NotNull
    default Optional<Boolean> isFallDamageFailsSession() {
        return Optional.empty();
    }

    /**
     * Creates a new fluent builder for defining a {@link ParkourCourse} programmatically.
     *
     * @param id the unique identifier for this course
     * @return a new Builder
     */
    @NotNull
    static Builder builder(@NotNull String id) {
        return new Builder(id);
    }

    /**
     * Fluent builder for {@link ParkourCourse}.
     */
    final class Builder {
        private final String id;
        private Component displayName;
        private String worldName;
        private CourseRegion startRegion;
        private CourseRegion endRegion;
        private final List<Location> startLocations = new java.util.ArrayList<>();
        private CourseRegion portalRegion;
        private boolean portalTeleport = false;
        private final Set<Material> blockList = new java.util.HashSet<>();
        private BlockListMode blockListMode = BlockListMode.BLACKLIST;
        private Integer minY;
        private DieMode dieMode = DieMode.RESTART_DIE;
        private final List<Checkpoint> checkpoints = new java.util.ArrayList<>();
        private final Map<Material, String> modifierOverrides = new java.util.HashMap<>();
        private final List<String> rewardCommands = new java.util.ArrayList<>();
        private long timeLimitMs = -1L;
        private Location failLocation;
        private Location completeLocation;
        private Boolean preventDamage;
        private Boolean fallDamageFailsSession;

        public Builder(@NotNull String id) {
            this.id = java.util.Objects.requireNonNull(id, "id cannot be null");
            this.displayName = Component.text(id);
        }

        @NotNull
        public Builder displayName(@NotNull Component displayName) {
            this.displayName = java.util.Objects.requireNonNull(displayName, "displayName cannot be null");
            return this;
        }

        @NotNull
        public Builder displayName(@NotNull String miniMessage) {
            this.displayName = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(miniMessage);
            return this;
        }

        @NotNull
        public Builder worldName(@NotNull String worldName) {
            this.worldName = java.util.Objects.requireNonNull(worldName, "worldName cannot be null");
            return this;
        }

        @NotNull
        public Builder startRegion(@NotNull CourseRegion startRegion) {
            this.startRegion = java.util.Objects.requireNonNull(startRegion, "startRegion cannot be null");
            if (this.worldName == null && startRegion.getMin().getWorld() != null) {
                this.worldName = startRegion.getMin().getWorld().getName();
            }
            return this;
        }

        @NotNull
        public Builder endRegion(@NotNull CourseRegion endRegion) {
            this.endRegion = java.util.Objects.requireNonNull(endRegion, "endRegion cannot be null");
            return this;
        }

        @NotNull
        public Builder addStartLocation(@NotNull Location location) {
            this.startLocations.add(location.clone());
            return this;
        }

        @NotNull
        public Builder startLocations(@NotNull java.util.Collection<Location> locations) {
            this.startLocations.clear();
            locations.forEach(loc -> this.startLocations.add(loc.clone()));
            return this;
        }

        @NotNull
        public Builder portalRegion(@org.jetbrains.annotations.Nullable CourseRegion portalRegion) {
            this.portalRegion = portalRegion;
            return this;
        }

        @NotNull
        public Builder portalTeleport(boolean portalTeleport) {
            this.portalTeleport = portalTeleport;
            return this;
        }

        @NotNull
        public Builder blockList(@NotNull java.util.Collection<Material> materials) {
            this.blockList.clear();
            this.blockList.addAll(materials);
            return this;
        }

        @NotNull
        public Builder addBlockListMaterial(@NotNull Material material) {
            this.blockList.add(material);
            return this;
        }

        @NotNull
        public Builder blockListMode(@NotNull BlockListMode mode) {
            this.blockListMode = java.util.Objects.requireNonNull(mode, "blockListMode cannot be null");
            return this;
        }

        @NotNull
        public Builder minY(@org.jetbrains.annotations.Nullable Integer minY) {
            this.minY = minY;
            return this;
        }

        @NotNull
        public Builder dieMode(@NotNull DieMode dieMode) {
            this.dieMode = java.util.Objects.requireNonNull(dieMode, "dieMode cannot be null");
            return this;
        }

        @NotNull
        public Builder addCheckpoint(@NotNull Checkpoint checkpoint) {
            this.checkpoints.add(checkpoint);
            return this;
        }

        @NotNull
        public Builder checkpoints(@NotNull java.util.Collection<Checkpoint> checkpoints) {
            this.checkpoints.clear();
            this.checkpoints.addAll(checkpoints);
            return this;
        }

        @NotNull
        public Builder addModifierOverride(@NotNull Material material, @NotNull String modifierKey) {
            this.modifierOverrides.put(material, modifierKey);
            return this;
        }

        @NotNull
        public Builder modifierOverrides(@NotNull Map<Material, String> overrides) {
            this.modifierOverrides.clear();
            this.modifierOverrides.putAll(overrides);
            return this;
        }

        @NotNull
        public Builder addRewardCommand(@NotNull String command) {
            this.rewardCommands.add(command);
            return this;
        }

        @NotNull
        public Builder rewardCommands(@NotNull java.util.Collection<String> commands) {
            this.rewardCommands.clear();
            this.rewardCommands.addAll(commands);
            return this;
        }

        @NotNull
        public Builder timeLimitMs(long timeLimitMs) {
            this.timeLimitMs = timeLimitMs;
            return this;
        }

        @NotNull
        public Builder failLocation(@org.jetbrains.annotations.Nullable Location failLocation) {
            this.failLocation = failLocation != null ? failLocation.clone() : null;
            return this;
        }

        @NotNull
        public Builder completeLocation(@org.jetbrains.annotations.Nullable Location completeLocation) {
            this.completeLocation = completeLocation != null ? completeLocation.clone() : null;
            return this;
        }

        @NotNull
        public Builder preventDamage(@org.jetbrains.annotations.Nullable Boolean preventDamage) {
            this.preventDamage = preventDamage;
            return this;
        }

        @NotNull
        public Builder fallDamageFailsSession(@org.jetbrains.annotations.Nullable Boolean fallDamageFailsSession) {
            this.fallDamageFailsSession = fallDamageFailsSession;
            return this;
        }

        @NotNull
        public ParkourCourse build() {
            if (worldName == null) {
                throw new IllegalStateException("Course worldName must be specified.");
            }
            if (startRegion == null) {
                throw new IllegalStateException("Course startRegion must be specified.");
            }
            if (endRegion == null) {
                throw new IllegalStateException("Course endRegion must be specified.");
            }

            final String fId = id;
            final Component fDisplayName = displayName;
            final String fWorldName = worldName;
            final CourseRegion fStartRegion = startRegion;
            final CourseRegion fEndRegion = endRegion;
            final List<Location> fStartLocations = List.copyOf(startLocations);
            final CourseRegion fPortalRegion = portalRegion;
            final boolean fPortalTeleport = portalTeleport;
            final Set<Material> fBlockList = Set.copyOf(blockList);
            final BlockListMode fBlockListMode = blockListMode;
            final Integer fMinY = minY;
            final DieMode fDieMode = dieMode;
            final List<Checkpoint> fCheckpoints = List.copyOf(checkpoints);
            final Map<Material, String> fModifierOverrides = Map.copyOf(modifierOverrides);
            final List<String> fRewardCommands = List.copyOf(rewardCommands);
            final long fTimeLimitMs = timeLimitMs;
            final Location fFailLoc = failLocation;
            final Location fCompLoc = completeLocation;
            final Boolean fPreventDamage = preventDamage;
            final Boolean fFallDamageFails = fallDamageFailsSession;

            return new ParkourCourse() {
                @Override @NotNull public String getId() { return fId; }
                @Override @NotNull public Component getDisplayName() { return fDisplayName; }
                @Override @NotNull public String getWorldName() { return fWorldName; }
                @Override @NotNull public CourseRegion getStartRegion() { return fStartRegion; }
                @Override @NotNull public CourseRegion getEndRegion() { return fEndRegion; }
                @Override @NotNull @Unmodifiable public List<Location> getStartLocations() { return fStartLocations; }
                @Override @NotNull public Optional<CourseRegion> getPortalRegion() { return Optional.ofNullable(fPortalRegion); }
                @Override public boolean isPortalTeleport() { return fPortalTeleport; }
                @Override @NotNull @Unmodifiable public Set<Material> getBlockList() { return fBlockList; }
                @Override @NotNull public BlockListMode getBlockListMode() { return fBlockListMode; }
                @Override @NotNull public Optional<Integer> getMinY() { return Optional.ofNullable(fMinY); }
                @Override @NotNull public DieMode getDieMode() { return fDieMode; }
                @Override @NotNull @Unmodifiable public List<Checkpoint> getCheckpoints() { return fCheckpoints; }
                @Override @NotNull @Unmodifiable public Map<Material, String> getModifierOverrides() { return fModifierOverrides; }
                @Override @NotNull @Unmodifiable public List<String> getRewardCommands() { return fRewardCommands; }
                @Override public long getTimeLimitMs() { return fTimeLimitMs; }
                @Override @NotNull public Optional<Location> getFailLocation() { return Optional.ofNullable(fFailLoc); }
                @Override @NotNull public Optional<Location> getCompleteLocation() { return Optional.ofNullable(fCompLoc); }
                @Override @NotNull public Optional<Boolean> isPreventDamage() { return Optional.ofNullable(fPreventDamage); }
                @Override @NotNull public Optional<Boolean> isFallDamageFailsSession() { return Optional.ofNullable(fFallDamageFails); }
            };
        }
    }
}

