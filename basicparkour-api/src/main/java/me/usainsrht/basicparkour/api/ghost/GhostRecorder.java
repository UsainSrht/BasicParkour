package me.usainsrht.basicparkour.api.ghost;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * API for recording and replaying ghost runs (recorded player paths through a course).
 *
 * <p>The ghost recorder captures player position snapshots at a configurable interval
 * (default 50ms / 20 frames per second equivalent). Recordings are stored asynchronously
 * and can be replayed as a visual entity (ArmorStand NPC) following the recorded path.</p>
 *
 * <p>This component is optional. If not configured, the ghost system is a no-op.</p>
 */
public interface GhostRecorder {

    /**
     * Starts recording position frames for the given player.
     * Calling this on a player that is already being recorded has no effect.
     *
     * @param player the player to record
     */
    void startRecording(@NotNull Player player);

    /**
     * Stops recording and discards the in-progress recording without saving.
     *
     * @param player the player
     */
    void discardRecording(@NotNull Player player);

    /**
     * Stops recording and asynchronously saves the completed ghost run for the given
     * course. The future completes with a ghost run identifier upon success.
     *
     * @param player   the player
     * @param courseId the course this recording is associated with
     * @param timeMs   the completion time in milliseconds (used to decide if this ghost
     *                 replaces the personal-best ghost)
     * @return a future completing with the saved ghost's unique ID
     */
    @NotNull
    CompletableFuture<UUID> saveRecording(@NotNull Player player,
                                           @NotNull String courseId,
                                           long timeMs);

    /**
     * Returns {@code true} if the given player is currently being recorded.
     *
     * @param player the player
     * @return whether recording is active
     */
    boolean isRecording(@NotNull Player player);
}
