package me.usainsrht.basicparkour.api.event;

import me.usainsrht.basicparkour.api.storage.PlayerRecord;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a parkour completion record is about to be saved into storage.
 *
 * <p>Cancelling this event prevents the record from being saved to the database.</p>
 */
public class CourseRecordSaveEvent extends PlayerParkourEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private PlayerRecord record;
    private final boolean personalBest;

    public CourseRecordSaveEvent(@NotNull Player player,
                                 @NotNull PlayerRecord record,
                                 boolean personalBest) {
        super(player);
        this.record = record;
        this.personalBest = personalBest;
    }

    /**
     * Returns the player record that will be saved.
     *
     * @return the record
     */
    @NotNull
    public PlayerRecord getRecord() {
        return record;
    }

    /**
     * Updates the player record to be saved.
     *
     * @param record the updated record
     */
    public void setRecord(@NotNull PlayerRecord record) {
        this.record = record;
    }

    /**
     * Whether this record is expected to be a new personal best for the player.
     *
     * @return true if personal best
     */
    public boolean isPersonalBest() {
        return personalBest;
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
