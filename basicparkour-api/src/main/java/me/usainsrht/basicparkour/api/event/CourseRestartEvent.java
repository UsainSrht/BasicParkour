package me.usainsrht.basicparkour.api.event;

import me.usainsrht.basicparkour.api.session.ParkourSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player restarts their active parkour session.
 *
 * <p>Cancelling this event aborts the restart; the session continues uninterrupted.</p>
 */
public class CourseRestartEvent extends PlayerParkourEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final ParkourSession session;
    private final RestartReason reason;
    private @Nullable Location startLocation;

    public CourseRestartEvent(@NotNull Player player,
                              @NotNull ParkourSession session,
                              @NotNull RestartReason reason,
                              @Nullable Location startLocation) {
        super(player);
        this.session = session;
        this.reason = reason;
        this.startLocation = startLocation;
    }

    @NotNull
    public ParkourSession getSession() {
        return session;
    }

    @NotNull
    public RestartReason getReason() {
        return reason;
    }

    /**
     * Returns the start location the player will be teleported to upon restarting.
     *
     * @return start location or null if no teleportation
     */
    @Nullable
    public Location getStartLocation() {
        return startLocation;
    }

    /**
     * Modifies the start location where the player will be teleported on restart.
     *
     * @param startLocation the new start location
     */
    public void setStartLocation(@Nullable Location startLocation) {
        this.startLocation = startLocation;
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

    /**
     * The trigger that initiated restarting the parkour session.
     */
    public enum RestartReason {
        /** Player typed /bp restart */
        COMMAND,
        /** Player clicked the Restart hotbar utility item */
        HOTBAR,
        /** Triggered programmatically via API */
        API
    }
}
