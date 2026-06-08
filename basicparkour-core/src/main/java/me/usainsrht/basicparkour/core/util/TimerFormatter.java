package me.usainsrht.basicparkour.core.util;

import org.jetbrains.annotations.NotNull;

/**
 * Utility class for formatting elapsed millisecond durations into human-readable strings.
 */
public final class TimerFormatter {

    private TimerFormatter() {}

    /**
     * Formats an elapsed time in milliseconds as {@code MM:SS.mmm}.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code 0} → {@code "00:00.000"}</li>
     *   <li>{@code 5432} → {@code "00:05.432"}</li>
     *   <li>{@code 125789} → {@code "02:05.789"}</li>
     *   <li>{@code 3661000} → {@code "61:01.000"} (no hour cap)</li>
     * </ul>
     * </p>
     *
     * @param elapsedMs elapsed milliseconds (non-negative)
     * @return formatted string
     */
    public static String format(long elapsedMs) {
        if (elapsedMs < 0) elapsedMs = 0;
        long totalSeconds = elapsedMs / 1000L;
        long millis = elapsedMs % 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d.%03d", minutes, seconds, millis);
    }

    /**
     * Formats an elapsed time as a compact string for leaderboard display, e.g. {@code "2:05.789"}.
     * Omits leading zero on minutes when minutes {@literal <} 10.
     *
     * @param elapsedMs elapsed milliseconds
     * @return compact formatted string
     */
    public static String formatCompact(long elapsedMs) {
        if (elapsedMs < 0) elapsedMs = 0;
        long totalSeconds = elapsedMs / 1000L;
        long millis = elapsedMs % 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        if (minutes == 0) {
            return String.format("0:%02d.%03d", seconds, millis);
        }
        return String.format("%d:%02d.%03d", minutes, seconds, millis);
    }

    /**
     * Parses a formatted time string back into milliseconds.
     * Accepts the {@code MM:SS.mmm} format produced by {@link #format(long)}.
     *
     * @param formatted the time string
     * @return parsed milliseconds
     * @throws IllegalArgumentException if the format is invalid
     */
    public static long parse(@NotNull String formatted) {
        // Expected pattern: digits:digits.digits
        String[] colonSplit = formatted.split(":");
        if (colonSplit.length != 2) throw new IllegalArgumentException("Invalid time format: " + formatted);
        String[] dotSplit = colonSplit[1].split("\\.");
        if (dotSplit.length != 2) throw new IllegalArgumentException("Invalid time format: " + formatted);
        try {
            long minutes = Long.parseLong(colonSplit[0]);
            long seconds = Long.parseLong(dotSplit[0]);
            long millis  = Long.parseLong(dotSplit[1]);
            return (minutes * 60_000L) + (seconds * 1_000L) + millis;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid time format: " + formatted, e);
        }
    }
}
