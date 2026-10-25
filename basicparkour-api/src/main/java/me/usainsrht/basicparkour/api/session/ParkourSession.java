package me.usainsrht.basicparkour.api.session;

import me.usainsrht.basicparkour.api.BasicParkourAPI;
import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.event.CourseCompleteEvent;
import me.usainsrht.basicparkour.api.event.SessionFailEvent;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.Map;
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
     * The location where the player was when they joined the session.
     * Used for returning the player if they fail with FAIL_DIE mode.
     *
     * @return the join location
     */
    @NotNull
    org.bukkit.Location getJoinLocation();

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
     * Takes into account paused time and time penalties.
     *
     * @return elapsed time in milliseconds
     */
    default long getElapsedMillis() {
        if (getState() == SessionState.PAUSED) {
            return Math.max(0, getPausedElapsedMillis() + getTimePenalty());
        }
        return Math.max(0, (System.currentTimeMillis() - getStartTime() - getPausedDurationMillis()) + getTimePenalty());
    }

    /**
     * Returns the elapsed milliseconds recorded at the moment this session was paused.
     *
     * @return paused elapsed milliseconds
     */
    default long getPausedElapsedMillis() {
        return 0L;
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

    // ── Session Controls ─────────────────────────────────────────────────────

    /**
     * Restarts this session from the beginning.
     */
    default void restart() {}

    /**
     * Leaves this session cleanly and teleports the player back to where they started.
     */
    default void leave() {
        leave(true);
    }

    /**
     * Leaves this session cleanly, with configurable teleportation.
     *
     * @param teleport true to teleport the player to their exit/join location
     */
    default void leave(boolean teleport) {}

    /**
     * Teleports the player back to their last checkpoint (or start location).
     */
    default void teleportToCheckpoint() {}

    /**
     * Pauses this parkour session. While paused, the elapsed timer is frozen,
     * checkpoint triggers and modifiers are bypassed.
     *
     * @return true if successfully paused, false if already paused or not active
     */
    default boolean pause() {
        return false;
    }

    /**
     * Resumes a paused session.
     *
     * @return true if successfully resumed, false if not paused
     */
    default boolean resume() {
        return false;
    }

    /**
     * Returns whether this session is currently paused.
     *
     * @return true if paused
     */
    default boolean isPaused() {
        return getState() == SessionState.PAUSED;
    }

    /**
     * Returns the total duration (in milliseconds) this session has spent paused.
     *
     * @return paused duration in ms
     */
    default long getPausedDurationMillis() {
        return 0L;
    }

    // ── Checkpoint & Timer Modification ──────────────────────────────────────

    /**
     * Explicitly sets the current checkpoint index for this session.
     *
     * @param checkpointIndex the new checkpoint index (-1 for start)
     */
    default void setCurrentCheckpointIndex(int checkpointIndex) {}

    /**
     * Advances the checkpoint index by one.
     *
     * @return the new checkpoint index, or -1 if not active
     */
    default int advanceCheckpoint() {
        return -1;
    }

    /**
     * Adds or subtracts time (in milliseconds) from this session's elapsed time.
     * Positive millis adds a time penalty (increases elapsed time).
     * Negative millis gives a time bonus (decreases elapsed time).
     *
     * @param penaltyMillis amount of milliseconds to add as a penalty
     */
    default void addTimePenalty(long penaltyMillis) {}

    /**
     * Returns the total penalty time in milliseconds applied to this session.
     *
     * @return time penalty in ms
     */
    default long getTimePenalty() {
        return 0L;
    }

    /**
     * Updates the start timestamp of this session.
     *
     * @param newStartTime the new start timestamp in milliseconds
     */
    default void setStartTime(long newStartTime) {}

    // ── Location Overrides ───────────────────────────────────────────────────

    /**
     * Sets or updates the location where the player will be teleported if returned to join location.
     *
     * @param location the new join location
     */
    default void setJoinLocation(@NotNull org.bukkit.Location location) {}

    /**
     * Gets custom fail location override for this session if configured.
     *
     * @return optional custom fail location
     */
    @NotNull
    default Optional<Location> getCustomFailLocation() {
        return Optional.empty();
    }

    /**
     * Sets a custom fail location override for this session.
     *
     * @param location custom fail location, or null to clear
     */
    default void setCustomFailLocation(@Nullable Location location) {}

    /**
     * Gets custom complete location override for this session if configured.
     *
     * @return optional custom complete location
     */
    @NotNull
    default Optional<Location> getCustomCompleteLocation() {
        return Optional.empty();
    }

    /**
     * Sets a custom complete location override for this session.
     *
     * @param location custom complete location, or null to clear
     */
    default void setCustomCompleteLocation(@Nullable Location location) {}

    // ── Metadata Store ───────────────────────────────────────────────────────

    /**
     * Sets a metadata value on this session.
     *
     * @param key   metadata key
     * @param value metadata value (null removes key)
     */
    default void setMetadata(@NotNull String key, @Nullable Object value) {}

    /**
     * Retrieves a metadata value from this session.
     *
     * @param key metadata key
     * @return metadata value or null if not found
     */
    @Nullable
    default Object getMetadata(@NotNull String key) {
        return null;
    }

    /**
     * Retrieves a typed metadata value from this session.
     *
     * @param key  metadata key
     * @param type expected value class
     * @param <T>  value type
     * @return optional typed value
     */
    @NotNull
    default <T> Optional<T> getMetadata(@NotNull String key, @NotNull Class<T> type) {
        Object val = getMetadata(key);
        if (val != null && type.isInstance(val)) {
            return Optional.of(type.cast(val));
        }
        return Optional.empty();
    }

    /**
     * Checks if this session has a metadata value for the given key.
     *
     * @param key metadata key
     * @return true if key exists
     */
    default boolean hasMetadata(@NotNull String key) {
        return getMetadata(key) != null;
    }

    /**
     * Removes and returns a metadata value from this session.
     *
     * @param key metadata key
     * @return removed value or null
     */
    @Nullable
    default Object removeMetadata(@NotNull String key) {
        return null;
    }

    /**
     * Returns an unmodifiable map of all metadata stored on this session.
     *
     * @return unmodifiable metadata map
     */
    @NotNull
    @Unmodifiable
    default Map<String, Object> getMetadataMap() {
        return Collections.emptyMap();
    }

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
        /** Player stepped on a blacklisted block or a non-whitelisted block. */
        BLOCK_LIST,
        /** Triggered programmatically by a third-party plugin. */
        API
    }
}
