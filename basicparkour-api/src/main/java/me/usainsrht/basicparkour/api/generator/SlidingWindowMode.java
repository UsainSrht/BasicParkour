package me.usainsrht.basicparkour.api.generator;

/**
 * Determines how old platforms behind the player are removed in a sliding-window parkour run.
 */
public enum SlidingWindowMode {

    /**
     * Platforms are removed based strictly on platform distance behind the player.
     */
    BLOCKS,

    /**
     * Platforms are removed after a specified time duration (in seconds) has elapsed.
     */
    TIME,

    /**
     * Dual mode: platforms are removed if either the distance threshold is exceeded
     * OR the time duration has elapsed.
     */
    DUAL
}
