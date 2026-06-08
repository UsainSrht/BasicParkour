package me.usainsrht.basicparkour.core.session;

import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.api.session.SessionState;
import me.usainsrht.basicparkour.api.session.TimerSnapshot;
import me.usainsrht.basicparkour.core.util.TimerFormatter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable session implementation. Thread-safe via atomic fields.
 *
 * <p>Lifecycle: {@code ACTIVE → COMPLETED | FAILED | INVALIDATED}.</p>
 *
 * <p>The {@link #fail(FailReason)} and {@link #complete()} methods delegate to
 * {@link SessionManager} which performs the actual teleport and record saving;
 * they only change state here. This keeps the session as a pure state container.</p>
 */
public final class ParkourSessionImpl implements ParkourSession {

    private final Player player;
    private final ParkourCourse course;
    private final long startTime;

    private final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.ACTIVE);
    private final AtomicInteger checkpointIndex = new AtomicInteger(-1);

    // Callback references injected by SessionManager to avoid circular dependency
    private final SessionManager sessionManager;

    public ParkourSessionImpl(@NotNull Player player,
                               @NotNull ParkourCourse course,
                               @NotNull SessionManager sessionManager) {
        this.player = player;
        this.course = course;
        this.sessionManager = sessionManager;
        this.startTime = System.currentTimeMillis();
    }

    @Override @NotNull public Player getPlayer() { return player; }
    @Override @NotNull public ParkourCourse getCourse() { return course; }
    @Override @NotNull public SessionState getState() { return state.get(); }
    @Override public long getStartTime() { return startTime; }
    @Override public int getCurrentCheckpointIndex() { return checkpointIndex.get(); }

    @Override
    @NotNull
    public TimerSnapshot getTimerSnapshot() {
        long elapsed = getElapsedMillis();
        return new TimerSnapshot(elapsed, checkpointIndex.get(), TimerFormatter.format(elapsed));
    }

    /**
     * Advances the checkpoint index by one. Returns the new index,
     * or {@code -1} if the CAS fails (state is no longer ACTIVE).
     */
    public int advanceCheckpoint() {
        if (state.get() != SessionState.ACTIVE) return -1;
        return checkpointIndex.incrementAndGet();
    }

    /**
     * Attempts to transition the session to {@link SessionState#FAILED}.
     * Returns {@code true} if this call succeeded (only one caller wins).
     */
    public boolean tryFail() {
        return state.compareAndSet(SessionState.ACTIVE, SessionState.FAILED);
    }

    /**
     * Attempts to transition the session to {@link SessionState#COMPLETED}.
     * Returns {@code true} if this call succeeded.
     */
    public boolean tryComplete() {
        return state.compareAndSet(SessionState.ACTIVE, SessionState.COMPLETED);
    }

    /**
     * Marks the session as invalidated (course removed, server shutting down).
     */
    public boolean tryInvalidate() {
        return state.compareAndSet(SessionState.ACTIVE, SessionState.INVALIDATED);
    }

    // -------------------------------------------------------------------------
    // ParkourSession API delegates
    // -------------------------------------------------------------------------

    @Override
    public void fail(@NotNull FailReason reason) {
        sessionManager.failSession(player, reason);
    }

    @Override
    public void complete() {
        sessionManager.completeSession(player);
    }
}
