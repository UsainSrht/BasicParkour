package me.usainsrht.basicparkour.api.session;

import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.event.CourseCompleteEvent;
import me.usainsrht.basicparkour.api.event.SessionFailEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Represents an active parkour run for a single player on a specific course.
 *
 * <p>Sessions are created by {@link BasicParkourAPI.SessionManager#startSession}
 * and invalidated when the player completes, fails, or disconnects.</p>
 */
public interface ParkourSession {

    /**
     * The player this session belongs to.
     *
     * @return the player
     */
    @NotNull
    Player getPlayer();

    /**
     * The course being run.
     *
     * @return the course
     */
    @NotNull
    ParkourCourse getCourse();

    /**
     * The current state of this session.
     *
     * @return session state
     */
    @NotNull
    SessionState getState();

    /**
     * The epoch millisecond timestamp at which the session started.
     *
     * @return start timestamp in milliseconds
     */
    long getStartTime();

    /**
     * The zero-based index of the <em>last reached</em> checkpoint.
     * Returns {@code -1} if no checkpoint has been reached yet (player is at the start).
     *
     * @return last checkpoint index, or -1
     */
    int getCurrentCheckpointIndex();

    /**
     * Returns the last checkpoint the player reached, or an empty Optional if none.
     *
     * @return last checkpoint optional
     */
    @NotNull
    default Optional<Checkpoint> getLastCheckpoint() {
        int idx = getCurrentCheckpointIndex();
        if (idx < 0) return Optional.empty();
        return getCourse().getCheckpoint(idx);
    }

    /**
     * Returns the next checkpoint the player needs to reach, or empty if all checkpoints
     * have been passed (player is heading to the end).
     *
     * @return next checkpoint optional
     */
    @NotNull
    default Optional<Checkpoint> getNextCheckpoint() {
        return getCourse().getCheckpoint(getCurrentCheckpointIndex() + 1);
    }

    /**
     * Calculates the elapsed time since this session started.
     * Uses {@link System#currentTimeMillis()} for millisecond accuracy.
     *
     * @return elapsed time in milliseconds
     */
    default long getElapsedMillis() {
        return System.currentTimeMillis() - getStartTime();
    }

    /**
     * Returns a snapshot of the current timer state.
     *
     * @return timer snapshot
     */
    @NotNull
    TimerSnapshot getTimerSnapshot();

    /**
     * Immediately fails this session. The player is teleported to their last checkpoint
     * (or the course start if no checkpoint was reached). Fires {@link SessionFailEvent}.
     *
     * <p>Must be called from a thread-safe context for the player's entity region.</p>
     *
     * @param reason the reason for the failure
     */
    void fail(@NotNull FailReason reason);

    /**
     * Marks this session as complete. Saves the record asynchronously,
     * runs reward commands, and fires {@link CourseCompleteEvent}.
     *
     * <p>Must be called from a thread-safe context for the player's entity region.</p>
     */
    void complete();

    /**
     * Reasons a session can be failed.
     */
    enum FailReason {
        /** Player stepped on a kill block. */
        KILL_BLOCK,
        /** Player fell out of the world. */
        VOID,
        /** The course time limit was exceeded. */
        TIME_LIMIT,
        /** The player disconnected. */
        DISCONNECT,
        /** Triggered programmatically by a third-party plugin. */
        API
    }
}
