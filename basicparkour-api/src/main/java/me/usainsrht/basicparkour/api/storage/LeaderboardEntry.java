package me.usainsrht.basicparkour.api.storage;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * An entry in a course leaderboard, combining a player record with its rank.
 *
 * @param rank        1-based position on the leaderboard (1 = fastest)
 * @param playerUuid  the player's UUID
 * @param playerName  the player's display name
 * @param timeMs      their best completion time in milliseconds
 */
public record LeaderboardEntry(
    int rank,
    @NotNull UUID playerUuid,
    @NotNull String playerName,
    long timeMs
) {}
