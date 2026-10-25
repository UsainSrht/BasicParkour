package me.usainsrht.basicparkour.api.generator;

import me.usainsrht.basicparkour.api.course.DieMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.Map;

/**
 * Immutable definition of a generator template loaded from {@code generators/*.yml}.
 *
 * <p>A template acts as a "blueprint" for creating generated parkour runs. Multiple
 * concurrent {@link GeneratedCourseInstance}s may be created from the same template
 * simultaneously (each with its own seed and world position).</p>
 *
 * @param id                     Unique identifier for this template (filename without extension).
 * @param displayName             Human-readable name shown to players (MiniMessage format).
 * @param difficulty              Base difficulty level (parameters may be overridden below).
 * @param theme                   Block palette used for platform/obstacle placement.
 * @param lengthType              Whether the run is infinite or fixed-length.
 * @param fixedLength             Number of platforms to generate (only used when {@code FIXED}).
 * @param blockMode               Full-upfront or sliding-window block placement.
 * @param slidingWindowAhead      Platforms to generate ahead of the player (sliding window only).
 * @param cleanupDelaySeconds     Seconds to wait after session ends before removing placed blocks.
 * @param entryMode               Where to build the run: dedicated world slot or fixed marker.
 * @param dedicatedWorldName      World name used when {@code entryMode == DEDICATED_WORLD}.
 * @param dedicatedWorldRadius    Radius in the dedicated world within which to search for a slot.
 * @param shared                  If {@code true}, multiple players can run the same instance.
 * @param seed                    Fixed seed; {@code null} = random per run.
 * @param leaderboardCourseId     Course ID used for leaderboard storage (all generated runs with
 *                                the same value share one leaderboard).
 * @param fixedYaw                Fixed yaw angle for WALK_IN entry mode.
 * @param platformShapes          List of allowed platform shapes (e.g. 1x1, 2x2, 3x3).
 * @param minGap                  Override for minimum horizontal gap between platforms.
 * @param maxGap                  Override for maximum horizontal gap.
 * @param minHeightDelta          Override for minimum vertical delta per jump.
 * @param maxHeightDelta          Override for maximum vertical delta per jump.
 * @param platformMinSize         Override for minimum platform width in blocks.
 * @param platformMaxSize         Override for maximum platform width in blocks.
 * @param obstacleChance          Override for probability [0, 1] of an obstacle on each platform.
 * @param modifierChance          Override for probability [0, 1] of a special modifier block.
 * @param modifierOverrides       Material → modifier key mapping injected into the generated course.
 * @param dieMode                 DieMode when player fails (FAIL_DIE vs RESTART_DIE).
 * @param appearAnimation         Animation when new blocks appear in endless/sliding runs (NONE, DEFAULT, GROW).
 * @param slidingWindowMode       Sliding window mode (BLOCKS, TIME, DUAL).
 * @param slidingWindowTimeSeconds Lifespan in seconds for platforms under TIME or DUAL mode (0 = disabled).
 * @param failLocation            Optional custom location to teleport to on fail/leave.
 * @param completeLocation        Optional custom location to teleport to on complete.
 */
