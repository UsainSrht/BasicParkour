package me.usainsrht.basicparkour.api.event;

import me.usainsrht.basicparkour.api.session.ParkourSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a paused parkour session is about to be resumed.
 *
 * <p>Cancelling this event keeps the session paused.</p>
 */
public class CourseResumeEvent extends PlayerParkourEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final ParkourSession session;

    public CourseResumeEvent(@NotNull Player player, @NotNull ParkourSession session) {
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
