package me.usainsrht.basicparkour.core.session;

import me.usainsrht.basicparkour.api.course.ParkourCourse;
import me.usainsrht.basicparkour.api.event.CoursePauseEvent;
import me.usainsrht.basicparkour.api.event.CourseResumeEvent;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.api.session.SessionState;
import me.usainsrht.basicparkour.api.session.TimerSnapshot;
import me.usainsrht.basicparkour.core.util.TimerFormatter;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable session implementation. Thread-safe via atomic fields.
 *
 * <p>Lifecycle: {@code ACTIVE ↔ PAUSED → COMPLETED | FAILED | INVALIDATED}.</p>
 *
 * <p>The {@link #fail(FailReason)} and {@link #complete()} methods delegate to
 * {@link SessionManager} which performs the actual teleport and record saving;
 * they only change state here. This keeps the session as a pure state container.</p>
 */
public final class ParkourSessionImpl implements ParkourSession {

    private final Player player;
    private final AtomicReference<Location> joinLocation;
    private final ParkourCourse course;
    private final AtomicLong startTime;
    private final AtomicLong timePenalty = new AtomicLong(0L);
    private final AtomicLong totalPausedTime = new AtomicLong(0L);
    private final AtomicLong pauseStartTime = new AtomicLong(0L);

    private final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.ACTIVE);
    private final AtomicInteger checkpointIndex = new AtomicInteger(-1);
    private final AtomicReference<Location> customFailLocation = new AtomicReference<>(null);
    private final AtomicReference<Location> customCompleteLocation = new AtomicReference<>(null);
    private final ConcurrentHashMap<String, Object> metadata = new ConcurrentHashMap<>();

    // Callback references injected by SessionManager to avoid circular dependency
    private final SessionManager sessionManager;

    public ParkourSessionImpl(@NotNull Player player,
                               @NotNull Location joinLocation,
                               @NotNull ParkourCourse course,
                               @NotNull SessionManager sessionManager) {
        this(player, joinLocation, course, sessionManager, System.currentTimeMillis());
    }

    public ParkourSessionImpl(@NotNull Player player,
                               @NotNull Location joinLocation,
                               @NotNull ParkourCourse course,
                               @NotNull SessionManager sessionManager,
                               long startTime) {
        this.player = player;
        this.joinLocation = new AtomicReference<>(joinLocation.clone());
        this.course = course;
        this.sessionManager = sessionManager;
        this.startTime = new AtomicLong(startTime);
    }

    @Override @NotNull public Player getPlayer() { return player; }
    @Override @NotNull public Location getJoinLocation() { return joinLocation.get().clone(); }
    @Override @NotNull public ParkourCourse getCourse() { return course; }
    @Override @NotNull public SessionState getState() { return state.get(); }
    @Override public long getStartTime() { return startTime.get(); }
    @Override public int getCurrentCheckpointIndex() { return checkpointIndex.get(); }

    @Override
    public long getPausedElapsedMillis() {
        long pStart = pauseStartTime.get();
        if (pStart <= 0L) return 0L;
        return Math.max(0, (pStart - startTime.get() - totalPausedTime.get()));
    }

    @Override
    @NotNull
    public TimerSnapshot getTimerSnapshot() {
        long elapsed = getElapsedMillis();
        return new TimerSnapshot(elapsed, checkpointIndex.get(), TimerFormatter.format(elapsed));
    }

    @Override
    public void setCurrentCheckpointIndex(int index) {
        checkpointIndex.set(index);
    }

    /**
     * Advances the checkpoint index by one. Returns the new index,
     * or {@code -1} if the CAS fails (state is no longer ACTIVE or PAUSED).
     */
    @Override
    public int advanceCheckpoint() {
        SessionState s = state.get();
        if (s != SessionState.ACTIVE && s != SessionState.PAUSED) return -1;
        return checkpointIndex.incrementAndGet();
    }

    @Override
    public void addTimePenalty(long penaltyMillis) {
        timePenalty.addAndGet(penaltyMillis);
    }

    @Override
    public long getTimePenalty() {
        return timePenalty.get();
    }

    @Override
    public void setStartTime(long newStartTime) {
        startTime.set(newStartTime);
    }

    @Override
    public long getPausedDurationMillis() {
        return totalPausedTime.get();
    }

    @Override
    public boolean pause() {
        if (state.get() != SessionState.ACTIVE) return false;
        if (Bukkit.getServer() != null) {
            CoursePauseEvent event = new CoursePauseEvent(player, this);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
        }

        if (state.compareAndSet(SessionState.ACTIVE, SessionState.PAUSED)) {
            pauseStartTime.set(System.currentTimeMillis());
            return true;
        }
        return false;
    }

    @Override
    public boolean resume() {
        if (state.get() != SessionState.PAUSED) return false;
        if (Bukkit.getServer() != null) {
            CourseResumeEvent event = new CourseResumeEvent(player, this);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) return false;
        }

        if (state.compareAndSet(SessionState.PAUSED, SessionState.ACTIVE)) {
            long pStart = pauseStartTime.getAndSet(0L);
            if (pStart > 0L) {
                totalPausedTime.addAndGet(System.currentTimeMillis() - pStart);
            }
            return true;
        }
        return false;
    }

    @Override
    public void restart() {
        sessionManager.restartSession(player);
    }

    @Override
    public void leave() {
        sessionManager.leaveSession(player);
    }

    @Override
    public void leave(boolean teleport) {
        sessionManager.leaveSession(player, teleport);
    }

    @Override
    public void teleportToCheckpoint() {
        sessionManager.teleportToCheckpoint(player);
    }

    @Override
    public void setJoinLocation(@NotNull Location location) {
        this.joinLocation.set(location.clone());
    }

    @Override
    @NotNull
    public Optional<Location> getCustomFailLocation() {
        Location loc = customFailLocation.get();
        return loc != null ? Optional.of(loc.clone()) : Optional.empty();
    }

    @Override
    public void setCustomFailLocation(@Nullable Location location) {
        this.customFailLocation.set(location != null ? location.clone() : null);
    }

    @Override
    @NotNull
    public Optional<Location> getCustomCompleteLocation() {
        Location loc = customCompleteLocation.get();
        return loc != null ? Optional.of(loc.clone()) : Optional.empty();
    }

    @Override
    public void setCustomCompleteLocation(@Nullable Location location) {
        this.customCompleteLocation.set(location != null ? location.clone() : null);
    }

    // ── Metadata Store ───────────────────────────────────────────────────────

    @Override
    public void setMetadata(@NotNull String key, @Nullable Object value) {
        if (value == null) {
            metadata.remove(key);
        } else {
            metadata.put(key, value);
        }
    }

    @Override
    @Nullable
    public Object getMetadata(@NotNull String key) {
        return metadata.get(key);
    }

    @Override
    public boolean hasMetadata(@NotNull String key) {
        return metadata.containsKey(key);
    }

    @Override
    @Nullable
    public Object removeMetadata(@NotNull String key) {
        return metadata.remove(key);
    }

    @Override
    @NotNull
    @Unmodifiable
    public Map<String, Object> getMetadataMap() {
        return Collections.unmodifiableMap(metadata);
    }

    /**
     * Attempts to transition the session to {@link SessionState#FAILED}.
     * Returns {@code true} if this call succeeded (only one caller wins).
     */
    public boolean tryFail() {
        return state.compareAndSet(SessionState.ACTIVE, SessionState.FAILED)
            || state.compareAndSet(SessionState.PAUSED, SessionState.FAILED);
    }

    /**
     * Attempts to transition the session to {@link SessionState#COMPLETED}.
     * Returns {@code true} if this call succeeded.
     */
    public boolean tryComplete() {
        return state.compareAndSet(SessionState.ACTIVE, SessionState.COMPLETED)
            || state.compareAndSet(SessionState.PAUSED, SessionState.COMPLETED);
    }

    /**
     * Marks the session as invalidated (course removed, server shutting down).
     */
    public boolean tryInvalidate() {
        return state.compareAndSet(SessionState.ACTIVE, SessionState.INVALIDATED)
            || state.compareAndSet(SessionState.PAUSED, SessionState.INVALIDATED);
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
