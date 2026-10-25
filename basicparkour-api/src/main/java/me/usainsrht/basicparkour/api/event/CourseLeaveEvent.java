package me.usainsrht.basicparkour.api.event;

import me.usainsrht.basicparkour.api.session.ParkourSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player leaves or is removed from a parkour session.
 *
 * <p>If triggered by a player action (such as {@link LeaveReason#COMMAND},
 * {@link LeaveReason#HOTBAR}, or {@link LeaveReason#API}), this event is cancellable.
 * Cancelling prevents the player from leaving their run.</p>
 */
public class CourseLeaveEvent extends PlayerParkourEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final ParkourSession session;
    private final LeaveReason reason;
    private boolean teleport;
    private @Nullable Location exitLocation;

    public CourseLeaveEvent(@NotNull Player player,
                            @NotNull ParkourSession session,
                            @NotNull LeaveReason reason,
                            boolean teleport,
                            @Nullable Location exitLocation) {
        super(player);
        this.session = session;
        this.reason = reason;
        this.teleport = teleport;
        this.exitLocation = exitLocation;
    }

    @NotNull
    public ParkourSession getSession() {
        return session;
    }

    @NotNull
    public LeaveReason getReason() {
        return reason;
    }

    /**
     * Whether the player will be teleported upon leaving.
     *
     * @return true if teleportation will occur
     */
    public boolean isTeleport() {
        return teleport;
    }

    /**
     * Sets whether the player should be teleported upon leaving.
     *
     * @param teleport true to teleport
     */
    public void setTeleport(boolean teleport) {
        this.teleport = teleport;
    }

    /**
     * Returns the exit location the player will be teleported to, if any.
     *
     * @return exit location or null
     */
    @Nullable
    public Location getExitLocation() {
        return exitLocation;
    }

    /**
     * Overrides the destination location where the player will be returned upon leaving.
     *
     * @param exitLocation custom exit destination
     */
    public void setExitLocation(@Nullable Location exitLocation) {
        this.exitLocation = exitLocation;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        if (!reason.isCancellable()) {
            return; // Disconnects or plugin shutdown cannot be cancelled
        }
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
     * The trigger that initiated leaving the parkour session.
     */
    public enum LeaveReason {
        /** Player typed /bp leave */
        COMMAND(true),
        /** Player clicked the Leave hotbar utility item */
        HOTBAR(true),
        /** Triggered programmatically via API */
        API(true),
        /** Player disconnected from the server */
        DISCONNECT(false),
        /** Plugin shutdown or reload */
        PLUGIN_STOP(false),
        /** Player was teleported away externally (command, server, chorus fruit, etc.) */
        TELEPORT(false);

        private final boolean cancellable;

        LeaveReason(boolean cancellable) {
            this.cancellable = cancellable;
        }

        public boolean isCancellable() {
            return cancellable;
        }
    }
}
