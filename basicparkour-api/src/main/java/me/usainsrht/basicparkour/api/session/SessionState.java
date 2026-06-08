package me.usainsrht.basicparkour.api.session;

/**
 * Represents the lifecycle state of a {@link ParkourSession}.
 */
public enum SessionState {

    /**
     * The session is active and the player is currently running the course.
     * The timer is counting up.
     */
    ACTIVE,

    /**
     * The session has been completed successfully.
     * The timer has stopped and the elapsed time has been recorded.
     */
    COMPLETED,

    /**
     * The session was failed (kill block, void, disconnect, time limit exceeded, or API call).
     * The player has been or is being teleported to their last checkpoint.
     */
    FAILED,

    /**
     * The session has been forcibly invalidated (e.g., the course was unloaded).
     * This is a terminal state.
     */
    INVALIDATED
}
