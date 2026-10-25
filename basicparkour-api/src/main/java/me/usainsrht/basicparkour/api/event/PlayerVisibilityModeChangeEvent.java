package me.usainsrht.basicparkour.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Fired when a player's parkour visibility mode (ALL, TRANSLUCENT, NONE) is changed.
 *
 * <p>Cancelling this event prevents the visibility mode from being updated.</p>
 */
public class PlayerVisibilityModeChangeEvent extends PlayerParkourEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final String oldMode;
    private String newMode;

    public PlayerVisibilityModeChangeEvent(@NotNull Player player,
                                           @NotNull String oldMode,
                                           @NotNull String newMode) {
        super(player);
        this.oldMode = oldMode.toUpperCase(Locale.ROOT);
        this.newMode = newMode.toUpperCase(Locale.ROOT);
    }

    /**
     * Returns the previous visibility mode.
     *
     * @return old visibility mode (e.g. ALL, TRANSLUCENT, NONE)
     */
    @NotNull
    public String getOldMode() {
        return oldMode;
    }

    /**
     * Returns the requested new visibility mode.
     *
     * @return new visibility mode
     */
    @NotNull
    public String getNewMode() {
        return newMode;
    }

    /**
     * Modifies the new visibility mode to set.
     *
     * @param newMode the updated mode
     */
    public void setNewMode(@NotNull String newMode) {
        this.newMode = newMode.toUpperCase(Locale.ROOT);
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
