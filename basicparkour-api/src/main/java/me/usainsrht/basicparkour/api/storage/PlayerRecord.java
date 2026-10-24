package me.usainsrht.basicparkour.api.storage;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * An immutable record of a player's completion time (and block count for endless runs)
 * for a specific course.
 *
 * @param playerUuid  the player's UUID
 * @param playerName  the player's last known username
 * @param courseId    the course identifier
 * @param timeMs      the completion time in milliseconds
 * @param recordedAt  epoch millisecond timestamp when this record was stored
 * @param blocks      number of blocks cleared (used for endless/generated runs; 0 for standard)
 */
public record PlayerRecord(
    @NotNull UUID playerUuid,
    @NotNull String playerName,
    @NotNull String courseId,
    long timeMs,
    long recordedAt,
    int blocks
) {
    public PlayerRecord(@NotNull UUID playerUuid, @NotNull String playerName,
                        @NotNull String courseId, long timeMs, long recordedAt) {
        this(playerUuid, playerName, courseId, timeMs, recordedAt, 0);
    }

    /**
     * Factory method for creating a new standard record at the current time.
     */
    public static PlayerRecord of(@NotNull UUID uuid, @NotNull String name,
                                   @NotNull String courseId, long timeMs) {
        return new PlayerRecord(uuid, name, courseId, timeMs, System.currentTimeMillis(), 0);
    }

    /**
     * Factory method for creating a new record with block count at the current time.
     */
    public static PlayerRecord of(@NotNull UUID uuid, @NotNull String name,
                                   @NotNull String courseId, long timeMs, int blocks) {
        return new PlayerRecord(uuid, name, courseId, timeMs, System.currentTimeMillis(), blocks);
    }
}
