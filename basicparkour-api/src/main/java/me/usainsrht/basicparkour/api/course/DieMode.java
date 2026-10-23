package me.usainsrht.basicparkour.api.course;

/**
 * Determines what happens when a player fails a course session.
 */
public enum DieMode {
    /**
     * The player is teleported to their last reached checkpoint (or course start) and their session remains active.
     */
    RESTART_DIE,

    /**
     * The player is teleported to where they were before they started the session, and their session is failed and ended.
     */
    FAIL_DIE
}