public record GeneratorTemplate(
    @NotNull  String             id,
    @NotNull  String             displayName,
    @NotNull  GeneratorDifficulty difficulty,
    @NotNull  GeneratorTheme      theme,
    @NotNull  LengthType          lengthType,
              int                 fixedLength,
    @NotNull  BlockMode           blockMode,
              int                 slidingWindowAhead,
              int                 cleanupDelaySeconds,
    @NotNull  EntryMode           entryMode,
    @Nullable String              dedicatedWorldName,
              int                 dedicatedWorldRadius,
              boolean             shared,
    @Nullable Long                seed,
    @NotNull  String              leaderboardCourseId,
    @Nullable Float               fixedYaw,

    // ── Platform shapes ───────────────────────────────────────────────────
    @NotNull @Unmodifiable List<String> platformShapes,

    // ── Difficulty overrides (NaN / -1 = use difficulty defaults) ─────────
              int                 minGap,
              int                 maxGap,
              int                 minHeightDelta,
              int                 maxHeightDelta,
              int                 platformMinSize,
              int                 platformMaxSize,
              double              obstacleChance,
              double              modifierChance,

    @NotNull @Unmodifiable Map<Material, String> modifierOverrides,

    // ── Endless / session options ─────────────────────────────────────────
    @NotNull  DieMode             dieMode,
    @NotNull  BlockAppearAnimation appearAnimation,
    @NotNull  SlidingWindowMode   slidingWindowMode,
              double              slidingWindowTimeSeconds,
    @Nullable Location            failLocation,
    @Nullable Location            completeLocation,

    // ── Direction & Cone Constraint ───────────────────────────────────────
              double              directionCone,
    @Nullable Float               directionYaw,
              int                 corridorWidth,
              int                 maxHeightDeviation,

    // ── Protection overrides ──────────────────────────────────────────────
    @Nullable Boolean             preventDamage,
    @Nullable Boolean             fallDamageFailsSession
) {
    public GeneratorTemplate {
        platformShapes = List.copyOf(platformShapes);
        modifierOverrides = Map.copyOf(modifierOverrides);
    }

    public GeneratorTemplate(
        @NotNull  String             id,
        @NotNull  String             displayName,
        @NotNull  GeneratorDifficulty difficulty,
        @NotNull  GeneratorTheme      theme,
        @NotNull  LengthType          lengthType,
                  int                 fixedLength,
        @NotNull  BlockMode           blockMode,
                  int                 slidingWindowAhead,
                  int                 cleanupDelaySeconds,
        @NotNull  EntryMode           entryMode,
        @Nullable String              dedicatedWorldName,
                  int                 dedicatedWorldRadius,
                  boolean             shared,
        @Nullable Long                seed,
        @NotNull  String              leaderboardCourseId,
        @Nullable Float               fixedYaw,
        @NotNull  List<String>        platformShapes,
                  int                 minGap,
                  int                 maxGap,
                  int                 minHeightDelta,
                  int                 maxHeightDelta,
                  int                 platformMinSize,
                  int                 platformMaxSize,
                  double              obstacleChance,
                  double              modifierChance,
        @NotNull  Map<Material, String> modifierOverrides,
        @NotNull  DieMode             dieMode,
        @NotNull  BlockAppearAnimation appearAnimation,
        @NotNull  SlidingWindowMode   slidingWindowMode,
                  double              slidingWindowTimeSeconds,
        @Nullable Location            failLocation,
        @Nullable Location            completeLocation,
                  double              directionCone,
        @Nullable Float               directionYaw,
                  int                 corridorWidth,
                  int                 maxHeightDeviation
    ) {
        this(
            id, displayName, difficulty, theme, lengthType, fixedLength,
            blockMode, slidingWindowAhead, cleanupDelaySeconds,
            entryMode, dedicatedWorldName, dedicatedWorldRadius, shared, seed,
            leaderboardCourseId, fixedYaw, platformShapes,
            minGap, maxGap, minHeightDelta, maxHeightDelta,
            platformMinSize, platformMaxSize, obstacleChance, modifierChance,
            modifierOverrides,
            dieMode, appearAnimation, slidingWindowMode, slidingWindowTimeSeconds,
            failLocation, completeLocation,
            directionCone, directionYaw, corridorWidth, maxHeightDeviation,
            null, null
        );
    }

    public GeneratorTemplate(
        @NotNull  String             id,
        @NotNull  String             displayName,
        @NotNull  GeneratorDifficulty difficulty,
        @NotNull  GeneratorTheme      theme,
        @NotNull  LengthType          lengthType,
                  int                 fixedLength,
        @NotNull  BlockMode           blockMode,
                  int                 slidingWindowAhead,
                  int                 cleanupDelaySeconds,
        @NotNull  EntryMode           entryMode,
        @Nullable String              dedicatedWorldName,
                  int                 dedicatedWorldRadius,
                  boolean             shared,
        @Nullable Long                seed,
        @NotNull  String              leaderboardCourseId,
        @Nullable Float               fixedYaw,
        @NotNull  List<String>        platformShapes,
                  int                 minGap,
                  int                 maxGap,
                  int                 minHeightDelta,
                  int                 maxHeightDelta,
                  int                 platformMinSize,
                  int                 platformMaxSize,
                  double              obstacleChance,
                  double              modifierChance,
        @NotNull  Map<Material, String> modifierOverrides,
        @NotNull  DieMode             dieMode,
        @NotNull  BlockAppearAnimation appearAnimation,
        @NotNull  SlidingWindowMode   slidingWindowMode,
                  double              slidingWindowTimeSeconds,
        @Nullable Location            failLocation,
        @Nullable Location            completeLocation
    ) {
        this(
            id, displayName, difficulty, theme, lengthType, fixedLength,
            blockMode, slidingWindowAhead, cleanupDelaySeconds,
            entryMode, dedicatedWorldName, dedicatedWorldRadius, shared, seed,
            leaderboardCourseId, fixedYaw, platformShapes,
            minGap, maxGap, minHeightDelta, maxHeightDelta,
            platformMinSize, platformMaxSize, obstacleChance, modifierChance,
            modifierOverrides,
            dieMode, appearAnimation, slidingWindowMode, slidingWindowTimeSeconds,
            failLocation, completeLocation,
            lengthType == LengthType.INFINITE ? 90.0 : 360.0,
            null,
            0,
            15
        );
    }

    public GeneratorTemplate(
        @NotNull  String             id,
        @NotNull  String             displayName,
        @NotNull  GeneratorDifficulty difficulty,
        @NotNull  GeneratorTheme      theme,
        @NotNull  LengthType          lengthType,
                  int                 fixedLength,
        @NotNull  BlockMode           blockMode,
                  int                 slidingWindowAhead,
                  int                 cleanupDelaySeconds,
        @NotNull  EntryMode           entryMode,
        @Nullable String              dedicatedWorldName,
                  int                 dedicatedWorldRadius,
                  boolean             shared,
        @Nullable Long                seed,
        @NotNull  String              leaderboardCourseId,
                  int                 minGap,
                  int                 maxGap,
                  int                 minHeightDelta,
                  int                 maxHeightDelta,
                  int                 platformMinSize,
                  int                 platformMaxSize,
                  double              obstacleChance,
                  double              modifierChance,
        @NotNull Map<Material, String> modifierOverrides
    ) {
        this(
            id, displayName, difficulty, theme, lengthType, fixedLength,
            blockMode, slidingWindowAhead, cleanupDelaySeconds,
            entryMode, dedicatedWorldName, dedicatedWorldRadius, shared, seed,
            leaderboardCourseId, null, List.of(),
            minGap, maxGap, minHeightDelta, maxHeightDelta,
            platformMinSize, platformMaxSize, obstacleChance, modifierChance,
            modifierOverrides,
            lengthType == LengthType.INFINITE ? DieMode.FAIL_DIE : DieMode.RESTART_DIE,
            BlockAppearAnimation.DEFAULT,
            SlidingWindowMode.BLOCKS,
            0.0,
            null,
            null
        );
    }

    public GeneratorTemplate(
        @NotNull  String             id,
        @NotNull  String             displayName,
        @NotNull  GeneratorDifficulty difficulty,
        @NotNull  GeneratorTheme      theme,
        @NotNull  LengthType          lengthType,
                  int                 fixedLength,
        @NotNull  BlockMode           blockMode,
                  int                 slidingWindowAhead,
                  int                 cleanupDelaySeconds,
        @NotNull  EntryMode           entryMode,
        @Nullable String              dedicatedWorldName,
                  int                 dedicatedWorldRadius,
                  boolean             shared,
        @Nullable Long                seed,
        @NotNull  String              leaderboardCourseId,
        @Nullable Float               fixedYaw,
        @NotNull  List<String>        platformShapes,
                  int                 minGap,
                  int                 maxGap,
                  int                 minHeightDelta,
                  int                 maxHeightDelta,
                  int                 platformMinSize,
                  int                 platformMaxSize,
                  double              obstacleChance,
                  double              modifierChance,
        @NotNull Map<Material, String> modifierOverrides
    ) {
        this(
            id, displayName, difficulty, theme, lengthType, fixedLength,
            blockMode, slidingWindowAhead, cleanupDelaySeconds,
            entryMode, dedicatedWorldName, dedicatedWorldRadius, shared, seed,
            leaderboardCourseId, fixedYaw, platformShapes,
            minGap, maxGap, minHeightDelta, maxHeightDelta,
            platformMinSize, platformMaxSize, obstacleChance, modifierChance,
            modifierOverrides,
            lengthType == LengthType.INFINITE ? DieMode.FAIL_DIE : DieMode.RESTART_DIE,
            BlockAppearAnimation.DEFAULT,
            SlidingWindowMode.BLOCKS,
            0.0,
            null,
            null
        );
    }

    public GeneratorTemplate(
        @NotNull  String             id,
        @NotNull  String             displayName,
        @NotNull  GeneratorDifficulty difficulty,
        @NotNull  GeneratorTheme      theme,
        @NotNull  LengthType          lengthType,
                  int                 fixedLength,
        @NotNull  BlockMode           blockMode,
                  int                 slidingWindowAhead,
                  int                 cleanupDelaySeconds,
        @NotNull  EntryMode           entryMode,
        @Nullable String              dedicatedWorldName,
                  int                 dedicatedWorldRadius,
                  boolean             shared,
        @Nullable Long                seed,
        @NotNull  String              leaderboardCourseId,
        @Nullable Float               fixedYaw,
                  int                 minGap,
                  int                 maxGap,
                  int                 minHeightDelta,
                  int                 maxHeightDelta,
                  int                 platformMinSize,
                  int                 platformMaxSize,
                  double              obstacleChance,
                  double              modifierChance,
        @NotNull Map<Material, String> modifierOverrides
    ) {
        this(
            id, displayName, difficulty, theme, lengthType, fixedLength,
            blockMode, slidingWindowAhead, cleanupDelaySeconds,
            entryMode, dedicatedWorldName, dedicatedWorldRadius, shared, seed,
            leaderboardCourseId, fixedYaw, List.of(),
            minGap, maxGap, minHeightDelta, maxHeightDelta,
            platformMinSize, platformMaxSize, obstacleChance, modifierChance,
            modifierOverrides,
            lengthType == LengthType.INFINITE ? DieMode.FAIL_DIE : DieMode.RESTART_DIE,
            BlockAppearAnimation.DEFAULT,
            SlidingWindowMode.BLOCKS,
            0.0,
            null,
            null
        );
    }

    // ── Resolved parameter helpers (merges difficulty defaults + overrides) ─

    /** Resolved platform shapes. */
    public @NotNull List<String> resolvedPlatformShapes() {
        if (!platformShapes.isEmpty()) {
            return platformShapes;
        }
        int min = resolvedPlatformMinSize();
        int max = resolvedPlatformMaxSize();
        if (min == 1 && max == 1) return List.of("1x1");
        if (min == 2 && max == 2) return List.of("2x2");
        if (min >= 3 && max >= 3) return List.of("3x3");
        List<String> list = new java.util.ArrayList<>();
        if (min <= 1) list.add("1x1");
        if (min <= 2 && max >= 2) list.add("2x2");
        if (max >= 3) list.add("3x3");
        return list.isEmpty() ? List.of("1x1") : list;
    }

    /** Resolved minimum gap, accounting for difficulty defaults. */
    public int resolvedMinGap() {
        return minGap > 0 ? minGap : difficulty.getDefaultMinGap();
    }

    /** Resolved maximum gap. */
    public int resolvedMaxGap() {
        return maxGap > 0 ? maxGap : difficulty.getDefaultMaxGap();
    }

    /** Resolved minimum height delta. */
    public int resolvedMinHeightDelta() {
        return minHeightDelta != Integer.MIN_VALUE ? minHeightDelta : difficulty.getDefaultMinHeightDelta();
    }

    /** Resolved maximum height delta. */
    public int resolvedMaxHeightDelta() {
        return maxHeightDelta != Integer.MAX_VALUE ? maxHeightDelta : difficulty.getDefaultMaxHeightDelta();
    }

    /** Resolved platform minimum width. */
    public int resolvedPlatformMinSize() {
        return platformMinSize > 0 ? platformMinSize : difficulty.getDefaultPlatformMinSize();
    }

    /** Resolved platform maximum width. */
    public int resolvedPlatformMaxSize() {
        return platformMaxSize > 0 ? platformMaxSize : difficulty.getDefaultPlatformMaxSize();
    }

    /** Resolved obstacle chance [0, 1]. */
    public double resolvedObstacleChance() {
        return obstacleChance >= 0 ? obstacleChance : difficulty.getDefaultObstacleChance();
    }

    /** Resolved modifier chance [0, 1]. */
    public double resolvedModifierChance() {
        return modifierChance >= 0 ? modifierChance : difficulty.getDefaultModifierChance();
    }

    /**
     * Resolved direction cone angle in degrees [0, 360].
     * Defaults to 90.0 for infinite runs, 360.0 for unconstrained runs.
     */
    public double resolvedDirectionCone() {
        if (directionCone >= 0.0) return directionCone;
        return lengthType == LengthType.INFINITE ? 90.0 : 360.0;
    }

    /**
     * Resolved base yaw direction for the cone projection.
     */
    public float resolvedDirectionYaw(float defaultYaw) {
        if (directionYaw != null) return directionYaw;
        if (fixedYaw != null) return fixedYaw;
        return defaultYaw;
    }

    /**
     * Resolved corridor width in blocks (0 = disabled / unconstrained cone).
     */
    public int resolvedCorridorWidth() {
        return Math.max(0, corridorWidth);
    }

    /**
     * Resolved maximum height deviation in blocks from start Y (0 = disabled).
     */
    public int resolvedMaxHeightDeviation() {
        return Math.max(0, maxHeightDeviation);
    }

    /** Resolved maximum turn angle spread in degrees. */
    public double resolvedMaxTurnAngle() {
        return difficulty.getDefaultMaxTurnAngle();
    }

    /** Resolved sliding window ahead platform count. */
    public int resolvedSlidingWindowAhead() {
        return slidingWindowAhead > 0 ? slidingWindowAhead : difficulty.getDefaultSlidingWindowAhead();
    }

    /** Resolved platform lifespan in seconds before disappearing. */
    public double resolvedSlidingWindowTimeSeconds() {
        return slidingWindowTimeSeconds > 0.0 ? slidingWindowTimeSeconds : difficulty.getDefaultSlidingWindowTimeSeconds();
    }

    /**
     * Creates a new fluent builder for defining a {@link GeneratorTemplate} programmatically.
     *
     * @param id the unique template ID
     * @return a new Builder
     */
    @NotNull
    public static Builder builder(@NotNull String id) {
        return new Builder(id);
    }

    /**
     * Fluent builder for {@link GeneratorTemplate}.
     */
    public static final class Builder {
        private final String id;
        private String displayName;
        private GeneratorDifficulty difficulty = GeneratorDifficulty.MEDIUM;
        private GeneratorTheme theme = new GeneratorTheme("default", List.of(Material.STONE), List.of(), List.of());
        private LengthType lengthType = LengthType.INFINITE;
        private int fixedLength = 50;
        private BlockMode blockMode = BlockMode.SLIDING_WINDOW;
        private int slidingWindowAhead = 5;
        private int cleanupDelaySeconds = 10;
        private EntryMode entryMode = EntryMode.DEDICATED_WORLD;
        private String dedicatedWorldName = "parkour_generator";
        private int dedicatedWorldRadius = 2000;
        private boolean shared = false;
        private Long seed = null;
        private String leaderboardCourseId;
        private Float fixedYaw = null;
        private List<String> platformShapes = List.of("1x1");
        private int minGap = -1;
        private int maxGap = -1;
        private int minHeightDelta = Integer.MIN_VALUE;
        private int maxHeightDelta = Integer.MAX_VALUE;
        private int platformMinSize = -1;
        private int platformMaxSize = -1;
        private double obstacleChance = -1.0;
        private double modifierChance = -1.0;
        private Map<Material, String> modifierOverrides = Map.of();
        private DieMode dieMode = DieMode.FAIL_DIE;
        private BlockAppearAnimation appearAnimation = BlockAppearAnimation.DEFAULT;
        private SlidingWindowMode slidingWindowMode = SlidingWindowMode.BLOCKS;
        private double slidingWindowTimeSeconds = 0.0;
        private Location failLocation = null;
        private Location completeLocation = null;
        private double directionCone = 90.0;
        private Float directionYaw = null;
        private int corridorWidth = 0;
        private int maxHeightDeviation = 15;
        private Boolean preventDamage = null;
        private Boolean fallDamageFailsSession = null;

        public Builder(@NotNull String id) {
            this.id = java.util.Objects.requireNonNull(id, "id cannot be null");
            this.displayName = id;
            this.leaderboardCourseId = id;
        }

        public Builder displayName(@NotNull String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder difficulty(@NotNull GeneratorDifficulty difficulty) {
            this.difficulty = difficulty;
            return this;
        }

        public Builder theme(@NotNull GeneratorTheme theme) {
            this.theme = theme;
            return this;
        }

        public Builder lengthType(@NotNull LengthType lengthType) {
            this.lengthType = lengthType;
            return this;
        }

        public Builder fixedLength(int fixedLength) {
            this.fixedLength = fixedLength;
            return this;
        }

        public Builder blockMode(@NotNull BlockMode blockMode) {
            this.blockMode = blockMode;
            return this;
        }

        public Builder slidingWindowAhead(int slidingWindowAhead) {
            this.slidingWindowAhead = slidingWindowAhead;
            return this;
        }

        public Builder cleanupDelaySeconds(int cleanupDelaySeconds) {
            this.cleanupDelaySeconds = cleanupDelaySeconds;
            return this;
        }

        public Builder entryMode(@NotNull EntryMode entryMode) {
            this.entryMode = entryMode;
            return this;
        }

        public Builder dedicatedWorldName(@Nullable String dedicatedWorldName) {
            this.dedicatedWorldName = dedicatedWorldName;
            return this;
        }

        public Builder dedicatedWorldRadius(int dedicatedWorldRadius) {
            this.dedicatedWorldRadius = dedicatedWorldRadius;
            return this;
        }

        public Builder shared(boolean shared) {
            this.shared = shared;
            return this;
        }

        public Builder seed(@Nullable Long seed) {
            this.seed = seed;
            return this;
        }

        public Builder leaderboardCourseId(@NotNull String leaderboardCourseId) {
            this.leaderboardCourseId = leaderboardCourseId;
            return this;
        }

        public Builder fixedYaw(@Nullable Float fixedYaw) {
            this.fixedYaw = fixedYaw;
            return this;
        }

        public Builder platformShapes(@NotNull List<String> shapes) {
            this.platformShapes = shapes;
            return this;
        }

        public Builder minGap(int minGap) {
            this.minGap = minGap;
            return this;
        }

        public Builder maxGap(int maxGap) {
            this.maxGap = maxGap;
            return this;
        }

        public Builder minHeightDelta(int minHeightDelta) {
            this.minHeightDelta = minHeightDelta;
            return this;
        }

        public Builder maxHeightDelta(int maxHeightDelta) {
            this.maxHeightDelta = maxHeightDelta;
            return this;
        }

        public Builder platformMinSize(int platformMinSize) {
            this.platformMinSize = platformMinSize;
            return this;
        }

        public Builder platformMaxSize(int platformMaxSize) {
            this.platformMaxSize = platformMaxSize;
            return this;
        }

        public Builder obstacleChance(double obstacleChance) {
            this.obstacleChance = obstacleChance;
            return this;
        }

        public Builder modifierChance(double modifierChance) {
            this.modifierChance = modifierChance;
            return this;
        }

        public Builder modifierOverrides(@NotNull Map<Material, String> modifierOverrides) {
            this.modifierOverrides = modifierOverrides;
            return this;
        }

        public Builder dieMode(@NotNull DieMode dieMode) {
            this.dieMode = dieMode;
            return this;
        }

        public Builder appearAnimation(@NotNull BlockAppearAnimation appearAnimation) {
            this.appearAnimation = appearAnimation;
            return this;
        }

        public Builder slidingWindowMode(@NotNull SlidingWindowMode slidingWindowMode) {
            this.slidingWindowMode = slidingWindowMode;
            return this;
        }

        public Builder slidingWindowTimeSeconds(double slidingWindowTimeSeconds) {
            this.slidingWindowTimeSeconds = slidingWindowTimeSeconds;
            return this;
        }

        public Builder failLocation(@Nullable Location failLocation) {
            this.failLocation = failLocation;
            return this;
        }

        public Builder completeLocation(@Nullable Location completeLocation) {
            this.completeLocation = completeLocation;
            return this;
        }

        public Builder directionCone(double directionCone) {
            this.directionCone = directionCone;
            return this;
        }

        public Builder directionYaw(@Nullable Float directionYaw) {
            this.directionYaw = directionYaw;
            return this;
        }

        public Builder corridorWidth(int corridorWidth) {
            this.corridorWidth = corridorWidth;
            return this;
        }

        public Builder maxHeightDeviation(int maxHeightDeviation) {
            this.maxHeightDeviation = maxHeightDeviation;
            return this;
        }

        public Builder preventDamage(@Nullable Boolean preventDamage) {
            this.preventDamage = preventDamage;
            return this;
        }

        public Builder fallDamageFailsSession(@Nullable Boolean fallDamageFailsSession) {
            this.fallDamageFailsSession = fallDamageFailsSession;
            return this;
        }

        @NotNull
        public GeneratorTemplate build() {
            return new GeneratorTemplate(
                id, displayName, difficulty, theme, lengthType, fixedLength,
                blockMode, slidingWindowAhead, cleanupDelaySeconds, entryMode,
                dedicatedWorldName, dedicatedWorldRadius, shared, seed, leaderboardCourseId,
                fixedYaw, platformShapes, minGap, maxGap, minHeightDelta, maxHeightDelta,
                platformMinSize, platformMaxSize, obstacleChance, modifierChance,
                modifierOverrides, dieMode, appearAnimation, slidingWindowMode,
                slidingWindowTimeSeconds, failLocation, completeLocation,
                directionCone, directionYaw, corridorWidth, maxHeightDeviation,
                preventDamage, fallDamageFailsSession
            );
        }
    }
}
