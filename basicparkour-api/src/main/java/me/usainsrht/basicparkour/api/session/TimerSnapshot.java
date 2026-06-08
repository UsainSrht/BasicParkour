package me.usainsrht.basicparkour.api.session;

/**
 * An immutable snapshot of a session's timer at a specific moment in time.
 *
 * <p>Used to safely pass timer state across threads without holding a reference
 * to the full {@link ParkourSession}.</p>
 *
 * @param elapsedMs        elapsed milliseconds since session start
 * @param checkpointIndex  the current checkpoint index at the time of snapshot
 * @param formatted        human-readable formatted time string (e.g. "02:34.567")
 */
public record TimerSnapshot(long elapsedMs, int checkpointIndex, String formatted) {

    /**
     * Returns the elapsed time in whole seconds.
     *
     * @return elapsed seconds
     */
    public long elapsedSeconds() {
        return elapsedMs / 1000L;
    }

    /**
     * Returns the elapsed time in whole minutes.
     *
     * @return elapsed minutes
     */
    public long elapsedMinutes() {
        return elapsedMs / 60_000L;
    }
}
