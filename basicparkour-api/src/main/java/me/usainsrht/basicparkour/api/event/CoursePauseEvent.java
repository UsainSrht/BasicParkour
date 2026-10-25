package me.usainsrht.basicparkour.api.event;

import me.usainsrht.basicparkour.api.session.ParkourSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when an active parkour session is about to be paused.
 *
 * <p>Cancelling this event prevents the session from being paused.</p>
 */
public class CoursePauseEvent extends PlayerParkourEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final ParkourSession session;

    public CoursePauseEvent(@NotNull Player player, @NotNull ParkourSession session) {
        super(player);
        this.session = session;
    }

    @NotNull
    public ParkourSession getSession() {
        return session;
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
