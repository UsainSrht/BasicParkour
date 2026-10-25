package me.usainsrht.basicparkour.api.event;

import me.usainsrht.basicparkour.api.session.ParkourSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Fired when a player reaches the end of a course and is about to complete their session,
 * BEFORE records are saved, rewards are executed, or the session is terminated.
 *
 * <p>Cancelling this event prevents the completion from occurring; the player remains in their session.</p>
 * <p>Listeners can modify the elapsed time recorded (e.g. adding time bonuses or penalties),
 * inspect or alter the reward commands, or prevent personal best saving.</p>
 */
public class CoursePreCompleteEvent extends PlayerParkourEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final ParkourSession session;
    private long elapsedMs;
    private final List<String> rewardCommands;
    private boolean recordBestTime = true;

    public CoursePreCompleteEvent(@NotNull Player player,
                                   @NotNull ParkourSession session,
                                   long elapsedMs,
                                   @NotNull List<String> rewardCommands) {
        super(player);
        this.session = session;
        this.elapsedMs = elapsedMs;
        this.rewardCommands = new ArrayList<>(rewardCommands);
    }

    @NotNull
    public ParkourSession getSession() {
        return session;
    }

    /**
     * Returns the elapsed time in milliseconds about to be recorded.
     *
     * @return elapsed time in ms
     */
    public long getElapsedMs() {
        return elapsedMs;
    }

    /**
     * Overrides the completion time in milliseconds that will be recorded.
     *
     * @param elapsedMs the new completion time
     */
    public void setElapsedMs(long elapsedMs) {
        this.elapsedMs = Math.max(0, elapsedMs);
    }

    /**
     * Returns the mutable list of console commands to be executed as rewards upon completion.
     * Listeners may add, remove, or clear commands.
     *
     * @return mutable reward command list
     */
    @NotNull
    public List<String> getRewardCommands() {
        return rewardCommands;
    }

    /**
     * Whether this completion should be saved to the database/leaderboard if it's a personal best.
     *
     * @return true if saving is enabled
     */
    public boolean isRecordBestTime() {
        return recordBestTime;
    }

    /**
     * Sets whether this completion should be eligible for database record saving.
     *
     * @param recordBestTime true to allow saving, false to suppress
     */
    public void setRecordBestTime(boolean recordBestTime) {
        this.recordBestTime = recordBestTime;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
