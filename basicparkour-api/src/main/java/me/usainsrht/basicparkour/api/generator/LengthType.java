package me.usainsrht.basicparkour.api.generator;

/**
 * Determines how many jumps a generated parkour run contains.
 */
public enum LengthType {

    /**
     * The course runs forever. New platforms are generated ahead of the player
     * as they progress; the session only ends when the player quits or fails.
     */
    INFINITE,

    /**
     * The course has a fixed number of platforms/jumps configured via
     * {@code fixed-length} in the generator template YAML. When the last
     * platform is reached and the player steps into the end region, the session
     * is completed normally.
     */
    FIXED
}
