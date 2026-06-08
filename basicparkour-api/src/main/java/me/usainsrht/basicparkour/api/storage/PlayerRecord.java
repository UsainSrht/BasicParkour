package me.usainsrht.basicparkour.api.storage;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * An immutable record of a player's completion time for a specific course.
 *
 * @param playerUuid  the player's UUID
 * @param playerName  the player's last known username
 * @param courseId    the course identifier
 * @param timeMs      the completion time in milliseconds
 * @param recordedAt  epoch millisecond timestamp when this record was stored
 */
public record PlayerRecord(
    @NotNull UUID playerUuid,
    @NotNull String playerName,
    @NotNull String courseId,
    long timeMs,
    long recordedAt
) {
    /**
     * Factory method for creating a new record at the current time.
     */
    public static PlayerRecord of(@NotNull UUID uuid, @NotNull String name,
                                   @NotNull String courseId, long timeMs) {
        return new PlayerRecord(uuid, name, courseId, timeMs, System.currentTimeMillis());
    }
}
