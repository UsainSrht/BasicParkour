package me.usainsrht.basicparkour.api.modifier;

import me.usainsrht.basicparkour.api.session.ParkourSession;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * An SPI that allows third-party plugins to define custom block interactions
 * within a parkour course.
 *
 * <h2>Registration</h2>
 * <pre>{@code
 * BasicParkourAPI.get().getModifierRegistry().register(new MyCustomModifier());
 * }</pre>
 *
 * <h2>Invocation</h2>
 * The Basic Parkour engine calls {@link #apply(Player, ParkourSession)} on the player's
 * entity scheduler thread when the player's feet enter a block that {@link #matches(Block)}.
 */
public interface BlockModifier {

    /**
     * The unique {@link NamespacedKey} that identifies this modifier.
     * Used in course YAML files via {@code modifiers[].type}.
     *
     * @return namespaced key
     */
    @NotNull
    NamespacedKey getKey();

    /**
     * Returns {@code true} if this modifier should activate for the given block.
     *
     * <p>The default implementation matches the block's material against
     * {@link #getDefaultMaterial()}. Override for custom matching logic.</p>
     *
     * @param block the block the player is standing on or inside
     * @return whether this modifier applies
     */
    boolean matches(@NotNull Block block);

    /**
     * Applies this modifier's effect to the player within the context of their session.
     * This method is always called on the entity's owning region thread.
     *
     * @param player  the affected player
     * @param session the player's active session
     */
    void apply(@NotNull Player player, @NotNull ParkourSession session);

    /**
     * Returns a human-readable description of this modifier for admin display.
     *
     * @return display name string
     */
    @NotNull
    default String getDisplayName() {
        return getKey().toString();
    }
}
