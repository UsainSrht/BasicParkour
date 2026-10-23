package me.usainsrht.basicparkour.api.course;

/**
 * Determines how the blocklist applies to a course.
 */
public enum BlockListMode {
    /**
     * Players stepping on any material in the blocklist will fail the session.
     */
    BLACKLIST,

    /**
     * Players must only step on materials in the blocklist. Stepping on any other solid block fails the session.
     */
    WHITELIST
}
