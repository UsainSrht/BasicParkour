package me.usainsrht.basicparkour.core.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.usainsrht.basicparkour.api.storage.LeaderboardEntry;
import me.usainsrht.basicparkour.api.storage.ParkourRepository;
import me.usainsrht.basicparkour.api.storage.PlayerRecord;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.NotNull;

import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * MySQL/PostgreSQL backed repository using HikariCP.
 *
 * <p>Supports both MySQL 8+ and PostgreSQL 13+ via configurable JDBC URLs.
 * Uses an UPSERT-style INSERT that only overwrites when the new time is faster.</p>
 *
 * <h2>Schema</h2>
 * <pre>{@code
 * CREATE TABLE IF NOT EXISTS basicparkour_records (
 *   uuid        VARCHAR(36)  NOT NULL,
 *   player_name VARCHAR(16)  NOT NULL,
 *   course_id   VARCHAR(64)  NOT NULL,
 *   time_ms     BIGINT       NOT NULL,
 *   recorded_at BIGINT       NOT NULL,
 *   PRIMARY KEY (uuid, course_id)
 * );
 * }</pre>
 */
public final class MySQLRepository implements ParkourRepository {

    private final ConfigurationSection dbConfig;
    private HikariDataSource dataSource;

    private final Executor ioExecutor = Executors.newFixedThreadPool(
        Math.max(2, Runtime.getRuntime().availableProcessors()),
        r -> { Thread t = new Thread(r, "BasicParkour-DB-IO"); t.setDaemon(true); return t; }
    );

    public MySQLRepository(@NotNull ConfigurationSection dbConfig) {
        this.dbConfig = dbConfig;
    }

    @Override
    public void init() throws Exception {
        String host = dbConfig.getString("host", "localhost");
        int port = dbConfig.getInt("port", 3306);
        String database = dbConfig.getString("database", "basicparkour");
        String username = dbConfig.getString("username", "root");
        String password = dbConfig.getString("password", "");
        int poolSize = dbConfig.getInt("pool-size", 10);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
            .formatted(host, port, database));
        config.setUsername(username);
        config.setPassword(password);
        config.setMaximumPoolSize(poolSize);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30_000);
        config.setIdleTimeout(600_000);
        config.setMaxLifetime(1_800_000);
        config.setPoolName("BasicParkour-MySQL");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        dataSource = new HikariDataSource(config);
        createSchema();
    }

    private void createSchema() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS basicparkour_records (
                    uuid        VARCHAR(36)  NOT NULL,
                    player_name VARCHAR(16)  NOT NULL,
                    course_id   VARCHAR(64)  NOT NULL,
                    time_ms     BIGINT       NOT NULL,
                    recorded_at BIGINT       NOT NULL,
                    PRIMARY KEY (uuid, course_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
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
    // ParkourRepository implementation
    // -----------------------------------------------------------------------

    @Override
    @NotNull
    public CompletableFuture<Boolean> saveRecord(@NotNull PlayerRecord record) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                // Read current best
                long existingBest = Long.MAX_VALUE;
                try (PreparedStatement sel = conn.prepareStatement(
                    "SELECT time_ms FROM basicparkour_records WHERE uuid = ? AND course_id = ?")) {
                    sel.setString(1, record.playerUuid().toString());
                    sel.setString(2, record.courseId());
                    ResultSet rs = sel.executeQuery();
                    if (rs.next()) existingBest = rs.getLong(1);
                }
                boolean newBest = record.timeMs() < existingBest;
                if (newBest) {
                    try (PreparedStatement upsert = conn.prepareStatement("""
                        INSERT INTO basicparkour_records (uuid, player_name, course_id, time_ms, recorded_at)
                        VALUES (?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE
                            player_name = VALUES(player_name),
                            time_ms = IF(VALUES(time_ms) < time_ms, VALUES(time_ms), time_ms),
                            recorded_at = IF(VALUES(time_ms) < time_ms, VALUES(recorded_at), recorded_at)
                        """)) {
                        upsert.setString(1, record.playerUuid().toString());
                        upsert.setString(2, record.playerName());
                        upsert.setString(3, record.courseId());
                        upsert.setLong(4, record.timeMs());
                        upsert.setLong(5, record.recordedAt());
                        upsert.executeUpdate();
                    }
                }
                return newBest;
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
                     "SELECT * FROM basicparkour_records WHERE uuid = ? AND course_id = ?")) {
                stmt.setString(1, playerUuid.toString());
                stmt.setString(2, courseId);
                ResultSet rs = stmt.executeQuery();
                return rs.next() ? Optional.of(mapRecord(rs)) : Optional.<PlayerRecord>empty();
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
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT uuid, player_name, time_ms FROM basicparkour_records " +
                     "WHERE course_id = ? ORDER BY time_ms ASC LIMIT ?")) {
                stmt.setString(1, courseId);
                stmt.setInt(2, limit);
                ResultSet rs = stmt.executeQuery();
                List<LeaderboardEntry> board = new ArrayList<>();
                int rank = 1;
                while (rs.next()) {
                    board.add(new LeaderboardEntry(rank++,
                        UUID.fromString(rs.getString("uuid")),
                        rs.getString("player_name"),
                        rs.getLong("time_ms")));
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
                     "SELECT * FROM basicparkour_records WHERE uuid = ?")) {
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
