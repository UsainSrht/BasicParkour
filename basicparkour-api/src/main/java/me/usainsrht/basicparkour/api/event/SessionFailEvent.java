package me.usainsrht.basicparkour.api.event;

import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.api.session.ParkourSession.FailReason;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player's session is failed — either by a kill block, void,
 * time limit, disconnection, or a programmatic API call.
 *
 * <p>This event is not cancellable. By the time it is fired, the respawn
 * teleport has already been scheduled. Listeners may use this event to
 * play sounds, show effects, or update external statistics.</p>
 */
public class SessionFailEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ParkourSession session;
    private final FailReason reason;

    public SessionFailEvent(@NotNull Player player,
                             @NotNull ParkourSession session,
                             @NotNull FailReason reason) {
        super(false);
        this.player = player;
        this.session = session;
        this.reason = reason;
    }

    @NotNull public Player getPlayer() { return player; }
    @NotNull public ParkourSession getSession() { return session; }

    /** The reason the session failed. */
    @NotNull public FailReason getReason() { return reason; }

    @Override @NotNull public HandlerList getHandlers() { return HANDLERS; }
    @NotNull public static HandlerList getHandlerList() { return HANDLERS; }
}
