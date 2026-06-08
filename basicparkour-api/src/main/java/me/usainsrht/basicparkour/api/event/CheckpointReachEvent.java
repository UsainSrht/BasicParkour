package me.usainsrht.basicparkour.api.event;

import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.api.session.TimerSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player triggers a checkpoint in their current session.
 *
 * <p>Cancelling this event prevents the checkpoint from being registered.
 * The player's respawn location will NOT be updated and the checkpoint
 * will be eligible to fire again on the next movement.</p>
 */
public class CheckpointReachEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final Player player;
    private final ParkourSession session;
    private final Checkpoint checkpoint;
    private final TimerSnapshot timerAtReach;

    public CheckpointReachEvent(@NotNull Player player,
                                 @NotNull ParkourSession session,
                                 @NotNull Checkpoint checkpoint,
                                 @NotNull TimerSnapshot timerAtReach) {
        super(false);
        this.player = player;
        this.session = session;
        this.checkpoint = checkpoint;
        this.timerAtReach = timerAtReach;
    }

    @NotNull public Player getPlayer() { return player; }
    @NotNull public ParkourSession getSession() { return session; }
    @NotNull public Checkpoint getCheckpoint() { return checkpoint; }

    /** Timer snapshot taken at the exact moment the checkpoint was reached. */
    @NotNull public TimerSnapshot getTimerAtReach() { return timerAtReach; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override @NotNull public HandlerList getHandlers() { return HANDLERS; }
    @NotNull public static HandlerList getHandlerList() { return HANDLERS; }
}
