package me.usainsrht.basicparkour.api.event;

import me.usainsrht.basicparkour.api.course.ParkourCourse;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player begins a new parkour session by entering a course's start region.
 *
 * <p>If cancelled, the session will NOT be created and the player's movement will
 * not be tracked for this entry event. Subsequent entries into the start region
 * will fire this event again.</p>
 */
public class CourseStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final Player player;
    private final ParkourCourse course;

    public CourseStartEvent(@NotNull Player player, @NotNull ParkourCourse course) {
        super(false); // synchronous
        this.player = player;
        this.course = course;
    }

    /** The player who entered the start region. */
    @NotNull
    public Player getPlayer() { return player; }

    /** The course that is being started. */
    @NotNull
    public ParkourCourse getCourse() { return course; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    @NotNull
    public HandlerList getHandlers() { return HANDLERS; }

    @NotNull
    public static HandlerList getHandlerList() { return HANDLERS; }
}
