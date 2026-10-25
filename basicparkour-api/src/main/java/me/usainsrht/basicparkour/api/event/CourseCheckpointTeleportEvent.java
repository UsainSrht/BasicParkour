package me.usainsrht.basicparkour.api.event;

import me.usainsrht.basicparkour.api.course.Checkpoint;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

/**
 * Fired when a player teleports back to their last checkpoint (or start location)
 * during an active session (via command, hotbar item, or API).
 *
 * <p>Cancelling this event cancels the teleportation.</p>
 */
public class CourseCheckpointTeleportEvent extends PlayerParkourEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final ParkourSession session;
    private final Checkpoint checkpoint;
    private Location targetLocation;

    public CourseCheckpointTeleportEvent(@NotNull Player player,
                                        @NotNull ParkourSession session,
                                        @NotNull Location targetLocation) {
        super(player);
        this.session = session;
        this.checkpoint = session.getLastCheckpoint().orElse(null);
        this.targetLocation = targetLocation;
    }

    @NotNull
    public ParkourSession getSession() {
        return session;
    }

    /**
     * Returns the checkpoint the player is returning to, or empty if returning to start.
     *
     * @return optional checkpoint
     */
    @NotNull
    public Optional<Checkpoint> getCheckpoint() {
        return Optional.ofNullable(checkpoint);
    }

    /**
     * Returns the destination location where the player will be teleported.
     *
     * @return target location
     */
    @NotNull
    public Location getTargetLocation() {
        return targetLocation;
    }

    /**
     * Modifies the destination location for the checkpoint teleport.
     *
     * @param targetLocation the new target location
     */
    public void setTargetLocation(@NotNull Location targetLocation) {
        this.targetLocation = targetLocation;
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
