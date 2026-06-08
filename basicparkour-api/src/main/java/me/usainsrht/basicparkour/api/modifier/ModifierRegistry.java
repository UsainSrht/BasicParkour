package me.usainsrht.basicparkour.api.modifier;

import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry for all active {@link BlockModifier} implementations.
 *
 * <p>Modifiers are evaluated in registration order. The first modifier whose
 * {@link BlockModifier#matches(org.bukkit.block.Block)} returns {@code true} is applied.
 * Course-specific overrides are checked before global modifiers.</p>
 */
public interface ModifierRegistry {

    /**
     * Registers a block modifier. The modifier's {@link BlockModifier#getKey()} must be unique.
     *
     * @param modifier the modifier to register
     * @throws IllegalArgumentException if a modifier with the same key is already registered
     */
    void register(@NotNull BlockModifier modifier);

    /**
     * Unregisters the modifier with the given key, if present.
     *
     * @param key the modifier key to remove
     */
    void unregister(@NotNull NamespacedKey key);

    /**
     * Finds a modifier by its namespaced key.
     *
     * @param key the key to look up
     * @return the modifier, or empty
     */
    @NotNull
    Optional<BlockModifier> getModifier(@NotNull NamespacedKey key);

    /**
     * Returns all registered modifiers.
     *
     * @return unmodifiable collection of modifiers
     */
    @NotNull
    @Unmodifiable
    Collection<BlockModifier> getModifiers();
}
