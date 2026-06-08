package me.usainsrht.basicparkour.api.storage;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Asynchronous data repository interface for persisting parkour records and leaderboards.
 *
 * <p>All methods return {@link CompletableFuture} and are safe to call from any thread.
 * The underlying I/O operations are performed off the main thread. Do not block on the
 * returned futures from the server main thread.</p>
 *
 * <p>Implementations are provided for:</p>
 * <ul>
 *   <li>{@code InMemoryRepository} — development/testing, no persistence</li>
 *   <li>{@code SQLiteRepository} — single-server deployments</li>
 *   <li>{@code MySQLRepository} — multi-server/production deployments (also supports PostgreSQL)</li>
 * </ul>
 */
public interface ParkourRepository {

    /**
     * Initialises the repository (creates tables, opens connection pools, etc.).
     * Called once during plugin startup. Blocks until initialisation is complete.
     *
     * @throws Exception if initialisation fails
     */
    void init() throws Exception;

    /**
     * Closes the repository and releases all resources.
     * Called once during plugin shutdown.
     */
    void close();

    /**
     * Saves a player's completion record.
     * If a better time already exists for this player on this course, the new time
     * is only persisted if it is faster (implementation-dependent; SQL variant uses UPSERT).
     *
     * @param record the record to save
     * @return a future that completes with {@code true} if this was a personal best
     */
    @NotNull
    CompletableFuture<Boolean> saveRecord(@NotNull PlayerRecord record);

    /**
     * Retrieves the personal best record for a player on a specific course.
     *
     * @param playerUuid the player's UUID
     * @param courseId   the course ID
     * @return a future containing the personal best, or empty if no record exists
     */
    @NotNull
    CompletableFuture<Optional<PlayerRecord>> getPersonalBest(@NotNull UUID playerUuid,
                                                               @NotNull String courseId);

    /**
     * Retrieves the top-N leaderboard entries for a specific course, ordered by
     * ascending completion time.
     *
     * @param courseId the course ID
     * @param limit    maximum number of entries to return
     * @return a future containing the ranked leaderboard list
     */
    @NotNull
    CompletableFuture<List<LeaderboardEntry>> getLeaderboard(@NotNull String courseId, int limit);

    /**
     * Retrieves all records for a given player across all courses.
     *
     * @param playerUuid the player's UUID
     * @return a future containing all records for the player
     */
    @NotNull
    CompletableFuture<List<PlayerRecord>> getAllRecords(@NotNull UUID playerUuid);

    /**
     * Deletes all records for a player (admin reset functionality).
     *
     * @param playerUuid the player's UUID
     * @return a future that completes when deletion is done
     */
    @NotNull
    CompletableFuture<Void> deleteRecords(@NotNull UUID playerUuid);
}
