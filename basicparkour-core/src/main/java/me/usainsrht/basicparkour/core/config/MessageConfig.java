package me.usainsrht.basicparkour.core.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

/**
 * Typed wrapper around {@code messages.yml} providing MiniMessage template strings
 * for all player-facing messages.
 *
 * <p>All strings use MiniMessage syntax and may contain the following placeholders
 * (where applicable):</p>
 * <ul>
 *   <li>{@code {time}} — formatted timer string (MM:SS.mmm)</li>
 *   <li>{@code {checkpoint}} — current checkpoint number (1-based)</li>
 *   <li>{@code {total}} — total checkpoints on the course</li>
 *   <li>{@code {course}} — course display name</li>
 *   <li>{@code {rank}} — leaderboard rank</li>
 * </ul>
 */
public final class MessageConfig {

    private String courseStart;
    private String checkpointReach;
    private String courseComplete;
    private String sessionFail;
    private String personalBest;
    private String timerDisplay;
    private String leaderboardHeader;
    private String leaderboardEntry;
    private String noCourse;
    private String alreadyInSession;

    public MessageConfig(@NotNull FileConfiguration cfg) {
        reload(cfg);
    }

    public void reload(@NotNull FileConfiguration cfg) {
        courseStart = get(cfg, "course-start",
            "<gradient:green:aqua>▶ <bold>Basic Parkour</bold> started on <course>! Good luck!</gradient>");
        checkpointReach = get(cfg, "checkpoint-reach",
            "<green>✔ Checkpoint <gray>{checkpoint}</gray> reached! <dark_gray>[<white>{time}</white>]</dark_gray>");
        courseComplete = get(cfg, "course-complete",
            "<gold><bold>🏁 Course complete!</bold></gold> <yellow>Time: <white>{time}</white></yellow>");
        sessionFail = get(cfg, "session-fail",
            "<red>✗ Failed! <gray>Respawning at last checkpoint...</gray></red>");
        personalBest = get(cfg, "personal-best",
            "<aqua>🏆 <bold>New personal best!</bold> <white>{time}</white></aqua>");
        timerDisplay = get(cfg, "timer-display",
            "<gray>⏱ <white>{time}</white>  <dark_gray>CP: <white>{checkpoint}</white>/<white>{total}</white></dark_gray>");
        leaderboardHeader = get(cfg, "leaderboard-header",
            "<gold>═══ <bold>Basic Parkour Leaderboard — {course}</bold> ═══</gold>");
        leaderboardEntry = get(cfg, "leaderboard-entry",
            "<gray>{rank}. <white>{player}</white> — <yellow>{time}</yellow></gray>");
        noCourse = get(cfg, "no-course", "<red>No course found with that ID.</red>");
        alreadyInSession = get(cfg, "already-in-session",
            "<yellow>You are already running a course! Use <white>/basicparkour leave</white> to exit.</yellow>");
    }

    private static String get(@NotNull FileConfiguration cfg, @NotNull String key,
                               @NotNull String defaultValue) {
        return cfg.getString("messages." + key, defaultValue);
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public @NotNull String getCourseStart() { return courseStart; }
    public @NotNull String getCheckpointReach() { return checkpointReach; }
    public @NotNull String getCourseComplete() { return courseComplete; }
    public @NotNull String getSessionFail() { return sessionFail; }
    public @NotNull String getPersonalBest() { return personalBest; }
    public @NotNull String getTimerDisplay() { return timerDisplay; }
    public @NotNull String getLeaderboardHeader() { return leaderboardHeader; }
    public @NotNull String getLeaderboardEntry() { return leaderboardEntry; }
    public @NotNull String getNoCourse() { return noCourse; }
    public @NotNull String getAlreadyInSession() { return alreadyInSession; }
}
