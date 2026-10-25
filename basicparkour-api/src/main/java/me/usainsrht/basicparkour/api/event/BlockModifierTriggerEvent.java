package me.usainsrht.basicparkour.api.event;

import me.usainsrht.basicparkour.api.modifier.BlockModifier;
import me.usainsrht.basicparkour.api.session.ParkourSession;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player triggers a {@link BlockModifier} (such as a jump pad, boost block,
 * kill block, or custom third-party modifier).
 *
 * <p>Cancelling this event prevents the modifier from applying its effect to the player.</p>
 */
public class BlockModifierTriggerEvent extends PlayerParkourEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();
    private boolean cancelled;

    private final ParkourSession session;
    private final Block block;
    private final BlockModifier modifier;

    public BlockModifierTriggerEvent(@NotNull Player player,
                                     @NotNull ParkourSession session,
                                     @NotNull Block block,
                                     @NotNull BlockModifier modifier) {
        super(player);
        this.session = session;
        this.block = block;
        this.modifier = modifier;
    }

    @NotNull
    public ParkourSession getSession() {
        return session;
    }

    /**
     * Returns the block that triggered the modifier.
     *
     * @return the block
     */
    @NotNull
    public Block getBlock() {
        return block;
    }

    /**
     * Returns the modifier that is about to be applied.
     *
     * @return the block modifier
     */
    @NotNull
    public BlockModifier getModifier() {
        return modifier;
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
