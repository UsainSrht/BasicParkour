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
    @Nullable Location            completeLocation
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
}
