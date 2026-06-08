package me.usainsrht.basicparkour.core.storage;

import me.usainsrht.basicparkour.api.storage.LeaderboardEntry;
import me.usainsrht.basicparkour.api.storage.ParkourRepository;
import me.usainsrht.basicparkour.api.storage.PlayerRecord;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Volatile in-memory repository. All data is lost on server restart.
 * Intended for development, testing, and lightweight single-session use.
 *
 * <p>Thread-safe using {@link ConcurrentHashMap} and defensive copies.</p>
 */
public final class InMemoryRepository implements ParkourRepository {

    /** player UUID → course ID → best record */
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<String, PlayerRecord>> records
        = new ConcurrentHashMap<>();

    @Override public void init() { /* nothing to initialise */ }
    @Override public void close() { records.clear(); }

    @Override
    @NotNull
    public CompletableFuture<Boolean> saveRecord(@NotNull PlayerRecord record) {
        return CompletableFuture.supplyAsync(() -> {
            var playerMap = records.computeIfAbsent(record.playerUuid(), k -> new ConcurrentHashMap<>());
            PlayerRecord existing = playerMap.get(record.courseId());
            boolean isNewBest = existing == null || record.timeMs() < existing.timeMs();
            if (isNewBest) {
                playerMap.put(record.courseId(), record);
            }
            return isNewBest;
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerRecord>> getPersonalBest(@NotNull UUID playerUuid,
                                                                       @NotNull String courseId) {
        return CompletableFuture.supplyAsync(() ->
            Optional.ofNullable(records.getOrDefault(playerUuid, new ConcurrentHashMap<>()).get(courseId))
        );
    }

    @Override
    @NotNull
    public CompletableFuture<List<LeaderboardEntry>> getLeaderboard(@NotNull String courseId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<PlayerRecord> all = new ArrayList<>();
            records.forEach((uuid, courseMap) -> {
                PlayerRecord r = courseMap.get(courseId);
                if (r != null) all.add(r);
            });
            all.sort(Comparator.comparingLong(PlayerRecord::timeMs));
            List<LeaderboardEntry> board = new ArrayList<>();
            for (int i = 0; i < Math.min(limit, all.size()); i++) {
                PlayerRecord r = all.get(i);
                board.add(new LeaderboardEntry(i + 1, r.playerUuid(), r.playerName(), r.timeMs()));
            }
            return Collections.unmodifiableList(board);
        });
    }

    @Override
    @NotNull
    public CompletableFuture<List<PlayerRecord>> getAllRecords(@NotNull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            var map = records.get(playerUuid);
            if (map == null) return List.of();
            return List.copyOf(map.values());
        });
    }

    @Override
    @NotNull
    public CompletableFuture<Void> deleteRecords(@NotNull UUID playerUuid) {
        return CompletableFuture.runAsync(() -> records.remove(playerUuid));
    }
}
