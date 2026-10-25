package me.usainsrht.basicparkour.api.event;

import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.api.session.ParkourSession.FailReason;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player is about to fail their parkour session (kill block, void,
 * time limit, or programmatic trigger), BEFORE the session state is changed and
 * before respawn teleportation is executed.
 *
 * <p>Cancelling this event cancels the failure entirely; the player remains in their
 * active session and no fail messages or respawn teleports take place. This is ideal
 * for custom shields, extra lives, or grace period mechanics.</p>
 */
public class CoursePreFailEvent extends PlayerParkourEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final ParkourSession session;
    private FailReason reason;
    private Location respawnLocation;

    public CoursePreFailEvent(@NotNull Player player,
                              @NotNull ParkourSession session,
                              @NotNull FailReason reason,
                              @NotNull Location respawnLocation) {
        super(player);
        this.session = session;
        this.reason = reason;
        this.respawnLocation = respawnLocation;
    }

    @NotNull
    public ParkourSession getSession() {
        return session;
    }

    /**
     * Returns the reason for the upcoming failure.
     *
     * @return fail reason
     */
    @NotNull
    public FailReason getReason() {
        return reason;
    }

    /**
     * Changes the fail reason for this failure.
     *
     * @param reason the new reason
     */
    public void setReason(@NotNull FailReason reason) {
        this.reason = reason;
    }

    /**
     * Returns the target location where the player will be teleported upon failing.
     *
     * @return respawn location
     */
    @NotNull
    public Location getRespawnLocation() {
        return respawnLocation;
    }

    /**
     * Modifies the respawn location where the player will be teleported upon failing.
     *
     * @param respawnLocation the custom respawn location
     */
    public void setRespawnLocation(@NotNull Location respawnLocation) {
        this.respawnLocation = respawnLocation;
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
