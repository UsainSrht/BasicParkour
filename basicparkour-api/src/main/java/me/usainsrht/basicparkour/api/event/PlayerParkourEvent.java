package me.usainsrht.basicparkour.api.event;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for all player-centric Basic Parkour events.
 */
public abstract class PlayerParkourEvent extends ParkourEvent {

    protected final Player player;

    public PlayerParkourEvent(@NotNull Player player) {
        super(false);
        this.player = player;
    }

    public PlayerParkourEvent(@NotNull Player player, boolean isAsync) {
        super(isAsync);
        this.player = player;
    }

    /**
     * Returns the player associated with this event.
     *
     * @return the player
     */
    @NotNull
    public Player getPlayer() {
        return player;
    }
}
