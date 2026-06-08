package me.usainsrht.basicparkour.api.generator;

/**
 * Controls how blocks are placed and removed during a generated parkour run.
 */
public enum BlockMode {

    /**
     * All platforms are placed into the world upfront before the session starts
     * (for {@link LengthType#FIXED} runs) or in large batches ahead of time.
     * All placed blocks are cleaned up after a configurable delay once the session ends.
     */
    FULL_UPFRONT,

    /**
     * Platforms are generated on-demand as the player approaches them ("look-ahead"),
     * and old platforms behind the player are removed ("trail-behind") automatically.
     * This minimises the world footprint for infinite or very long runs.
     *
     * <p>The number of platforms kept visible ahead and behind is controlled by
     * {@code sliding-window-ahead} in the generator template YAML.</p>
     */
    SLIDING_WINDOW
}
