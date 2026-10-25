package me.usainsrht.basicparkour.api.generator;

/**
 * Difficulty level for a generated parkour course.
 *
 * <p>Each constant carries default parameter ranges used by the generator algorithm.
 * All defaults can be overridden on a per-template basis via {@code difficulty-overrides}
 * in the generator YAML file.</p>
 */
public enum GeneratorDifficulty {

    /**
     * Welcoming, accessible, relaxed parkour.
     * Generous landing platforms (2x2, 3x3), short gaps (1-2 air blocks),
     * gentle forward curves, flat or mild downward steps, zero obstacles.
     */
    EASY(
        /* minGap */                  2,
        /* maxGap */                  3,
        /* minHeightDelta */         -1,
        /* maxHeightDelta */          0,
        /* platformMinSize */         2,
        /* platformMaxSize */         3,
        /* obstacleChance */          0.0,
        /* modifierChance */          0.0,
        /* maxTurnAngle */           20.0,
        /* slidingWindowAhead */      6,
        /* slidingWindowTimeSeconds */ 12.0
    ),

    /**
     * Standard balanced Minecraft parkour.
     * Classic sprint-jumps (1-3 air blocks), mild verticality (-1 to +1),
     * natural winding turns (up to 40°), 1x1 and 2x2 platforms, occasional obstacles.
     */
    MEDIUM(
        /* minGap */                  2,
        /* maxGap */                  4,
        /* minHeightDelta */         -1,
        /* maxHeightDelta */          1,
        /* platformMinSize */         1,
        /* platformMaxSize */         2,
        /* obstacleChance */          0.10,
        /* modifierChance */          0.05,
        /* maxTurnAngle */           40.0,
        /* slidingWindowAhead */      5,
        /* slidingWindowTimeSeconds */ 7.0
    ),

    /**
     * Challenging technical parkour requiring sprint momentum preservation.
     * True 4-block flat jumps (gap 5), precision 1x1 landings, steeper drops (-2 to +1),
     * sharp turns (up to 65°) and zig-zags, faster sliding window tempo (4.0s).
     */
    HARD(
        /* minGap */                  3,
        /* maxGap */                  5,
        /* minHeightDelta */         -2,
        /* maxHeightDelta */          1,
        /* platformMinSize */         1,
        /* platformMaxSize */         1,
        /* obstacleChance */          0.25,
        /* modifierChance */          0.12,
        /* maxTurnAngle */           65.0,
        /* slidingWindowAhead */      4,
        /* slidingWindowTimeSeconds */ 4.0
    ),

    /**
     * High-stakes elite parkour for skilled runners.
     * Maximum range jumps (gap 5 flat, gap 6 downhill), acute corner jumps (up to 85°),
     * technical +1 elevation climbs with tight margins, steep 3-block drops,
     * unforgiving 1x1 landings, rapid reaction time (2.5s sliding window).
     */
    EXPERT(
        /* minGap */                  3,
        /* maxGap */                  5,
        /* minHeightDelta */         -3,
        /* maxHeightDelta */          1,
        /* platformMinSize */         1,
        /* platformMaxSize */         1,
        /* obstacleChance */          0.35,
        /* modifierChance */          0.20,
        /* maxTurnAngle */           85.0,
        /* slidingWindowAhead */      3,
        /* slidingWindowTimeSeconds */ 2.5
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
    /** Maximum heading turn angle spread in degrees. */
    private final double defaultMaxTurnAngle;
    /** Default sliding window ahead platform count for endless runs. */
    private final int    defaultSlidingWindowAhead;
    /** Default platform lifespan in seconds before disappearing. */
    private final double defaultSlidingWindowTimeSeconds;

    GeneratorDifficulty(int minGap, int maxGap,
                        int minHeightDelta, int maxHeightDelta,
                        int platformMinSize, int platformMaxSize,
                        double obstacleChance, double modifierChance) {
        this(minGap, maxGap, minHeightDelta, maxHeightDelta,
             platformMinSize, platformMaxSize, obstacleChance, modifierChance,
             45.0, 5, 10.0);
    }

    GeneratorDifficulty(int minGap, int maxGap,
                        int minHeightDelta, int maxHeightDelta,
                        int platformMinSize, int platformMaxSize,
                        double obstacleChance, double modifierChance,
                        double maxTurnAngle,
                        int slidingWindowAhead,
                        double slidingWindowTimeSeconds) {
        this.defaultMinGap                  = minGap;
        this.defaultMaxGap                  = maxGap;
        this.defaultMinHeightDelta          = minHeightDelta;
        this.defaultMaxHeightDelta          = maxHeightDelta;
        this.defaultPlatformMinSize         = platformMinSize;
        this.defaultPlatformMaxSize         = platformMaxSize;
        this.defaultObstacleChance          = obstacleChance;
        this.defaultModifierChance          = modifierChance;
        this.defaultMaxTurnAngle            = maxTurnAngle;
        this.defaultSlidingWindowAhead      = slidingWindowAhead;
        this.defaultSlidingWindowTimeSeconds = slidingWindowTimeSeconds;
    }

    // ── Getters ────────────────────────────────────────────────────────────

    public int    getDefaultMinGap()                  { return defaultMinGap; }
    public int    getDefaultMaxGap()                  { return defaultMaxGap; }
    public int    getDefaultMinHeightDelta()          { return defaultMinHeightDelta; }
    public int    getDefaultMaxHeightDelta()          { return defaultMaxHeightDelta; }
    public int    getDefaultPlatformMinSize()         { return defaultPlatformMinSize; }
    public int    getDefaultPlatformMaxSize()         { return defaultPlatformMaxSize; }
    public double getDefaultObstacleChance()          { return defaultObstacleChance; }
    public double getDefaultModifierChance()          { return defaultModifierChance; }
    public double getDefaultMaxTurnAngle()            { return defaultMaxTurnAngle; }
    public int    getDefaultSlidingWindowAhead()      { return defaultSlidingWindowAhead; }
    public double getDefaultSlidingWindowTimeSeconds() { return defaultSlidingWindowTimeSeconds; }
}
