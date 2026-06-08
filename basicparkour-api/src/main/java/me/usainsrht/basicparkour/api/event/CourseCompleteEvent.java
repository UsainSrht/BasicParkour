package me.usainsrht.basicparkour.api.event;

import me.usainsrht.basicparkour.api.session.ParkourSession;
import me.usainsrht.basicparkour.api.storage.PlayerRecord;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player successfully completes a parkour course.
 *
 * <p>This event is not cancellable; the session has already been marked complete
 * by the time listeners receive it. Use the timer data here to display results or
 * trigger external integrations.</p>
 */
public class CourseCompleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ParkourSession session;
    private final long elapsedMs;
    private final boolean isPersonalBest;
    private final @Nullable PlayerRecord previousBest;

    public CourseCompleteEvent(@NotNull Player player,
                                @NotNull ParkourSession session,
                                long elapsedMs,
                                boolean isPersonalBest,
                                @Nullable PlayerRecord previousBest) {
        super(false);
        this.player = player;
        this.session = session;
        this.elapsedMs = elapsedMs;
        this.isPersonalBest = isPersonalBest;
        this.previousBest = previousBest;
    }

    @NotNull public Player getPlayer() { return player; }
    @NotNull public ParkourSession getSession() { return session; }

    /** The total completion time in milliseconds. */
    public long getElapsedMs() { return elapsedMs; }

    /** Whether this run is a new personal best for the player on this course. */
    public boolean isPersonalBest() { return isPersonalBest; }

    /**
     * The player's previous personal best record, or {@code null} if this is their first completion.
     *
     * @return previous best, or null
     */
    @Nullable public PlayerRecord getPreviousBest() { return previousBest; }

    @Override @NotNull public HandlerList getHandlers() { return HANDLERS; }
    @NotNull public static HandlerList getHandlerList() { return HANDLERS; }
}
