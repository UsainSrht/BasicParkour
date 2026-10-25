package me.usainsrht.basicparkour.api.event;

import org.bukkit.event.Event;

/**
 * Base class for all Basic Parkour events.
 */
public abstract class ParkourEvent extends Event {

    public ParkourEvent() {
        super(false); // Synchronous by default
    }

    public ParkourEvent(boolean isAsync) {
        super(isAsync);
    }
}
