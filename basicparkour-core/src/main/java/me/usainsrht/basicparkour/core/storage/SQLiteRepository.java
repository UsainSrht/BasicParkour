package me.usainsrht.basicparkour.core.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.usainsrht.basicparkour.api.storage.LeaderboardEntry;
import me.usainsrht.basicparkour.api.storage.ParkourRepository;
import me.usainsrht.basicparkour.api.storage.PlayerRecord;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * SQLite-backed repository using HikariCP for connection pooling.
 *
 * <h2>Schema</h2>
 * <pre>{@code
 * CREATE TABLE basicparkour_records (
 *   uuid        TEXT    NOT NULL,
 *   player_name TEXT    NOT NULL,
 *   course_id   TEXT    NOT NULL,
 *   time_ms     INTEGER NOT NULL,
 *   recorded_at INTEGER NOT NULL,
 *   PRIMARY KEY (uuid, course_id)       -- keeps only the best time per player per course
 * );
 * CREATE INDEX idx_course_time ON basicparkour_records (course_id, time_ms);
 * }</pre>
 *
 * <p>UPSERT semantics: a new record only replaces the existing one if {@code time_ms} is lower.</p>
 */
public final class SQLiteRepository implements ParkourRepository {

    private final File dataFolder;
    private HikariDataSource dataSource;

    /** Dedicated I/O thread pool so database operations never block Folia regions. */
    private final Executor ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "BasicParkour-SQLite-IO");
        t.setDaemon(true);
        return t;
    });

    public SQLiteRepository(@NotNull File dataFolder) {
        this.dataFolder = dataFolder;
    }

    @Override
    public void init() throws Exception {
        File dbFile = new File(dataFolder, "basicparkour.db");
        HikariConfig config = new HikariConfig();
        config.setDriverClassName("org.sqlite.JDBC");
        config.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
        config.setMaximumPoolSize(1); // SQLite is single-writer
        config.setConnectionTestQuery("SELECT 1");
        config.setPoolName("BasicParkour-SQLite");
        dataSource = new HikariDataSource(config);
        createSchema();
    }

    private void createSchema() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS basicparkour_records (
                    uuid        TEXT    NOT NULL,
                    player_name TEXT    NOT NULL,
                    course_id   TEXT    NOT NULL,
                    time_ms     INTEGER NOT NULL,
                    recorded_at INTEGER NOT NULL,
                    PRIMARY KEY (uuid, course_id)
                )
                """);
            stmt.executeUpdate("""
                CREATE INDEX IF NOT EXISTS idx_course_time
                    ON basicparkour_records (course_id, time_ms)
                """);
        }
    }

    @Override
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) dataSource.close();
    }

    // -----------------------------------------------------------------------
    // ParkourRepository
    // -----------------------------------------------------------------------

    @Override
    @NotNull
    public CompletableFuture<Boolean> saveRecord(@NotNull PlayerRecord record) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                // Check existing best
                long existingBest = Long.MAX_VALUE;
                try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT time_ms FROM basicparkour_records WHERE uuid = ? AND course_id = ?")) {
                    sel.setString(1, record.playerUuid().toString());
                    sel.setString(2, record.courseId());
                    ResultSet rs = sel.executeQuery();
                    if (rs.next()) existingBest = rs.getLong(1);
                }

                boolean isNewBest = record.timeMs() < existingBest;
                if (isNewBest) {
                    try (PreparedStatement upsert = conn.prepareStatement("""
                        INSERT INTO basicparkour_records (uuid, player_name, course_id, time_ms, recorded_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON CONFLICT(uuid, course_id) DO UPDATE SET
                            player_name = excluded.player_name,
                            time_ms = excluded.time_ms,
                            recorded_at = excluded.recorded_at
                        WHERE excluded.time_ms < basicparkour_records.time_ms
                        """)) {
                        upsert.setString(1, record.playerUuid().toString());
                        upsert.setString(2, record.playerName());
                        upsert.setString(3, record.courseId());
                        upsert.setLong(4, record.timeMs());
                        upsert.setLong(5, record.recordedAt());
                        upsert.executeUpdate();
                    }
                }
                return isNewBest;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to save record", e);
            }
        }, ioExecutor);
    }

    @Override
    @NotNull
    public CompletableFuture<Optional<PlayerRecord>> getPersonalBest(@NotNull UUID playerUuid,
                                                                       @NotNull String courseId) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT uuid, player_name, course_id, time_ms, recorded_at " +
                     "FROM basicparkour_records WHERE uuid = ? AND course_id = ?")) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, courseId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) return Optional.of(mapRecord(rs));
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get personal best", e);
            }
        }, ioExecutor);
    }

    @Override
    @NotNull
    public CompletableFuture<List<LeaderboardEntry>> getLeaderboard(@NotNull String courseId, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("""
                     SELECT uuid, player_name, time_ms
                     FROM basicparkour_records
                     WHERE course_id = ?
                     ORDER BY time_ms ASC
                     LIMIT ?
                     """)) {
                stmt.setString(1, courseId);
                stmt.setInt(2, limit);
                ResultSet rs = stmt.executeQuery();
                List<LeaderboardEntry> board = new ArrayList<>();
                int rank = 1;
                while (rs.next()) {
                    board.add(new LeaderboardEntry(
                        rank++,
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("player_name"),
                        rs.getLong("time_ms")
                    ));
                }
                return Collections.unmodifiableList(board);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get leaderboard", e);
            }
        }, ioExecutor);
    }

    @Override
    @NotNull
    public CompletableFuture<List<PlayerRecord>> getAllRecords(@NotNull UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT uuid, player_name, course_id, time_ms, recorded_at " +
                     "FROM basicparkour_records WHERE uuid = ?")) {
                stmt.setString(1, playerUuid.toString());
                ResultSet rs = stmt.executeQuery();
                List<PlayerRecord> list = new ArrayList<>();
                while (rs.next()) list.add(mapRecord(rs));
                return Collections.unmodifiableList(list);
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get all records", e);
            }
        }, ioExecutor);
    }

    @Override
    @NotNull
    public CompletableFuture<Void> deleteRecords(@NotNull UUID playerUuid) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "DELETE FROM basicparkour_records WHERE uuid = ?")) {
                stmt.setString(1, playerUuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete records", e);
            }
        }, ioExecutor);
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    @NotNull
    private static PlayerRecord mapRecord(@NotNull ResultSet rs) throws SQLException {
        return new PlayerRecord(
            UUID.fromString(rs.getString("uuid")),
            rs.getString("player_name"),
            rs.getString("course_id"),
            rs.getLong("time_ms"),
            rs.getLong("recorded_at")
        );
    }
}
