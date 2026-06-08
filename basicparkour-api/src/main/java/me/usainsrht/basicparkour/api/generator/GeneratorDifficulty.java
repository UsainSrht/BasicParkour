package me.usainsrht.basicparkour.api.generator;

/**
 * Difficulty level for a generated parkour course.
 *
 * <p>Each constant carries default parameter ranges used by the generator algorithm.
 * All defaults can be overridden on a per-template basis via {@code difficulty-overrides}
 * in the generator YAML file.</p>
 */
public enum GeneratorDifficulty {

    /** Very short gaps, flat terrain, no obstacles or modifiers by default. */
    EASY(
        /* minGap */          2,
        /* maxGap */          3,
        /* minHeightDelta */  -1,
        /* maxHeightDelta */   1,
        /* platformMinSize */  2,
        /* platformMaxSize */  3,
        /* obstacleChance */   0.0,
        /* modifierChance */   0.0
    ),

    /** Moderate gaps and mild height changes; occasional obstacles. */
    MEDIUM(
        3, 5,
        -2, 2,
        1, 2,
        0.15, 0.05
    ),

    /** Larger gaps, steeper height changes, regular obstacles and modifiers. */
    HARD(
        4, 6,
        -2, 3,
        1, 2,
        0.25, 0.10
    ),

    /** Extreme gaps, sharp height changes, frequent obstacles and modifiers. */
    EXPERT(
        5, 8,
        -3, 4,
        1, 1,
        0.40, 0.18
    );

    // ── Default parameter fields ───────────────────────────────────────────

    private final int    defaultMinGap;
    private final int    defaultMaxGap;
    private final int    defaultMinHeightDelta;
    private final int    defaultMaxHeightDelta;
    /** Minimum width of a generated platform (in blocks). */
    private final int    defaultPlatformMinSize;
    /** Maximum width of a generated platform (in blocks). */
    private final int    defaultPlatformMaxSize;
    /** Probability [0, 1] of placing a slab/fence obstacle on a platform. */
    private final double defaultObstacleChance;
    /** Probability [0, 1] of placing a special modifier block on a platform. */
    private final double defaultModifierChance;

    GeneratorDifficulty(int minGap, int maxGap,
                        int minHeightDelta, int maxHeightDelta,
                        int platformMinSize, int platformMaxSize,
                        double obstacleChance, double modifierChance) {
        this.defaultMinGap          = minGap;
        this.defaultMaxGap          = maxGap;
        this.defaultMinHeightDelta  = minHeightDelta;
        this.defaultMaxHeightDelta  = maxHeightDelta;
        this.defaultPlatformMinSize = platformMinSize;
        this.defaultPlatformMaxSize = platformMaxSize;
        this.defaultObstacleChance  = obstacleChance;
        this.defaultModifierChance  = modifierChance;
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public int    getDefaultMinGap()          { return defaultMinGap; }
    public int    getDefaultMaxGap()          { return defaultMaxGap; }
    public int    getDefaultMinHeightDelta()  { return defaultMinHeightDelta; }
    public int    getDefaultMaxHeightDelta()  { return defaultMaxHeightDelta; }
    public int    getDefaultPlatformMinSize() { return defaultPlatformMinSize; }
    public int    getDefaultPlatformMaxSize() { return defaultPlatformMaxSize; }
    public double getDefaultObstacleChance()  { return defaultObstacleChance; }
    public double getDefaultModifierChance()  { return defaultModifierChance; }
}
