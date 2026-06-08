package me.usainsrht.basicparkour.core.modifier;

import me.usainsrht.basicparkour.api.modifier.BlockModifier;
import me.usainsrht.basicparkour.api.modifier.ModifierRegistry;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe implementation of {@link ModifierRegistry}.
 * Preserves registration order for deterministic evaluation.
 */
public final class ModifierRegistryImpl implements ModifierRegistry {

    /** LinkedHashMap preserves insertion order; synchronised wrapper for thread safety. */
    private final Map<NamespacedKey, BlockModifier> modifiers =
        Collections.synchronizedMap(new LinkedHashMap<>());

    @Override
    public void register(@NotNull BlockModifier modifier) {
        NamespacedKey key = modifier.getKey();
        if (modifiers.containsKey(key)) {
            throw new IllegalArgumentException(
                "A modifier with key '%s' is already registered.".formatted(key));
        }
        modifiers.put(key, modifier);
    }

    @Override
    public void unregister(@NotNull NamespacedKey key) {
        modifiers.remove(key);
    }

    @Override
    @NotNull
    public Optional<BlockModifier> getModifier(@NotNull NamespacedKey key) {
        return Optional.ofNullable(modifiers.get(key));
    }

    @Override
    @NotNull
    @Unmodifiable
    public Collection<BlockModifier> getModifiers() {
        return Collections.unmodifiableCollection(modifiers.values());
    }
}
